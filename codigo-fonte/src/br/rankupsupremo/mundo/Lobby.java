package br.rankupsupremo.mundo;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * O lobby: uma ilha flutuante num mundo vazio onde todo mundo chega. Tem um farol no centro, a praça e dois
 * portais - o dourado leva ao Rankup e o verde ao SMP RPG (volta para onde o jogador estava no survival).
 * Letreiros mostram quantos jogam em cada modo; a bússola abre o menu de mundos. Nada se quebra e ninguém se machuca.
 */
public final class Lobby implements Listener {

    private static final int Y = 100;
    private static final int PORTAL_X = 17;

    private final RankupSupremo plugin;
    private final NamespacedKey chaveBussola;
    private final NamespacedKey chaveLetreiro;
    private final Map<UUID, Long> ultimoPortal = new HashMap<>();
    private final List<TextDisplay> letreiros = new ArrayList<>();
    private TextDisplay letreiroRankup;
    private TextDisplay letreiroRpg;
    private World mundo;
    private Location spawn;
    private BukkitTask tarefa;
    private int tique;

    public Lobby(RankupSupremo plugin) {
        this.plugin = plugin;
        this.chaveBussola = new NamespacedKey(plugin, "bussola-lobby");
        this.chaveLetreiro = new NamespacedKey(plugin, "letreiro-lobby");
    }

    public boolean ativo() {
        return mundo != null;
    }

