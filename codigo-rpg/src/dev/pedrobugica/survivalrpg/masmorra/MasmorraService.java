package dev.pedrobugica.survivalrpg.masmorra;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.construcao.Obra;
import dev.pedrobugica.survivalrpg.construcao.Terreno;
import dev.pedrobugica.survivalrpg.item.Raridade;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.mob.MobService;
import dev.pedrobugica.survivalrpg.util.Menu;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Masmorras com instância: o grupo entra pelo Portão das Masmorras da capital, limpa três salas (os portões de
 * grade fecham durante a luta) e enfrenta o chefe, que tem barra de vida e golpes especiais. Lá dentro ninguém
 * perde itens ao morrer (renasce na entrada). Vencer dá XP, dinheiro e dois itens raros; cada masmorra tem recarga.
 */
public final class MasmorraService implements Listener {

    private static final String PERM_ADMIN = "survivalrpg.admin.construir";

    public record Def(String id, String nome, int nivel, int nivelMinimo, Tema tema, double dinheiro, Material icone) {
    }

    /** Uma cópia de uma masmorra no mundo; só um grupo por vez. */
    public final class Instancia {
        final Def def;
        final int idx;
        final World w;
        final int ox;
        final int oy;
        final int oz;
        final List<Planta.Sala> salas;
        boolean construida;
        final List<UUID> jogadores = new ArrayList<>();
        final Map<UUID, Location> volta = new HashMap<>();
        final Map<UUID, Integer> fora = new HashMap<>();
        LivingEntity chefe;
        BossBar barra;
        long inicio;
        long proxSismo;
        long proxInvocar;
        long proxEspecial;
        boolean furia;
        boolean vencida;
        final List<Block> teias = new ArrayList<>();

        Instancia(Def def, int idx, World w, int ox, int oy, int oz) {
            this.def = def;
            this.idx = idx;
            this.w = w;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.salas = Planta.desenhar(new Obra(w), def.tema(), ox, oy, oz, new Random(idx));
        }

        String chave() {
            return def.id() + "-" + idx;
        }

        boolean contem(Location l) {
            return l.getWorld() != null && l.getWorld().equals(w) && l.getX() >= ox - 2 && l.getX() <= ox + Planta.comprimento() + 2
                    && l.getZ() >= oz - 16 && l.getZ() <= oz + 16 && l.getY() >= oy - 4 && l.getY() <= oy + 18;
        }

        Location inicioSala() {
            Planta.Sala s = salas.get(0);
            return new Location(w, s.centroX(), oy + 1, s.centroZ(), -90, 0);
        }

        boolean livre() {
            return construida && jogadores.isEmpty();
        }

