package net.intertime.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Генератор світу InterTime: материки, океани, річки, високі гори,
 * печери (зали + тунелі + кришталеві зали) та власні структури.
 * Дерева, руди й трава додаються стандартними деко-фічами за біомами.
 */
public class InterGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo wi, Random random, int cx, int cz, ChunkData data) {
        Terrain t = Terrain.of(wi.getSeed());
        int minY = wi.getMinHeight();
        int maxY = wi.getMaxHeight();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx;
                int z = cz * 16 + lz;
                Terrain.Info in = t.info(x, z);
                int h = Math.max(minY + 6, Math.min(maxY - 3, (int) Math.round(in.height)));
                Biome b = t.biome(in);
                Material top = t.top(b, in, x, z);
                int midDepth = t.midDepth(b, in);
                boolean crystal = t.crystalZone(x, z);
                int carveTop = t.carveTop(x, z, h);
                boolean prevAir = false;

                for (int y = minY; y <= h; y++) {
                    int hs = Terrain.hash(x, y, z);
                    if (y == minY || (y - minY < 4 && hs % (y - minY + 1) == 0)) {
                        data.setBlock(lx, y, lz, Material.BEDROCK);
                        prevAir = false;
                        continue;
                    }
                    int depth = h - y;
                    Material m;
                    if (depth == 0) m = top;
                    else if (depth <= midDepth) m = t.mid(b, in, x, y, z);
                    else m = t.stoneAt(y, hs);

                    boolean air = false;
                    if (y > minY + 4 && y <= carveTop && t.carved(x, y, z)) {
                        air = true;
                        m = y <= minY + 10 ? Material.LAVA : Material.AIR;
                    }

                    boolean band = crystal && y > -48 && y < 24;
                    if (air && m == Material.AIR) {
                        if (band && !prevAir) {
                            // підлога печери: кристали та кальцит
                            data.setBlock(lx, y - 1, lz, hs % 100 < 35 ? Material.AMETHYST_BLOCK : Material.CALCITE);
                            if (hs % 100 < 14) m = Material.AMETHYST_CLUSTER;
                        }
                    } else if (!air && prevAir && band && hs % 100 < 30) {
                        m = Material.AMETHYST_BLOCK;      // стеля
                    }

                    data.setBlock(lx, y, lz, m);
                    prevAir = air && m == Material.AIR;
                }

                // вода
                for (int y = h + 1; y <= Terrain.SEA && y < maxY; y++) {
                    data.setBlock(lx, y, lz, Material.WATER);
                }
                if (h < Terrain.SEA && (b == Biome.FROZEN_OCEAN || b == Biome.FROZEN_RIVER)) {
                    data.setBlock(lx, Terrain.SEA, lz, Material.ICE);
                }
            }
        }
    }

    // справжня генерація шуму, поверхні та печер - наша; деко-фічі (дерева, руди) - стандартні
    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new InterBiomeProvider();
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return new ArrayList<>(Arrays.asList(
                new Structures.Towers(),
                new Structures.Islands(),
                new Structures.Spires(),
                new Structures.Ruins()));
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        Terrain t = Terrain.of(world.getSeed());
        for (int ring = 0; ring < 80; ring++) {
            int dist = ring * 24;
            int steps = Math.max(1, ring * 6);
            for (int s = 0; s < steps; s++) {
                double a = 2 * Math.PI * s / steps;
                int x = (int) Math.round(Math.cos(a) * dist);
                int z = (int) Math.round(Math.sin(a) * dist);
                Terrain.Info in = t.info(x, z);
                if (in.height > Terrain.SEA + 2 && in.height < 105 && in.river < 0.3) {
                    return new Location(world, x + 0.5, (int) in.height + 1, z + 0.5);
                }
            }
        }
        return new Location(world, 0.5, Terrain.SEA + 3, 0.5);
    }
}
