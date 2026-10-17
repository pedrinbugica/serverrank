package dev.pedrobugica.survivalrpg.construcao;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Random;

/**
 * Casas prontas do próprio jogo (as das vilas) colocadas num lote: acha a porta no modelo, gira a casa para a
 * porta ficar virada para a rua e encaixa a frente na beira do lote. Blocos-quebra-cabeça (jigsaw) somem e o
 * ar do andar de baixo não abre buracos no chão.
 */
public final class Modelos {

    private static final BlockFace[] ORDEM = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /** Lote: centro da beira da frente (fx, fz), para onde a porta vira, largura (ao longo da rua) e profundidade. */
    public record Lote(int fx, int fz, BlockFace virado, int largura, int profundidade) {
    }

    /** Resultado: onde a porta ficou (para pôr placa, NPC, caminho). */
    public record Colocado(String modelo, int portaX, int portaY, int portaZ, int x1, int z1, int x2, int z2) {
    }

    private record Analise(Structure estrutura, int sx, int sy, int sz, int px, int py, int pz, BlockFace frente) {
    }

    private Modelos() {
    }

    private static Analise analisar(String id) {
        NamespacedKey k = NamespacedKey.fromString(id);
        if (k == null) {
            return null;
        }
        Structure s;
        try {
            s = Bukkit.getStructureManager().loadStructure(k);
        } catch (RuntimeException e) {
            return null;
        }
        if (s == null || s.getPalettes().isEmpty()) {
            return null;
        }
        BlockVector t = s.getSize();
        int sx = t.getBlockX();
        int sy = t.getBlockY();
        int sz = t.getBlockZ();
        int melhor = Integer.MAX_VALUE;
        int px = sx / 2;
        int py = 1;
        int pz = 0;
        for (BlockState b : s.getPalettes().get(0).getBlocks()) {
            if (b.getBlockData() instanceof Door d && d.getHalf() == Bisected.Half.BOTTOM) {
                int borda = Math.min(Math.min(b.getX(), sx - 1 - b.getX()), Math.min(b.getZ(), sz - 1 - b.getZ()));
                int nota = borda * 100 + b.getY();
                if (nota < melhor) {
                    melhor = nota;
                    px = b.getX();
                    py = b.getY();
                    pz = b.getZ();
                }
            }
        }
        BlockFace frente;
        int dn = pz;
        int ds = sz - 1 - pz;
        int dw = px;
        int de = sx - 1 - px;
        int m = Math.min(Math.min(dn, ds), Math.min(dw, de));
        if (m == dn) {
            frente = BlockFace.NORTH;
        } else if (m == ds) {
            frente = BlockFace.SOUTH;
        } else if (m == dw) {
            frente = BlockFace.WEST;
        } else {
            frente = BlockFace.EAST;
        }
        return new Analise(s, sx, sy, sz, px, py, pz, frente);
    }

    private static int indice(BlockFace f) {
        for (int i = 0; i < ORDEM.length; i++) {
            if (ORDEM[i] == f) {
                return i;
            }
        }
        return 0;
    }

    /** Gira (x, z) k vezes 90° no sentido horário, como o jogo faz ao colocar estruturas. */
    private static int[] girar(int x, int z, int k) {
        return switch (k & 3) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    /**
     * Tenta colocar o modelo no lote. 'chao' = altura do piso da rua (a porta fica em chao+1).
     * 'limite' diz se um ponto (x, z) do mundo pode ser ocupado (ex.: dentro da muralha).
     */
    public static Colocado colocar(World w, String id, Lote lote, int chao, java.util.function.BiPredicate<Integer, Integer> limite, Random rnd) {
        Analise a = analisar(id);
        if (a == null) {
            return null;
        }
        int k = (indice(lote.virado()) - indice(a.frente()) + 4) & 3;
        int[] porta = girar(a.px(), a.pz(), k);
        int[][] cantos = {girar(0, 0, k), girar(a.sx() - 1, 0, k), girar(0, a.sz() - 1, k), girar(a.sx() - 1, a.sz() - 1, k)};
        int dx = lote.virado().getModX();
        int dz = lote.virado().getModZ();
        int ox;
        int oz;
        if (dx != 0) {
            int max = Integer.MIN_VALUE;
            for (int[] c : cantos) {
                max = Math.max(max, c[0] * dx);
            }
            ox = lote.fx() - max * dx;
            oz = lote.fz() - porta[1];
        } else {
            int max = Integer.MIN_VALUE;
            for (int[] c : cantos) {
                max = Math.max(max, c[1] * dz);
            }
            oz = lote.fz() - max * dz;
            ox = lote.fx() - porta[0];
        }
        int x1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int z1 = Integer.MAX_VALUE;
        int z2 = Integer.MIN_VALUE;
        for (int[] c : cantos) {
            x1 = Math.min(x1, ox + c[0]);
            x2 = Math.max(x2, ox + c[0]);
            z1 = Math.min(z1, oz + c[1]);
            z2 = Math.max(z2, oz + c[1]);
        }
        // Cabe no lote?
        int lx1;
        int lx2;
        int lz1;
        int lz2;
        if (dx != 0) {
            lx1 = dx > 0 ? lote.fx() - (lote.profundidade() - 1) : lote.fx();
            lx2 = dx > 0 ? lote.fx() : lote.fx() + (lote.profundidade() - 1);
            lz1 = lote.fz() - (lote.largura() - 1) / 2;
            lz2 = lote.fz() + lote.largura() / 2;
        } else {
            lz1 = dz > 0 ? lote.fz() - (lote.profundidade() - 1) : lote.fz();
            lz2 = dz > 0 ? lote.fz() : lote.fz() + (lote.profundidade() - 1);
            lx1 = lote.fx() - (lote.largura() - 1) / 2;
            lx2 = lote.fx() + lote.largura() / 2;
        }
        if (x1 < lx1 || x2 > lx2 || z1 < lz1 || z2 > lz2) {
            return null;
        }
        if (!limite.test(x1, z1) || !limite.test(x1, z2) || !limite.test(x2, z1) || !limite.test(x2, z2)) {
            return null;
        }
        int oy = chao + 1 - a.py();
        StructureRotation rot = switch (k) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        final int base = chao;
        BlockTransformer limpar = (regiao, x, y, z, atual, estado) -> {
            Material m = atual.getType();
            if (m == Material.JIGSAW || m == Material.STRUCTURE_BLOCK) {
                return estado.getWorld();
            }
            if (m.isAir() && y <= base) {
                return estado.getWorld();
            }
            return atual;
        };
        a.estrutura().place(new Location(w, ox, oy, oz), false, rot, Mirror.NONE, 0, 1.0f, rnd, List.of(limpar), List.of());
        return new Colocado(id, ox + porta[0], chao + 1, oz + porta[1], x1, z1, x2, z2);
    }
}
