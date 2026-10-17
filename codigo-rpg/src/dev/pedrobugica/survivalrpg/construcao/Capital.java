package dev.pedrobugica.survivalrpg.construcao;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.mundo.Lugar;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

/**
 * Vila Aurora, a capital do RPG, construída em volta do spawn do mundo de sobrevivência:
 * terreno nivelado, muralha redonda com 3 portões e torres, praça com chafariz (onde já ficam os NPCs),
 * castelo ao norte, ruas calçadas com postes, casas de verdade das vilas do jogo (taverna, forja, templo,
 * biblioteca...), fazenda, jardim, campo de treino e o Portão das Masmorras.
 * Antes de mexer em qualquer bloco tira uma foto da área inteira (/rpgadmin desfazer capital volta tudo).
 */
public final class Capital {

    public static final String ID = "capital";
    public static final int RAIO_MURALHA = 40;
    public static final int RAIO_PLANO = 46;
    public static final int RAIO_TOTAL = 60;

    private final SurvivalRpgPlugin plugin;
    private final NamespacedKey chaveExtra;
    private boolean construindo;

    public Capital(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.chaveExtra = new NamespacedKey(plugin, "capital-extra");
    }

    public boolean construida() {
        return plugin.lugares().obter(ID) != null;
    }

    public boolean construindo() {
        return construindo;
    }

    // ------------------------------------------------------------------ CONSTRUIR

    public void construir(CommandSender quem, boolean forcar, Runnable depois) {
        if (construindo) {
            plugin.mensagens().enviar(quem, "capital-construindo");
            return;
        }
        World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
        if (w == null) {
            plugin.mensagens().enviar(quem, "mundo-nao-encontrado", "mundo", plugin.regioes().mundoPrincipal());
            return;
        }
        Location spawn = w.getSpawnLocation();
        int cx = plugin.getConfig().getInt("capital.x", spawn.getBlockX());
        int cz = plugin.getConfig().getInt("capital.z", spawn.getBlockZ());
        int h = plugin.getConfig().getInt("capital.altura-do-chao", spawn.getBlockY() - 1);
        construindo = true;
        plugin.mensagens().enviar(quem, "capital-analisando");
        int r = RAIO_TOTAL + 4;
        Terreno.carregar(w, cx - r, cz - r, cx + r, cz + r).thenAccept(t -> {
            try {
                int achados = t.blocosDeJogador(cx, cz, RAIO_TOTAL, Set.of());
                int limite = plugin.getConfig().getInt("capital.maximo-blocos-de-jogador", 12);
                if (achados > limite && !forcar) {
                    construindo = false;
                    plugin.mensagens().enviar(quem, "capital-tem-construcoes", "blocos", String.valueOf(achados));
                    plugin.getLogger().warning("Capital NÃO construída: " + achados + " blocos de jogador perto do spawn (use /rpgadmin capital construir forcar).");
                    return;
                }
                new Plano(w, t, cx, cz, h, quem, depois).comecar();
            } catch (RuntimeException e) {
                construindo = false;
                plugin.getLogger().log(Level.SEVERE, "Erro ao planejar a capital", e);
                plugin.mensagens().enviar(quem, "capital-erro");
            }
        });
    }

