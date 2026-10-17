package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Entrada e saída de jogadores: mensagens, picareta inicial, scoreboard e teleporte ao spawn.
 */
public final class JogadorListener implements Listener {

    private final RankupSupremo plugin;
    private final MineracaoListener mineracao;

    public JogadorListener(RankupSupremo plugin, MineracaoListener mineracao) {
        this.plugin = plugin;
        this.mineracao = mineracao;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean novo = !plugin.jogadores().existe(p.getUniqueId());
        DadosJogador d = plugin.jogadores().obter(p);
        d.setNome(p.getName());
        String tag = plugin.ranks().obter(d.rank()).tag();

        String entrada = plugin.getConfig().getString("geral.mensagem-entrada", "");
        e.joinMessage(entrada.isEmpty() ? null : Texto.cor(entrada, "jogador", p.getName(), "rank", tag));

        boolean naSobrevivencia = plugin.sobrevivencia().ehSobrevivencia(p.getWorld());
        if (!naSobrevivencia && plugin.getConfig().getBoolean("geral.desativar-fome", true)) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            // Com o lobby ligado, todo mundo chega no lobby e escolhe o modo nos portais.
            // Sem lobby: quem saiu no mundo Sobrevivência continua lá; os demais vão para o spawn do Rankup.
            boolean lobby = plugin.lobby() != null && plugin.lobby().ativo() && plugin.getConfig().getBoolean("lobby.teleportar-ao-entrar", true);
            if (lobby) {
                plugin.lobby().aoEntrar(p);
            } else if (novo || (!naSobrevivencia && plugin.getConfig().getBoolean("spawn.teleportar-ao-entrar", true))) {
                plugin.acoes().teleportarSpawn(p, false);
            }
            plugin.visual().criar(p);
            // Atualiza a lore das picaretas do servidor (melhorias novas aparecem nas picaretas antigas)
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack it = p.getInventory().getItem(i);
                if (plugin.picareta().ehPicaretaDoServidor(it)) {
                    plugin.picareta().atualizarVisual(it);
                    p.getInventory().setItem(i, it);
                }
            }
            if (!d.recebeuPicareta() && plugin.ehMundoRankup(p.getWorld())) {
                d.setRecebeuPicareta(true);
                plugin.acoes().darPicareta(p, true);
            }
            if (novo) {
                String primeira = plugin.getConfig().getString("geral.mensagem-primeira-entrada", "");
                if (!primeira.isEmpty()) {
                    Bukkit.broadcast(Texto.cor(primeira, "jogador", p.getName(),
                            "numero", String.valueOf(plugin.jogadores().total())));
                }
                plugin.mensagens().enviarLista(p, "boas-vindas", "jogador", p.getName());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoSair(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        DadosJogador d = plugin.jogadores().obter(p);
        String saida = plugin.getConfig().getString("geral.mensagem-saida", "");
        e.quitMessage(saida.isEmpty() ? null
                : Texto.cor(saida, "jogador", p.getName(), "rank", plugin.ranks().obter(d.rank()).tag()));
        plugin.visual().remover(p);
        mineracao.limpar(p.getUniqueId());
        plugin.efeitos().limpar(p);
        plugin.desativarModoConstrucao(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoRenascer(PlayerRespawnEvent e) {
        // Só quem morreu no Rankup volta ao spawn do Rankup; na Sobrevivência valem cama/âncora e o Multiverse.
        if (plugin.ehMundoRankup(e.getPlayer().getWorld()) && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            e.setRespawnLocation(plugin.spawn());
        }
    }
}
