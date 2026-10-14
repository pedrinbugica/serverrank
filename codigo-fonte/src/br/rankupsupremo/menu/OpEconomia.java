package br.rankupsupremo.menu;

import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.rank.Rank;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Página "Economia e Rankup" do /menuop.
 */
final class OpEconomia {

    private final MenuOp op;

    OpEconomia(MenuOp op) {
        this.op = op;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <dark_green>Economia <dark_gray>» <black>" + alvo.getName()));
        MenuOp.Pagina pg = this::abrir;
        DadosJogador d = op.plugin.jogadores().obter(alvo);
        String mundo = op.plugin.visual().variaveis().nomeMundo(alvo.getWorld());

        // Dinheiro (é o dinheiro do mundo onde ele está: Rankup e Sobrevivência são separados)
        dinheiro(m, 10, Material.EMERALD, 1_000, alvo);
        dinheiro(m, 11, Material.EMERALD_BLOCK, 100_000, alvo);
        dinheiro(m, 12, Material.GOLD_BLOCK, 1_000_000, alvo);
        dinheiro(m, 13, Material.DIAMOND_BLOCK, 1_000_000_000, alvo);
        op.acaoConfirmada(m, 14, Itens.criar(Material.REDSTONE, "<red><bold>Zerar dinheiro",
                List.of("<gray>Deixa o saldo em $0", "<gray>(no mundo " + mundo + "<gray>).", "", "<red>Shift + clique para confirmar")),
                alvo, false, "Dinheiro zerado", (p, c) -> {
                    double saldo = op.plugin.economia().saldo(alvo);
                    if (saldo > 0) {
                        op.plugin.economia().sacar(alvo, saldo);
                    }
                }, pg);
        m.item(15, Itens.criar(Material.WRITABLE_BOOK, "<green><bold>Definir dinheiro",
                List.of("<gray>Digite o valor no chat.", "<gray>Aceita 5000, 2,5k, 10m, 1b...",
                        "<gray>Vale para o mundo " + mundo + "<gray>.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Quanto dinheiro " + alvo.getName() + " deve ter?", pg, texto -> {
                            Double v = MenuOp.numero(texto);
                            if (v == null) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                                return;
                            }
                            double saldo = op.plugin.economia().saldo(alvo);
                            if (v > saldo) {
                                op.plugin.economia().depositar(alvo, v - saldo);
                            } else if (v < saldo) {
                                op.plugin.economia().sacar(alvo, saldo - v);
                            }
                            op.plugin.mensagens().enviar(p, "menuop-feito", "acao", "Dinheiro definido: $" + Numeros.abreviar(v),
                                    "jogador", alvo.getName());
                        });
                    }
                });
        m.item(16, Itens.criar(Material.AMETHYST_CLUSTER, "<light_purple><bold>Tokens: " + Numeros.inteiro(d.tokens()), List.of(
                "<gray>Esquerdo: <green>+1.000 <gray>| Direito: <red>-1.000",
                "<gray>Shift + esquerdo: <green>+10.000",
                "<gray>Shift + direito: <white>digitar o valor")), (p, c) -> {
                    if (!op.podeUsar(p, alvo)) {
                        return;
                    }
                    switch (c) {
                        case SHIFT_RIGHT -> op.perguntar(p, alvo, "Quantos tokens " + alvo.getName() + " deve ter?", pg, texto -> {
                            Double v = MenuOp.numero(texto);
                            if (v == null) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                                return;
                            }
                            d.setTokens(v.longValue());
                            op.plugin.visual().atualizarJogador(alvo);
                        });
                        case RIGHT -> {
                            d.adicionarTokens(-1_000);
                            op.concluir(p, alvo, false, "-1.000 tokens", pg);
                        }
                        case SHIFT_LEFT -> {
                            d.adicionarTokens(10_000);
                            op.concluir(p, alvo, false, "+10.000 tokens", pg);
                        }
                        default -> {
                            d.adicionarTokens(1_000);
                            op.concluir(p, alvo, false, "+1.000 tokens", pg);
                        }
                    }
                });

        // Rank e prestígio
        op.acao(m, 19, Itens.criar(Material.EXPERIENCE_BOTTLE, "<green><bold>Rank +1 / -1",
                List.of("<gray>Atual: " + op.plugin.ranks().obter(d.rank()).tag(),
                        "<gray>Esquerdo: <green>sobe 1 rank (grátis)", "<gray>Direito: <red>desce 1 rank")), alvo, false,
                "Rank alterado", (p, c) -> {
                    List<Rank> ranks = op.plugin.ranks().todos();
                    int i = op.plugin.ranks().obter(d.rank()).indice() + (c.isRightClick() ? -1 : 1);
                    d.setRank(ranks.get(Math.max(0, Math.min(ranks.size() - 1, i))).id());
                }, pg);
        op.acao(m, 20, Itens.criar(Material.NETHER_STAR, "<gradient:#00F5FF:#B388FF><bold>Rank máximo</bold></gradient>",
                List.of("<gray>Vai direto para " + op.plugin.ranks().ultimo().tag()), true), alvo, false, "Rank máximo definido",
                (p, c) -> d.setRank(op.plugin.ranks().ultimo().id()), pg);
        m.item(21, Itens.criar(Material.LADDER, "<yellow><bold>Escolher rank",
                List.of("<gray>Abre a lista de ranks", "<gray>para escolher qualquer um.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        escolherRank(p, alvo);
                    }
                });
        op.acao(m, 22, Itens.criar(Material.END_CRYSTAL, "<light_purple><bold>Prestígio " + d.prestigio() + " <gray>(+1 / -1)",
                List.of("<gray>Esquerdo: <green>+1", "<gray>Direito: <red>-1", "<gray>Shift: <white>x10")), alvo, false,
                "Prestígio alterado", (p, c) -> {
                    int q = c.isShiftClick() ? 10 : 1;
                    int novo = d.prestigio() + (c.isRightClick() ? -q : q);
                    d.setPrestigio(Math.max(0, Math.min(op.plugin.ranks().prestigioMaximo(), novo)));
                }, pg);
        m.item(23, Itens.criar(Material.WRITABLE_BOOK, "<light_purple><bold>Definir prestígio",
                List.of("<gray>Digite o número no chat.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual prestígio " + alvo.getName() + " deve ter?", pg, texto -> {
                            Double v = MenuOp.numero(texto);
                            if (v == null) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                                return;
                            }
                            d.setPrestigio((int) Math.min(op.plugin.ranks().prestigioMaximo(), v));
                            op.plugin.visual().atualizarJogador(alvo);
                            op.plugin.jogadores().atualizarTop();
                        });
                    }
                });
        op.acao(m, 24, Itens.criar(Material.IRON_PICKAXE, "<gold><bold>Blocos minerados: " + Numeros.abreviar(d.blocos()),
                List.of("<gray>Esquerdo: <green>+10 mil", "<gray>Shift + esquerdo: <green>+100 mil",
                        "<gray>Shift + direito: <red>zerar", "<gray>(ranks altos pedem blocos)")), alvo, false,
                "Blocos alterados", (p, c) -> {
                    if (c == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                        d.setBlocos(0);
                    } else {
                        d.adicionarBlocos(c.isShiftClick() ? 100_000 : 10_000);
                    }
                }, pg);
        op.acao(m, 25, Itens.criar(Material.HOPPER, "<green><bold>Autovenda " + (d.autovender() ? "<green>(ligada)" : "<red>(desligada)"),
                List.of("<gray>Liga/desliga a venda automática", "<gray>dele (mesmo sem prestígio/VIP).")), alvo, false,
                "Autovenda alterada", (p, c) -> d.setAutovender(!d.autovender()), pg);

        // Boosters e progresso
        op.acao(m, 28, Itens.criar(Material.EXPERIENCE_BOTTLE, "<gold><bold>Booster de venda +100%",
                List.of("<gray>Esquerdo: <green>dar por 30 minutos", "<gray>Direito: <red>tirar o booster")), alvo, false,
                "Booster de venda alterado", (p, c) -> d.setBooster(c.isRightClick() ? 0 : 1.0,
                        c.isRightClick() ? 0 : System.currentTimeMillis() + 30 * 60_000L), pg);
        op.acao(m, 29, Itens.criar(Material.AMETHYST_SHARD, "<light_purple><bold>Booster de tokens +100%",
                List.of("<gray>Esquerdo: <green>dar por 30 minutos", "<gray>Direito: <red>tirar o booster")), alvo, false,
                "Booster de tokens alterado", (p, c) -> d.setBoosterTokens(c.isRightClick() ? 0 : 1.0,
                        c.isRightClick() ? 0 : System.currentTimeMillis() + 30 * 60_000L), pg);
        op.acao(m, 30, Itens.criar(Material.CHEST_MINECART, "<green><bold>Forçar venda do inventário",
                List.of("<gray>Vende os minérios do inventário dele.")), alvo, false, "Venda forçada",
                (p, c) -> op.plugin.acoes().vender(alvo), pg);
        op.acao(m, 31, Itens.criar(Material.GOLDEN_HELMET, "<yellow><bold>Ranking (top 10)",
                List.of("<gray>Mostra o top no chat.")), alvo, true, "Ranking mostrado", (p, c) -> {
                    p.closeInventory();
                    op.plugin.acoes().mostrarTop(p);
                }, null);
        op.acaoConfirmada(m, 32, Itens.criar(Material.TNT, "<dark_red><bold>Resetar progresso do Rankup",
                List.of("<gray>Rank inicial, prestígio 0, tokens 0,", "<gray>blocos 0 e sem boosters.",
                        "<gray>(o dinheiro não muda)", "", "<red>Shift + clique para confirmar")), alvo, false,
                "Progresso resetado", (p, c) -> {
                    d.setRank(op.plugin.ranks().primeiro().id());
                    d.setPrestigio(0);
                    d.setTokens(0);
                    d.setBlocos(0);
                    d.setBooster(0, 0);
                    d.setBoosterTokens(0, 0);
                }, pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    private void dinheiro(Menu m, int slot, Material icone, long valor, Player alvo) {
        String v = Numeros.abreviar(valor);
        ItemStack item = Itens.criar(icone, "<green><bold>$" + v, List.of(
                "<gray>Esquerdo: <green>+$" + v,
                "<gray>Direito: <red>-$" + v,
                "<gray>Shift: <white>x10"));
        m.item(slot, item, (p, c) -> {
            if (!op.podeUsar(p, alvo)) {
                return;
            }
            double quantia = valor * (c.isShiftClick() ? 10.0 : 1.0);
            if (c.isRightClick()) {
                op.plugin.economia().sacar(alvo, Math.min(quantia, op.plugin.economia().saldo(alvo)));
            } else {
                op.plugin.economia().depositar(alvo, quantia);
            }
            op.concluir(p, alvo, false, (c.isRightClick() ? "-$" : "+$") + Numeros.abreviar(quantia), this::abrir);
        });
    }

    private void escolherRank(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red>Escolher rank <dark_gray>» <black>" + alvo.getName()));
        DadosJogador d = op.plugin.jogadores().obter(alvo);
        List<Rank> ranks = op.plugin.ranks().todos();
        for (int i = 0; i < ranks.size() && i < 45; i++) {
            Rank r = ranks.get(i);
            boolean atual = r.id().equals(d.rank());
            op.acao(m, i, Itens.criar(r.icone(), r.tag() + " <white>" + r.nome(),
                    List.of(atual ? "<yellow>➜ Rank atual" : "<green>Clique para definir"), atual), alvo, false,
                    "Rank definido: " + r.nome(), (p, c) -> d.setRank(r.id()), this::abrir);
        }
        m.item(49, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (p, c) -> abrir(p, alvo));
        m.abrir(admin);
    }
}
