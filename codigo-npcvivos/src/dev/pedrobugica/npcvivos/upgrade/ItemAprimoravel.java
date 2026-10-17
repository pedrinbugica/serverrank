package dev.pedrobugica.npcvivos.upgrade;

import dev.pedrobugica.npcvivos.util.Texto;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lê e grava o nível de um item DENTRO dele (PersistentDataContainer): o nível vai junto com o item,
 * não some ao renomear e não dá para falsificar escrevendo na lore. Também desenha o nome ("Espada de
 * Ferro +7", com a cor do nível) e as linhas de bônus na lore. Cria as pedras e o pergaminho de proteção.
 */
public final class ItemAprimoravel {

    public enum Tipo {
        ARMA, ARMADURA
    }

    private final UpgradeService upgrades;
    private final NamespacedKey chaveNivel;
    private final NamespacedKey chaveRaridade;
    private final NamespacedKey chaveHabilidade;
    private final NamespacedKey chaveNomeBase;
    private final NamespacedKey chaveLinhas;
    private final NamespacedKey chavePedra;
    private final NamespacedKey chavePergaminho;

    ItemAprimoravel(Plugin plugin, UpgradeService upgrades) {
        this.upgrades = upgrades;
        this.chaveNivel = new NamespacedKey(plugin, "nivel");
        this.chaveRaridade = new NamespacedKey(plugin, "raridade");
        this.chaveHabilidade = new NamespacedKey(plugin, "habilidade");
        this.chaveNomeBase = new NamespacedKey(plugin, "nome-base");
        this.chaveLinhas = new NamespacedKey(plugin, "linhas-lore");
        this.chavePedra = new NamespacedKey(plugin, "pedra");
        this.chavePergaminho = new NamespacedKey(plugin, "pergaminho");
    }

    public NamespacedKey chaveNivel() {
        return chaveNivel;
    }

    public NamespacedKey chaveHabilidade() {
        return chaveHabilidade;
    }

    // ------------------------------------------------------------------ LEITURA

    /** Arma ou armadura (o que pode ser aprimorado); null para o resto. */
    public static Tipo tipo(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String n = item.getType().name();
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR") || n.equals("TRIDENT") || n.equals("MACE")
                || n.equals("BOW") || n.equals("CROSSBOW")) {
            return Tipo.ARMA;
        }
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) {
            return Tipo.ARMADURA;
        }
        return null;
    }

    public int nivel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        return item.getPersistentDataContainer().getOrDefault(chaveNivel, PersistentDataType.INTEGER, 0);
    }

    public Raridade raridade(ItemStack item) {
        Raridade r = Raridade.buscar(item.getPersistentDataContainer().get(chaveRaridade, PersistentDataType.STRING));
        return r != null ? r : upgrades.raridadePorMaterial(item.getType());
    }

    public Habilidade habilidade(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return Habilidade.buscar(item.getPersistentDataContainer().get(chaveHabilidade, PersistentDataType.STRING));
    }

    /** Nome do item SEM o "+7", em MiniMessage (para as mensagens). */
    public String nomeBase(ItemStack item) {
        String json = item.getPersistentDataContainer().get(chaveNomeBase, PersistentDataType.STRING);
        if (json != null) {
            return MiniMessage.miniMessage().serialize(GsonComponentSerializer.gson().deserialize(json));
        }
        if (item.hasItemMeta() && item.getItemMeta().hasCustomName() && nivel(item) == 0) {
            return MiniMessage.miniMessage().serialize(item.getItemMeta().customName());
        }
        return "<lang:" + item.getType().translationKey() + ">";
    }

    // ------------------------------------------------------------------ ESCRITA

    /** Muda o nível (0 tira o aprimoramento) e redesenha nome e lore. Sorteia a habilidade ao chegar no +9. */
    public Habilidade definir(ItemStack item, int novoNivel) {
        Tipo tipo = tipo(item);
        Habilidade[] nova = new Habilidade[1];
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int antes = pdc.getOrDefault(chaveNivel, PersistentDataType.INTEGER, 0);
            if (antes == 0 && novoNivel > 0 && meta.hasCustomName() && !pdc.has(chaveNomeBase)) {
                pdc.set(chaveNomeBase, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(meta.customName()));
            }
            if (tipo == Tipo.ARMA && novoNivel >= 9 && !pdc.has(chaveHabilidade)) {
                Habilidade[] todas = Habilidade.values();
                nova[0] = todas[ThreadLocalRandom.current().nextInt(todas.length)];
                pdc.set(chaveHabilidade, PersistentDataType.STRING, nova[0].name());
            }
            if (novoNivel <= 0) {
                pdc.remove(chaveNivel);
            } else {
                pdc.set(chaveNivel, PersistentDataType.INTEGER, novoNivel);
            }
            desenhar(meta, item.getType(), tipo);
        });
        return nova[0];
    }

    public void definirRaridade(ItemStack item, Raridade r) {
        item.editMeta(meta -> {
            if (r == null) {
                meta.getPersistentDataContainer().remove(chaveRaridade);
            } else {
                meta.getPersistentDataContainer().set(chaveRaridade, PersistentDataType.STRING, r.name());
            }
            desenhar(meta, item.getType(), tipo(item));
        });
    }

    /** Novo nome dado na bigorna (null ou vazio = volta ao nome normal do item). */
    public void renomear(ItemStack item, String texto) {
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (texto == null || texto.isBlank()) {
                pdc.remove(chaveNomeBase);
            } else {
                pdc.set(chaveNomeBase, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(Component.text(texto)));
            }
            desenhar(meta, item.getType(), tipo(item));
        });
    }

    /** Redesenha nome e lore (ex.: depois de virar netherite na mesa de ferraria, a raridade muda). */
    public void redesenhar(ItemStack item) {
        if (nivel(item) > 0) {
            item.editMeta(meta -> desenhar(meta, item.getType(), tipo(item)));
        }
    }

    private void desenhar(ItemMeta meta, Material material, Tipo tipo) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int n = pdc.getOrDefault(chaveNivel, PersistentDataType.INTEGER, 0);
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        int antigas = pdc.getOrDefault(chaveLinhas, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < antigas && !lore.isEmpty(); i++) {
            lore.remove(lore.size() - 1);
        }
        String json = pdc.get(chaveNomeBase, PersistentDataType.STRING);
        Component base = json == null ? null : GsonComponentSerializer.gson().deserialize(json);
        if (n <= 0) {
            // Só desfaz o que o upgrade desenhou antes; um item nunca aprimorado fica como está
            if (pdc.has(chaveLinhas)) {
                meta.customName(base);
                pdc.remove(chaveNomeBase);
                pdc.remove(chaveLinhas);
                meta.setEnchantmentGlintOverride(null);
                meta.lore(lore.isEmpty() ? null : lore);
            }
            return;
        }
        NamedTextColor cor = corDoNivel(n);
        Component nome = (base != null ? base : Component.translatable(material.translationKey())).colorIfAbsent(cor)
                .append(Component.text(" +" + n, cor, TextDecoration.BOLD))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        meta.customName(nome);

        Raridade r = Raridade.buscar(pdc.get(chaveRaridade, PersistentDataType.STRING));
        if (r == null) {
            r = upgrades.raridadePorMaterial(material);
        }
        List<String> linhas = new ArrayList<>();
        if (!lore.isEmpty()) {
            linhas.add("");
        }
        String tag = "<" + NamedTextColor.NAMES.key(cor) + ">";
        linhas.add(tag + "✦ Aprimorado +" + n + " <dark_gray>(" + r.formatado() + "<dark_gray>, até +" + upgrades.maximo(r) + ")");
        String bonus = "+" + Texto.dinheiro(upgrades.bonusTotal(n)) + "%";
        linhas.add(tipo == Tipo.ARMADURA ? "<gray>Defesa: <green>" + bonus : "<gray>Dano: <green>" + bonus);
        Habilidade h = Habilidade.buscar(pdc.get(chaveHabilidade, PersistentDataType.STRING));
        if (tipo == Tipo.ARMA && h != null) {
            linhas.add(n >= 9 ? "<gold>✦ " + h.nome() + ": <white>" + upgrades.descricaoHabilidade(h, n)
                    : "<dark_gray>✦ " + h.nome() + " (dormindo até o +9)");
        }
        lore.addAll(Texto.lore(linhas));
        meta.lore(lore);
        pdc.set(chaveLinhas, PersistentDataType.INTEGER, linhas.size());
        meta.setEnchantmentGlintOverride(n >= 7 ? Boolean.TRUE : null);
    }

    public static NamedTextColor corDoNivel(int n) {
        if (n >= 9) {
            return NamedTextColor.GOLD;
        }
        if (n >= 7) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        if (n >= 4) {
            return NamedTextColor.AQUA;
        }
        return NamedTextColor.GREEN;
    }

    // ------------------------------------------------------------------ PEDRAS E PERGAMINHO

    public ItemStack criarPedra(Pedra p, int quantidade) {
        ItemStack it = new ItemStack(p.material(), Math.max(1, quantidade));
        it.editMeta(meta -> {
            meta.customName(Texto.item(p.nomeCompleto()));
            meta.lore(Texto.lore(List.of("<gray>O ferreiro usa esta pedra para", "<gray>aprimorar armas e armaduras.",
                    "", "<dark_gray>Leve a um NPC Ferreiro.")));
            meta.getPersistentDataContainer().set(chavePedra, PersistentDataType.STRING, p.name());
            if (p.ordinal() >= Pedra.EPICA.ordinal()) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return it;
    }

    public ItemStack criarPergaminho(int quantidade) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, quantidade));
        it.editMeta(meta -> {
            meta.customName(Texto.item("<gold>Pergaminho de Proteção"));
            meta.lore(Texto.lore(List.of("<gray>Se um aprimoramento falhar, o", "<gray>pergaminho se desfaz e o item",
                    "<gray>não perde nível.", "", "<dark_gray>Ligue na forja do ferreiro.")));
            meta.getPersistentDataContainer().set(chavePergaminho, PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(true);
        });
        return it;
    }

    public Pedra pedra(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return Pedra.buscar(item.getPersistentDataContainer().get(chavePedra, PersistentDataType.STRING));
    }

    public boolean ehPergaminho(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getPersistentDataContainer().has(chavePergaminho);
    }

    /** Pedra ou pergaminho (não podem virar receita, nem ir para bigorna/ferraria). */
    public boolean especial(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        PersistentDataContainerView v = item.getPersistentDataContainer();
        return v.has(chavePedra) || v.has(chavePergaminho);
    }
}
