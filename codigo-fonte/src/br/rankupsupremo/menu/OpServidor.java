package br.rankupsupremo.menu;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

/**
 * Página "Servidor" do /menuop: anúncios, manutenção, regras da Sobrevivência, ações para todos e desligar.
 */
final class OpServidor {

    private final MenuOp op;
    private BukkitTask desligando;

    OpServidor(MenuOp op) {
        this.op = op;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <light_purple>Servidor"));
        MenuOp.Pagina pg = this::abrir;

        // Comunicação e manutenção
        m.item(10, Itens.criar(Material.GOAT_HORN, "<gold><bold>Anúncio no chat",
                List.of("<gray>Manda um anúncio grande para todos.", "<gray>Aceita cores: &a &l &#FFAA00")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual o anúncio?", pg, texto -> {
                            op.plugin.mensagens().transmitir("menuop-anuncio", "mensagem", texto, "jogador", p.getName());
                            for (Player t : Bukkit.getOnlinePlayers()) {
                                Acoes.som(t, "block.note_block.pling", 1f, 1f);
                            }
                        });
                    }
                });
        m.item(11, Itens.criar(Material.OAK_SIGN, "<yellow><bold>Título na tela de todos",
                List.of("<gray>Mostra um texto grande no meio", "<gray>da tela de todo mundo.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual o título?", pg, texto -> {
                            for (Player t : Bukkit.getOnlinePlayers()) {
                                Acoes.titulo(t, "<gold><bold>" + texto, "<gray>— " + p.getName());
                                Acoes.som(t, "ui.toast.challenge_complete", 0.7f, 1.2f);
                            }
                        });
                    }
                });
        boolean whitelist = Bukkit.hasWhitelist();
        op.acao(m, 12, Itens.criar(whitelist ? Material.IRON_DOOR : Material.OAK_DOOR,
                "<red><bold>Manutenção " + (whitelist ? "<green>(LIGADA)" : "<gray>(desligada)"), List.of(
                        "<gray>Ligada: só entra quem está na lista", "<gray>branca (whitelist) e os OPs.",
                        "<gray>Quem já está online continua.")), alvo, true, whitelist ? "Manutenção desligada" : "Manutenção ligada",
                (p, c) -> Bukkit.setWhitelist(!Bukkit.hasWhitelist()), pg);
        m.item(13, Itens.criar(Material.PAPER, "<white><bold>Lista branca (whitelist)",
                List.of("<gray>Esquerdo: <green>adicionar um nick", "<gray>Direito: <red>remover um nick",
                        "<gray>Shift: <white>adicionar todos os online")), (p, c) -> {
                    if (!op.podeUsar(p, alvo)) {
                        return;
                    }
                    if (c.isShiftClick()) {
                        for (Player t : Bukkit.getOnlinePlayers()) {
                            MenuOp.console("whitelist add " + t.getName());
                        }
                        op.concluir(p, alvo, true, "Todos os online na whitelist", pg);
                        return;
                    }
                    boolean remover = c.isRightClick();
                    op.perguntar(p, alvo, remover ? "Qual nick remover da whitelist?" : "Qual nick adicionar na whitelist?", pg, texto -> {
                        String n = texto.split("\\s+")[0];
                        MenuOp.console("whitelist " + (remover ? "remove " : "add ") + n);
                        op.plugin.mensagens().enviar(p, "menuop-feito-geral", "acao", (remover ? "Removido da whitelist: " : "Adicionado na whitelist: ") + n);
                    });
                });
        op.acao(m, 14, Itens.criar(Material.CHEST, "<green><bold>Salvar tudo agora",
                List.of("<gray>Salva os mundos e os dados", "<gray>dos jogadores no disco.")), alvo, true, "Tudo salvo", (p, c) -> {
                    MenuOp.console("save-all");
                    op.plugin.jogadores().salvarAssincrono();
                }, pg);
        op.acao(m, 15, Itens.criar(Material.REDSTONE_TORCH, "<aqua><bold>Desempenho", desempenho()), alvo, true,
                "Desempenho atualizado", (p, c) -> { }, pg);
        boolean contando = desligando != null;
        op.acaoConfirmada(m, 16, Itens.criar(Material.REDSTONE_BLOCK, contando ? "<yellow><bold>Cancelar desligamento" : "<dark_red><bold>Desligar o servidor",
                List.of("<gray>Avisa todos e desliga em 10 segundos.", "<gray>Com AUTO_REINICIAR=sim no INICIAR.bat,",
                        "<gray>ele liga de novo sozinho.", "", "<red>Shift + clique " + (contando ? "para cancelar" : "para confirmar"))),
                alvo, true, contando ? "Desligamento cancelado" : "Desligando em 10 segundos", (p, c) -> desligar(p), pg);

        // Regras da Sobrevivência (salvas no config.yml)
        boolean pvp = op.plugin.getConfig().getBoolean("sobrevivencia.pvp", true);
        op.acao(m, 19, Itens.criar(Material.IRON_SWORD, "<red><bold>PvP na Sobrevivência " + ligado(pvp),
                List.of("<gray>Jogadores podem se atacar.")), alvo, true, "PvP alterado", (p, c) -> salvar("sobrevivencia.pvp", !pvp), pg);
        String dif = op.plugin.getConfig().getString("sobrevivencia.dificuldade", "NORMAL").toUpperCase(Locale.ROOT);
        op.acao(m, 20, Itens.criar(Material.CREEPER_HEAD, "<gold><bold>Dificuldade: <white>" + nomeDificuldade(dif),
                List.of("<gray>Clique para trocar:", "<gray>pacífico → fácil → normal → difícil")), alvo, true,
                "Dificuldade alterada", (p, c) -> {
                    Difficulty[] todas = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
                    int i = 2;
                    for (int k = 0; k < todas.length; k++) {
                        if (todas[k].name().equals(dif)) {
                            i = k;
                        }
                    }
                    salvar("sobrevivencia.dificuldade", todas[(i + 1) % todas.length].name());
                }, pg);
        regra(m, 21, Material.ENDER_CHEST, "<dark_purple><bold>Manter inventário ao morrer", GameRules.KEEP_INVENTORY, alvo);
        regra(m, 22, Material.ZOMBIE_HEAD, "<dark_green><bold>Monstros nascem", GameRules.SPAWN_MONSTERS, alvo);
        regra(m, 23, Material.TNT, "<red><bold>Creeper/Enderman mexem em blocos", GameRules.MOB_GRIEFING, alvo);
        int dormir = op.plugin.getConfig().getInt("sobrevivencia.porcentagem-para-dormir", 30);
        op.acao(m, 24, Itens.criar(Material.RED_BED, "<aqua><bold>Dormir para pular a noite: <white>" + dormir + "%",
                List.of("<gray>Porcentagem de jogadores dormindo.", "<gray>Clique: 0% → 30% → 50% → 100%")), alvo, true,
                "Regra de dormir alterada", (p, c) -> salvar("sobrevivencia.porcentagem-para-dormir",
                        dormir < 30 ? 30 : dormir < 50 ? 50 : dormir < 100 ? 100 : 0), pg);
        int borda = op.plugin.getConfig().getInt("sobrevivencia.borda-raio", 10000);
        op.acao(m, 25, Itens.criar(Material.STRUCTURE_VOID, "<white><bold>Borda do mundo: <white>" + Numeros.inteiro(borda) + " blocos",
                List.of("<gray>Raio a partir do spawn do survival.", "<gray>Esquerdo: <green>+1.000 <gray>| Direito: <red>-1.000")),
                alvo, true, "Borda alterada", (p, c) -> salvar("sobrevivencia.borda-raio",
                        Math.max(1000, borda + (c.isRightClick() ? -1000 : 1000))), pg);

        // Para todos os jogadores
        op.acao(m, 28, Itens.criar(Material.GOLDEN_APPLE, "<gold><bold>Curar todos", List.of("<gray>Vida e fome cheias para todos.")),
                alvo, true, "Todos curados", (p, c) -> Bukkit.getOnlinePlayers().forEach(OpJogador::curar), pg);
        op.acaoConfirmada(m, 29, Itens.criar(Material.ENDER_PEARL, "<aqua><bold>Trazer todos até você",
                List.of("<gray>Teleporta todo mundo para você.", "", "<red>Shift + clique para confirmar")), alvo, true,
                "Todos teleportados", (p, c) -> {
                    for (Player t : Bukkit.getOnlinePlayers()) {
                        if (t != p) {
                            t.teleport(p);
                        }
                    }
                }, pg);
        m.item(30, Itens.criar(Material.EMERALD, "<green><bold>Dinheiro para todos",
                List.of("<gray>Cada um recebe no mundo onde está.", "<gray>Digite o valor (ex.: 10k, 1m).")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Quanto dinheiro dar para cada jogador?", pg, texto -> {
                            Double v = MenuOp.numero(texto);
                            if (v == null || v <= 0) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                                return;
                            }
                            for (Player t : Bukkit.getOnlinePlayers()) {
                                op.plugin.economia().depositar(t, v);
                                op.plugin.mensagens().enviar(t, "menuop-presente", "presente", "$" + Numeros.abreviar(v));
                            }
                        });
                    }
                });
        m.item(31, Itens.criar(Material.AMETHYST_SHARD, "<light_purple><bold>Tokens para todos",
                List.of("<gray>Digite a quantidade.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Quantos tokens dar para cada jogador?", pg, texto -> {
                            Double v = MenuOp.numero(texto);
                            if (v == null || v <= 0) {
                                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                                return;
                            }
                            for (Player t : Bukkit.getOnlinePlayers()) {
                                op.plugin.jogadores().obter(t).adicionarTokens(v.longValue());
                                op.plugin.mensagens().enviar(t, "menuop-presente", "presente", Numeros.inteiro(v.longValue()) + " tokens");
                            }
                        });
                    }
                });
        op.acao(m, 32, Itens.criar(Material.EXPERIENCE_BOTTLE, "<gold><bold>Booster para todos",
                List.of("<gray>Venda +100% e tokens +100%", "<gray>por 30 minutos para todos online.")), alvo, true,
                "Boosters entregues", (p, c) -> {
                    long ate = System.currentTimeMillis() + 30 * 60_000L;
                    for (Player t : Bukkit.getOnlinePlayers()) {
                        DadosJogador d = op.plugin.jogadores().obter(t);
                        d.setBooster(1.0, ate);
                        d.setBoosterTokens(1.0, ate);
                        op.plugin.mensagens().enviar(t, "menuop-presente", "presente", "boosters +100% por 30 minutos");
                    }
                }, pg);
        op.acao(m, 33, Itens.criar(Material.FIREWORK_ROCKET, "<light_purple><bold>Festa para todos",
                List.of("<gray>Fogos e título de festa", "<gray>em todos os jogadores.")), alvo, true, "Festa feita", (p, c) -> {
                    for (Player t : Bukkit.getOnlinePlayers()) {
                        OpJogador.fogos(op.plugin, t);
                        Acoes.titulo(t, "<gradient:#FFD700:#FF8C00><bold>FESTA!</bold></gradient>", "<white>Oferecida por " + p.getName());
                    }
                }, pg);
        op.acaoConfirmada(m, 34, Itens.criar(Material.BEACON, "<yellow><bold>Mandar todos para o spawn",
                List.of("<gray>Todos voltam ao spawn do Rankup.", "", "<red>Shift + clique para confirmar")), alvo, true,
                "Todos no spawn", (p, c) -> Bukkit.getOnlinePlayers().forEach(t -> op.plugin.acoes().teleportarSpawn(t, true)), pg);

        // Informações
        m.item(37, Itens.criar(Material.PLAYER_HEAD, "<yellow><bold>Jogadores online: " + Bukkit.getOnlinePlayers().size(),
                List.of("<gray>Clique para escolher um jogador.")), (p, c) -> op.escolherJogador(p));
        op.acao(m, 38, Itens.criar(Material.BOOKSHELF, "<white><bold>Plugins instalados",
                List.of("<gray>" + Bukkit.getPluginManager().getPlugins().length + " plugins. Clique para ver a lista.")), alvo, true,
                "Lista mostrada", (p, c) -> {
                    p.closeInventory();
                    p.performCommand("bukkit:plugins");
                }, null);
        m.item(39, Itens.criar(Material.NAME_TAG, "<white><bold>Versão", List.of(
                "<gray>Servidor: <white>" + Bukkit.getName() + " " + Bukkit.getMinecraftVersion(),
                "<gray>RankupSupremo: <white>" + op.plugin.getPluginMeta().getVersion())));
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    private static String ligado(boolean v) {
        return v ? "<green>(ligado)" : "<red>(desligado)";
    }

    private static String nomeDificuldade(String d) {
        return switch (d) {
            case "PEACEFUL" -> "pacífico";
            case "EASY" -> "fácil";
            case "HARD" -> "difícil";
            default -> "normal";
        };
    }

    /** Muda uma opção da Sobrevivência no config.yml e aplica nos mundos. */
    private void salvar(String chave, Object valor) {
        op.plugin.getConfig().set(chave, valor);
        op.plugin.saveConfig();
        op.plugin.sobrevivencia().configurarTodos();
    }

    /** Liga/desliga uma regra (gamerule) nos três mundos da Sobrevivência; o mundo guarda a regra. */
    private void regra(Menu m, int slot, Material icone, String nome, GameRule<Boolean> regra, Player alvo) {
        World principal = op.plugin.sobrevivencia().mundo();
        boolean atual = principal != null && Boolean.TRUE.equals(principal.getGameRuleValue(regra));
        op.acao(m, slot, Itens.criar(icone, nome + " " + ligado(atual), List.of("<gray>Vale para o mundo Sobrevivência,", "<gray>Nether e End.")),
                alvo, true, "Regra alterada", (p, c) -> {
                    for (String n : op.plugin.sobrevivencia().nomesMundos()) {
                        World w = Bukkit.getWorld(n);
                        if (w != null) {
                            w.setGameRule(regra, !atual);
                        }
                    }
                }, this::abrir);
    }

    private static List<String> desempenho() {
        double[] tps = Bukkit.getTPS();
        Runtime rt = Runtime.getRuntime();
        long usada = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        double mspt = Bukkit.getAverageTickTime();
        return List.of(
                "<gray>TPS (1m/5m/15m): <white>" + String.format(Locale.ROOT, "%.1f / %.1f / %.1f", Math.min(20, tps[0]), Math.min(20, tps[1]), Math.min(20, tps[2])),
                "<gray>Tempo por tick: " + (mspt < 40 ? "<green>" : "<red>") + String.format(Locale.ROOT, "%.1f ms", mspt) + " <dark_gray>(até 50 é ok)",
                "<gray>Memória: <white>" + usada + " MB <gray>de <white>" + max + " MB",
                "<gray>Jogadores: <white>" + Bukkit.getOnlinePlayers().size(),
                "",
                "<yellow>Clique para atualizar");
    }

    private void desligar(Player admin) {
        if (desligando != null) {
            desligando.cancel();
            desligando = null;
            op.plugin.mensagens().transmitir("menuop-desligamento-cancelado");
            return;
        }
        final int[] segundos = {10};
        desligando = Bukkit.getScheduler().runTaskTimer(op.plugin, () -> {
            if (segundos[0] <= 0) {
                desligando.cancel();
                desligando = null;
                MenuOp.console("save-all");
                Bukkit.shutdown();
                return;
            }
            for (Player t : Bukkit.getOnlinePlayers()) {
                Acoes.titulo(t, "<red><bold>Servidor desligando", "<white>em " + segundos[0] + " segundos");
                Acoes.som(t, "block.note_block.hat", 1f, 1f);
            }
            if (segundos[0] == 10 || segundos[0] <= 3) {
                op.plugin.mensagens().transmitir("menuop-desligando", "segundos", String.valueOf(segundos[0]), "jogador", admin.getName());
            }
            segundos[0]--;
        }, 0L, 20L);
    }
}
