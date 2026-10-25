package net.intertime.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.Arrays;
import java.util.List;

/** Віддає біоми з тієї ж математики, що й висота (тому дерева, трава й мобі відповідають ландшафту). */
public class InterBiomeProvider extends BiomeProvider {

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Terrain.of(worldInfo.getSeed()).biomeAt(x, y, z);
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return Arrays.asList(
                Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.FROZEN_OCEAN,
                Biome.RIVER, Biome.FROZEN_RIVER, Biome.BEACH, Biome.SNOWY_BEACH,
                Biome.PLAINS, Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST,
                Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.SNOWY_PLAINS,
                Biome.DESERT, Biome.SAVANNA, Biome.BADLANDS, Biome.JUNGLE, Biome.SWAMP,
                Biome.WINDSWEPT_HILLS, Biome.MEADOW, Biome.GROVE, Biome.SNOWY_SLOPES,
                Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.STONY_PEAKS,
                Biome.LUSH_CAVES, Biome.DRIPSTONE_CAVES);
    }
}
