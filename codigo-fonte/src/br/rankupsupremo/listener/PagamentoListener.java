package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * O dinheiro do Rankup e o da Sobrevivência são separados (Multiverse-Inventories troca o saldo ao mudar de mundo).
 * Por isso o /pay só funciona entre jogadores online no mesmo mundo; senão o dinheiro "pularia" de um lado para o outro.
 */
public final class PagamentoListener implements Listener {

    private static final Set<String> COMANDOS = Set.of("pay", "epay", "pagar");

    private final RankupSupremo plugin;

    public PagamentoListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoComando(PlayerCommandPreprocessEvent e) {
        String[] partes = e.getMessage().length() > 1 ? e.getMessage().substring(1).trim().split("\\s+") : new String[0];
        if (partes.length < 2) {
            return; // sem alvo: o Essentials mostra como usar
        }
        String comando = partes[0].toLowerCase(Locale.ROOT);
        int doisPontos = comando.indexOf(':');
        if (doisPontos >= 0) {
            comando = comando.substring(doisPontos + 1);
        }
        if (!COMANDOS.contains(comando)) {
            return;
        }
        Player p = e.getPlayer();
        String nome = partes[1];
        if (nome.contains("*") || nome.contains(",")) {
            e.setCancelled(true);
            plugin.mensagens().enviar(p, "pagamento-varios");
            return;
        }
        Player alvo = Bukkit.getPlayerExact(nome);
        if (alvo == null) {
            alvo = Bukkit.getPlayer(nome);
        }
        if (alvo == null || !p.canSee(alvo)) {
            e.setCancelled(true);
            plugin.mensagens().enviar(p, "pagamento-offline");
            return;
        }
        if (!grupo(p.getWorld()).equals(grupo(alvo.getWorld()))) {
            e.setCancelled(true);
            plugin.mensagens().enviar(p, "pagamento-outro-mundo", "jogador", alvo.getName(),
                    "mundo", plugin.visual().variaveis().nomeMundo(alvo.getWorld()));
        }
    }

    /** Mundos que dividem o mesmo dinheiro. */
    private String grupo(World w) {
        if (plugin.ehMundoRankup(w)) {
            return "rankup";
        }
        if (plugin.sobrevivencia().ehSobrevivencia(w)) {
            return "sobrevivencia";
        }
        return w.getName();
    }
}
