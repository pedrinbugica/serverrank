package br.rankupsupremo.menu;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * /menuop - painel do dono com TUDO que dá para fazer no servidor, separado em categorias:
 * jogador, moderação, cargos, economia e rankup, picaretas e itens, mundos e minas, servidor.
 */
public final class MenuOp implements CommandExecutor, TabCompleter {

    public static final String PERMISSAO = "rankupsupremo.menuop";

    /** Reabre a página atual depois de uma ação: (admin, alvo). */
    interface Pagina extends BiConsumer<Player, Player> {
    }

    final RankupSupremo plugin;
    final FerramentasOp ferramentas;
    private final OpJogador jogador;
    private final OpCargos cargos;
    private final OpEconomia economia;
    private final OpItens itens;
    private final OpMundos mundos;
    private final OpServidor servidor;
    private final OpRpg rpg;

    public MenuOp(RankupSupremo plugin, FerramentasOp ferramentas) {
        this.plugin = plugin;
        this.ferramentas = ferramentas;
        this.jogador = new OpJogador(this);
        this.cargos = new OpCargos(this);
        this.economia = new OpEconomia(this);
        this.itens = new OpItens(this);
        this.mundos = new OpMundos(this);
        this.servidor = new OpServidor(this);
        this.rpg = new OpRpg(this);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player admin)) {
            plugin.mensagens().enviar(s, "apenas-jogador");
            return true;
        }
        if (!admin.hasPermission(PERMISSAO)) {
            plugin.mensagens().enviar(admin, "sem-permissao");
            return true;
        }
        Player alvo = admin;
        if (a.length > 0) {
            alvo = Bukkit.getPlayerExact(a[0]);
            if (alvo == null) {
                plugin.mensagens().enviar(admin, "admin-jogador-nao-encontrado");
                return true;
            }
        }
        abrir(admin, alvo);
        Acoes.som(admin, "block.ender_chest.open", 0.7f, 1.2f);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1 && s.hasPermission(PERMISSAO)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    r.add(p.getName());
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ PAINEL PRINCIPAL

    public void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <black>" + alvo.getName()));
        m.item(4, cabeca(alvo));
        m.item(0, Itens.criar(Material.KNOWLEDGE_BOOK, "<yellow><bold>Como usar", List.of(
                "<gray>As ações de <white>Jogador<gray>, <white>Moderação<gray>,",
                "<white>Cargos<gray>, <white>Economia <gray>e <white>Itens <gray>valem para",
                "<gray>o jogador escolhido (a cabeça no topo).",
                "",
                "<gray>Troque o jogador pela <aqua>bússola<gray>.",
                "<gray>Ações perigosas pedem <white>Shift + clique<gray>.",
                "<gray>Algumas pedem um texto no chat.")));
        m.item(8, Itens.criar(Material.COMPASS, "<aqua><bold>Escolher jogador",
                List.of("<gray>Aplicar as ações em outro jogador online.")), (p, c) -> escolherJogador(p));

        categoria(m, 19, Material.GOLDEN_APPLE, "<green><bold>Jogador", List.of(
                "<gray>Curar, modo de jogo, voar, modo deus,",
                "<gray>velocidade, efeitos, XP, teleportes,",
                "<gray>ver inventário e baú do End, apelido..."), (p, c) -> jogador.abrirJogador(p, alvo));
        categoria(m, 20, Material.IRON_BARS, "<red><bold>Moderação", List.of(
                "<gray>Expulsar, silenciar, banir, desbanir,",
                "<gray>congelar, avisar, espiar e mais."), (p, c) -> jogador.abrirModeracao(p, alvo));
        categoria(m, 21, Material.NAME_TAG, "<gold><bold>Cargos", List.of(
                "<gray>Dar VIP, VIP+, MVP e cargos da staff,",
                "<gray>VIP por 30 dias, tirar cargos e OP."), (p, c) -> cargos.abrir(p, alvo));
        categoria(m, 22, Material.EMERALD, "<dark_green><bold>Economia e Rankup", List.of(
                "<gray>Dinheiro, tokens, rank, prestígio,",
                "<gray>blocos, boosters e progresso."), (p, c) -> economia.abrir(p, alvo));
        categoria(m, 23, Material.NETHERITE_PICKAXE, "<aqua><bold>Picaretas e Itens", List.of(
                "<gray>Picaretas MAX e Divina, editor de picareta,",
                "<gray>kits OP, encantar e consertar itens..."), (p, c) -> itens.abrir(p, alvo));
        categoria(m, 24, Material.GRASS_BLOCK, "<dark_aqua><bold>Mundos e Minas", List.of(
                "<gray>Teleportes, tempo, clima, minas,",
                "<gray>spawn, construção e pré-geração do mapa."), (p, c) -> mundos.abrir(p, alvo));
        categoria(m, 25, Material.COMMAND_BLOCK, "<light_purple><bold>Servidor", List.of(
                "<gray>Anúncios, manutenção, whitelist, regras",
                "<gray>da Sobrevivência, ações para todos,",
                "<gray>desempenho e desligar."), (p, c) -> servidor.abrir(p, alvo));
        categoria(m, 31, Material.GOLDEN_HELMET, "<gold><bold>RPG, Reinos e NPCs", List.of(
                "<gray>Personagem (nível, classe, itens),",
                "<gray>lobby do RPG, capital, lugares, masmorras,",
                "<gray>castelos, guerras, exércitos, moradores,",
                "<gray>NPCs, reputação e aprimoramento."), (p, c) -> rpg.abrir(p, alvo));

        // Atalhos rápidos do próprio dono
        acao(m, 38, Itens.criar(Material.ENDER_EYE, "<gray><bold>Invisível (vanish)",
                List.of("<gray>Liga/desliga o <white>seu<gray> vanish.")), alvo, true, "Vanish alterado",
                (p, c) -> p.performCommand("vanish"), this::abrir);
        acao(m, 39, Itens.criar(Material.FEATHER, "<aqua><bold>Voar (você)", List.of("<gray>Liga/desliga o seu voo.")), alvo, true,
                "Voo alterado", (p, c) -> {
                    boolean voar = !p.getAllowFlight();
                    p.setAllowFlight(voar);
                    p.setFlying(voar);
                }, this::abrir);
        acao(m, 40, Itens.criar(Material.BRICKS, "<gold><bold>Modo construção (você)",
                List.of("<gray>Quebrar/colocar blocos no spawn", "<gray>e nas minas do Rankup.")), alvo, true,
                "Modo construção alterado", (p, c) -> plugin.alternarModoConstrucao(p), this::abrir);
        acao(m, 41, Itens.criar(Material.GOLDEN_CARROT, "<gold><bold>Curar (você)", List.of("<gray>Vida e fome cheias.")),
                alvo, true, "Você foi curado", (p, c) -> OpJogador.curar(p), this::abrir);
        acao(m, 42, Itens.criar(Material.COMPARATOR, "<white><bold>Recarregar configurações",
                List.of("<gray>Mesmo que /rankupadmin recarregar.")), alvo, true, "Configurações recarregadas",
                (p, c) -> plugin.recarregar(), this::abrir);

        m.item(49, Itens.criar(Material.BARRIER, "<red><bold>Fechar", List.of()), (p, c) -> p.closeInventory());
        m.preencherVazios(Itens.vidro(Material.RED_STAINED_GLASS_PANE));
        m.abrir(admin);
    }

    private void categoria(Menu m, int slot, Material icone, String nome, List<String> descricao,
                           BiConsumer<Player, ClickType> abrir) {
        List<String> lore = new ArrayList<>(descricao);
        lore.add("");
        lore.add("<yellow>Clique para abrir!");
        m.item(slot, Itens.criar(icone, nome, lore, true), (p, c) -> {
            if (p.hasPermission(PERMISSAO)) {
                abrir.accept(p, c);
            }
        });
    }

    ItemStack cabeca(Player alvo) {
        DadosJogador d = plugin.jogadores().obter(alvo);
        ItemStack cabeca = Itens.criar(Material.PLAYER_HEAD, "<gold><bold>" + alvo.getName(), List.of(
                "<gray>Rank: " + plugin.ranks().obter(d.rank()).tag(),
                "<gray>Prestígio: <light_purple>" + d.prestigio(),
                "<gray>Dinheiro (mundo atual): <green>$" + Numeros.abreviar(plugin.economia().saldo(alvo)),
                "<gray>Tokens: <light_purple>" + Numeros.inteiro(d.tokens()),
                "<gray>Blocos: <gold>" + Numeros.abreviar(d.blocos()),
                "<gray>Mundo: <white>" + plugin.visual().variaveis().nomeMundo(alvo.getWorld()),
                "<gray>Modo de jogo: <white>" + alvo.getGameMode().name().toLowerCase(Locale.ROOT),
                "<gray>Vida: <red>" + Math.round(alvo.getHealth()) + " <gray>| Nível: <green>" + alvo.getLevel(),
                "<gray>OP: " + (alvo.isOp() ? "<green>sim" : "<red>não")
                        + (ferramentas.congelado(alvo) ? " <aqua>(congelado)" : "")));
        cabeca.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(alvo));
        return cabeca;
    }

    /** Barra de baixo das páginas: voltar, cabeça do alvo e fechar. */
    void rodape(Menu m, Player alvo) {
        m.item(48, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (p, c) -> abrir(p, alvo));
        m.item(49, cabeca(alvo));
        m.item(50, Itens.criar(Material.BARRIER, "<red><bold>Fechar", List.of()), (p, c) -> p.closeInventory());
        m.preencherVazios(Itens.vidro(Material.RED_STAINED_GLASS_PANE));
    }

    // ------------------------------------------------------------------ AÇÕES

    /**
     * Botão de ação. "geral" = ação do admin ou do servidor (a mensagem não cita o alvo).
     * "reabrir" = página a reabrir depois (null para não reabrir, ex.: quando abre outro inventário).
     */
    void acao(Menu m, int slot, ItemStack item, Player alvo, boolean geral, String descricao,
              BiConsumer<Player, ClickType> efeito, Pagina reabrir) {
        m.item(slot, item, (p, c) -> {
            if (!podeUsar(p, alvo)) {
                return;
            }
            efeito.accept(p, c);
            if (reabrir != null) {
                concluir(p, alvo, geral, descricao, reabrir);
            }
        });
    }

    /** Ação perigosa: só executa com Shift + clique. */
    void acaoConfirmada(Menu m, int slot, ItemStack item, Player alvo, boolean geral, String descricao,
                        BiConsumer<Player, ClickType> efeito, Pagina reabrir) {
        m.item(slot, item, (p, c) -> {
            if (!podeUsar(p, alvo)) {
                return;
            }
            if (!c.isShiftClick()) {
                plugin.mensagens().enviar(p, "menuop-confirmar", "acao", descricao);
                Acoes.som(p, "block.note_block.bass", 1f, 0.8f);
                return;
            }
            efeito.accept(p, c);
            if (reabrir != null) {
                concluir(p, alvo, geral, descricao, reabrir);
            }
        });
    }

    boolean podeUsar(Player p, Player alvo) {
        if (!p.hasPermission(PERMISSAO)) {
            p.closeInventory();
            plugin.mensagens().enviar(p, "sem-permissao");
            return false;
        }
        if (!alvo.isOnline()) {
            p.closeInventory();
            plugin.mensagens().enviar(p, "admin-jogador-nao-encontrado");
            return false;
        }
        return true;
    }

    void concluir(Player admin, Player alvo, boolean geral, String descricao, Pagina reabrir) {
        plugin.visual().atualizarJogador(alvo);
        plugin.jogadores().atualizarTop();
        if (geral) {
            plugin.mensagens().enviar(admin, "menuop-feito-geral", "acao", descricao);
        } else {
            plugin.mensagens().enviar(admin, "menuop-feito", "acao", descricao, "jogador", alvo.getName());
        }
        Acoes.som(admin, "entity.experience_orb.pickup", 0.8f, 1.6f);
        if (admin.isOnline() && alvo.isOnline()) {
            reabrir.accept(admin, alvo);
        }
    }

    /** Pergunta no chat e depois reabre a página. */
    void perguntar(Player admin, Player alvo, String pergunta, Pagina reabrir, Consumer<String> resposta) {
        ferramentas.perguntar(admin, pergunta, texto -> {
            if (!admin.hasPermission(PERMISSAO)) {
                return;
            }
            resposta.accept(texto);
            if (alvo.isOnline() && reabrir != null) {
                reabrir.accept(admin, alvo);
            }
        });
    }

    /** Executa um comando pelo console (LuckPerms, Essentials, Chunky...). */
    static void console(String comando) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
    }

    static void dar(Player alvo, ItemStack item) {
        for (ItemStack sobra : alvo.getInventory().addItem(item).values()) {
            alvo.getWorld().dropItemNaturally(alvo.getLocation(), sobra);
        }
    }

    static Double numero(String texto) {
        String t = texto.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(" ", "");
        double mult = 1;
        if (t.endsWith("k")) {
            mult = 1e3;
        } else if (t.endsWith("m")) {
            mult = 1e6;
        } else if (t.endsWith("b")) {
            mult = 1e9;
        } else if (t.endsWith("t")) {
            mult = 1e12;
        }
        if (mult > 1) {
            t = t.substring(0, t.length() - 1);
        }
        t = t.replace(".", "").replace(",", ".");
        try {
            double v = Double.parseDouble(t) * mult;
            return Double.isFinite(v) && v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ESCOLHER JOGADOR

    void escolherJogador(Player admin) {
        Menu m = new Menu(6, Texto.cor("<dark_red>Escolha um jogador"));
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            ItemStack cabeca = Itens.criar(Material.PLAYER_HEAD, "<yellow>" + p.getName(), List.of(
                    "<gray>Rank: " + plugin.ranks().obter(plugin.jogadores().obter(p).rank()).tag(),
                    "<gray>Mundo: <white>" + plugin.visual().variaveis().nomeMundo(p.getWorld()),
                    "", "<green>Clique para abrir o Menu OP dele"));
            cabeca.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(p));
            m.item(slot++, cabeca, (quem, c) -> {
                if (p.isOnline() && quem.hasPermission(PERMISSAO)) {
                    abrir(quem, p);
                }
            });
        }
        m.item(49, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (quem, c) -> abrir(quem, quem));
        m.abrir(admin);
    }
}
