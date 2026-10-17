package br.rankupsupremo.menu;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.mina.GerenciadorMinas;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.picareta.GerenciadorPicareta.Melhoria;
import br.rankupsupremo.picareta.GerenciadorPicareta.Tipo;
import br.rankupsupremo.rank.Rank;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Menus do servidor: ranks, minas e melhorias de picareta.
 */
public final class Menus {

    private static final int[] SLOTS_INTERNOS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final RankupSupremo plugin;

    public Menus(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private static ItemStack borda() {
        return Itens.vidro(Material.BLACK_STAINED_GLASS_PANE);
    }

    private static int[] slots(int quantidade) {
        if (quantidade <= SLOTS_INTERNOS.length) {
            return SLOTS_INTERNOS;
        }
        int[] todos = new int[54];
        for (int i = 0; i < 54; i++) {
            todos[i] = i;
        }
        return todos;
    }

    // ------------------------------------------------------------------ RANKS

    public void ranks(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        List<Rank> lista = plugin.ranks().todos();
        Menu menu = new Menu(6, Texto.cor("<dark_gray>Ranks <gray>(Prestígio " + d.prestigio() + ")"));
        Rank atual = plugin.ranks().obter(d.rank());
        Rank proximo = plugin.ranks().proximo(atual);
        double saldo = plugin.economia().saldo(p);
        int[] s = slots(lista.size());
        for (int i = 0; i < lista.size() && i < s.length; i++) {
            Rank r = lista.get(i);
            double custo = plugin.ranks().custo(r, d.prestigio());
            List<String> lore = new ArrayList<>();
            Mina mina = minaDoRank(r);
            lore.add("<gray>Custo: <green>$" + Numeros.abreviar(custo));
            if (r.blocosNecessarios() > 0) {
                lore.add("<gray>Requisito: <gold>" + Numeros.abreviar(r.blocosNecessarios()) + " blocos minerados");
            }
            if (mina != null) {
                lore.add("<gray>Mina: " + mina.nome());
            }
            lore.add("");
            boolean brilho = false;
            if (r.indice() < atual.indice()) {
                lore.add("<green>✔ Rank concluído");
            } else if (r.indice() == atual.indice()) {
                lore.add("<yellow>➜ Seu rank atual");
                brilho = true;
            } else if (proximo != null && r.indice() == proximo.indice()) {
                double frac = custo <= 0 ? 1 : saldo / custo;
                lore.add("<gray>Progresso: " + Numeros.barra(frac, 10, "■", "<green>", "<dark_gray>")
                        + " <white>" + Numeros.porcentagem(frac) + "%");
                boolean blocosOk = d.blocos() >= r.blocosNecessarios();
                if (r.blocosNecessarios() > 0) {
                    lore.add("<gray>Blocos: " + (blocosOk ? "<green>" : "<red>") + Numeros.abreviar(d.blocos())
                            + "<dark_gray>/<white>" + Numeros.abreviar(r.blocosNecessarios()));
                }
                lore.add("");
                if (!blocosOk) {
                    lore.add("<red>Minere mais " + Numeros.abreviar(r.blocosNecessarios() - d.blocos()) + " blocos");
                } else {
                    lore.add(saldo >= custo ? "<green><bold>CLIQUE PARA UPAR!" : "<red>Faltam $" + Numeros.abreviar(custo - saldo));
                }
            } else {
                lore.add("<red>✖ Bloqueado");
            }
            ItemStack item = Itens.criar(r.icone(), r.tag() + " <white>" + r.nome(), lore, brilho);
            boolean ehProximo = proximo != null && r.indice() == proximo.indice();
            menu.item(s[i], item, ehProximo ? (pl, c) -> {
                pl.closeInventory();
                if (plugin.acoes().rankup(pl)) {
                    ranks(pl);
                }
            } : null);
        }
        if (lista.size() <= SLOTS_INTERNOS.length) {
            menu.item(49, Itens.criar(Material.NETHER_STAR, "<light_purple><bold>Prestígio " + d.prestigio(), List.of(
                    "<gray>Bônus de venda atual: <green>+"
                            + Numeros.multiplicador(d.prestigio() * plugin.ranks().bonusVendaPrestigio() * 100) + "%",
                    "",
                    "<gray>Chegue ao rank " + plugin.ranks().ultimo().tag(),
                    "<gray>e use <white>/prestigio <gray>para renascer!"), d.prestigio() > 0));
            menu.preencherVazios(borda());
        }
        menu.abrir(p);
    }

    private Mina minaDoRank(Rank r) {
        for (Mina m : plugin.minas().todas()) {
            if (m.rankId().equals(r.id())) {
                return m;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ MINAS

    public void minas(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        List<Mina> normais = plugin.minas().normais();
        List<Mina> especiais = plugin.minas().especiais();
        Menu menu = new Menu(6, Texto.cor("<dark_gray>Minas"));
        boolean cabe = normais.size() <= 21 && especiais.size() <= 7;
        if (cabe) {
            // Linhas 1 a 3: minas dos ranks | linha 4: minas especiais (VIP, PvP, Prestígio)
            int[] sn = centralizar(normais.size(), 1, 3);
            for (int i = 0; i < normais.size(); i++) {
                menu.item(sn[i], iconeMina(p, d, normais.get(i)), irPara(normais.get(i)));
            }
            int[] se = centralizar(especiais.size(), 4, 1);
            for (int i = 0; i < especiais.size(); i++) {
                menu.item(se[i], iconeMina(p, d, especiais.get(i)), irPara(especiais.get(i)));
            }
        } else {
            List<Mina> lista = plugin.minas().todas();
            int[] s = slots(lista.size());
            for (int i = 0; i < lista.size() && i < s.length; i++) {
                menu.item(s[i], iconeMina(p, d, lista.get(i)), irPara(lista.get(i)));
            }
        }
        if (cabe || plugin.minas().todas().size() <= SLOTS_INTERNOS.length) {
            Mina melhor = plugin.minas().melhorMina(p, d);
            if (melhor != null) {
                menu.item(49, Itens.criar(Material.ENDER_PEARL, "<aqua><bold>Ir para a melhor mina",
                        List.of("<gray>Sua melhor mina: " + melhor.nome(), "", "<green>Clique para ir!"), true), (pl, c) -> {
                    pl.closeInventory();
                    plugin.acoes().teleportarMina(pl, melhor);
                });
            }
            menu.preencherVazios(borda());
        }
        menu.abrir(p);
    }

    /** Slots das colunas 1 a 7 a partir da linha indicada; a última linha fica centralizada. */
    private static int[] centralizar(int quantidade, int linha, int linhas) {
        int[] r = new int[quantidade];
        for (int i = 0; i < quantidade; i++) {
            int l = Math.min(i / 7, linhas - 1);
            int naLinha = Math.min(7, quantidade - l * 7);
            int inicio = 1 + (7 - naLinha) / 2;
            r[i] = (linha + l) * 9 + inicio + (i % 7);
        }
        return r;
    }

    private BiConsumer<Player, ClickType> irPara(Mina m) {
        return (pl, c) -> {
            pl.closeInventory();
            plugin.acoes().teleportarMina(pl, m);
        };
    }

    private ItemStack iconeMina(Player p, DadosJogador d, Mina m) {
        GerenciadorMinas.Bloqueio bloqueio = plugin.minas().verificarAcesso(p, d, m);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Acesso: " + plugin.minas().requisito(m));
        switch (m.tipo()) {
            case VIP -> lore.add("<gray>Mina exclusiva, com menos gente!");
            case PVP -> {
                lore.add("<red>⚔ PvP LIGADO! <gray>Quem morrer aqui perde");
                lore.add("<gray>os itens (a picareta fica com você).");
            }
            case PRESTIGIO -> lore.add("<gray>Só para quem já prestigiou.");
            default -> {
            }
        }
        lore.add("");
        if (m.dropsRank() > 0) {
            Mina fonte = plugin.minas().fonteDeDrops(m, d);
            lore.add("<white>Drops: <gray>minérios da " + fonte.nome());
            lore.add("<dark_gray>(sempre melhores que os do seu rank)");
        } else {
            lore.add("<white>Blocos:");
            for (int b = 0; b < m.blocos().size(); b++) {
                Material mat = m.blocos().get(b);
                double chance = m.peso(b) / m.pesoTotal();
                lore.add(" <dark_gray>▪ <gray>" + nomeBloco(mat) + " <dark_gray>(" + Numeros.porcentagem(chance)
                        + "%) <green>$" + Numeros.abreviar(plugin.vendas().preco(mat)));
            }
        }
        if (m.multiplicadorTokens() != 1.0) {
            lore.add("<white>Tokens: <light_purple>x" + Numeros.multiplicador(m.multiplicadorTokens()));
        }
        lore.add("");
        lore.add("<gray>Restante: <white>" + Numeros.porcentagem(m.restante()) + "%");
        lore.add("<gray>Próximo reset: <yellow>"
                + Numeros.tempo(Math.max(0, (m.proximoReset() - System.currentTimeMillis()) / 1000)));
        lore.add("");
        if (bloqueio == null) {
            lore.add("<green><bold>CLIQUE PARA IR!");
            return Itens.criar(m.icone(), m.nome(), lore, m.especial());
        }
        lore.add("<red>✖ " + Texto.substituir(plugin.mensagens().bruta(bloqueio.chave()), bloqueio.pares()));
        return Itens.criar(Material.BARRIER, m.nome() + " <red>(bloqueada)", lore);
    }

    /** Nome legível em português para os blocos mais comuns. */
    public static String nomeBloco(Material m) {
        return switch (m) {
            case STONE -> "Pedra";
            case COBBLESTONE -> "Pedregulho";
            case ANDESITE -> "Andesito";
            case DIORITE -> "Diorito";
            case GRANITE -> "Granito";
            case TUFF -> "Tufo";
            case DEEPSLATE -> "Ardósia";
            case COBBLED_DEEPSLATE -> "Pedregulho de Ardósia";
            case COAL_ORE -> "Minério de Carvão";
            case DEEPSLATE_COAL_ORE -> "Carvão de Ardósia";
            case COPPER_ORE -> "Minério de Cobre";
            case DEEPSLATE_COPPER_ORE -> "Cobre de Ardósia";
            case IRON_ORE -> "Minério de Ferro";
            case DEEPSLATE_IRON_ORE -> "Ferro de Ardósia";
            case LAPIS_ORE -> "Minério de Lápis";
            case DEEPSLATE_LAPIS_ORE -> "Lápis de Ardósia";
            case REDSTONE_ORE -> "Minério de Redstone";
            case DEEPSLATE_REDSTONE_ORE -> "Redstone de Ardósia";
            case GOLD_ORE -> "Minério de Ouro";
            case DEEPSLATE_GOLD_ORE -> "Ouro de Ardósia";
            case NETHER_GOLD_ORE -> "Ouro do Nether";
            case NETHER_QUARTZ_ORE -> "Minério de Quartzo";
            case DIAMOND_ORE -> "Minério de Diamante";
            case DEEPSLATE_DIAMOND_ORE -> "Diamante de Ardósia";
            case EMERALD_ORE -> "Minério de Esmeralda";
            case DEEPSLATE_EMERALD_ORE -> "Esmeralda de Ardósia";
            case COAL_BLOCK -> "Bloco de Carvão";
            case COPPER_BLOCK -> "Bloco de Cobre";
            case IRON_BLOCK -> "Bloco de Ferro";
            case LAPIS_BLOCK -> "Bloco de Lápis";
            case REDSTONE_BLOCK -> "Bloco de Redstone";
            case GOLD_BLOCK -> "Bloco de Ouro";
            case AMETHYST_BLOCK -> "Bloco de Ametista";
            case QUARTZ_BLOCK -> "Bloco de Quartzo";
            case DIAMOND_BLOCK -> "Bloco de Diamante";
            case EMERALD_BLOCK -> "Bloco de Esmeralda";
            case ANCIENT_DEBRIS -> "Detritos Ancestrais";
            case NETHERITE_BLOCK -> "Bloco de Netherita";
            case OBSIDIAN -> "Obsidiana";
            default -> {
                String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(n.charAt(0)) + n.substring(1);
            }
        };
    }

    // ------------------------------------------------------------------ MUNDOS

    public void mundos(Player p) {
        int rankup = 0;
        int sobrevivencia = 0;
        for (Player outro : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.sobrevivencia().ehReinoRpg(outro.getWorld())) {
                sobrevivencia++;
            } else if (plugin.ehMundoRankup(outro.getWorld())) {
                rankup++;
            }
        }
        Menu menu = new Menu(3, Texto.cor("<dark_gray>Escolha um mundo"));
        menu.item(11, Itens.criar(Material.DIAMOND_PICKAXE, "<gradient:#FFD700:#FF8C00><bold>RANKUP</bold></gradient>", List.of(
                "<gray>Minere, venda, suba de rank",
                "<gray>e conquiste prestígios!",
                "",
                "<gray>Jogadores: <green>" + rankup,
                "",
                "<green><bold>CLIQUE PARA IR!"), plugin.ehMundoRankup(p.getWorld())), (pl, c) -> {
            pl.closeInventory();
            plugin.acoes().teleportarSpawn(pl, true);
        });
        boolean disponivel = plugin.sobrevivencia().mundo() != null;
        menu.item(15, Itens.criar(Material.GRASS_BLOCK, "<gradient:#56AB2F:#A8E063><bold>SMP RPG</bold></gradient>", List.of(
                "<gray>Survival com RPG: 5 classes, níveis,",
                "<gray>habilidades, monstros com nível, itens raros,",
                "<gray>a capital Vila Aurora, vilas, castelos",
                "<gray>e masmorras com chefes!",
                "<gray>Terrenos protegidos e inventário próprio.",
                "",
                "<gray>Jogadores: <green>" + sobrevivencia,
                "",
                disponivel ? "<green><bold>CLIQUE PARA IR!" : "<red>Indisponível no momento"),
                plugin.sobrevivencia().ehReinoRpg(p.getWorld())), (pl, c) -> {
            pl.closeInventory();
            plugin.sobrevivencia().teleportar(pl);
        });
        if (plugin.lobby() != null && plugin.lobby().ativo()) {
            menu.item(13, Itens.criar(Material.BEACON, "<gradient:#FFD700:#FFFFFF><bold>LOBBY</bold></gradient>", List.of(
                    "<gray>Voltar para a ilha inicial",
                    "<gray>(os portais levam aos modos)",
                    "",
                    "<yellow>/lobby"), plugin.lobby().ehLobby(p.getWorld())), (pl, c) -> {
                pl.closeInventory();
                plugin.lobby().teleportar(pl);
            });
        }
        menu.preencherVazios(borda());
        menu.abrir(p);
    }

    // ------------------------------------------------------------------ PICARETA

    public void picareta(Player p) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (!GerenciadorPicareta.ehPicareta(mao)) {
            if (!plugin.picareta().temPicareta(p)) {
                plugin.acoes().darPicareta(p, true);
            } else {
                plugin.mensagens().enviar(p, "picareta-segure");
            }
            return;
        }
        GerenciadorPicareta gp = plugin.picareta();
        DadosJogador d = plugin.jogadores().obter(p);
        Menu menu = new Menu(6, Texto.cor("<dark_gray>Melhorar Picareta"));
        menu.item(4, mao.clone());

        List<Tipo> comDinheiro = new ArrayList<>();
        List<Tipo> comTokens = new ArrayList<>();
        for (Tipo t : Tipo.values()) {
            (gp.melhoria(t).moeda() == GerenciadorPicareta.Moeda.TOKENS ? comTokens : comDinheiro).add(t);
        }
        menu.item(9, Itens.criar(Material.GOLD_NUGGET, "<gold><bold>Melhorias com dinheiro",
                List.of("<gray>Pagas com o dinheiro do Rankup.")));
        int[] sd = centralizar(comDinheiro.size(), 1, 1);
        for (int i = 0; i < comDinheiro.size(); i++) {
            botaoMelhoria(menu, sd[i], p, d, mao, comDinheiro.get(i));
        }
        menu.item(27, Itens.criar(Material.AMETHYST_SHARD, "<light_purple><bold>Melhorias com tokens",
                List.of("<gray>Tokens caem minerando nas minas.", "<gray>Veja os seus com <white>/tokens")));
        int[] st = centralizar(comTokens.size(), 3, 1);
        for (int i = 0; i < comTokens.size(); i++) {
            botaoMelhoria(menu, st[i], p, d, mao, comTokens.get(i));
        }
        menu.item(48, Itens.criar(Material.GOLD_INGOT, "<gold>Seu saldo: <green>$"
                + Numeros.abreviar(plugin.economia().saldo(p)), List.of("<gray>Venda minérios com <white>/vender")));
        menu.item(50, Itens.criar(Material.AMETHYST_CLUSTER, "<light_purple>Seus tokens: <white>"
                + Numeros.inteiro(d.tokens()), List.of("<gray>Chance de ganhar tokens: <white>"
                + Numeros.multiplicador(plugin.getConfig().getDouble("tokens.chance-por-bloco", 2.0)
                * gp.multiplicadorSorteToken(gp.nivel(mao, Tipo.SORTE_TOKEN)) * (1 + d.boosterTokensAtivo())) + "% <gray>por bloco")));
        menu.preencherVazios(borda());
        menu.abrir(p);
        Acoes.som(p, "block.enchantment_table.use", 0.6f, 1.2f);
    }

    private void botaoMelhoria(Menu menu, int slot, Player p, DadosJogador d, ItemStack mao, Tipo t) {
        GerenciadorPicareta gp = plugin.picareta();
        Melhoria m = gp.melhoria(t);
        int nivel = gp.nivel(mao, t);
        boolean tokens = m.moeda() == GerenciadorPicareta.Moeda.TOKENS;
        List<String> lore = new ArrayList<>();
        if (!m.descricao().isEmpty()) {
            lore.add("<gray>" + m.descricao());
        }
        switch (t) {
            case EXPLOSAO -> lore.add("<gray>Chance atual: <white>" + Numeros.multiplicador(nivel * gp.chanceExplosaoPorNivel()) + "%");
            case FORTUNA -> lore.add("<gray>Média por bloco: <white>x" + Numeros.multiplicador(1 + nivel / 4.0));
            case LASER -> lore.add("<gray>Chance atual: <white>" + Numeros.multiplicador(nivel * gp.chanceLaserPorNivel()) + "%");
            case SORTE_TOKEN -> lore.add("<gray>Tokens: <white>+" + Numeros.multiplicador(nivel * gp.bonusSorteTokenPorNivel() * 100) + "%");
            case VELOCIDADE -> lore.add("<gray>Efeito atual: <white>" + (nivel > 0 ? "Velocidade " + nivel : "nenhum"));
            default -> {
            }
        }
        lore.add("");
        lore.add("<gray>Nível: <white>" + nivel + "<dark_gray>/" + m.maximo());
        if (m.maximo() > 1) {
            lore.add("<gray>Progresso: " + Numeros.barra((double) nivel / m.maximo(), 10, "■",
                    tokens ? "<light_purple>" : "<aqua>", "<dark_gray>"));
        }
        lore.add("");
        if (nivel >= m.maximo()) {
            lore.add("<gold><bold>NÍVEL MÁXIMO!");
        } else {
            double custo = m.custo(nivel);
            boolean pode = tokens ? d.tokens() >= custo : plugin.economia().saldo(p) >= custo;
            lore.add("<gray>Custo: " + (tokens ? "<light_purple>" + Numeros.inteiro((long) custo) + " tokens"
                    : "<green>$" + Numeros.abreviar(custo)));
            lore.add("");
            lore.add((pode ? "<yellow>" : "<red>") + "Clique esquerdo: <white>+1 nível");
            if (m.maximo() > 1) {
                lore.add((pode ? "<yellow>" : "<red>") + "Clique direito: <white>+10 níveis");
            }
        }
        ItemStack icone = Itens.criar(m.icone(), m.cor() + "<bold>" + m.nome() + " <gray>" + nivel, lore, nivel > 0);
        menu.item(slot, icone, (pl, clique) -> {
            int qtd = (clique == ClickType.RIGHT || clique == ClickType.SHIFT_RIGHT) ? 10 : 1;
            plugin.acoes().comprarMelhoria(pl, t, qtd);
            if (GerenciadorPicareta.ehPicareta(pl.getInventory().getItemInMainHand()) && plugin.ehMundoRankup(pl.getWorld())) {
                picareta(pl);
            }
        });
    }
}
