package br.rankupsupremo.menu;

import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Página "Mundos e Minas" do /menuop. Teleportes e tempo valem para VOCÊ (o dono).
 */
final class OpMundos {

    private final MenuOp op;

    OpMundos(MenuOp op) {
        this.op = op;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <dark_aqua>Mundos e Minas"));
        MenuOp.Pagina pg = this::abrir;
        String base = op.plugin.sobrevivencia().nomeBase();

        // Teleportes (você)
        op.acao(m, 10, Itens.criar(Material.BEACON, "<gold><bold>Spawn do Rankup", List.of("<gray>Vai para o spawn.")), alvo, true,
                "Teleportado", (p, c) -> op.plugin.acoes().teleportarSpawn(p, true), null);
        op.acao(m, 11, Itens.criar(Material.GRASS_BLOCK, "<green><bold>Spawn da Sobrevivência", List.of("<gray>Vai para a praça do survival.")),
                alvo, true, "Teleportado", (p, c) -> {
                    Location s = op.plugin.sobrevivencia().spawn();
                    if (s != null) {
                        p.teleport(s);
                    }
                }, null);
        op.acao(m, 12, Itens.criar(Material.NETHERRACK, "<red><bold>Nether da Sobrevivência", List.of("<gray>Vai para um lugar seguro no Nether.")),
                alvo, true, "Teleportado", (p, c) -> irPara(p, Bukkit.getWorld(base + "_nether")), null);
        op.acao(m, 13, Itens.criar(Material.END_STONE, "<light_purple><bold>The End da Sobrevivência", List.of("<gray>Vai para a plataforma do End.")),
                alvo, true, "Teleportado", (p, c) -> irPara(p, Bukkit.getWorld(base + "_the_end")), null);
        m.item(14, Itens.criar(Material.DIAMOND_PICKAXE, "<aqua><bold>Todas as minas", List.of(
                "<gray>Ir, resetar ou reconstruir", "<gray>qualquer mina (inclusive VIP e PvP)."), true), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        minas(p, alvo);
                    }
                });
        m.item(15, Itens.criar(Material.FILLED_MAP, "<white><bold>Ir para coordenadas",
                List.of("<gray>Digite <white>x y z <gray>no chat", "<gray>(no mundo onde você está).")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Para onde? (x y z)", null, texto -> {
                            String[] partes = texto.replace(",", " ").trim().split("\\s+");
                            try {
                                p.teleport(new Location(p.getWorld(), Double.parseDouble(partes[0]), Double.parseDouble(partes[1]),
                                        Double.parseDouble(partes[2]), p.getLocation().getYaw(), p.getLocation().getPitch()));
                            } catch (RuntimeException e) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                            }
                        });
                    }
                });
        op.acao(m, 16, Itens.criar(Material.RECOVERY_COMPASS, "<gray><bold>Voltar (/back)",
                List.of("<gray>Volta para onde você estava", "<gray>antes do último teleporte.")), alvo, true, "Teleportado",
                (p, c) -> p.performCommand("back"), null);

        // Tempo e clima do mundo onde você está
        World w = admin.getWorld();
        String nomeMundo = op.plugin.visual().variaveis().nomeMundo(w);
        String aviso = op.plugin.ehMundoRankup(w) ? "<dark_gray>(no Rankup o clima fica sempre limpo)" : "";
        tempo(m, 19, Material.SUNFLOWER, "<yellow><bold>Dia", 1000, alvo, nomeMundo);
        tempo(m, 20, Material.GOLD_BLOCK, "<gold><bold>Meio-dia", 6000, alvo, nomeMundo);
        tempo(m, 21, Material.BLACK_WOOL, "<dark_blue><bold>Noite", 13000, alvo, nomeMundo);
        op.acao(m, 22, Itens.criar(Material.LIGHT_BLUE_STAINED_GLASS, "<aqua><bold>Céu limpo",
                List.of("<gray>Mundo: " + nomeMundo)), alvo, true, "Clima limpo", (p, c) -> {
                    p.getWorld().setStorm(false);
                    p.getWorld().setThundering(false);
                    p.getWorld().setClearWeatherDuration(20 * 60 * 30);
                }, pg);
        op.acao(m, 23, Itens.criar(Material.WATER_BUCKET, "<blue><bold>Chuva", List.of("<gray>Mundo: " + nomeMundo, aviso)), alvo, true,
                "Chuva ligada", (p, c) -> {
                    p.getWorld().setStorm(true);
                    p.getWorld().setThundering(false);
                }, pg);
        op.acao(m, 24, Itens.criar(Material.TRIDENT, "<dark_gray><bold>Tempestade", List.of("<gray>Mundo: " + nomeMundo, aviso)), alvo, true,
                "Tempestade ligada", (p, c) -> {
                    p.getWorld().setStorm(true);
                    p.getWorld().setThundering(true);
                }, pg);
        boolean ciclo = Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_TIME));
        op.acao(m, 25, Itens.criar(Material.CLOCK, "<white><bold>Ciclo do dia " + (ciclo ? "<green>(andando)" : "<red>(parado)"),
                List.of("<gray>Liga/desliga a passagem do tempo", "<gray>no mundo " + nomeMundo + "<gray>.")), alvo, true,
                "Ciclo do dia alterado", (p, c) -> p.getWorld().setGameRule(GameRules.ADVANCE_TIME,
                        !Boolean.TRUE.equals(p.getWorld().getGameRuleValue(GameRules.ADVANCE_TIME))), pg);

        // Construção e mapa
        op.acao(m, 28, Itens.criar(Material.RED_BED, "<gold><bold>Spawn do Rankup aqui",
                List.of("<gray>Define o spawn do Rankup", "<gray>onde você está agora.")), alvo, true, "Spawn do Rankup definido", (p, c) -> {
                    if (op.plugin.ehMundoRankup(p.getWorld())) {
                        op.plugin.definirSpawn(p.getLocation());
                    } else {
                        op.plugin.mensagens().enviar(p, "apenas-rankup");
                    }
                }, pg);
        op.acao(m, 29, Itens.criar(Material.GREEN_BED, "<green><bold>Spawn da Sobrevivência aqui",
                List.of("<gray>Define o spawn do survival", "<gray>onde você está agora.")), alvo, true, "Spawn da Sobrevivência definido", (p, c) -> {
                    if (op.plugin.sobrevivencia().ehSobrevivencia(p.getWorld())) {
                        p.performCommand("mv setspawn");
                    } else {
                        op.plugin.mensagens().enviar(p, "sobrevivencia-indisponivel");
                    }
                }, pg);
        op.acao(m, 30, Itens.criar(Material.TNT, "<red><bold>Resetar todas as minas", List.of("<gray>Enche todas as minas agora.")),
                alvo, true, "Minas resetadas", (p, c) -> op.plugin.minas().resetarTodas(true), pg);
        op.acaoConfirmada(m, 31, Itens.criar(Material.STONE_BRICKS, "<gold><bold>Reconstruir o spawn",
                List.of("<gray>Constrói o spawn do Rankup de novo.", "", "<red>Shift + clique para confirmar")), alvo, true,
                "Spawn reconstruído", (p, c) -> op.plugin.construtor().construirSpawn(op.plugin.mundo()), pg);
        op.acao(m, 32, Itens.criar(Material.MAP, "<aqua><bold>Pré-gerar o mapa do survival",
                List.of("<gray>Gera 3.000 blocos em volta do spawn", "<gray>para o servidor ficar mais leve.",
                        "<gray>Demora; pode jogar enquanto isso.")), alvo, true, "Pré-geração iniciada", (p, c) -> {
                    MenuOp.console("chunky world " + base);
                    MenuOp.console("chunky radius 3000");
                    MenuOp.console("chunky start");
                }, pg);
        op.acao(m, 33, Itens.criar(Material.REPEATER, "<aqua><bold>Pausar / continuar pré-geração",
                List.of("<gray>Esquerdo: <white>pausar", "<gray>Direito: <white>continuar")), alvo, true, "Pré-geração alterada",
                (p, c) -> MenuOp.console(c.isRightClick() ? "chunky continue" : "chunky pause"), pg);
        m.item(34, Itens.criar(Material.SPYGLASS, "<white><bold>Situação dos mundos", infoMundos()));

        // Limpeza
        op.acao(m, 37, Itens.criar(Material.HOPPER, "<yellow><bold>Limpar itens do chão",
                List.of("<gray>Apaga os itens jogados no chão", "<gray>em todos os mundos (deixa mais leve).")), alvo, true,
                "Itens do chão apagados", (p, c) -> {
                    int n = remover(Item.class);
                    op.plugin.mensagens().enviar(p, "menuop-feito-geral", "acao", n + " itens apagados");
                }, pg);
        op.acao(m, 38, Itens.criar(Material.ZOMBIE_HEAD, "<red><bold>Matar monstros",
                List.of("<gray>Remove zumbis, esqueletos, creepers...", "<gray>de todos os mundos.")), alvo, true,
                "Monstros removidos", (p, c) -> {
                    int n = remover(Monster.class);
                    op.plugin.mensagens().enviar(p, "menuop-feito-geral", "acao", n + " monstros removidos");
                }, pg);
        op.acaoConfirmada(m, 39, Itens.criar(Material.LEAD, "<dark_red><bold>Matar animais soltos",
                List.of("<gray>Remove animais sem nome do mundo", "<gray>onde você está (cuidado com as farms!).", "",
                        "<red>Shift + clique para confirmar")), alvo, true, "Animais removidos", (p, c) -> {
                    int n = 0;
                    for (Entity e : p.getWorld().getEntities()) {
                        if (e instanceof Animals a && a.customName() == null && !a.isLeashed()) {
                            a.remove();
                            n++;
                        }
                    }
                    op.plugin.mensagens().enviar(p, "menuop-feito-geral", "acao", n + " animais removidos");
                }, pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    private void tempo(Menu m, int slot, Material icone, String nome, long tempo, Player alvo, String mundo) {
        op.acao(m, slot, Itens.criar(icone, nome, List.of("<gray>Mundo: " + mundo)), alvo, true, "Horário alterado",
                (p, c) -> p.getWorld().setTime(tempo), this::abrir);
    }

    private List<String> infoMundos() {
        List<String> l = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            l.add("<white>" + w.getName() + "<gray>: " + w.getPlayers().size() + " jogadores, "
                    + w.getLoadedChunks().length + " chunks, " + w.getEntityCount() + " entidades");
        }
        return l;
    }

    private static int remover(Class<? extends Entity> tipo) {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(tipo)) {
                if (e.customName() == null) {
                    e.remove();
                    n++;
                }
            }
        }
        return n;
    }

    /** Teleporta para um lugar seguro perto do spawn do mundo (Nether e End). */
    private void irPara(Player p, World w) {
        if (w == null) {
            op.plugin.mensagens().enviar(p, "sobrevivencia-indisponivel");
            return;
        }
        if (w.getEnvironment() == World.Environment.THE_END) {
            p.teleport(new Location(w, 100.5, 50, 0.5, 90, 0)); // plataforma de obsidiana
            return;
        }
        Location s = w.getSpawnLocation();
        for (int raio = 0; raio <= 24; raio += 4) {
            for (int dx = -raio; dx <= raio; dx += 4) {
                for (int dz = -raio; dz <= raio; dz += 4) {
                    for (int y = 32; y < 118; y++) {
                        Block chao = w.getBlockAt(s.getBlockX() + dx, y, s.getBlockZ() + dz);
                        if (chao.getType().isSolid() && chao.getRelative(0, 1, 0).isEmpty() && chao.getRelative(0, 2, 0).isEmpty()) {
                            p.teleport(chao.getLocation().add(0.5, 1, 0.5));
                            return;
                        }
                    }
                }
            }
        }
        p.teleport(s);
    }

    // ------------------------------------------------------------------ MINAS

    private void minas(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red>Todas as minas"));
        List<Mina> todas = op.plugin.minas().todas();
        for (int i = 0; i < todas.size() && i < 45; i++) {
            Mina mina = todas.get(i);
            m.item(i, Itens.criar(mina.icone(), mina.nome(), List.of(
                    "<gray>Acesso: " + op.plugin.minas().requisito(mina),
                    "<gray>Restante: <white>" + Numeros.porcentagem(mina.restante()) + "%",
                    "<gray>Próximo reset: <yellow>" + Numeros.tempo(Math.max(0, (mina.proximoReset() - System.currentTimeMillis()) / 1000)),
                    "",
                    "<yellow>Esquerdo: <white>ir para a mina",
                    "<yellow>Direito: <white>resetar agora",
                    "<yellow>Shift + direito: <white>reconstruir a estrutura"), mina.especial()), (p, c) -> {
                        if (!op.podeUsar(p, alvo)) {
                            return;
                        }
                        switch (c) {
                            case RIGHT -> {
                                op.plugin.minas().resetar(mina, true);
                                op.plugin.mensagens().enviar(p, "admin-mina-resetada", "mina", mina.nome());
                                minas(p, alvo);
                            }
                            case SHIFT_RIGHT -> {
                                p.closeInventory();
                                op.plugin.mensagens().enviar(p, "admin-reconstruindo", "alvo", mina.nome());
                                op.plugin.construtor().limparAreaMina(mina, () -> {
                                    op.plugin.construtor().construirMina(mina);
                                    op.plugin.estado().setMinaConstruida(mina.id(), true);
                                    op.plugin.minas().resetar(mina, false);
                                    op.plugin.mensagens().enviar(p, "admin-reconstruido", "alvo", mina.nome());
                                });
                            }
                            default -> {
                                p.closeInventory();
                                if (mina.mundo() != null) {
                                    p.teleport(mina.spawn());
                                }
                            }
                        }
                    });
        }
        m.item(49, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (p, c) -> abrir(p, alvo));
        m.abrir(admin);
    }
}
