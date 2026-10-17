package dev.pedrobugica.npcvivos.listeners;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Liga o combate da fase 6 aos eventos do jogo: mercenário que defende o dono, ajudante de chefe que
 * não dá recompensa e limpeza de contratos quando o jogador sai.
 * O dano que chega nos NPCs é tratado no NpcListener (que é quem cancela o dano do manequim).
 */
public final class CombateListener implements Listener {

    private final NpcVivosPlugin plugin;

    public CombateListener(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    /** Briga entre jogadores: o mercenário entra do lado do dono (apanhando ou batendo). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoBaterEmJogador(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player levou)) {
            return;
        }
        Player bateu = jogadorPorTras(e.getDamager());
        if (bateu == null || bateu.equals(levou)) {
            return;
        }
        plugin.mercenarios().defender(levou, bateu);
        plugin.mercenarios().defender(bateu, levou);
    }

    private static Player jogadorPorTras(Entity quem) {
        if (quem instanceof Player p) {
            return p;
        }
        if (quem instanceof Projectile proj) {
            ProjectileSource atirador = proj.getShooter();
            if (atirador instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    /** Ajudante de chefe não dá item nem experiência: ele existe só para atrapalhar na luta. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrerBicho(EntityDeathEvent e) {
        if (plugin.bosses().ehAjudante(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        plugin.mercenarios().esquecer(e.getPlayer().getUniqueId());
        plugin.duelos().esquecer(e.getPlayer().getUniqueId());
    }
}
