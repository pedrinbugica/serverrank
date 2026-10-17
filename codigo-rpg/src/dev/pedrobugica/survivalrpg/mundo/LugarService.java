package dev.pedrobugica.survivalrpg.mundo;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.construcao.Instantaneo;
import dev.pedrobugica.survivalrpg.construcao.Terreno;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.util.Menu;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Os lugares do mundo: descoberta (título + XP), menu com distância e direção, viagem rápida entre cidades,
 * proteção das cidades contra quebrar/colocar blocos, e a colocação das estruturas prontas dos datapacks
 * (Dungeons and Taverns, Structory) em pontos do mapa longe das bases dos jogadores.
 */
public final class LugarService implements Listener {

    private static final String PERM_CONSTRUIR = "survivalrpg.admin.construir";

    private final SurvivalRpgPlugin plugin;
    private final File arquivo;
    private final Map<String, Lugar> lugares = new LinkedHashMap<>();
    private final Map<UUID, String> rumo = new HashMap<>();
    private final Map<UUID, Long> rumoAte = new HashMap<>();
    private final Map<UUID, BukkitTask> viagens = new HashMap<>();
    private BukkitTask tarefa;
    private int tique;
    private boolean populando;

    public LugarService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "lugares.yml");
    }

    // ------------------------------------------------------------------ DADOS

    public void carregar() {
        for (Lugar l : lugares.values()) {
            plugin.regioes().removerZona(l.id());
        }
        lugares.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection s = y.getConfigurationSection("lugares");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                Lugar.Tipo t = c == null ? null : Lugar.Tipo.buscar(c.getString("tipo"));
                if (t == null) {
                    continue;
                }
                registrarSemGravar(new Lugar(id, c.getString("nome", id), t, c.getString("mundo", plugin.regioes().mundoPrincipal()),
                        c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("raio-zona", 0), c.getBoolean("protegido", false),
                        c.getString("estrutura", "")));
            }
        }
        plugin.getLogger().info(lugares.size() + " lugar(es) no mapa.");
    }

    public void gravar() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Lugares do mapa (criados pelo /rpgadmin ou pela construção automática).",
                "raio-zona > 0 = zona segura (sem monstros e sem PvP). protegido = ninguém quebra nem coloca blocos."));
        for (Lugar l : lugares.values()) {
            String b = "lugares." + l.id() + ".";
            y.set(b + "nome", l.nome());
            y.set(b + "tipo", l.tipo().name());
            y.set(b + "mundo", l.mundo());
            y.set(b + "x", l.x());
            y.set(b + "y", l.y());
            y.set(b + "z", l.z());
            y.set(b + "raio-zona", l.raioZona());
            y.set(b + "protegido", l.protegido());
            y.set(b + "estrutura", l.estrutura());
        }
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar lugares.yml", e);
        }
    }

    private void registrarSemGravar(Lugar l) {
        lugares.put(l.id(), l);
        if (l.raioZona() > 0) {
            plugin.regioes().registrarZona(new RegiaoService.ZonaSegura(l.id(), l.formatado(), l.mundo(), l.x() + 0.5, l.z() + 0.5, l.raioZona()));
        } else {
            plugin.regioes().removerZona(l.id());
        }
    }

    public void registrar(Lugar l) {
        registrarSemGravar(l);
        gravar();
    }

    public boolean remover(String id) {
        Lugar l = lugares.remove(id);
        if (l == null) {
            return false;
        }
        plugin.regioes().removerZona(id);
        gravar();
        return true;
    }

    public Collection<Lugar> todos() {
        return List.copyOf(lugares.values());
    }

    public Lugar obter(String id) {
        return id == null ? null : lugares.get(id.toLowerCase(Locale.ROOT));
    }

    public Lugar capital() {
        for (Lugar l : lugares.values()) {
            if (l.tipo() == Lugar.Tipo.CAPITAL) {
                return l;
            }
        }
        return null;
    }

    public boolean descoberto(PerfilRpg r, Lugar l) {
        return r.regioesDescobertas().contains("zona:" + l.id()) || r.regioesDescobertas().contains("lugar:" + l.id());
    }

    /** Lugar protegido que contém este ponto (null = pode construir). */
    public Lugar protegidoEm(Location loc) {
        for (Lugar l : lugares.values()) {
            if (l.protegido() && l.raioZona() > 0 && l.distancia(loc) <= l.raioZona() + 2) {
                return l;
            }
        }
        return null;
    }

    public Lugar zonaDeViagem(Location loc) {
        for (Lugar l : lugares.values()) {
            if (l.tipo().viagem() && l.raioZona() > 0 && l.distancia(loc) <= l.raioZona()) {
                return l;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ TAREFA: DESCOBERTA E RUMO

    public void iniciar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (BukkitTask t : viagens.values()) {
            t.cancel();
        }
        viagens.clear();
    }

    private void tick() {
        tique++;
        if (tique % 4 != 0) {
            return;
        }
        long agora = System.currentTimeMillis();
        rumoAte.entrySet().removeIf(e -> {
            if (e.getValue() < agora) {
                rumo.remove(e.getKey());
                return true;
            }
            return false;
        });
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.mundoRpg(p.getWorld())) {
                continue;
            }
            PerfilRpg r = plugin.perfis().perfil(p);
            for (Lugar l : lugares.values()) {
                if (l.raioZona() > 0 || descoberto(r, l)) {
                    continue; // as zonas seguras são descobertas pelo RegiaoService
                }
                if (l.distancia(p.getLocation()) <= plugin.getConfig().getDouble("lugares.raio-descoberta", 40)) {
                    descobrir(p, r, l);
                }
            }
        }
    }

    private void descobrir(Player p, PerfilRpg r, Lugar l) {
        r.regioesDescobertas().add("lugar:" + l.id());
        r.marcarSujo();
        p.showTitle(Title.title(Texto.cor(l.formatado()), Texto.cor(plugin.mensagens().bruta("lugar-descoberto-subtitulo"),
                        "tipo", l.tipo().nome()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.5f);
        double xp = plugin.getConfig().getDouble("xp.descobrir-lugar", 150) * Math.max(1, r.nivel() / 5.0);
        plugin.perfis().darXp(p, xp, Texto.semCor(l.nome()) + " descoberto");
        plugin.mensagens().enviar(p, "descobriu", "lugar", l.formatado(), "xp", Texto.numero(xp));
    }

    /** Texto do rumo para a barra de ação (seta + distância), ou null. */
    public String textoRumo(Player p) {
        Lugar l = obter(rumo.get(p.getUniqueId()));
        if (l == null) {
            return null;
        }
        double d = l.distancia(p.getLocation());
        if (d == Double.MAX_VALUE) {
            return null;
        }
        if (d < 12) {
            rumo.remove(p.getUniqueId());
            rumoAte.remove(p.getUniqueId());
            return null;
        }
        double alvo = Math.toDegrees(Math.atan2(-(l.x() + 0.5 - p.getLocation().getX()), l.z() + 0.5 - p.getLocation().getZ()));
        double rel = ((alvo - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] setas = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
        String seta = setas[Math.floorMod(Math.round(rel / 45.0), 8)];
        return "<yellow>" + seta + " " + l.formatado() + " <gray>" + Texto.numero(d) + "m";
    }

    public void apontar(Player p, Lugar l) {
        rumo.put(p.getUniqueId(), l.id());
        rumoAte.put(p.getUniqueId(), System.currentTimeMillis() + 5 * 60_000L);
        Location loc = l.local();
        if (loc != null && loc.getWorld().equals(p.getWorld())) {
            p.setCompassTarget(loc);
        }
        plugin.mensagens().enviar(p, "lugar-rumo", "lugar", l.formatado());
    }

    // ------------------------------------------------------------------ MENU

    public void menu(Player p, int pagina) {
        PerfilRpg r = plugin.perfis().perfil(p);
        List<Lugar> l = new ArrayList<>(lugares.values());
        l.removeIf(x -> !x.mundo().equals(p.getWorld().getName()) && !descoberto(r, x));
        l.sort(Comparator.comparingDouble(x -> x.distancia(p.getLocation())));
        int porPagina = 45;
        int paginas = Math.max(1, (l.size() + porPagina - 1) / porPagina);
        int pag = Math.max(0, Math.min(pagina, paginas - 1));
        Menu m = new Menu(6, Texto.cor(plugin.mensagens().bruta("menu-lugares-titulo"), "pagina", String.valueOf(pag + 1),
                "paginas", String.valueOf(paginas)));
        Lugar aqui = zonaDeViagem(p.getLocation());
        int slot = 0;
        for (int i = pag * porPagina; i < l.size() && slot < porPagina; i++, slot++) {
            Lugar x = l.get(i);
            double d = x.distancia(p.getLocation());
            String dist = d == Double.MAX_VALUE ? "outro mundo" : Texto.numero(d) + "m";
            if (!descoberto(r, x)) {
                m.item(slot, Menu.criar(Material.GRAY_DYE, "<dark_gray>??? <gray>(" + x.tipo().nome() + ")",
                        List.of("<gray>Ainda não descoberto.", "<gray>Fica a uns <white>" + arredondar(d) + "<gray> daqui.",
                                "", "<yellow>Clique para seguir a direção")), (q, t) -> {
                    q.closeInventory();
                    apontar(q, x);
                });
                continue;
            }
            Regiao reg = plugin.regioes().regiao(x.local() == null ? p.getLocation() : x.local());
            List<String> lore = new ArrayList<>(List.of("<gray>Tipo: " + x.tipo().cor() + x.tipo().nome(),
                    "<gray>Distância: <white>" + dist,
                    "<gray>Níveis da região: <white>" + (reg == null ? "?" : reg.faixa()),
                    "<gray>Coordenadas: <white>" + x.x() + ", " + x.z()));
            boolean podeViajar = x.tipo().viagem() && aqui != null && !aqui.id().equals(x.id());
            lore.add("");
            if (podeViajar) {
                lore.add("<green>Clique: viajar por <gold>$" + Texto.dinheiro(custoViagem(aqui, x)));
                lore.add("<yellow>Shift+clique: só mostrar a direção");
            } else {
                lore.add("<yellow>Clique para seguir a direção");
                if (x.tipo().viagem()) {
                    lore.add("<dark_gray>Viagem rápida: só de dentro de uma cidade,");
                    lore.add("<dark_gray>vila ou taverna que você já conhece.");
                }
            }
            m.item(slot, Menu.criar(x.tipo().icone(), x.formatado(), lore), (q, t) -> {
                q.closeInventory();
                if (podeViajar && !t.isShiftClick()) {
                    viajar(q, aqui, x);
                } else {
                    apontar(q, x);
                }
            });
        }
        if (pag > 0) {
            m.item(45, Menu.criar(Material.ARROW, "<yellow>Página anterior", List.of()), (q, t) -> menu(q, pag - 1));
        }
        if (pag < paginas - 1) {
            m.item(53, Menu.criar(Material.ARROW, "<yellow>Próxima página", List.of()), (q, t) -> menu(q, pag + 1));
        }
        m.item(49, Menu.criar(Material.COMPASS, "<yellow><bold>Lugares",
                List.of("<gray>Descobertos: <white>" + l.stream().filter(x -> descoberto(r, x)).count() + "<gray>/<white>" + l.size(),
                        "<gray>Viagem rápida: de uma cidade/vila/taverna", "<gray>para outra que você já descobriu.")), null);
        m.item(48, Menu.criar(Material.BARRIER, "<gray>Voltar", List.of()), (q, t) -> plugin.menus().perfil(q));
        m.abrir(p);
    }

    private static String arredondar(double d) {
        if (d == Double.MAX_VALUE) {
            return "longe";
        }
        return Texto.numero(Math.max(100, Math.round(d / 100.0) * 100)) + "m";
    }

    // ------------------------------------------------------------------ VIAGEM RÁPIDA

    public double custoViagem(Lugar de, Lugar para) {
        double d = de.mundo().equals(para.mundo()) ? Math.hypot(de.x() - para.x(), de.z() - para.z()) : 2000;
        return Math.round(plugin.getConfig().getDouble("viagem.custo-base", 50)
                + plugin.getConfig().getDouble("viagem.custo-por-100-blocos", 10) * d / 100.0);
    }

    public void viajar(Player p, Lugar de, Lugar para) {
        if (viagens.containsKey(p.getUniqueId())) {
            return;
        }
        double custo = custoViagem(de, para);
        if (plugin.economia().ativa() && plugin.economia().saldo(p) < custo) {
            plugin.mensagens().enviar(p, "viagem-sem-dinheiro", "custo", Texto.dinheiro(custo));
            return;
        }
        int segundos = plugin.getConfig().getInt("viagem.espera-segundos", 3);
        Location inicio = p.getLocation();
        plugin.mensagens().enviar(p, "viagem-preparando", "lugar", para.formatado(), "segundos", String.valueOf(segundos));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.3f, 1.6f);
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            viagens.remove(p.getUniqueId());
            if (!p.isOnline() || p.getLocation().getWorld() != inicio.getWorld() || p.getLocation().distanceSquared(inicio) > 1.0) {
                plugin.mensagens().enviar(p, "viagem-cancelada");
                return;
            }
            if (plugin.economia().ativa() && !plugin.economia().sacar(p, custo)) {
                plugin.mensagens().enviar(p, "viagem-sem-dinheiro", "custo", Texto.dinheiro(custo));
                return;
            }
            Location destino = pontoSeguro(para);
            if (destino == null) {
                plugin.mensagens().enviar(p, "viagem-cancelada");
                return;
            }
            p.teleport(destino);
            p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            plugin.mensagens().enviar(p, "viagem-chegou", "lugar", para.formatado(), "custo", Texto.dinheiro(custo));
        }, 20L * segundos);
        viagens.put(p.getUniqueId(), t);
    }

    /** Onde o jogador aparece ao chegar: no chão, no centro do lugar (ou no spawn, se for a capital). */
    public Location pontoSeguro(Lugar l) {
        Location c = l.local();
        if (c == null) {
            return null;
        }
        if (l.tipo() == Lugar.Tipo.CAPITAL) {
            return c.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
        }
        Block topo = c.getWorld().getHighestBlockAt(c.getBlockX(), c.getBlockZ());
        return new Location(c.getWorld(), c.getBlockX() + 0.5, topo.getY() + 1, c.getBlockZ() + 0.5, c.getYaw(), 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent e) {
        if (viagens.containsKey(e.getPlayer().getUniqueId()) && e.hasChangedBlock()) {
            BukkitTask t = viagens.remove(e.getPlayer().getUniqueId());
            t.cancel();
            plugin.mensagens().enviar(e.getPlayer(), "viagem-cancelada");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoLevarDano(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && viagens.containsKey(p.getUniqueId())) {
            viagens.remove(p.getUniqueId()).cancel();
            plugin.mensagens().enviar(p, "viagem-cancelada");
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        BukkitTask t = viagens.remove(e.getPlayer().getUniqueId());
        if (t != null) {
            t.cancel();
        }
        rumo.remove(e.getPlayer().getUniqueId());
        rumoAte.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ PROTEÇÃO DAS CIDADES

    private boolean bloquear(Player p, Location l) {
        if (p != null && p.hasPermission(PERM_CONSTRUIR)) {
            return false;
        }
        if (protegidoEm(l) != null) {
            if (p != null) {
                p.sendActionBar(Texto.cor(plugin.mensagens().bruta("lugar-protegido")));
                plugin.hud().pausar(p, 1500);
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent e) {
        if (bloquear(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent e) {
        if (bloquear(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent e) {
        if (bloquear(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEncherBalde(PlayerBucketFillEvent e) {
        if (bloquear(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPisar(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND
                && protegidoEm(e.getClickedBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrarQuadro(HangingBreakByEntityEvent e) {
        Player p = e.getRemover() instanceof Player pl ? pl : null;
        if ((p == null || !p.hasPermission(PERM_CONSTRUIR)) && protegidoEm(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> protegidoEm(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> protegidoEm(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQueimar(BlockBurnEvent e) {
        if (protegidoEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAcender(BlockIgniteEvent e) {
        if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && protegidoEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        } else if (e.getPlayer() != null && bloquear(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMobMexer(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Player) && protegidoEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    /** GriefPrevention: ninguém cria terreno protegido dentro de uma cidade (sem depender do jar do GP). */
    @SuppressWarnings("unchecked")
    public void ligarGriefPrevention() {
        try {
            Class<? extends Event> evento = (Class<? extends Event>) Class.forName("me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent");
            Bukkit.getPluginManager().registerEvent(evento, this, EventPriority.HIGH, (ouvinte, ev) -> {
                if (!evento.isInstance(ev)) {
                    return;
                }
                try {
                    Object claim = ev.getClass().getMethod("getClaim").invoke(ev);
                    Location a = (Location) claim.getClass().getMethod("getLesserBoundaryCorner").invoke(claim);
                    Location b = (Location) claim.getClass().getMethod("getGreaterBoundaryCorner").invoke(claim);
                    CommandSender quem = (CommandSender) ev.getClass().getMethod("getCreator").invoke(ev);
                    if (quem instanceof Player p && p.hasPermission(PERM_CONSTRUIR)) {
                        return;
                    }
                    for (Lugar l : lugares.values()) {
                        if (!l.protegido() || l.raioZona() <= 0 || a.getWorld() == null || !a.getWorld().getName().equals(l.mundo())) {
                            continue;
                        }
                        double px = Math.max(a.getX(), Math.min(l.x(), b.getX()));
                        double pz = Math.max(a.getZ(), Math.min(l.z(), b.getZ()));
                        if (Math.hypot(px - l.x(), pz - l.z()) <= l.raioZona() + 4) {
                            ((org.bukkit.event.Cancellable) ev).setCancelled(true);
                            if (quem != null) {
                                plugin.mensagens().enviar(quem, "lugar-sem-terreno", "lugar", l.formatado());
                            }
                            return;
                        }
                    }
                } catch (ReflectiveOperationException | ClassCastException ignorado) {
                    // versão diferente do GP: sem esta proteção
                }
            }, plugin, true);
            plugin.getLogger().info("GriefPrevention detectado: não dá para proteger terreno dentro das cidades.");
        } catch (ClassNotFoundException ignorado) {
            // sem GriefPrevention
        }
    }

    // ------------------------------------------------------------------ COLOCAR ESTRUTURAS PRONTAS

    public boolean populando() {
        return populando;
    }

    /** Um pedido de estrutura: tipo, lista de estruturas possíveis (ou auto-vila/auto-taverna), anel de distância. */
    private record Pedido(Lugar.Tipo tipo, List<String> estruturas, double min, double max, int raioZona, boolean protegido,
                          int raioFoto, double angulo) {
    }

    /** Lê os planos do config.yml e coloca tudo o que falta, um lugar de cada vez. */
    public void popular(CommandSender quem, Runnable fim) {
        if (populando) {
            plugin.mensagens().enviar(quem, "lugares-ja-populando");
            return;
        }
        World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
        if (w == null) {
            plugin.mensagens().enviar(quem, "mundo-nao-encontrado", "mundo", plugin.regioes().mundoPrincipal());
            return;
        }
        Deque<Pedido> fila = new ArrayDeque<>();
        ConfigurationSection planos = plugin.getConfig().getConfigurationSection("lugares.planos");
        if (planos != null) {
            for (String k : planos.getKeys(false)) {
                ConfigurationSection c = planos.getConfigurationSection(k);
                Lugar.Tipo t = c == null ? null : Lugar.Tipo.buscar(c.getString("tipo"));
                if (t == null) {
                    continue;
                }
                long ja = lugares.values().stream().filter(l -> l.tipo() == t).count();
                int quer = c.getInt("quantidade", 1);
                List<Integer> anel = c.getIntegerList("anel");
                double base = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                for (int i = (int) ja; i < quer; i++) {
                    fila.add(new Pedido(t, c.getStringList("estruturas"), anel.isEmpty() ? 300 : anel.get(0),
                            anel.size() < 2 ? 1200 : anel.get(1), c.getInt("raio-zona", 0), c.getBoolean("protegido", false),
                            c.getInt("raio-foto", 48), base + i * (Math.PI * 2 / quer)));
                }
            }
        }
        if (fila.isEmpty()) {
            plugin.mensagens().enviar(quem, "lugares-nada-a-fazer");
            if (fim != null) {
                fim.run();
            }
            return;
        }
        populando = true;
        plugin.mensagens().enviar(quem, "lugares-populando", "total", String.valueOf(fila.size()));
        proximo(w, quem, fila, 0, 0, fim);
    }

    private void proximo(World w, CommandSender quem, Deque<Pedido> fila, int feitos, int tentativa, Runnable fim) {
        Pedido ped = fila.peek();
        if (ped == null) {
            populando = false;
            plugin.mensagens().enviar(quem, "lugares-pronto", "total", String.valueOf(feitos));
            if (fim != null) {
                fim.run();
            }
            return;
        }
        if (tentativa >= plugin.getConfig().getInt("lugares.tentativas", 40)) {
            plugin.getLogger().warning("Não achei lugar bom para " + ped.tipo() + " (desisti depois de " + tentativa + " tentativas).");
            fila.poll();
            Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, feitos, 0, fim), 2L);
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location centro = plugin.regioes().centro(w);
        double ang = ped.angulo() + rnd.nextDouble(-0.45, 0.45) + (tentativa > 15 ? rnd.nextDouble(-1.5, 1.5) : 0);
        double dist = rnd.nextDouble(ped.min(), ped.max());
        int x = (int) Math.round(centro.getX() + Math.cos(ang) * dist);
        int z = (int) Math.round(centro.getZ() + Math.sin(ang) * dist);
        Runnable tentarDeNovo = () -> Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, feitos, tentativa + 1, fim), 1L);
        String motivo = longeDeTudo(w, x, z, ped);
        if (motivo != null) {
            tentarDeNovo.run();
            return;
        }
        // 1ª olhada (barata): água, morro e bioma no centro
        Terreno.carregar(w, x - 24, z - 24, x + 24, z + 24).thenAccept(t1 -> {
            int[] alturas = new int[25];
            int agua = 0;
            int n = 0;
            for (int dx = -24; dx <= 24; dx += 12) {
                for (int dz = -24; dz <= 24; dz += 12) {
                    alturas[n++] = t1.chao(x + dx, z + dz);
                    if (t1.agua(x + dx, z + dz)) {
                        agua++;
                    }
                }
            }
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int a : alturas) {
                min = Math.min(min, a);
                max = Math.max(max, a);
            }
            int maxDesnivel = ped.tipo() == Lugar.Tipo.VILA ? 10 : 16;
            if (t1.agua(x, z) || agua > 5 || max - min > maxDesnivel) {
                tentarDeNovo.run();
                return;
            }
            int chao = t1.chao(x, z);
            Biome bioma = t1.bioma(x, chao + 1, z);
            String estrutura = escolherEstrutura(ped, bioma);
            if (estrutura == null) {
                tentarDeNovo.run();
                return;
            }
            final int ymin = min;
            final int ymax = max;
            // 2ª olhada (completa): nada de jogador na área inteira, e a foto para poder desfazer
            int r = ped.raioFoto();
            Terreno.carregar(w, x - r, z - r, x + r, z + r).thenAccept(t2 -> {
                if (t2.blocosDeJogador(x, z, r, Set.of()) > 0) {
                    tentarDeNovo.run();
                    return;
                }
                String id = novoId(ped.tipo());
                int fundo = ped.tipo() == Lugar.Tipo.MASMORRA ? ymin - 70 : ymin - 24;
                Instantaneo foto = Instantaneo.tirar(t2, x - r, fundo, z - r, x + r, ymax + 50, z + r);
                gravarFoto(id, foto);
                colocarComArea(w, estrutura, x, chao + 1, z, ok -> {
                    if (!ok) {
                        plugin.getLogger().warning("O jogo não conseguiu colocar " + estrutura + " em " + x + ", " + z + "; tentando outro ponto.");
                        tentarDeNovo.run();
                        return;
                    }
                    Lugar l = new Lugar(id, novoNome(ped.tipo()), ped.tipo(), w.getName(), x, chao + 1, z, ped.raioZona(), ped.protegido(), estrutura);
                    registrar(l);
                    plugin.getLogger().info("Lugar novo: " + Texto.semCor(l.nome()) + " (" + estrutura + ") em " + x + ", " + (chao + 1) + ", " + z);
                    plugin.mensagens().enviar(quem, "lugar-criado", "lugar", l.formatado(), "x", String.valueOf(x), "z", String.valueOf(z));
                    plugin.npcHook().povoar(l);
                    fila.poll();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> proximo(w, quem, fila, feitos + 1, 0, fim), 40L);
                });
            });
        });
    }

    /** null = ponto bom; senão, o motivo de não servir (perto de jogadores, terrenos ou outros lugares). */
    private String longeDeTudo(World w, int x, int z, Pedido ped) {
        double minimo = plugin.getConfig().getDouble("lugares.distancia-minima-entre", 220);
        for (Lugar l : lugares.values()) {
            if (l.mundo().equals(w.getName()) && Math.hypot(l.x() - x, l.z() - z) < (l.tipo() == Lugar.Tipo.CAPITAL ? Math.max(minimo, 200) : minimo)) {
                return "perto de " + l.id();
            }
        }
        double dj = plugin.getConfig().getDouble("lugares.distancia-de-jogadores", 160);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(w) && Math.hypot(p.getLocation().getX() - x, p.getLocation().getZ() - z) < dj) {
                return "jogador online perto";
            }
        }
        for (Location l : locaisDeJogadores(w)) {
            if (Math.hypot(l.getX() - x, l.getZ() - z) < dj) {
                return "base de jogador perto";
            }
        }
        return null;
    }

    private List<Location> cacheLocais;
    private long cacheLocaisEm;

    /** Onde os jogadores costumam ficar: última posição, cama e terrenos do GriefPrevention. */
    private List<Location> locaisDeJogadores(World w) {
        if (cacheLocais != null && System.currentTimeMillis() - cacheLocaisEm < 120_000) {
            return cacheLocais;
        }
        List<Location> l = new ArrayList<>();
        for (OfflinePlayer o : Bukkit.getOfflinePlayers()) {
            try {
                Location a = o.getLocation();
                if (a != null && w.equals(a.getWorld())) {
                    l.add(a);
                }
                Location b = o.getRespawnLocation(false);
                if (b != null && w.equals(b.getWorld())) {
                    l.add(b);
                }
            } catch (RuntimeException ignorado) {
                // arquivo do jogador estranho
            }
        }
        try {
            Object gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention").getField("instance").get(null);
            Object ds = gp.getClass().getField("dataStore").get(gp);
            Collection<?> claims = (Collection<?>) ds.getClass().getMethod("getClaims").invoke(ds);
            for (Object c : claims) {
                Location a = (Location) c.getClass().getMethod("getLesserBoundaryCorner").invoke(c);
                Location b = (Location) c.getClass().getMethod("getGreaterBoundaryCorner").invoke(c);
                if (a != null && w.equals(a.getWorld())) {
                    l.add(a);
                    l.add(b);
                    l.add(new Location(w, (a.getX() + b.getX()) / 2, 0, (a.getZ() + b.getZ()) / 2));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignorado) {
            // sem GriefPrevention
        }
        cacheLocais = l;
        cacheLocaisEm = System.currentTimeMillis();
        return l;
    }

    private String escolherEstrutura(Pedido ped, Biome bioma) {
        String b = bioma == null ? "plains" : bioma.getKey().getKey();
        List<String> opcoes = new ArrayList<>();
        for (String e : ped.estruturas()) {
            if (e.equalsIgnoreCase("auto-vila")) {
                opcoes.add(vilaDoBioma(b));
            } else if (e.equalsIgnoreCase("auto-taverna")) {
                opcoes.add("nova_structures:tavern_" + madeiraDoBioma(b));
            } else {
                opcoes.add(e);
            }
        }
        if (opcoes.isEmpty()) {
            return null;
        }
        return opcoes.get(ThreadLocalRandom.current().nextInt(opcoes.size()));
    }

    private static String vilaDoBioma(String b) {
        if (b.contains("desert") || b.contains("badlands")) {
            return "minecraft:village_desert";
        }
        if (b.contains("savanna")) {
            return "minecraft:village_savanna";
        }
        if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove")) {
            return "minecraft:village_snowy";
        }
        if (b.contains("taiga")) {
            return "minecraft:village_taiga";
        }
        if (b.contains("birch")) {
            return "nova_structures:village_birch";
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return "nova_structures:village_swamp";
        }
        if (b.contains("jungle")) {
            return "nova_structures:village_jungle";
        }
        if (b.contains("dark_forest")) {
            return "nova_structures:village_dappled";
        }
        return "minecraft:village_plains";
    }

    private static String madeiraDoBioma(String b) {
        if (b.contains("desert") || b.contains("badlands")) {
            return "desert";
        }
        if (b.contains("savanna")) {
            return "acacia";
        }
        if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove")) {
            return "snowy";
        }
        if (b.contains("taiga")) {
            return "spruce";
        }
        if (b.contains("birch")) {
            return "birch";
        }
        if (b.contains("mangrove")) {
            return "mangrove";
        }
        if (b.contains("swamp")) {
            return "swamp";
        }
        if (b.contains("jungle")) {
            return "jungle";
        }
        if (b.contains("cherry")) {
            return "cherry";
        }
        if (b.contains("pale")) {
            return "pale";
        }
        if (b.contains("dark_forest")) {
            return "dark_oak";
        }
        return "oak";
    }

    /**
     * Carrega (sem travar) e segura carregada uma área grande em volta do ponto, roda o /place structure e solta os
     * chunks depois. O jogo só coloca a estrutura se todos os chunks que ela ocupa estiverem carregados.
     */
    public void colocarComArea(World w, String estrutura, int x, int y, int z, Consumer<Boolean> resultado) {
        int r = plugin.getConfig().getInt("lugares.raio-carregar", 144);
        Terreno.carregar(w, x - r, z - r, x + r, z + r).thenAccept(t -> {
            List<long[]> segurados = new ArrayList<>();
            for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
                for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                    if (w.addPluginChunkTicket(cx, cz, plugin)) {
                        segurados.add(new long[]{cx, cz});
                    }
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                colocarEstrutura(w, estrutura, x, y, z, ok -> {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (long[] c : segurados) {
                            w.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
                        }
                    }, 200L);
                    resultado.accept(ok);
                });
            }, 5L);
        });
    }

    /** Roda o /place structure do próprio jogo e diz se deu certo. */
    public void colocarEstrutura(World w, String estrutura, int x, int y, int z, Consumer<Boolean> resultado) {
        List<String> saida = new ArrayList<>();
        CommandSender captura = Bukkit.createCommandSender(c -> saida.add(PlainTextComponentSerializer.plainText().serialize(c)));
        String cmd = "execute in " + w.getKey() + " run place structure " + estrutura + " " + x + " " + y + " " + z;
        boolean rodou;
        try {
            rodou = Bukkit.dispatchCommand(captura, cmd);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao rodar: " + cmd, e);
            rodou = false;
        }
        String texto = String.join(" | ", saida);
        boolean ok = rodou && (texto.toLowerCase(Locale.ROOT).contains("generated") || texto.toLowerCase(Locale.ROOT).contains("success"));
        plugin.getLogger().info("[estrutura] " + cmd + " -> " + (texto.isEmpty() ? "(sem resposta)" : texto));
        resultado.accept(ok);
    }

    private String novoId(Lugar.Tipo t) {
        String base = t.name().toLowerCase(Locale.ROOT);
        int i = 1;
        while (lugares.containsKey(base + "_" + i)) {
            i++;
        }
        return base + "_" + i;
    }

    private String novoNome(Lugar.Tipo t) {
        List<String> nomes = new ArrayList<>(plugin.getConfig().getStringList("lugares.nomes." + t.name().toLowerCase(Locale.ROOT)));
        for (Lugar l : lugares.values()) {
            nomes.remove(l.nome());
        }
        if (nomes.isEmpty()) {
            return t.nome() + " " + (lugares.values().stream().filter(l -> l.tipo() == t).count() + 1);
        }
        return nomes.get(ThreadLocalRandom.current().nextInt(nomes.size()));
    }

    // ------------------------------------------------------------------ FOTOS (DESFAZER)

    public File arquivoFoto(String id) {
        return new File(plugin.getDataFolder(), "desfazer/" + id + ".dat.gz");
    }

    public void gravarFoto(String id, Instantaneo foto) {
        File f = arquivoFoto(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                foto.gravar(f);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a foto de " + id, e);
            }
        });
    }

    /** Põe o terreno de volta como era antes da obra 'id' (e tira o lugar do mapa). */
    public void desfazer(CommandSender quem, String id) {
        File f = arquivoFoto(id);
        if (!f.exists()) {
            plugin.mensagens().enviar(quem, "desfazer-sem-foto", "id", id);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Instantaneo foto = Instantaneo.ler(f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    World w = Bukkit.getWorld(foto.mundo());
                    if (w == null) {
                        plugin.mensagens().enviar(quem, "mundo-nao-encontrado", "mundo", foto.mundo());
                        return;
                    }
                    Terreno.carregar(w, foto.x1(), foto.z1(), foto.x2(), foto.z2()).thenAccept(t -> {
                        var obra = foto.restauracao(t);
                        plugin.mensagens().enviar(quem, "desfazer-comecou", "id", id, "blocos", String.valueOf(obra.tamanho()));
                        if (id.equals("capital")) {
                            plugin.capital().removerExtras();
                        }
                        obra.executar(plugin, 8000, () -> {
                            remover(id);
                            plugin.mensagens().enviar(quem, "desfazer-pronto", "id", id);
                        });
                    });
                });
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Foto de " + id + " ilegível", e);
                plugin.mensagens().enviar(quem, "desfazer-sem-foto", "id", id);
            }
        });
    }

    /** Coloca uma estrutura onde o admin está (com foto para desfazer). */
    public void colocarAqui(Player p, String estrutura, Lugar.Tipo tipo, int raioZona, String nome) {
        Location l = p.getLocation();
        World w = l.getWorld();
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int r = Math.max(32, raioZona + 24);
        Terreno.carregar(w, x - r, z - r, x + r, z + r).thenAccept(t -> {
            String id = novoId(tipo);
            int chao = t.chao(x, z);
            gravarFoto(id, Instantaneo.tirar(t, x - r, chao - (tipo == Lugar.Tipo.MASMORRA ? 70 : 30), z - r, x + r, chao + 60, z + r));
            colocarComArea(w, estrutura, x, chao + 1, z, ok -> {
                if (!ok) {
                    plugin.mensagens().enviar(p, "estrutura-falhou", "estrutura", estrutura);
                    return;
                }
                Lugar novo = new Lugar(id, nome == null || nome.isBlank() ? novoNome(tipo) : nome, tipo, w.getName(), x, chao + 1, z,
                        raioZona, raioZona > 0 && tipo.viagem(), estrutura);
                registrar(novo);
                plugin.npcHook().povoar(novo);
                plugin.mensagens().enviar(p, "lugar-criado", "lugar", novo.formatado(), "x", String.valueOf(x), "z", String.valueOf(z));
            });
        });
    }

    /** Item de menu genérico para outras telas. */
    public ItemStack icone(Lugar l) {
        return Menu.criar(l.tipo().icone(), l.formatado(), List.of());
    }
}
