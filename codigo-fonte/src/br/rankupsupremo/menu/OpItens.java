package br.rankupsupremo.menu;

import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.picareta.GerenciadorPicareta.Melhoria;
import br.rankupsupremo.picareta.GerenciadorPicareta.Tipo;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Texto;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Página "Picaretas e Itens" do /menuop, com o editor de picareta.
 */
final class OpItens {

    private static final Material[] CONSTRUCAO = {Material.STONE_BRICKS, Material.OAK_PLANKS, Material.SPRUCE_LOG, Material.GLASS,
            Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE, Material.SMOOTH_STONE, Material.DEEPSLATE_TILES, Material.SEA_LANTERN};
    private static final Material[] OVOS = {Material.COW_SPAWN_EGG, Material.PIG_SPAWN_EGG, Material.SHEEP_SPAWN_EGG,
            Material.CHICKEN_SPAWN_EGG, Material.HORSE_SPAWN_EGG, Material.WOLF_SPAWN_EGG, Material.CAT_SPAWN_EGG,
            Material.VILLAGER_SPAWN_EGG, Material.ALLAY_SPAWN_EGG};

    private final MenuOp op;

    OpItens(MenuOp op) {
        this.op = op;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <aqua>Itens <dark_gray>» <black>" + alvo.getName()));
        MenuOp.Pagina pg = this::abrir;
        GerenciadorPicareta gp = op.plugin.picareta();

        // Picaretas
        op.acao(m, 10, Itens.criar(Material.DIAMOND_PICKAXE, "<gradient:#00E5FF:#7C4DFF><bold>Picareta Suprema MAX</bold></gradient>",
                List.of("<gray>Todas as melhorias no máximo normal."), true), alvo, false, "Picareta MAX entregue",
                (p, c) -> MenuOp.dar(alvo, gp.criarEspecial(Material.DIAMOND_PICKAXE,
                        "<gradient:#00E5FF:#7C4DFF><bold>Picareta Suprema MAX</bold></gradient>", gp.niveisMaximos())), pg);
        op.acao(m, 11, Itens.criar(Material.NETHERITE_PICKAXE, "<gradient:#FF0000:#FFD700><bold>☠ Picareta Divina ☠</bold></gradient>", List.of(
                "<gray>Eficiência 50, Fortuna 300, Explosão 66,", "<gray>Laser 100, Sorte de Token 100, Velocidade 5,",
                "<gray>Visão Noturna e Fundição.", "<red>Muito acima do limite dos jogadores."), true), alvo, false,
                "Picareta Divina entregue", (p, c) -> MenuOp.dar(alvo, gp.criarEspecial(Material.NETHERITE_PICKAXE,
                        "<gradient:#FF0000:#FFD700><bold>☠ Picareta Divina ☠</bold></gradient>", niveisDivina())), pg);
        op.acao(m, 12, Itens.criar(Material.ANVIL, "<yellow><bold>Maximizar picareta na mão",
                List.of("<gray>Coloca todas as melhorias no máximo", "<gray>na picareta que ele está segurando.")), alvo, false,
                "Picareta da mão maximizada", (p, c) -> {
                    ItemStack mao = alvo.getInventory().getItemInMainHand();
                    if (!GerenciadorPicareta.ehPicareta(mao)) {
                        op.plugin.mensagens().enviar(p, "menuop-sem-picareta", "jogador", alvo.getName());
                        return;
                    }
                    for (Tipo t : Tipo.values()) {
                        gp.definirNivel(mao, t, Math.max(gp.nivel(mao, t), gp.melhoria(t).maximo()));
                    }
                    alvo.getInventory().setItemInMainHand(mao);
                }, pg);
        op.acao(m, 13, Itens.criar(Material.IRON_PICKAXE, "<aqua><bold>Picareta Suprema nova",
                List.of("<gray>Dá uma picareta do servidor zerada.")), alvo, false, "Picareta entregue",
                (p, c) -> op.plugin.acoes().darPicareta(alvo, true), pg);
        m.item(14, Itens.criar(Material.SMITHING_TABLE, "<gold><bold>Editor de picareta",
                List.of("<gray>Escolha o nível de cada melhoria", "<gray>da picareta que ele está segurando.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        editor(p, alvo);
                    }
                });

        // Kits e itens OP
        op.acao(m, 19, Itens.criar(Material.NETHERITE_CHESTPLATE, "<dark_purple><bold>Kit OP completo", List.of(
                "<gray>Armadura Proteção X, espada Afiação X,", "<gray>arco, élitro, totens, maçãs e ferramentas."), true),
                alvo, false, "Kit OP entregue", (p, c) -> kitOp(alvo), pg);
        op.acao(m, 20, Itens.criar(Material.NETHERITE_SWORD, "<red><bold>Espada OP",
                List.of("<gray>Afiação X, Saque V, Aspecto Flamejante II..."), true), alvo, false, "Espada entregue",
                (p, c) -> MenuOp.dar(alvo, espada()), pg);
        op.acao(m, 21, Itens.criar(Material.BOW, "<gold><bold>Arco OP", List.of("<gray>Força X, Infinidade, Chama, Impacto."), true),
                alvo, false, "Arco entregue", (p, c) -> {
                    ItemStack arco = encantado(Material.BOW, Enchantment.POWER, 10);
                    arco.editMeta(meta -> {
                        meta.addEnchant(Enchantment.INFINITY, 1, true);
                        meta.addEnchant(Enchantment.FLAME, 1, true);
                        meta.addEnchant(Enchantment.PUNCH, 2, true);
                    });
                    MenuOp.dar(alvo, arco);
                    MenuOp.dar(alvo, new ItemStack(Material.ARROW));
                }, pg);
        op.acao(m, 22, Itens.criar(Material.NETHERITE_HELMET, "<dark_aqua><bold>Armadura OP",
                List.of("<gray>4 peças de netherita Proteção X."), true), alvo, false, "Armadura entregue", (p, c) -> armadura(alvo), pg);
        op.acao(m, 23, Itens.criar(Material.ELYTRA, "<white><bold>Élitro + foguetes",
                List.of("<gray>Élitro inquebrável e 64 foguetes.")), alvo, false, "Élitro entregue", (p, c) -> {
                    MenuOp.dar(alvo, encantado(Material.ELYTRA, Enchantment.UNBREAKING, 10));
                    MenuOp.dar(alvo, new ItemStack(Material.FIREWORK_ROCKET, 64));
                }, pg);
        op.acao(m, 24, Itens.criar(Material.TOTEM_OF_UNDYING, "<yellow><bold>5 Totens da imortalidade", List.of()), alvo, false,
                "Totens entregues", (p, c) -> {
                    for (int i = 0; i < 5; i++) {
                        MenuOp.dar(alvo, new ItemStack(Material.TOTEM_OF_UNDYING));
                    }
                }, pg);
        op.acao(m, 25, Itens.criar(Material.ENCHANTED_GOLDEN_APPLE, "<gold><bold>Comida OP",
                List.of("<gray>64 maçãs encantadas e", "<gray>64 cenouras douradas.")), alvo, false, "Comida entregue", (p, c) -> {
                    MenuOp.dar(alvo, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64));
                    MenuOp.dar(alvo, new ItemStack(Material.GOLDEN_CARROT, 64));
                }, pg);

