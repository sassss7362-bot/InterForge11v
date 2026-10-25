package net.intertime.world;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Уся "математика" світу в одному місці: висота, біоми, печери, матеріали поверхні.
 * Той самий клас використовують генератор, провайдер біомів та структури.
 */
public final class Terrain {

    public static final int SEA = 63;

    private static final ConcurrentHashMap<Long, Terrain> CACHE = new ConcurrentHashMap<>();

    public static Terrain of(long seed) {
        return CACHE.computeIfAbsent(seed, Terrain::new);
    }

    /** Дані про одну колонку світу. */
    public static final class Info {
        public final double height, temp, humid, river, cont, mount;

        Info(double height, double temp, double humid, double river, double cont, double mount) {
            this.height = height;
            this.temp = temp;
            this.humid = humid;
            this.river = river;
            this.cont = cont;
            this.mount = mount;
        }
    }

    private static final Material[] BANDS = {
            Material.TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.RED_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.BROWN_TERRACOTTA};

    private final SimplexOctaveGenerator cont, ero, ridge, temp, humid, riv, detail;
    private final SimplexOctaveGenerator cave1, cave2, cave3, zone, lush, entr;
    private final ConcurrentHashMap<Long, Info> infoCache = new ConcurrentHashMap<>();

    private Terrain(long seed) {
        cont = oct(seed, 4, 0.0016);
        ero = oct(seed + 1, 3, 0.004);
        ridge = oct(seed + 2, 4, 0.0055);
        temp = oct(seed + 3, 2, 0.0011);
        humid = oct(seed + 4, 2, 0.0013);
        riv = oct(seed + 5, 3, 0.0028);
        detail = oct(seed + 6, 3, 0.02);
        cave1 = oct(seed + 7, 2, 0.018);
        cave2 = oct(seed + 8, 2, 0.028);
        cave3 = oct(seed + 9, 2, 0.028);
        zone = oct(seed + 10, 2, 0.0035);
        lush = oct(seed + 11, 2, 0.0035);
        entr = oct(seed + 12, 2, 0.012);
    }

    private static SimplexOctaveGenerator oct(long seed, int octaves, double scale) {
        SimplexOctaveGenerator g = new SimplexOctaveGenerator(seed, octaves);
        g.setScale(scale);
        return g;
    }

    private static double n2(SimplexOctaveGenerator g, double x, double z) {
        return g.noise(x, z, 2.0, 0.5, true);
    }

