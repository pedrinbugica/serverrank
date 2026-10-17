package dev.pedrobugica.npcvivos.combate;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Duelo 1 contra 1 com um NPC duelista: o jogador escolhe a dificuldade, paga a aposta e luta.
 * Ganhar paga o prêmio, reputação e um título; sair da arena, morrer ou estourar o tempo é derrota.
 */
public final class DueloService {

    /** Um duelo em andamento. */
    private static final class Duelo {
        private final String npcId;
        private final UUID jogador;
        private final String dificuldade;
        private final long comecou;
        private final double aposta;

        private Duelo(String npcId, UUID jogador, String dificuldade, double aposta) {
            this.npcId = npcId;
            this.jogador = jogador;
            this.dificuldade = dificuldade;
            this.aposta = aposta;
            this.comecou = System.currentTimeMillis();
        }
    }

    private final NpcVivosPlugin plugin;
    /** id do NPC -> duelo. */
    private final Map<String, Duelo> duelos = new HashMap<>();
    private BukkitTask tarefa;

    public DueloService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    public void iniciar() {
        parar();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::vigiar, 100L, 20L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    // ------------------------------------------------------------------ DIFICULDADES

    public List<String> dificuldades() {
        ConfigurationSection s = plugin.comportamento().cfg().getConfigurationSection("duelos.dificuldades");
        return s == null ? List.of() : List.copyOf(s.getKeys(false));
    }

    public ConfigurationSection dificuldade(String nome) {
        return nome == null ? null
                : plugin.comportamento().cfg().getConfigurationSection("duelos.dificuldades." + nome.toLowerCase(Locale.ROOT));
    }

    public String nomeBonito(String dificuldade) {
        ConfigurationSection s = dificuldade(dificuldade);
        return s == null ? dificuldade : s.getString("nome", dificuldade);
    }

    // ------------------------------------------------------------------ COMEÇAR

    public boolean comecar(Player p, NpcVivo npc, String nomeDificuldade) {
        ConfigurationSection d = dificuldade(nomeDificuldade);
        if (!plugin.comportamento().ativo() || !npc.tem(Papel.DUELISTA) || d == null) {
            plugin.mensagens().enviar(p, "combate-desligado");
            return false;
        }
        if (emDuelo(npc.id())) {
            plugin.mensagens().enviar(p, "duelo-npc-ocupado");
            return false;
        }
        if (duelosDoJogador(p) != null) {
            plugin.mensagens().enviar(p, "duelo-ja-esta");
            return false;
        }
        Cerebro c = plugin.comportamento().cerebro(npc);
        if (c == null || c.morto() || c.duelistaOcupado()) {
            plugin.mensagens().enviar(p, "duelo-npc-ocupado");
            return false;
        }
        double aposta = d.getDouble("aposta", 0);
        if (aposta > 0 && (!plugin.economia().ativa() || !plugin.economia().sacar(p, aposta))) {
            plugin.mensagens().enviar(p, "sem-dinheiro", "preco", Texto.dinheiro(aposta));
            return false;
        }
        // O duelista pega vida e dano da dificuldade escolhida
        c.setAtributos(plugin.comportamento().atributosDe(npc)
                .comVidaEDano(d.getDouble("vida", 60), d.getDouble("dano", 5)));
        c.setAlvo(p.getUniqueId());
        c.setEstado(EstadoNpc.COMBATE);
        duelos.put(npc.id(), new Duelo(npc.id(), p.getUniqueId(), nomeDificuldade.toLowerCase(Locale.ROOT), aposta));
        plugin.combate().atualizarNome(npc, c);
        plugin.mensagens().enviar(p, "duelo-comecou", "npc", npc.nome(), "dificuldade", nomeBonito(nomeDificuldade),
                "aposta", Texto.dinheiro(aposta), "segundos", String.valueOf(tempoMaximo()));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
        plugin.comercio().registrar(p, npc, "DUELO", nomeDificuldade, 1, aposta);
        return true;
    }

    private int tempoMaximo() {
        return plugin.comportamento().inteiro("duelos.tempo-maximo-segundos", 180);
    }

    // ------------------------------------------------------------------ VIGIAR

    private void vigiar() {
        double raio = plugin.comportamento().num("duelos.raio-da-arena", 24);
        for (Duelo d : new HashMap<>(duelos).values()) {
            NpcVivo npc = plugin.npcs().obter(d.npcId);
            Cerebro c = plugin.comportamento().cerebroExistente(d.npcId);
            Player p = Bukkit.getPlayer(d.jogador);
            if (npc == null || c == null) {
                duelos.remove(d.npcId);
                continue;
            }
            if (p == null || p.isDead()) {
                terminar(npc, c, p, false, "duelo-perdeu-morreu");
                continue;
            }
            Cerebro brain = c;
            if (brain.posto() != null && (!p.getWorld().equals(brain.posto().getWorld())
                    || p.getLocation().distanceSquared(brain.posto()) > raio * raio)) {
                terminar(npc, c, p, false, "duelo-perdeu-fugiu");
                continue;
            }
            if ((System.currentTimeMillis() - d.comecou) / 1000 >= tempoMaximo()) {
                terminar(npc, c, p, false, "duelo-perdeu-tempo");
            }
        }
    }

    // ------------------------------------------------------------------ FIM

    /** O NPC caiu: o jogador venceu. */
    public void npcPerdeu(NpcVivo npc, Player autor) {
        Duelo d = duelos.get(npc.id());
        Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
        if (d == null || c == null) {
            return;
        }
        terminar(npc, c, autor, true, "duelo-venceu");
    }

    private void terminar(NpcVivo npc, Cerebro c, Player p, boolean jogadorVenceu, String chave) {
        Duelo d = duelos.remove(npc.id());
        if (d == null) {
            return;
        }
        ConfigurationSection cfg = dificuldade(d.dificuldade);
        c.setAlvo(null);
        // Tira a vida e o dano da dificuldade e devolve os do papel. Cuidado: setAtributos enche a vida,
        // então se ela caiu no duelo precisamos deixá-la caída de novo para o respawn trazer o corpo de volta.
        boolean caiu = c.morto();
        c.setAtributos(plugin.comportamento().atributosDe(npc));
        if (caiu) {
            c.marcarMorte();
        }
        c.ocuparDuelista(plugin.comportamento().inteiro("duelos.espera-entre-duelos-segundos", 10) * 1000L);
        if (!c.morto()) {
            c.setEstado(EstadoNpc.VOLTANDO_AO_POSTO);
        }
        plugin.combate().atualizarNome(npc, c);
        if (p == null || cfg == null) {
            return;
        }
        if (jogadorVenceu) {
            double premio = cfg.getDouble("premio", 0);
            if (premio > 0 && plugin.economia().ativa()) {
                plugin.economia().depositar(p, premio);
            }
            Faccao f = plugin.faccoes().daNpc(npc);
            int rep = cfg.getInt("reputacao", 0);
            if (f != null && rep > 0) {
                plugin.faccoes().reputacaoPorAjuda(p, f, rep);
            }
            String titulo = cfg.getString("titulo", "");
            plugin.mensagens().enviar(p, chave, "npc", npc.nome(), "premio", Texto.dinheiro(premio),
                    "dificuldade", nomeBonito(d.dificuldade), "titulo", titulo);
            if (!titulo.isBlank()) {
                plugin.repositorio().guardarTitulo(p.getUniqueId(), titulo);
                // Título é conquista: vai para o disco na hora, não espera o salvamento de 5 minutos
                plugin.repositorio().salvarAssincrono();
                plugin.mensagens().enviar(p, "duelo-titulo", "titulo", titulo);
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            plugin.comercio().registrar(p, npc, "DUELO", "venceu-" + d.dificuldade, 1, premio);
        } else {
            plugin.mensagens().enviar(p, chave, "npc", npc.nome(), "aposta", Texto.dinheiro(d.aposta));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            plugin.comercio().registrar(p, npc, "DUELO", "perdeu-" + d.dificuldade, 1, d.aposta);
        }
        if (plugin.comportamento().cfg().getBoolean("duelos.curar-no-fim", true) && !p.isDead()) {
            AttributeInstance maxima = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.min(p.getHealth() + 10, maxima == null ? 20 : maxima.getValue()));
            p.setFoodLevel(20);
        }
    }

    /** O jogador desistiu (saiu, deslogou ou usou o botão da conversa). */
    public void desistir(Player p) {
        String id = duelosDoJogador(p);
        if (id == null) {
            plugin.mensagens().enviar(p, "duelo-nenhum");
            return;
        }
        NpcVivo npc = plugin.npcs().obter(id);
        Cerebro c = plugin.comportamento().cerebroExistente(id);
        if (npc != null && c != null) {
            terminar(npc, c, p, false, "duelo-desistiu");
        }
    }

    // ------------------------------------------------------------------ CONSULTAS

    public boolean emDuelo(String npcId) {
        return duelos.containsKey(npcId);
    }

    public boolean ehDesafiante(String npcId, Player p) {
        Duelo d = duelos.get(npcId);
        return d != null && d.jogador.equals(p.getUniqueId());
    }

    /** Id do NPC com quem o jogador está duelando (null se não está em duelo). */
    public String duelosDoJogador(Player p) {
        for (Duelo d : duelos.values()) {
            if (d.jogador.equals(p.getUniqueId())) {
                return d.npcId;
            }
        }
        return null;
    }

    public void esquecer(UUID jogador) {
        for (Duelo d : new HashMap<>(duelos).values()) {
            if (d.jogador.equals(jogador)) {
                NpcVivo npc = plugin.npcs().obter(d.npcId);
                Cerebro c = plugin.comportamento().cerebroExistente(d.npcId);
                if (npc != null && c != null) {
                    terminar(npc, c, null, false, "duelo-desistiu");
                }
            }
        }
    }
}
