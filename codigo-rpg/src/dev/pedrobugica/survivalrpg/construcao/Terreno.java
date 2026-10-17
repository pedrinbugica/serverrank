package dev.pedrobugica.survivalrpg.construcao;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Leitura do terreno sem travar: carrega os chunks de uma área em segundo plano e tira "fotos" (snapshots)
 * para ler blocos e alturas à vontade.
 */
public final class Terreno {

    private final World mundo;
    private final Map<Long, ChunkSnapshot> fotos = new HashMap<>();

    private Terreno(World mundo) {
        this.mundo = mundo;
    }

    public World mundo() {
        return mundo;
    }

    private static long chave(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Carrega (gerando se precisar) os chunks do retângulo de blocos e devolve as fotos na thread principal. */
    public static CompletableFuture<Terreno> carregar(World w, int x1, int z1, int x2, int z2) {
        Terreno t = new Terreno(w);
        int cx1 = Math.min(x1, x2) >> 4;
        int cx2 = Math.max(x1, x2) >> 4;
        int cz1 = Math.min(z1, z2) >> 4;
        int cz2 = Math.max(z1, z2) >> 4;
        int total = (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
        CompletableFuture<Terreno> f = new CompletableFuture<>();
        AtomicInteger faltam = new AtomicInteger(total);
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                final int a = cx;
                final int b = cz;
                w.getChunkAtAsync(a, b, true, (Chunk c) -> {
                    if (c != null) {
                        t.fotos.put(chave(a, b), c.getChunkSnapshot(true, true, false));
                    }
                    if (faltam.decrementAndGet() == 0) {
                        f.complete(t);
                    }
                });
            }
        }
        return f;
    }

    private ChunkSnapshot foto(int x, int z) {
        return fotos.get(chave(x >> 4, z >> 4));
    }

    public boolean temFoto(int x, int z) {
        return foto(x, z) != null;
    }

    public Material tipo(int x, int y, int z) {
        ChunkSnapshot s = foto(x, z);
        if (s == null || y < mundo.getMinHeight() || y >= mundo.getMaxHeight()) {
            return Material.AIR;
        }
        return s.getBlockType(x & 15, y, z & 15);
    }

    public org.bukkit.block.data.BlockData dados(int x, int y, int z) {
        ChunkSnapshot s = foto(x, z);
        if (s == null || y < mundo.getMinHeight() || y >= mundo.getMaxHeight()) {
            return Material.AIR.createBlockData();
        }
        return s.getBlockData(x & 15, y, z & 15);
    }

    /** Bloco mais alto que não é ar (inclui folhas e água). */
    public int topo(int x, int z) {
        ChunkSnapshot s = foto(x, z);
        if (s == null) {
            return mundo.getMinHeight();
        }
        int y = Math.min(mundo.getMaxHeight() - 1, s.getHighestBlockYAt(x & 15, z & 15) + 1);
        while (y > mundo.getMinHeight() && s.getBlockType(x & 15, y, z & 15).isAir()) {
            y--;
        }
        return y;
    }

    /** O chão de verdade: ignora árvores, plantas, neve e água. */
    public int chao(int x, int z) {
        int y = topo(x, z);
        while (y > mundo.getMinHeight()) {
            Material m = tipo(x, y, z);
            if (m.isSolid() && !Tag.LOGS.isTagged(m) && !Tag.LEAVES.isTagged(m) && m != Material.SNOW
                    && !m.name().contains("MUSHROOM_BLOCK") && m != Material.MUSHROOM_STEM && m != Material.BAMBOO
                    && m != Material.CACTUS && m != Material.PUMPKIN && m != Material.MELON && m != Material.BEE_NEST) {
                return y;
            }
            y--;
        }
        return y;
    }

    /** Tem água em cima do chão desta coluna? */
    public boolean agua(int x, int z) {
        int c = chao(x, z);
        Material m = tipo(x, c + 1, z);
        return m == Material.WATER || m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS
                || m == Material.TALL_SEAGRASS || m == Material.ICE;
    }

    public org.bukkit.block.Biome bioma(int x, int y, int z) {
        ChunkSnapshot s = foto(x, z);
        return s == null ? null : s.getBiome(x & 15, y, z & 15);
    }

    /** Conta blocos "de jogador" perto da superfície num raio (baús, camas, fornalhas, placas, vidro...). */
    public int blocosDeJogador(int cx, int cz, int raio, Set<Material> extras) {
        int achados = 0;
        for (int x = cx - raio; x <= cx + raio; x++) {
            for (int z = cz - raio; z <= cz + raio; z++) {
                if (!temFoto(x, z)) {
                    continue;
                }
                int topo = topo(x, z);
                int chao = chao(x, z);
                for (int y = Math.max(mundo.getMinHeight(), chao - 12); y <= topo + 1; y++) {
                    Material m = tipo(x, y, z);
                    if (deJogador(m) || extras.contains(m)) {
                        achados++;
                    }
                }
            }
        }
        return achados;
    }

    public static boolean deJogador(Material m) {
        if (m.isAir()) {
            return false;
        }
        String n = m.name();
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL || m == Material.FURNACE
                || m == Material.BLAST_FURNACE || m == Material.SMOKER || m == Material.CRAFTING_TABLE
                || m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL
                || m == Material.ENCHANTING_TABLE || m == Material.BREWING_STAND || m == Material.HOPPER
                || m == Material.FARMLAND || m == Material.TORCH || m == Material.WALL_TORCH || m == Material.GLASS
                || m == Material.GLASS_PANE || m == Material.BOOKSHELF || m == Material.JUKEBOX || m == Material.BEACON
                || m == Material.ENDER_CHEST || m == Material.LECTERN || m == Material.LOOM || m == Material.STONECUTTER
                || m == Material.CARTOGRAPHY_TABLE || m == Material.SMITHING_TABLE || m == Material.GRINDSTONE
                || m == Material.REDSTONE_WIRE || m == Material.REPEATER || m == Material.COMPARATOR || m == Material.PISTON
                || m == Material.OBSERVER || m == Material.DISPENSER || m == Material.DROPPER
                || n.endsWith("_BED") || n.endsWith("_SIGN") || n.endsWith("SHULKER_BOX") || n.endsWith("_DOOR")
                || n.endsWith("_CONCRETE") || n.endsWith("_STAINED_GLASS") || n.endsWith("_CARPET") || n.endsWith("_BANNER");
    }
}
