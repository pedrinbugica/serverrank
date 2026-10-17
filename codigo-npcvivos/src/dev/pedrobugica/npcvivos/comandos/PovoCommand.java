package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.povo.MoradorService;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /povo (staff: gerar moradores, renomear, amizade...) e /trabalho (jogadores: o trabalho pedido por um morador). */
public final class PovoCommand implements CommandExecutor, TabCompleter {

    public static final String PERM_ADMIN = "npcvivos.admin.povo";

    private final NpcVivosPlugin plugin;

    public PovoCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private MoradorService m() {
        return plugin.moradores();
    }

    private static void msg(CommandSender s, String t) {
        s.sendMessage(Texto.cor(t));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "<red>Só dentro do jogo.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("trabalho")) {
            if (a.length > 0 && a[0].equalsIgnoreCase("abandonar")) {
                m().abandonarTrabalho(p);
            } else {
                m().mostrarTrabalho(p);
            }
            return true;
        }
        if (!p.hasPermission(PERM_ADMIN)) {
            msg(p, "<red>Sem permissão.");
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gerar" -> {
                int qtd = 5;
                String prof = null;
                if (a.length > 1) {
                    try {
                        qtd = Math.max(1, Math.min(40, Integer.parseInt(a[1])));
                    } catch (NumberFormatException e) {
                        msg(p, "<red>Quantidade inválida.");
                        return true;
                    }
                }
                if (a.length > 2) {
                    prof = a[2].toLowerCase(Locale.ROOT);
                    if (!MoradorService.profissoes().contains(prof)) {
                        msg(p, "<red>Profissões: " + String.join(", ", MoradorService.profissoes()));
                        return true;
                    }
                }
                if (!m().cfg().getStringList("mundos").contains(p.getWorld().getName())) {
                    msg(p, "<yellow>Aviso: este mundo não está em povo.yml > mundos, então eles ficam com cara de aldeão.");
                }
                int n = m().gerar(p.getLocation(), qtd, prof);
                msg(p, "<green>" + n + " morador(es) criado(s) em volta de você.");
            }
            case "info" -> {
                AbstractVillager v = m().maisPerto(p, 8);
                if (v == null) {
                    msg(p, "<red>Nenhum morador perto (8 blocos).");
                    return true;
                }
                MoradorService.Identidade id = m().identidade(v);
                msg(p, "<gold><bold>" + id.completo() + " <gray>(" + m().nomeProfissao(v, id.feminina()) + ")");
                msg(p, "<gray>Personalidade: <white>" + id.personalidade().nome(id.feminina()) + " <gray>| Skin: <white>" + id.skin()
                        + " <gray>| Vida: <red>" + (int) Math.ceil(v.getHealth()));
                msg(p, "<gray>Amizade com você: " + MoradorService.coracoes(m().amizade(v, p.getUniqueId())) + " <dark_gray>("
                        + m().amizade(v, p.getUniqueId()) + ")");
            }
            case "nome" -> {
                if (a.length < 2) {
                    msg(p, "<yellow>/povo nome <nome> [sobrenome]");
                    return true;
                }
                AbstractVillager v = m().maisPerto(p, 8);
                if (v == null) {
                    msg(p, "<red>Nenhum morador perto (8 blocos).");
                    return true;
                }
                m().renomear(v, a[1], a.length > 2 ? a[2] : null);
                msg(p, "<green>Agora se chama " + m().nomeCompleto(v) + ".");
            }
            case "amizade" -> {
                AbstractVillager v = m().maisPerto(p, 8);
                if (v == null || a.length < 2) {
                    msg(p, "<yellow>/povo amizade <-100 a 100> <gray>(no morador mais perto)");
                    return true;
                }
                try {
                    m().definirAmizade(v, p, Math.max(-100, Math.min(100, Integer.parseInt(a[1]))));
                } catch (NumberFormatException e) {
                    msg(p, "<red>Número inválido.");
                }
            }
            case "recarregar" -> {
                m().carregar();
                plugin.corpos().revestirTodos(m());
                msg(p, "<green>povo.yml recarregado e moradores refeitos.");
            }
            default -> {
                msg(p, "<gold><bold>/povo <gray>- moradores humanos");
                msg(p, "<yellow>gerar [quantidade] [profissão] <gray>- cria moradores em volta de você");
                msg(p, "<yellow>info <gray>- quem é o morador mais perto");
                msg(p, "<yellow>nome <nome> [sobrenome] <gray>- renomeia o mais perto");
                msg(p, "<yellow>amizade <valor> <gray>- define a amizade dele com você");
                msg(p, "<yellow>recarregar <gray>- relê o povo.yml");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("trabalho")) {
            if (a.length == 1) {
                op.add("abandonar");
            }
        } else if (s.hasPermission(PERM_ADMIN)) {
            if (a.length == 1) {
                op.addAll(List.of("gerar", "info", "nome", "amizade", "recarregar"));
            } else if (a.length == 3 && a[0].equalsIgnoreCase("gerar")) {
                op.addAll(MoradorService.profissoes());
            }
        }
        String ultimo = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return op.stream().filter(x -> x.startsWith(ultimo)).toList();
    }
}
