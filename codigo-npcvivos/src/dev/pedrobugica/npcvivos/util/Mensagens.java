package dev.pedrobugica.npcvivos.util;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Todas as mensagens do plugin ficam em mensagens.yml (o código nunca escreve texto direto para o jogador).
 * Chaves novas de atualizações entram sozinhas no arquivo, sem apagar o que a staff editou.
 */
public final class Mensagens {

    private final JavaPlugin plugin;
    private YamlConfiguration yml = new YamlConfiguration();
    private String prefixo = "";

    public Mensagens(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        File arquivo = new File(plugin.getDataFolder(), "mensagens.yml");
        if (!arquivo.exists()) {
            plugin.saveResource("mensagens.yml", false);
        }
        yml = YamlConfiguration.loadConfiguration(arquivo);
        try (InputStream in = plugin.getResource("mensagens.yml")) {
            if (in != null) {
                YamlConfiguration padrao = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean mudou = false;
                for (String k : padrao.getKeys(true)) {
                    if (!yml.contains(k)) {
                        yml.set(k, padrao.get(k));
                        mudou = true;
                    }
                }
                if (mudou) {
                    yml.save(arquivo);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível atualizar mensagens.yml", e);
        }
        prefixo = yml.getString("prefixo", "");
    }

    public String bruta(String chave) {
        String s = yml.getString(chave);
        return s == null ? "<red>[mensagem ausente: " + chave + "]" : s;
    }

    public List<String> lista(String chave) {
        return yml.getStringList(chave);
    }

    public void enviar(CommandSender alvo, String chave, String... pares) {
        String s = bruta(chave);
        if (!s.isEmpty()) {
            alvo.sendMessage(Texto.cor(prefixo + s, pares));
        }
    }

    public void enviarSemPrefixo(CommandSender alvo, String chave, String... pares) {
        String s = bruta(chave);
        if (!s.isEmpty()) {
            alvo.sendMessage(Texto.cor(s, pares));
        }
    }

    public void enviarLista(CommandSender alvo, String chave, String... pares) {
        for (String linha : lista(chave)) {
            alvo.sendMessage(Texto.cor(linha, pares));
        }
    }
}