        List<Player> online() {
            List<Player> l = new ArrayList<>();
            for (UUID u : jogadores) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    l.add(p);
                }
            }
            return l;
        }
    }

    private final SurvivalRpgPlugin plugin;
    private final File arquivo;
    private final NamespacedKey chaveMob;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final List<Instancia> instancias = new ArrayList<>();
    private final List<Location> entradas = new ArrayList<>();
    private final Map<UUID, Instancia> porJogador = new HashMap<>();
    private final Map<String, Long> recargas = new HashMap<>();
    private final List<String> construidas = new ArrayList<>();
    private BukkitTask tarefa;
    private int tique;

    public MasmorraService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "masmorras.yml");
        this.chaveMob = new NamespacedKey(plugin, "masmorra");
    }

    // ------------------------------------------------------------------ CARREGAR

    public void carregar() {
        defs.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("masmorras.lista");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                Tema t = Tema.buscar(c.getString("tema", id));
                Material icone = switch (t) {
                    case CRIPTA -> Material.SKELETON_SKULL;
                    case ARANHAS -> Material.SPIDER_EYE;
                    case FORTALEZA -> Material.WITHER_SKELETON_SKULL;
                };
                defs.put(id, new Def(id, c.getString("nome", id), c.getInt("nivel", 10), c.getInt("nivel-minimo", 1), t,
                        c.getDouble("dinheiro", 500), icone));
            }
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        entradas.clear();
        for (String e : y.getStringList("entradas")) {
            String[] p = e.split(",");
            World w = p.length == 4 ? Bukkit.getWorld(p[0]) : null;
            if (w != null) {
                entradas.add(new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])));
            }
        }
        construidas.clear();
        construidas.addAll(y.getStringList("construidas"));
        recargas.clear();
        ConfigurationSection r = y.getConfigurationSection("recargas");
        if (r != null) {
            for (String k : r.getKeys(false)) {
                recargas.put(k, r.getLong(k));
            }
        }
        montarInstancias();
    }

    private void gravar() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (Location e : entradas) {
            l.add(e.getWorld().getName() + "," + e.getX() + "," + e.getY() + "," + e.getZ());
        }
        y.set("entradas", l);
        y.set("construidas", construidas);
        long agora = System.currentTimeMillis();
        recargas.entrySet().removeIf(e -> e.getValue() < agora);
        for (Map.Entry<String, Long> e : recargas.entrySet()) {
            y.set("recargas." + e.getKey(), e.getValue());
        }
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar masmorras.yml", e);
        }
    }

    private void montarInstancias() {
        instancias.clear();
        World w = Bukkit.getWorld(plugin.getConfig().getString("masmorras.mundo", plugin.regioes().mundoPrincipal()));
        if (w == null) {
            return;
        }
        WorldBorder b = w.getWorldBorder();
        double meia = Math.min(b.getSize() / 2 - 300, plugin.getConfig().getDouble("masmorras.distancia", 40000));
        int baseX = (int) (b.getCenter().getX() - meia);
        int baseZ = (int) (b.getCenter().getZ() - meia);
        int oy = plugin.getConfig().getInt("masmorras.altura", 270);
        int porDef = Math.max(1, plugin.getConfig().getInt("masmorras.instancias-por-masmorra", 2));
        int d = 0;
        for (Def def : defs.values()) {
            for (int i = 0; i < porDef; i++) {
                Instancia in = new Instancia(def, i, w, baseX + i * (Planta.comprimento() + 40), oy, baseZ + d * 80);
                in.construida = construidas.contains(in.chave());
                instancias.add(in);
            }
            d++;
        }
    }

    public void registrarEntrada(Location l) {
        entradas.removeIf(e -> e.getWorld().equals(l.getWorld()) && e.distanceSquared(l) < 400);
        entradas.add(l);
        gravar();
    }

    /** Constrói as instâncias que faltam (uma de cada vez, sem travar). */
    public void prepararTodas() {
        for (Instancia in : instancias) {
            if (!in.construida) {
                construir(in, () -> prepararTodas());
                return;
            }
        }
    }

    private void construir(Instancia in, Runnable depois) {
        Obra o = new Obra(in.w);
        Planta.desenhar(o, in.def.tema(), in.ox, in.oy, in.oz, new Random(in.idx));
        plugin.getLogger().info("Construindo a masmorra " + in.chave() + " em " + in.ox + ", " + in.oy + ", " + in.oz + "...");
        Terreno.carregar(in.w, in.ox - 2, in.oz - 16, in.ox + Planta.comprimento() + 2, in.oz + 16).thenAccept(t ->
                o.executar(plugin, 5000, () -> {
                    in.construida = true;
                    construidas.add(in.chave());
                    gravar();
                    resetar(in);
                    plugin.getLogger().info("Masmorra " + in.chave() + " pronta.");
                    if (depois != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, depois, 20L);
                    }
                }));
    }

    public void reconstruir(Runnable depois) {
        construidas.clear();
        for (Instancia in : instancias) {
            in.construida = false;
        }
        gravar();
        prepararTodas();
        if (depois != null) {
            depois.run();
        }
    }

    // ------------------------------------------------------------------ TAREFA

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
        for (Instancia in : instancias) {
            if (!in.jogadores.isEmpty()) {
                encerrar(in, false);
            }
        }
        gravar();
    }

    private void tick() {
        tique++;
        if (tique % 2 == 0) {
            for (Location e : entradas) {
                if (e.isChunkLoaded() && !e.getWorld().getNearbyPlayers(e, 24).isEmpty()) {
                    e.getWorld().spawnParticle(Particle.PORTAL, e.clone().add(0, 2.5, 3), 30, 0.9, 1.4, 0.2, 0.4);
                }
            }
        }
        long agora = System.currentTimeMillis();
        for (Instancia in : instancias) {
            if (in.jogadores.isEmpty()) {
                continue;
            }
            // quem saiu da área (home, spawn...) sai da corrida
            for (UUID u : new ArrayList<>(in.jogadores)) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) {
                    sairDaCorrida(in, u, null);
                    continue;
                }
                if (!in.contem(p.getLocation()) && !p.isDead()) {
                    int n = in.fora.merge(u, 1, Integer::sum);
                    if (n >= 10) {
                        sairDaCorrida(in, u, null);
                        plugin.mensagens().enviar(p, "masmorra-saiu");
                    }
                } else {
                    in.fora.remove(u);
                }
            }
            if (in.jogadores.isEmpty()) {
                resetar(in);
                continue;
            }
            if (agora - in.inicio > plugin.getConfig().getLong("masmorras.tempo-maximo-minutos", 40) * 60_000L && !in.vencida) {
                for (Player p : in.online()) {
                    plugin.mensagens().enviar(p, "masmorra-tempo-esgotado");
                }
                encerrar(in, true);
                continue;
            }
            for (Planta.Sala s : in.salas) {
                atualizarSala(in, s, agora);
            }
            if (in.chefe != null && in.chefe.isValid() && !in.chefe.isDead()) {
                ataquesDoChefe(in, agora);
            }
        }
    }

    private boolean alguemDentro(Instancia in, Planta.Sala s) {
        for (Player p : in.online()) {
            if (!p.isDead() && s.contem(p.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void atualizarSala(Instancia in, Planta.Sala s, long agora) {
        if (s.estado == Planta.Estado.LIMPA) {
            return;
        }
        boolean dentro = alguemDentro(in, s);
        if (s.estado == Planta.Estado.ESPERANDO) {
            if (dentro && !in.vencida) {
                s.estado = Planta.Estado.LUTANDO;
                portao(in, s.portaoEntrada, true);
                portao(in, s.portaoSaida, true);
                if (s.chefe) {
                    invocarChefe(in, s);
                } else {
                    onda(in, s);
                }
            }
            return;
        }
        // LUTANDO
        s.mobs.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || !e.isValid() || e.isDead();
        });
        if (!dentro) {
            if (s.vaziaDesde == 0) {
                s.vaziaDesde = agora;
            } else if (agora - s.vaziaDesde > 10_000) {
                // todo mundo caiu ou fugiu: a sala volta ao começo
                for (UUID u : s.mobs) {
                    Entity e = Bukkit.getEntity(u);
                    if (e != null) {
                        e.remove();
                    }
                }
                if (s.chefe) {
                    tirarChefe(in);
                }
                s.reiniciar();
                portao(in, s.portaoEntrada, false);
                for (Player p : in.online()) {
                    plugin.mensagens().enviar(p, "masmorra-sala-reiniciou");
                }
            }
            return;
        }
        s.vaziaDesde = 0;
        if (!s.chefe && s.mobs.isEmpty()) {
            if (s.ondaAtual < s.ondas) {
                onda(in, s);
            } else {
                s.estado = Planta.Estado.LIMPA;
                portao(in, s.portaoEntrada, false);
                portao(in, s.portaoSaida, false);
                for (Player p : in.online()) {
                    p.showTitle(Title.title(Texto.cor(plugin.mensagens().bruta("masmorra-sala-limpa")), Texto.cor(""),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
                    p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.8f);
                }
            }
        }
    }

    private void portao(Instancia in, List<int[]> blocos, boolean fechar) {
        for (int[] b : blocos) {
            Block bl = in.w.getBlockAt(b[0], b[1], b[2]);
            bl.setType(fechar ? Material.IRON_BARS : Material.AIR, true);
        }
        if (!blocos.isEmpty()) {
            int[] b = blocos.get(blocos.size() / 2);
            in.w.playSound(new Location(in.w, b[0], b[1], b[2]), fechar ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ MONSTROS

    private Location pontoNaSala(Instancia in, Planta.Sala s, ThreadLocalRandom r) {
        double x = r.nextDouble(s.x1 + 2, s.x2 - 1);
        double z = r.nextDouble(s.z1 + 2, s.z2 - 1);
        return new Location(in.w, Math.floor(x) + 0.5, in.oy + 1, Math.floor(z) + 0.5);
    }

    private LivingEntity criarMob(Instancia in, Location l, EntityType tipo, int nivel, MobService.Tier tier) {
        Entity e = in.w.spawnEntity(l, tipo, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        plugin.mobs().aplicar(le, nivel, tier, false);
        le.setRemoveWhenFarAway(false);
        le.setPersistent(false);
        le.getPersistentDataContainer().set(chaveMob, PersistentDataType.STRING, in.chave());
        if (le instanceof PiglinAbstract pa) {
            pa.setImmuneToZombification(true);
        }
        if (le instanceof Mob m) {
            List<Player> alvos = in.online();
            if (!alvos.isEmpty()) {
                m.setTarget(alvos.get(ThreadLocalRandom.current().nextInt(alvos.size())));
            }
        }
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        return le;
    }

    private void onda(Instancia in, Planta.Sala s) {
        s.ondaAtual++;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int jogadores = Math.max(1, in.online().size());
        int quantos = 3 + s.ondaAtual + 2 * (jogadores - 1) + in.salas.indexOf(s);
        int elites = in.salas.indexOf(s) >= 2 ? s.ondaAtual : (s.ondaAtual == s.ondas && s.ondas > 1 ? 1 : 0);
        List<EntityType> tipos = in.def.tema().mobs();
        for (int i = 0; i < quantos + elites; i++) {
            MobService.Tier tier = i < elites ? MobService.Tier.ELITE : MobService.Tier.NORMAL;
            int nivel = Math.max(1, in.def.nivel() + r.nextInt(-2, 3));
            LivingEntity m = criarMob(in, pontoNaSala(in, s, r), tipos.get(r.nextInt(tipos.size())), nivel, tier);
            if (m != null) {
                s.mobs.add(m.getUniqueId());
            }
        }
        for (Player p : in.online()) {
            p.showTitle(Title.title(Texto.cor(""), Texto.cor(plugin.mensagens().bruta("masmorra-onda"), "onda", String.valueOf(s.ondaAtual),
                    "ondas", String.valueOf(Math.max(1, s.ondas))), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.2f);
        }
    }

    private void invocarChefe(Instancia in, Planta.Sala s) {
        Tema t = in.def.tema();
        Location l = new Location(in.w, s.centroX(), in.oy + 1, s.centroZ(), 90, 0);
        LivingEntity c = criarMob(in, l, t.chefe(), in.def.nivel() + 3, MobService.Tier.CAMPEAO);
        if (c == null) {
            return;
        }
        plugin.mobs().fixarNome(c, t.nomeChefe());
        AttributeInstance vida = c.getAttribute(Attribute.MAX_HEALTH);
        int n = Math.max(1, in.online().size());
        if (vida != null) {
            vida.setBaseValue(Math.min(1024, vida.getBaseValue() * plugin.getConfig().getDouble("masmorras.chefe-vida", 2.5) * (1 + 0.5 * (n - 1))));
            c.setHealth(vida.getValue());
        }
        AttributeInstance escala = c.getAttribute(Attribute.SCALE);
        if (escala != null) {
            escala.setBaseValue(t.escalaChefe());
        }
        AttributeInstance kb = c.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(0.8);
        }
        EntityEquipment eq = c.getEquipment();
        if (eq != null) {
            if (t.capaceteChefe() != null) {
                eq.setHelmet(new ItemStack(t.capaceteChefe()));
                eq.setHelmetDropChance(0);
            }
            if (t.armaChefe() != null) {
                eq.setItemInMainHand(new ItemStack(t.armaChefe()));
                eq.setItemInMainHandDropChance(0);
            }
        }
        s.mobs.add(c.getUniqueId());
        in.chefe = c;
        in.furia = false;
        long agora = System.currentTimeMillis();
        in.proxSismo = agora + 6000;
        in.proxInvocar = agora + 9000;
        in.proxEspecial = agora + 12000;
        in.barra = BossBar.bossBar(Texto.cor(t.nomeChefe()), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        for (Player p : in.online()) {
            p.showBossBar(in.barra);
            p.showTitle(Title.title(Texto.cor(t.nomeChefe()), Texto.cor(plugin.mensagens().bruta("masmorra-chefe-subtitulo")),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600))));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        }
    }

    private void tirarChefe(Instancia in) {
        if (in.chefe != null && in.chefe.isValid()) {
            in.chefe.remove();
        }
        in.chefe = null;
        if (in.barra != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(in.barra);
            }
            in.barra = null;
        }
    }

    private void ataquesDoChefe(Instancia in, long agora) {
        LivingEntity c = in.chefe;
        AttributeInstance max = c.getAttribute(Attribute.MAX_HEALTH);
        double frac = max == null ? 1 : c.getHealth() / max.getValue();
        if (in.barra != null) {
            in.barra.progress((float) Math.max(0, Math.min(1, frac)));
        }
        Planta.Sala sala = in.salas.get(in.salas.size() - 1);
        List<Player> alvos = new ArrayList<>();
        for (Player p : in.online()) {
            if (!p.isDead() && sala.contem(p.getLocation())) {
                alvos.add(p);
            }
        }
        if (alvos.isEmpty()) {
            return;
        }
        if (c instanceof Mob m && (m.getTarget() == null || !alvos.contains(m.getTarget()))) {
            m.setTarget(alvos.get(ThreadLocalRandom.current().nextInt(alvos.size())));
        }
        if (!in.furia && frac <= 0.5) {
            in.furia = true;
            c.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, true));
            c.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, true));
            for (Player p : alvos) {
                plugin.mensagens().enviar(p, "masmorra-chefe-furia", "chefe", in.def.tema().nomeChefe());
                p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.7f);
            }
        }
        double nivel = in.def.nivel();
        double ritmo = in.furia ? 0.7 : 1;
        if (agora >= in.proxSismo) {
            in.proxSismo = agora + (long) (7000 * ritmo);
            Location l = c.getLocation();
            for (int k = 0; k < 32; k++) {
                double a = Math.PI * 2 * k / 32;
                in.w.spawnParticle(Particle.DUST_PLUME, l.clone().add(Math.cos(a) * 5, 0.2, Math.sin(a) * 5), 2, 0.1, 0, 0.1, 0);
            }
            in.w.playSound(l, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!c.isValid() || c.isDead()) {
                    return;
                }
                Location lc = c.getLocation();
                in.w.spawnParticle(Particle.EXPLOSION, lc, 3, 1.5, 0.2, 1.5, 0);
                in.w.playSound(lc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.7f);
                for (Player p : alvos) {
                    if (p.isValid() && !p.isDead() && p.getLocation().distanceSquared(lc) <= 5.5 * 5.5) {
                        p.damage(4 + nivel * 0.35, c);
                        Vector v = p.getLocation().toVector().subtract(lc.toVector()).setY(0);
                        p.setVelocity((v.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : v.normalize().multiply(0.9)).setY(0.6));
                    }
                }
            }, 24L);
        }
        if (agora >= in.proxInvocar) {
            in.proxInvocar = agora + (long) (12000 * ritmo);
            Planta.Sala s = in.salas.get(in.salas.size() - 1);
            int n = 2 + alvos.size() / 2;
            List<EntityType> tipos = in.def.tema().mobs();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < n; i++) {
                Location l = c.getLocation().add(r.nextDouble(-3, 3), 0, r.nextDouble(-3, 3));
                if (!l.getBlock().isPassable()) {
                    l = c.getLocation();
                }
                LivingEntity m = criarMob(in, l, tipos.get(r.nextInt(tipos.size())), Math.max(1, in.def.nivel() - 2), MobService.Tier.NORMAL);
                if (m != null) {
                    s.mobs.add(m.getUniqueId());
                }
            }
            for (Player p : alvos) {
                plugin.mensagens().enviar(p, "masmorra-chefe-invoca", "chefe", in.def.tema().nomeChefe());
            }
        }
        if (agora >= in.proxEspecial) {
            in.proxEspecial = agora + (long) (10000 * ritmo);
            especial(in, c, alvos, nivel);
        }
    }

    private void especial(Instancia in, LivingEntity c, List<Player> alvos, double nivel) {
        switch (in.def.tema()) {
            case CRIPTA -> {
                for (Player p : alvos) {
                    for (int k = 0; k < 4; k++) {
                        Vector dir = p.getEyeLocation().toVector().subtract(c.getEyeLocation().toVector()).normalize()
                                .add(new Vector(ThreadLocalRandom.current().nextDouble(-0.08, 0.08), 0.05, ThreadLocalRandom.current().nextDouble(-0.08, 0.08)));
                        Arrow a = c.launchProjectile(Arrow.class, dir.multiply(1.9));
                        a.setDamage(2 + nivel * 0.08);
                        a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                        Bukkit.getScheduler().runTaskLater(plugin, a::remove, 80L);
                    }
                }
                in.w.playSound(c.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.2f, 0.6f);
            }
            case ARANHAS -> {
                for (Player p : alvos) {
                    Block b = p.getLocation().getBlock();
                    if (b.getType().isAir()) {
                        b.setType(Material.COBWEB, false);
                        in.teias.add(b);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (b.getType() == Material.COBWEB) {
                                b.setType(Material.AIR, false);
                            }
                            in.teias.remove(b);
                        }, 80L);
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, true));
                }
                in.w.playSound(c.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.4f, 0.5f);
            }
            case FORTALEZA -> {
                Location l = c.getLocation();
                for (int raio = 2; raio <= 7; raio++) {
                    final int r = raio;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (int k = 0; k < 24; k++) {
                            double a = Math.PI * 2 * k / 24;
                            in.w.spawnParticle(Particle.FLAME, l.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 2, 0.1, 0.1, 0.1, 0.01);
                        }
                    }, raio * 2L);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player p : alvos) {
                        if (p.isValid() && !p.isDead() && p.getLocation().distanceSquared(l) <= 49) {
                            p.setFireTicks(Math.max(p.getFireTicks(), 100));
                            p.damage(3 + nivel * 0.2, c);
                        }
                    }
                }, 14L);
                in.w.playSound(l, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
            }
        }
    }

    // ------------------------------------------------------------------ ENTRAR E SAIR

    public boolean naEntrada(Player p) {
        for (Location e : entradas) {
            if (e.getWorld().equals(p.getWorld()) && e.distanceSquared(p.getLocation()) <= 12 * 12) {
                return true;
            }
        }
        var cap = plugin.lugares().capital();
        return cap != null && cap.distancia(p.getLocation()) <= cap.raioZona() && plugin.getConfig().getBoolean("masmorras.entrar-de-qualquer-lugar-da-capital", false);
    }

    private String chaveRecarga(UUID u, Def d) {
        return u + "." + d.id();
    }

    public long recarga(UUID u, Def d) {
        return Math.max(0, recargas.getOrDefault(chaveRecarga(u, d), 0L) - System.currentTimeMillis());
    }

    public void menu(Player p, boolean daEntrada) {
        PerfilRpg r = plugin.perfis().perfil(p);
        Menu m = new Menu(3, Texto.cor(plugin.mensagens().bruta("menu-masmorras-titulo")));
        int[] slots = {11, 13, 15, 12, 14, 10, 16};
        int i = 0;
        for (Def d : defs.values()) {
            if (i >= slots.length) {
                break;
            }
            long livres = instancias.stream().filter(x -> x.def == d && x.livre()).count();
            long total = instancias.stream().filter(x -> x.def == d).count();
            long rec = recarga(p.getUniqueId(), d);
            List<String> lore = new ArrayList<>(List.of(
                    "<gray>Monstros de nível <white>" + d.nivel() + " <gray>(chefe: <white>" + (d.nivel() + 3) + "<gray>)",
                    "<gray>Nível mínimo: " + (r.nivel() >= d.nivelMinimo() ? "<green>" : "<red>") + d.nivelMinimo(),
                    "<gray>Chefe: " + d.tema().nomeChefe(),
                    "",
                    "<gray>Recompensa: <gold>$" + Texto.dinheiro(d.dinheiro()) + " <gray>+ <light_purple>2 itens raros <gray>+ XP",
                    "<gray>Salas livres: <white>" + livres + "<gray>/<white>" + total,
                    "<gray>Grupo: até <white>" + plugin.getConfig().getInt("grupo.maximo", 5) + " <gray>(chama quem estiver perto)",
                    ""));
            if (rec > 0) {
                lore.add("<red>Você pode voltar em " + (rec / 60000 + 1) + " min");
            } else if (!daEntrada || !naEntrada(p)) {
                lore.add("<yellow>Entre pelo Portão das Masmorras");
                lore.add("<yellow>em Vila Aurora (capital).");
            } else {
                lore.add("<green>Clique para entrar!");
            }
            m.item(slots[i++], Menu.criar(d.icone(), d.nome(), lore), (q, t) -> {
                q.closeInventory();
                if (!naEntrada(q)) {
                    plugin.mensagens().enviar(q, "masmorra-va-ao-portao");
                    Lugarzinho.apontarCapital(plugin, q);
                    return;
                }
                entrar(q, d);
            });
        }
        m.item(22, Menu.criar(Material.BOOK, "<yellow><bold>Como funciona",
                List.of("<gray>1. Junte um grupo: <white>/grupo convidar <nick>", "<gray>2. Fiquem perto do portal e cliquem",
                        "<gray>3. Limpem as salas (os portões fecham na luta)", "<gray>4. Derrotem o chefe!",
                        "", "<green>Morrer lá dentro não faz perder itens:", "<green>você renasce na entrada.",
                        "<gray>Para sair: <white>/masmorra sair")), null);
        m.preencher(Material.BLACK_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    /** Ajuda pequena para apontar a capital sem depender da ordem de criação dos serviços. */
    private static final class Lugarzinho {
        static void apontarCapital(SurvivalRpgPlugin plugin, Player p) {
            var cap = plugin.lugares().capital();
            if (cap != null) {
                plugin.lugares().apontar(p, cap);
            }
        }
    }

    public void entrar(Player quem, Def d) {
        if (porJogador.containsKey(quem.getUniqueId())) {
            plugin.mensagens().enviar(quem, "masmorra-ja-dentro");
            return;
        }
        List<Player> time = new ArrayList<>();
        var g = plugin.grupos().grupo(quem);
        if (g != null) {
            for (Player p : g.online()) {
                if (p.getWorld().equals(quem.getWorld()) && p.getLocation().distanceSquared(quem.getLocation()) <= 20 * 20
                        && !porJogador.containsKey(p.getUniqueId())) {
                    time.add(p);
                }
            }
        }
        if (!time.contains(quem)) {
            time.add(0, quem);
        }
        List<String> problemas = new ArrayList<>();
        for (Player p : time) {
            PerfilRpg r = plugin.perfis().perfil(p);
            if (r.nivel() < d.nivelMinimo()) {
                problemas.add(p.getName() + " (nível " + r.nivel() + ")");
            } else if (recarga(p.getUniqueId(), d) > 0) {
                problemas.add(p.getName() + " (recarga " + (recarga(p.getUniqueId(), d) / 60000 + 1) + " min)");
            }
        }
        if (!problemas.isEmpty()) {
            plugin.mensagens().enviar(quem, "masmorra-nao-pode", "jogadores", String.join(", ", problemas),
                    "nivel", String.valueOf(d.nivelMinimo()));
            return;
        }
        Instancia in = null;
        for (Instancia x : instancias) {
            if (x.def == d && x.livre()) {
                in = x;
                break;
            }
        }
        if (in == null) {
            boolean algumaConstruida = instancias.stream().anyMatch(x -> x.def == d && x.construida);
            plugin.mensagens().enviar(quem, algumaConstruida ? "masmorra-ocupada" : "masmorra-preparando");
            if (!algumaConstruida) {
                prepararTodas();
            }
            return;
        }
        resetar(in);
        in.inicio = System.currentTimeMillis();
        in.vencida = false;
        Location inicio = in.inicioSala();
        for (Player p : time) {
            in.jogadores.add(p.getUniqueId());
            in.volta.put(p.getUniqueId(), p.getLocation());
            porJogador.put(p.getUniqueId(), in);
            p.teleport(inicio);
            p.setFallDistance(0);
            p.showTitle(Title.title(Texto.cor(d.nome()), Texto.cor(plugin.mensagens().bruta("masmorra-entrou-subtitulo")),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(700))));
            p.playSound(inicio, Sound.AMBIENT_CAVE, 1f, 0.8f);
            plugin.mensagens().enviar(p, "masmorra-entrou", "masmorra", d.nome(), "jogadores", String.valueOf(time.size()));
        }
        plugin.getLogger().info(time.size() + " jogador(es) entraram em " + in.chave());
    }

    /** Sai da masmorra (comando ou fim). */
    public void sair(Player p, boolean avisar) {
        Instancia in = porJogador.get(p.getUniqueId());
        if (in == null) {
            if (avisar) {
                plugin.mensagens().enviar(p, "masmorra-fora");
            }
            return;
        }
        sairDaCorrida(in, p.getUniqueId(), p);
        if (avisar) {
            plugin.mensagens().enviar(p, "masmorra-saiu");
        }
    }

    private void sairDaCorrida(Instancia in, UUID u, Player p) {
        in.jogadores.remove(u);
        in.fora.remove(u);
        porJogador.remove(u);
        Location volta = in.volta.remove(u);
        Player jogador = p != null ? p : Bukkit.getPlayer(u);
        if (jogador != null) {
            if (in.barra != null) {
                jogador.hideBossBar(in.barra);
            }
            if (in.contem(jogador.getLocation())) {
                jogador.teleport(volta != null ? volta : saidaPadrao());
            }
        }
        if (in.jogadores.isEmpty()) {
            resetar(in);
        }
    }

    private Location saidaPadrao() {
        var cap = plugin.lugares().capital();
        if (cap != null) {
            Location l = plugin.lugares().pontoSeguro(cap);
            if (l != null) {
                return l;
            }
        }
        World w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
        return (w != null ? w : Bukkit.getWorlds().get(0)).getSpawnLocation();
    }

    private void encerrar(Instancia in, boolean levarDeVolta) {
        for (UUID u : new ArrayList<>(in.jogadores)) {
            Player p = Bukkit.getPlayer(u);
            if (levarDeVolta || p != null) {
                sairDaCorrida(in, u, p);
            }
        }
        resetar(in);
    }

    /** Limpa a instância: tira monstros e itens, fecha os portões certos e zera as salas. */
    private void resetar(Instancia in) {
        tirarChefe(in);
        for (Block b : in.teias) {
            if (b.getType() == Material.COBWEB) {
                b.setType(Material.AIR, false);
            }
        }
        in.teias.clear();
        if (in.construida) {
            Location c = new Location(in.w, in.ox + Planta.comprimento() / 2.0, in.oy + 6, in.oz);
            if (c.isChunkLoaded()) {
                for (Entity e : in.w.getNearbyEntities(c, Planta.comprimento() / 2.0 + 4, 14, 18)) {
                    if (!(e instanceof Player) && in.contem(e.getLocation())) {
                        e.remove();
                    }
                }
            }
        }
        for (Planta.Sala s : in.salas) {
            s.reiniciar();
            if (in.construida) {
                portao(in, s.portaoEntrada, false);
                portao(in, s.portaoSaida, !s.entrada);
            }
        }
        in.vencida = false;
        in.fora.clear();
    }

    // ------------------------------------------------------------------ CHEFE DERROTADO

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoMorrerMob(EntityDeathEvent e) {
        for (Instancia in : instancias) {
            if (in.chefe != null && in.chefe.getUniqueId().equals(e.getEntity().getUniqueId())) {
                vitoria(in);
                return;
            }
        }
    }

    private void vitoria(Instancia in) {
        in.vencida = true;
        Planta.Sala s = in.salas.get(in.salas.size() - 1);
        for (UUID u : s.mobs) {
            Entity e = Bukkit.getEntity(u);
            if (e != null && !e.equals(in.chefe)) {
                e.remove();
            }
        }
        s.mobs.clear();
        s.estado = Planta.Estado.LIMPA;
        portao(in, s.portaoEntrada, false);
        if (in.barra != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(in.barra);
            }
            in.barra = null;
        }
        in.chefe = null;
        Def d = in.def;
        List<String> nomes = new ArrayList<>();
        long recarga = plugin.getConfig().getLong("masmorras.recarga-minutos", 20) * 60_000L;
        for (Player p : in.online()) {
            nomes.add(p.getName());
            PerfilRpg r = plugin.perfis().perfil(p);
            plugin.perfis().darXp(p, d.nivel() * plugin.getConfig().getDouble("masmorras.xp-por-nivel", 120), "Chefe derrotado");
            if (plugin.economia().ativa()) {
                plugin.economia().depositar(p, d.dinheiro());
            }
            for (int k = 0; k < 2; k++) {
                Raridade rar = plugin.itens().sortearRaridade(plugin.getConfig().getIntegerList("masmorras.pesos-recompensa"), MobService.Tier.CAMPEAO);
                ItemStack it = plugin.itens().gerar(d.nivel() + 2, rar, r.classe());
                for (ItemStack sobra : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), sobra);
                }
            }
            recargas.put(chaveRecarga(p.getUniqueId(), d), System.currentTimeMillis() + recarga);
            p.showTitle(Title.title(Texto.cor(plugin.mensagens().bruta("masmorra-vitoria-titulo")),
                    Texto.cor(plugin.mensagens().bruta("masmorra-vitoria-subtitulo"), "dinheiro", Texto.dinheiro(d.dinheiro())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(4000), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            plugin.mensagens().enviar(p, "masmorra-saindo", "segundos", "20");
        }
        gravar();
        for (Player o : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(o, "masmorra-anuncio", "jogadores", String.join(", ", nomes), "chefe", d.tema().nomeChefe(),
                    "masmorra", d.nome());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (in.vencida) {
                encerrar(in, true);
            }
        }, 20L * 20);
    }

    // ------------------------------------------------------------------ MORTE, VOLTA E PROTEÇÃO

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoMorrer(PlayerDeathEvent e) {
        if (porJogador.containsKey(e.getPlayer().getUniqueId())) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoRenascer(PlayerRespawnEvent e) {
        Instancia in = porJogador.get(e.getPlayer().getUniqueId());
        if (in != null) {
            e.setRespawnLocation(in.inicioSala());
            Bukkit.getScheduler().runTask(plugin, () -> plugin.mensagens().enviar(e.getPlayer(), "masmorra-renasceu"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSairDoServidor(PlayerQuitEvent e) {
        Instancia in = porJogador.get(e.getPlayer().getUniqueId());
        if (in != null) {
            sairDaCorrida(in, e.getPlayer().getUniqueId(), e.getPlayer());
        }
    }

    @EventHandler
    public void aoEntrarNoServidor(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        for (Instancia in : instancias) {
            if (in.contem(p.getLocation()) && !porJogador.containsKey(p.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> p.teleport(saidaPadrao()), 5L);
                return;
            }
        }
    }

    private Instancia instanciaEm(Location l) {
        for (Instancia in : instancias) {
            if (in.contem(l)) {
                return in;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent e) {
        if (!e.getPlayer().hasPermission(PERM_ADMIN) && instanciaEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent e) {
        if (!e.getPlayer().hasPermission(PERM_ADMIN) && instanciaEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBalde(PlayerBucketEmptyEvent e) {
        if (!e.getPlayer().hasPermission(PERM_ADMIN) && instanciaEm(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent e) {
        if (instanciaEm(e.getLocation()) != null) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoNascer(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM && instanciaEm(e.getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent e) {
        if ((e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)
                && instanciaEm(e.getFrom()) != null && instanciaEm(e.getTo()) != instanciaEm(e.getFrom())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Material m = e.getClickedBlock().getType();
        if (m != Material.LODESTONE && m != Material.PURPLE_STAINED_GLASS && m != Material.CRYING_OBSIDIAN) {
            return;
        }
        for (Location en : entradas) {
            if (en.getWorld().equals(e.getClickedBlock().getWorld()) && en.distanceSquared(e.getClickedBlock().getLocation().add(0.5, 0, 0.5)) <= 36) {
                e.setCancelled(true);
                menu(e.getPlayer(), true);
                return;
            }
        }
    }

    public boolean naMasmorra(Player p) {
        return porJogador.containsKey(p.getUniqueId());
    }

    public List<Location> entradas() {
        return List.copyOf(entradas);
    }

    public int instanciasConstruidas() {
        return (int) instancias.stream().filter(i -> i.construida).count();
    }

    public int instanciasTotal() {
        return instancias.size();
    }

    public Location inicioDe(String chave) {
        for (Instancia in : instancias) {
            if (in.chave().equals(chave)) {
                return in.inicioSala();
            }
        }
        return null;
    }

    public List<String> chaves() {
        return instancias.stream().map(Instancia::chave).toList();
    }
}
