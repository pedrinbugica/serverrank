package br.rankupsupremo.dados;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.rank.GerenciadorRanks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Guarda os dados de todos os jogadores em plugins/RankupSupremo/jogadores.yml.
 */
public final class GerenciadorJogadores {

    private final RankupSupremo plugin;
    private final File arquivo;
    private final Map<UUID, DadosJogador> dados = new ConcurrentHashMap<>();
    private final Object travaArquivo = new Object();
    private volatile List<DadosJogador> top = List.of();

    public GerenciadorJogadores(RankupSupremo plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "jogadores.yml");
    }

    public void carregar() {
        dados.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(arquivo);
        String rankInicial = plugin.ranks().primeiro().id();
        for (String chave : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(chave);
            if (s == null) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(chave);
            } catch (IllegalArgumentException e) {
                continue;
            }
            DadosJogador d = new DadosJogador(uuid, s.getString("nome", "?"), s.getString("rank", rankInicial));
            d.setPrestigio(s.getInt("prestigio", 0));
            d.setBlocos(s.getLong("blocos", 0));
            d.setAutovender(s.getBoolean("autovender", false));
            d.setRecebeuPicareta(s.getBoolean("recebeu-picareta", false));
            d.setUltimaSobrevivencia(s.getString("ultima-sobrevivencia"));
            d.setVisitouSobrevivencia(s.getBoolean("visitou-sobrevivencia", false));
            d.setTokens(s.getLong("tokens", 0));
            d.setBooster(s.getDouble("booster.bonus", 0), s.getLong("booster.ate", 0));
            d.setBoosterTokens(s.getDouble("booster-tokens.bonus", 0), s.getLong("booster-tokens.ate", 0));
            dados.put(uuid, d);
        }
        atualizarTop();
        plugin.getLogger().info("Dados de " + dados.size() + " jogador(es) carregados.");
    }

    /** Obtém (ou cria) os dados do jogador. */
    public DadosJogador obter(Player p) {
        return dados.computeIfAbsent(p.getUniqueId(),
                id -> new DadosJogador(id, p.getName(), plugin.ranks().primeiro().id()));
    }

    public DadosJogador obterSeExistir(UUID uuid) {
        return dados.get(uuid);
    }

    public boolean existe(UUID uuid) {
        return dados.containsKey(uuid);
    }

    public DadosJogador porNome(String nome) {
        for (DadosJogador d : dados.values()) {
            if (d.nome() != null && d.nome().equalsIgnoreCase(nome)) {
                return d;
            }
        }
        return null;
    }

    public Collection<DadosJogador> todos() {
        return dados.values();
    }

    public int total() {
        return dados.size();
    }

    /** Recalcula o ranking (prestígio > rank > blocos). */
    public void atualizarTop() {
        GerenciadorRanks ranks = plugin.ranks();
        List<DadosJogador> lista = new ArrayList<>(dados.values());
        lista.sort(Comparator.comparingInt(DadosJogador::prestigio).reversed()
                .thenComparing(Comparator.comparingInt((DadosJogador d) -> ranks.obter(d.rank()).indice()).reversed())
                .thenComparing(Comparator.comparingLong(DadosJogador::blocos).reversed()));
        top = List.copyOf(lista.subList(0, Math.min(10, lista.size())));
    }

    public List<DadosJogador> top() {
        return top;
    }

    private String gerarConteudo() {
        YamlConfiguration yml = new YamlConfiguration();
        for (DadosJogador d : dados.values()) {
            String k = d.uuid().toString();
            yml.set(k + ".nome", d.nome());
            yml.set(k + ".rank", d.rank());
            yml.set(k + ".prestigio", d.prestigio());
            yml.set(k + ".blocos", d.blocos());
            yml.set(k + ".autovender", d.autovender());
            yml.set(k + ".recebeu-picareta", d.recebeuPicareta());
            if (d.ultimaSobrevivencia() != null) {
                yml.set(k + ".ultima-sobrevivencia", d.ultimaSobrevivencia());
            }
            if (d.visitouSobrevivencia()) {
                yml.set(k + ".visitou-sobrevivencia", true);
            }
            yml.set(k + ".tokens", d.tokens());
            if (d.boosterAtivo() > 0) {
                yml.set(k + ".booster.bonus", d.boosterBonus());
                yml.set(k + ".booster.ate", d.boosterAte());
            }
            if (d.boosterTokensAtivo() > 0) {
                yml.set(k + ".booster-tokens.bonus", d.boosterTokensBonus());
                yml.set(k + ".booster-tokens.ate", d.boosterTokensAte());
            }
        }
        return yml.saveToString();
    }

    private void escrever(String conteudo) {
        synchronized (travaArquivo) {
            try {
                File tmp = new File(arquivo.getParentFile(), "jogadores.yml.tmp");
                Files.writeString(tmp.toPath(), conteudo, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), arquivo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar jogadores.yml", e);
            }
        }
    }

    /** Salva em segundo plano (sem travar o servidor). */
    public void salvarAssincrono() {
        String conteudo = gerarConteudo();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> escrever(conteudo));
    }

    public void salvarSincrono() {
        escrever(gerarConteudo());
    }
}
