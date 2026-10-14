package br.rankupsupremo.config;

import br.rankupsupremo.RankupSupremo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Estado interno do servidor (estruturas já construídas, posições das minas...).
 * Arquivo: plugins/RankupSupremo/estado.yml (não é necessário editar).
 */
public final class Estado {

    private final RankupSupremo plugin;
    private final File arquivo;
    private YamlConfiguration yml;

    public Estado(RankupSupremo plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "estado.yml");
        carregar();
    }

    public void carregar() {
        yml = YamlConfiguration.loadConfiguration(arquivo);
    }

    public void salvar() {
        try {
            yml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar estado.yml", e);
        }
    }

    public boolean spawnConstruido() {
        return yml.getBoolean("spawn-construido", false);
    }

    public void setSpawnConstruido(boolean v) {
        yml.set("spawn-construido", v);
        salvar();
    }

    public boolean comandosIniciaisExecutados() {
        return yml.getBoolean("comandos-iniciais-executados", false);
    }

    public void setComandosIniciaisExecutados(boolean v) {
        yml.set("comandos-iniciais-executados", v);
        salvar();
    }

    public boolean spawnSobrevivenciaConstruido() {
        return yml.getBoolean("spawn-sobrevivencia-construido", false);
    }

    public void setSpawnSobrevivenciaConstruido(boolean v) {
        yml.set("spawn-sobrevivencia-construido", v);
        salvar();
    }

    /** Blocos de "comandos-unicos" do config.yml que já foram executados. */
    public boolean blocoExecutado(String id) {
        return yml.getStringList("comandos-unicos-executados").contains(id);
    }

    public void marcarBlocoExecutado(String id) {
        List<String> l = new ArrayList<>(yml.getStringList("comandos-unicos-executados"));
        if (!l.contains(id)) {
            l.add(id);
        }
        yml.set("comandos-unicos-executados", l);
        salvar();
    }

    /** Posição fixa (índice) da mina no mundo. Minas novas recebem o próximo índice livre. */
    public int indiceMina(String id) {
        ConfigurationSection s = yml.getConfigurationSection("minas-indices");
        if (s != null && s.contains(id)) {
            return s.getInt(id);
        }
        int maior = -1;
        if (s != null) {
            for (String k : s.getKeys(false)) {
                maior = Math.max(maior, s.getInt(k));
            }
        }
        int novo = maior + 1;
        yml.set("minas-indices." + id, novo);
        salvar();
        return novo;
    }

    public boolean minaConstruida(String id) {
        return yml.getStringList("minas-construidas").contains(id);
    }

    public void setMinaConstruida(String id, boolean v) {
        List<String> l = new ArrayList<>(yml.getStringList("minas-construidas"));
        l.remove(id);
        if (v) {
            l.add(id);
        }
        yml.set("minas-construidas", l);
        salvar();
    }
}
