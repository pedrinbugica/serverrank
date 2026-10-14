package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Proteção do mundo Rankup: só é possível minerar dentro das minas. Admins usam /rankupadmin construir.
 * Nada aqui se aplica ao mundo Sobrevivência (lá vale o GriefPrevention).
 */
public final class ProtecaoListener implements Listener {

    private final RankupSupremo plugin;

    public ProtecaoListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private boolean rankup(World w) {
        return plugin.ehMundoRankup(w);
    }

    private boolean livre(Entity e) {
        return e instanceof Player p && plugin.emModoConstrucao(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent e) {
        if (rankup(e.getBlock().getWorld()) && !livre(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent e) {
        if (rankup(e.getBlock().getWorld()) && !livre(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEncherBalde(PlayerBucketFillEvent e) {
        if (rankup(e.getBlock().getWorld()) && !livre(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrarQuadro(HangingBreakByEntityEvent e) {
        if (rankup(e.getEntity().getWorld()) && !livre(e.getRemover())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoInteragirEntidade(PlayerInteractEntityEvent e) {
        if (!rankup(e.getRightClicked().getWorld()) || livre(e.getPlayer())) {
            return;
        }
        switch (e.getRightClicked().getType()) {
            case ITEM_FRAME, GLOW_ITEM_FRAME, ARMOR_STAND, PAINTING -> e.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoManipularSuporte(PlayerArmorStandManipulateEvent e) {
        if (rankup(e.getRightClicked().getWorld()) && !livre(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoInteragir(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && rankup(e.getPlayer().getWorld()) && !livre(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAtacarEntidade(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player || !rankup(e.getEntity().getWorld())) {
            return; // dano em jogadores é tratado abaixo
        }
        if (!livre(e.getDamager())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoReceberDano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !rankup(p.getWorld())) {
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            plugin.acoes().teleportarSpawn(p, false);
            return;
        }
        if (plugin.getConfig().getBoolean("geral.desativar-dano", true) && !pvpNaMina(e, p)) {
            e.setCancelled(true);
        }
    }

    /** Na mina PvP vale dano entre jogadores (os dois precisam estar dentro dela). */
    private boolean pvpNaMina(EntityDamageEvent e, Player vitima) {
        if (!(e instanceof EntityDamageByEntityEvent ed)) {
            return false;
        }
        Entity dano = ed.getDamager();
        Player atacante = null;
        if (dano instanceof Player direto) {
            atacante = direto;
        } else if (dano instanceof Projectile proj && proj.getShooter() instanceof Player atirador) {
            atacante = atirador;
        }
        return atacante != null && !atacante.equals(vitima)
                && plugin.minas().zonaPvp(vitima.getLocation()) && plugin.minas().zonaPvp(atacante.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMudarFome(FoodLevelChangeEvent e) {
        if (rankup(e.getEntity().getWorld()) && plugin.getConfig().getBoolean("geral.desativar-fome", true)
                && e.getFoodLevel() < e.getEntity().getFoodLevel()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent e) {
        if (e.getFrom().getBlockY() == e.getTo().getBlockY() || !rankup(e.getTo().getWorld())) {
            return;
        }
        if (e.getTo().getY() < plugin.getConfig().getInt("spawn.altura-minima", 20)) {
            plugin.acoes().teleportarSpawn(e.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent e) {
        if (rankup(e.getEntity().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent e) {
        if (rankup(e.getBlock().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQueimar(BlockBurnEvent e) {
        if (rankup(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoIncendiar(BlockIgniteEvent e) {
        if (rankup(e.getBlock().getWorld()) && (e.getPlayer() == null || !livre(e.getPlayer()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEspalhar(BlockSpreadEvent e) {
        if (rankup(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFolhasSumirem(LeavesDecayEvent e) {
        if (rankup(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMudarClima(WeatherChangeEvent e) {
        if (e.toWeatherState() && rankup(e.getWorld())) {
            e.setCancelled(true);
        }
    }
}
