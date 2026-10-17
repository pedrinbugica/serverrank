package dev.pedrobugica.survivalrpg.masmorra;

import dev.pedrobugica.survivalrpg.construcao.Obra;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A planta de uma masmorra: entrada, três salas de combate e a arena do chefe, em linha, ligadas por corredores
 * com portões de grade. Tudo fechado (chão, paredes e teto), construído no alto, longe de tudo.
 */
public final class Planta {

    public enum Estado {
        ESPERANDO, LUTANDO, LIMPA
    }

    /** Uma sala: o miolo (onde se anda) e os blocos dos portões de entrada e saída. */
    public static final class Sala {
        public final int x1;
        public final int y1;
        public final int z1;
        public final int x2;
        public final int y2;
        public final int z2;
        public final boolean entrada;
        public final boolean chefe;
        public final int ondas;
        public final List<int[]> portaoEntrada = new ArrayList<>();
        public final List<int[]> portaoSaida = new ArrayList<>();
        // estado da corrida (muda durante o jogo)
        public Estado estado = Estado.ESPERANDO;
        public final List<UUID> mobs = new ArrayList<>();
        public int ondaAtual;
        public long vaziaDesde;

        Sala(int x1, int y1, int z1, int x2, int y2, int z2, boolean entrada, boolean chefe, int ondas) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.entrada = entrada;
            this.chefe = chefe;
            this.ondas = ondas;
        }

        public boolean contem(Location l) {
            return l.getX() >= x1 && l.getX() < x2 + 1 && l.getZ() >= z1 && l.getZ() < z2 + 1 && l.getY() >= y1 - 1 && l.getY() < y2 + 1;
        }

        public double centroX() {
            return (x1 + x2 + 1) / 2.0;
        }

        public double centroZ() {
            return (z1 + z2 + 1) / 2.0;
        }

