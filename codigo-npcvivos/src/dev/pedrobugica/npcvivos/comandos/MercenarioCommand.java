package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /mercenario - mostra o mercenário contratado e quanto tempo falta; /mercenario dispensar encerra o contrato.
 * Contratar é só falando com o NPC, para o jogador ter que ir até ele.
 */
public final class MercenarioCommand implements CommandExecutor, TabCompleter {

    private final NpcVivosPlugin plugin;

    public MercenarioCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return true;
        }
        if (a.length >= 1 && (a[0].equalsIgnoreCase("dispensar") || a[0].equalsIgnoreCase("sair"))) {
            plugin.mercenarios().dispensar(p, "mercenario-dispensado");
            return true;
        }
        String id = plugin.mercenarios().idContratado(p);
        NpcVivo npc = id == null ? null : plugin.npcs().obter(id);
        if (npc == null) {
            plugin.mensagens().enviar(p, "mercenario-nenhum");
            plugin.mensagens().enviarLista(p, "ajuda-mercenario");
            return true;
        }
        plugin.mensagens().enviar(p, "mercenario-status", "npc", npc.nome(),
                "minutos", String.valueOf(plugin.mercenarios().minutosRestantes(p)),
                "estado", plugin.comportamento().estadoDe(npc));
        plugin.mensagens().enviarLista(p, "ajuda-mercenario");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        return a.length == 1 ? List.of("dispensar") : List.of();
    }
}
