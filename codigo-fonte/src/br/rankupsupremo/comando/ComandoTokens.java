package br.rankupsupremo.comando;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Numeros;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /tokens [jogador] e /tokens pagar <jogador> <quantidade>.
 * Tokens de mina caem minerando no Rankup e compram as melhorias especiais da picareta.
 */
public final class ComandoTokens implements CommandExecutor, TabCompleter {

    private final RankupSupremo plugin;

    public ComandoTokens(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " <jogador>");
                return true;
            }
            plugin.mensagens().enviar(p, "tokens-saldo", "tokens", Numeros.inteiro(plugin.jogadores().obter(p).tokens()));
            return true;
        }
        if (a[0].equalsIgnoreCase("pagar") || a[0].equalsIgnoreCase("enviar")) {
            pagar(s, label, a);
            return true;
        }
        DadosJogador d = buscar(a[0]);
        if (d == null) {
            plugin.mensagens().enviar(s, "admin-jogador-nao-encontrado");
            return true;
        }
        plugin.mensagens().enviar(s, "tokens-saldo-outro", "jogador", d.nome(), "tokens", Numeros.inteiro(d.tokens()));
        return true;
    }

    private void pagar(CommandSender s, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return;
        }
        if (a.length < 3) {
            plugin.mensagens().enviar(p, "admin-uso", "uso", "/" + label + " pagar <jogador> <quantidade>");
            return;
        }
        DadosJogador alvo = buscar(a[1]);
        if (alvo == null) {
            plugin.mensagens().enviar(p, "admin-jogador-nao-encontrado");
            return;
        }
        if (alvo.uuid().equals(p.getUniqueId())) {
            plugin.mensagens().enviar(p, "tokens-pagar-si-mesmo");
            return;
        }
        long qtd;
        try {
            qtd = Long.parseLong(a[2]);
        } catch (NumberFormatException e) {
            qtd = -1;
        }
        if (qtd <= 0) {
            plugin.mensagens().enviar(p, "admin-numero-invalido");
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        synchronized (d) {
            if (d.tokens() < qtd) {
                plugin.mensagens().enviar(p, "tokens-sem-saldo", "tokens", Numeros.inteiro(d.tokens()));
                Acoes.som(p, "entity.villager.no", 1f, 1f);
                return;
            }
            d.adicionarTokens(-qtd);
        }
        alvo.adicionarTokens(qtd);
        String q = Numeros.inteiro(qtd);
        plugin.mensagens().enviar(p, "tokens-pago", "jogador", alvo.nome(), "tokens", q);
        Acoes.som(p, "entity.experience_orb.pickup", 1f, 1.2f);
        Player recebedor = Bukkit.getPlayer(alvo.uuid());
        if (recebedor != null) {
            plugin.mensagens().enviar(recebedor, "tokens-recebido", "jogador", p.getName(), "tokens", q);
            Acoes.som(recebedor, "entity.experience_orb.pickup", 1f, 1.5f);
            plugin.visual().atualizarJogador(recebedor);
        }
        plugin.visual().atualizarJogador(p);
    }

    private DadosJogador buscar(String nome) {
        Player online = Bukkit.getPlayerExact(nome);
        return online != null ? plugin.jogadores().obter(online) : plugin.jogadores().porNome(nome);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) {
            String i = a[0].toLowerCase(Locale.ROOT);
            if ("pagar".startsWith(i)) {
                r.add("pagar");
            }
            nomes(r, i);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("pagar")) {
            nomes(r, a[1].toLowerCase(Locale.ROOT));
        }
        return r;
    }

    private static void nomes(List<String> r, String inicio) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(inicio)) {
                r.add(p.getName());
            }
        }
    }
}
