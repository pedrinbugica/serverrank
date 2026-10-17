package dev.pedrobugica.npcvivos.dados;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Dados em um arquivo YAML (dados.yml). Bom para começar e fácil de ler à mão.
 */
public final class YamlRepositorio extends RepositorioMemoria {

    private final File arquivo;
    private final Object trava = new Object();

    public YamlRepositorio(JavaPlugin plugin) {
        super(plugin);
        this.arquivo = new File(plugin.getDataFolder(), "dados.yml");
    }

    public boolean existe() {
        return arquivo.exists();
    }

    public File arquivo() {
        return arquivo;
    }

    @Override
    public void carregar() {
        importar(lerArquivo());
    }

    /** Lê o dados.yml como fotografia (também usado para migrar para o SQLite). */
    Map<String, Map<String, String>> lerArquivo() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        Map<String, Map<String, String>> d = new LinkedHashMap<>();
        for (String cat : y.getKeys(false)) {
            Map<String, String> valores = new TreeMap<>();
            if (cat.equals("dia")) {
                d.computeIfAbsent("geral", c -> new TreeMap<>()).put("dia", y.getString("dia"));
                continue;
            }
            if (cat.equals("marcas")) {
                y.getStringList("marcas").forEach(m -> valores.put(m, "1"));
            } else {
                ConfigurationSection s = y.getConfigurationSection(cat);
                if (s == null) {
                    continue;
                }
                for (String k : s.getKeys(true)) {
                    if (!s.isConfigurationSection(k)) {
                        valores.put(k, String.valueOf(s.get(k)));
                    }
                }
            }
            d.put(cat, valores);
        }
        return d;
    }

    @Override
    protected void gravar(Map<String, Map<String, String>> foto) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("dia", foto.getOrDefault("geral", Map.of()).get("dia"));
        for (Map.Entry<String, Map<String, String>> cat : foto.entrySet()) {
            if (cat.getKey().equals("geral")) {
                continue;
            }
            if (cat.getKey().equals("marcas")) {
                y.set("marcas", new ArrayList<>(cat.getValue().keySet()));
                continue;
            }
            cat.getValue().forEach((k, v) -> y.set(cat.getKey() + "." + k, numeroOuTexto(v)));
        }
        String conteudo = y.saveToString();
        synchronized (trava) {
            try {
                File tmp = new File(arquivo.getParentFile(), "dados.yml.tmp");
                Files.writeString(tmp.toPath(), conteudo, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), arquivo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar dados.yml", e);
            }
        }
    }

    private static Object numeroOuTexto(String v) {
        try {
            if (v.matches("-?\\d+")) {
                return Long.parseLong(v);
            }
            if (v.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(v);
            }
        } catch (NumberFormatException e) {
            return v;
        }
        return v;
    }
}
