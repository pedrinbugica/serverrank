package br.rankupsupremo.menu;

import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Página "Cargos" do /menuop (usa o LuckPerms pelo console).
 */
final class OpCargos {

    private record Cargo(String grupo, String nome, Material icone) {
    }

    private static final List<Cargo> CARGOS = List.of(
            new Cargo("default", "<gray><bold>Jogador", Material.LEATHER_HELMET),
            new Cargo("vip", "<green><bold>VIP", Material.EMERALD),
            new Cargo("vipplus", "<yellow><bold>VIP+", Material.GOLD_INGOT),
            new Cargo("mvp", "<aqua><bold>MVP", Material.DIAMOND),
            new Cargo("ajudante", "<yellow><bold>Ajudante", Material.BOOK),
            new Cargo("moderador", "<dark_green><bold>Moderador", Material.IRON_SWORD),
            new Cargo("admin", "<red><bold>Admin", Material.DIAMOND_SWORD),
            new Cargo("dono", "<dark_red><bold>Dono", Material.NETHER_STAR));

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    private final MenuOp op;

    OpCargos(MenuOp op) {
        this.op = op;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <gold>Cargos <dark_gray>» <black>" + alvo.getName()));
        // O LuckPerms aplica as mudanças em segundo plano: reabre um pouco depois para mostrar o resultado
        MenuOp.Pagina pg = (a, b) -> Bukkit.getScheduler().runTaskLater(op.plugin, () -> {
            if (a.isOnline() && b.isOnline()) {
                abrir(a, b);
            }
        }, 10L);
        String nome = alvo.getName();
        for (int i = 0; i < CARGOS.size(); i++) {
            Cargo cargo = CARGOS.get(i);
            boolean tem = cargo.grupo().equals("default") || alvo.hasPermission("group." + cargo.grupo());
            op.acao(m, SLOTS[i], Itens.criar(cargo.icone(), cargo.nome() + (tem ? " <green>✔" : ""), List.of(
                    "<gray>Tem este cargo: " + (tem ? "<green>sim" : "<red>não"),
                    "",
                    "<yellow>Esquerdo: <white>vira SÓ este cargo",
                    "<yellow>Direito: <white>soma este cargo aos outros",
                    "<yellow>Shift + esquerdo: <white>soma por 30 dias",
                    "<yellow>Shift + direito: <white>tira este cargo"), tem), alvo, false, "Cargo alterado", (p, c) -> {
                        String cmd = switch (c) {
                            case SHIFT_LEFT -> "parent addtemp " + cargo.grupo() + " 30d";
                            case SHIFT_RIGHT -> "parent remove " + cargo.grupo();
                            case RIGHT -> "parent add " + cargo.grupo();
                            default -> "parent set " + cargo.grupo();
                        };
                        MenuOp.console("lp user " + nome + " " + cmd);
                    }, pg);
        }

        op.acaoConfirmada(m, 29, Itens.criar(Material.BARRIER, "<red><bold>Tirar todos os cargos",
                List.of("<gray>Volta a ser um jogador comum.", "", "<red>Shift + clique para confirmar")), alvo, false,
                "Cargos removidos", (p, c) -> MenuOp.console("lp user " + nome + " parent clear"), pg);
        m.item(30, Itens.criar(Material.TRIPWIRE_HOOK, "<aqua><bold>Permissão avulsa",
                List.of("<gray>Esquerdo: <white>dar uma permissão", "<gray>Direito: <white>tirar uma permissão",
                        "<gray>Ex.: <white>essentials.fly")), (p, c) -> {
                    if (!op.podeUsar(p, alvo)) {
                        return;
                    }
                    boolean tirar = c.isRightClick();
                    op.perguntar(p, alvo, tirar ? "Qual permissão tirar de " + nome + "?" : "Qual permissão dar para " + nome + "?",
                            pg, texto -> {
                                String perm = texto.split("\\s+")[0];
                                MenuOp.console("lp user " + nome + " permission " + (tirar ? "unset " + perm : "set " + perm + " true"));
                                op.plugin.mensagens().enviar(p, "menuop-feito", "acao",
                                        (tirar ? "Permissão removida: " : "Permissão dada: ") + perm, "jogador", nome);
                            });
                });
        op.acao(m, 31, Itens.criar(Material.KNOWLEDGE_BOOK, "<white><bold>Ver cargos e permissões",
                List.of("<gray>Mostra no chat os cargos dele.")), alvo, false, "Informações mostradas", (p, c) -> {
                    p.closeInventory();
                    p.performCommand("lp user " + nome + " parent info");
                }, null);
        op.acaoConfirmada(m, 33, Itens.criar(Material.COMMAND_BLOCK, "<dark_red><bold>OP do servidor " + (alvo.isOp() ? "<green>(sim)" : "<red>(não)"),
                List.of("<gray>OP pode usar todos os comandos", "<gray>do Minecraft. Dê só para quem confia!", "",
                        "<red>Shift + clique para alternar")), alvo, false, "OP alterado", (p, c) -> alvo.setOp(!alvo.isOp()), pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }
}
