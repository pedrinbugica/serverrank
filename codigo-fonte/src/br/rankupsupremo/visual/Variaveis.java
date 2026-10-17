package br.rankupsupremo.visual;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.rank.Rank;
import br.rankupsupremo.util.Numeros;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Variáveis usadas na scoreboard, TAB e PlaceholderAPI.
 */
public final class Variaveis {

    private final RankupSupremo plugin;

    public Variaveis(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public Map<String, String> de(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        Rank atual = plugin.ranks().obter(d.rank());
        Rank proximo = plugin.ranks().proximo(atual);
        double saldo = plugin.economia().saldo(p);
        double custo;
        String proximoTag;
        if (proximo != null) {
            custo = plugin.ranks().custo(proximo, d.prestigio());
            proximoTag = proximo.tag();
        } else if (d.prestigio() < plugin.ranks().prestigioMaximo()) {
            custo = plugin.ranks().custoPrestigio(d.prestigio());
            proximoTag = plugin.mensagens().bruta("variavel-proximo-prestigio");
        } else {
            custo = 0;
            proximoTag = plugin.mensagens().bruta("variavel-maximo");
        }
        double frac = custo <= 0 ? 1 : saldo / custo;
        Map<String, String> v = new LinkedHashMap<>();
        v.put("jogador", p.getName());
        v.put("rank", atual.tag());
        v.put("rank_nome", atual.nome());
        v.put("proximo", proximoTag);
        v.put("custo", Numeros.abreviar(custo));
        v.put("progresso", Numeros.porcentagem(frac));
        v.put("barra", Numeros.barra(frac, 10, "■", "<green>", "<dark_gray>"));
        v.put("dinheiro", Numeros.abreviar(saldo));
        v.put("prestigio", String.valueOf(d.prestigio()));
        v.put("multiplicador", Numeros.multiplicador(plugin.vendas().multiplicador(p, d)));
        v.put("blocos", Numeros.abreviar(d.blocos()));
        v.put("tokens", Numeros.abreviar(d.tokens()));
        double booster = d.boosterAtivo();
        v.put("booster", booster > 0
                ? "<green>+" + Numeros.multiplicador(booster * 100) + "% <gray>("
                + Numeros.tempo(Math.max(0, (d.boosterAte() - System.currentTimeMillis()) / 1000)) + ")"
                : plugin.mensagens().bruta("variavel-desligado"));
        v.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        v.put("max", String.valueOf(Bukkit.getMaxPlayers()));
        v.put("site", plugin.getConfig().getString("site", ""));
        v.put("discord", plugin.getConfig().getString("discord", ""));
        v.put("autovender", d.autovender() && plugin.acoes().podeAutovender(p, d)
                ? plugin.mensagens().bruta("variavel-ligado") : plugin.mensagens().bruta("variavel-desligado"));
        int naSobrevivencia = 0;
        for (Player outro : Bukkit.getOnlinePlayers()) {
            if (plugin.sobrevivencia().ehReinoRpg(outro.getWorld())) {
                naSobrevivencia++;
            }
        }
        v.put("online_sobrevivencia", String.valueOf(naSobrevivencia));
        v.put("online_rankup", String.valueOf(Bukkit.getOnlinePlayers().size() - naSobrevivencia));
        v.put("mundo", nomeMundo(p.getWorld()));
        v.put("mundo_tag", plugin.sobrevivencia().ehReinoRpg(p.getWorld())
                ? plugin.getConfig().getString("chat.tag-mundo.sobrevivencia", "")
                : plugin.getConfig().getString("chat.tag-mundo.rankup", ""));
        return v;
    }

    /** Nome bonito do mundo em que o jogador está. */
    public String nomeMundo(World w) {
        if (plugin.ehMundoRankup(w)) {
            return plugin.mensagens().bruta("nome-mundo-rankup");
        }
        if (w.getName().equalsIgnoreCase(plugin.sobrevivencia().nomeLobbyRpg())) {
            return "Cidadela dos Heróis";
        }
        if (plugin.sobrevivencia().ehSobrevivencia(w)) {
            return switch (w.getEnvironment()) {
                case NETHER -> plugin.mensagens().bruta("nome-mundo-nether");
                case THE_END -> plugin.mensagens().bruta("nome-mundo-end");
                default -> plugin.mensagens().bruta("nome-mundo-sobrevivencia");
            };
        }
        return w.getName();
    }

    public static String aplicar(String texto, Map<String, String> v) {
        String r = texto;
        for (Map.Entry<String, String> e : v.entrySet()) {
            r = r.replace("{" + e.getKey() + "}", e.getValue());
        }
        return r;
    }
}