    public boolean ehLobby(World w) {
        return w != null && w.equals(mundo);
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    // ------------------------------------------------------------------ INICIAR

    public void iniciar() {
        if (!plugin.getConfig().getBoolean("lobby.ativado", true)) {
            return;
        }
        String nome = plugin.getConfig().getString("lobby.mundo", "lobby");
        World w = Bukkit.getWorld(nome);
        if (w == null) {
            w = new WorldCreator(nome).generator(new GeradorVazio()).environment(World.Environment.NORMAL)
                    .generateStructures(false).createWorld();
        }
        if (w == null) {
            plugin.getLogger().severe("Não foi possível criar o mundo do lobby '" + nome + "'.");
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
        w.setTime(plugin.getConfig().getLong("lobby.horario", 6000));
        w.setStorm(false);
        w.setThundering(false);
        if (!plugin.estado().lobbyConstruido()) {
            plugin.getLogger().info("Construindo o lobby pela primeira vez...");
            construir(w);
            plugin.estado().setLobbyConstruido(true);
        }
        spawn = new Location(w, 0.5, Y + 1, 10.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        // a ilha fica sempre carregada (é pequena): os letreiros não somem quando ninguém está lá
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                w.addPluginChunkTicket(cx, cz, plugin);
            }
        }
        criarLetreiros();
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
        plugin.getLogger().info("Lobby pronto no mundo '" + nome + "'.");
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (TextDisplay t : letreiros) {
            t.remove();
        }
        letreiros.clear();
    }

    // ------------------------------------------------------------------ CONSTRUIR

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void set(World w, int x, int y, int z, String dados) {
        BlockData d = Bukkit.createBlockData(dados);
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    private void construir(World w) {
        Random rnd = new Random(7);
        Material[] flores = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM,
                Material.AZURE_BLUET, Material.PINK_TULIP};
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                double r = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(z, x);
                double limite = 25 + 2.2 * Math.sin(ang * 5) + 1.3 * Math.cos(ang * 3);
                if (r > limite) {
                    continue;
                }
                boolean caminho = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && r > 7 && r < limite - 1.2;
                Material topo;
                if (r <= 7) {
                    topo = r > 5.5 ? Material.STONE_BRICKS : ((x + z) & 1) == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
                } else if (caminho) {
                    topo = rnd.nextInt(6) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                } else {
                    topo = Material.GRASS_BLOCK;
                }
                set(w, x, Y, z, topo);
                int prof = (int) Math.round((limite - r) * 1.15 + 2 + rnd.nextInt(3));
                for (int d = 1; d <= prof; d++) {
                    Material m = d <= 2 ? Material.DIRT : rnd.nextInt(100) < 14 ? Material.ANDESITE
                            : rnd.nextInt(100) < 12 ? Material.COBBLESTONE : rnd.nextInt(100) < 3 ? Material.COAL_ORE : Material.STONE;
                    set(w, x, Y - d, z, m);
                }
                if (topo == Material.GRASS_BLOCK && r > 9) {
                    int s = rnd.nextInt(100);
                    if (s < 14) {
                        set(w, x, Y + 1, z, Material.SHORT_GRASS);
                    } else if (s < 19) {
                        set(w, x, Y + 1, z, flores[rnd.nextInt(flores.length)]);
                    }
                }
            }
        }
        // farol no centro (feixe dourado)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(w, x, Y - 2, z, Material.IRON_BLOCK);
                set(w, x, Y, z, Material.GOLD_BLOCK);
            }
        }
        set(w, 0, Y - 1, 0, Material.BEACON);
        set(w, 0, Y, 0, Material.YELLOW_STAINED_GLASS);
        // pilares da praça
        for (int[] p : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            for (int y = Y + 1; y <= Y + 4; y++) {
                set(w, p[0], y, p[1], Material.QUARTZ_PILLAR);
            }
            set(w, p[0], Y + 5, p[1], Material.CHISELED_QUARTZ_BLOCK);
            set(w, p[0], Y + 6, p[1], "minecraft:lantern[hanging=false]");
        }
        // postes nos caminhos
        for (int s = 10; s <= 22; s += 6) {
            for (int[] d : new int[][]{{s, 2}, {s, -2}, {-s, 2}, {-s, -2}, {2, s}, {-2, s}, {2, -s}, {-2, -s}}) {
                if (Math.abs(d[0]) == PORTAL_X || Math.abs(d[1]) == PORTAL_X) {
                    continue;
                }
                set(w, d[0], Y + 1, d[1], Material.SPRUCE_FENCE);
                set(w, d[0], Y + 2, d[1], Material.SPRUCE_FENCE);
                set(w, d[0], Y + 3, d[1], "minecraft:lantern[hanging=false]");
            }
        }
        // portais
        portal(w, -PORTAL_X, Material.GOLD_BLOCK, Material.POLISHED_BLACKSTONE_BRICKS, Material.DIAMOND_BLOCK);
        portal(w, PORTAL_X, Material.MOSSY_STONE_BRICKS, Material.EMERALD_BLOCK, Material.CHISELED_STONE_BRICKS);
        // mural ao norte
        for (int x = -3; x <= 3; x++) {
            for (int y = Y + 1; y <= Y + 4; y++) {
                set(w, x, y, -18, Math.abs(x) == 3 || y == Y + 4 ? Material.DARK_OAK_LOG : Material.DARK_OAK_PLANKS);
            }
        }
        // árvores
        for (int[] a : new int[][]{{12, 12}, {-12, 12}, {12, -12}, {-12, -12}, {19, 8}, {-19, -8}}) {
            Location l = new Location(w, a[0], Y + 1, a[1]);
            l.getBlock().setType(Material.AIR, false);
            w.generateTree(l, rnd, rnd.nextBoolean() ? TreeType.TREE : TreeType.BIRCH);
        }
    }

    private void portal(World w, int px, Material moldura, Material detalhe, Material topo) {
        for (int z = -3; z <= 3; z++) {
            for (int y = Y + 1; y <= Y + 7; y++) {
                int az = Math.abs(z);
                Material m;
                if (az <= 1 && y <= Y + 5) {
                    m = Material.AIR;
                } else if (az == 3) {
                    m = y <= Y + 5 ? detalhe : y == Y + 6 ? moldura : Material.AIR;
                } else if (y == Y + 7) {
                    m = z == 0 ? topo : az <= 2 ? moldura : Material.AIR;
                } else {
                    m = moldura;
                }
                set(w, px, y, z, m);
            }
        }
        set(w, px, Y + 8, 0, "minecraft:lantern[hanging=false]");
    }

    // ------------------------------------------------------------------ LETREIROS

    private TextDisplay letreiro(double x, double y, double z, String texto) {
        TextDisplay t = mundo.spawn(new Location(mundo, x, y, z), TextDisplay.class, e -> {
            e.text(Texto.cor(texto));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            e.setShadowed(true);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(chaveLetreiro, PersistentDataType.BYTE, (byte) 1);
        });
        letreiros.add(t);
        return t;
    }

    private void criarLetreiros() {
        for (Entity e : mundo.getEntities()) {
            if (e.getPersistentDataContainer().has(chaveLetreiro)) {
                e.remove();
            }
        }
        letreiros.clear();
        letreiro(0.5, Y + 6.2, 0.5, plugin.getConfig().getString("lobby.letreiro-centro",
                "<gradient:#FFD700:#FF8C00><bold>RankUP Supremo</bold></gradient>\n<gray>Escolha seu modo de jogo\n<white>⬅ Rankup   <dark_gray>|   <white>SMP RPG ➡"));
        letreiroRankup = letreiro(-PORTAL_X + 0.5, Y + 9.2, 0.5, "");
        letreiroRpg = letreiro(PORTAL_X + 0.5, Y + 9.2, 0.5, "");
        letreiro(0.5, Y + 2.6, -17.3, String.join("\n", plugin.getConfig().getStringList("lobby.mural")));
        atualizarLetreiros();
    }

    private void atualizarLetreiros() {
        int rankup = 0;
        int rpg = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.ehMundoRankup(p.getWorld())) {
                rankup++;
            } else if (plugin.sobrevivencia().ehSobrevivencia(p.getWorld())) {
                rpg++;
            }
        }
        if (letreiroRankup != null && letreiroRankup.isValid()) {
            letreiroRankup.text(Texto.cor(plugin.getConfig().getString("lobby.letreiro-rankup",
                    "<gradient:#FFD700:#FF8C00><bold>RANKUP</bold></gradient>\n<gray>Minere • Venda • Suba de rank\n<yellow>{jogadores} jogando"),
                    "jogadores", String.valueOf(rankup)));
        }
        if (letreiroRpg != null && letreiroRpg.isValid()) {
            letreiroRpg.text(Texto.cor(plugin.getConfig().getString("lobby.letreiro-rpg",
                    "<gradient:#56AB2F:#A8E063><bold>SMP RPG</bold></gradient>\n<gray>Classes • Masmorras • Vilas • Castelos\n<yellow>{jogadores} jogando"),
                    "jogadores", String.valueOf(rpg)));
        }
    }

    private void tick() {
        tique++;
        if (mundo == null || mundo.getPlayers().isEmpty()) {
            return;
        }
        if (tique % 10 == 0) {
            atualizarLetreiros();
        }
        Random r = new Random();
        Particle.DustOptions ouro = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.4f);
        Particle.DustOptions verde = new Particle.DustOptions(Color.fromRGB(90, 220, 90), 1.4f);
        for (int i = 0; i < 6; i++) {
            mundo.spawnParticle(Particle.DUST, -PORTAL_X + 0.5, Y + 1 + r.nextDouble() * 5, -1 + r.nextDouble() * 3, 1, 0.1, 0, 0, 0, ouro);
            mundo.spawnParticle(Particle.DUST, PORTAL_X + 0.5, Y + 1 + r.nextDouble() * 5, -1 + r.nextDouble() * 3, 1, 0.1, 0, 0, 0, verde);
        }
        if (tique % 3 == 0) {
            mundo.spawnParticle(Particle.END_ROD, 0.5, Y + 2, 0.5, 2, 0.4, 0.6, 0.4, 0.01);
        }
    }

    // ------------------------------------------------------------------ JOGADORES

    public void teleportar(Player p) {
        if (spawn == null) {
            plugin.acoes().teleportarSpawn(p, true);
            return;
        }
        p.teleport(spawn);
        p.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
    }

    /** Ao chegar no lobby: modo aventura, vida cheia e a bússola do menu. */
    private void preparar(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !ehLobby(p.getWorld())) {
                return;
            }
            if (p.getGameMode() == GameMode.SURVIVAL) {
                p.setGameMode(GameMode.ADVENTURE);
            }
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setHealth(Math.max(1, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                    : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            ItemStack b = new ItemStack(Material.COMPASS);
            b.editMeta(m -> {
                m.displayName(Texto.cor("<!italic><gold><bold>Escolher modo de jogo</bold> <gray>(clique)"));
                m.lore(List.of(Texto.cor("<!italic><gray>Rankup ou SMP RPG"), Texto.cor("<!italic><gray>Ou atravesse um dos portais!")));
                m.addItemFlags(ItemFlag.values());
                m.getPersistentDataContainer().set(chaveBussola, PersistentDataType.BYTE, (byte) 1);
            });
            p.getInventory().setItem(4, b);
            plugin.mensagens().enviarLista(p, "lobby-bem-vindo", "jogador", p.getName());
        }, 6L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMudarDeMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (ehLobby(p.getWorld())) {
            preparar(p);
        } else if (ehLobby(e.getFrom()) && p.getGameMode() == GameMode.ADVENTURE) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        if (plugin.ehMundoRankup(p.getWorld())) {
            // a picareta inicial é entregue no mundo do Rankup (o lobby tem inventário separado)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DadosJogador d = plugin.jogadores().obter(p);
                if (p.isOnline() && plugin.ehMundoRankup(p.getWorld()) && !d.recebeuPicareta()) {
                    d.setRecebeuPicareta(true);
                    plugin.acoes().darPicareta(p, true);
                }
            }, 10L);
        }
    }

    /** Chamado pelo JogadorListener quando o jogador entra no servidor. */
    public void aoEntrar(Player p) {
        if (ehLobby(p.getWorld())) {
            preparar(p);
        } else {
            teleportar(p);
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
            return;
        }
        int bx = l.getBlockX();
        if (Math.abs(bx) != PORTAL_X || Math.abs(l.getBlockZ()) > 1 || l.getY() < Y + 1 || l.getY() > Y + 6) {
            return;
        }
        long agora = System.currentTimeMillis();
        if (ultimoPortal.getOrDefault(p.getUniqueId(), 0L) > agora) {
            return;
        }
        ultimoPortal.put(p.getUniqueId(), agora + 3000);
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.6f);
        if (bx < 0) {
            plugin.acoes().teleportarSpawn(p, true);
        } else {
            plugin.sobrevivencia().teleportar(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoRenascer(PlayerRespawnEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && spawn != null) {
            e.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoUsarBussola(PlayerInteractEvent e) {
        if (!ehLobby(e.getPlayer().getWorld())) {
            return;
        }
        ItemStack it = e.getItem();
        if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(chaveBussola)
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK
                || e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
            e.setCancelled(true);
            plugin.menus().mundos(e.getPlayer());
            return;
        }
        if (!podeConstruir(e.getPlayer()) && e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ PROTEÇÃO

    private boolean podeConstruir(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && (p.hasPermission("rankupsupremo.construir") || plugin.emModoConstrucao(p));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoLevarDano(EntityDamageEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTerFome(FoodLevelChangeEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoDropar(PlayerDropItemEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPegar(EntityPickupItemEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTrocarMao(PlayerSwapHandItemsEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && !podeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoInteragirEntidade(PlayerInteractEntityEvent e) {
        if (ehLobby(e.getPlayer().getWorld()) && !podeConstruir(e.getPlayer()) && e.getRightClicked() instanceof org.bukkit.entity.ItemFrame) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrarQuadro(HangingBreakEvent e) {
        if (ehLobby(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }
}
