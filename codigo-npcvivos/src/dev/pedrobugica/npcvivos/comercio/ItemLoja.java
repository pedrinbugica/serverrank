package dev.pedrobugica.npcvivos.comercio;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Um item da loja de um NPC.
 * - Na lista "compra" (o jogador compra do NPC): preço por lote, estoque máximo (0 = infinito) e reposição por hora.
 * - Na lista "venda" (o jogador vende ao NPC): preço por lote e limite diário de unidades por jogador (0 = sem limite).
 *
 * @param chave        identificador estável dentro do NPC (ex.: "compra.0") para estoque e limites
 * @param item         item exato (com nome/encantamentos) ou só o material
 * @param quantidade   tamanho do lote
 * @param importadoDe  facção de onde o item vem (fase 7): em guerra com ela, a rota fecha, o estoque não volta
 *                     e o preço sobe
 */
public record ItemLoja(String chave, ItemStack item, int quantidade, double precoBase, int estoqueMax,
                       double repoePorHora, int limiteDiario, String importadoDe) {

    public ItemLoja(String chave, ItemStack item, int quantidade, double precoBase, int estoqueMax,
                    double repoePorHora, int limiteDiario) {
        this(chave, item, quantidade, precoBase, estoqueMax, repoePorHora, limiteDiario, null);
    }

    public Material material() {
        return item.getType();
    }

    /** Item sem nome/encantamentos personalizados (pode ser comparado só pelo material). */
    public boolean simples() {
        return !item.hasItemMeta();
    }

    /** Preço de uma unidade. */
    public double precoUnidade() {
        return precoBase / Math.max(1, quantidade);
    }

    public ItemStack lote() {
        ItemStack c = item.clone();
        c.setAmount(1);
        return c;
    }
}
