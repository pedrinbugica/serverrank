package br.rankupsupremo.config;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Mensagens do plugin, carregadas de mensagens.yml.
 */
public final class Mensagens {

    private final RankupSupremo plugin;
    private YamlConfiguration yml;
    private String prefixo = "";

    public Mensagens(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        File arquivo = new File(plugin.getDataFolder(), "mensagens.yml");
        yml = YamlConfiguration.loadConfiguration(arquivo);
        // Mensagens novas (de atualizações do plugin) são adicionadas ao arquivo automaticamente.
        InputStream padrao = plugin.getResource("mensagens.yml");
        if (padrao != null) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(padrao, StandardCharsets.UTF_8)));
            yml.options().copyDefaults(true);
            try {
                yml.save(arquivo);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível atualizar mensagens.yml", e);
            }
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

    public Component componente(String chave, String... pares) {
        return Texto.cor(bruta(chave), pares);
    }

    /** Envia a mensagem com o prefixo do servidor. Mensagens vazias não são enviadas. */
    public void enviar(CommandSender alvo, String chave, String... pares) {
        String s = bruta(chave);
        if (s.isEmpty()) {
            return;
        }
        alvo.sendMessage(Texto.cor(prefixo + s, pares));
    }

    /** Envia sem prefixo (para listas, cabeçalhos etc). */
    public void enviarSemPrefixo(CommandSender alvo, String chave, String... pares) {
        String s = bruta(chave);
        if (s.isEmpty()) {
            return;
        }
        alvo.sendMessage(Texto.cor(s, pares));
    }

    public void enviarLista(CommandSender alvo, String chave, String... pares) {
        for (String linha : lista(chave)) {
            alvo.sendMessage(Texto.cor(linha, pares));
        }
    }

    public void transmitir(String chave, String... pares) {
        String s = bruta(chave);
        if (s.isEmpty()) {
            return;
        }
        Bukkit.broadcast(Texto.cor(prefixo + s, pares));
    }
}