    /** Remove os letreiros flutuantes e os NPCs que a capital criou (usado ao desfazer). */
    public void removerExtras() {
        World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
        if (w != null) {
            for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(chaveExtra)) {
                    e.remove();
                }
            }
        }
        plugin.npcHook().removerDaCapital();
    }

    private static double smooth(double f) {
        f = Math.max(0, Math.min(1, f));
        return f * f * (3 - 2 * f);
    }

    // ------------------------------------------------------------------ O PLANO

    private final class Plano {
        final World w;
        final Terreno t;
        final int cx;
        final int cz;
        final int h;
        final CommandSender quem;
        final Runnable depois;
        final Random rnd = new Random(20261015L);
        final int[][] altura = new int[RAIO_TOTAL * 2 + 1][RAIO_TOTAL * 2 + 1];
        final Obra o;
        final List<Object[]> letreiros = new ArrayList<>();

        Plano(World w, Terreno t, int cx, int cz, int h, CommandSender quem, Runnable depois) {
            this.w = w;
            this.t = t;
            this.cx = cx;
            this.cz = cz;
            this.h = h;
            this.quem = quem;
            this.depois = depois;
            this.o = new Obra(w);
        }

        void set(int dx, int y, int dz, Material m) {
            o.set(cx + dx, y, cz + dz, m);
        }

        void set(int dx, int y, int dz, BlockData d) {
            o.set(cx + dx, y, cz + dz, d);
        }

        void set(int dx, int y, int dz, String dados) {
            o.set(cx + dx, y, cz + dz, dados);
        }

        Material pedra() {
            int r = rnd.nextInt(100);
            return r < 68 ? Material.STONE_BRICKS : r < 84 ? Material.MOSSY_STONE_BRICKS : r < 95 ? Material.CRACKED_STONE_BRICKS : Material.ANDESITE;
        }

        Material calcada() {
            int r = rnd.nextInt(100);
            return r < 38 ? Material.COBBLESTONE : r < 66 ? Material.STONE_BRICKS : r < 80 ? Material.ANDESITE
                    : r < 92 ? Material.MOSSY_COBBLESTONE : Material.GRAVEL;
        }

        int alturaEm(int dx, int dz) {
            if (Math.abs(dx) > RAIO_TOTAL || Math.abs(dz) > RAIO_TOTAL) {
                return t.chao(cx + dx, cz + dz);
            }
            return altura[dx + RAIO_TOTAL][dz + RAIO_TOTAL];
        }

        void comecar() {
            // Foto da área inteira, para desfazer
            int minChao = Integer.MAX_VALUE;
            int maxTopo = Integer.MIN_VALUE;
            for (int dx = -RAIO_TOTAL - 2; dx <= RAIO_TOTAL + 2; dx++) {
                for (int dz = -RAIO_TOTAL - 2; dz <= RAIO_TOTAL + 2; dz++) {
                    minChao = Math.min(minChao, t.chao(cx + dx, cz + dz));
                    maxTopo = Math.max(maxTopo, t.topo(cx + dx, cz + dz));
                }
            }
            int y1 = Math.min(minChao, h) - 4;
            int y2 = Math.max(maxTopo, h + 30) + 2;
            Instantaneo foto = Instantaneo.tirar(t, cx - RAIO_TOTAL - 2, y1, cz - RAIO_TOTAL - 2, cx + RAIO_TOTAL + 2, y2, cz + RAIO_TOTAL + 2);
            plugin.lugares().gravarFoto(ID, foto);

            terreno();
            praca();
            ruas();
            muralha();
            castelo();
            postes();
            fazenda(23, -22);
            treino(-23, -22);
            jardim(23, 22);
            santuario(-23, 22);
            barracas();
            plugin.mensagens().enviar(quem, "capital-comecou", "blocos", String.valueOf(o.tamanho()));
            plugin.getLogger().info("Construindo a capital em " + cx + ", " + h + ", " + cz + " (" + o.tamanho() + " blocos)...");
            o.executar(plugin, plugin.getConfig().getInt("capital.blocos-por-tique", 6000), this::casas);
        }

        // ---------------------------------------------------------- TERRENO

        void terreno() {
            for (int dx = -RAIO_TOTAL; dx <= RAIO_TOTAL; dx++) {
                for (int dz = -RAIO_TOTAL; dz <= RAIO_TOTAL; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    int x = cx + dx;
                    int z = cz + dz;
                    int chao = t.chao(x, z);
                    int topo = t.topo(x, z);
                    if (r > RAIO_TOTAL) {
                        altura[dx + RAIO_TOTAL][dz + RAIO_TOTAL] = chao;
                        continue;
                    }
                    int alvo = r <= RAIO_PLANO ? h : (int) Math.round(h + (chao - h) * smooth((r - RAIO_PLANO) / (RAIO_TOTAL - RAIO_PLANO)));
                    altura[dx + RAIO_TOTAL][dz + RAIO_TOTAL] = alvo;
                    if (r > RAIO_PLANO && alvo == chao) {
                        continue;
                    }
                    for (int y = chao + 1; y < alvo; y++) {
                        o.set(x, y, z, y < alvo - 3 ? Material.STONE : Material.DIRT);
                    }
                    if (r <= RAIO_PLANO || alvo < chao) {
                        for (int y = alvo + 1; y <= Math.max(topo, alvo + 1); y++) {
                            o.set(x, y, z, Material.AIR);
                        }
                    }
                    o.set(x, alvo, z, Material.GRASS_BLOCK);
                    if (alvo != chao) {
                        o.set(x, alvo - 1, z, Material.DIRT);
                        o.set(x, alvo - 2, z, Material.DIRT);
                    }
                }
            }
        }

        // ---------------------------------------------------------- PRAÇA E CHAFARIZ

        void praca() {
            for (int dx = -11; dx <= 11; dx++) {
                for (int dz = -11; dz <= 11; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > 10.5) {
                        continue;
                    }
                    Material m;
                    if (r > 9.5 || Math.abs(dx) <= 1 && r > 3.5 || Math.abs(dz) <= 1 && r > 3.5) {
                        m = Material.STONE_BRICKS;
                    } else if (r > 5.5 && r <= 6.5) {
                        m = Material.CHISELED_STONE_BRICKS;
                    } else {
                        m = rnd.nextInt(10) == 0 ? Material.ANDESITE : Material.POLISHED_ANDESITE;
                    }
                    set(dx, h, dz, m);
                    set(dx, h - 1, dz, Material.STONE);
                }
            }
            // Chafariz
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r < 2.5) {
                        set(dx, h, dz, Material.WATER);
                        set(dx, h - 1, dz, Material.STONE_BRICKS);
                    } else if (r < 3.5) {
                        set(dx, h, dz, Material.STONE_BRICKS);
                        set(dx, h + 1, dz, "minecraft:stone_brick_slab[type=bottom]");
                    }
                }
            }
            set(1, h - 1, 0, Material.SEA_LANTERN);
            set(-1, h - 1, 0, Material.SEA_LANTERN);
            set(0, h - 1, 1, Material.SEA_LANTERN);
            set(0, h - 1, -1, Material.SEA_LANTERN);
            for (int y = h; y <= h + 3; y++) {
                set(0, y, 0, Material.CHISELED_STONE_BRICKS);
            }
            set(0, h + 4, 0, Material.WATER);
            letreiro(0, h + 7, 0, "<gold><bold>Vila Aurora</bold>\n<gray>Capital do Reino\n<yellow>/rpg <dark_gray>• <yellow>/classe <dark_gray>• <yellow>/lugares");
        }

        // ---------------------------------------------------------- RUAS

        void rua(int dx, int dz, int lado) {
            // lado = distância da linha do meio (-2..2 calçada, 3 = passeio)
            int a = Math.abs(lado);
            set(dx, h, dz, a <= 1 ? calcada() : a == 2 ? Material.STONE_BRICKS : Material.DIRT_PATH);
            set(dx, h - 1, dz, Material.STONE);
        }

        void ruas() {
            for (int s = 10; s <= RAIO_MURALHA + 3; s++) {
                for (int l = -3; l <= 3; l++) {
                    rua(l, s, l);      // sul
                    rua(s, l, l);      // leste
                    rua(-s, l, l);     // oeste
                }
            }
            for (int s = 10; s <= 21; s++) {
                for (int l = -3; l <= 3; l++) {
                    rua(l, -s, l);     // norte (até o castelo)
                }
            }
            // Estrada de terra fora dos portões, acompanhando o terreno
            for (int s = RAIO_MURALHA + 4; s <= RAIO_TOTAL + 6; s++) {
                for (int l = -2; l <= 2; l++) {
                    estrada(l, s);
                    estrada(s, l);
                    estrada(-s, l);
                }
            }
            // Caminhos até os cantos (fazenda, treino, jardim, santuário)
            for (int s = 4; s <= 17; s++) {
                caminho(24, -s);
                caminho(-24, -s);
                caminho(24, s);
                caminho(-24, s);
            }
        }

        void estrada(int dx, int dz) {
            int y = alturaEm(dx, dz);
            Material topo = t.tipo(cx + dx, y, cz + dz);
            if (topo == Material.WATER || y < h - 12) {
                return;
            }
            set(dx, y, dz, rnd.nextInt(6) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH);
            set(dx, y + 1, dz, Material.AIR);
        }

        void caminho(int dx, int dz) {
            set(dx, h, dz, rnd.nextInt(4) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH);
        }

        void poste(int dx, int dz) {
            set(dx, h + 1, dz, Material.SPRUCE_FENCE);
            set(dx, h + 2, dz, Material.SPRUCE_FENCE);
            set(dx, h + 3, dz, Material.SPRUCE_FENCE);
            set(dx, h + 4, dz, "minecraft:lantern[hanging=false]");
        }

        void postes() {
            for (int s = 13; s <= 37; s += 8) {
                poste(3, s);
                poste(-3, s + 4);
                poste(s, 3);
                poste(s + 4, -3);
                poste(-s, -3);
                poste(-s - 4, 3);
            }
            poste(3, -14);
            poste(-3, -19);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + Math.PI / 8;
                poste((int) Math.round(Math.cos(a) * 9.5), (int) Math.round(Math.sin(a) * 9.5));
            }
        }

        // ---------------------------------------------------------- MURALHA E TORRES

        boolean portao(int dx, int dz) {
            // Portões no sul, leste e oeste (o norte é o castelo)
            return (dz > 30 && Math.abs(dx) <= 2) || (dx > 30 && Math.abs(dz) <= 2) || (dx < -30 && Math.abs(dz) <= 2);
        }

        void muralha() {
            for (int dx = -RAIO_MURALHA - 3; dx <= RAIO_MURALHA + 3; dx++) {
                for (int dz = -RAIO_MURALHA - 3; dz <= RAIO_MURALHA + 3; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r < RAIO_MURALHA - 1 || r >= RAIO_MURALHA + 2) {
                        continue;
                    }
                    boolean aberto = portao(dx, dz);
                    for (int y = h - 2; y <= h + 7; y++) {
                        if (aberto && y >= h + 1 && (y <= h + 4 || (y == h + 5 && Math.abs(dx) <= 1 || y == h + 5 && Math.abs(dz) <= 1))) {
                            set(dx, y, dz, Material.AIR);
                            continue;
                        }
                        if (aberto && y == h) {
                            continue; // o chão do portão é a rua
                        }
                        set(dx, y, dz, pedra());
                    }
                    // ameias por fora
                    if (r >= RAIO_MURALHA + 1) {
                        double ang = Math.atan2(dz, dx);
                        int s = (int) Math.floor(ang * (RAIO_MURALHA + 1.5));
                        if (Math.floorMod(s, 4) < 2) {
                            set(dx, h + 8, dz, pedra());
                        }
                    }
                }
            }
            // lanternas no alto, por dentro
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                int dx = (int) Math.round(Math.cos(a) * (RAIO_MURALHA - 1));
                int dz = (int) Math.round(Math.sin(a) * (RAIO_MURALHA - 1));
                if (!portao(dx, dz)) {
                    set(dx, h + 8, dz, "minecraft:lantern[hanging=false]");
                }
            }
            int[][] torres = {{6, 40}, {-6, 40}, {40, 6}, {40, -6}, {-40, 6}, {-40, -6}, {29, 29}, {-29, 29}, {29, -29}, {-29, -29}, {0, -41}};
            for (int[] tr : torres) {
                torre(tr[0], tr[1], 3.5, h + 11);
            }
        }

        void torre(int tx, int tz, double raio, int topo) {
            int ri = (int) Math.ceil(raio);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d >= raio) {
                        continue;
                    }
                    for (int y = h - 2; y <= topo; y++) {
                        set(tx + dx, y, tz + dz, y == topo - 1 && d >= raio - 1 ? Material.CHISELED_STONE_BRICKS : pedra());
                    }
                    if (d >= raio - 1 && ((dx + dz) & 1) == 0) {
                        set(tx + dx, topo + 1, tz + dz, pedra());
                    }
                }
            }
            set(tx, topo + 1, tz, "minecraft:lantern[hanging=false]");
        }

        // ---------------------------------------------------------- CASTELO

        void castelo() {
            int x1 = -8;
            int x2 = 8;
            int z1 = -35;
            int z2 = -22;
            for (int dx = x1; dx <= x2; dx++) {
                for (int dz = z1; dz <= z2; dz++) {
                    boolean parede = dx == x1 || dx == x2 || dz == z1 || dz == z2;
                    set(dx, h, dz, parede ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS);
                    set(dx, h - 1, dz, Material.STONE);
                    for (int y = h + 1; y <= h + 9; y++) {
                        set(dx, y, dz, parede ? (y == h + 9 ? Material.CHISELED_STONE_BRICKS : pedra()) : Material.AIR);
                    }
                    set(dx, h + 10, dz, parede ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS);
                    if (parede && ((dx + dz) & 1) == 0) {
                        set(dx, h + 11, dz, pedra());
                    }
                }
            }
            // entrada
            for (int y = h + 1; y <= h + 4; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (y <= h + 3 || dx == 0) {
                        set(dx, y, z2, Material.AIR);
                    }
                }
            }
            // janelas
            for (int dz : new int[]{-25, -29, -33}) {
                for (int y = h + 3; y <= h + 5; y++) {
                    set(x1, y, dz, Material.GLASS);
                    set(x2, y, dz, Material.GLASS);
                }
            }
            for (int dx : new int[]{-5, 5}) {
                for (int y = h + 3; y <= h + 5; y++) {
                    set(dx, y, z2, Material.GLASS);
                    set(dx, y, z1, Material.GLASS);
                }
            }
            // torres dos cantos
            torre(x1, z1, 2.5, h + 13);
            torre(x2, z1, 2.5, h + 13);
            torre(x1, z2, 2.5, h + 13);
            torre(x2, z2, 2.5, h + 13);
            // tapete, trono, mesas, estandartes e lustres
            for (int dz = z2 - 1; dz >= z1 + 3; dz--) {
                set(0, h + 1, dz, Material.RED_CARPET);
            }
            set(0, h + 1, z1 + 1, "minecraft:quartz_stairs[facing=north]");
            set(-1, h + 1, z1 + 1, Material.GOLD_BLOCK);
            set(1, h + 1, z1 + 1, Material.GOLD_BLOCK);
            set(0, h + 2, z1 + 1, Material.AIR);
            for (int dz = z1 + 4; dz <= z2 - 3; dz++) {
                for (int lado : new int[]{-5, 5}) {
                    set(lado, h + 1, dz, "minecraft:dark_oak_slab[type=top]");
                    set(lado - 1, h + 1, dz, "minecraft:spruce_stairs[facing=west]");
                    set(lado + 1, h + 1, dz, "minecraft:spruce_stairs[facing=east]");
                }
            }
            for (int dz : new int[]{-26, -30}) {
                set(x1 + 1, h + 5, dz, "minecraft:red_wall_banner[facing=east]");
                set(x2 - 1, h + 5, dz, "minecraft:red_wall_banner[facing=west]");
            }
            set(-2, h + 3, z2 + 1, "minecraft:red_wall_banner[facing=south]");
            set(2, h + 3, z2 + 1, "minecraft:red_wall_banner[facing=south]");
            for (int dx : new int[]{-4, 0, 4}) {
                for (int dz : new int[]{-25, -29, -33}) {
                    if (dx == 0 && dz == -33) {
                        continue;
                    }
                    set(dx, h + 9, dz, "minecraft:lantern[hanging=true]");
                }
            }
            letreiro(0, h + 6, z2 + 1, "<red><bold>Castelo de Aurora</bold>\n<gray>Sede do Conselho do Reino");
        }

        // ---------------------------------------------------------- CANTOS: FAZENDA, TREINO, JARDIM, SANTUÁRIO

        void cerca(int cx0, int cz0, Material cerca, int portaX, int portaZ) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                        if (cx0 + dx == portaX && cz0 + dz == portaZ) {
                            set(cx0 + dx, h + 1, cz0 + dz, cerca == Material.OAK_FENCE ? "minecraft:oak_fence_gate[facing=north]" : "minecraft:air");
                        } else {
                            set(cx0 + dx, h + 1, cz0 + dz, cerca);
                        }
                    }
                }
            }
        }

        void fazenda(int fx, int fz) {
            String[] plantas = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]"};
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0) {
                        set(fx, h, fz + dz, Material.WATER);
                        continue;
                    }
                    set(fx + dx, h, fz + dz, "minecraft:farmland[moisture=7]");
                    set(fx + dx, h + 1, fz + dz, plantas[Math.floorMod(dz + 3, plantas.length)]);
                }
            }
            set(fx, h, fz + 3, "minecraft:oak_slab[type=top]");
            cerca(fx, fz, Material.OAK_FENCE, 24, fz + 4);
            set(fx - 4, h + 2, fz - 4, Material.HAY_BLOCK);
            set(fx + 4, h + 1, fz - 4, Material.COMPOSTER);
            letreiro(fx, h + 4, fz + 5, "<green><bold>Fazenda de Marta</bold>");
        }

        void treino(int fx, int fz) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    set(fx + dx, h, fz + dz, rnd.nextInt(3) == 0 ? Material.GRAVEL : Material.COARSE_DIRT);
                }
            }
            cerca(fx, fz, Material.SPRUCE_FENCE, -24, fz + 4);
            int[][] bonecos = {{-2, -2}, {0, -2}, {2, -2}};
            for (int[] b : bonecos) {
                set(fx + b[0], h + 1, fz + b[1], Material.SPRUCE_FENCE);
                set(fx + b[0], h + 2, fz + b[1], Material.HAY_BLOCK);
                set(fx + b[0], h + 3, fz + b[1], "minecraft:carved_pumpkin[facing=south]");
            }
            set(fx - 2, h + 1, fz + 2, Material.TARGET);
            set(fx + 2, h + 1, fz + 2, Material.TARGET);
            set(fx, h + 1, fz + 2, Material.HAY_BLOCK);
            letreiro(fx, h + 5, fz + 5, "<red><bold>Campo de Treino</bold>\n<gray>Aprenda sobre as classes: <yellow>/classe");
        }

        void jardim(int fx, int fz) {
            Material[] flores = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM,
                    Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY};
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        continue;
                    }
                    if (rnd.nextInt(3) == 0) {
                        set(fx + dx, h + 1, fz + dz, flores[rnd.nextInt(flores.length)]);
                    }
                }
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if ((Math.abs(dx) == 4 || Math.abs(dz) == 4) && !(fx + dx == 24 && fz + dz == fz - 4)) {
                        set(fx + dx, h + 1, fz + dz, "minecraft:oak_leaves[persistent=true]");
                    }
                }
            }
            set(fx - 2, h + 1, fz + 3, "minecraft:spruce_stairs[facing=south]");
            set(fx + 2, h + 1, fz + 3, "minecraft:spruce_stairs[facing=south]");
            set(fx - 3, h + 1, fz - 2, Material.LANTERN);
            set(fx + 3, h + 1, fz - 2, Material.LANTERN);
            letreiro(fx, h + 4, fz - 5, "<light_purple><bold>Jardim da Rainha</bold>");
        }

        void santuario(int fx, int fz) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    set(fx + dx, h, fz + dz, Math.abs(dx) == 4 || Math.abs(dz) == 4 ? Material.POLISHED_BLACKSTONE_BRICKS
                            : rnd.nextInt(5) == 0 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
                }
            }
            int[][] pilares = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
            for (int[] p : pilares) {
                for (int y = h + 1; y <= h + 5; y++) {
                    set(fx + p[0], y, fz + p[1], Material.POLISHED_BLACKSTONE_BRICKS);
                }
                set(fx + p[0], h + 6, fz + p[1], Material.CHISELED_POLISHED_BLACKSTONE);
                set(fx + p[0], h + 7, fz + p[1], "minecraft:soul_lantern[hanging=false]");
            }
            // moldura do portal (fundo, lado sul)
            int pz = fz + 3;
            for (int dx = -2; dx <= 2; dx++) {
                for (int y = h + 1; y <= h + 6; y++) {
                    boolean borda = Math.abs(dx) == 2 || y == h + 6;
                    set(fx + dx, y, pz, borda ? Material.CRYING_OBSIDIAN : Material.PURPLE_STAINED_GLASS);
                }
            }
            set(fx, h + 1, fz, Material.LODESTONE);
            set(fx - 2, h + 1, fz - 1, Material.SOUL_CAMPFIRE);
            set(fx + 2, h + 1, fz - 1, Material.SOUL_CAMPFIRE);
            letreiro(fx, h + 3, fz, "<dark_purple><bold>⚔ Portão das Masmorras ⚔</bold>\n<gray>Clique na pedra-guia ou no portal\n<gray>ou use <yellow>/masmorra");
            plugin.masmorras().registrarEntrada(new Location(w, cx + fx + 0.5, h + 1, cz + fz + 0.5));
        }

        void barracas() {
            String[] lã = {"minecraft:red_wool", "minecraft:yellow_wool", "minecraft:blue_wool", "minecraft:lime_wool"};
            int[][] pos = {{7, 7}, {-7, 7}, {7, -8}, {-7, -8}};
            for (int i = 0; i < pos.length; i++) {
                int bx = pos[i][0];
                int bz = pos[i][1];
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                            set(bx + dx, h + 1, bz + dz, Material.SPRUCE_FENCE);
                            set(bx + dx, h + 2, bz + dz, Material.SPRUCE_FENCE);
                        }
                        set(bx + dx, h + 3, bz + dz, lã[i]);
                    }
                }
                set(bx, h + 1, bz, Material.BARREL);
            }
        }

        void letreiro(int dx, int y, int dz, String texto) {
            letreiros.add(new Object[]{cx + dx + 0.5, (double) y, cz + dz + 0.5, texto});
        }

        // ---------------------------------------------------------- CASAS (modelos do jogo)

        boolean dentro(int x, int z) {
            double dx = x - cx;
            double dz = z - cz;
            return Math.sqrt(dx * dx + dz * dz) < RAIO_MURALHA - 2;
        }

        void casas() {
            List<String> casas = new ArrayList<>(plugin.getConfig().getStringList("capital.casas"));
            if (casas.isEmpty()) {
                casas.add("minecraft:village/plains/houses/plains_small_house_1");
            }
            // [dx, dz, direção, largura, profundidade, nome, modelos especiais...]
            Object[][] lotes = {
                    {18, -4, BlockFace.SOUTH, 12, 11, "<gold><bold>Taverna do Javali Dourado", "capital.taverna"},
                    {18, 4, BlockFace.NORTH, 12, 11, "<gold><bold>Forja de Borin", "capital.forja"},
                    {-18, -4, BlockFace.SOUTH, 12, 11, "<aqua><bold>Biblioteca Arcana", "capital.biblioteca"},
                    {-18, 4, BlockFace.NORTH, 12, 11, "<yellow><bold>Templo da Luz", "capital.templo"},
                    {4, 21, BlockFace.WEST, 11, 11, "<green><bold>Casa do Cartógrafo", "capital.cartografo"},
                    {-4, 21, BlockFace.EAST, 11, 11, null, null},
                    {30, -4, BlockFace.SOUTH, 12, 11, null, null},
                    {30, 4, BlockFace.NORTH, 12, 11, null, null},
                    {-30, -4, BlockFace.SOUTH, 12, 11, null, null},
                    {-30, 4, BlockFace.NORTH, 12, 11, null, null},
                    {4, 32, BlockFace.WEST, 9, 11, null, null},
                    {-4, 32, BlockFace.EAST, 9, 11, null, null},
                    {4, -16, BlockFace.WEST, 10, 7, null, null},
                    {-4, -16, BlockFace.EAST, 10, 7, null, null},
            };
            int feitas = 0;
            for (Object[] l : lotes) {
                // a frente do lote é a linha logo depois do passeio (lado ±4): a casa fica para trás dela
                Modelos.Lote lote = new Modelos.Lote(cx + (int) l[0], cz + (int) l[1], (BlockFace) l[2], (int) l[3], (int) l[4]);
                List<String> tentar = new ArrayList<>();
                if (l[6] != null) {
                    List<String> especiais = new ArrayList<>(plugin.getConfig().getStringList((String) l[6]));
                    java.util.Collections.shuffle(especiais, rnd);
                    tentar.addAll(especiais);
                }
                List<String> resto = new ArrayList<>(casas);
                java.util.Collections.shuffle(resto, rnd);
                tentar.addAll(resto);
                Modelos.Colocado c = null;
                for (String id : tentar) {
                    try {
                        c = Modelos.colocar(w, id, lote, h, this::dentro, rnd);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Modelo " + id + " falhou", e);
                        c = null;
                    }
                    if (c != null) {
                        break;
                    }
                }
                if (c == null) {
                    plugin.getLogger().warning("Nenhuma casa coube no lote " + l[0] + ", " + l[1]);
                    continue;
                }
                feitas++;
                plugin.getLogger().info("Casa " + c.modelo() + " na porta " + c.portaX() + ", " + c.portaY() + ", " + c.portaZ());
                if (l[5] != null) {
                    BlockFace f = (BlockFace) l[2];
                    letreiros.add(new Object[]{c.portaX() + 0.5 + f.getModX() * 1.5, (double) c.portaY() + 3.2, c.portaZ() + 0.5 + f.getModZ() * 1.5,
                            (String) l[5]});
                    if (l[6] != null && l[6].equals("capital.forja")) {
                        plugin.npcHook().moverBorin(new Location(w, c.portaX() + 0.5 + f.getModX() * 2, h + 1, c.portaZ() + 1.5 + f.getModZ() * 2));
                    }
                    if (l[6] != null && l[6].equals("capital.taverna")) {
                        plugin.npcHook().taverneiro(new Location(w, c.portaX() + 0.5 + f.getModX() * 2, h + 1, c.portaZ() + 0.5 + f.getModZ() * 2));
                    }
                }
            }
            plugin.getLogger().info(feitas + " casa(s) colocada(s) na capital.");
            finalizar();
        }

        // ---------------------------------------------------------- FIM

        void finalizar() {
            // árvores (o jogo gera do jeito dele)
            int[][] arvores = {{23, 22}, {13, -30}, {-13, -30}, {14, 30}, {-14, 30}, {33, 14}, {-33, 14}};
            for (int[] a : arvores) {
                Location l = new Location(w, cx + a[0], h + 1, cz + a[1]);
                if (l.getBlock().getType().isAir()) {
                    w.generateTree(l, rnd, a[0] == 23 ? TreeType.BIG_TREE : TreeType.TREE);
                }
            }
            // a água do chafariz começa a correr
            w.getBlockAt(cx, h + 4, cz).setType(Material.WATER, true);
            for (Object[] l : letreiros) {
                TextDisplay d = w.spawn(new Location(w, (double) l[0], (double) l[1], (double) l[2]), TextDisplay.class, e -> {
                    e.text(Texto.cor((String) l[3]));
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setAlignment(TextDisplay.TextAlignment.CENTER);
                    e.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                    e.setShadowed(true);
                    e.setPersistent(true);
                    e.setViewRange(0.6f);
                    e.getPersistentDataContainer().set(chaveExtra, PersistentDataType.BYTE, (byte) 1);
                });
                d.setPersistent(true);
            }
            // NPCs: os da praça ficam no chão novo; guardas nos portões; guardião no santuário; instrutor no treino
            plugin.npcHook().arrumarCapital(w, cx, h, cz);
            // quem estava na área volta para a praça
            for (Player p : w.getPlayers()) {
                if (Math.hypot(p.getLocation().getX() - cx, p.getLocation().getZ() - cz) <= RAIO_TOTAL) {
                    p.teleport(new Location(w, cx + 0.5, h + 1, cz + 6.5, 180, 0));
                }
            }
            w.setSpawnLocation(new Location(w, cx + 0.5, h + 1, cz + 6.5, 180, 0));
            if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                // O Multiverse guarda o spawn na configuração dele; sem isso ele voltaria ao antigo ao reiniciar.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(java.util.Locale.ROOT, "mvsetspawn %s:%.1f,%d,%.1f,180,0",
                        w.getName(), cx + 0.5, h + 1, cz + 6.5));
            }
            plugin.lugares().registrar(new Lugar(ID, "<gold>Vila Aurora", Lugar.Tipo.CAPITAL, w.getName(), cx, h + 1, cz,
                    RAIO_MURALHA + 4, true, "capital"));
            construindo = false;
            plugin.mensagens().enviar(quem, "capital-pronta");
            plugin.getLogger().info("Capital Vila Aurora pronta.");
            if (depois != null) {
                depois.run();
            }
        }
    }
}
