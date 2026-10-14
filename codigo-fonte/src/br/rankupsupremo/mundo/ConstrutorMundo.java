package br.rankupsupremo.mundo;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.mina.Mina;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Constrói automaticamente o spawn (ilha flutuante) e as estruturas das minas.
 */
public final class ConstrutorMundo {

    /** Centro fixo da estrutura do spawn. */
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Y = 100;
    public static final int SPAWN_Z = 0;
    private static final int RAIO_SPAWN = 18;

    private final RankupSupremo plugin;

    public ConstrutorMundo(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    // ------------------------------------------------------------------ SPAWN

    public void construirSpawn(World w) {
        final int cx = SPAWN_X;
        final int cy = SPAWN_Y;
        final int cz = SPAWN_Z;
        final int r = RAIO_SPAWN;
        Random rnd = new Random(20261012L);

        // Ilha flutuante por baixo (cone invertido irregular)
        for (int y = cy - 1; y >= cy - 14; y--) {
            double t = (double) (y - (cy - 15)) / 15.0; // 1 no topo -> 0 embaixo
            double raioCamada = r * Math.pow(t, 0.75);
            for (int x = -r - 1; x <= r + 1; x++) {
                for (int z = -r - 1; z <= r + 1; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    double irregular = raioCamada + (rnd.nextDouble() - 0.5) * 2.2;
                    if (d <= irregular) {
                        Material m;
                        int profundidade = cy - y;
                        if (profundidade == 1) {
                            m = Material.POLISHED_BLACKSTONE_BRICKS;
                        } else if (profundidade < 5) {
                            m = rnd.nextInt(4) == 0 ? Material.ANDESITE : Material.STONE;
                        } else if (profundidade < 9) {
                            m = rnd.nextInt(3) == 0 ? Material.TUFF : Material.DEEPSLATE;
                        } else {
                            m = rnd.nextInt(5) == 0 ? Material.BLACKSTONE : Material.DEEPSLATE;
                        }
                        set(w, cx + x, y, cz + z, m);
                    }
                }
            }
        }

        // Piso decorado
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.5) {
                    continue;
                }
                Material m;
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    m = Material.GOLD_BLOCK; // base do sinalizador
                } else if (d < 3.5) {
                    m = Material.SMOOTH_QUARTZ;
                } else if (d < 4.5) {
                    m = Material.GOLD_BLOCK;
                } else if (d < 5.5) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d < 10.5) {
                    m = (x == 0 || z == 0) ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ;
                } else if (d < 11.5) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d < 16.5) {
                    m = (x == 0 || z == 0) ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ;
                } else if (d < 17.5) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else {
                    m = Material.QUARTZ_BRICKS;
                }
                set(w, cx + x, cy, cz + z, m);
                // limpa o espaço acima (útil em reconstruções)
                for (int y = cy + 1; y <= cy + 8; y++) {
                    set(w, cx + x, y, cz + z, Material.AIR);
                }
            }
        }

        // Luzes no piso (anel de lanternas do mar)
        for (int ang = 0; ang < 360; ang += 30) {
            double rad = Math.toRadians(ang + 15);
            int lx = (int) Math.round(Math.cos(rad) * 8);
            int lz = (int) Math.round(Math.sin(rad) * 8);
            set(w, cx + lx, cy, cz + lz, Material.SEA_LANTERN);
            int lx2 = (int) Math.round(Math.cos(rad) * 14);
            int lz2 = (int) Math.round(Math.sin(rad) * 14);
            set(w, cx + lx2, cy, cz + lz2, Material.SEA_LANTERN);
        }

        // Borda de vidro (evita quedas)
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= r - 0.5 && d <= r + 0.5) {
                    set(w, cx + x, cy + 1, cz + z, Material.WHITE_STAINED_GLASS);
                }
            }
        }

        // Pilares com luz
        for (int ang = 0; ang < 360; ang += 45) {
            double rad = Math.toRadians(ang + 22.5);
            int px = (int) Math.round(Math.cos(rad) * (r - 2));
            int pz = (int) Math.round(Math.sin(rad) * (r - 2));
            set(w, cx + px, cy + 1, cz + pz, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int y = cy + 2; y <= cy + 4; y++) {
                set(w, cx + px, y, cz + pz, Material.QUARTZ_PILLAR);
            }
            set(w, cx + px, cy + 5, cz + pz, Material.GOLD_BLOCK);
            set(w, cx + px, cy + 6, cz + pz, Material.SEA_LANTERN);
        }

        // Sinalizador central com feixe dourado
        set(w, cx, cy + 1, cz, Material.BEACON);
        set(w, cx, cy + 2, cz, Material.YELLOW_STAINED_GLASS);

        w.setSpawnLocation(cx, cy + 1, cz + 8, 180f);
        plugin.getLogger().info("Spawn construído em " + cx + ", " + cy + ", " + cz + ".");
    }

    // ------------------------------------------------------------------ MINAS

    /** Cores e materiais de cada tema de mina. */
    private record Paleta(Material plataforma, Material borda, Material luz, Material acento, int alturaBorda) {
    }

    private Paleta paleta(Mina m) {
        FileConfiguration c = plugin.getConfig();
        return switch (m.tema()) {
            case CRISTAL -> new Paleta(Material.CALCITE, Material.PURPLE_STAINED_GLASS, Material.PEARLESCENT_FROGLIGHT,
                    Material.AMETHYST_BLOCK, 1);
            case DESERTO -> new Paleta(Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.GLOWSTONE,
                    Material.ORANGE_TERRACOTTA, 1);
            case CEU -> new Paleta(Material.GRASS_BLOCK, Material.WHITE_STAINED_GLASS, Material.SEA_LANTERN,
                    Material.MOSS_BLOCK, 1);
            case PVP -> new Paleta(Material.POLISHED_BLACKSTONE, Material.RED_NETHER_BRICKS, Material.SHROOMLIGHT,
                    Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, 2);
            case PRESTIGIO -> new Paleta(Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.SEA_LANTERN,
                    Material.END_STONE, 1);
            default -> {
                Material plataforma = bloco(c.getString("minas.estrutura.plataforma"), Material.POLISHED_ANDESITE);
                yield new Paleta(plataforma, bloco(c.getString("minas.estrutura.vidro"), Material.GLASS),
                        bloco(c.getString("minas.estrutura.luz"), Material.SEA_LANTERN), plataforma, 1);
            }
        };
    }

    public void construirMina(Mina m) {
        World w = m.mundo();
        FileConfiguration c = plugin.getConfig();
        Paleta p = paleta(m);
        Material plataforma = p.plataforma();
        Material vidro = p.borda();
        Material luz = p.luz();
        int largura = m.larguraPlataforma() > 0 ? m.larguraPlataforma()
                : Math.max(3, Math.min(12, c.getInt("minas.estrutura.largura-plataforma", 6)));

        int x0 = m.x0();
        int x1 = m.x1();
        int z0 = m.z0();
        int z1 = m.z1();
        int yb = m.yBase();
        int yt = m.yTopo();

        // Chão de bedrock
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                set(w, x, yb - 1, z, Material.BEDROCK);
            }
        }
        // Paredes
        for (int y = yb; y < yt; y++) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                set(w, x, y, z0 - 1, m.parede());
                set(w, x, y, z1 + 1, m.parede());
            }
            for (int z = z0; z <= z1; z++) {
                set(w, x0 - 1, y, z, m.parede());
                set(w, x1 + 1, y, z, m.parede());
            }
        }
        // Plataforma ao redor
        int ax0 = x0 - 1 - largura;
        int ax1 = x1 + 1 + largura;
        int az0 = z0 - 1 - largura;
        int az1 = z1 + 1 + largura;
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                    continue; // buraco da mina
                }
                int dist = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1)); // 1 = parede
                Material mat;
                if (dist == 1) {
                    mat = m.detalhe();
                } else if (dist == 2 && p.acento() != plataforma) {
                    mat = p.acento();
                } else if (dist == 3 && ((x + z) % 4 == 0)) {
                    mat = luz;
                } else if (dist == largura + 1) {
                    mat = m.parede();
                } else {
                    mat = plataforma;
                }
                set(w, x, yt, z, mat);
                set(w, x, yt - 1, z, m.parede()); // base da plataforma
                for (int y = yt + 1; y <= yt + 8; y++) {
                    set(w, x, y, z, Material.AIR);
                }
                if (dist == largura + 1) {
                    for (int y = 1; y <= p.alturaBorda(); y++) {
                        set(w, x, yt + y, z, vidro);
                    }
                }
            }
        }
        // Pilares iluminados nos cantos
        int[][] cantos = {{ax0, az0}, {ax0, az1}, {ax1, az0}, {ax1, az1}};
        for (int[] canto : cantos) {
            for (int y = yt + 1; y <= yt + 3; y++) {
                set(w, canto[0], y, canto[1], m.parede());
            }
            set(w, canto[0], yt + 4, canto[1], luz);
        }
        decorarTema(m, largura);
        plugin.getLogger().info("Mina '" + m.id() + "' construída em X=" + m.cx() + " Z=" + m.cz() + ".");
    }

    /** Pontos da plataforma num "anel" a uma distância fixa do buraco, de passo em passo. */
    private static List<int[]> anel(Mina m, int dist, int passo) {
        List<int[]> pontos = new ArrayList<>();
        int ax0 = m.x0() - dist;
        int ax1 = m.x1() + dist;
        int az0 = m.z0() - dist;
        int az1 = m.z1() + dist;
        int i = 0;
        for (int x = ax0; x <= ax1; x++) {
            if (i++ % passo == 0) {
                pontos.add(new int[]{x, az0});
                pontos.add(new int[]{x, az1});
            }
        }
        i = 0;
        for (int z = az0 + 1; z < az1; z++) {
            if (i++ % passo == 0) {
                pontos.add(new int[]{ax0, z});
                pontos.add(new int[]{ax1, z});
            }
        }
        return pontos;
    }

    /** Não decora em cima do ponto onde o jogador chega (frente da mina). */
    private static boolean pertoDaChegada(Mina m, int x, int z) {
        return Math.abs(x - m.cx()) <= 3 && z >= m.z1() + 1 && z <= m.z1() + 8;
    }

    private void decorarTema(Mina m, int largura) {
        World w = m.mundo();
        int yt = m.yTopo();
        Random rnd = new Random(m.id().hashCode());
        switch (m.tema()) {
            case CRISTAL -> {
                // Cristais de ametista crescendo da plataforma
                for (int[] pt : anel(m, largura, 5)) {
                    if (pertoDaChegada(m, pt[0], pt[1])) {
                        continue;
                    }
                    int altura = 1 + rnd.nextInt(3);
                    for (int y = 1; y <= altura; y++) {
                        set(w, pt[0], yt + y, pt[1], Material.AMETHYST_BLOCK);
                    }
                    set(w, pt[0], yt + altura + 1, pt[1], Material.AMETHYST_CLUSTER);
                }
            }
            case DESERTO -> {
                // Pilares de templo com lanternas
                for (int[] pt : anel(m, largura, 6)) {
                    if (pertoDaChegada(m, pt[0], pt[1])) {
                        continue;
                    }
                    set(w, pt[0], yt + 1, pt[1], Material.CHISELED_SANDSTONE);
                    for (int y = 2; y <= 4; y++) {
                        set(w, pt[0], yt + y, pt[1], Material.CUT_SANDSTONE);
                    }
                    set(w, pt[0], yt + 5, pt[1], Material.CHISELED_SANDSTONE);
                    set(w, pt[0], yt + 6, pt[1], Material.LANTERN);
                }
            }
            case CEU -> {
                // Ilha flutuante por baixo da plataforma
                int raioMax = m.tamanho() / 2 + largura + 1;
                for (int camada = 2; camada <= 14; camada++) {
                    double fator = 1.0 - (camada - 2) / 14.0;
                    int raio = (int) Math.round(raioMax * Math.pow(fator, 0.8));
                    for (int dx = -raio; dx <= raio; dx++) {
                        for (int dz = -raio; dz <= raio; dz++) {
                            if (dx * dx + dz * dz > raio * raio + rnd.nextInt(Math.max(1, raio))) {
                                continue;
                            }
                            int x = m.cx() + dx;
                            int z = m.cz() + dz;
                            if (w.getBlockAt(x, yt - camada, z).getType().isAir()) {
                                set(w, x, yt - camada, z, camada < 4 ? Material.DIRT : (rnd.nextInt(4) == 0 ? Material.ANDESITE : Material.STONE));
                            }
                        }
                    }
                }
                // Flores e grama na plataforma
                for (int x = m.x0() - largura; x <= m.x1() + largura; x++) {
                    for (int z = m.z0() - largura; z <= m.z1() + largura; z++) {
                        if (w.getBlockAt(x, yt, z).getType() == Material.GRASS_BLOCK && !pertoDaChegada(m, x, z) && rnd.nextInt(9) == 0) {
                            Material[] plantas = {Material.SHORT_GRASS, Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.CORNFLOWER};
                            set(w, x, yt + 1, z, plantas[rnd.nextInt(plantas.length)]);
                        }
                    }
                }
                // Nuvens em volta
                int distNuvem = m.tamanho() / 2 + largura + 8;
                for (int i = 0; i < 10; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    int ncx = m.cx() + (int) (Math.cos(ang) * (distNuvem + rnd.nextInt(12)));
                    int ncz = m.cz() + (int) (Math.sin(ang) * (distNuvem + rnd.nextInt(12)));
                    int ncy = yt - 6 + rnd.nextInt(20);
                    int rx = 3 + rnd.nextInt(3);
                    int rz = 2 + rnd.nextInt(3);
                    for (int dx = -rx; dx <= rx; dx++) {
                        for (int dz = -rz; dz <= rz; dz++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                double d = (dx * dx) / (double) (rx * rx) + (dz * dz) / (double) (rz * rz) + (dy * dy) / 2.0;
                                if (d <= 1.0 && rnd.nextInt(10) != 0) {
                                    set(w, ncx + dx, ncy + dy, ncz + dz, Material.WHITE_WOOL);
                                }
                            }
                        }
                    }
                }
            }
            case PVP -> {
                // Ruínas que servem de cobertura
                Material[] ruina = {Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.COBBLESTONE,
                        Material.CRACKED_POLISHED_BLACKSTONE_BRICKS};
                for (int i = 0; i < 14; i++) {
                    int dist = 3 + rnd.nextInt(Math.max(1, largura - 3));
                    List<int[]> pts = anel(m, dist, 1);
                    int[] pt = pts.get(rnd.nextInt(pts.size()));
                    if (pertoDaChegada(m, pt[0], pt[1])) {
                        continue;
                    }
                    boolean eixoX = rnd.nextBoolean();
                    int comprimento = 2 + rnd.nextInt(3);
                    for (int k = 0; k < comprimento; k++) {
                        int x = pt[0] + (eixoX ? k : 0);
                        int z = pt[1] + (eixoX ? 0 : k);
                        if (m.contem(w, x, yt, z) || pertoDaChegada(m, x, z)) {
                            continue;
                        }
                        int altura = 1 + rnd.nextInt(2);
                        for (int y = 1; y <= altura; y++) {
                            set(w, x, yt + y, z, ruina[rnd.nextInt(ruina.length)]);
                        }
                    }
                }
            }
            case PRESTIGIO -> {
                for (int[] pt : anel(m, largura, 5)) {
                    if (pertoDaChegada(m, pt[0], pt[1])) {
                        continue;
                    }
                    set(w, pt[0], yt + 1, pt[1], Material.PURPUR_PILLAR);
                    set(w, pt[0], yt + 2, pt[1], Material.PURPUR_PILLAR);
                    set(w, pt[0], yt + 3, pt[1], Material.END_ROD);
                }
            }
            default -> {
            }
        }
    }

    /** Limpa a área ao redor de uma mina em lotes (usado em /rankupadmin reconstruir). */
    public void limparAreaMina(Mina m, Runnable aoTerminar) {
        final World w = m.mundo();
        final int minX = m.cx() - 60;
        final int maxX = m.cx() + 60;
        final int minZ = m.cz() - 60;
        final int maxZ = m.cz() + 60;
        final int minY = Math.max(w.getMinHeight(), m.yTopo() - 85);
        final int maxY = Math.min(w.getMaxHeight() - 1, m.yTopo() + 22);
        new BukkitRunnable() {
            int x = minX;

            @Override
            public void run() {
                int colunas = 0;
                while (x <= maxX && colunas < 4) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            if (!w.getBlockAt(x, y, z).getType().isAir()) {
                                set(w, x, y, z, Material.AIR);
                            }
                        }
                    }
                    x++;
                    colunas++;
                }
                if (x > maxX) {
                    cancel();
                    aoTerminar.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Material bloco(String nome, Material padrao) {
        Material m = nome == null ? null : Material.matchMaterial(nome);
        return m == null || !m.isBlock() || m.isAir() ? padrao : m;
    }
}