        // Item na mão do jogador
        op.acao(m, 28, Itens.criar(Material.ENCHANTING_TABLE, "<light_purple><bold>Encantar tudo (item na mão)",
                List.of("<gray>Coloca todos os encantamentos que", "<gray>servem no item, no nível 10.", "<gray>(menos maldições)"), true),
                alvo, false, "Item encantado", (p, c) -> {
                    ItemStack mao = alvo.getInventory().getItemInMainHand();
                    if (mao.getType().isAir()) {
                        op.plugin.mensagens().enviar(p, "menuop-mao-vazia", "jogador", alvo.getName());
                        return;
                    }
                    encantarTudo(mao);
                    alvo.getInventory().setItemInMainHand(mao);
                }, pg);
        op.acao(m, 29, Itens.criar(Material.OBSIDIAN, "<dark_purple><bold>Inquebrável (item na mão)",
                List.of("<gray>Liga/desliga: o item nunca quebra.")), alvo, false, "Item alterado", (p, c) -> {
                    ItemStack mao = alvo.getInventory().getItemInMainHand();
                    if (!mao.getType().isAir()) {
                        mao.editMeta(meta -> meta.setUnbreakable(!meta.isUnbreakable()));
                        alvo.getInventory().setItemInMainHand(mao);
                    }
                }, pg);
        op.acao(m, 30, Itens.criar(Material.ANVIL, "<gray><bold>Consertar tudo",
                List.of("<gray>Conserta todos os itens", "<gray>do inventário dele.")), alvo, false, "Itens consertados", (p, c) -> {
                    for (ItemStack it : alvo.getInventory().getContents()) {
                        if (it != null && it.getItemMeta() instanceof Damageable) {
                            it.editMeta(Damageable.class, dm -> dm.setDamage(0));
                        }
                    }
                }, pg);
        op.acao(m, 31, Itens.criar(Material.SLIME_BALL, "<green><bold>Copiar item da mão",
                List.of("<gray>Dá para VOCÊ uma cópia do", "<gray>item que ele está segurando.")), alvo, false, "Item copiado", (p, c) -> {
                    ItemStack mao = alvo.getInventory().getItemInMainHand();
                    if (!mao.getType().isAir()) {
                        MenuOp.dar(p, mao.clone());
                    }
                }, pg);
        m.item(32, Itens.criar(Material.NAME_TAG, "<yellow><bold>Renomear item da mão",
                List.of("<gray>Digite o nome no chat.", "<gray>Aceita cores: &a &l &#FFAA00")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual o novo nome do item?", pg, texto -> {
                            ItemStack mao = alvo.getInventory().getItemInMainHand();
                            if (mao.getType().isAir()) {
                                op.plugin.mensagens().enviar(p, "menuop-mao-vazia", "jogador", alvo.getName());
                                return;
                            }
                            mao.editMeta(meta -> meta.displayName(Texto.item(texto)));
                            alvo.getInventory().setItemInMainHand(mao);
                        });
                    }
                });
        op.acao(m, 33, Itens.criar(Material.PLAYER_HEAD, "<gold><bold>Cabeça de " + alvo.getName(),
                List.of("<gray>Dá para você a cabeça dele.")), alvo, false, "Cabeça entregue", (p, c) -> {
                    ItemStack cabeca = new ItemStack(Material.PLAYER_HEAD);
                    cabeca.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(alvo));
                    MenuOp.dar(p, cabeca);
                }, pg);
        op.acao(m, 34, Itens.criar(Material.NETHERITE_AXE, "<dark_gray><bold>Ferramentas OP",
                List.of("<gray>Picareta, machado, pá e enxada", "<gray>de netherita Eficiência X."), true), alvo, false,
                "Ferramentas entregues", (p, c) -> {
                    ItemStack pic = encantado(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 10);
                    pic.editMeta(meta -> meta.addEnchant(Enchantment.FORTUNE, 5, true));
                    MenuOp.dar(alvo, pic);
                    MenuOp.dar(alvo, encantado(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 10));
                    MenuOp.dar(alvo, encantado(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 10));
                    MenuOp.dar(alvo, encantado(Material.NETHERITE_HOE, Enchantment.EFFICIENCY, 10));
                }, pg);

        // Pacotes
        op.acao(m, 37, Itens.criar(Material.BRICKS, "<gold><bold>Pacote de construção",
                List.of("<gray>64 de 9 blocos bonitos.")), alvo, false, "Blocos entregues", (p, c) -> {
                    for (Material b : CONSTRUCAO) {
                        MenuOp.dar(alvo, new ItemStack(b, 64));
                    }
                }, pg);
        op.acao(m, 38, Itens.criar(Material.WOLF_SPAWN_EGG, "<green><bold>Ovos de animais",
                List.of("<gray>Vaca, porco, ovelha, galinha, cavalo,", "<gray>lobo, gato, aldeão e allay.")), alvo, false,
                "Ovos entregues", (p, c) -> {
                    for (Material o : OVOS) {
                        MenuOp.dar(alvo, new ItemStack(o, 4));
                    }
                }, pg);
        op.acao(m, 39, Itens.criar(Material.PURPLE_SHULKER_BOX, "<light_purple><bold>Caixas de Shulker",
                List.of("<gray>6 shulkers coloridas.")), alvo, false, "Shulkers entregues", (p, c) -> {
                    for (Material s : List.of(Material.WHITE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
                            Material.LIME_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.PURPLE_SHULKER_BOX)) {
                        MenuOp.dar(alvo, new ItemStack(s));
                    }
                }, pg);
        op.acao(m, 40, Itens.criar(Material.EXPERIENCE_BOTTLE, "<green><bold>Frascos de XP",
                List.of("<gray>3 pacotes de 64.")), alvo, false, "Frascos entregues", (p, c) -> {
                    for (int i = 0; i < 3; i++) {
                        MenuOp.dar(alvo, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                    }
                }, pg);
        op.acao(m, 41, Itens.criar(Material.DIAMOND_BLOCK, "<aqua><bold>Blocos de minério",
                List.of("<gray>64 de diamante, esmeralda,", "<gray>ouro, ferro e netherita.")), alvo, false,
                "Blocos entregues", (p, c) -> {
                    for (Material b : List.of(Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.GOLD_BLOCK,
                            Material.IRON_BLOCK, Material.NETHERITE_BLOCK)) {
                        MenuOp.dar(alvo, new ItemStack(b, 64));
                    }
                }, pg);
        op.acao(m, 42, Itens.criar(Material.BEACON, "<white><bold>Sinalizador completo",
                List.of("<gray>Sinalizador + 164 blocos de ferro", "<gray>(pirâmide completa).")), alvo, false,
                "Sinalizador entregue", (p, c) -> {
                    MenuOp.dar(alvo, new ItemStack(Material.BEACON));
                    MenuOp.dar(alvo, new ItemStack(Material.IRON_BLOCK, 64));
                    MenuOp.dar(alvo, new ItemStack(Material.IRON_BLOCK, 64));
                    MenuOp.dar(alvo, new ItemStack(Material.IRON_BLOCK, 36));
                }, pg);
        op.acao(m, 43, Itens.criar(Material.ENCHANTED_BOOK, "<light_purple><bold>Livros encantados",
                List.of("<gray>Remendo, Inquebrável III, Eficiência V,", "<gray>Fortuna III, Afiação V, Proteção IV,",
                        "<gray>Saque III, Peso-Pena IV e Toque Suave.")), alvo, false, "Livros entregues", (p, c) -> {
                    livro(alvo, Enchantment.MENDING, 1);
                    livro(alvo, Enchantment.UNBREAKING, 3);
                    livro(alvo, Enchantment.EFFICIENCY, 5);
                    livro(alvo, Enchantment.FORTUNE, 3);
                    livro(alvo, Enchantment.SHARPNESS, 5);
                    livro(alvo, Enchantment.PROTECTION, 4);
                    livro(alvo, Enchantment.LOOTING, 3);
                    livro(alvo, Enchantment.FEATHER_FALLING, 4);
                    livro(alvo, Enchantment.SILK_TOUCH, 1);
                }, pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    // ------------------------------------------------------------------ EDITOR DE PICARETA

    private void editor(Player admin, Player alvo) {
        GerenciadorPicareta gp = op.plugin.picareta();
        ItemStack mao = alvo.getInventory().getItemInMainHand();
        if (!GerenciadorPicareta.ehPicareta(mao)) {
            op.plugin.mensagens().enviar(admin, "menuop-sem-picareta", "jogador", alvo.getName());
            return;
        }
        Menu m = new Menu(4, Texto.cor("<dark_red>Editor de picareta <dark_gray>» <black>" + alvo.getName()));
        m.item(4, mao.clone());
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 13};
        Tipo[] tipos = Tipo.values();
        for (int i = 0; i < tipos.length && i < slots.length; i++) {
            Tipo t = tipos[i];
            Melhoria mel = gp.melhoria(t);
            int nivel = gp.nivel(mao, t);
            m.item(slots[i], Itens.criar(mel.icone(), mel.cor() + "<bold>" + mel.nome() + " <white>" + nivel, List.of(
                    "<gray>Máximo normal: <white>" + mel.maximo(),
                    "",
                    "<yellow>Esquerdo: <white>+1 <gray>| <yellow>Direito: <white>-1",
                    "<yellow>Shift: <white>±10"), nivel > 0), (p, c) -> {
                        if (!op.podeUsar(p, alvo)) {
                            return;
                        }
                        ItemStack atual = alvo.getInventory().getItemInMainHand();
                        if (!GerenciadorPicareta.ehPicareta(atual)) {
                            op.plugin.mensagens().enviar(p, "menuop-sem-picareta", "jogador", alvo.getName());
                            p.closeInventory();
                            return;
                        }
                        int passo = c.isShiftClick() ? 10 : 1;
                        int limite = t == Tipo.EFICIENCIA ? 255 : 10_000;
                        int novo = gp.nivel(atual, t) + (c.isRightClick() ? -passo : passo);
                        gp.definirNivel(atual, t, Math.max(0, Math.min(limite, novo)));
                        alvo.getInventory().setItemInMainHand(atual);
                        op.plugin.efeitos().atualizar(alvo);
                        editor(p, alvo);
                    });
        }
        m.item(31, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (p, c) -> abrir(p, alvo));
        m.preencherVazios(Itens.vidro(Material.RED_STAINED_GLASS_PANE));
        m.abrir(admin);
    }

    // ------------------------------------------------------------------ AUXILIARES

    static Map<Tipo, Integer> niveisDivina() {
        Map<Tipo, Integer> n = new EnumMap<>(Tipo.class);
        n.put(Tipo.EFICIENCIA, 50);
        n.put(Tipo.FORTUNA, 300);
        n.put(Tipo.EXPLOSAO, 66);
        n.put(Tipo.LASER, 100);
        n.put(Tipo.SORTE_TOKEN, 100);
        n.put(Tipo.VELOCIDADE, 5);
        n.put(Tipo.VISAO_NOTURNA, 1);
        n.put(Tipo.FUNDICAO, 1);
        return n;
    }

    private static void encantarTudo(ItemStack item) {
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (e.isCursed() || e.equals(Enchantment.SILK_TOUCH) || e.equals(Enchantment.RIPTIDE) || !e.canEnchantItem(item)) {
                continue;
            }
            item.addUnsafeEnchantment(e, e.getMaxLevel() > 1 ? 10 : 1);
        }
    }

    private static void livro(Player alvo, Enchantment e, int nivel) {
        ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
        livro.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(e, nivel, true));
        MenuOp.dar(alvo, livro);
    }

    private static ItemStack espada() {
        ItemStack espada = encantado(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 10);
        espada.editMeta(meta -> {
            meta.addEnchant(Enchantment.LOOTING, 5, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE, 5, true);
        });
        return espada;
    }

    private static void armadura(Player alvo) {
        MenuOp.dar(alvo, encantado(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 10));
        MenuOp.dar(alvo, encantado(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 10));
        MenuOp.dar(alvo, encantado(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 10));
        ItemStack botas = encantado(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 10);
        botas.editMeta(meta -> meta.addEnchant(Enchantment.FEATHER_FALLING, 10, true));
        MenuOp.dar(alvo, botas);
    }

    private static void kitOp(Player alvo) {
        armadura(alvo);
        MenuOp.dar(alvo, espada());
        ItemStack arco = encantado(Material.BOW, Enchantment.POWER, 10);
        arco.editMeta(meta -> meta.addEnchant(Enchantment.INFINITY, 1, true));
        MenuOp.dar(alvo, arco);
        ItemStack picareta = encantado(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 10);
        picareta.editMeta(meta -> meta.addEnchant(Enchantment.FORTUNE, 5, true));
        MenuOp.dar(alvo, picareta);
        MenuOp.dar(alvo, encantado(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 10));
        MenuOp.dar(alvo, encantado(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 10));
        MenuOp.dar(alvo, encantado(Material.ELYTRA, Enchantment.UNBREAKING, 10));
        MenuOp.dar(alvo, new ItemStack(Material.ARROW, 1));
        MenuOp.dar(alvo, new ItemStack(Material.FIREWORK_ROCKET, 64));
        for (int i = 0; i < 3; i++) {
            MenuOp.dar(alvo, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }
        MenuOp.dar(alvo, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64));
        MenuOp.dar(alvo, new ItemStack(Material.COOKED_BEEF, 64));
    }

    private static ItemStack encantado(Material material, Enchantment encanto, int nivel) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.addEnchant(encanto, nivel, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
        });
        return item;
    }
}
