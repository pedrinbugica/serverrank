package dev.pedrobugica.npcvivos.povo;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corpo de gente para mobs do jogo (o "disfarce" dos mods Humanoid Villagers / Villagers Reborn, sem mod nenhum):
 * o mob de verdade (aldeão, soldado...) continua com toda a IA do Minecraft - anda, trabalha, dorme, foge, luta -
 * mas fica escondido dos jogadores, e um Manequim com skin de jogador segue ele a cada tique.
 * Bater, atirar ou clicar no manequim chega no mob; quando o mob se machuca o manequim pisca vermelho,
 * e quando o mob morre o manequim cai junto. Os manequins nunca são salvos no mapa: nascem de novo
 * sempre que o mob carrega.
 */
public final class Corpos implements Listener {

    /** Um tipo de mob que ganha corpo (moradores, soldados...). */
    public interface Molde {
        /** Este mob deve ganhar um corpo? */
        boolean quer(LivingEntity e);

        /** Skin, nome, roupas... (chamado ao criar o corpo). */
        void vestir(LivingEntity cerebro, Mannequin corpo);

        /** Atualiza nome/vida no corpo (depois de dano, cura...). */
        default void atualizar(LivingEntity cerebro, Mannequin corpo) {
        }

        /** O jogador clicou com o botão direito no corpo. */
        default void clicar(Player p, LivingEntity cerebro, Mannequin corpo) {
        }

        /** Som de dor (o mob fica mudo: os sons de esqueleto/aldeão não combinam com o corpo de gente). */
        default Sound somDeDor() {
            return Sound.ENTITY_PLAYER_HURT;
        }
    }

    private static final class Corpo {
        final LivingEntity cerebro;
        final Mannequin corpo;
        final Molde molde;
        final boolean eraSilencioso;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        boolean dormindo;

        Corpo(LivingEntity cerebro, Mannequin corpo, Molde molde, boolean eraSilencioso) {
            this.cerebro = cerebro;
            this.corpo = corpo;
            this.molde = molde;
            this.eraSilencioso = eraSilencioso;
        }
    }

    private final NpcVivosPlugin plugin;
    private final NamespacedKey chaveCorpo;
    private final List<Molde> moldes = new ArrayList<>();
    private final Map<UUID, Corpo> porCerebro = new HashMap<>();
    private final Map<UUID, Corpo> porCorpo = new HashMap<>();
    private final Map<UUID, Long> ultimoClique = new HashMap<>();
    private BukkitTask tarefa;

    public Corpos(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.chaveCorpo = new NamespacedKey(plugin, "corpo-de");
    }

    public void registrar(Molde m) {
        if (!moldes.contains(m)) {
            moldes.add(m);
        }
    }

    // ------------------------------------------------------------------ LIGAR / DESLIGAR

