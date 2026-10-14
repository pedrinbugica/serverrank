package br.rankupsupremo.visual;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.mundo.ConstrutorMundo;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hologramas (TextDisplay) do spawn e das minas. Não são salvos no mundo: são recriados a cada início.
 */
public final class GerenciadorHologramas {

    private final RankupSupremo plugin;
    private final NamespacedKey chave;
    private final Map<String, TextDisplay> hologramas = new HashMap<>();
    private final Map<String, Location> locais = new HashMap<>();
    private BukkitTask tarefa;
    private int ciclo;

    public GerenciadorHologramas(RankupSupremo plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "holograma");
    }

    public void iniciar(World mundo) {
        removerTodos();
        if (!plugin.getConfig().getBoolean("hologramas.ativado", true)) {
            return;
        }
        limparAntigos(mundo);
        int x = ConstrutorMundo.SPAWN_X;
        int y = ConstrutorMundo.SPAWN_Y;
        int z = ConstrutorMundo.SPAWN_Z;
        locais.put("spawn", new Location(mundo, x + 0.5, y + 4.2, z + 0.5));
        locais.put("comandos", new Location(mundo, x + 8.5, y + 1.6, z + 0.5));
        locais.put("top", new Location(mundo, x - 7.5, y + 1.6, z + 0.5));
        for (Mina m : plugin.minas().todas()) {
            if (m.mundo() != null) {
                locais.put("mina:" + m.id(), m.localHolograma());
            }
        }
        World sobrevivencia = plugin.sobrevivencia().mundo();
        if (sobrevivencia != null) {
            locais.put("sobrevivencia", localSobrevivencia(sobrevivencia));
        }
        atualizar(true);
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, () -> atualizar(false), 20L, 20L);
    }

    private static Location localSobrevivencia(World w) {
        Location s = w.getSpawnLocation();
        return new Location(w, s.getBlockX() + 0.5, s.getBlockY() + 2.6, s.getBlockZ() - 3 + 0.5);
    }

    /** Chamado quando o mundo Sobrevivência é criado/carregado depois do início do servidor. */
    public void adicionarSobrevivencia(World w) {
        if (tarefa == null || !plugin.getConfig().getBoolean("hologramas.ativado", true)) {
            return;
        }
        TextDisplay antigo = hologramas.remove("sobrevivencia");
        if (antigo != null && antigo.isValid()) {
            antigo.remove();
        }
        locais.put("sobrevivencia", localSobrevivencia(w));
        definir("sobrevivencia", linhas(plugin.getConfig().getStringList("hologramas.sobrevivencia")));
    }

    private void limparAntigos(World mundo) {
        for (Entity e : mundo.getEntitiesByClass(TextDisplay.class)) {
            if (e.getPersistentDataContainer().has(chave, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
    }

    public void removerTodos() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (TextDisplay td : hologramas.values()) {
            if (td.isValid()) {
                td.remove();
            }
        }
        hologramas.clear();
        locais.clear();
    }

    private void atualizar(boolean tudo) {
        ciclo++;
        if (tudo || ciclo % 10 == 0) {
            definir("spawn", linhas(plugin.getConfig().getStringList("hologramas.spawn")));
            definir("comandos", linhas(plugin.getConfig().getStringList("hologramas.comandos")));
            definir("top", top());
            definir("sobrevivencia", linhas(plugin.getConfig().getStringList("hologramas.sobrevivencia")));
        }
        for (Mina m : plugin.minas().todas()) {
            if (m.mundo() == null) {
                continue;
            }
            long segundos = Math.max(0, (m.proximoReset() - System.currentTimeMillis()) / 1000);
            String tempo = m.resetando() ? plugin.mensagens().bruta("holograma-resetando") : Numeros.tempo(segundos);
            List<Component> l = new ArrayList<>();
            for (String s : plugin.getConfig().getStringList("hologramas.mina")) {
                l.add(Texto.cor(s, "mina", m.nome(), "rank", plugin.minas().requisito(m),
                        "restante", Numeros.porcentagem(m.restante()),
                        "barra", Numeros.barra(m.restante(), 10, "■", "<green>", "<dark_gray>"),
                        "tempo", tempo));
            }
            definir("mina:" + m.id(), Texto.linhas(l));
        }
    }

    private Component linhas(List<String> config) {
        List<Component> l = new ArrayList<>();
        for (String s : config) {
            l.add(Texto.cor(s));
        }
        return Texto.linhas(l);
    }

    private Component top() {
        List<Component> l = new ArrayList<>();
        l.add(Texto.cor(plugin.getConfig().getString("hologramas.top-titulo", "TOP")));
        l.add(Component.empty());
        List<DadosJogador> top = plugin.jogadores().top();
        if (top.isEmpty()) {
            l.add(Texto.cor(plugin.getConfig().getString("hologramas.top-vazio", "")));
        }
        String formato = plugin.getConfig().getString("hologramas.top-linha", "{posicao}. {jogador}");
        int pos = 1;
        for (DadosJogador d : top) {
            l.add(Texto.cor(formato, "posicao", String.valueOf(pos++), "jogador", d.nome(),
                    "rank", plugin.ranks().obter(d.rank()).tag(), "prestigio", String.valueOf(d.prestigio()),
                    "blocos", Numeros.abreviar(d.blocos())));
        }
        return Texto.linhas(l);
    }

    private void definir(String id, Component texto) {
        Location loc = locais.get(id);
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        TextDisplay td = hologramas.get(id);
        if (td == null || !td.isValid()) {
            loc.getWorld().addPluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);
            td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                d.setLineWidth(400);
                d.setViewRange(1.5f);
                d.getPersistentDataContainer().set(chave, PersistentDataType.BYTE, (byte) 1);
                d.text(texto);
            });
            hologramas.put(id, td);
            return;
        }
        if (!texto.equals(td.text())) {
            td.text(texto);
        }
    }
}
