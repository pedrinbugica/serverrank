package dev.pedrobugica.survivalrpg.comandos;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Comandos do jogador: /rpg, /classe, /atributos, /poderes, /grupo, /g, /lugares e /masmorra.
 * Quase tudo abre menu; os subcomandos existem para quem prefere digitar.
 */
public final class JogadorCommand implements CommandExecutor, TabCompleter {

    private final SurvivalRpgPlugin plugin;

    public JogadorCommand(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return true;
        }
        if (!plugin.mundoRpg(p.getWorld()) && !cmd.getName().equals("rpg")) {
            plugin.mensagens().enviar(p, "fora-do-rpg");
            return true;
        }
        switch (cmd.getName()) {
            case "rpg" -> rpg(p, a);
            case "classe" -> {
                if (a.length >= 1) {
                    Classe c = Classe.buscar(a[0]);
                    if (c == null) {
                        plugin.mensagens().enviar(p, "classe-invalida");
                        return true;
                    }
                    PerfilRpg r = plugin.perfis().perfil(p);
                    if (r.classe() != null && r.classe() != c && (a.length < 2 || !a[1].equalsIgnoreCase("confirmar"))) {
                        plugin.mensagens().enviar(p, "classe-confirmar-troca", "classe", c.nome().toLowerCase(Locale.ROOT));
                        return true;
                    }
                    plugin.escolherClasse(p, c, false);
                } else {
                    plugin.menus().classes(p);
                }
            }
            case "atributos" -> plugin.menus().atributos(p);
            case "poderes" -> plugin.menus().habilidades(p);
            case "grupo" -> grupo(p, a);
            case "g" -> {
                if (a.length == 0) {
                    plugin.mensagens().enviar(p, "grupo-uso-chat");
                } else {
                    plugin.grupos().chat(p, String.join(" ", a));
                }
            }
            case "lugares" -> plugin.lugares().menu(p, 0);
            case "masmorra" -> {
                if (a.length >= 1 && a[0].equalsIgnoreCase("sair")) {
                    plugin.masmorras().sair(p, true);
                } else {
                    plugin.masmorras().menu(p, true);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void rpg(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ajuda", "help" -> plugin.mensagens().enviarLista(p, "ajuda");
            case "numeros", "números" -> {
                PerfilRpg r = plugin.perfis().perfil(p);
                r.setNumerosDeDano(!r.numerosDeDano());
                plugin.mensagens().enviar(p, r.numerosDeDano() ? "numeros-ligados" : "numeros-desligados");
            }
            case "perfil", "ver" -> {
                Player alvo = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : p;
                if (alvo == null) {
                    plugin.mensagens().enviar(p, "jogador-offline");
                    return;
                }
                PerfilRpg r = plugin.perfis().perfil(alvo);
                plugin.mensagens().enviarLista(p, "perfil-texto", "jogador", alvo.getName(),
                        "classe", r.classe() == null ? "<gray>nenhuma" : r.classe().formatado(),
                        "nivel", String.valueOf(r.nivel()),
                        "lugares", String.valueOf(r.regioesDescobertas().size()));
            }
            default -> {
                if (!plugin.mundoRpg(p.getWorld())) {
                    plugin.mensagens().enviar(p, "fora-do-rpg");
                    return;
                }
                plugin.menus().perfil(p);
            }
        }
    }

    private void grupo(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "convidar", "add", "invite" -> {
                Player alvo = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (alvo == null) {
                    plugin.mensagens().enviar(p, "jogador-offline");
                    return;
                }
                plugin.grupos().convidar(p, alvo);
            }
            case "aceitar", "accept" -> plugin.grupos().aceitar(p);
            case "sair", "leave" -> plugin.grupos().sair(p);
            case "expulsar", "kick" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviarLista(p, "grupo-ajuda");
                    return;
                }
                plugin.grupos().expulsar(p, a[1]);
            }
            case "lista", "ver", "info" -> plugin.grupos().lista(p);
            case "ir", "tp" -> plugin.grupos().irAoLider(p);
            case "chat" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviar(p, "grupo-uso-chat");
                    return;
                }
                plugin.grupos().chat(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            default -> plugin.mensagens().enviarLista(p, "grupo-ajuda");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> opcoes = new ArrayList<>();
        switch (cmd.getName()) {
            case "rpg" -> {
                if (a.length == 1) {
                    opcoes.addAll(List.of("ajuda", "numeros", "perfil"));
                } else if (a.length == 2 && a[0].equalsIgnoreCase("perfil")) {
                    return null;
                }
            }
            case "classe" -> {
                if (a.length == 1) {
                    for (Classe c : Classe.values()) {
                        opcoes.add(c.name().toLowerCase(Locale.ROOT));
                    }
                }
            }
            case "grupo" -> {
                if (a.length == 1) {
                    opcoes.addAll(List.of("convidar", "aceitar", "sair", "expulsar", "lista", "ir", "chat"));
                } else if (a.length == 2 && (a[0].equalsIgnoreCase("convidar") || a[0].equalsIgnoreCase("expulsar"))) {
                    return null;
                }
            }
            case "masmorra" -> {
                if (a.length == 1) {
                    opcoes.add("sair");
                }
            }
            default -> {
            }
        }
        String ultimo = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.startsWith(ultimo));
        return opcoes;
    }
}
