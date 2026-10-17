package dev.pedrobugica.survivalrpg.mundo;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.construcao.Obra;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.util.Texto;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * O lobby do RPG ("Cidadela dos Heróis"), no estilo dos servidores de RPG de hoje: uma ilha flutuante com uma espada
 * gigante cravada no centro da praça, o mural com TODOS os comandos (norte), as estátuas das cinco classes para escolher
 * (oeste), os portais para a capital, para continuar a aventura, para as terras selvagens e para as masmorras (leste),
 * os estandartes dos reinos e o ranking dos heróis (sul). Quem vem do lobby principal (portal verde ou /sobrevivencia)
 * chega aqui; /rpglobby volta. Ninguém se machuca, não tem fome e nada quebra.
 */
public final class LobbyRpg implements Listener {

    public static final int Y = 100;

    private record Portal(String id, int x, int z, boolean deFrente, String titulo, Color cor) {
        /** deFrente = portal virado para o norte/sul (atravessa no eixo z); senão para leste/oeste (eixo x). */
        boolean dentro(Location l) {
            int bx = l.getBlockX();
            int bz = l.getBlockZ();
            if (l.getY() < Y + 1 || l.getY() > Y + 5) {
                return false;
            }
            return deFrente ? bz == z && Math.abs(bx - x) <= 1 : bx == x && Math.abs(bz - z) <= 1;
        }
    }

    private static final List<Portal> PORTAIS = List.of(
            new Portal("capital", 27, -15, false, "<gold><bold>Vila Aurora</bold>\n<gray>A capital do RPG\n<yellow>NPCs, lojas e o portão das masmorras", Color.fromRGB(255, 200, 40)),
            new Portal("continuar", 27, -5, false, "<green><bold>Continuar a Aventura</bold>\n<gray>Volta exatamente\n<gray>de onde você parou", Color.fromRGB(90, 220, 90)),
            new Portal("rtp", 27, 5, false, "<red><bold>Terras Selvagens</bold>\n<gray>Um lugar aleatório e seguro\n<gray>para começar do zero (/rtp)", Color.fromRGB(230, 80, 50)),
            new Portal("masmorras", 27, 15, false, "<light_purple><bold>Masmorras</bold>\n<gray>Chefes, grupos e tesouros\n<gray>(/masmorra)", Color.fromRGB(170, 80, 230)),
            new Portal("lobby", 0, 36, true, "<aqua><bold>Lobby Principal</bold>\n<gray>Rankup e outros modos", Color.fromRGB(80, 200, 230)));

    private final SurvivalRpgPlugin plugin;
    private final NamespacedKey chaveExtra;
    private final NamespacedKey chaveClasse;
    private final Map<UUID, Long> ultimoPortal = new HashMap<>();
    private final List<Entity> extras = new ArrayList<>();
    private final Map<String, Location> bandeiras = new HashMap<>();
    private TextDisplay ranking;
    private TextDisplay painelReinos;
    private World mundo;
    private Location spawn;
    private BukkitTask tarefa;
    private int tique;

    public LobbyRpg(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.chaveExtra = new NamespacedKey(plugin, "lobby-rpg");
        this.chaveClasse = new NamespacedKey(plugin, "lobby-rpg-classe");
    }

    public boolean ativo() {
        return mundo != null;
    }

    public boolean ehLobby(World w) {
        return w != null && w.equals(mundo);
    }

