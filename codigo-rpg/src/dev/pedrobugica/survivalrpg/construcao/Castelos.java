package dev.pedrobugica.survivalrpg.construcao;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.hook.NpcHook;
import dev.pedrobugica.survivalrpg.mundo.Lugar;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Os castelos dos reinos do NpcVivos (Valdoria, Corvanor, Eldoria...): cada um num ponto do mapa, longe da capital e
 * das bases dos jogadores. Muralha quadrada com quatro torres, portão com grade, pátio com quartel, estábulo, poço e
 * bonecos de treino, e a fortaleza com a sala do trono (onde o rei/rainha fica). As bandeiras, tapetes e lã são da
 * cor do reino: quando o castelo é conquistado, o NpcVivos pinta tudo com a cor do novo dono.
 * Antes de construir tira uma foto do terreno (/rpgadmin desfazer reino_&lt;id&gt;).
 */
public final class Castelos {

    /** Meia largura da muralha (o castelo ocupa 51x51) e do terreno nivelado em volta. */
    public static final int MURALHA = 25;
    private static final int PLANO = 31;
    private static final int TOTAL = 40;

    private final SurvivalRpgPlugin plugin;
    private boolean construindo;

    public Castelos(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean construindo() {
        return construindo;
    }

    public static String idLugar(String reino) {
        return "reino_" + reino.toLowerCase(Locale.ROOT);
    }

    /** Constrói, um de cada vez, os castelos dos reinos que ainda não têm. */
    public void construirFaltando(CommandSender quem, Runnable fim) {
        if (construindo) {
            plugin.mensagens().enviar(quem, "lugares-ja-populando");
            return;
        }
        World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
        List<NpcHook.InfoReino> reinos = plugin.npcHook().reinos();
        if (w == null || reinos.isEmpty()) {
            if (fim != null) {
                fim.run();
            }
            return;
        }
        Deque<NpcHook.InfoReino> fila = new ArrayDeque<>();
        for (NpcHook.InfoReino r : reinos) {
            if (plugin.lugares().obter(idLugar(r.id())) == null) {
                fila.add(r);
            }
        }
        if (fila.isEmpty()) {
            if (fim != null) {
                fim.run();
            }
            return;
        }
        construindo = true;
        double base = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        int total = reinos.size();
        quem.sendMessage(Texto.cor("<gold>[RPG] <yellow>Construindo " + fila.size() + " castelo(s) de reino pelo mapa..."));
        proximo(w, quem, fila, base, total, 0, 0, fim);
    }

    private void proximo(World w, CommandSender quem, Deque<NpcHook.InfoReino> fila, double base, int total, int indice, int tentativa, Runnable fim) {
        NpcHook.InfoReino r = fila.peek();
        if (r == null) {
            construindo = false;
            quem.sendMessage(Texto.cor("<gold>[RPG] <green>Castelos dos reinos prontos."));
            if (fim != null) {
                fim.run();
            }
            return;
        }
        int maxTentativas = plugin.getConfig().getInt("reinos.tentativas", 50);
        if (tentativa >= maxTentativas) {
            plugin.getLogger().warning("Não achei lugar bom para o castelo de " + r.id() + " (desisti depois de " + tentativa + " tentativas).");
            fila.poll();
            Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, base, total, indice + 1, 0, fim), 2L);
            return;
        }
        Runnable tentarDeNovo = () -> Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, base, total, indice, tentativa + 1, fim), 1L);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Integer> anel = plugin.getConfig().getIntegerList("reinos.anel");
        double min = anel.isEmpty() ? 1100 : anel.get(0);
        double max = anel.size() < 2 ? 1700 : anel.get(1);
        // os reinos ficam espalhados em volta da capital (um em cada direção)
        int ordem = plugin.npcHook().reinos().indexOf(r);
        double ang = base + Math.max(0, ordem) * (Math.PI * 2 / Math.max(1, total)) + rnd.nextDouble(-0.35, 0.35)
                + (tentativa > 25 ? rnd.nextDouble(-0.9, 0.9) : 0);
        double dist = rnd.nextDouble(min, max);
        Location centro = plugin.regioes().centro(w);
        int x = (int) Math.round(centro.getX() + Math.cos(ang) * dist);
        int z = (int) Math.round(centro.getZ() + Math.sin(ang) * dist);
        double minimoLugares = plugin.getConfig().getDouble("reinos.distancia-de-lugares", 220);
        for (Lugar l : plugin.lugares().todos()) {
            if (l.mundo().equals(w.getName()) && Math.hypot(l.x() - x, l.z() - z) < minimoLugares) {
                tentarDeNovo.run();
                return;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(w) && Math.hypot(p.getLocation().getX() - x, p.getLocation().getZ() - z) < 120) {
                tentarDeNovo.run();
                return;
            }
        }
        Terreno.carregar(w, x - TOTAL, z - TOTAL, x + TOTAL, z + TOTAL).thenAccept(t -> {
            int agua = 0;
            int minChao = Integer.MAX_VALUE;
            int maxChao = Integer.MIN_VALUE;
            int[] alturas = new int[121];
            int n = 0;
            for (int dx = -PLANO; dx <= PLANO; dx += 6) {
                for (int dz = -PLANO; dz <= PLANO; dz += 6) {
                    int c = t.chao(x + dx, z + dz);
                    if (n < alturas.length) {
                        alturas[n++] = c;
                    }
                    minChao = Math.min(minChao, c);
                    maxChao = Math.max(maxChao, c);
                    if (t.agua(x + dx, z + dz)) {
                        agua++;
                    }
                }
            }
            Biome bioma = t.bioma(x, t.chao(x, z) + 1, z);
            String b = bioma == null ? "" : bioma.getKey().getKey();
            if (agua > 4 || maxChao - minChao > 18 || b.contains("ocean") || b.contains("river") || b.contains("swamp")
                    || t.blocosDeJogador(x, z, TOTAL, java.util.Set.of()) > 0) {
                tentarDeNovo.run();
                return;
            }
            java.util.Arrays.sort(alturas, 0, n);
            int h = alturas[n / 2];
            try {
                new Plano(w, t, x, z, h, r).construir(() -> {
                    fila.poll();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, base, total, indice + 1, 0, fim), 60L);
                });
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao construir o castelo de " + r.id(), e);
                tentarDeNovo.run();
            }
        });
    }

    // ------------------------------------------------------------------ O PLANO

    private final class Plano {
        final World w;
        final Terreno t;
        final int cx;
        final int cz;
        final int h;
        final NpcHook.InfoReino reino;
        final String cor;
        final Obra o;
        final Random rnd;
        final java.util.Set<String> avisados = new java.util.HashSet<>();

        Plano(World w, Terreno t, int cx, int cz, int h, NpcHook.InfoReino reino) {
            this.w = w;
            this.t = t;
            this.cx = cx;
            this.cz = cz;
            this.h = h;
            this.reino = reino;
            this.cor = reino.bandeira().toLowerCase(Locale.ROOT);
            this.o = new Obra(w);
            this.rnd = new Random(reino.id().hashCode() * 31L + cx);
        }

        void set(int dx, int y, int dz, Material m) {
            o.set(cx + dx, y, cz + dz, m);
        }

        void set(int dx, int y, int dz, String dados) {
            try {
                o.set(cx + dx, y, cz + dz, dados);
            } catch (IllegalArgumentException e) {
                // bloco que não existe nesta versão do jogo: pula só ele, o castelo continua
                if (avisados.add(dados)) {
                    plugin.getLogger().warning("Bloco desconhecido no castelo (ignorado): " + dados);
                }
            }
        }

        Material pedra() {
            int r = rnd.nextInt(100);
            return r < 70 ? Material.STONE_BRICKS : r < 84 ? Material.MOSSY_STONE_BRICKS : r < 95 ? Material.CRACKED_STONE_BRICKS : Material.ANDESITE;
        }

        void construir(Runnable depois) {
            int minChao = Integer.MAX_VALUE;
            int maxTopo = Integer.MIN_VALUE;
            for (int dx = -TOTAL; dx <= TOTAL; dx++) {
                for (int dz = -TOTAL; dz <= TOTAL; dz++) {
                    minChao = Math.min(minChao, t.chao(cx + dx, cz + dz));
                    maxTopo = Math.max(maxTopo, t.topo(cx + dx, cz + dz));
                }
            }
            String id = idLugar(reino.id());
            Instantaneo foto = Instantaneo.tirar(t, cx - TOTAL, Math.min(minChao, h) - 4, cz - TOTAL, cx + TOTAL, Math.max(maxTopo, h + 32) + 2, cz + TOTAL);
            plugin.lugares().gravarFoto(id, foto);
            terreno();
            muralha();
            torres();
            portao();
            patio();
            fortaleza();
            quartel();
            estabulo();
            treino();
            plugin.getLogger().info("Construindo o castelo de " + reino.id() + " em " + cx + ", " + h + ", " + cz + " (" + o.tamanho() + " blocos)...");
            o.executar(plugin, plugin.getConfig().getInt("capital.blocos-por-tique", 6000), () -> {
                String nome = reino.cor() + "Castelo de " + reino.curto();
                Lugar l = new Lugar(id, nome, Lugar.Tipo.REINO, w.getName(), cx, h + 1, cz, 0, true, "castelo-real");
                plugin.lugares().registrar(l);
                Location centro = new Location(w, cx + 0.5, h + 1, cz + 6.5);
                Location trono = new Location(w, cx + 0.5, h + 2, cz - 18.5, 0f, 0f);
                plugin.npcHook().registrarCastelo(reino.id(), nome, centro, MURALHA, trono);
                plugin.getLogger().info("Castelo de " + reino.id() + " pronto.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("survivalrpg.admin")) {
                        p.sendMessage(Texto.cor("<gold>[RPG] <green>" + nome + " <green>construído em <white>" + cx + ", " + cz));
                    }
                }
                depois.run();
            });
        }

        // ---------------------------------------------------------- TERRENO

        void terreno() {
            for (int dx = -TOTAL; dx <= TOTAL; dx++) {
                for (int dz = -TOTAL; dz <= TOTAL; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    // distância "quadrada arredondada" (o castelo é quadrado)
                    double r = Math.max(Math.abs(dx), Math.abs(dz)) * 0.8 + Math.hypot(dx, dz) * 0.2;
                    int chao = t.chao(x, z);
                    int topo = t.topo(x, z);
                    int alvo;
                    if (r <= PLANO) {
                        alvo = h;
                    } else {
                        double f = Math.min(1, (r - PLANO) / (TOTAL - PLANO));
                        f = f * f * (3 - 2 * f);
                        alvo = (int) Math.round(h + (chao - h) * f);
                    }
                    if (r > PLANO && alvo == chao) {
                        continue;
                    }
                    for (int y = chao + 1; y < alvo; y++) {
                        o.set(x, y, z, y < alvo - 3 ? Material.STONE : Material.DIRT);
                    }
                    if (r <= PLANO || alvo < chao) {
                        for (int y = alvo + 1; y <= Math.max(topo, alvo + 1); y++) {
                            o.set(x, y, z, Material.AIR);
                        }
                        if (r <= PLANO) {
                            for (int y = Math.max(topo, alvo) + 1; y <= h + 30; y++) {
                                o.set(x, y, z, Material.AIR); // árvores e morros por cima
                            }
                        }
                    }
                    o.set(x, alvo, z, Material.GRASS_BLOCK);
                    o.set(x, alvo - 1, z, Material.DIRT);
                    o.set(x, alvo - 2, z, Material.DIRT);
                }
            }
        }

        // ---------------------------------------------------------- MURALHA

        void muralha() {
            for (int a = -MURALHA; a <= MURALHA; a++) {
                for (int espessura = 0; espessura < 3; espessura++) {
                    int d = MURALHA - espessura;
                    int[][] lados = {{a, d}, {a, -d}, {d, a}, {-d, a}};
                    for (int[] p : lados) {
                        if (Math.abs(p[0]) > MURALHA || Math.abs(p[1]) > MURALHA) {
                            continue;
                        }
                        for (int y = h - 2; y <= h + 8; y++) {
                            set(p[0], y, p[1], y == h + 8 ? Material.STONE_BRICKS : pedra());
                        }
                    }
                }
                // ameias na borda de fora
                if ((a & 1) == 0) {
                    set(a, h + 9, MURALHA, pedra());
                    set(a, h + 9, -MURALHA, pedra());
                    set(MURALHA, h + 9, a, pedra());
                    set(-MURALHA, h + 9, a, pedra());
                }
                // lanternas no caminho de ronda
                if (Math.floorMod(a, 8) == 0 && Math.abs(a) < MURALHA - 4) {
                    set(a, h + 9, MURALHA - 2, "minecraft:lantern[hanging=false]");
                    set(a, h + 9, -MURALHA + 2, "minecraft:lantern[hanging=false]");
                    set(MURALHA - 2, h + 9, a, "minecraft:lantern[hanging=false]");
                    set(-MURALHA + 2, h + 9, a, "minecraft:lantern[hanging=false]");
                }
            }
            // escadas de pedra subindo para a muralha (dentro, nos 4 lados)
            for (int i = 0; i < 8; i++) {
                int y = h + 1 + i;
                for (int l = 0; l < 2; l++) {
                    set(-17 + i, y, MURALHA - 3 - l, "minecraft:stone_brick_stairs[facing=east]");
                    set(17 - i, y, -MURALHA + 3 + l, "minecraft:stone_brick_stairs[facing=west]");
                    set(MURALHA - 3 - l, y, -10 + i, "minecraft:stone_brick_stairs[facing=south]");
                    set(-MURALHA + 3 + l, y, 10 - i, "minecraft:stone_brick_stairs[facing=north]");
                }
            }
        }

        void torres() {
            int[][] cantos = {{MURALHA - 1, MURALHA - 1}, {-MURALHA + 1, MURALHA - 1}, {MURALHA - 1, -MURALHA + 1}, {-MURALHA + 1, -MURALHA + 1}};
            for (int[] c : cantos) {
                torre(c[0], c[1], 5, h + 14);
            }
        }

        /** Torre redonda: parede, piso na altura da muralha e no topo, ameias e bandeira. */
        void torre(int tx, int tz, int raio, int topo) {
            for (int dx = -raio; dx <= raio; dx++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r > raio + 0.5) {
                        continue;
                    }
                    boolean parede = r > raio - 0.7;
                    for (int y = h - 2; y <= topo; y++) {
                        if (parede) {
                            boolean janela = (y == h + 4 || y == h + 11) && (dx == 0 || dz == 0);
                            set(tx + dx, y, tz + dz, janela ? Material.AIR : pedra());
                        } else if (y == h || y == h + 8 || y == topo) {
                            set(tx + dx, y, tz + dz, y == topo ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS);
                        } else if (y > h) {
                            set(tx + dx, y, tz + dz, Material.AIR);
                        }
                    }
                    if (parede && ((dx + dz) & 1) == 0) {
                        set(tx + dx, topo + 1, tz + dz, pedra());
                    }
                }
            }
            // escada de mão por dentro
            for (int y = h + 1; y <= topo; y++) {
                set(tx, y, tz + raio - 1, "minecraft:ladder[facing=north]");
            }
            set(tx, h + 8, tz + raio - 1, "minecraft:ladder[facing=north]");
            set(tx, topo, tz + raio - 1, "minecraft:ladder[facing=north]");
            // passagem para o caminho de ronda
            for (int y = h + 9; y <= h + 10; y++) {
                set(tx - Integer.signum(tx) * raio, y, tz, Material.AIR);
                set(tx, y, tz - Integer.signum(tz) * raio, Material.AIR);
            }
            // mastro com bandeira
            for (int y = topo + 1; y <= topo + 4; y++) {
                set(tx, y, tz, Material.SPRUCE_FENCE);
            }
            set(tx, topo + 5, tz, "minecraft:" + cor + "_banner[rotation=" + (tz > 0 ? 0 : 8) + "]");
            set(tx, topo - 1, tz, "minecraft:lantern[hanging=true]");
            // bandeiras de parede para fora
            set(tx + Integer.signum(tx) * (raio + 1), h + 10, tz, "minecraft:" + cor + "_wall_banner[facing=" + (tx > 0 ? "east" : "west") + "]");
            set(tx, h + 10, tz + Integer.signum(tz) * (raio + 1), "minecraft:" + cor + "_wall_banner[facing=" + (tz > 0 ? "south" : "north") + "]");
        }

        // ---------------------------------------------------------- PORTÃO (sul)

        void portao() {
            // abre a passagem na muralha
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = MURALHA - 3; dz <= MURALHA; dz++) {
                    for (int y = h + 1; y <= h + 5; y++) {
                        set(dx, y, dz, Material.AIR);
                    }
                    set(dx, h, dz, Material.STONE_BRICKS);
                }
                set(dx, h + 5, MURALHA, Material.IRON_BARS); // grade levantada
                set(dx, h + 6, MURALHA - 1, Material.CHISELED_STONE_BRICKS);
            }
            // torres do portão
            for (int lado : new int[]{-1, 1}) {
                int bx = lado * 5;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = MURALHA - 3; dz <= MURALHA + 2; dz++) {
                        boolean parede = Math.abs(dx) == 2 || dz == MURALHA - 3 || dz == MURALHA + 2;
                        for (int y = h - 2; y <= h + 12; y++) {
                            set(bx + dx, y, dz, parede || y == h + 12 || y <= h ? pedra() : Material.AIR);
                        }
                        if (parede && ((dx + dz) & 1) == 0) {
                            set(bx + dx, h + 13, dz, pedra());
                        }
                    }
                }
                set(bx, h + 10, MURALHA + 3, "minecraft:" + cor + "_wall_banner[facing=south]");
                set(bx, h + 3, MURALHA + 3, "minecraft:lantern[hanging=false]");
                set(bx, h + 2, MURALHA + 3, Material.STONE_BRICK_WALL);
                set(bx, h + 1, MURALHA + 3, Material.STONE_BRICK_WALL);
            }
            // bandeira grande em cima do portão
            for (int dx = -1; dx <= 1; dx++) {
                set(dx, h + 8, MURALHA + 1, "minecraft:" + cor + "_wall_banner[facing=south]");
            }
            // estrada para fora
            for (int dz = MURALHA + 1; dz <= TOTAL; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int y = dz <= PLANO ? h : t.chao(cx + dx, cz + dz);
                    set(dx, y, dz, rnd.nextInt(5) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH);
                }
            }
        }

        // ---------------------------------------------------------- PÁTIO

        void patio() {
            for (int dx = -MURALHA + 3; dx <= MURALHA - 3; dx++) {
                for (int dz = -MURALHA + 3; dz <= MURALHA - 3; dz++) {
                    int r = rnd.nextInt(100);
                    if (Math.abs(dx) <= 2 && dz > -5) {
                        set(dx, h, dz, rnd.nextInt(6) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    } else if (r < 8) {
                        set(dx, h, dz, Material.COARSE_DIRT);
                    } else if (r < 12) {
                        set(dx, h, dz, Material.GRAVEL);
                    }
                    if (r > 96 && Math.abs(dx) > 3) {
                        set(dx, h + 1, dz, Material.SHORT_GRASS);
                    }
                }
            }
            // poço
            int px = -8;
            int pz = 10;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean meio = dx == 0 && dz == 0;
                    set(px + dx, h, pz + dz, meio ? Material.WATER : Material.STONE_BRICKS);
                    set(px + dx, h - 1, pz + dz, meio ? Material.WATER : Material.STONE_BRICKS);
                    set(px + dx, h - 2, pz + dz, Material.STONE_BRICKS);
                    if (!meio) {
                        set(px + dx, h + 1, pz + dz, Material.STONE_BRICK_WALL);
                    }
                }
            }
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                set(px + c[0], h + 2, pz + c[1], Material.SPRUCE_FENCE);
                set(px + c[0], h + 3, pz + c[1], Material.SPRUCE_FENCE);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(px + dx, h + 4, pz + dz, "minecraft:spruce_slab[type=bottom]");
                }
            }
            // postes de luz
            for (int[] p : new int[][]{{4, 18}, {-4, 18}, {4, 8}, {-4, 8}, {4, -2}, {-4, -2}}) {
                set(p[0], h + 1, p[1], Material.SPRUCE_FENCE);
                set(p[0], h + 2, p[1], Material.SPRUCE_FENCE);
                set(p[0], h + 3, p[1], "minecraft:lantern[hanging=false]");
            }
            // estandartes do reino no caminho
            for (int dz : new int[]{14, 2}) {
                set(3, h + 1, dz, "minecraft:" + cor + "_banner[rotation=12]");
                set(-3, h + 1, dz, "minecraft:" + cor + "_banner[rotation=4]");
            }
        }

        // ---------------------------------------------------------- FORTALEZA E SALA DO TRONO

        void fortaleza() {
            int x1 = -9;
            int x2 = 9;
            int z1 = -22;
            int z2 = -6;
            for (int dx = x1; dx <= x2; dx++) {
                for (int dz = z1; dz <= z2; dz++) {
                    boolean parede = dx == x1 || dx == x2 || dz == z1 || dz == z2;
                    boolean canto = (dx == x1 || dx == x2) && (dz == z1 || dz == z2);
                    for (int y = h - 1; y <= h + 13; y++) {
                        Material m;
                        if (y <= h) {
                            m = y == h && !parede ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                        } else if (canto) {
                            m = Material.POLISHED_DEEPSLATE;
                        } else if (parede) {
                            boolean janela = ((y >= h + 3 && y <= h + 4) || (y >= h + 9 && y <= h + 10))
                                    && ((dx == x1 || dx == x2) && Math.floorMod(dz, 4) == 0 || (dz == z1 || dz == z2) && Math.floorMod(dx, 4) == 2);
                            m = janela ? Material.GLASS_PANE : y == h + 7 || y == h + 13 ? Material.POLISHED_ANDESITE : pedra();
                        } else if (y == h + 7) {
                            m = Material.DARK_OAK_PLANKS;
                        } else if (y == h + 13) {
                            m = Material.STONE_BRICKS;
                        } else {
                            m = Material.AIR;
                        }
                        set(dx, y, dz, m);
                    }
                    if (parede && ((dx + dz) & 1) == 0) {
                        set(dx, h + 14, dz, pedra());
                    }
                }
            }
            // porta (arco aberto) para o pátio
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = h + 1; y <= h + 3; y++) {
                    set(dx, y, z2, Material.AIR);
                }
            }
            set(-2, h + 3, z2 + 1, "minecraft:lantern[hanging=false]");
            set(2, h + 3, z2 + 1, "minecraft:lantern[hanging=false]");
            set(-2, h + 2, z2 + 1, Material.STONE_BRICK_WALL);
            set(2, h + 2, z2 + 1, Material.STONE_BRICK_WALL);
            set(-2, h + 1, z2 + 1, Material.STONE_BRICK_WALL);
            set(2, h + 1, z2 + 1, Material.STONE_BRICK_WALL);
            set(0, h + 5, z2 + 1, "minecraft:" + cor + "_wall_banner[facing=south]");
            // tapete do reino até o trono
            for (int dz = z1 + 2; dz <= z2 - 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    set(dx, h + 1, dz, "minecraft:" + cor + "_carpet");
                }
            }
            // colunas e lustres
            for (int dz = z2 - 3; dz >= z1 + 3; dz -= 4) {
                for (int dx : new int[]{-6, 6}) {
                    for (int y = h + 1; y <= h + 6; y++) {
                        set(dx, y, dz, Material.QUARTZ_PILLAR);
                    }
                    set(dx + (dx > 0 ? 1 : -1), h + 4, dz, "minecraft:" + cor + "_wall_banner[facing=" + (dx > 0 ? "west" : "east") + "]");
                }
                set(0, h + 6, dz, "minecraft:lantern[hanging=true]");
                set(-3, h + 6, dz, corrente());
                set(-3, h + 5, dz, "minecraft:lantern[hanging=true]");
                set(3, h + 6, dz, corrente());
                set(3, h + 5, dz, "minecraft:lantern[hanging=true]");
            }
            // trono (o rei fica em pé na frente dele)
            int tz = z1 + 2;
            set(0, h + 1, tz, Material.GOLD_BLOCK);
            set(0, h + 2, tz, "minecraft:quartz_stairs[facing=south]");
            set(-1, h + 2, tz, Material.GOLD_BLOCK);
            set(1, h + 2, tz, Material.GOLD_BLOCK);
            set(0, h + 3, tz - 1, Material.GOLD_BLOCK);
            set(0, h + 4, tz - 1, Material.GOLD_BLOCK);
            for (int dx = -2; dx <= 2; dx++) {
                set(dx, h + 1, tz, dx == 0 ? "minecraft:gold_block" : "minecraft:" + cor + "_carpet");
                set(dx, h + 5, z1 + 1, "minecraft:" + cor + "_wall_banner[facing=south]");
            }
            for (int dx = -4; dx <= 4; dx += 8) {
                for (int y = h + 1; y <= h + 6; y++) {
                    set(dx, y, z1 + 1, "minecraft:" + cor + "_wool");
                }
            }
            // escada para o andar de cima e o telhado
            for (int y = h + 1; y <= h + 13; y++) {
                set(x2 - 1, y, z1 + 1, "minecraft:ladder[facing=west]");
            }
            // andar de cima: biblioteca e quarto real
            for (int dz = z1 + 1; dz <= z2 - 1; dz += 1) {
                set(x1 + 1, h + 8, dz, Material.BOOKSHELF);
                set(x1 + 1, h + 9, dz, Material.BOOKSHELF);
            }
            set(3, h + 8, z1 + 2, Material.CHEST);
            set(5, h + 8, z1 + 2, Material.CRAFTING_TABLE);
            set(0, h + 12, -14, "minecraft:lantern[hanging=true]");
            // torre central com o estandarte do reino
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -17; dz <= -11; dz++) {
                    boolean parede = Math.abs(dx) == 3 || dz == -17 || dz == -11;
                    for (int y = h + 14; y <= h + 22; y++) {
                        set(dx, y, dz, parede || y == h + 22 ? pedra() : Material.AIR);
                    }
                    if (parede && ((dx + dz) & 1) == 0) {
                        set(dx, h + 23, dz, pedra());
                    }
                }
            }
            for (int y = h + 23; y <= h + 27; y++) {
                set(0, y, -14, Material.SPRUCE_FENCE);
            }
            set(0, h + 28, -14, "minecraft:" + cor + "_banner[rotation=8]");
            set(0, h + 18, -10, "minecraft:" + cor + "_wall_banner[facing=south]");
            set(4, h + 18, -14, "minecraft:" + cor + "_wall_banner[facing=east]");
            set(-4, h + 18, -14, "minecraft:" + cor + "_wall_banner[facing=west]");
        }

        // ---------------------------------------------------------- QUARTEL, ESTÁBULO, TREINO

        void quartel() {
            int x1 = 13;
            int x2 = 21;
            int z1 = -2;
            int z2 = 14;
            for (int dx = x1; dx <= x2; dx++) {
                for (int dz = z1; dz <= z2; dz++) {
                    boolean parede = dx == x1 || dx == x2 || dz == z1 || dz == z2;
                    boolean canto = (dx == x1 || dx == x2) && (dz == z1 || dz == z2);
                    set(dx, h, dz, Material.SPRUCE_PLANKS);
                    for (int y = h + 1; y <= h + 4; y++) {
                        Material m = canto ? Material.STRIPPED_SPRUCE_LOG : parede ? (y == h + 2 && Math.floorMod(dz, 3) == 0 ? Material.GLASS_PANE
                                : Material.SPRUCE_PLANKS) : Material.AIR;
                        set(dx, y, dz, m);
                    }
                    set(dx, h + 5, dz, "minecraft:spruce_slab[type=bottom]");
                }
            }
            for (int y = h + 1; y <= h + 2; y++) {
                set(x1, y, 6, Material.AIR);
            }
            set(x1 - 1, h + 3, 5, "minecraft:" + cor + "_wall_banner[facing=west]");
            set(x1 - 1, h + 3, 7, "minecraft:" + cor + "_wall_banner[facing=west]");
            for (int dz = z1 + 1; dz <= z2 - 1; dz += 3) {
                set(x2 - 1, h + 1, dz, Material.BARREL);
                set(x2 - 2, h + 1, dz, "minecraft:" + cor + "_wool");
            }
            set(x1 + 2, h + 1, z1 + 1, Material.CHEST);
            set(x1 + 3, h + 1, z1 + 1, Material.SMITHING_TABLE);
            set(x1 + 4, h + 1, z1 + 1, Material.GRINDSTONE);
            set(17, h + 4, 6, "minecraft:lantern[hanging=true]");
        }

        void estabulo() {
            int x1 = -19;
            int x2 = -13;
            int z1 = 0;
            int z2 = 14;
            for (int dx = x1; dx <= x2; dx++) {
                for (int dz = z1; dz <= z2; dz++) {
                    boolean borda = dx == x1 || dz == z1 || dz == z2;
                    set(dx, h, dz, Material.COARSE_DIRT);
                    if (borda) {
                        set(dx, h + 1, dz, Material.OAK_FENCE);
                    } else if (dx == x2 && Math.floorMod(dz, 4) != 1) {
                        set(dx, h + 1, dz, Material.OAK_FENCE);
                    }
                    if (dx <= x1 + 2) {
                        set(dx, h + 4, dz, "minecraft:oak_slab[type=bottom]");
                    }
                }
            }
            for (int dz = z1 + 2; dz <= z2 - 2; dz += 4) {
                set(x1 + 1, h + 1, dz, Material.HAY_BLOCK);
                set(x1 + 1, h + 2, dz, Material.HAY_BLOCK);
                set(x1 + 1, h + 3, dz, Material.OAK_FENCE);
            }
            set(x1 + 1, h + 1, z1 + 1, caldeirao());
        }

        void treino() {
            for (int i = 0; i < 4; i++) {
                int dx = -20 + i * 3;
                int dz = -3;
                set(dx, h, dz, Material.COARSE_DIRT);
                set(dx, h + 1, dz, Material.OAK_FENCE);
                set(dx, h + 2, dz, Material.HAY_BLOCK);
                set(dx, h + 3, dz, "minecraft:carved_pumpkin[facing=south]");
            }
            set(-20, h + 1, -7, Material.TARGET);
            set(-19, h + 1, -7, Material.TARGET);
        }
    }

    /** Corrente de ferro (chamava "chain" antes da 1.21.9). */
    private static String corrente() {
        return (Material.matchMaterial("IRON_CHAIN") != null ? "minecraft:iron_chain" : "minecraft:chain") + "[axis=y]";
    }

    /** Caldeirão com água (o nome mudou entre versões). */
    private static Material caldeirao() {
        Material m = Material.matchMaterial("WATER_CAULDRON");
        return m != null ? m : Material.CAULDRON;
    }
}
