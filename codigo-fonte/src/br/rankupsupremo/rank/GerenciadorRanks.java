package br.rankupsupremo.rank;

import br.rankupsupremo.RankupSupremo;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carrega os ranks de ranks.yml e calcula custos (inclusive com prestígio).
 */
public final class GerenciadorRanks {

    private final RankupSupremo plugin;
    private List<Rank> lista = List.of();
    private Map<String, Rank> porId = Map.of();

    private int prestigioMaximo;
    private double prestigioCustoBase;
    private double prestigioAumentoCusto;
    private double aumentoCustoRanks;
    private double bonusVendaPrestigio;

    public GerenciadorRanks(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "ranks.yml"));
        ConfigurationSection sec = yml.getConfigurationSection("ranks");
        List<Rank> nova = new ArrayList<>();
        Map<String, Rank> mapa = new HashMap<>();
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r == null) {
                    continue;
                }
                String chave = id.toLowerCase(Locale.ROOT);
                Material icone = Material.matchMaterial(r.getString("icone", "STONE"));
                if (icone == null || !icone.isItem() || icone.isAir()) {
                    plugin.getLogger().warning("Rank '" + id + "': ícone inválido, usando STONE.");
                    icone = Material.STONE;
                }
                Rank rank = new Rank(chave, nova.size(), r.getString("nome", id), r.getString("tag", "[" + id + "]"),
                        Math.max(0, r.getDouble("custo", 0)), icone, r.getStringList("comandos"),
                        Math.max(0, r.getLong("requisitos.blocos", 0)));
                nova.add(rank);
                mapa.put(chave, rank);
            }
        }
        if (nova.isEmpty()) {
            plugin.getLogger().severe("Nenhum rank encontrado em ranks.yml! Criando rank padrão.");
            Rank padrao = new Rank("inicial", 0, "Inicial", "<gray>[Inicial]", 0, Material.STONE, List.of(), 0);
            nova.add(padrao);
            mapa.put(padrao.id(), padrao);
        }
        lista = List.copyOf(nova);
        porId = Map.copyOf(mapa);

        FileConfiguration c = plugin.getConfig();
        prestigioMaximo = Math.max(0, c.getInt("prestigio.maximo", 100));
        prestigioCustoBase = Math.max(0, c.getDouble("prestigio.custo-base", 50_000_000));
        prestigioAumentoCusto = Math.max(0, c.getDouble("prestigio.aumento-custo-por-prestigio", 0.5));
        aumentoCustoRanks = Math.max(0, c.getDouble("prestigio.aumento-custo-ranks", 0.35));
        bonusVendaPrestigio = Math.max(0, c.getDouble("prestigio.bonus-venda", 0.10));
        plugin.getLogger().info(lista.size() + " ranks carregados.");
    }

    public List<Rank> todos() {
        return lista;
    }

    public Rank primeiro() {
        return lista.get(0);
    }

    public Rank ultimo() {
        return lista.get(lista.size() - 1);
    }

    /** Retorna o rank pelo id; se não existir (ex.: removido da config), retorna o primeiro. */
    public Rank obter(String id) {
        if (id == null) {
            return primeiro();
        }
        Rank r = porId.get(id.toLowerCase(Locale.ROOT));
        return r == null ? primeiro() : r;
    }

    public Rank buscar(String id) {
        return id == null ? null : porId.get(id.toLowerCase(Locale.ROOT));
    }

    public Rank proximo(Rank atual) {
        int i = atual.indice() + 1;
        return i < lista.size() ? lista.get(i) : null;
    }

    /** Custo do rank considerando o prestígio do jogador. */
    public double custo(Rank rank, int prestigio) {
        return rank.custo() * (1.0 + prestigio * aumentoCustoRanks);
    }

    public double custoPrestigio(int prestigioAtual) {
        return prestigioCustoBase * (1.0 + prestigioAtual * prestigioAumentoCusto);
    }

    public int prestigioMaximo() {
        return prestigioMaximo;
    }

    public double bonusVendaPrestigio() {
        return bonusVendaPrestigio;
    }
}
