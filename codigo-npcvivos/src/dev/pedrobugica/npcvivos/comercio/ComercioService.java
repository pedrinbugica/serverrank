package dev.pedrobugica.npcvivos.comercio;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.dados.Repositorio;
import dev.pedrobugica.npcvivos.menu.Menu;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Comércio com NPCs: comprar (com estoque que acaba e repõe), vender (com limite diário) e consertar (ferreiro).
 * Toda transação vai para plugins/NpcVivos/logs/transacoes-AAAA-MM.log.
 */
public final class ComercioService {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final NpcVivosPlugin plugin;
    private final Object travaLog = new Object();
    private BukkitTask tarefaReposicao;

    public ComercioService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private Repositorio dados() {
        return plugin.repositorio();
    }

    // ------------------------------------------------------------------ ESTOQUE

    /** Estoque atual (unidades de lote). Itens sem estoque máximo são infinitos (-1). */
    public int estoque(NpcVivo npc, ItemLoja item) {
        if (item.estoqueMax() <= 0) {
            return -1;
        }
        Double atual = dados().estoque(npc.id(), item.chave());
        if (atual == null) {
            dados().setEstoque(npc.id(), item.chave(), item.estoqueMax());
            return item.estoqueMax();
        }
        return (int) Math.floor(Math.min(item.estoqueMax(), atual));
    }

