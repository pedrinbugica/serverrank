package br.rankupsupremo.mundo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Gerador de mundo vazio (void). O spawn e as minas são construídos pelo plugin.
 * Configurado em bukkit.yml -> worlds.world.generator: RankupSupremo
 */
public final class GeradorVazio extends ChunkGenerator {

    private static final BiomeProvider BIOMAS = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.PLAINS;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.PLAINS);
        }
    };

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return BIOMAS;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, ConstrutorMundo.SPAWN_X + 0.5, ConstrutorMundo.SPAWN_Y + 1,
                ConstrutorMundo.SPAWN_Z + 8.5, 180f, 0f);
    }
}
