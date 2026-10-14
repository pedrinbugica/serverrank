package br.rankupsupremo.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Criação rápida de itens para menus.
 */
public final class Itens {

    private Itens() {
    }

    public static ItemStack criar(Material material, String nome, List<String> lore, boolean brilho, String... pares) {
        Material m = material != null && material.isItem() && !material.isAir() ? material : Material.PAPER;
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            meta.displayName(Texto.item(nome, pares));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Texto.lore(lore, pares));
            }
            meta.addItemFlags(ItemFlag.values());
            if (brilho) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }

    public static ItemStack criar(Material material, String nome, List<String> lore, String... pares) {
        return criar(material, nome, lore, false, pares);
    }

    public static ItemStack vidro(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Texto.item(" "));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
