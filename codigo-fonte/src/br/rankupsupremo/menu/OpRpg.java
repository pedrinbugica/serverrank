package br.rankupsupremo.menu;

import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Texto;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Página "RPG, Reinos e NPCs" do /menuop: tudo do SMP RPG (SurvivalRPG) e dos NPCs/reinos (NpcVivos) num lugar só.
 * Os botões chamam os comandos de administração desses plugins (/rpgadmin, /reinoadmin, /npcv, /povo), então
 * funcionam mesmo que eles mudem por dentro. As ações de personagem valem para o jogador escolhido.
 */
final class OpRpg {

    private static final String[] CLASSES = {"GUERREIRO", "ARQUEIRO", "MAGO", "CLERIGO", "LADINO"};
    private static final Material[] ICONES_CLASSE = {Material.IRON_SWORD, Material.BOW, Material.BLAZE_ROD, Material.GOLDEN_AXE,
            Material.IRON_SWORD};

    private final MenuOp op;

    OpRpg(MenuOp op) {
        this.op = op;
    }

    private boolean temRpg() {
        return Bukkit.getPluginManager().isPluginEnabled("SurvivalRPG");
    }

    private boolean temNpcs() {
        return Bukkit.getPluginManager().isPluginEnabled("NpcVivos");
    }

    private String ph(Player alvo, String texto) {
        if (!op.plugin.temPlaceholderAPI()) {
            return "?";
        }
        String r = PlaceholderAPI.setPlaceholders(alvo, texto);
        return r.contains("%") ? "?" : r;
    }

    void abrir(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <gold>RPG, Reinos e NPCs"));
        MenuOp.Pagina pg = this::abrir;
        String n = alvo.getName();
        if (!temRpg() || !temNpcs()) {
            m.item(4, Itens.criar(Material.BARRIER, "<red><bold>Plugin desligado", List.of(
                    "<gray>SurvivalRPG: " + (temRpg() ? "<green>ligado" : "<red>desligado"),
                    "<gray>NpcVivos: " + (temNpcs() ? "<green>ligado" : "<red>desligado"))));
        }

        // ---------------- Personagem do jogador escolhido
        m.item(10, Itens.criar(Material.PLAYER_HEAD, "<gold><bold>Personagem de " + n, List.of(
                "<gray>Classe: <white>" + ph(alvo, "%rpg_classe%"),
                "<gray>Nível: <yellow>" + ph(alvo, "%rpg_nivel%") + " <gray>(" + ph(alvo, "%rpg_xp_porcento%") + "%)",
                "<gray>Pontos livres: <white>" + ph(alvo, "%rpg_pontos%"),
                "<gray>Região: <white>" + ph(alvo, "%rpg_regiao%"),
                "<gray>Facção/Reino: <white>" + ph(alvo, "%npcvivos_faccao%"),
                "<gray>Grupo: <white>" + ph(alvo, "%rpg_grupo%"))));
        m.item(11, Itens.criar(Material.EXPERIENCE_BOTTLE, "<green><bold>Definir nível", List.of("<gray>Digite o nível (1 a 50) no chat.")),
                (p, c) -> numero(p, alvo, "Nível de " + n + "? (1 a 50)", v -> MenuOp.console("rpgadmin nivel " + n + " " + v)));
        m.item(12, Itens.criar(Material.LAPIS_LAZULI, "<aqua><bold>Dar XP do RPG", List.of("<gray>Digite a quantidade no chat.")),
                (p, c) -> numero(p, alvo, "Quanto XP dar para " + n + "?", v -> MenuOp.console("rpgadmin xp " + n + " " + v)));
        m.item(13, Itens.criar(Material.NETHER_STAR, "<light_purple><bold>Definir pontos de atributo", List.of("<gray>Digite a quantidade no chat.")),
                (p, c) -> numero(p, alvo, "Pontos livres de " + n + "?", v -> MenuOp.console("rpgadmin pontos " + n + " " + v)));
        m.item(14, Itens.criar(Material.ARMOR_STAND, "<yellow><bold>Escolher a classe", List.of("<gray>Sem custo e sem espera.")),
                (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        classes(p, alvo);
                    }
                });
        op.acao(m, 15, Itens.criar(Material.DIAMOND_SWORD, "<gold><bold>Item de RPG", List.of(
                "<gray>Esquerdo: <gold>lendário <gray>do nível dele",
                "<gray>Direito: <light_purple>épico <gray>do nível dele",
                "<gray>Shift: <gold>lendário nível 50")), alvo, false, "Item de RPG entregue", (p, c) -> {
                    String nivel = c.isShiftClick() ? "50" : ph(alvo, "%rpg_nivel%").replaceAll("\\D", "");
                    if (nivel.isEmpty()) {
                        nivel = "10";
                    }
                    MenuOp.console("rpgadmin item " + n + " " + nivel + " " + (c.isRightClick() && !c.isShiftClick() ? "EPICO" : "LENDARIO"));
                }, pg);
        op.acaoConfirmada(m, 16, Itens.criar(Material.TNT_MINECART, "<red><bold>Resetar personagem", List.of(
                "<gray>Classe, nível, pontos e lugares", "<gray>descobertos voltam do zero.", "", "<red>Shift + clique para confirmar")),
                alvo, false, "Personagem resetado", (p, c) -> MenuOp.console("rpgadmin resetar " + n), pg);