    private static double n3(SimplexOctaveGenerator g, double x, double y, double z) {
        return g.noise(x, y, z, 2.0, 0.5, true);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double smooth(double a, double b, double v) {
        double t = clamp((v - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return h & 0x7fffffff;
    }

    // ================================================================ висота та клімат

    public Info info(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Info i = infoCache.get(key);
        if (i == null) {
            i = compute(x, z);
            if (infoCache.size() > 60000) infoCache.clear();
            infoCache.put(key, i);
        }
        return i;
    }

    public int height(int x, int z) {
        return (int) Math.round(info(x, z).height);
    }

    private Info compute(int x, int z) {
        double c = clamp(n2(cont, x, z) * 1.5, -1, 1);                 // материки / океани
        double e = (n2(ero, x, z) + 1) / 2;                            // ерозія 0..1
        double rr = 1 - Math.abs(n2(ridge, x, z));                     // гребені гір
        rr *= rr;
        double mount = smooth(0.02, 0.40, c) * smooth(0.30, 0.70, e);  // де будуть гори

        double h = 64 + c * 40;
        h += mount * (rr * 140 + 15);
        h += n2(detail, x, z) * (3 + mount * 9);

        double rv = Math.abs(n2(riv, x, z));                           // річки
        double rf = 0;
        if (c > -0.08 && rv < 0.04) {
            rf = 1 - rv / 0.04;
            double target = SEA - 3.0;
            double k = rf * (1 - 0.85 * mount);
            h = h + (target - h) * k;
        }

        double t = clamp(n2(temp, x, z) * 1.6, -1.2, 1.2);
        double hu = clamp(n2(humid, x, z) * 1.6, -1.2, 1.2);
        t -= Math.max(0, (h - 100) / 160.0);                           // холодніше на висоті
        return new Info(Math.min(h, 300), t, hu, rf, c, mount);
    }

    // ================================================================ біоми

    public Biome biome(Info i) {
        double t = i.temp, hu = i.humid;
        int h = (int) i.height;
        if (h < SEA - 1) {
            boolean deep = h < 46;
            if (t < -0.45) return Biome.FROZEN_OCEAN;
            if (t > 0.5) return deep ? Biome.DEEP_OCEAN : Biome.WARM_OCEAN;
            return deep ? Biome.DEEP_OCEAN : Biome.OCEAN;
        }
        if (i.river > 0.55) return t < -0.45 ? Biome.FROZEN_RIVER : Biome.RIVER;
        if (h > 175) {
            if (t < -0.3) return Biome.FROZEN_PEAKS;
            if (t < 0.25) return Biome.JAGGED_PEAKS;
            return Biome.STONY_PEAKS;
        }
        if (h > 135) {
            if (t < -0.1) return Biome.SNOWY_SLOPES;
            return hu > 0.1 ? Biome.GROVE : Biome.WINDSWEPT_HILLS;
        }
        if (h > 108) {
            if (t < -0.35) return Biome.SNOWY_SLOPES;
            return hu > 0.0 ? Biome.MEADOW : Biome.WINDSWEPT_HILLS;
        }
        if (h <= SEA + 2) {
            if (hu > 0.35 && t > -0.2) return Biome.SWAMP;
            return t < -0.45 ? Biome.SNOWY_BEACH : Biome.BEACH;
        }
        if (t > 0.5) {
            if (hu < -0.25) return Biome.DESERT;
            if (hu < 0.2) return (hu < -0.05 && t > 0.7) ? Biome.BADLANDS : Biome.SAVANNA;
            return Biome.JUNGLE;
        }
        if (t > 0.0) {
            if (hu > 0.45) return Biome.SWAMP;
            if (hu > 0.25) return Biome.DARK_FOREST;
            if (hu > 0.0) return t > 0.25 ? Biome.FOREST : Biome.BIRCH_FOREST;
            return Biome.PLAINS;
        }
        if (t < -0.4) return hu > 0.0 ? Biome.SNOWY_TAIGA : Biome.SNOWY_PLAINS;
        return hu > 0.1 ? Biome.TAIGA : Biome.PLAINS;
    }

    /** Біом у точці з урахуванням підземних біомів (лускаві та дріпстоунові печери). */
    public Biome biomeAt(int x, int y, int z) {
        Info i = info(x, z);
        if (y < i.height - 14 && y > -56) {
            double c = n2(lush, x, z);
            if (c > 0.38) return Biome.LUSH_CAVES;
            if (c < -0.42) return Biome.DRIPSTONE_CAVES;
        }
        return biome(i);
    }

    // ================================================================ поверхня

    private boolean under(Info i) {
        return i.height < SEA - 1 || i.river > 0.55;
    }

    public Material top(Biome b, Info i, int x, int z) {
        int hs = hash(x, 0, z) % 10;
        if (under(i)) return hs < 3 ? Material.GRAVEL : hs < 5 ? Material.CLAY : Material.SAND;
        if (b == Biome.DESERT || b == Biome.BEACH || b == Biome.SNOWY_BEACH) return Material.SAND;
        if (b == Biome.BADLANDS) return Material.RED_SAND;
        if (b == Biome.FROZEN_PEAKS || b == Biome.SNOWY_SLOPES || b == Biome.GROVE) return Material.SNOW_BLOCK;
        if (b == Biome.JAGGED_PEAKS) return i.height > 190 ? Material.SNOW_BLOCK : Material.STONE;
        if (b == Biome.STONY_PEAKS) return Material.STONE;
        if (b == Biome.WINDSWEPT_HILLS && i.height > 125) return hs < 2 ? Material.GRAVEL : Material.STONE;
        return Material.GRASS_BLOCK;
    }

    public Material mid(Biome b, Info i, int x, int y, int z) {
        if (under(i)) return top(b, i, x, z);
        if (b == Biome.DESERT || b == Biome.BEACH || b == Biome.SNOWY_BEACH) return Material.SAND;
        if (b == Biome.BADLANDS) return BANDS[(y / 3 + 6000) % BANDS.length];
        if (b == Biome.FROZEN_PEAKS || b == Biome.SNOWY_SLOPES || b == Biome.GROVE
                || b == Biome.JAGGED_PEAKS || b == Biome.STONY_PEAKS) return Material.STONE;
        if (b == Biome.WINDSWEPT_HILLS && i.height > 125) return Material.STONE;
        return Material.DIRT;
    }

    public int midDepth(Biome b, Info i) {
        if (b == Biome.DESERT) return 4;
        if (b == Biome.BADLANDS) return 12;
        if (b == Biome.JAGGED_PEAKS || b == Biome.STONY_PEAKS || b == Biome.FROZEN_PEAKS) return 0;
        if (b == Biome.WINDSWEPT_HILLS && i.height > 125) return 0;
        return 3;
    }

    public Material stoneAt(int y, int hash) {
        if (y >= 0) return Material.STONE;
        if (y <= -8) return Material.DEEPSLATE;
        return (hash % 8) < -y ? Material.DEEPSLATE : Material.STONE;
    }

    // ================================================================ печери

    /** true, якщо блок треба вирізати (печера). */
    public boolean carved(int x, int y, int z) {
        double c1 = n3(cave1, x, y * 1.5, z);              // великі зали
        if (c1 > 0.58) return true;
        double a = n3(cave2, x, y, z);                     // тунелі-"спагеті"
        double b = n3(cave3, x, y, z);
        return a * a + b * b < 0.0028;
    }

    /** Найвища точка, до якої печери можуть вирізатись (подекуди виходять на поверхню). */
    public int carveTop(int x, int z, int h) {
        int top = h - 4;
        if (h > SEA + 3 && n2(entr, x, z) > 0.62) top = h;
        return top;
    }

    /** Зони кришталевих печер. */
    public boolean crystalZone(int x, int z) {
        return n2(zone, x, z) > 0.42;
    }
}