    public void iniciarReposicao() {
        if (tarefaReposicao != null) {
            tarefaReposicao.cancel();
        }
        // A cada minuto cada item repõe (reposição por hora / 60) unidades, até o máximo.
        tarefaReposicao = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (NpcVivo npc : plugin.npcs().todos()) {
                for (ItemLoja i : npc.compra()) {
                    if (i.estoqueMax() > 0 && i.repoePorHora() > 0 && !plugin.mercado().rotaCortada(npc, i)) {
                        Double atual = dados().estoque(npc.id(), i.chave());
                        double novo = Math.min(i.estoqueMax(), (atual == null ? i.estoqueMax() : atual) + i.repoePorHora() / 60.0);
                        dados().setEstoque(npc.id(), i.chave(), novo);
                    }
                }
            }
        }, 20L * 60, 20L * 60);
    }

    public void pararReposicao() {
        if (tarefaReposicao != null) {
            tarefaReposicao.cancel();
            tarefaReposicao = null;
        }
    }

    // ------------------------------------------------------------------ MENUS

    /** O jogador ainda está perto do NPC? (evita comprar de longe com o menu aberto) */
    public boolean perto(Player p, NpcVivo npc) {
        Location l = npc.local();
        double max = plugin.getConfig().getDouble("npc.distancia-interacao", 8);
        if (l == null || !l.getWorld().equals(p.getWorld()) || l.distanceSquared(p.getLocation()) > max * max) {
            p.closeInventory();
            plugin.mensagens().enviar(p, "longe-do-npc", "npc", npc.nome());
            return false;
        }
        return true;
    }

    public void abrir(Player p, NpcVivo npc, boolean comprar) {
        String recusa = plugin.faccoes().recusaNegociar(p, npc);
        if (recusa != null) {
            plugin.mensagens().enviar(p, recusa, "npc", npc.nome());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (!plugin.economia().ativa()) {
            plugin.mensagens().enviar(p, "economia-indisponivel");
            return;
        }
        if (comprar && npc.compra().isEmpty() && !npc.venda().isEmpty()) {
            comprar = false;
        }
        final boolean abaComprar = comprar;
        Menu m = new Menu(6, Texto.cor(Texto.substituir(plugin.mensagens().bruta(abaComprar ? "loja-titulo-comprar" : "loja-titulo-vender"),
                "npc", Texto.semCor(npc.nome()))));
        List<ItemLoja> itens = abaComprar ? npc.compra() : npc.venda();
        for (int i = 0; i < itens.size() && i < SLOTS.length; i++) {
            ItemLoja it = itens.get(i);
            m.item(SLOTS[i], abaComprar ? iconeCompra(p, npc, it) : iconeVenda(p, npc, it), (pl, c) -> {
                if (!perto(pl, npc)) {
                    return;
                }
                if (abaComprar) {
                    comprar(pl, npc, it, c.isShiftClick() ? 8 : 1);
                } else {
                    vender(pl, npc, it, c.isShiftClick());
                }
                abrir(pl, npc, abaComprar);
            });
        }
        if (!npc.compra().isEmpty()) {
            m.item(45, Menu.criar(Material.EMERALD, abaComprar ? "<green><bold>» Comprar «" : "<green>Comprar",
                    List.of("<gray>Itens que " + Texto.semCor(npc.nome()) + " vende.")), (pl, c) -> abrir(pl, npc, true));
        }
        if (!npc.venda().isEmpty()) {
            m.item(46, Menu.criar(Material.GOLD_INGOT, !abaComprar ? "<gold><bold>» Vender «" : "<gold>Vender",
                    List.of("<gray>Itens que " + Texto.semCor(npc.nome()) + " compra de você.")), (pl, c) -> abrir(pl, npc, false));
        }
        m.item(49, Menu.criar(Material.SUNFLOWER, "<yellow>Seu dinheiro: <green>$" + Texto.dinheiro(plugin.economia().saldo(p)),
                List.of("<gray>É o dinheiro do mundo onde você está.")), null);
        if (npc.tem(Papel.FERREIRO)) {
            m.item(51, iconeConserto(p), (pl, c) -> {
                if (perto(pl, npc)) {
                    consertar(pl, npc);
                    abrir(pl, npc, abaComprar);
                }
            });
        }
        if (npc.tem(Papel.FERREIRO) && plugin.upgrades().ativo()) {
            m.item(52, Menu.criar(Material.SMITHING_TABLE, "<light_purple><bold>Aprimorar o item da mão", List.of(
                    "<gray>Sobe o nível da arma ou armadura", "<gray>que está na sua mão (+1 a +10).", "",
                    "<yellow>Clique para abrir a forja")), (pl, c) -> {
                if (perto(pl, npc)) {
                    plugin.upgrades().abrirForja(pl, npc);
                }
            });
        }
        m.item(53, Menu.criar(Material.BARRIER, "<red>Fechar", List.of()), (pl, c) -> pl.closeInventory());
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    private ItemStack iconeCompra(Player p, NpcVivo npc, ItemLoja it) {
        ItemStack icone = it.lote();
        icone.setAmount(Math.max(1, Math.min(icone.getMaxStackSize(), it.quantidade())));
        int estoque = estoque(npc, it);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Quantidade: <white>" + it.quantidade());
        double preco = precoCompra(p, npc, it);
        lore.add("<gray>Preço: <green>$" + Texto.dinheiro(preco) + (preco < it.precoBase() - 0.001 ? " <dark_gray><st>$" + Texto.dinheiro(it.precoBase()) + "</st>" : ""));
        lore.addAll(plugin.mercado().motivos(npc, it, true));
        if (plugin.faccoes().multiplicadorCompra(p, npc) > 1.001) {
            lore.add("<red>(mais caro: sua reputação com eles é baixa)");
        }
        lore.add("<gray>Estoque: " + (estoque < 0 ? "<white>à vontade" : (estoque == 0 ? "<red>esgotado" : "<white>" + estoque))
                + (estoque >= 0 && it.repoePorHora() > 0 ? " <dark_gray>(repõe " + Texto.dinheiro(it.repoePorHora()) + "/hora)" : ""));
        lore.add("");
        lore.add("<yellow>Clique: <white>comprar 1");
        lore.add("<yellow>Shift + clique: <white>comprar 8");
        adicionarLore(icone, lore);
        return icone;
    }

    private ItemStack iconeVenda(Player p, NpcVivo npc, ItemLoja it) {
        ItemStack icone = it.lote();
        icone.setAmount(Math.max(1, Math.min(icone.getMaxStackSize(), it.quantidade())));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Paga: <green>$" + Texto.dinheiro(precoVendaLote(p, npc, it)) + " <gray>por " + it.quantidade());
        lore.addAll(plugin.mercado().motivos(npc, it, false));
        lore.add("<gray>Você tem: <white>" + contar(p.getInventory(), it));
        if (it.limiteDiario() > 0) {
            int resta = Math.max(0, it.limiteDiario() - dados().vendidoHoje(p.getUniqueId(), npc.id(), it.chave()));
            lore.add("<gray>Ainda compra hoje: " + (resta == 0 ? "<red>0 (volte amanhã)" : "<white>" + resta));
        }
        lore.add("");
        lore.add("<yellow>Clique: <white>vender " + it.quantidade());
        lore.add("<yellow>Shift + clique: <white>vender tudo");
        adicionarLore(icone, lore);
        return icone;
    }

    /**
     * Preço do lote para este jogador: base x mercado (procura/oferta, guerra, rota cortada, vitória, promoção)
     * x reputação com a facção do NPC (e desconto de membro).
     */
    public double precoCompra(Player p, NpcVivo npc, ItemLoja it) {
        return Math.round(it.precoBase() * plugin.mercado().fatorCompra(npc, it) * plugin.faccoes().multiplicadorCompra(p, npc) * 100) / 100.0;
    }

    /** Quanto o NPC paga por um lote a este jogador. */
    public double precoVendaLote(Player p, NpcVivo npc, ItemLoja it) {
        return Math.round(it.precoBase() * plugin.mercado().fatorVenda(npc, it) * plugin.faccoes().multiplicadorVenda(p, npc) * 100) / 100.0;
    }

    private static void adicionarLore(ItemStack icone, List<String> linhas) {
        icone.editMeta(meta -> {
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(net.kyori.adventure.text.Component.empty());
            }
            lore.addAll(Texto.lore(linhas));
            meta.lore(lore);
        });
    }

    private ItemStack iconeConserto(Player p) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        double custo = custoConserto(mao);
        return Menu.criar(Material.ANVIL, "<aqua><bold>Consertar o item da mão", custo > 0
                ? List.of("<gray>Custo: <green>$" + Texto.dinheiro(custo), "", "<yellow>Clique para consertar")
                : List.of("<gray>Segure um item gasto (ferramenta,", "<gray>arma ou armadura) e clique."));
    }

    // ------------------------------------------------------------------ COMPRAR / VENDER / CONSERTAR

    private void comprar(Player p, NpcVivo npc, ItemLoja it, int lotes) {
        int estoque = estoque(npc, it);
        if (estoque == 0) {
            plugin.mensagens().enviar(p, "sem-estoque");
            som(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        if (estoque > 0) {
            lotes = Math.min(lotes, estoque);
        }
        double precoLote = precoCompra(p, npc, it);
        double preco = precoLote * lotes;
        double saldo = plugin.economia().saldo(p);
        if (saldo < preco) {
            // compra o máximo que der
            int pode = (int) Math.floor(saldo / Math.max(0.0001, precoLote));
            if (pode <= 0) {
                plugin.mensagens().enviar(p, "sem-dinheiro", "preco", Texto.dinheiro(precoLote));
                som(p, Sound.ENTITY_VILLAGER_NO);
                return;
            }
            lotes = Math.min(lotes, pode);
            preco = precoLote * lotes;
        }
        int unidades = it.quantidade() * lotes;
        if (espaco(p.getInventory(), it.lote()) < unidades) {
            plugin.mensagens().enviar(p, "sem-espaco");
            som(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        if (!plugin.economia().sacar(p, preco)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return;
        }
        if (estoque > 0) {
            dados().setEstoque(npc.id(), it.chave(), estoque - lotes);
        }
        int restante = unidades;
        while (restante > 0) {
            ItemStack lote = it.lote();
            int q = Math.min(lote.getMaxStackSize(), restante);
            lote.setAmount(q);
            restante -= q;
            for (ItemStack sobra : p.getInventory().addItem(lote).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            }
        }
        plugin.mensagens().enviar(p, "comprou", "quantidade", String.valueOf(unidades), "item", nomeItem(it),
                "preco", Texto.dinheiro(preco), "npc", npc.nome());
        som(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        registrar(p, npc, "COMPRA", it.material().name(), unidades, preco);
        plugin.mercado().registrarCompra(npc, it, lotes);
    }

    private void vender(Player p, NpcVivo npc, ItemLoja it, boolean tudo) {
        PlayerInventory inv = p.getInventory();
        int tem = contar(inv, it);
        int unidades = tudo ? (tem / it.quantidade()) * it.quantidade() : it.quantidade();
        if (it.limiteDiario() > 0) {
            int resta = Math.max(0, it.limiteDiario() - dados().vendidoHoje(p.getUniqueId(), npc.id(), it.chave()));
            if (resta < it.quantidade()) {
                plugin.mensagens().enviar(p, "limite-diario", "npc", npc.nome());
                som(p, Sound.ENTITY_VILLAGER_NO);
                return;
            }
            unidades = Math.min(unidades, (resta / it.quantidade()) * it.quantidade());
        }
        if (unidades <= 0 || tem < unidades) {
            plugin.mensagens().enviar(p, "sem-itens", "quantidade", String.valueOf(it.quantidade()), "item", nomeItem(it));
            som(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        double valor = Math.round(it.precoUnidade() * plugin.mercado().fatorVenda(npc, it) * plugin.faccoes().multiplicadorVenda(p, npc)
                * unidades * 100) / 100.0;
        ItemStack[] conteudo = inv.getStorageContents();
        int remover = unidades;
        for (int i = 0; i < conteudo.length && remover > 0; i++) {
            ItemStack s = conteudo[i];
            if (s != null && combina(s, it)) {
                int q = Math.min(remover, s.getAmount());
                remover -= q;
                if (q == s.getAmount()) {
                    conteudo[i] = null;
                } else {
                    ItemStack resto = s.clone();
                    resto.setAmount(s.getAmount() - q);
                    conteudo[i] = resto;
                }
            }
        }
        if (!plugin.economia().depositar(p, valor)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return; // nada foi tirado do inventário ainda
        }
        inv.setStorageContents(conteudo);
        dados().adicionarVendido(p.getUniqueId(), npc.id(), it.chave(), unidades);
        plugin.mensagens().enviar(p, "vendeu", "quantidade", String.valueOf(unidades), "item", nomeItem(it),
                "valor", Texto.dinheiro(valor), "npc", npc.nome());
        som(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        registrar(p, npc, "VENDA", it.material().name(), unidades, valor);
        plugin.mercado().registrarVenda(npc, it, unidades / (double) it.quantidade());
        plugin.faccoes().reputacaoPorVenda(p, npc);
    }

    private double custoConserto(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof Damageable d) || !d.hasDamage() || item.getType().getMaxDurability() <= 0) {
            return 0;
        }
        double porPonto = plugin.getConfig().getDouble("ferreiro.custo-por-ponto", 0.5);
        return Math.max(plugin.getConfig().getDouble("ferreiro.custo-minimo", 5), Math.ceil(d.getDamage() * porPonto));
    }

    private void consertar(Player p, NpcVivo npc) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        double custo = custoConserto(mao);
        if (custo <= 0) {
            plugin.mensagens().enviar(p, "nada-para-consertar");
            som(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        if (plugin.economia().saldo(p) < custo) {
            plugin.mensagens().enviar(p, "sem-dinheiro", "preco", Texto.dinheiro(custo));
            som(p, Sound.ENTITY_VILLAGER_NO);
            return;
        }
        if (!plugin.economia().sacar(p, custo)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return;
        }
        mao.editMeta(Damageable.class, d -> d.setDamage(0));
        p.getInventory().setItemInMainHand(mao);
        plugin.mensagens().enviar(p, "consertou", "preco", Texto.dinheiro(custo), "npc", npc.nome());
        som(p, Sound.BLOCK_ANVIL_USE);
        registrar(p, npc, "CONSERTO", mao.getType().name(), 1, custo);
    }

    // ------------------------------------------------------------------ AUXILIARES

    private static boolean combina(ItemStack s, ItemLoja it) {
        if (it.simples()) {
            return s.getType() == it.material() && !s.hasItemMeta();
        }
        return s.isSimilar(it.lote());
    }

    private static int contar(PlayerInventory inv, ItemLoja it) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && combina(s, it)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static int espaco(PlayerInventory inv, ItemStack modelo) {
        int max = modelo.getMaxStackSize();
        int livre = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                livre += max;
            } else if (s.isSimilar(modelo)) {
                livre += Math.max(0, max - s.getAmount());
            }
        }
        return livre;
    }

    /** Nome do item para mensagens: o nome personalizado ou o nome do jogo (no idioma do jogador). */
    public static String nomeItem(ItemLoja it) {
        if (it.item().hasItemMeta() && it.item().getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(it.item().getItemMeta().displayName());
        }
        return "<lang:" + it.material().translationKey() + ">";
    }

    private static void som(Player p, Sound s) {
        p.playSound(p.getLocation(), s, 0.8f, 1.2f);
    }

    /** Grava uma linha no log de transações (compras, vendas, consertos e missões). */
    public void registrar(Player p, NpcVivo npc, String tipo, String item, int quantidade, double valor) {
        String linha = String.format(java.util.Locale.ROOT, "%s | %-8s | %s (%s) | %s | %s x%d | $%.2f | %s%n",
                LocalDateTime.now().format(DATA), tipo, p.getName(), p.getUniqueId(), npc.id(), item,
                quantidade, valor, p.getWorld().getName());
        File pasta = new File(plugin.getDataFolder(), "logs");
        File arquivo = new File(pasta, "transacoes-" + LocalDateTime.now().format(MES) + ".log");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (travaLog) {
                try {
                    Files.createDirectories(pasta.toPath());
                    Files.writeString(arquivo.toPath(), linha, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o log de transações", ex);
                }
            }
        });
    }
}
