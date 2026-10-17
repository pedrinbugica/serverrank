package br.rankupsupremo.loja;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.menu.Menu;
import br.rankupsupremo.rank.Rank;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /loja: loja do servidor organizada por categorias. Cada mundo tem a sua (loja.yml),
 * porque o dinheiro do Rankup e o da Sobrevivência são separados.
 */
public final class GerenciadorLoja {

    public enum TipoItem {
        ITEM, BOOSTER_VENDA, BOOSTER_TOKENS
    }

    public record ItemLoja(String id, TipoItem tipo, Material material, String nome, List<String> descricao,
                           int quantidade, double compra, double venda, double bonus, int minutos,
                           double custoPercentual, double custoMinimo) {
    }

    public record Categoria(String id, String nome, List<String> descricao, Material icone, int slot,
                            boolean venderMinerios, List<ItemLoja> itens) {
    }

    public record Loja(String titulo, int linhas, List<Categoria> categorias) {
    }

    private static final int[] SLOTS_ITENS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final RankupSupremo plugin;
    private final Map<String, Loja> lojas = new LinkedHashMap<>();

    public GerenciadorLoja(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        lojas.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "loja.yml"));
        int itens = 0;
        for (String mundo : List.of("rankup", "sobrevivencia")) {
            ConfigurationSection s = yml.getConfigurationSection(mundo);
            if (s == null) {
                continue;
            }
            List<Categoria> cats = new ArrayList<>();
            ConfigurationSection cs = s.getConfigurationSection("categorias");
            if (cs != null) {
                for (String id : cs.getKeys(false)) {
                    ConfigurationSection c = cs.getConfigurationSection(id);
                    if (c == null) {
                        continue;
                    }
                    List<ItemLoja> lista = new ArrayList<>();
                    ConfigurationSection is = c.getConfigurationSection("itens");
                    if (is != null) {
                        for (String iid : is.getKeys(false)) {
                            ItemLoja it = lerItem(mundo + "." + id + "." + iid, iid, is.getConfigurationSection(iid));
                            if (it != null) {
                                lista.add(it);
                            }
                        }
                    }
                    itens += lista.size();
                    cats.add(new Categoria(id, c.getString("nome", id), c.getStringList("descricao"),
                            material(c.getString("icone"), Material.CHEST), c.getInt("slot", -1),
                            "vender-minerios".equalsIgnoreCase(c.getString("tipo", "")), List.copyOf(lista)));
                }
            }
            lojas.put(mundo, new Loja(s.getString("titulo", "<dark_gray>Loja"),
                    Math.max(3, Math.min(6, s.getInt("linhas", 4))), List.copyOf(cats)));
        }
        plugin.getLogger().info("Loja carregada: " + lojas.size() + " mundo(s), " + itens + " itens.");
    }

    private ItemLoja lerItem(String caminho, String id, ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        TipoItem tipo = switch (s.getString("tipo", "item").toLowerCase(Locale.ROOT)) {
            case "booster-venda" -> TipoItem.BOOSTER_VENDA;
            case "booster-tokens" -> TipoItem.BOOSTER_TOKENS;
            default -> TipoItem.ITEM;
        };
        Material m = material(s.getString(tipo == TipoItem.ITEM ? "material" : "icone"), null);
        if (m == null) {
            plugin.getLogger().warning("loja.yml: item inválido em '" + caminho + "', ignorado.");
            return null;
        }
        int qtd = Math.max(1, Math.min(64 * 36, s.getInt("quantidade", 1)));
        double compra = Math.max(0, s.getDouble("compra", 0));
        double venda = Math.max(0, s.getDouble("venda", 0));
        if (compra > 0 && venda >= compra) {
            plugin.getLogger().warning("loja.yml: em '" + caminho + "' a venda é maior que a compra (dinheiro infinito!). Venda desativada.");
            venda = 0;
        }
        String nome = s.getString("nome", "<white><lang:" + m.translationKey() + ">");
        return new ItemLoja(id, tipo, m, nome, s.getStringList("descricao"), qtd, compra, venda,
                Math.max(0, s.getDouble("bonus", 0.5)), Math.max(1, s.getInt("minutos", 15)),
                Math.max(0, s.getDouble("custo-percentual", 0)), Math.max(0, s.getDouble("custo-minimo", 0)));
    }

    private static Material material(String nome, Material padrao) {
        Material m = nome == null ? null : Material.matchMaterial(nome);
        return m != null && m.isItem() && !m.isAir() ? m : padrao;
    }

    private Loja lojaDo(Player p) {
        if (plugin.ehMundoRankup(p.getWorld())) {
            return lojas.get("rankup");
        }
        if (plugin.sobrevivencia().ehReinoRpg(p.getWorld())) {
            return lojas.get("sobrevivencia");
        }
        return null;
    }

    // ------------------------------------------------------------------ MENUS

    public void abrir(Player p) {
        Loja loja = lojaDo(p);
        if (loja == null || loja.categorias().isEmpty()) {
            plugin.mensagens().enviar(p, "loja-indisponivel");
            return;
        }
        if (!plugin.economia().ativa()) {
            plugin.mensagens().enviar(p, "economia-indisponivel");
            return;
        }
        Menu menu = new Menu(loja.linhas(), Texto.cor(loja.titulo()));
        int tamanho = loja.linhas() * 9;
        int automatico = 10;
        for (Categoria c : loja.categorias()) {
            int slot = c.slot();
            if (slot < 0 || slot >= tamanho - 9) {
                slot = automatico++;
            }
            List<String> lore = new ArrayList<>(c.descricao());
            if (!lore.isEmpty()) {
                lore.add("");
            }
            if (c.venderMinerios()) {
                lore.add("<green><bold>CLIQUE PARA VENDER!");
                menu.item(slot, Itens.criar(c.icone(), c.nome(), lore), (pl, clique) -> {
                    pl.closeInventory();
                    if (plugin.acoes().noRankup(pl)) {
                        plugin.acoes().vender(pl);
                    }
                });
            } else {
                lore.add("<gray>" + c.itens().size() + " itens");
                lore.add("<yellow>Clique para abrir!");
                menu.item(slot, Itens.criar(c.icone(), c.nome(), lore), (pl, clique) -> categoria(pl, c));
            }
        }
        menu.item(tamanho - 5, saldo(p));
        menu.preencherVazios(Itens.vidro(Material.GRAY_STAINED_GLASS_PANE));
        menu.abrir(p);
        Acoes.som(p, "block.chest.open", 0.6f, 1.2f);
    }

    private ItemStack saldo(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Dinheiro neste mundo: <green>$" + Numeros.abreviar(plugin.economia().saldo(p)));
        if (plugin.ehMundoRankup(p.getWorld())) {
            lore.add("<gray>Tokens: <light_purple>" + Numeros.inteiro(d.tokens()));
            lore.add("");
            lore.add("<gray>Booster de venda: " + textoBooster(d.boosterAtivo(), d.boosterAte()));
            lore.add("<gray>Booster de tokens: " + textoBooster(d.boosterTokensAtivo(), d.boosterTokensAte()));
        }
        return Itens.criar(Material.GOLD_INGOT, "<gold><bold>Sua carteira", lore);
    }

    private static String textoBooster(double bonus, long ate) {
        if (bonus <= 0) {
            return "<dark_gray>nenhum";
        }
        long seg = Math.max(0, (ate - System.currentTimeMillis()) / 1000);
        return "<green>+" + Numeros.multiplicador(bonus * 100) + "% <gray>(" + Numeros.tempo(seg) + ")";
    }

    private void categoria(Player p, Categoria c) {
        if (lojaDo(p) == null) {
            p.closeInventory();
            return;
        }
        Menu menu = new Menu(6, Texto.cor("<dark_gray>Loja » " + Texto.plano(Texto.cor(c.nome()))));
        for (int i = 0; i < c.itens().size() && i < SLOTS_ITENS.length; i++) {
            ItemLoja it = c.itens().get(i);
            menu.item(SLOTS_ITENS[i], icone(p, it), (pl, clique) -> {
                clicar(pl, it, clique);
                if (lojaDo(pl) != null) {
                    categoria(pl, c);
                }
            });
        }
        menu.item(48, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (pl, clique) -> abrir(pl));
        menu.item(50, saldo(p));
        menu.preencherVazios(Itens.vidro(Material.BLACK_STAINED_GLASS_PANE));
        menu.abrir(p);
    }

    private ItemStack icone(Player p, ItemLoja it) {
        List<String> lore = new ArrayList<>(it.descricao());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        if (it.tipo() == TipoItem.ITEM) {
            lore.add("<gray>Quantidade: <white>" + it.quantidade());
            if (it.compra() > 0) {
                lore.add("<gray>Comprar: <green>$" + Numeros.abreviar(it.compra()));
            }
            if (it.venda() > 0) {
                lore.add("<gray>Vender: <gold>$" + Numeros.abreviar(it.venda()));
            }
            lore.add("");
            if (it.compra() > 0) {
                lore.add("<yellow>Esquerdo: <white>comprar " + it.quantidade());
                lore.add("<yellow>Shift + esquerdo: <white>comprar " + it.quantidade() * 4);
            }
            if (it.venda() > 0) {
                lore.add("<yellow>Direito: <white>vender " + it.quantidade());
                lore.add("<yellow>Shift + direito: <white>vender tudo");
            }
        } else {
            boolean tokens = it.tipo() == TipoItem.BOOSTER_TOKENS;
            lore.add("<gray>Bônus: <green>+" + Numeros.multiplicador(it.bonus() * 100) + "% "
                    + (tokens ? "<gray>de chance de tokens" : "<gray>no valor das vendas"));
            lore.add("<gray>Duração: <white>" + it.minutos() + " minutos");
            lore.add("<gray>Preço: <green>$" + Numeros.abreviar(precoBooster(p, it)));
            lore.add("");
            lore.add("<dark_gray>O preço acompanha o custo do seu próximo rank.");
            lore.add("<dark_gray>Comprar o mesmo booster de novo soma o tempo.");
            lore.add("");
            lore.add("<green><bold>CLIQUE PARA ATIVAR!");
        }
        ItemStack icone = Itens.criar(it.material(), it.nome(), lore, it.tipo() != TipoItem.ITEM);
        if (it.tipo() == TipoItem.ITEM) {
            icone.setAmount(Math.max(1, Math.min(it.material().getMaxStackSize(), it.quantidade())));
        }
        return icone;
    }

    // ------------------------------------------------------------------ COMPRA E VENDA

    private void clicar(Player p, ItemLoja it, ClickType clique) {
        if (it.tipo() != TipoItem.ITEM) {
            comprarBooster(p, it);
            return;
        }
        switch (clique) {
            case LEFT -> comprar(p, it, 1);
            case SHIFT_LEFT -> comprar(p, it, 4);
            case RIGHT -> vender(p, it, false);
            case SHIFT_RIGHT -> vender(p, it, true);
            default -> {
            }
        }
    }

    private void comprar(Player p, ItemLoja it, int vezes) {
        if (it.compra() <= 0) {
            return;
        }
        int unidades = it.quantidade() * vezes;
        double preco = it.compra() * vezes;
        if (espaco(p.getInventory(), it.material()) < unidades) {
            plugin.mensagens().enviar(p, "loja-sem-espaco");
            Acoes.som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        double saldo = plugin.economia().saldo(p);
        if (saldo < preco) {
            plugin.mensagens().enviar(p, "loja-sem-dinheiro", "preco", Numeros.abreviar(preco), "falta", Numeros.abreviar(preco - saldo));
            Acoes.som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        if (!plugin.economia().sacar(p, preco)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return;
        }
        int restante = unidades;
        int max = Math.max(1, it.material().getMaxStackSize());
        while (restante > 0) {
            int q = Math.min(max, restante);
            restante -= q;
            for (ItemStack sobra : p.getInventory().addItem(new ItemStack(it.material(), q)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            }
        }
        plugin.mensagens().enviar(p, "loja-comprou", "quantidade", String.valueOf(unidades), "item", it.nome(),
                "preco", Numeros.abreviar(preco));
        Acoes.som(p, "entity.experience_orb.pickup", 0.8f, 1.3f);
    }

    private void vender(Player p, ItemLoja it, boolean tudo) {
        if (it.venda() <= 0) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] conteudo = inv.getStorageContents();
        int tem = 0;
        for (ItemStack s : conteudo) {
            if (s != null && s.getType() == it.material() && !s.hasItemMeta()) {
                tem += s.getAmount();
            }
        }
        int vender = tudo ? tem : it.quantidade();
        if (tem <= 0 || tem < vender) {
            plugin.mensagens().enviar(p, "loja-sem-itens", "quantidade", String.valueOf(it.quantidade()), "item", it.nome());
            Acoes.som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        double valor = it.venda() / it.quantidade() * vender;
        int remover = vender;
        for (int i = 0; i < conteudo.length && remover > 0; i++) {
            ItemStack s = conteudo[i];
            if (s != null && s.getType() == it.material() && !s.hasItemMeta()) {
                int q = Math.min(remover, s.getAmount());
                remover -= q;
                if (q == s.getAmount()) {
                    conteudo[i] = null;
                } else {
                    ItemStack resto = s.clone(); // não mexe no inventário antes de pagar
                    resto.setAmount(s.getAmount() - q);
                    conteudo[i] = resto;
                }
            }
        }
        if (!plugin.economia().depositar(p, valor)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return; // nada foi removido ainda
        }
        inv.setStorageContents(conteudo);
        plugin.mensagens().enviar(p, "loja-vendeu", "quantidade", String.valueOf(vender), "item", it.nome(),
                "valor", Numeros.abreviar(valor));
        Acoes.som(p, "entity.experience_orb.pickup", 0.8f, 1.6f);
    }

    /** Quantos itens deste material ainda cabem no inventário. */
    private static int espaco(PlayerInventory inv, Material m) {
        int max = m.getMaxStackSize();
        ItemStack modelo = new ItemStack(m);
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

    // ------------------------------------------------------------------ BOOSTERS

    private double precoBooster(Player p, ItemLoja it) {
        DadosJogador d = plugin.jogadores().obter(p);
        Rank atual = plugin.ranks().obter(d.rank());
        Rank proximo = plugin.ranks().proximo(atual);
        double base = plugin.ranks().custo(proximo != null ? proximo : atual, d.prestigio());
        return Math.max(it.custoMinimo(), Math.round(base * it.custoPercentual()));
    }

    private void comprarBooster(Player p, ItemLoja it) {
        if (!plugin.acoes().noRankup(p)) {
            p.closeInventory();
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        boolean tokens = it.tipo() == TipoItem.BOOSTER_TOKENS;
        double ativo = tokens ? d.boosterTokensAtivo() : d.boosterAtivo();
        long ate = tokens ? d.boosterTokensAte() : d.boosterAte();
        if (ativo > it.bonus()) {
            plugin.mensagens().enviar(p, "booster-ja-ativo", "bonus", Numeros.multiplicador(ativo * 100));
            Acoes.som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        double preco = precoBooster(p, it);
        double saldo = plugin.economia().saldo(p);
        if (saldo < preco) {
            plugin.mensagens().enviar(p, "loja-sem-dinheiro", "preco", Numeros.abreviar(preco), "falta", Numeros.abreviar(preco - saldo));
            Acoes.som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        if (!plugin.economia().sacar(p, preco)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return;
        }
        long agora = System.currentTimeMillis();
        long inicio = ativo == it.bonus() ? Math.max(agora, ate) : agora;
        long fim = inicio + it.minutos() * 60_000L;
        if (tokens) {
            d.setBoosterTokens(it.bonus(), fim);
        } else {
            d.setBooster(it.bonus(), fim);
        }
        plugin.mensagens().enviar(p, tokens ? "booster-tokens-ativado" : "booster-venda-ativado",
                "bonus", Numeros.multiplicador(it.bonus() * 100), "tempo", Numeros.tempo((fim - agora) / 1000),
                "preco", Numeros.abreviar(preco));
        Acoes.som(p, "block.beacon.activate", 0.8f, 1.4f);
        plugin.visual().atualizarJogador(p);
    }
}
