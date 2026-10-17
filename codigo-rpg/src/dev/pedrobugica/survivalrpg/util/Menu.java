package dev.pedrobugica.survivalrpg.util;


import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Menu (inventário) com ações por slot. Os itens do menu nunca podem ser pegos.
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventario;
    private final Map<Integer, BiConsumer<Player, ClickType>> acoes = new HashMap<>();

    public Menu(int linhas, Component titulo) {
        this.inventario = Bukkit.createInventory(this, Math.max(1, Math.min(6, linhas)) * 9, titulo);
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

    public void preencher(Material vidro) {
        ItemStack v = criar(vidro, " ", List.of());
        for (int i = 0; i < inventario.getSize(); i++) {
            if (inventario.getItem(i) == null) {
                inventario.setItem(i, v);
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

    public static ItemStack criar(Material material, String nome, List<String> lore, String... pares) {
        Material m = material != null && material.isItem() && !material.isAir() ? material : Material.PAPER;
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            meta.displayName(Texto.item(nome, pares));
            if (!lore.isEmpty()) {
                meta.lore(Texto.lore(lore, pares));
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