        public void reiniciar() {
            estado = entrada ? Estado.LIMPA : Estado.ESPERANDO;
            mobs.clear();
            ondaAtual = 0;
            vaziaDesde = 0;
        }
    }

    private static final int[][] SALAS = {
            // largura (x), profundidade (z), altura, ondas
            {9, 9, 6, 0},
            {15, 15, 8, 1},
            {17, 13, 8, 2},
            {15, 15, 9, 2},
            {25, 25, 12, 0},
    };
    private static final int CORREDOR = 7;

    private Planta() {
    }

    /** Comprimento total em x (para espaçar as instâncias). */
    public static int comprimento() {
        int c = 0;
        for (int[] s : SALAS) {
            c += s[0] + 2 + CORREDOR;
        }
        return c;
    }

    /** Desenha a masmorra na obra e devolve as salas. (ox, oy, oz) = canto oeste, altura do chão, linha do meio. */
    public static List<Sala> desenhar(Obra o, Tema t, int ox, int oy, int oz, Random rnd) {
        List<Sala> salas = new ArrayList<>();
        int cursor = ox;
        for (int i = 0; i < SALAS.length; i++) {
            int w = SALAS[i][0];
            int d = SALAS[i][1];
            int alt = SALAS[i][2];
            int xa = cursor;
            int xb = cursor + w + 1;
            int za = oz - d / 2 - 1;
            int zb = oz + d / 2 + 1;
            boolean chefe = i == SALAS.length - 1;
            Sala s = new Sala(xa + 1, oy + 1, za + 1, xb - 1, oy + alt, zb - 1, i == 0, chefe, SALAS[i][3]);
            // casca: chão, paredes e teto
            for (int x = xa; x <= xb; x++) {
                for (int z = za; z <= zb; z++) {
                    boolean borda = x == xa || x == xb || z == za || z == zb;
                    o.set(x, oy, z, borda ? t.parede(rnd) : t.chao(rnd));
                    o.set(x, oy - 1, z, t.parede(rnd));
                    o.set(x, oy + alt + 1, z, t.teto());
                    for (int y = oy + 1; y <= oy + alt; y++) {
                        o.set(x, y, z, borda ? t.parede(rnd) : Material.AIR);
                    }
                }
            }
            // pilares e luz
            if (w >= 13) {
                int[][] cantos = {{xa + 3, za + 3}, {xa + 3, zb - 3}, {xb - 3, za + 3}, {xb - 3, zb - 3}};
                for (int[] c : cantos) {
                    for (int y = oy + 1; y <= oy + alt; y++) {
                        o.set(c[0], y, c[1], t.pilar());
                    }
                }
            }
            if (chefe) {
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI / 4 * k;
                    int px = (int) Math.round(s.centroX() - 0.5 + Math.cos(a) * 8);
                    int pz = (int) Math.round(s.centroZ() - 0.5 + Math.sin(a) * 8);
                    for (int y = oy + 1; y <= oy + alt; y++) {
                        o.set(px, y, pz, t.pilar());
                    }
                    o.set(px, oy + alt - 2, pz, Material.SHROOMLIGHT);
                }
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        o.set((int) s.centroX() + x, oy, (int) s.centroZ() + z, t.pilar());
                    }
                }
            }
            for (int x = xa + 2; x <= xb - 2; x += 4) {
                for (int z = za + 2; z <= zb - 2; z += 4) {
                    o.set(x, oy + alt, z, t.luz());
                }
            }
            // enfeites encostados nas paredes
            int enfeites = 4 + w / 3;
            for (int k = 0; k < enfeites; k++) {
                boolean ladoX = rnd.nextBoolean();
                int x = ladoX ? (rnd.nextBoolean() ? xa + 1 : xb - 1) : xa + 2 + rnd.nextInt(Math.max(1, w - 3));
                int z = ladoX ? za + 2 + rnd.nextInt(Math.max(1, d - 3)) : (rnd.nextBoolean() ? za + 1 : zb - 1);
                if (Math.abs(z - oz) <= 2) {
                    continue; // não tampa as passagens
                }
                Material m = t.enfeite(rnd);
                if (m == Material.CANDLE) {
                    o.set(x, oy + 1, z, "minecraft:candle[lit=true,candles=3]");
                } else if (m == Material.COBWEB) {
                    o.set(x, oy + alt, z, m);
                } else if (m == Material.IRON_CHAIN) {
                    for (int y = oy + alt - 2; y <= oy + alt; y++) {
                        o.set(x, y, z, m);
                    }
                } else {
                    o.set(x, oy + 1, z, m);
                }
            }
            // passagem na parede oeste (vinda do corredor) e leste (para o próximo)
            if (i > 0) {
                for (int z = oz - 1; z <= oz + 1; z++) {
                    for (int y = oy + 1; y <= oy + 4; y++) {
                        o.set(xa, y, z, Material.AIR);
                        s.portaoEntrada.add(new int[]{xa, y, z});
                    }
                }
            }
            if (i < SALAS.length - 1) {
                for (int z = oz - 1; z <= oz + 1; z++) {
                    for (int y = oy + 1; y <= oy + 4; y++) {
                        o.set(xb, y, z, Material.AIR);
                        s.portaoSaida.add(new int[]{xb, y, z});
                    }
                }
                // corredor
                for (int x = xb + 1; x <= xb + CORREDOR; x++) {
                    for (int z = oz - 2; z <= oz + 2; z++) {
                        boolean borda = z == oz - 2 || z == oz + 2;
                        o.set(x, oy, z, borda ? t.parede(rnd) : t.chao(rnd));
                        o.set(x, oy - 1, z, t.parede(rnd));
                        o.set(x, oy + 5, z, t.teto());
                        for (int y = oy + 1; y <= oy + 4; y++) {
                            o.set(x, y, z, borda ? t.parede(rnd) : Material.AIR);
                        }
                    }
                    if ((x - xb) % 3 == 0) {
                        o.set(x, oy + 4, oz, t.luz());
                    }
                }
            }
            salas.add(s);
            cursor = xb + CORREDOR + 1;
        }
        return salas;
    }
}
