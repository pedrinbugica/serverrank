package br.rankupsupremo.comando;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.mina.Mina;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comandos dos jogadores: /rankup, /ranks, /prestigio, /minas, /vender, /autovender, /picareta, /spawn, /toprank, /loja...
 */
public final class ComandosJogador implements CommandExecutor, TabCompleter {

    private final RankupSupremo plugin;

    public ComandosJogador(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.mensagens().enviar(sender, "apenas-jogador");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "rankup" -> plugin.acoes().rankup(p);
            case "ranks" -> plugin.menus().ranks(p);
            case "prestigio" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("confirmar")) {
                    plugin.acoes().prestigiar(p);
                } else {
                    plugin.acoes().infoPrestigio(p);
                }
            }
            case "minas" -> {
                if (args.length == 0) {
                    plugin.menus().minas(p);
                } else {
                    Mina m = plugin.minas().obter(args[0]);
                    if (m == null) {
                        plugin.mensagens().enviar(p, "mina-nao-encontrada");
                    } else {
                        plugin.acoes().teleportarMina(p, m);
                    }
                }
            }
            case "vender" -> {
                if (plugin.acoes().noRankup(p)) {
                    plugin.acoes().vender(p);
                }
            }
            case "autovender" -> {
                if (plugin.acoes().noRankup(p)) {
                    plugin.acoes().alternarAutovender(p);
                }
            }
            case "picareta" -> {
                if (plugin.acoes().noRankup(p)) {
                    plugin.menus().picareta(p);
                }
            }
            case "spawn" -> plugin.acoes().teleportarSpawn(p, true);
            case "lobby" -> plugin.lobby().teleportar(p);
            case "toprank" -> plugin.acoes().mostrarTop(p);
            case "sobrevivencia" -> plugin.sobrevivencia().teleportar(p);
            case "mundos" -> plugin.menus().mundos(p);
            case "rtp" -> plugin.sobrevivencia().rtp(p);
            case "loja" -> plugin.loja().abrir(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> r = new ArrayList<>();
        if (args.length != 1) {
            return r;
        }
        String inicio = args[0].toLowerCase(Locale.ROOT);
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "minas" -> {
                for (Mina m : plugin.minas().todas()) {
                    if (m.id().startsWith(inicio)) {
                        r.add(m.id());
                    }
                }
            }
            case "prestigio" -> {
                if ("confirmar".startsWith(inicio)) {
                    r.add("confirmar");
                }
            }
            default -> {
            }
        }
        return r;
    }
}
