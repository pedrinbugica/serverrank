package br.rankupsupremo.comando;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.rank.Rank;
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
 * /rankupadmin - administração do RankUP Supremo.
 */
public final class ComandoAdmin implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("recarregar", "setrank", "setprestigio", "darpicareta",
            "setpicareta", "tokens", "resetarmina", "reconstruir", "setspawn", "construir", "info");

    private final RankupSupremo plugin;

    public ComandoAdmin(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("rankupsupremo.admin")) {
            plugin.mensagens().enviar(s, "sem-permissao");
            return true;
        }
        if (a.length == 0) {
            plugin.mensagens().enviarLista(s, "admin-ajuda");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "recarregar" -> {
                plugin.recarregar();
                plugin.mensagens().enviar(s, "admin-recarregado");
            }
            case "setrank" -> {
                if (a.length < 3) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " setrank <jogador> <rank>");
                    return true;
                }
                DadosJogador d = dados(s, a[1]);
                if (d == null) {
                    return true;
                }
                Rank r = plugin.ranks().buscar(a[2]);
                if (r == null) {
                    plugin.mensagens().enviar(s, "admin-rank-invalido", "ranks", idsRanks());
                    return true;
                }
                d.setRank(r.id());
                atualizarSeOnline(d);
                plugin.mensagens().enviar(s, "admin-rank-definido", "jogador", d.nome(), "rank", r.tag());
            }
            case "setprestigio" -> {
                if (a.length < 3) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " setprestigio <jogador> <numero>");
                    return true;
                }
                DadosJogador d = dados(s, a[1]);
                if (d == null) {
                    return true;
                }
                int n;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "admin-numero-invalido");
                    return true;
                }
                d.setPrestigio(Math.min(n, plugin.ranks().prestigioMaximo()));
                atualizarSeOnline(d);
                plugin.mensagens().enviar(s, "admin-prestigio-definido", "jogador", d.nome(), "prestigio",
                        String.valueOf(d.prestigio()));
            }
            case "darpicareta" -> {
                Player alvo = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
                if (alvo == null) {
                    plugin.mensagens().enviar(s, "admin-jogador-nao-encontrado");
                    return true;
                }
                plugin.acoes().darPicareta(alvo, true);
                plugin.mensagens().enviar(s, "admin-picareta-dada", "jogador", alvo.getName());
            }
            case "setpicareta" -> {
                if (!(s instanceof Player p) || a.length < 3) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso",
                            "/" + label + " setpicareta <melhoria> <nivel> (segurando a picareta)");
                    return true;
                }
                GerenciadorPicareta.Tipo tipo = GerenciadorPicareta.tipoPorNome(a[1]);
                var item = p.getInventory().getItemInMainHand();
                if (tipo == null || !GerenciadorPicareta.ehPicareta(item)) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso",
                            "/" + label + " setpicareta <melhoria> <nivel> (segurando a picareta)");
                    return true;
                }
                int nivel;
                try {
                    // Eficiência é encantamento do jogo (máx. 255); as outras melhorias aceitam níveis de admin maiores
                    int limite = tipo == GerenciadorPicareta.Tipo.EFICIENCIA ? 255 : 10_000;
                    nivel = Math.max(0, Math.min(limite, Integer.parseInt(a[2])));
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "admin-numero-invalido");
                    return true;
                }
                plugin.picareta().definirNivel(item, tipo, nivel);
                p.getInventory().setItemInMainHand(item);
                plugin.mensagens().enviar(s, "admin-picareta-definida", "melhoria", tipo.chave(), "nivel", String.valueOf(nivel));
            }
            case "tokens" -> {
                if (a.length < 4) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " tokens <jogador> <dar|tirar|definir> <quantidade>");
                    return true;
                }
                DadosJogador d = dados(s, a[1]);
                if (d == null) {
                    return true;
                }
                long qtd;
                try {
                    qtd = Math.max(0, Long.parseLong(a[3]));
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "admin-numero-invalido");
                    return true;
                }
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "dar" -> d.adicionarTokens(qtd);
                    case "tirar" -> d.adicionarTokens(-qtd);
                    case "definir" -> d.setTokens(qtd);
                    default -> {
                        plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " tokens <jogador> <dar|tirar|definir> <quantidade>");
                        return true;
                    }
                }
                atualizarSeOnline(d);
                plugin.mensagens().enviar(s, "admin-tokens-definido", "jogador", d.nome(), "tokens", Numeros.inteiro(d.tokens()));
            }
            case "resetarmina" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " resetarmina <mina|todas>");
                    return true;
                }
                if (a[1].equalsIgnoreCase("todas")) {
                    plugin.minas().resetarTodas(true);
                    plugin.mensagens().enviar(s, "admin-mina-resetada", "mina", "todas");
                    return true;
                }
                Mina m = plugin.minas().obter(a[1]);
                if (m == null) {
                    plugin.mensagens().enviar(s, "mina-nao-encontrada");
                    return true;
                }
                plugin.minas().resetar(m, true);
                plugin.mensagens().enviar(s, "admin-mina-resetada", "mina", m.nome());
            }
            case "reconstruir" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " reconstruir <mina|spawn>");
                    return true;
                }
                if (a[1].equalsIgnoreCase("spawn")) {
                    plugin.construtor().construirSpawn(plugin.mundo());
                    plugin.mensagens().enviar(s, "admin-reconstruido", "alvo", "spawn");
                    return true;
                }
                Mina m = plugin.minas().obter(a[1]);
                if (m == null || m.mundo() == null) {
                    plugin.mensagens().enviar(s, "mina-nao-encontrada");
                    return true;
                }
                plugin.mensagens().enviar(s, "admin-reconstruindo", "alvo", m.nome());
                plugin.construtor().limparAreaMina(m, () -> {
                    plugin.construtor().construirMina(m);
                    plugin.estado().setMinaConstruida(m.id(), true);
                    plugin.minas().resetar(m, false);
                    plugin.mensagens().enviar(s, "admin-reconstruido", "alvo", m.nome());
                });
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) {
                    plugin.mensagens().enviar(s, "apenas-jogador");
                    return true;
                }
                plugin.definirSpawn(p.getLocation());
                plugin.mensagens().enviar(s, "admin-spawn-definido");
            }
            case "construir" -> {
                if (!(s instanceof Player p)) {
                    plugin.mensagens().enviar(s, "apenas-jogador");
                    return true;
                }
                boolean ativo = plugin.alternarModoConstrucao(p);
                plugin.mensagens().enviar(s, ativo ? "admin-construir-ativado" : "admin-construir-desativado");
            }
            case "info" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviar(s, "admin-uso", "uso", "/" + label + " info <jogador>");
                    return true;
                }
                DadosJogador d = dados(s, a[1]);
                if (d == null) {
                    return true;
                }
                plugin.mensagens().enviarLista(s, "admin-info", "jogador", d.nome(),
                        "rank", plugin.ranks().obter(d.rank()).tag(), "prestigio", String.valueOf(d.prestigio()),
                        "blocos", Numeros.inteiro(d.blocos()), "autovender", d.autovender() ? "sim" : "não",
                        "tokens", Numeros.inteiro(d.tokens()),
                        "uuid", d.uuid().toString());
            }
            default -> plugin.mensagens().enviarLista(s, "admin-ajuda");
        }
        return true;
    }

    private DadosJogador dados(CommandSender s, String nome) {
        Player online = Bukkit.getPlayerExact(nome);
        DadosJogador d = online != null ? plugin.jogadores().obter(online) : plugin.jogadores().porNome(nome);
        if (d == null) {
            plugin.mensagens().enviar(s, "admin-jogador-nao-encontrado");
        }
        return d;
    }

    private void atualizarSeOnline(DadosJogador d) {
        Player p = Bukkit.getPlayer(d.uuid());
        if (p != null) {
            plugin.visual().atualizarJogador(p);
        }
    }

    private String idsRanks() {
        List<String> ids = new ArrayList<>();
        for (Rank r : plugin.ranks().todos()) {
            ids.add(r.id());
        }
        return String.join(", ", ids);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (!s.hasPermission("rankupsupremo.admin")) {
            return r;
        }
        if (a.length == 1) {
            filtrar(r, SUBS, a[0]);
            return r;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            switch (sub) {
                case "setrank", "setprestigio", "darpicareta", "info", "tokens" -> {
                    List<String> nomes = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        nomes.add(p.getName());
                    }
                    filtrar(r, nomes, a[1]);
                }
                case "resetarmina", "reconstruir" -> {
                    List<String> ids = new ArrayList<>();
                    for (Mina m : plugin.minas().todas()) {
                        ids.add(m.id());
                    }
                    ids.add(sub.equals("resetarmina") ? "todas" : "spawn");
                    filtrar(r, ids, a[1]);
                }
                case "setpicareta" -> {
                    List<String> chaves = new ArrayList<>();
                    for (GerenciadorPicareta.Tipo t : GerenciadorPicareta.Tipo.values()) {
                        chaves.add(t.chave());
                    }
                    filtrar(r, chaves, a[1]);
                }
                default -> {
                }
            }
        } else if (a.length == 3 && sub.equals("tokens")) {
            filtrar(r, List.of("dar", "tirar", "definir"), a[2]);
        } else if (a.length == 3 && sub.equals("setrank")) {
            List<String> ids = new ArrayList<>();
            for (Rank rank : plugin.ranks().todos()) {
                ids.add(rank.id());
            }
            filtrar(r, ids, a[2]);
        }
        return r;
    }

    private static void filtrar(List<String> destino, List<String> opcoes, String inicio) {
        String i = inicio.toLowerCase(Locale.ROOT);
        for (String o : opcoes) {
            if (o.toLowerCase(Locale.ROOT).startsWith(i)) {
                destino.add(o);
            }
        }
    }
}
