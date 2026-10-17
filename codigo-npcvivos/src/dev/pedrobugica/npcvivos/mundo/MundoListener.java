package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Jogador matando jogador (diplomacia e pontos de guerra) e placar das guerras ao entrar. */
public final class MundoListener implements Listener {

    private final NpcVivosPlugin plugin;

    public MundoListener(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent e) {
        Player vitima = e.getEntity();
        Player autor = vitima.getKiller();
        if (autor == null || autor.equals(vitima)) {
            return;
        }
        plugin.guerras().aoMatarJogador(autor, vitima);
        plugin.diplomacia().aoMatarJogador(autor, vitima);
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent e) {
        plugin.guerras().atualizarJogador(e.getPlayer());
    }
}
