package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Fase 7 do documento de design: o mundo que muda sozinho. Este serviço guarda o mundo.yml e liga as tarefas
 * do mercado (preço dinâmico e troca entre NPCs), da diplomacia (relações que mudam), das guerras e das invasões.
 */
public final class MundoVivoService {

    private final NpcVivosPlugin plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final List<BukkitTask> tarefas = new ArrayList<>();

    public MundoVivoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        File f = new File(plugin.getDataFolder(), "mundo.yml");
        if (!f.exists()) {
            plugin.saveResource("mundo.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource("mundo.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler o mundo.yml padrão", e);
        }
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public double num(String caminho, double padrao) {
        return cfg.getDouble(caminho, padrao);
    }

    public int inteiro(String caminho, int padrao) {
        return cfg.getInt(caminho, padrao);
    }

    public boolean sim(String caminho, boolean padrao) {
        return cfg.getBoolean(caminho, padrao);
    }

    /** Liga/desliga o preço dinâmico e grava no mundo.yml (comando /npcv mercado). */
    public void setPrecoDinamico(boolean ligado) {
        cfg.set("precos.ativo", ligado);
        File f = new File(plugin.getDataFolder(), "mundo.yml");
        try {
            YamlConfiguration disco = YamlConfiguration.loadConfiguration(f);
            disco.set("precos.ativo", ligado);
            disco.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o mundo.yml", e);
        }
    }

    // ------------------------------------------------------------------ TAREFAS

    public void iniciar() {
        parar();
        long minuto = 20L * 60;
        // Procura e oferta registradas vão "esquecendo" devagar: o preço volta ao normal com o tempo
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.mercado().decair(10), minuto * 10, minuto * 10));
        long troca = Math.max(1, inteiro("comercio-entre-npcs.intervalo-minutos", 20)) * minuto;
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.mercado().trocarEntreNpcs(), troca, troca));
        // Mágoas entre facções cicatrizam aos poucos
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.diplomacia().cicatrizar(), minuto * 60, minuto * 60));
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.guerras().verificar(), 20L * 5, 20L * 20));
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.invasoes().tick(), 20L * 5, 20L));
        long invasao = Math.max(1, inteiro("invasoes.intervalo-minutos", 60)) * minuto;
        tarefas.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.invasoes().talvezIniciar(), invasao, invasao));
    }

    public void parar() {
        tarefas.forEach(BukkitTask::cancel);
        tarefas.clear();
    }
}
