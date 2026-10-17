package dev.pedrobugica.npcvivos.dados;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Dados num banco SQLite (dados.db): um arquivo só, sem configuração, e não corrompe se o servidor cair
 * no meio de um salvamento. Tudo continua em memória; o banco é gravado de tempos em tempos.
 * Na primeira vez, os dados do dados.yml antigo são migrados sozinhos.
 */
public final class SqliteRepositorio extends RepositorioMemoria {

    private final File arquivo;
    private final Object trava = new Object();

    public SqliteRepositorio(JavaPlugin plugin) {
        super(plugin);
        this.arquivo = new File(plugin.getDataFolder(), "dados.db");
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + arquivo.getAbsolutePath());
    }

    @Override
    public void carregar() {
        boolean novo = !arquivo.exists();
        synchronized (trava) {
            try (Connection c = conectar(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS dados (categoria TEXT NOT NULL, chave TEXT NOT NULL, valor TEXT NOT NULL,"
                        + " PRIMARY KEY (categoria, chave))");
                Map<String, Map<String, String>> foto = new LinkedHashMap<>();
                try (ResultSet r = s.executeQuery("SELECT categoria, chave, valor FROM dados")) {
                    while (r.next()) {
                        foto.computeIfAbsent(r.getString(1), k -> new TreeMap<>()).put(r.getString(2), r.getString(3));
                    }
                }
                importar(foto);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível abrir o banco dados.db", e);
                return;
            }
        }
        YamlRepositorio antigo = new YamlRepositorio(plugin);
        if (novo && antigo.existe()) {
            importar(antigo.lerArquivo());
            gravar(exportar());
            File migrado = new File(plugin.getDataFolder(), "dados.yml.migrado-para-sqlite");
            if (antigo.arquivo().renameTo(migrado)) {
                plugin.getLogger().info("Dados do dados.yml migrados para o banco SQLite (dados.db).");
            }
        }
    }

    @Override
    protected void gravar(Map<String, Map<String, String>> foto) {
        synchronized (trava) {
            try (Connection c = conectar()) {
                c.setAutoCommit(false);
                try (Statement s = c.createStatement();
                     PreparedStatement ins = c.prepareStatement("INSERT INTO dados (categoria, chave, valor) VALUES (?, ?, ?)")) {
                    s.execute("DELETE FROM dados");
                    for (Map.Entry<String, Map<String, String>> cat : foto.entrySet()) {
                        for (Map.Entry<String, String> e : cat.getValue().entrySet()) {
                            ins.setString(1, cat.getKey());
                            ins.setString(2, e.getKey());
                            ins.setString(3, e.getValue());
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar no banco dados.db", e);
            }
        }
    }
}