    public World mundo() {
        return mundo;
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    // ------------------------------------------------------------------ LIGAR

    public void iniciar() {
        if (!plugin.getConfig().getBoolean("lobby-rpg.ativado", true)) {
            return;
        }
        String nome = plugin.getConfig().getString("lobby-rpg.mundo", "rpg_lobby");
        World w = Bukkit.getWorld(nome);
        if (w == null) {
            w = new WorldCreator(nome).generator(new GeradorVazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        }
        if (w == null) {
            plugin.getLogger().severe("Não foi possível criar o mundo do lobby do RPG '" + nome + "'.");
            return;
        }
        mundo = w;
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        w.setGameRule(GameRules.RESPAWN_RADIUS, 0);
        w.setGameRule(GameRules.PVP, false);
        w.setTime(plugin.getConfig().getLong("lobby-rpg.horario", 12300));
        w.setStorm(false);
        w.setThundering(false);
        spawn = new Location(w, 0.5, Y + 1, 18.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        for (int cx = -4; cx <= 3; cx++) {
            for (int cz = -4; cz <= 3; cz++) {
                w.addPluginChunkTicket(cx, cz, plugin);
            }
        }
        if (!YamlConfiguration.loadConfiguration(arquivoEstado()).contains("lobby-rpg-construido")) {
            construir(this::depoisDeConstruir);
        } else {
            depoisDeConstruir();
        }
    }

    private File arquivoEstado() {
        return new File(plugin.getDataFolder(), "estado.yml");
    }

    private void depoisDeConstruir() {
        criarExtras();
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
        plugin.npcHook().guiaDoLobby(new Location(mundo, 5.5, Y + 1, 12.5, 150f, 0f));
        plugin.getLogger().info("Lobby do RPG pronto no mundo '" + mundo.getName() + "'.");
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (Entity e : extras) {
            e.remove();
        }
        extras.clear();
    }

    /** Reconstrói a ilha (comando da staff). */
    public void reconstruir(Runnable fim) {
        if (mundo == null) {
            return;
        }
        construir(() -> {
            criarExtras();
            if (fim != null) {
                fim.run();
            }
        });
    }

    // ------------------------------------------------------------------ CONSTRUIR

    private void construir(Runnable fim) {
        plugin.getLogger().info("Construindo o lobby do RPG...");
        Obra o = new Obra(mundo);
        Random rnd = new Random(2026);
        ilha(o, rnd);
        praca(o, rnd);
        espada(o);
        mural(o);
        classes(o);
        for (Portal p : PORTAIS) {
            portal(o, p);
        }
        reinos(o);
        enfeites(o, rnd);
        o.executar(plugin, 20000, () -> {
            for (int[] a : new int[][]{{-30, 26}, {30, 26}, {-30, -22}, {33, -26}, {16, 32}, {-34, 4}, {-36, -8}}) {
                Location l = new Location(mundo, a[0], Y + 1, a[1]);
                mundo.generateTree(l, rnd, rnd.nextBoolean() ? org.bukkit.TreeType.TREE : org.bukkit.TreeType.CHERRY);
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivoEstado());
            y.set("lobby-rpg-construido", System.currentTimeMillis());
            try {
                y.save(arquivoEstado());
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Não foi possível gravar estado.yml: " + e.getMessage());
            }
            fim.run();
        });
    }

    private void ilha(Obra o, Random rnd) {
        Material[] flores = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM,
                Material.AZURE_BLUET, Material.PINK_TULIP, Material.LILY_OF_THE_VALLEY};
        for (int x = -46; x <= 46; x++) {
            for (int z = -46; z <= 46; z++) {
                double r = Math.hypot(x, z);
                double ang = Math.atan2(z, x);
                double limite = 41 + 2.5 * Math.sin(ang * 5) + 1.6 * Math.cos(ang * 3);
                if (r > limite) {
                    continue;
                }
                o.set(x, Y, z, Material.GRASS_BLOCK);
                int prof = (int) Math.round((limite - r) * 0.9 + 3 + rnd.nextInt(3));
                for (int d = 1; d <= prof; d++) {
                    Material m = d <= 2 ? Material.DIRT : rnd.nextInt(100) < 12 ? Material.ANDESITE : rnd.nextInt(100) < 10 ? Material.TUFF
                            : rnd.nextInt(100) < 3 ? Material.AMETHYST_BLOCK : Material.STONE;
                    o.set(x, Y - d, z, m);
                }
                if (r > 16 && rnd.nextInt(100) < 16) {
                    o.set(x, Y + 1, z, rnd.nextInt(4) == 0 ? flores[rnd.nextInt(flores.length)] : Material.SHORT_GRASS);
                }
            }
        }
    }

    private void praca(Obra o, Random rnd) {
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double r = Math.hypot(x, z);
                if (r > 15.5) {
                    continue;
                }
                Material m;
                if (r > 14.3) {
                    m = Material.POLISHED_DEEPSLATE;
                } else if (r > 13.3) {
                    m = Material.CHISELED_DEEPSLATE;
                } else if (Math.abs(x) == Math.abs(z) && r > 5) {
                    m = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (r > 9 && r < 10) {
                    m = Material.GILDED_BLACKSTONE;
                } else {
                    m = ((x + z) & 1) == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
                }
                o.set(x, Y, z, m);
                o.set(x, Y + 1, z, Material.AIR);
            }
        }
        // avenidas: norte (mural), sul (spawn), leste (portais) e oeste (classes)
        for (int s = 15; s <= 40; s++) {
            for (int l = -2; l <= 2; l++) {
                Material m = Math.abs(l) == 2 ? Material.POLISHED_ANDESITE : rnd.nextInt(7) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                if (s <= 27) {
                    avenida(o, l, -s, m);
                }
                if (s <= 37) {
                    avenida(o, l, s, m);
                }
                if (s <= 26) {
                    avenida(o, s, l, m);
                }
                if (s <= 22) {
                    avenida(o, -s, l, m);
                }
            }
        }
        // largo em frente aos portais e ao mural
        for (int x = 19; x <= 26; x++) {
            for (int z = -19; z <= 19; z++) {
                avenida(o, x, z, rnd.nextInt(8) == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
        for (int x = -20; x <= 20; x++) {
            for (int z = -27; z <= -20; z++) {
                avenida(o, x, z, rnd.nextInt(8) == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
    }

    private void avenida(Obra o, int x, int z, Material m) {
        o.set(x, Y, z, m);
        o.set(x, Y + 1, z, Material.AIR);
    }

    /** A Espada do Herói, cravada na pedra no meio da praça. */
    private void espada(Obra o) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                o.set(x, Y + 1, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                o.set(x, Y + 2, z, Material.MOSSY_STONE_BRICKS);
            }
        }
        for (int y = Y + 3; y <= Y + 11; y++) {
            for (int x = -1; x <= 1; x++) {
                o.set(x, y, 0, x == 0 ? Material.IRON_BLOCK : Material.LIGHT_GRAY_CONCRETE);
            }
        }
        for (int x = -4; x <= 4; x++) {
            o.set(x, Y + 12, 0, Math.abs(x) == 4 ? Material.RAW_GOLD_BLOCK : Material.GOLD_BLOCK);
        }
        o.set(0, Y + 12, 1, Material.GOLD_BLOCK);
        o.set(0, Y + 12, -1, Material.GOLD_BLOCK);
        for (int y = Y + 13; y <= Y + 15; y++) {
            o.set(0, y, 0, "minecraft:stripped_dark_oak_log[axis=y]");
        }
        o.set(0, Y + 16, 0, Material.GOLD_BLOCK);
        o.set(0, Y + 17, 0, Material.END_ROD);
        // luzes em volta
        for (int[] p : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            o.set(p[0], Y + 1, p[1], Material.CHISELED_STONE_BRICKS);
            o.set(p[0], Y + 2, p[1], "minecraft:lantern[hanging=false]");
        }
    }

    /** O mural de comandos ao norte (os textos são letreiros flutuantes, editáveis no config). */
    private void mural(Obra o) {
        int z0 = -30;
        for (int x = -23; x <= 23; x++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int y = Y - 1; y <= Y + 15; y++) {
                    boolean pilar = Math.floorMod(x + 23, 15) == 0 || x == 23;
                    Material m;
                    if (pilar) {
                        m = y == Y + 15 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_DEEPSLATE;
                    } else if (y == Y + 15 || y <= Y + 1) {
                        m = Material.STONE_BRICKS;
                    } else {
                        m = dz == 2 ? Material.DEEPSLATE_TILES : Material.STONE_BRICKS;
                    }
                    o.set(x, y, z0 + dz, m);
                }
            }
            if ((x & 1) == 0) {
                o.set(x, Y + 16, z0 + 2, Material.STONE_BRICKS);
            }
        }
        for (int x : new int[]{-23, -8, 7, 23}) {
            o.set(x, Y + 16, z0 + 2, Material.CHISELED_STONE_BRICKS);
            o.set(x, Y + 17, z0 + 2, "minecraft:lantern[hanging=false]");
        }
        for (int x = -21; x <= 21; x += 7) {
            o.set(x, Y + 13, z0 + 3, "minecraft:red_wall_banner[facing=south]");
        }
        for (int x = -22; x <= 22; x++) {
            o.set(x, Y + 1, z0 + 3, "minecraft:stone_brick_slab[type=bottom]");
        }
    }

    private void classes(Obra o) {
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(144 + i * 18);
            int x = (int) Math.round(Math.cos(ang) * 22);
            int z = (int) Math.round(Math.sin(ang) * 22);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    o.set(x + dx, Y, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    o.set(x + dx, Y + 1, z + dz, Math.abs(dx) + Math.abs(dz) == 2 ? Material.POLISHED_BLACKSTONE_BRICK_SLAB : Material.POLISHED_BLACKSTONE);
                }
            }
            o.set(x, Y + 1, z, Material.CHISELED_POLISHED_BLACKSTONE);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        o.set(x + dx, Y, z + dz, Material.POLISHED_ANDESITE);
                    }
                }
            }
        }
    }

    private void portal(Obra o, Portal p) {
        Material moldura = switch (p.id()) {
            case "capital" -> Material.GOLD_BLOCK;
            case "continuar" -> Material.MOSSY_STONE_BRICKS;
            case "rtp" -> Material.RED_NETHER_BRICKS;
            case "masmorras" -> Material.PURPUR_BLOCK;
            default -> Material.PRISMARINE_BRICKS;
        };
        Material detalhe = switch (p.id()) {
            case "capital" -> Material.CHISELED_STONE_BRICKS;
            case "continuar" -> Material.EMERALD_BLOCK;
            case "rtp" -> Material.MAGMA_BLOCK;
            case "masmorras" -> Material.CRYING_OBSIDIAN;
            default -> Material.DARK_PRISMARINE;
        };
        for (int a = -3; a <= 3; a++) {
            for (int y = Y + 1; y <= Y + 7; y++) {
                int aa = Math.abs(a);
                Material m;
                if (aa <= 1 && y <= Y + 5) {
                    m = Material.AIR;
                } else if (aa == 3) {
                    m = y <= Y + 5 ? detalhe : y == Y + 6 ? moldura : Material.AIR;
                } else if (y == Y + 7) {
                    m = a == 0 ? Material.BEACON : aa <= 2 ? moldura : Material.AIR;
                } else {
                    m = moldura;
                }
                if (p.deFrente()) {
                    o.set(p.x() + a, y, p.z(), m);
                } else {
                    o.set(p.x(), y, p.z() + a, m);
                }
            }
            if (p.deFrente()) {
                o.set(p.x() + a, Y, p.z(), Material.POLISHED_DEEPSLATE);
            } else {
                o.set(p.x(), Y, p.z() + a, Material.POLISHED_DEEPSLATE);
            }
        }
        o.set(p.x(), Y + 8, p.z(), "minecraft:lantern[hanging=false]");
    }

    /** Estandartes dos reinos (sudoeste). A cor vem do reinos.yml do NpcVivos. */
    private void reinos(Obra o) {
        var lista = plugin.npcHook().reinos();
        bandeiras.clear();
        int[][] pos = {{-19, 18}, {-14, 25}, {-7, 30}, {-23, 11}, {-2, 33}};
        for (int i = 0; i < lista.size() && i < pos.length; i++) {
            var r = lista.get(i);
            int x = pos[i][0];
            int z = pos[i][1];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    o.set(x + dx, Y, z + dz, Material.POLISHED_ANDESITE);
                }
            }
            o.set(x, Y + 1, z, Material.CHISELED_STONE_BRICKS);
            o.set(x, Y + 2, z, Material.STONE_BRICK_WALL);
            o.set(x, Y + 3, z, Material.STONE_BRICK_WALL);
            o.set(x, Y + 4, z, Material.SPRUCE_FENCE);
            // estandarte virado para a praça
            double ang = Math.toDegrees(Math.atan2(-x, z)) + 180;
            int rot = (int) Math.round(((ang % 360) + 360) % 360 / 22.5) % 16;
            o.set(x, Y + 5, z, "minecraft:" + r.bandeira().toLowerCase(java.util.Locale.ROOT) + "_banner[rotation=" + rot + "]");
            bandeiras.put(x + "," + (Y + 5) + "," + z, new Location(mundo, x, Y + 5, z));
        }
    }

    private void enfeites(Obra o, Random rnd) {
        // postes nas avenidas
        for (int s = 18; s <= 36; s += 6) {
            for (int[] d : new int[][]{{3, s}, {-3, s}, {s, 3}, {s, -3}, {-s, 3}, {-s, -3}, {3, -s}, {-3, -s}}) {
                if (Math.abs(d[0]) > 26 && Math.abs(d[1]) <= 3) {
                    continue;
                }
                if (d[1] < -26 || d[1] > 34 && Math.abs(d[0]) <= 3) {
                    continue;
                }
                if (Math.hypot(d[0], d[1]) > 38) {
                    continue;
                }
                o.set(d[0], Y + 1, d[1], Material.SPRUCE_FENCE);
                o.set(d[0], Y + 2, d[1], Material.SPRUCE_FENCE);
                o.set(d[0], Y + 3, d[1], "minecraft:lantern[hanging=false]");
            }
        }
        // bancos em volta da praça
        for (int[] b : new int[][]{{8, 13, 0}, {-8, 13, 0}, {8, -13, 1}, {-8, -13, 1}}) {
            for (int dx = -1; dx <= 1; dx++) {
                o.set(b[0] + dx, Y + 1, b[1], "minecraft:spruce_stairs[facing=" + (b[2] == 0 ? "south" : "north") + "]");
            }
        }
        // canteiros
        for (int[] c : new int[][]{{11, 11}, {-11, 11}, {11, -11}, {-11, -11}}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    o.set(c[0] + dx, Y + 1, c[1] + dz, Math.abs(dx) == 1 || Math.abs(dz) == 1 ? Material.SPRUCE_TRAPDOOR : Material.MOSS_BLOCK);
                }
            }
            o.set(c[0], Y + 2, c[1], Material.FLOWERING_AZALEA);
        }
    }

    // ------------------------------------------------------------------ LETREIROS E ESTÁTUAS

    private TextDisplay letreiro(double x, double y, double z, String texto, Display.Billboard modo, float escala) {
        TextDisplay t = mundo.spawn(new Location(mundo, x, y, z), TextDisplay.class, e -> {
            e.text(Texto.cor(texto));
            e.setBillboard(modo);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBackgroundColor(Color.fromARGB(140, 10, 10, 20));
            e.setShadowed(true);
            e.setPersistent(false);
            e.setLineWidth(260);
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(escala, escala, escala), new org.joml.AxisAngle4f()));
            e.getPersistentDataContainer().set(chaveExtra, PersistentDataType.BYTE, (byte) 1);
        });
        extras.add(t);
        return t;
    }

    private void criarExtras() {
        for (Entity e : extras) {
            e.remove();
        }
        extras.clear();
        for (Entity e : mundo.getEntities()) {
            if (e.getPersistentDataContainer().has(chaveExtra)) {
                e.remove();
            }
        }
        var cfg = plugin.getConfig();
        letreiro(0.5, Y + 20.5, 0.5, cfg.getString("lobby-rpg.titulo",
                "<gradient:#FFD700:#FF8C00><bold>⚔ SMP RPG ⚔</bold></gradient>\n<gray>A Cidadela dos Heróis"), Display.Billboard.CENTER, 2.2f);
        letreiro(0.5, Y + 4.2, 3.3, "<gray>A <gold>Espada do Herói</gold>\n<dark_gray>dizem que só o mais digno a tira da pedra...",
                Display.Billboard.CENTER, 0.8f);
        // mural de comandos
        letreiro(0.5, Y + 17.6, -26.5, "<gold><bold>📜 MURAL DE COMANDOS</bold>\n<gray>tudo o que dá para fazer no RPG", Display.Billboard.VERTICAL, 1.6f);
        ConfigurationSection mural = cfg.getConfigurationSection("lobby-rpg.mural");
        List<String> chaves = mural == null ? List.of() : new ArrayList<>(mural.getKeys(false));
        double[][] pos = {{-15.5, Y + 11.2}, {0.5, Y + 11.2}, {15.5, Y + 11.2}, {-15.5, Y + 5.4}, {0.5, Y + 5.4}, {15.5, Y + 5.4}};
        for (int i = 0; i < chaves.size() && i < pos.length; i++) {
            ConfigurationSection s = mural.getConfigurationSection(chaves.get(i));
            if (s == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder(s.getString("titulo", chaves.get(i)));
            for (String l : s.getStringList("linhas")) {
                sb.append("\n").append(l);
            }
            letreiro(pos[i][0], pos[i][1] - 2.4, -26.6, sb.toString(), Display.Billboard.VERTICAL, 0.85f);
        }
        // estátuas das classes
        Classe[] cs = Classe.values();
        for (int i = 0; i < cs.length && i < 5; i++) {
            double ang = Math.toRadians(144 + i * 18);
            double x = Math.round(Math.cos(ang) * 22) + 0.5;
            double z = Math.round(Math.sin(ang) * 22) + 0.5;
            estatua(cs[i], new Location(mundo, x, Y + 2, z));
            letreiro(x, Y + 5.1, z, cs[i].cor() + "<bold>" + cs[i].nome().toUpperCase() + "</bold>\n<gray>" + cs[i].papel()
                    + "\n<yellow>▶ Clique para escolher", Display.Billboard.CENTER, 0.75f);
        }
        letreiro(-21.5, Y + 8.5, 0.5, "<white><bold>🛡 SALÃO DAS CLASSES</bold>\n<gray>A 1ª classe é grátis", Display.Billboard.VERTICAL, 1.2f);
        // portais
        for (Portal p : PORTAIS) {
            letreiro(p.x() + 0.5 + (p.deFrente() ? 0 : -1.2), Y + 9.3, p.z() + 0.5 + (p.deFrente() ? -1.2 : 0), p.titulo(), Display.Billboard.CENTER, 0.8f);
        }
        letreiro(24.5, Y + 12, 0.5, "<white><bold>✦ PORTAIS ✦</bold>\n<gray>Atravesse para viajar", Display.Billboard.VERTICAL, 1.3f);
        // reinos
        var lista = plugin.npcHook().reinos();
        int[][] posR = {{-19, 18}, {-14, 25}, {-7, 30}, {-23, 11}, {-2, 33}};
        for (int i = 0; i < lista.size() && i < posR.length; i++) {
            var r = lista.get(i);
            letreiro(posR[i][0] + 0.5, Y + 8.2, posR[i][1] + 0.5, r.nome() + "\n<gray><italic>\"" + r.lema() + "\"\n<gray>" + r.rei()
                    + "\n<yellow>▶ Clique na bandeira", Display.Billboard.CENTER, 0.7f);
        }
        painelReinos = letreiro(-13.5, Y + 4.5, 18.5, "", Display.Billboard.VERTICAL, 0.75f);
        // ranking
        ranking = letreiro(14.5, Y + 4.8, 22.5, "<gold><bold>🏆 HERÓIS MAIS FORTES</bold>\n<gray>carregando...", Display.Billboard.VERTICAL, 0.85f);
        atualizarRanking();
        atualizarReinos();
    }

    private static ResolvableProfile perfil(String skin) {
        boolean fino = skin.endsWith("-fino");
        String base = fino ? skin.substring(0, skin.length() - 5) : skin;
        return ResolvableProfile.resolvableProfile().skinPatch(p -> p
                .body(Key.key("minecraft", "entity/player/" + (fino ? "slim/" : "wide/") + base))
                .model(fino ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC)).build();
    }

    private static ItemStack couro(Material m, int rgb) {
        ItemStack i = new ItemStack(m);
        i.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(rgb)));
        return i;
    }

    private void estatua(Classe c, Location l) {
        Location olhando = l.clone();
        olhando.setDirection(new Location(mundo, 0.5, l.getY(), 0.5).toVector().subtract(l.toVector()));
        Mannequin m = mundo.spawn(olhando, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setImmovable(true);
            e.setInvulnerable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setCustomNameVisible(false);
            e.setDescription(Texto.cor(""));
            e.getPersistentDataContainer().set(chaveExtra, PersistentDataType.BYTE, (byte) 1);
            e.getPersistentDataContainer().set(chaveClasse, PersistentDataType.STRING, c.name());
            var esc = e.getAttribute(Attribute.SCALE);
            if (esc != null) {
                esc.setBaseValue(1.15);
            }
            EntityEquipment eq = e.getEquipment();
            switch (c) {
                case GUERREIRO -> {
                    e.setProfile(perfil("steve"));
                    eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                    eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    eq.setItemInOffHand(new ItemStack(Material.SHIELD));
                }
                case ARQUEIRO -> {
                    e.setProfile(perfil("alex-fino"));
                    eq.setHelmet(couro(Material.LEATHER_HELMET, 0x2E6B2E));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0x3E7B2E));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0x5A3A1A));
                    eq.setBoots(couro(Material.LEATHER_BOOTS, 0x3A2A1A));
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                }
                case MAGO -> {
                    e.setProfile(perfil("noor-fino"));
                    eq.setHelmet(couro(Material.LEATHER_HELMET, 0x3B2A8A));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0x4B2E9A));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0x3B2A8A));
                    eq.setBoots(couro(Material.LEATHER_BOOTS, 0x2A1A5A));
                    eq.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                    eq.setItemInOffHand(new ItemStack(Material.ENCHANTED_BOOK));
                }
                case CLERIGO -> {
                    e.setProfile(perfil("sunny"));
                    eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0xF0EDE0));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0xE0D8B0));
                    eq.setBoots(new ItemStack(Material.GOLDEN_BOOTS));
                    eq.setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));
                    eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                }
                case LADINO -> {
                    e.setProfile(perfil("zuri"));
                    eq.setHelmet(couro(Material.LEATHER_HELMET, 0x1A1A1A));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0x222222));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0x1A1A1A));
                    eq.setBoots(couro(Material.LEATHER_BOOTS, 0x111111));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    eq.setItemInOffHand(new ItemStack(Material.IRON_SWORD));
                }
            }
        });
        extras.add(m);
    }

    // ------------------------------------------------------------------ RANKING E REINOS

    /** Lê os perfis do disco (fora da thread principal) e mostra os 10 maiores níveis. */
    private void atualizarRanking() {
        File pasta = new File(plugin.getDataFolder(), "jogadores");
        List<PerfilRpg> online = plugin.perfis().online();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, double[]> dados = new HashMap<>();
            Map<String, String> classes = new HashMap<>();
            File[] fs = pasta.listFiles((d, n) -> n.endsWith(".yml"));
            if (fs != null) {
                for (File f : fs) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    String nome = y.getString("nome");
                    if (nome != null) {
                        dados.put(nome, new double[]{y.getInt("nivel", 1), y.getDouble("xp")});
                        classes.put(nome, y.getString("classe", ""));
                    }
                }
            }
            for (PerfilRpg r : online) {
                if (r.nome() != null) {
                    dados.put(r.nome(), new double[]{r.nivel(), r.xp()});
                    classes.put(r.nome(), r.classe() == null ? "" : r.classe().name());
                }
            }
            List<Map.Entry<String, double[]>> top = new ArrayList<>(dados.entrySet());
            top.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0]).thenComparingDouble(e -> e.getValue()[1]).reversed());
            StringBuilder sb = new StringBuilder("<gold><bold>🏆 HERÓIS MAIS FORTES</bold>");
            String[] medalhas = {"<gold>①", "<gray>②", "<#CD7F32>③"};
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                var e = top.get(i);
                Classe c = Classe.buscar(classes.get(e.getKey()));
                sb.append("\n").append(i < 3 ? medalhas[i] : "<dark_gray>" + (i + 1) + ".").append(" <white>").append(e.getKey())
                        .append(" <dark_gray>- ").append(c == null ? "<gray>sem classe" : c.formatado()).append(" <gray>nv <yellow>")
                        .append((int) e.getValue()[0]);
            }
            if (top.isEmpty()) {
                sb.append("\n<gray>Ninguém ainda... seja o primeiro!");
            }
            String texto = sb.toString();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ranking != null && ranking.isValid()) {
                    ranking.text(Texto.cor(texto));
                }
            });
        });
    }

    private void atualizarReinos() {
        if (painelReinos == null || !painelReinos.isValid()) {
            return;
        }
        List<String> linhas = plugin.npcHook().linhasReinos();
        StringBuilder sb = new StringBuilder("<white><bold>👑 REINOS EM GUERRA</bold>");
        for (String l : linhas) {
            sb.append("\n").append(l);
        }
        if (linhas.isEmpty()) {
            sb.append("\n<gray>Os reinos ainda estão se formando...");
        }
        sb.append("\n<yellow>/reino <gray>para jurar lealdade");
        painelReinos.text(Texto.cor(sb.toString()));
    }

    private void tick() {
        tique++;
        if (mundo == null) {
            return;
        }
        if (tique % 1200 == 0) {
            atualizarRanking();
        }
        if (tique % 120 == 0) {
            atualizarReinos();
        }
        if (mundo.getPlayers().isEmpty()) {
            return;
        }
        Random r = new Random();
        for (Portal p : PORTAIS) {
            Particle.DustOptions cor = new Particle.DustOptions(p.cor(), 1.3f);
            for (int i = 0; i < 4; i++) {
                double a = -1 + r.nextDouble() * 3;
                double y = Y + 1 + r.nextDouble() * 4.5;
                if (p.deFrente()) {
                    mundo.spawnParticle(Particle.DUST, p.x() + a, y, p.z() + 0.5, 1, 0, 0, 0.05, 0, cor);
                } else {
                    mundo.spawnParticle(Particle.DUST, p.x() + 0.5, y, p.z() + a, 1, 0.05, 0, 0, 0, cor);
                }
            }
        }
        if (tique % 2 == 0) {
            mundo.spawnParticle(Particle.END_ROD, 0.5, Y + 13, 0.5, 2, 1.2, 4, 0.4, 0.01);
            mundo.spawnParticle(Particle.ENCHANT, 0.5, Y + 3, 0.5, 6, 1.5, 0.5, 1.5, 0.5);
        }
        // as estátuas olham para quem chega perto
        for (Entity e : extras) {
            if (e instanceof Mannequin m && m.isValid()) {
                Player perto = null;
                double melhor = 64;
                for (Player p : mundo.getPlayers()) {
                    double d = p.getLocation().distanceSquared(m.getLocation());
                    if (d < melhor) {
                        melhor = d;
                        perto = p;
                    }
                }
                if (perto != null) {
                    Location olho = m.getEyeLocation().setDirection(perto.getEyeLocation().toVector().subtract(m.getEyeLocation().toVector()));
                    m.setRotation(olho.getYaw(), olho.getPitch());
                }
            }
        }
    }

    // ------------------------------------------------------------------ JOGADORES

    public void teleportar(Player p) {
        if (spawn == null) {
            return;
        }
        p.teleport(spawn);
    }

    private void boasVindas(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !ehLobby(p.getWorld())) {
                return;
            }
            if (p.getGameMode() == GameMode.SURVIVAL) {
                p.setGameMode(GameMode.ADVENTURE);
            }
            p.setFoodLevel(20);
            p.setSaturation(20f);
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            p.showTitle(Title.title(Texto.cor("<gradient:#FFD700:#FF8C00><bold>⚔ SMP RPG ⚔</bold></gradient>"),
                    Texto.cor("<gray>Bem-vindo(a), <white>" + p.getName() + "<gray>!"),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.8f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1f);
            for (String l : plugin.getConfig().getStringList("lobby-rpg.boas-vindas")) {
                p.sendMessage(Texto.cor(Texto.substituir(l, "jogador", p.getName())));
            }
            if (plugin.perfis().perfil(p).classe() == null) {
                p.sendMessage(Texto.cor("<yellow>➜ Você ainda não tem classe! Vá ao <white>Salão das Classes <yellow>(à oeste) e clique numa estátua."));
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMudarDeMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (ehLobby(p.getWorld())) {
            boasVindas(p);
        } else if (ehLobby(e.getFrom()) && p.getGameMode() == GameMode.ADVENTURE) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!ehLobby(p.getWorld()) || !e.hasChangedBlock()) {
            return;
        }
        Location l = e.getTo();
        if (l.getY() < Y - 45) {
            p.teleport(spawn);
            p.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
            return;
        }
        for (Portal portal : PORTAIS) {
            if (!portal.dentro(l)) {
                continue;
            }
            long agora = System.currentTimeMillis();
            if (ultimoPortal.getOrDefault(p.getUniqueId(), 0L) > agora) {
                return;
            }
            ultimoPortal.put(p.getUniqueId(), agora + 3000);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
            usarPortal(p, portal.id());
            return;
        }
    }

    private void usarPortal(Player p, String id) {
        switch (id) {
            case "capital" -> {
                Lugar cap = plugin.lugares().capital();
                World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
                Location destino = cap != null ? plugin.lugares().pontoSeguro(cap) : w != null ? w.getSpawnLocation() : null;
                if (destino == null) {
                    p.sendMessage(Texto.cor("<red>A capital ainda não existe."));
                    return;
                }
                p.teleport(destino);
                p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            }
            case "continuar" -> p.performCommand("sobrevivencia");
            case "rtp" -> {
                // o /rtp do Rankup só funciona dentro da Sobrevivência: chega na capital e sorteia de lá
                World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
                if (w == null) {
                    return;
                }
                p.teleport(w.getSpawnLocation());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        p.performCommand("rtp");
                    }
                }, 10L);
            }
            case "masmorras" -> {
                Lugar cap = plugin.lugares().capital();
                if (cap != null && plugin.lugares().pontoSeguro(cap) != null) {
                    Location l = plugin.lugares().pontoSeguro(cap);
                    p.teleport(l.clone().add(-20, 0, 19));
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        plugin.masmorras().menu(p, true);
                    }
                }, 10L);
            }
            default -> p.performCommand("lobby");
        }
    }

    private void clicarEstatua(PlayerInteractEntityEvent e) {
        String c = e.getRightClicked().getPersistentDataContainer().get(chaveClasse, PersistentDataType.STRING);
        if (c == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Classe classe = Classe.buscar(c);
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        if (ultimoPortal.getOrDefault(p.getUniqueId(), 0L) > agora) {
            return;
        }
        ultimoPortal.put(p.getUniqueId(), agora + 700);
        if (classe == null) {
            return;
        }
        if (plugin.perfis().perfil(p).classe() == null) {
            plugin.escolherClasse(p, classe, false);
            e.getRightClicked().getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, e.getRightClicked().getLocation().add(0, 1.5, 0), 40, 0.5, 1, 0.5, 0.2);
        } else {
            plugin.menus().classes(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarEstatua(PlayerInteractEntityEvent e) {
        clicarEstatua(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarEstatuaEm(PlayerInteractAtEntityEvent e) {
        clicarEstatua(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarBloco(PlayerInteractEvent e) {
        if (!ehLobby(e.getPlayer().getWorld()) || e.getClickedBlock() == null || e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b.getType().name().endsWith("_BANNER")) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                e.getPlayer().performCommand("reino");
            }
        }
    }

    private boolean podeConstruir(Player p) {
        return p.hasPermission("survivalrpg.admin.construir") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoQuebrar(BlockBreakEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoColocar(BlockPlaceEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoDano(EntityDamageEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoFome(FoodLevelChangeEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoDropar(PlayerDropItemEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoRenascer(PlayerRespawnEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && spawn != null) {
            e.setRespawnLocation(spawn);
        }
    }
}
