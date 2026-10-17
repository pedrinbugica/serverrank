package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.NivelReputacao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /faccao - facções, relações e sua reputação; /faccao entrar <id>; /faccao sair; /reputacao.
 */
public final class FaccaoCommand implements CommandExecutor, TabCompleter {

    private final NpcVivosPlugin plugin;

    public FaccaoCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("reputacao")) {
            reputacao(p);
            return true;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("guerras")) {
            guerras(p);
            return true;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("sair")) {
            if (p.hasPermission("npcvivos.faccao.sair")) {
                plugin.faccoes().sair(p);
            } else {
                plugin.mensagens().enviar(p, "sem-permissao");
            }
            return true;
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("entrar")) {
            if (!p.hasPermission("npcvivos.faccao.entrar")) {
                plugin.mensagens().enviar(p, "sem-permissao");
                return true;
            }
            Faccao f = plugin.faccoes().obter(a[1]);
            if (f == null) {
                plugin.mensagens().enviar(p, "faccao-nao-existe", "id", a[1]);
            } else {
                plugin.faccoes().entrar(p, f);
            }
            return true;
        }
        lista(p);
        return true;
    }

    private void lista(Player p) {
        Faccao membro = plugin.faccoes().membro(p);
        plugin.mensagens().enviarSemPrefixo(p, "faccoes-cabecalho", "faccao",
                membro == null ? plugin.mensagens().bruta("faccoes-nenhuma") : membro.nome());
        for (Faccao f : plugin.faccoes().todas()) {
            NivelReputacao n = plugin.faccoes().nivel(p.getUniqueId(), f.id());
            plugin.mensagens().enviarSemPrefixo(p, "faccoes-linha", "id", f.id(), "faccao", f.nome(),
                    "reputacao", String.valueOf(plugin.faccoes().reputacao(p.getUniqueId(), f.id())), "nivel", n.formatado());
            for (String d : f.descricao()) {
                plugin.mensagens().enviarSemPrefixo(p, "faccoes-descricao", "texto", d);
            }
            List<String> rel = new ArrayList<>();
            for (Faccao outra : plugin.faccoes().todas()) {
                if (!outra.id().equals(f.id())) {
                    Relacao r = plugin.faccoes().relacao(f.id(), outra.id());
                    rel.add(outra.nome() + "<gray>: " + r.cor() + r.nome() + " <dark_gray>("
                            + Math.round(plugin.diplomacia().pontuacao(f.id(), outra.id())) + ")");
                }
            }
            if (!rel.isEmpty()) {
                plugin.mensagens().enviarSemPrefixo(p, "faccoes-relacoes", "relacoes", String.join("<gray>, ", rel));
            }
        }
        plugin.mensagens().enviarSemPrefixo(p, "faccoes-rodape");
    }

    /** /faccao guerras - placar das guerras que estão acontecendo. */
    private void guerras(Player p) {
        var lista = plugin.guerras().ativas();
        if (lista.isEmpty()) {
            plugin.mensagens().enviar(p, "guerras-nenhuma");
            return;
        }
        plugin.mensagens().enviarSemPrefixo(p, "guerras-cabecalho");
        for (var g : lista) {
            Faccao fa = plugin.faccoes().obter(g.a());
            Faccao fb = plugin.faccoes().obter(g.b());
            plugin.mensagens().enviarSemPrefixo(p, "guerras-linha", "a", fa == null ? g.a() : fa.nome(), "b", fb == null ? g.b() : fb.nome(),
                    "pa", String.valueOf(g.pontosA()), "pb", String.valueOf(g.pontosB()), "meta", String.valueOf(g.meta()),
                    "tempo", dev.pedrobugica.npcvivos.mundo.GuerraService.tempo(g.fim() - System.currentTimeMillis()));
        }
        plugin.mensagens().enviarSemPrefixo(p, "guerras-rodape");
    }

    private void reputacao(Player p) {
        plugin.mensagens().enviarSemPrefixo(p, "reputacao-cabecalho");
        for (Faccao f : plugin.faccoes().todas()) {
            int pontos = plugin.faccoes().reputacao(p.getUniqueId(), f.id());
            NivelReputacao n = NivelReputacao.de(pontos);
            plugin.mensagens().enviarSemPrefixo(p, "reputacao-linha", "faccao", f.nome(), "reputacao", String.valueOf(pontos),
                    "nivel", n.formatado(), "efeito", plugin.mensagens().bruta("efeito-" + n.name().toLowerCase(Locale.ROOT)));
        }
        plugin.mensagens().enviarSemPrefixo(p, "reputacao-rodape");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("reputacao")) {
            return r;
        }
        if (a.length == 1) {
            for (String o : List.of("entrar", "sair", "guerras")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    r.add(o);
                }
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("entrar")) {
            for (Faccao f : plugin.faccoes().todas()) {
                if (f.id().startsWith(a[1].toLowerCase(Locale.ROOT))) {
                    r.add(f.id());
                }
            }
        }
        return r;
    }
}
