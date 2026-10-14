package br.rankupsupremo.visual;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard lateral, cabeçalho/rodapé do TAB e nome dos jogadores na lista.
 */
public final class GerenciadorVisual {

    private static final String[] ENTRADAS = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e"};

    private final RankupSupremo plugin;
    private final Variaveis variaveis;
    private final Map<UUID, List<Component>> cache = new HashMap<>();
    private BukkitTask tarefa;
    private int ciclo;

    public GerenciadorVisual(RankupSupremo plugin) {
        this.plugin = plugin;
        this.variaveis = new Variaveis(plugin);
    }

    public Variaveis variaveis() {
        return variaveis;
    }

    public void iniciar() {
        parar();
        for (Player p : Bukkit.getOnlinePlayers()) {
            criar(p);
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::atualizarTodos, 20L, 20L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    private String processar(Player p, String texto, Map<String, String> v) {
        String r = Variaveis.aplicar(texto, v);
        if (plugin.temPlaceholderAPI() && r.indexOf('%') >= 0) {
            r = PlaceholderAPI.setPlaceholders(p, r);
        }
        return r;
    }

    public void criar(Player p) {
        cache.remove(p.getUniqueId());
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("scoreboard.ativado", true)) {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("rankup", Criteria.DUMMY,
                    Texto.cor(c.getString("scoreboard.titulo", "RANKUP")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            p.setScoreboard(sb);
        }
        atualizarJogador(p);
    }

    public void remover(Player p) {
        cache.remove(p.getUniqueId());
    }

    private void atualizarTodos() {
        ciclo++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                atualizarScoreboard(p, variaveis.de(p));
                if (ciclo % 2 == 0) {
                    atualizarTab(p);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Erro ao atualizar visual de " + p.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Atualiza tudo de um jogador (após rankup, prestígio, entrada...). */
    public void atualizarJogador(Player p) {
        Map<String, String> v = variaveis.de(p);
        atualizarScoreboard(p, v);
        atualizarTab(p);
        String formato = plugin.getConfig().getString("tab.nome-jogador", "{rank} <white>{jogador}");
        p.playerListName(Texto.cor(processar(p, formato, v)));
    }

    private void atualizarScoreboard(Player p, Map<String, String> v) {
        if (!plugin.getConfig().getBoolean("scoreboard.ativado", true)) {
            return;
        }
        Scoreboard sb = p.getScoreboard();
        Objective obj = sb.getObjective("rankup");
        if (obj == null) {
            return;
        }
        String chave = plugin.sobrevivencia().ehSobrevivencia(p.getWorld()) ? "scoreboard.linhas-sobrevivencia" : "scoreboard.linhas";
        List<String> linhas = plugin.getConfig().getStringList(chave);
        if (linhas.isEmpty()) {
            linhas = plugin.getConfig().getStringList("scoreboard.linhas");
        }
        int total = Math.min(linhas.size(), ENTRADAS.length);
        List<Component> novas = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            novas.add(Texto.cor(processar(p, linhas.get(i), v)));
        }
        List<Component> antigas = cache.get(p.getUniqueId());
        if (novas.equals(antigas)) {
            return;
        }
        for (int i = 0; i < total; i++) {
            if (antigas != null && i < antigas.size() && antigas.get(i).equals(novas.get(i))) {
                continue;
            }
            Score s = obj.getScore(ENTRADAS[i]);
            s.setScore(total - i);
            s.customName(novas.get(i));
        }
        for (int i = total; i < ENTRADAS.length; i++) {
            sb.resetScores(ENTRADAS[i]);
        }
        cache.put(p.getUniqueId(), novas);
    }

    private void atualizarTab(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("tab.ativado", true)) {
            return;
        }
        Map<String, String> v = variaveis.de(p);
        List<Component> cab = new ArrayList<>();
        for (String l : c.getStringList("tab.cabecalho")) {
            cab.add(Texto.cor(processar(p, l, v)));
        }
        List<Component> rod = new ArrayList<>();
        for (String l : c.getStringList("tab.rodape")) {
            rod.add(Texto.cor(processar(p, l, v)));
        }
        p.sendPlayerListHeaderAndFooter(Texto.linhas(cab), Texto.linhas(rod));
    }
}
