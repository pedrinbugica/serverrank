package dev.pedrobugica.npcvivos.faccao;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guardas (papel GUARDA): protegem a facção. Avisam ("Saia daqui, forasteiro!"), atiram flechas de longe
 * e batem de perto em quem é Inimigo da facção, está em guerra com ela ou atacou alguém dela.
 * Também: quem bate num NPC perde reputação, e matar monstros perto da vila ajuda a facção.
 */
public final class GuardaService implements Listener {

    private final NpcVivosPlugin plugin;
    private final NamespacedKey chaveFlecha;
    private final Map<String, Long> ultimoGolpe = new ConcurrentHashMap<>();
    private final Map<String, Long> avisos = new ConcurrentHashMap<>();
    private final Map<String, Long> ultimoAtaqueNpc = new ConcurrentHashMap<>();
    private final Map<UUID, Long> imunes = new ConcurrentHashMap<>();
    private BukkitTask tarefa;
    private BukkitTask tarefaRecuperar;

    public GuardaService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.chaveFlecha = new NamespacedKey(plugin, "flecha-guarda");
    }

    public void iniciar() {
        parar();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::vigiar, 40L, 10L);
        // Reputação negativa volta devagar para o neutro enquanto o jogador está online
        tarefaRecuperar = Bukkit.getScheduler().runTaskTimer(plugin, this::recuperarReputacao, 20L * 600, 20L * 600);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        if (tarefaRecuperar != null) {
            tarefaRecuperar.cancel();
            tarefaRecuperar = null;
        }
    }

    private double cfg(String k, double padrao) {
        return plugin.getConfig().getDouble("guardas." + k, padrao);
    }

    // ------------------------------------------------------------------ VIGIAR

    private void vigiar() {
        double raioAlerta = cfg("raio-alerta", 12);
        double raioAtaque = cfg("raio-ataque", 3.5);
        List<String> proibidos = plugin.getConfig().getStringList("guardas.mundos-proibidos");
        long agora = System.currentTimeMillis();
        for (NpcVivo npc : plugin.npcs().todos()) {
            if (!npc.tem(Papel.GUARDA) || npc.entidade() == null || proibidos.contains(npc.mundo())) {
                continue;
            }
            Faccao f = plugin.faccoes().daNpc(npc);
            Entity e = Bukkit.getEntity(npc.entidade());
            if (f == null || !(e instanceof Mannequin guarda) || !guarda.isValid()) {
                continue;
            }
            Player alvo = null;
            double melhor = raioAlerta * raioAlerta;
            for (Player p : guarda.getWorld().getPlayers()) {
                if (p.isDead() || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)
                        || imunes.getOrDefault(p.getUniqueId(), 0L) > agora || !plugin.faccoes().inimigoDosGuardas(p, f)) {
                    continue;
                }
                double d = p.getLocation().distanceSquared(guarda.getLocation());
                if (d < melhor) {
                    melhor = d;
                    alvo = p;
                }
            }
            if (alvo == null) {
                continue;
            }
            olhar(guarda, alvo);
            avisar(npc, alvo, agora);
            double dist = Math.sqrt(melhor);
            long ultimo = ultimoGolpe.getOrDefault(npc.id(), 0L);
            if (dist <= raioAtaque) {
                if (agora - ultimo >= (long) (cfg("intervalo-golpe-segundos", 1.2) * 1000)) {
                    ultimoGolpe.put(npc.id(), agora);
                    guarda.swingMainHand();
                    alvo.damage(cfg("dano", 4.0), guarda);
                    Vector empurrao = alvo.getLocation().toVector().subtract(guarda.getLocation().toVector());
                    if (empurrao.lengthSquared() > 0.01) {
                        alvo.setVelocity(empurrao.normalize().multiply(0.45).setY(0.32));
                    }
                }
            } else if (plugin.getConfig().getBoolean("guardas.flechas", true) && guarda.hasLineOfSight(alvo)
                    && agora - ultimo >= (long) (cfg("intervalo-flecha-segundos", 2.0) * 1000)) {
                ultimoGolpe.put(npc.id(), agora);
                atirar(guarda, alvo, dist);
            }
        }
    }

    private static void olhar(Mannequin guarda, Player alvo) {
        Location olho = guarda.getEyeLocation();
        Vector dir = alvo.getEyeLocation().toVector().subtract(olho.toVector());
        if (dir.lengthSquared() > 0.01) {
            Location l = olho.clone().setDirection(dir);
            guarda.setRotation(l.getYaw(), l.getPitch());
        }
    }

    private void avisar(NpcVivo npc, Player alvo, long agora) {
        String chave = alvo.getUniqueId() + ":" + npc.id();
        if (agora - avisos.getOrDefault(chave, 0L) < 8000) {
            return;
        }
        avisos.put(chave, agora);
        List<String> falas = plugin.mensagens().lista("guarda-alerta");
        if (!falas.isEmpty()) {
            alvo.sendMessage(Texto.cor(plugin.mensagens().bruta("fala"), "npc", npc.nome(),
                    "fala", Texto.substituir(falas.get(ThreadLocalRandom.current().nextInt(falas.size())), "jogador", alvo.getName())));
        }
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_VINDICATOR_CELEBRATE, 0.6f, 0.8f);
    }

    private void atirar(Mannequin guarda, Player alvo, double dist) {
        Location de = guarda.getEyeLocation();
        Vector dir = alvo.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(de.toVector());
        dir.setY(dir.getY() + dist * 0.06); // um pouco de arco
        Arrow flecha = guarda.launchProjectile(Arrow.class, dir.normalize().multiply(1.9));
        flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        flecha.setDamage(cfg("dano-flecha", 1.5));
        flecha.getPersistentDataContainer().set(chaveFlecha, PersistentDataType.BYTE, (byte) 1);
        guarda.getWorld().playSound(de, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flecha.isValid()) {
                flecha.remove();
            }
        }, 100L);
    }

    private void recuperarReputacao() {
        int pontos = plugin.getConfig().getInt("reputacao.recuperacao-a-cada-10-min", 5);
        if (pontos <= 0) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Map.Entry<String, Integer> r : plugin.repositorio().reputacoes(p.getUniqueId()).entrySet()) {
                if (r.getValue() < 0) {
                    plugin.repositorio().setReputacao(p.getUniqueId(), r.getKey(), Math.min(0, r.getValue() + pontos));
                }
            }
        }
    }

    // ------------------------------------------------------------------ EVENTOS

    private static Player atacante(Entity dano) {
        if (dano instanceof Player p) {
            return p;
        }
        if (dano instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /**
     * O golpe foi combinado? Num duelo o jogador aceitou o desafio da duelista e no mercenário ele é o patrão:
     * nos dois casos bater no NPC não é agressão, então a facção dele não perde reputação nem chama os guardas.
     */
    private boolean lutaCombinada(NpcVivo npc, Player p) {
        if (plugin.duelos().ehDesafiante(npc.id(), p) || npc.tem(Papel.INVASOR)) {
            return true; // duelo marcado, ou soldado de invasão (defender a vila não ofende ninguém)
        }
        Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
        return c != null && p.getUniqueId().equals(c.dono());
    }

    /** Bater num NPC de uma facção: perde reputação e os guardas passam a caçar o jogador. */
    @EventHandler(priority = EventPriority.LOW)
    public void aoAtacarNpc(EntityDamageByEntityEvent e) {
        NpcVivo npc = plugin.npcs().ehEntidadeNpc(e.getEntity()) ? plugin.npcs().porEntidade(e.getEntity()) : null;
        Player p = atacante(e.getDamager());
        if (npc == null || p == null || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return; // a varredura da espada pegou o NPC que estava do lado do monstro: não foi de propósito
        }
        if (lutaCombinada(npc, p)) {
            return; // duelo marcado ou o próprio mercenário: a facção não leva a mal (fase 6)
        }
        Faccao f = plugin.faccoes().daNpc(npc);
        String chave = p.getUniqueId() + ":" + npc.id();
        long agora = System.currentTimeMillis();
        if (f == null || agora - ultimoAtaqueNpc.getOrDefault(chave, 0L) < 3000) {
            return;
        }
        ultimoAtaqueNpc.put(chave, agora);
        plugin.faccoes().alterarReputacao(p, f, -plugin.getConfig().getInt("reputacao.atacar-npc", 15));
        plugin.faccoes().marcarProcurado(p, f);
        List<String> falas = plugin.mensagens().lista("npc-atacado");
        if (!falas.isEmpty()) {
            p.sendMessage(Texto.cor(plugin.mensagens().bruta("fala"), "npc", npc.nome(),
                    "fala", falas.get(ThreadLocalRandom.current().nextInt(falas.size()))));
        }
    }

    /** Atacar um membro da facção perto de um guarda dela também deixa o jogador procurado. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoAtacarMembro(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player vitima)) {
            return;
        }
        Player p = atacante(e.getDamager());
        Faccao f = plugin.faccoes().membro(vitima);
        if (p == null || f == null || p.equals(vitima)) {
            return;
        }
        Faccao doAtacante = plugin.faccoes().membro(p);
        if (doAtacante != null && doAtacante.id().equals(f.id())) {
            return;
        }
        for (NpcVivo npc : plugin.npcs().todos()) {
            Location l = npc.local();
            if (npc.tem(Papel.GUARDA) && f.id().equals(npc.faccao()) && l != null && l.getWorld().equals(vitima.getWorld())
                    && l.distanceSquared(vitima.getLocation()) <= 32 * 32) {
                String chave = p.getUniqueId() + ":membro:" + f.id();
                long agora = System.currentTimeMillis();
                if (agora - ultimoAtaqueNpc.getOrDefault(chave, 0L) > 5000) {
                    ultimoAtaqueNpc.put(chave, agora);
                    plugin.faccoes().alterarReputacao(p, f, -plugin.getConfig().getInt("reputacao.atacar-membro", 5));
                }
                plugin.faccoes().marcarProcurado(p, f);
                return;
            }
        }
    }

    /** Matar monstros perto de uma vila (NPCs de uma facção) ajuda a facção. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMatarMonstro(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        int pontos = plugin.getConfig().getInt("reputacao.por-monstro", 1);
        if (p == null || !(e.getEntity() instanceof Monster) || pontos <= 0) {
            return;
        }
        double raio = plugin.getConfig().getDouble("reputacao.raio-vila", 48);
        for (NpcVivo npc : plugin.npcs().todos()) {
            Location l = npc.local();
            Faccao f = plugin.faccoes().daNpc(npc);
            if (f != null && l != null && l.getWorld().equals(e.getEntity().getWorld())
                    && l.distanceSquared(e.getEntity().getLocation()) <= raio * raio) {
                plugin.faccoes().reputacaoPorAjuda(p, f, pontos);
                return;
            }
        }
    }

    /** Quem acabou de renascer fica um tempo sem apanhar dos guardas (evita ficar preso morrendo no spawn). */
    @EventHandler
    public void aoRenascer(PlayerRespawnEvent e) {
        imunes.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + (long) (cfg("imunidade-ao-renascer-segundos", 30) * 1000));
    }

    public void esquecer(UUID jogador) {
        imunes.remove(jogador);
        avisos.keySet().removeIf(k -> k.startsWith(jogador.toString()));
        ultimoAtaqueNpc.keySet().removeIf(k -> k.startsWith(jogador.toString()));
    }
}
