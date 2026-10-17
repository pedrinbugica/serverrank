package dev.pedrobugica.survivalrpg.mundo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/** Mundo vazio (void) do lobby do RPG: a ilha é construída pelo plugin. */
public final class GeradorVazio extends ChunkGenerator {

    private static final BiomeProvider BIOMAS = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.MEADOW;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.MEADOW);
        }
    };

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return BIOMAS;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, LobbyRpg.Y + 1, 18.5, 180f, 0f);
    }
}
