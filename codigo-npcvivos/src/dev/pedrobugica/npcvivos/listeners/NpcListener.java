package dev.pedrobugica.npcvivos.listeners;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * O jogador "toca" os NPCs por aqui: clique (conversa/loja) e dano (cancelado). Também recria os NPCs
 * quando o chunk ou o mundo carrega.
 */
public final class NpcListener implements Listener {

    private final NpcVivosPlugin plugin;

    public NpcListener(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * O jogo manda dois cliques ao clicar numa entidade ("no ponto" e "na entidade"), e o servidor pode
     * entregar um ou os dois: tratamos os dois e o DialogoService ignora o repetido.
     */
    private void clique(PlayerInteractEntityEvent e) {
        NpcVivo npc = plugin.npcs().porEntidade(e.getRightClicked());
        if (npc == null) {
            return;
        }
        e.setCancelled(true);
        if (npc.tem(dev.pedrobugica.npcvivos.core.Papel.INVASOR)) {
            return; // soldado de invasão não conversa
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!e.getPlayer().hasPermission("npcvivos.usar")) {
            plugin.mensagens().enviar(e.getPlayer(), "sem-permissao");
            return;
        }
        plugin.dialogo().interagir(e.getPlayer(), npc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEntityEvent e) {
        clique(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarEm(PlayerInteractAtEntityEvent e) {
        clique(e);
    }

    /**
     * O manequim nunca leva dano de verdade: a vida dos NPCs de combate é do plugin (CombateService).
     * Cancelamos em HIGHEST e não antes, para o bônus da arma aprimorada (que entra em HIGH) já estar
     * somado no dano que lemos aqui. Se outro plugin já tinha cancelado (área protegida), nada acontece.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoDano(EntityDamageEvent e) {
        if (!plugin.npcs().ehEntidadeNpc(e.getEntity())) {
            return;
        }
        boolean areaProtegida = e.isCancelled();
        e.setCancelled(true);
        NpcVivo npc = plugin.npcs().porEntidade(e.getEntity());
        if (npc != null && !areaProtegida) {
            plugin.combate().aoLevarDano(npc, e);
        }
    }

    @EventHandler
    public void aoCarregarChunk(ChunkLoadEvent e) {
        Chunk c = e.getChunk();
        int cx = c.getX();
        int cz = c.getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (c.isLoaded()) {
                plugin.npcs().aoCarregarChunk(c.getWorld(), cx, cz);
            }
        });
    }

    @EventHandler
    public void aoCarregarEntidades(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            plugin.npcs().entidadeCarregada(en);
        }
    }

    @EventHandler
    public void aoCarregarMundo(WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.npcs().spawnarTodos());
        // Ex.: o mundo Sobrevivência foi criado depois que o plugin ligou. Espera o RankupSupremo montar a praça
        // do spawn (ele limpa as árvores em volta 1 segundo depois) para os NPCs não ficarem em cima de uma copa.
        Bukkit.getScheduler().runTaskLater(plugin, plugin::criarExemplos, 60L);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        plugin.dialogo().esquecer(e.getPlayer().getUniqueId());
        plugin.faccoes().esquecer(e.getPlayer().getUniqueId());
        plugin.guardas().esquecer(e.getPlayer().getUniqueId());
    }
}
