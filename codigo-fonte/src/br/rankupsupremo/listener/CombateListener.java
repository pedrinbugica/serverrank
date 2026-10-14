package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combate entre jogadores (mina PvP e Sobrevivência): marca os dois por alguns segundos, bloqueia comandos
 * de fuga e o voo, e quem desloga em combate morre. Na mina PvP o jogador perde tudo, menos a picareta do servidor.
 */
public final class CombateListener implements Listener {

    public static final String BYPASS = "rankupsupremo.combate.bypass";

    private final RankupSupremo plugin;
    private final Map<UUID, Long> emCombate = new ConcurrentHashMap<>();
    private BukkitTask tarefa;

    public CombateListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void iniciar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private int segundos() {
        return Math.max(1, plugin.getConfig().getInt("combate.segundos", 15));
    }

    public boolean emCombate(Player p) {
        Long ate = emCombate.get(p.getUniqueId());
        return ate != null && ate > System.currentTimeMillis();
    }

    private void tick() {
        long agora = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : emCombate.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                emCombate.remove(e.getKey());
                continue;
            }
            long falta = e.getValue() - agora;
            if (falta <= 0) {
                emCombate.remove(e.getKey());
                plugin.mensagens().enviar(p, "combate-saiu");
            } else {
                p.sendActionBar(Texto.cor(plugin.mensagens().bruta("combate-barra"), "segundos",
                        String.valueOf((falta + 999) / 1000)));
            }
        }
    }

    private static Player atacante(Entity dano) {
        if (dano instanceof Player p) {
            return p;
        }
        if (dano instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoDano(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player vitima) || !plugin.getConfig().getBoolean("combate.ativado", true)) {
            return;
        }
        Player atacante = atacante(e.getDamager());
        if (atacante == null || atacante.equals(vitima)) {
            return;
        }
        marcar(vitima);
        marcar(atacante);
    }

    private void marcar(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean novo = !emCombate(p);
        emCombate.put(p.getUniqueId(), System.currentTimeMillis() + segundos() * 1000L);
        if (p.hasPermission(BYPASS)) {
            return;
        }
        if (p.isFlying() || p.getAllowFlight()) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        if (novo) {
            plugin.mensagens().enviar(p, "combate-entrou", "segundos", String.valueOf(segundos()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoVoar(PlayerToggleFlightEvent e) {
        if (e.isFlying() && emCombate(e.getPlayer()) && !e.getPlayer().hasPermission(BYPASS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!emCombate(p) || p.hasPermission(BYPASS)) {
            return;
        }
        String comando = e.getMessage().length() > 1 ? e.getMessage().substring(1).trim().split("\\s+")[0] : "";
        comando = comando.toLowerCase(Locale.ROOT);
        int doisPontos = comando.indexOf(':');
        if (doisPontos >= 0) {
            comando = comando.substring(doisPontos + 1);
        }
        Set<String> bloqueados = new HashSet<>();
        for (String c : plugin.getConfig().getStringList("combate.comandos-bloqueados")) {
            bloqueados.add(c.toLowerCase(Locale.ROOT));
        }
        if (bloqueados.contains(comando)) {
            e.setCancelled(true);
            Long ate = emCombate.get(p.getUniqueId());
            long falta = ate == null ? 0 : Math.max(1, (ate - System.currentTimeMillis() + 999) / 1000);
            plugin.mensagens().enviar(p, "combate-comando-bloqueado", "segundos", String.valueOf(falta));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean marcado = emCombate(p);
        emCombate.remove(p.getUniqueId());
        if (!marcado || p.hasPermission(BYPASS) || p.isDead()
                || !plugin.getConfig().getBoolean("combate.matar-ao-deslogar", true)) {
            return;
        }
        PlayerQuitEvent.QuitReason motivo = e.getReason();
        if (motivo != PlayerQuitEvent.QuitReason.DISCONNECTED && motivo != PlayerQuitEvent.QuitReason.TIMED_OUT) {
            return; // expulso pela staff ou erro do servidor: não é fuga
        }
        perderItensPvp(p, null);
        p.setHealth(0);
        plugin.mensagens().transmitir("combate-deslogou", "jogador", p.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrer(PlayerDeathEvent e) {
        Player p = e.getEntity();
        boolean marcado = emCombate.remove(p.getUniqueId()) != null;
        if (!plugin.ehMundoRankup(p.getWorld())) {
            return; // na Sobrevivência valem as regras normais (túmulos do AxGraves)
        }
        if (marcado || plugin.minas().zonaPvp(p.getLocation())) {
            perderItensPvp(p, e);
        }
    }

    /**
     * Mina PvP: o mundo Rankup mantém o inventário ao morrer, então tiramos os itens na mão
     * (menos a picareta do servidor) e deixamos cair no chão.
     */
    private void perderItensPvp(Player p, PlayerDeathEvent e) {
        if (!plugin.ehMundoRankup(p.getWorld())) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] conteudo = inv.getContents();
        for (int i = 0; i < conteudo.length; i++) {
            ItemStack it = conteudo[i];
            if (it == null || it.getType().isAir() || plugin.picareta().ehPicaretaDoServidor(it)) {
                continue;
            }
            ItemStack copia = it.clone();
            inv.setItem(i, null);
            if (e != null) {
                e.getDrops().add(copia);
            } else {
                p.getWorld().dropItemNaturally(p.getLocation(), copia);
            }
        }
        if (e != null) {
            e.setKeepInventory(true); // o que sobrou (a picareta) continua com o jogador
            plugin.mensagens().enviar(p, "combate-morreu-pvp");
        }
    }
}
