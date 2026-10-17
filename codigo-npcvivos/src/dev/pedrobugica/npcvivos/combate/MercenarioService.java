package dev.pedrobugica.npcvivos.combate;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.ComportamentoService;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mercenários: o jogador paga para um NPC segui-lo por um tempo e lutar por ele.
 * Regras para não virar exército nem atrapalhar quem não quer PvP:
 * um por jogador, só em mundo com PvP ligado, e quem morre só pode ser recontratado depois de um tempo.
 */
public final class MercenarioService {

    private final NpcVivosPlugin plugin;
    /** dono -> id do NPC contratado. */
    private final Map<UUID, String> contratos = new HashMap<>();
    /** id do NPC -> instante em que o contrato acaba. */
    private final Map<String, Long> fim = new HashMap<>();
    /** id do NPC -> instante em que ele pode ser contratado de novo. */
    private final Map<String, Long> esperaPorMorte = new HashMap<>();
    private BukkitTask tarefa;

    public MercenarioService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    public void iniciar() {
        parar();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::verificarContratos, 100L, 20L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    // ------------------------------------------------------------------ CONTRATAR

    public int minutos() {
        return plugin.comportamento().inteiro("mercenarios.minutos", 30);
    }

    /** Preço do contrato, com o mesmo desconto das lojas (reputação e membro da facção). */
    public double preco(Player p, NpcVivo npc) {
        double base = plugin.comportamento().num("mercenarios.preco", 1500);
        return Math.round(base * plugin.faccoes().multiplicadorCompra(p, npc) * 100) / 100.0;
    }

    /** Devolve true se contratou. As mensagens de recusa já são enviadas aqui. */
    public boolean contratar(Player p, NpcVivo npc) {
        if (!plugin.comportamento().ativo() || !npc.tem(Papel.MERCENARIO)) {
            plugin.mensagens().enviar(p, "combate-desligado");
            return false;
        }
        if (plugin.comportamento().mundoProibido(p.getWorld().getName())) {
            plugin.mensagens().enviar(p, "mercenario-mundo-proibido");
            return false;
        }
        if (plugin.comportamento().cfg().getBoolean("mercenarios.so-com-pvp-ligado", true) && !ComportamentoService.pvpLigado(p.getWorld())) {
            plugin.mensagens().enviar(p, "mercenario-sem-pvp");
            return false;
        }
        int limite = plugin.comportamento().inteiro("mercenarios.limite-por-jogador", 1);
        if (contratos.containsKey(p.getUniqueId()) && limite <= 1) {
            plugin.mensagens().enviar(p, "mercenario-ja-tem", "npc", nomeDoContratado(p));
            return false;
        }
        Cerebro c = plugin.comportamento().cerebro(npc);
        if (c == null) {
            return false;
        }
        if (c.morto()) {
            plugin.mensagens().enviar(p, "mercenario-morto");
            return false;
        }
        Long espera = esperaPorMorte.get(npc.id());
        if (espera != null && espera > System.currentTimeMillis()) {
            plugin.mensagens().enviar(p, "mercenario-esperando", "minutos",
                    String.valueOf(Math.max(1, (espera - System.currentTimeMillis()) / 60000)));
            return false;
        }
        if (c.dono() != null) {
            plugin.mensagens().enviar(p, "mercenario-ocupado");
            return false;
        }
        double custo = preco(p, npc);
        if (!plugin.economia().ativa() || !plugin.economia().sacar(p, custo)) {
            plugin.mensagens().enviar(p, "sem-dinheiro", "preco", Texto.dinheiro(custo));
            return false;
        }
        c.setDono(p.getUniqueId());
        c.encherVida();
        c.setEstado(EstadoNpc.SEGUINDO_DONO);
        contratos.put(p.getUniqueId(), npc.id());
        fim.put(npc.id(), System.currentTimeMillis() + minutos() * 60_000L);
        plugin.combate().atualizarNome(npc, c);
        plugin.mensagens().enviar(p, "mercenario-contratado", "npc", npc.nome(),
                "minutos", String.valueOf(minutos()), "valor", Texto.dinheiro(custo));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        plugin.comercio().registrar(p, npc, "MERCEN", "contrato-" + minutos() + "min", 1, custo);
        return true;
    }

    public void dispensar(Player p, String chaveMensagem) {
        String id = contratos.remove(p.getUniqueId());
        if (id == null) {
            plugin.mensagens().enviar(p, "mercenario-nenhum");
            return;
        }
        soltar(id);
        plugin.mensagens().enviar(p, chaveMensagem);
    }

    /** Solta o mercenário: ele volta ao posto e pode ser contratado de novo. */
    private void soltar(String npcId) {
        fim.remove(npcId);
        Cerebro c = plugin.comportamento().cerebroExistente(npcId);
        NpcVivo npc = plugin.npcs().obter(npcId);
        if (c != null) {
            c.setDono(null);
            c.setAlvo(null);
            if (!c.morto()) {
                c.setEstado(EstadoNpc.VOLTANDO_AO_POSTO);
            }
        }
        if (npc != null && c != null) {
            plugin.combate().atualizarNome(npc, c);
        }
    }

    // ------------------------------------------------------------------ TEMPO E AVISOS

    private void verificarContratos() {
        long agora = System.currentTimeMillis();
        for (Map.Entry<UUID, String> e : new HashMap<>(contratos).entrySet()) {
            Player dono = Bukkit.getPlayer(e.getKey());
            Long acaba = fim.get(e.getValue());
            Cerebro c = plugin.comportamento().cerebroExistente(e.getValue());
            if (c == null || c.morto()) {
                contratos.remove(e.getKey());
                esperaPorMorte.put(e.getValue(), agora
                        + plugin.comportamento().inteiro("mercenarios.espera-para-recontratar-minutos", 10) * 60_000L);
                soltar(e.getValue());
                if (dono != null) {
                    plugin.mensagens().enviar(dono, "mercenario-caiu");
                }
                continue;
            }
            if (acaba == null || acaba <= agora) {
                contratos.remove(e.getKey());
                soltar(e.getValue());
                if (dono != null) {
                    plugin.mensagens().enviar(dono, "mercenario-acabou");
                }
                continue;
            }
            long faltaSegundos = (acaba - agora) / 1000;
            if (dono != null && faltaSegundos == 60) {
                plugin.mensagens().enviar(dono, "mercenario-quase-acabando");
            }
        }
    }

    // ------------------------------------------------------------------ BRIGA

    /** Alguém bateu no dono (ou o dono bateu em alguém): o mercenário entra na briga. */
    public void defender(Player dono, Player inimigo) {
        String id = contratos.get(dono.getUniqueId());
        if (id == null || inimigo.equals(dono) || !ComportamentoService.atacavel(inimigo)) {
            return;
        }
        if (!ComportamentoService.pvpLigado(inimigo.getWorld()) || plugin.comportamento().mundoProibido(inimigo.getWorld().getName())) {
            return;
        }
        Cerebro c = plugin.comportamento().cerebroExistente(id);
        if (c == null || c.morto()) {
            return;
        }
        c.setAlvo(inimigo.getUniqueId());
        if (!c.estado().lutando()) {
            c.setEstado(EstadoNpc.COMBATE);
        }
    }

    // ------------------------------------------------------------------ CONSULTAS

    public String idContratado(Player p) {
        return contratos.get(p.getUniqueId());
    }

    public String nomeDoContratado(Player p) {
        NpcVivo npc = plugin.npcs().obter(contratos.get(p.getUniqueId()));
        return npc == null ? "—" : npc.nome();
    }

    /** Minutos que faltam do contrato (0 = sem contrato). */
    public long minutosRestantes(Player p) {
        String id = contratos.get(p.getUniqueId());
        Long acaba = id == null ? null : fim.get(id);
        return acaba == null ? 0 : Math.max(0, (acaba - System.currentTimeMillis() + 59_999) / 60_000);
    }

    public void esquecer(UUID jogador) {
        String id = contratos.remove(jogador);
        if (id != null) {
            soltar(id);
        }
    }
}