        // ---------------- Mundo do RPG (você)
        op.acao(m, 19, Itens.criar(Material.BELL, "<gold><bold>Lobby do RPG", List.of("<gray>Vai para a Cidadela dos Heróis.")), alvo, true,
                "Teleportado", (p, c) -> p.performCommand("rpglobby"), null);
        op.acao(m, 20, Itens.criar(Material.BEACON, "<yellow><bold>Capital Vila Aurora", List.of("<gray>Vai para a capital do RPG.")), alvo, true,
                "Teleportado", (p, c) -> p.performCommand("rpgadmin capital ir"), null);
        m.item(21, Itens.criar(Material.FILLED_MAP, "<green><bold>Lugares do mapa", List.of("<gray>Vilas, castelos, masmorras...",
                "<gray>com viagem e direção (/lugares).", "<gray>Shift: lista com coordenadas no chat.")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                p.closeInventory();
                p.performCommand(c.isShiftClick() ? "rpgadmin lugar lista" : "lugares");
            }
        });
        op.acao(m, 22, Itens.criar(Material.BRICKS, "<aqua><bold>Espalhar lugares que faltam", List.of(
                "<gray>Coloca as vilas, tavernas, masmorras,", "<gray>ruínas e castelos que faltam no mapa",
                "<gray>(config do SurvivalRPG, lugares.planos).", "<gray>Leva alguns minutos.")), alvo, true, "Colocando lugares",
                (p, c) -> p.performCommand("rpgadmin lugares popular"), pg);
        op.acao(m, 23, Itens.criar(Material.GOLDEN_HELMET, "<gold><bold>Construir castelos dos reinos", List.of(
                "<gray>Constrói os castelos que ainda", "<gray>não existem (um por reino).")), alvo, true, "Construindo castelos",
                (p, c) -> p.performCommand("rpgadmin reinos construir"), pg);
        op.acaoConfirmada(m, 24, Itens.criar(Material.LODESTONE, "<white><bold>Reconstruir o lobby do RPG", List.of(
                "<gray>Constrói a Cidadela de novo", "<gray>(desfaz mudanças feitas lá).", "", "<red>Shift + clique para confirmar")),
                alvo, true, "Lobby do RPG reconstruído", (p, c) -> p.performCommand("rpgadmin lobby reconstruir"), pg);
        op.acao(m, 25, Itens.criar(Material.SKELETON_SKULL, "<dark_red><bold>Masmorras", List.of(
                "<gray>Esquerdo: <white>construir as que faltam", "<gray>Direito: <white>reconstruir todas",
                "<gray>Shift: <white>abrir o menu das masmorras")), alvo, true, "Masmorras", (p, c) -> {
                    if (c.isShiftClick()) {
                        p.closeInventory();
                        p.performCommand("masmorra");
                    } else {
                        p.performCommand(c.isRightClick() ? "rpgadmin masmorra reconstruir" : "rpgadmin masmorra construir");
                    }
                }, null);

        // ---------------- Reinos e guerras
        m.item(28, Itens.criar(Material.YELLOW_BANNER, "<gold><bold>Reinos", List.of("<gray>Abre o menu dos reinos", "<gray>(como os jogadores veem).")),
                (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        p.closeInventory();
                        p.performCommand("reino");
                    }
                });
        m.item(29, Itens.criar(Material.IRON_SWORD, "<red><bold>Declarar guerra", List.of("<gray>Escolha os dois reinos.")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Quais reinos entram em guerra? (ex.: valdoria corvanor)", pg,
                        t -> MenuOp.console("reinoadmin guerra " + limpar(t)));
            }
        });
        m.item(30, Itens.criar(Material.WHITE_BANNER, "<white><bold>Fazer as pazes", List.of("<gray>Termina a guerra entre dois reinos.")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Quais reinos fazem as pazes? (ex.: valdoria corvanor)", pg,
                        t -> MenuOp.console("reinoadmin paz " + limpar(t)));
            }
        });
        m.item(31, Itens.criar(Material.TARGET, "<red><bold>Mandar um exército", List.of(
                "<gray>Digite: <white>reino alvo [soldados]", "<gray>ex.: <white>corvanor castelo_valdoria 12",
                "<gray>Os alvos estão na lista (botão ao lado).")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Exército: reino alvo [soldados]", pg, t -> MenuOp.console("reinoadmin ataque " + limpar(t)));
            }
        });
        op.acao(m, 32, Itens.criar(Material.SHIELD, "<aqua><bold>Completar guarnições", List.of(
                "<gray>Coloca os soldados que faltam", "<gray>nos castelos e vilas carregados.")), alvo, true, "Guarnições completas",
                (p, c) -> p.performCommand("reinoadmin guarnicao"), pg);
        op.acao(m, 33, Itens.criar(Material.WRITABLE_BOOK, "<white><bold>Castelos, vilas e guerras", List.of(
                "<gray>Lista no chat: dono, coordenadas,", "<gray>placar e exércitos em marcha.")), alvo, true, "Lista enviada",
                (p, c) -> {
                    p.closeInventory();
                    p.performCommand("reinoadmin lista");
                }, null);
        m.item(34, Itens.criar(Material.GOAT_HORN, "<dark_red><bold>Invasão de facção", List.of(
                "<gray>Ondas de soldados atacam a vila", "<gray>de uma facção (NpcVivos).", "<gray>Digite: <white>atacante defensor")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Invasão: facção atacante e defensora", pg, t -> MenuOp.console("npcv invasao " + limpar(t)));
            }
        });

        // ---------------- NPCs e moradores (onde você está)
        op.acao(m, 37, Itens.criar(Material.VILLAGER_SPAWN_EGG, "<green><bold>Gerar 5 moradores aqui", List.of(
                "<gray>Pessoas com nome, profissão, roupa,", "<gray>personalidade e vida (em volta de você).")), alvo, true,
                "Moradores criados", (p, c) -> p.performCommand("povo gerar 5"), pg);
        m.item(38, Itens.criar(Material.EMERALD, "<green><bold>Gerar moradores (escolher)", List.of(
                "<gray>Digite: <white>quantidade [profissão]", "<gray>ex.: <white>3 librarian",
                "<gray>farmer, fisherman, shepherd, fletcher, librarian,", "<gray>cartographer, cleric, armorer, weaponsmith,",
                "<gray>toolsmith, butcher, leatherworker, mason")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Quantos moradores? (e a profissão, se quiser)", pg, t -> p.performCommand("povo gerar " + limpar(t)));
            }
        });
        op.acao(m, 39, Itens.criar(Material.NAME_TAG, "<yellow><bold>Quem é este morador?", List.of("<gray>Info do morador mais perto de você.")),
                alvo, true, "Info enviada", (p, c) -> p.performCommand("povo info"), null);
        op.acao(m, 40, Itens.criar(Material.POPPY, "<light_purple><bold>Amizade máxima", List.of(
                "<gray>O morador mais perto vira", "<gray>seu melhor amigo (100).")), alvo, true, "Amizade definida",
                (p, c) -> p.performCommand("povo amizade 100"), pg);
        m.item(41, Itens.criar(iconeNpc(), "<aqua><bold>Criar NPC aqui", List.of(
                "<gray>Digite: <white>papel id nome", "<gray>papéis: comerciante, ferreiro, guia,",
                "<gray>missionario, guarda, mercenario, duelista, boss")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Novo NPC: papel id nome", null, t -> p.performCommand("npcv criar " + t.trim()));
            }
        });
        m.item(42, Itens.criar(Material.WRITABLE_BOOK, "<aqua><bold>Editar o NPC mais perto", List.of("<gray>Abre o editor do NpcVivos.")),
                (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        p.closeInventory();
                        p.performCommand("npcv editar");
                    }
                });
        op.acao(m, 43, Itens.criar(Material.COMPARATOR, "<white><bold>Recarregar RPG e NPCs", List.of(
                "<gray>Relê as configs do SurvivalRPG,", "<gray>NpcVivos, povo.yml e reinos.yml.")), alvo, true, "RPG e NPCs recarregados",
                (p, c) -> {
                    MenuOp.console("rpgadmin reload");
                    MenuOp.console("npcv reload");
                    p.performCommand("povo recarregar");
                }, pg);

        // ---------------- Reputação e itens do jogador escolhido
        m.item(45, Itens.criar(Material.GOLDEN_APPLE, "<gold><bold>Reputação de " + n, List.of(
                "<gray>Digite: <white>facção valor", "<gray>ex.: <white>valdoria 800 <gray>(herói)", "<gray>ou <white>corvanor -600 <gray>(inimigo)")),
                (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Reputação: facção valor", pg, t -> MenuOp.console("npcv reputacao " + n + " " + limpar(t)));
                    }
                });
        m.item(46, Itens.criar(Material.RED_BANNER, "<red><bold>Colocar " + n + " num reino", List.of(
                "<gray>Digite o id do reino/facção", "<gray>(ou <white>nenhuma<gray> para tirar).")), (p, c) -> {
            if (op.podeUsar(p, alvo)) {
                op.perguntar(p, alvo, "Reino/facção de " + n + "?", pg, t -> MenuOp.console("npcv membro " + n + " " + limpar(t)));
            }
        });
        op.acao(m, 47, Itens.criar(Material.AMETHYST_SHARD, "<light_purple><bold>Pedras de aprimoramento", List.of(
                "<gray>Esquerdo: <white>5 lendárias", "<gray>Direito: <white>5 pergaminhos de proteção")), alvo, false,
                "Pedras entregues", (p, c) -> MenuOp.console("npcv dar " + n + " " + (c.isRightClick() ? "pergaminho" : "lendaria") + " 5"), pg);
        op.acao(m, 51, Itens.criar(Material.ANVIL, "<gold><bold>Arma/armadura +10", List.of(
                "<gray>Deixa o item da SUA mão no +10", "<gray>(lendário).")), alvo, true, "Item aprimorado",
                (p, c) -> p.performCommand("npcv aprimorar 10 lendario"), pg);
        op.acao(m, 52, Itens.criar(Material.SPYGLASS, "<white><bold>Situação do RPG", List.of("<gray>Capital, lugares, masmorras", "<gray>e NPCs (no chat).")),
                alvo, true, "Situação enviada", (p, c) -> {
                    p.closeInventory();
                    p.performCommand("rpgadmin status");
                    p.performCommand("reino guerras");
                }, null);
        m.item(53, Itens.criar(Material.OAK_SIGN, "<green><bold>Marcar vila aqui", List.of(
                "<gray>Transforma o lugar onde você está", "<gray>numa vila de reino (raio 48).", "<gray>Digite o nome da vila.")),
                (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Nome da vila?", pg, t -> p.performCommand("reinoadmin vila 48 " + t.trim()));
                    }
                });
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    private void classes(Player admin, Player alvo) {
        Menu m = new Menu(3, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <gold>Classe de " + alvo.getName()));
        for (int i = 0; i < CLASSES.length; i++) {
            String c = CLASSES[i];
            String nome = c.charAt(0) + c.substring(1).toLowerCase(Locale.ROOT);
            op.acao(m, 11 + i, Itens.criar(ICONES_CLASSE[i], "<yellow><bold>" + nome, List.of("<gray>Clique para virar " + nome + ".")), alvo, false,
                    "Classe definida: " + nome, (p, cl) -> MenuOp.console("rpgadmin classe " + alvo.getName() + " " + c), this::abrir);
        }
        m.item(22, Itens.criar(Material.ARROW, "<gray>« Voltar", List.of()), (p, c) -> abrir(p, alvo));
        m.preencherVazios(Itens.vidro(Material.RED_STAINED_GLASS_PANE));
        m.abrir(admin);
    }

    private void numero(Player p, Player alvo, String pergunta, java.util.function.Consumer<String> uso) {
        if (!op.podeUsar(p, alvo)) {
            return;
        }
        op.perguntar(p, alvo, pergunta, this::abrir, t -> {
            Double v = MenuOp.numero(t);
            if (v == null) {
                op.plugin.mensagens().enviar(p, "admin-numero-invalido");
                return;
            }
            uso.accept(String.valueOf(Math.round(v)));
        });
    }

    /** Só letras, números, _ - e espaço: o texto do chat vira parte de um comando. */
    private static String limpar(String t) {
        return t.trim().replaceAll("[^\\p{L}\\p{N}_\\- ]", "").replaceAll("\\s+", " ");
    }

    private static Material iconeNpc() {
        Material m = Material.matchMaterial("MANNEQUIN_SPAWN_EGG");
        return m != null ? m : Material.ARMOR_STAND;
    }
}
