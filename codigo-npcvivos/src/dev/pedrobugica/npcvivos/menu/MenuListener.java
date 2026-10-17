package dev.pedrobugica.npcvivos.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/**
 * Cliques nos menus: cancela sempre e executa a ação no tick seguinte.
 */
public final class MenuListener implements Listener {

    private final Plugin plugin;

    public MenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicar(InventoryClickEvent e) {
        Inventory topo = e.getView().getTopInventory();
        if (!(topo.getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        ClickType clique = e.getClick();
        if (slot >= 0 && slot < topo.getSize()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == menu) {
                    menu.clicar(p, slot, clique);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoArrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }
}
