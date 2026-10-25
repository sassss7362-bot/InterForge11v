package net.intertime.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.loot.LootTables;

import java.util.Random;

/** Власні структури: вежі, острови в небі, кришталеві шпилі та руїни. */
public final class Structures {

    private Structures() {
    }

    /** Контекст однієї генерації (щоб популятори не зберігали стан між потоками). */
    static final class Ctx {
        final WorldInfo wi;
        final LimitedRegion rg;
        final Random r;
        final Terrain t;

        Ctx(WorldInfo wi, LimitedRegion rg, Random r, Terrain t) {
            this.wi = wi;
            this.rg = rg;
            this.r = r;
            this.t = t;
        }

        boolean ok(int x, int y, int z) {
            return y >= wi.getMinHeight() && y < wi.getMaxHeight() && rg.isInRegion(x, y, z);
        }

        void set(int x, int y, int z, Material m) {
            if (ok(x, y, z)) rg.setType(x, y, z, m);
        }

        void setData(int x, int y, int z, BlockData d) {
            if (ok(x, y, z)) rg.setBlockData(x, y, z, d);
        }

        Material get(int x, int y, int z) {
            return ok(x, y, z) ? rg.getType(x, y, z) : Material.AIR;
        }

        void chest(int x, int y, int z, LootTables table) {
            if (!ok(x, y, z)) return;
            rg.setType(x, y, z, Material.CHEST);
            try {
                BlockState st = rg.getBlockState(x, y, z);
                if (st instanceof Chest ch) {
                    ch.setLootTable(table.getLootTable());
                    ch.update(true, false);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    abstract static class Base extends BlockPopulator {
        /** 1 шанс із N на чанк. */
        abstract int chance();

        abstract void build(Ctx c, int x, int z);

        @Override
        public void populate(WorldInfo wi, Random r, int cx, int cz, LimitedRegion rg) {
            if (r.nextInt(chance()) != 0) return;
            int x = cx * 16 + 4 + r.nextInt(8);
            int z = cz * 16 + 4 + r.nextInt(8);
            try {
                build(new Ctx(wi, rg, r, Terrain.of(wi.getSeed())), x, z);
            } catch (Throwable ignored) {
            }
        }
    }

    // ================================================================ Зруйновані вежі

    public static class Towers extends Base {
        @Override
        int chance() {
            return 70;
        }

        @Override
        void build(Ctx c, int x, int z) {
            Terrain.Info in = c.t.info(x, z);
            if (in.height <= Terrain.SEA + 2 || in.river > 0.2 || in.height > 130) return;
            int lo = (int) in.height, hi = lo;
            int[][] off = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};
            for (int[] o : off) {
                int hh = c.t.height(x + o[0], z + o[1]);
                lo = Math.min(lo, hh);
                hi = Math.max(hi, hh);
            }
            if (hi - lo > 8) return;
            int base = lo;
            int height = 14 + c.r.nextInt(10);

            // фундамент та очищення (дерева, пагорби)
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) > 5.4) continue;
                    int gx = x + dx, gz = z + dz;
                    int ground = c.t.height(gx, gz);
                    for (int y = Math.min(ground, base) - 1; y < base; y++) c.set(gx, y, gz, Material.COBBLESTONE);
                    for (int y = base; y <= base + height + 3; y++) c.set(gx, y, gz, Material.AIR);
                }
            }