    public void iniciar() {
        parar();
        for (World w : Bukkit.getWorlds()) {
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (m.getPersistentDataContainer().has(chaveCorpo)) {
                    m.remove(); // sobra de um desligamento brusco
                }
            }
        }
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                talvezVestir(e);
            }
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::sincronizar, 1L, 1L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (Corpo c : List.copyOf(porCerebro.values())) {
            desfazer(c, false, false);
        }
        porCerebro.clear();
        porCorpo.clear();
    }

    /** Refaz os corpos de um molde (ex.: depois de recarregar a config). */
    public void revestirTodos(Molde m) {
        for (Corpo c : List.copyOf(porCerebro.values())) {
            if (c.molde == m) {
                desfazer(c, false, false);
            }
        }
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                talvezVestir(e);
            }
        }
    }

    // ------------------------------------------------------------------ CONSULTAS

    public boolean ehCorpo(Entity e) {
        return e instanceof Mannequin && e.getPersistentDataContainer().has(chaveCorpo);
    }

    public LivingEntity cerebroDe(Entity corpo) {
        Corpo c = porCorpo.get(corpo.getUniqueId());
        return c == null ? null : c.cerebro;
    }

    public Mannequin corpoDe(Entity cerebro) {
        Corpo c = porCerebro.get(cerebro.getUniqueId());
        return c == null ? null : c.corpo;
    }

    public boolean temCorpo(Entity cerebro) {
        return porCerebro.containsKey(cerebro.getUniqueId());
    }

    /** O mob atrás deste clique/golpe: o próprio, ou o dono do corpo. */
    public Entity real(Entity e) {
        LivingEntity c = cerebroDe(e);
        return c == null ? e : c;
    }

    /** Os mobs (carregados) que têm corpo deste molde. */
    public List<LivingEntity> cerebros(Molde m) {
        List<LivingEntity> l = new ArrayList<>();
        for (Corpo c : porCerebro.values()) {
            if (c.molde == m && c.cerebro.isValid()) {
                l.add(c.cerebro);
            }
        }
        return l;
    }

    /** Faz o corpo de novo (mudou de profissão, cresceu, trocou de nome...). */
    public void revestir(LivingEntity cerebro) {
        Corpo c = porCerebro.get(cerebro.getUniqueId());
        if (c != null) {
            desfazer(c, false, false);
        }
        talvezVestir(cerebro);
    }

    public void atualizar(LivingEntity cerebro) {
        Corpo c = porCerebro.get(cerebro.getUniqueId());
        if (c != null && c.corpo.isValid()) {
            c.molde.atualizar(c.cerebro, c.corpo);
        }
    }

    // ------------------------------------------------------------------ CRIAR / DESFAZER

    private void talvezVestir(Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Mannequin || e instanceof Player || !e.isValid() || le.isDead()) {
            return;
        }
        if (porCerebro.containsKey(e.getUniqueId())) {
            return;
        }
        for (Molde m : moldes) {
            if (m.quer(le)) {
                vestir(le, m);
                return;
            }
        }
    }

    private void vestir(LivingEntity cerebro, Molde molde) {
        Location l = cerebro.getLocation();
        Mannequin corpo = l.getWorld().spawn(l, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setImmovable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setCollidable(false);
            m.setRemoveWhenFarAway(false);
            m.getPersistentDataContainer().set(chaveCorpo, PersistentDataType.STRING, cerebro.getUniqueId().toString());
            molde.vestir(cerebro, m);
        });
        Corpo c = new Corpo(cerebro, corpo, molde, cerebro.isSilent());
        c.x = l.getX();
        c.y = l.getY();
        c.z = l.getZ();
        c.yaw = l.getYaw();
        c.pitch = l.getPitch();
        porCerebro.put(cerebro.getUniqueId(), c);
        porCorpo.put(corpo.getUniqueId(), c);
        cerebro.setSilent(true);
        if (!cerebro.isVisibleByDefault()) {
            cerebro.setVisibleByDefault(true); // versão antiga escondia "para sempre"; agora é por jogador
        }
        // esconde o mob de cada jogador (isso fica só na memória: sem o plugin, o mob volta a aparecer)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideEntity(plugin, cerebro);
        }
    }

    /**
     * @param morreu      true = o mob morreu: o corpo "morre" junto (cai e some, como um jogador)
     * @param descarregou true = o chunk está descarregando: não mexe no mob e tira o corpo só no próximo tique
     */
    private void desfazer(Corpo c, boolean morreu, boolean descarregou) {
        porCerebro.remove(c.cerebro.getUniqueId());
        porCorpo.remove(c.corpo.getUniqueId());
        if (descarregou) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (c.corpo.isValid()) {
                    for (Entity passageiro : c.corpo.getPassengers()) {
                        passageiro.remove();
                    }
                    c.corpo.remove();
                }
            });
            return;
        }
        if (c.cerebro.isValid()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showEntity(plugin, c.cerebro);
            }
            c.cerebro.setSilent(c.eraSilencioso);
        }
        if (!c.corpo.isValid()) {
            return;
        }
        if (morreu) {
            c.corpo.eject();
            c.corpo.setGravity(true);
            c.corpo.setHealth(0);
        } else {
            for (Entity passageiro : c.corpo.getPassengers()) {
                passageiro.remove();
            }
            c.corpo.remove();
        }
    }

    // ------------------------------------------------------------------ SEGUIR O MOB

    private void sincronizar() {
        if (porCerebro.isEmpty()) {
            return;
        }
        for (Corpo c : List.copyOf(porCerebro.values())) {
            if (!c.cerebro.isValid() || c.cerebro.isDead()) {
                desfazer(c, c.cerebro.isDead(), false);
                continue;
            }
            if (!c.corpo.isValid()) {
                porCerebro.remove(c.cerebro.getUniqueId());
                porCorpo.remove(c.corpo.getUniqueId());
                vestir(c.cerebro, c.molde);
                continue;
            }
            Location l = c.cerebro.getLocation();
            if (!l.getWorld().equals(c.corpo.getWorld())) {
                desfazer(c, false, false);
                continue;
            }
            boolean dorme = c.cerebro.isSleeping();
            if (dorme != c.dormindo) {
                c.dormindo = dorme;
                try {
                    c.corpo.setPose(dorme ? Pose.SLEEPING : Pose.STANDING, dorme);
                } catch (RuntimeException ignorado) {
                    // pose não aceita por esta versão
                }
            }
            double dx = l.getX() - c.x;
            double dy = l.getY() - c.y;
            double dz = l.getZ() - c.z;
            if (dx * dx + dy * dy + dz * dz < 0.0004 && Math.abs(l.getYaw() - c.yaw) < 2f && Math.abs(l.getPitch() - c.pitch) < 2f) {
                continue;
            }
            c.x = l.getX();
            c.y = l.getY();
            c.z = l.getZ();
            c.yaw = l.getYaw();
            c.pitch = l.getPitch();
            if (!c.corpo.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS)) {
                // versão que não leva passageiros junto: tira o balão de fala e anda sem ele
                for (Entity passageiro : c.corpo.getPassengers()) {
                    passageiro.remove();
                }
                c.corpo.teleport(l);
            }
            c.corpo.setBodyYaw(l.getYaw());
        }
    }

    // ------------------------------------------------------------------ EVENTOS

    @EventHandler
    public void aoEntrarNoMundo(EntityAddToWorldEvent e) {
        Entity en = e.getEntity();
        if (en instanceof LivingEntity && !(en instanceof Mannequin)) {
            Bukkit.getScheduler().runTask(plugin, () -> talvezVestir(en));
        }
    }

    @EventHandler
    public void aoSairDoMundo(EntityRemoveFromWorldEvent e) {
        Corpo c = porCerebro.get(e.getEntity().getUniqueId());
        if (c != null) {
            desfazer(c, false, true);
        }
    }

    /** Quem entra no servidor também não vê os mobs que têm corpo. */
    @EventHandler
    public void aoJogadorEntrar(PlayerJoinEvent e) {
        for (Corpo c : porCerebro.values()) {
            e.getPlayer().hideEntity(plugin, c.cerebro);
        }
    }

    /** Golpes e flechas no manequim vão para o mob de verdade (com o mesmo atacante, para o resto dos plugins). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoBaterNoCorpo(EntityDamageEvent e) {
        Corpo c = porCorpo.get(e.getEntity().getUniqueId());
        if (c == null) {
            return;
        }
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent ee) || !c.cerebro.isValid()) {
            return;
        }
        Entity atacante = ee.getDamager();
        if (atacante instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            atacante = s instanceof Entity en ? en : null;
            pr.remove();
        }
        if (atacante == null || atacante.equals(c.cerebro)) {
            return;
        }
        double dano = e.getDamage();
        Entity quem = atacante;
        // no próximo tique: o golpe de agora ainda está sendo processado pelo jogo
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (c.cerebro.isValid() && !c.cerebro.isDead()) {
                c.cerebro.damage(dano, quem);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMobSeMachucar(EntityDamageEvent e) {
        Corpo c = porCerebro.get(e.getEntity().getUniqueId());
        if (c == null || e.getFinalDamage() <= 0 || !c.corpo.isValid()) {
            return;
        }
        c.corpo.playHurtAnimation(0);
        c.corpo.getWorld().playSound(c.corpo.getLocation(), c.molde.somDeDor(), 0.8f, 1f);
        Bukkit.getScheduler().runTask(plugin, () -> atualizar(c.cerebro));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMobCurar(EntityRegainHealthEvent e) {
        if (porCerebro.containsKey(e.getEntity().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> atualizar((LivingEntity) e.getEntity()));
        }
    }

    /** O mob golpeou alguém ou atirou: o corpo mexe o braço. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMobAtacar(EntityDamageByEntityEvent e) {
        Corpo c = porCerebro.get(e.getDamager().getUniqueId());
        if (c != null && c.corpo.isValid()) {
            c.corpo.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMobAtirar(EntityShootBowEvent e) {
        Corpo c = porCerebro.get(e.getEntity().getUniqueId());
        if (c != null && c.corpo.isValid()) {
            c.corpo.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMobMorrer(EntityDeathEvent e) {
        Corpo c = porCerebro.get(e.getEntity().getUniqueId());
        if (c != null) {
            desfazer(c, true, false);
            return;
        }
        if (ehCorpo(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    private void clique(PlayerInteractEntityEvent e) {
        Corpo c = porCorpo.get(e.getRightClicked().getUniqueId());
        if (c == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long agora = System.currentTimeMillis();
        Long antes = ultimoClique.put(e.getPlayer().getUniqueId(), agora);
        if (antes != null && agora - antes < 350) {
            return; // o jogo manda o clique duas vezes
        }
        c.molde.clicar(e.getPlayer(), c.cerebro, c.corpo);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEntityEvent e) {
        clique(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarEm(PlayerInteractAtEntityEvent e) {
        clique(e);
    }
}
