package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.missao.Missao;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /missoes - missões ativas com o progresso; /missoes abandonar <número>.
 */
public final class MissoesCommand implements CommandExecutor, TabCompleter {

    private final NpcVivosPlugin plugin;

    public MissoesCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return true;
        }
        List<Missao> ativas = plugin.missoes().ativas(p);
        if (a.length >= 2 && a[0].equalsIgnoreCase("abandonar")) {
            Missao alvo = null;
            try {
                int n = Integer.parseInt(a[1]);
                if (n >= 1 && n <= ativas.size()) {
                    alvo = ativas.get(n - 1);
                }
            } catch (NumberFormatException e) {
                Missao m = plugin.missoes().obter(a[1]);
                if (m != null && ativas.contains(m)) {
                    alvo = m;
                }
            }
            if (alvo == null) {
                plugin.mensagens().enviar(p, "missao-nao-encontrada");
            } else {
                plugin.missoes().abandonar(p, alvo);
            }
            return true;
        }
        plugin.mensagens().enviarSemPrefixo(p, "missoes-cabecalho", "total", String.valueOf(ativas.size()),
                "maximo", String.valueOf(plugin.getConfig().getInt("missoes.maximo-ativas", 5)));
        if (ativas.isEmpty()) {
            plugin.mensagens().enviarSemPrefixo(p, "missoes-vazio");
        }
        int i = 1;
        for (Missao m : ativas) {
            NpcVivo npc = plugin.npcs().obter(m.npcEntrega());
            boolean pronta = plugin.missoes().pronta(p, m);
            plugin.mensagens().enviarSemPrefixo(p, pronta ? "missoes-linha-pronta" : "missoes-linha", "numero", String.valueOf(i++),
                    "missao", m.nome(), "objetivo", plugin.missoes().objetivo(m),
                    "progresso", Math.min(plugin.missoes().progresso(p, m), m.quantidade()) + "/" + m.quantidade(),
                    "npc", npc != null ? npc.nome() : m.npcEntrega());
        }
        plugin.mensagens().enviarSemPrefixo(p, "missoes-rodape");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1 && "abandonar".startsWith(a[0].toLowerCase(Locale.ROOT))) {
            r.add("abandonar");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("abandonar") && s instanceof Player p) {
            for (int i = 1; i <= plugin.missoes().ativas(p).size(); i++) {
                r.add(String.valueOf(i));
            }
        }
        return r;
    }
}
