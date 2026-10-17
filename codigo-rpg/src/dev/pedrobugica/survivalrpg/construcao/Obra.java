package dev.pedrobugica.survivalrpg.construcao;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lista de blocos a colocar, executada aos poucos (alguns milhares por tique) para não travar o servidor.
 * As escritas são feitas na ordem em que foram planejadas: a última para a mesma posição é a que fica.
 */
public final class Obra {

    private final World mundo;
    private long[] posicoes = new long[4096];
    private int[] blocos = new int[4096];
    private int total;
    private final List<BlockData> paleta = new ArrayList<>();
    private final Map<String, Integer> indice = new HashMap<>();

    public Obra(World mundo) {
        this.mundo = mundo;
    }

    public World mundo() {
        return mundo;
    }

    public int tamanho() {
        return total;
    }

    public static long chave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long k) {
        return (int) (k >> 38);
    }

    public static int y(long k) {
        return (int) (k << 52 >> 52);
    }

    public static int z(long k) {
        return (int) (k << 26 >> 38);
    }

    public void set(int x, int y, int z, BlockData d) {
        if (y < mundo.getMinHeight() || y >= mundo.getMaxHeight()) {
            return;
        }
        String s = d.getAsString();
        Integer i = indice.get(s);
        if (i == null) {
            i = paleta.size();
            paleta.add(d.clone());
            indice.put(s, i);
        }
        if (total == posicoes.length) {
            posicoes = java.util.Arrays.copyOf(posicoes, total * 2);
            blocos = java.util.Arrays.copyOf(blocos, total * 2);
        }
        posicoes[total] = chave(x, y, z);
        blocos[total] = i;
        total++;
    }

    public void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    public void set(int x, int y, int z, String dados) {
        set(x, y, z, Bukkit.createBlockData(dados));
    }

    /** Caixa cheia (inclusive nas duas pontas). */
    public void caixa(int x1, int y1, int z1, int x2, int y2, int z2, BlockData d) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, d);
                }
            }
        }
    }

    public void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        caixa(x1, y1, z1, x2, y2, z2, m.createBlockData());
    }

    /** Chunks que a obra toca (para carregar antes). */
    public Set<Long> chunks() {
        Set<Long> s = new HashSet<>();
        for (int i = 0; i < total; i++) {
            long k = posicoes[i];
            s.add((((long) (x(k) >> 4)) << 32) | ((z(k) >> 4) & 0xFFFFFFFFL));
        }
        return s;
    }

    /** Executa aos poucos; 'fim' roda na thread principal quando terminar. */
    public void executar(Plugin plugin, int porTique, Runnable fim) {
        Set<Long> cs = chunks();
        for (long c : cs) {
            mundo.addPluginChunkTicket((int) (c >> 32), (int) c, plugin);
        }
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                int limite = Math.min(total, i + porTique);
                for (; i < limite; i++) {
                    long k = posicoes[i];
                    mundo.getBlockAt(x(k), y(k), z(k)).setBlockData(paleta.get(blocos[i]), false);
                }
                if (i >= total) {
                    cancel();
                    for (long c : cs) {
                        mundo.removePluginChunkTicket((int) (c >> 32), (int) c, plugin);
                    }
                    if (fim != null) {
                        fim.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
