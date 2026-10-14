package br.rankupsupremo.menu;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ferramentas do /menuop que precisam de eventos: perguntas respondidas no chat (anúncio, motivo, valores)
 * e jogadores congelados.
 */
public final class FerramentasOp implements Listener {

    static final String FOGOS_SEGUROS = "fogos-seguros";

    private record Pergunta(Consumer<String> resposta, long expira) {
    }

    private final RankupSupremo plugin;
    private final Map<UUID, Pergunta> perguntas = new ConcurrentHashMap<>();
    private final Set<UUID> congelados = ConcurrentHashMap.newKeySet();

    public FerramentasOp(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    /** Fecha o menu e espera o admin digitar a resposta no chat (60 s). */
    public void perguntar(Player admin, String pergunta, Consumer<String> resposta) {
        admin.closeInventory();
        perguntas.put(admin.getUniqueId(), new Pergunta(resposta, System.currentTimeMillis() + 60_000));
        plugin.mensagens().enviar(admin, "menuop-pergunta", "pergunta", pergunta);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoFalar(AsyncChatEvent e) {
        Pergunta p = perguntas.remove(e.getPlayer().getUniqueId());
        if (p == null) {
            return;
        }
        e.setCancelled(true);
        Player admin = e.getPlayer();
        if (System.currentTimeMillis() > p.expira()) {
            return; // passou do tempo: a mensagem é descartada e não vai para o chat
        }
        String texto = Texto.plano(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!admin.isOnline()) {
                return;
            }
            if (texto.isEmpty() || texto.equalsIgnoreCase("cancelar")) {
                plugin.mensagens().enviar(admin, "menuop-pergunta-cancelada");
                return;
            }
            p.resposta().accept(texto);
        });
    }

    // ------------------------------------------------------------------ CONGELAR

    public boolean congelado(Player p) {
        return congelados.contains(p.getUniqueId());
    }

    /** Liga/desliga; retorna true se ficou congelado. */
    public boolean alternarCongelado(Player p) {
        if (!congelados.remove(p.getUniqueId())) {
            congelados.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent e) {
        if (!congelados.contains(e.getPlayer().getUniqueId())) {
            return;
        }
        Location de = e.getFrom();
        Location para = e.getTo();
        if (de.getX() != para.getX() || de.getY() != para.getY() || de.getZ() != para.getZ()) {
            Location fixo = de.clone();
            fixo.setYaw(para.getYaw());
            fixo.setPitch(para.getPitch());
            e.setTo(fixo); // pode olhar em volta, mas não anda
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (congelados.contains(p.getUniqueId()) && !p.hasPermission(MenuOp.PERMISSAO)) {
            String cmd = e.getMessage().toLowerCase();
            if (!cmd.startsWith("/msg ") && !cmd.startsWith("/r ") && !cmd.startsWith("/tell ") && !cmd.startsWith("/helpop")) {
                e.setCancelled(true);
                plugin.mensagens().enviar(p, "menuop-congelado-aviso");
            }
        }
    }

    /** Fogos soltos pelo /menuop não machucam ninguém. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoDanoFogos(EntityDamageByEntityEvent e) {
        if (e.getDamager().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, FOGOS_SEGUROS))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        perguntas.remove(e.getPlayer().getUniqueId());
    }
}
