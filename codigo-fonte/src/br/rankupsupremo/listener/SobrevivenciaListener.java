package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.sobrevivencia.GerenciadorSobrevivencia;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eventos do mundo Sobrevivência: configuração ao carregar, última posição e proteção do spawn.
 */
public final class SobrevivenciaListener implements Listener {

    private final RankupSupremo plugin;
    private final Map<UUID, Long> avisos = new HashMap<>();

    public SobrevivenciaListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private GerenciadorSobrevivencia sv() {
        return plugin.sobrevivencia();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregarMundo(WorldLoadEvent e) {
        World w = e.getWorld();
        if (sv().ehSobrevivencia(w)) {
            // Espera o Multiverse terminar de aplicar as propriedades dele
            Bukkit.getScheduler().runTaskLater(plugin, () -> sv().configurar(w), 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent e) {
        Location de = e.getFrom();
        Location para = e.getTo();
        if (sv().ehSobrevivencia(de.getWorld()) && !sv().ehSobrevivencia(para.getWorld())) {
            sv().salvarPosicao(e.getPlayer(), de);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoSair(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (sv().ehSobrevivencia(p.getWorld())) {
            sv().salvarPosicao(p, p.getLocation());
        }
        sv().limpar(p.getUniqueId());
        avisos.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Proteção do spawn

    private boolean bloqueado(Player p, Location l) {
        if (!sv().spawnProtegido(l) || plugin.emModoConstrucao(p) || p.hasPermission("rankupsupremo.construir")) {
            return false;
        }
        long agora = System.currentTimeMillis();
        Long ultimo = avisos.get(p.getUniqueId());
        if (ultimo == null || agora - ultimo > 2500) {
            avisos.put(p.getUniqueId(), agora);
            plugin.mensagens().enviar(p, "sobrevivencia-spawn-protegido");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent e) {
        if (bloqueado(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent e) {
        if (bloqueado(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent e) {
        if (bloqueado(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player vitima) || !sv().spawnProtegido(vitima.getLocation())) {
            return;
        }
        boolean atacantePlayer = e.getDamager() instanceof Player
                || (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player);
        if (atacantePlayer) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> sv().spawnProtegido(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> sv().spawnProtegido(b.getLocation()));
    }
}