            Material[] wall = {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE};
            for (int y = 0; y <= height; y++) {
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        int px = x + dx, py = base + y, pz = z + dz;
                        if (y == 0) {
                            if (d < 4.4) c.set(px, py, pz, Material.COBBLESTONE);
                        } else if (d >= 3.3 && d <= 4.4) {
                            if (y > height * 0.65 && c.r.nextInt(100) < (y - height * 0.65) * 14) continue; // зруйнований верх
                            boolean door = dz > 0 && Math.abs(dx) <= 1 && (y == 1 || y == 2);
                            boolean window = y > 2 && y % 5 == 2 && (dx == 0 || dz == 0);
                            if (door || window) continue;
                            c.set(px, py, pz, wall[c.r.nextInt(wall.length)]);
                        } else if (d < 3.3 && y % 5 == 0 && y < height - 1 && !(dx == -3 && dz == 0)) {
                            c.set(px, py, pz, Material.SPRUCE_PLANKS);       // поверхи
                        }
                    }
                }
            }

            // драбина всередині
            BlockData ladder = Material.LADDER.createBlockData();
            ((Directional) ladder).setFacing(BlockFace.EAST);
            for (int y = 1; y < height; y++) c.setData(x - 3, base + y, z, ladder);

            c.chest(x + 2, base + 1, z, LootTables.SIMPLE_DUNGEON);
            c.chest(x - 2, base + 6, z + 1, LootTables.SIMPLE_DUNGEON);
        }
    }

    // ================================================================ Острови в небі

    public static class Islands extends Base {
        @Override
        int chance() {
            return 40;
        }

        @Override
        void build(Ctx c, int x, int z) {
            int y0 = 140 + c.r.nextInt(50);
            if (c.t.info(x, z).height + 30 > y0) return;
            int radius = 7 + c.r.nextInt(5);
            int depth = radius + 5;

            for (int dy = 0; dy <= depth; dy++) {
                double rad = radius * Math.pow(1.0 - (double) dy / depth, 0.65);
                int rr = (int) Math.ceil(rad) + 1;
                for (int dx = -rr; dx <= rr; dx++) {
                    for (int dz = -rr; dz <= rr; dz++) {
                        int hs = Terrain.hash(x + dx, dy, z + dz);
                        double d = Math.sqrt(dx * dx + dz * dz) + (hs % 100) / 100.0 * 1.2 - 0.6;
                        if (d > rad) continue;
                        Material m;
                        if (dy == 0) m = Material.GRASS_BLOCK;
                        else if (dy <= 3) m = Material.DIRT;
                        else if (hs % 100 < 3) m = Material.IRON_ORE;
                        else if (hs % 100 < 6) m = Material.COAL_ORE;
                        else m = Material.STONE;
                        c.set(x + dx, y0 - dy, z + dz, m);
                    }
                }
            }

            // ставок
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 4 && c.get(x - 3 + dx, y0, z - 3 + dz) == Material.GRASS_BLOCK) {
                        c.set(x - 3 + dx, y0, z - 3 + dz, Material.WATER);
                    }
                }
            }
            // квіти
            Material[] flowers = {Material.DANDELION, Material.POPPY, Material.ALLIUM, Material.AZURE_BLUET};
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (c.r.nextInt(14) == 0 && c.get(x + dx, y0, z + dz) == Material.GRASS_BLOCK
                            && c.get(x + dx, y0 + 1, z + dz) == Material.AIR) {
                        c.set(x + dx, y0 + 1, z + dz, flowers[c.r.nextInt(flowers.length)]);
                    }
                }
            }
            // дерево
            World w = Bukkit.getWorld(c.wi.getUID());
            if (w != null) {
                try {
                    c.rg.generateTree(new Location(w, x + 2 + c.r.nextInt(3), y0 + 1, z + 2 + c.r.nextInt(3)),
                            c.r, TreeType.TREE);
                } catch (Throwable ignored) {
                }
            }
            // кристали під островом
            for (int k = 0; k < 4; k++) {
                int px = x + c.r.nextInt(5) - 2, pz = z + c.r.nextInt(5) - 2;
                int py = y0 - depth + 1 + c.r.nextInt(3);
                if (c.get(px, py, pz) == Material.STONE && c.get(px, py - 1, pz) == Material.AIR) {
                    c.set(px, py - 1, pz, Material.AMETHYST_CLUSTER);
                }
            }
        }
    }

    // ================================================================ Кришталеві шпилі

    public static class Spires extends Base {
        @Override
        int chance() {
            return 12;
        }

        @Override
        void build(Ctx c, int x, int z) {
            Terrain.Info in = c.t.info(x, z);
            if (in.height < 100 || in.river > 0.2) return;
            int base = c.t.height(x, z);
            int height = 12 + c.r.nextInt(14);

            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz > 18) continue;
                    for (int y = base - 3; y <= base; y++) c.set(x + dx, y, z + dz, Material.CALCITE);
                    for (int y = base + 1; y <= base + 3; y++) c.set(x + dx, y, z + dz, Material.AIR);
                }
            }
            for (int y = 1; y <= height; y++) {
                double rad = 3.2 * (1.0 - (double) y / height);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > rad * rad) continue;
                        int hs = Terrain.hash(x + dx, y, z + dz) % 100;
                        c.set(x + dx, base + y, z + dz, hs < 4 ? Material.BUDDING_AMETHYST : Material.AMETHYST_BLOCK);
                    }
                }
            }
            for (int k = 0; k < 10; k++) {
                int px = x + c.r.nextInt(9) - 4, pz = z + c.r.nextInt(9) - 4;
                if (c.get(px, base + 1, pz) == Material.AIR && c.get(px, base, pz) == Material.CALCITE) {
                    c.set(px, base + 1, pz, Material.AMETHYST_CLUSTER);
                }
            }
            c.set(x, base + height + 1, z, Material.AMETHYST_CLUSTER);
        }
    }

    // ================================================================ Руїни храму

    public static class Ruins extends Base {
        @Override
        int chance() {
            return 55;
        }

        @Override
        void build(Ctx c, int x, int z) {
            Terrain.Info in = c.t.info(x, z);
            if (in.height <= Terrain.SEA + 2 || in.height > 115 || in.river > 0.2) return;
            int lo = (int) in.height, hi = lo;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4 * k;
                int hh = c.t.height(x + (int) Math.round(Math.cos(a) * 7), z + (int) Math.round(Math.sin(a) * 7));
                lo = Math.min(lo, hh);
                hi = Math.max(hi, hh);
            }
            if (hi - lo > 6) return;
            int base = lo;

            Biome b = c.t.biome(in);
            boolean sand = b == Biome.DESERT || b == Biome.BADLANDS || b == Biome.SAVANNA;
            boolean jungle = b == Biome.JUNGLE || b == Biome.SWAMP;
            Material wall = sand ? Material.SANDSTONE : jungle ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
            Material pillar = sand ? Material.CUT_SANDSTONE : jungle ? Material.MOSSY_COBBLESTONE : Material.STONE_BRICKS;
            Material floor = sand ? Material.SMOOTH_SANDSTONE : Material.STONE_BRICKS;
            Material altar = sand ? Material.CHISELED_SANDSTONE : Material.CHISELED_STONE_BRICKS;
            LootTables loot = sand ? LootTables.DESERT_PYRAMID : jungle ? LootTables.JUNGLE_TEMPLE : LootTables.SIMPLE_DUNGEON;

            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) > 7.5) continue;
                    int gx = x + dx, gz = z + dz;
                    int ground = c.t.height(gx, gz);
                    for (int y = Math.min(ground, base) - 1; y < base; y++) c.set(gx, y, gz, wall);
                    for (int y = base; y <= base + 10; y++) c.set(gx, y, gz, Material.AIR);
                }
            }
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) > 7) continue;
                    if (Terrain.hash(x + dx, base, z + dz) % 100 < 18) continue;     // діри в підлозі
                    c.set(x + dx, base, z + dz, c.r.nextInt(5) == 0 && !sand ? Material.CRACKED_STONE_BRICKS : floor);
                }
            }
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4 * k;
                int px = x + (int) Math.round(Math.cos(a) * 6), pz = z + (int) Math.round(Math.sin(a) * 6);
                int ph = 3 + c.r.nextInt(5);
                for (int y = 1; y <= ph; y++) c.set(px, base + y, pz, pillar);
                if (c.r.nextBoolean()) c.set(px, base + ph, pz, altar);
            }
            c.set(x, base + 1, z, altar);
            c.chest(x, base + 2, z, loot);
        }
    }
}
