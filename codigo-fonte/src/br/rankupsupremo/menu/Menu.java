package br.rankupsupremo.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Menu (GUI) simples: cada slot pode ter uma ação ao ser clicado.
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventario;
    private final Map<Integer, BiConsumer<Player, ClickType>> acoes = new HashMap<>();

    public Menu(int linhas, Component titulo) {
        this.inventario = Bukkit.createInventory(this, Math.max(1, Math.min(6, linhas)) * 9, titulo);
    }

    public void item(int slot, ItemStack item) {
        item(slot, item, null);
    }

    public void item(int slot, ItemStack item, BiConsumer<Player, ClickType> acao) {
        if (slot < 0 || slot >= inventario.getSize()) {
            return;
        }
        inventario.setItem(slot, item);
        if (acao == null) {
            acoes.remove(slot);
        } else {
            acoes.put(slot, acao);
        }
    }

    public void preencherVazios(ItemStack item) {
        for (int i = 0; i < inventario.getSize(); i++) {
            if (inventario.getItem(i) == null) {
                inventario.setItem(i, item);
            }
        }
    }

    public void abrir(Player p) {
        p.openInventory(inventario);
    }

    void clicar(Player p, int slot, ClickType tipo) {
        BiConsumer<Player, ClickType> acao = acoes.get(slot);
        if (acao != null) {
            acao.accept(p, tipo);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventario;
    }
}
