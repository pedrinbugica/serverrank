package br.rankupsupremo.menu;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.util.Itens;
import br.rankupsupremo.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/**
 * Páginas "Jogador" e "Moderação" do /menuop.
 */
final class OpJogador {

    private static final PotionEffectType[] EFEITOS_OP = {
            PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE, PotionEffectType.REGENERATION,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.NIGHT_VISION, PotionEffectType.WATER_BREATHING,
            PotionEffectType.HASTE, PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST};

    private final MenuOp op;

    OpJogador(MenuOp op) {
        this.op = op;
    }

    static void curar(Player p) {
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(vida != null ? vida.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
    }

    static void fogos(org.bukkit.plugin.Plugin plugin, Player p) {
        Color[] cores = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA};
        for (int i = 0; i < 3; i++) {
            Firework f = (Firework) p.getWorld().spawnEntity(p.getLocation().add(i - 1, 1, 0), EntityType.FIREWORK_ROCKET);
            var meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(cores[(int) (Math.random() * cores.length)]).withFade(Color.WHITE).trail(true).flicker(true).build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
            f.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, FerramentasOp.FOGOS_SEGUROS),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    // ------------------------------------------------------------------ JOGADOR

    void abrirJogador(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <green>Jogador <dark_gray>» <black>" + alvo.getName()));
        MenuOp.Pagina pg = this::abrirJogador;

        op.acao(m, 10, Itens.criar(Material.GOLDEN_APPLE, "<gold><bold>Curar e alimentar",
                List.of("<gray>Vida e fome cheias, sem fogo.")), alvo, false, "Jogador curado", (p, c) -> curar(alvo), pg);
        op.acao(m, 11, Itens.criar(Material.GRASS_BLOCK, "<green><bold>Modo de jogo <gray>(" + alvo.getGameMode().name().toLowerCase(Locale.ROOT) + ")",
                List.of("<gray>Esquerdo: <white>próximo modo", "<gray>  (sobrevivência → criativo → aventura → espectador)",
                        "<gray>Direito: <white>volta para sobrevivência")), alvo, false, "Modo de jogo alterado", (p, c) -> {
                    if (c.isRightClick()) {
                        alvo.setGameMode(GameMode.SURVIVAL);
                        return;
                    }
                    alvo.setGameMode(switch (alvo.getGameMode()) {
                        case SURVIVAL -> GameMode.CREATIVE;
                        case CREATIVE -> GameMode.ADVENTURE;
                        case ADVENTURE -> GameMode.SPECTATOR;
                        case SPECTATOR -> GameMode.SURVIVAL;
                    });
                }, pg);
        op.acao(m, 12, Itens.criar(Material.FEATHER, "<aqua><bold>Voar " + (alvo.getAllowFlight() ? "<green>(ligado)" : "<red>(desligado)"),
                List.of("<gray>Liga/desliga o voo.")), alvo, false, "Voo alterado", (p, c) -> {
                    boolean voar = !alvo.getAllowFlight();
                    alvo.setAllowFlight(voar);
                    alvo.setFlying(voar);
                }, pg);
        op.acao(m, 13, Itens.criar(Material.TOTEM_OF_UNDYING, "<yellow><bold>Modo deus",
                List.of("<gray>Liga/desliga: não leva dano nenhum.")), alvo, false, "Modo deus alterado",
                (p, c) -> MenuOp.console("god " + alvo.getName()), pg);
        op.acao(m, 14, Itens.criar(Material.SUGAR, "<white><bold>Velocidade de andar/voar",
                List.of("<gray>Atual: <white>" + Math.round(alvo.getWalkSpeed() / 0.2f * 10) / 10.0 + "x",
                        "<gray>Esquerdo: <green>+1x <gray>(até 5x)", "<gray>Direito: <white>volta ao normal")), alvo, false,
                "Velocidade alterada", (p, c) -> {
                    if (c.isRightClick()) {
                        alvo.setWalkSpeed(0.2f);
                        alvo.setFlySpeed(0.1f);
                        return;
                    }
                    float vezes = Math.min(5f, Math.round(alvo.getWalkSpeed() / 0.2f) + 1f);
                    alvo.setWalkSpeed(Math.min(1f, 0.2f * vezes));
                    alvo.setFlySpeed(Math.min(1f, 0.1f * vezes));
                }, pg);
        op.acao(m, 15, Itens.criar(Material.GOLDEN_PICKAXE, "<yellow><bold>Pressa V (minerar rápido)",
                List.of("<gray>Liga/desliga o efeito Pressa V infinito.")), alvo, false, "Pressa alterada", (p, c) -> {
                    if (alvo.hasPotionEffect(PotionEffectType.HASTE)) {
                        alvo.removePotionEffect(PotionEffectType.HASTE);
                    } else {
                        alvo.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 4, false, false, true));
                    }
                }, pg);
        op.acao(m, 16, Itens.criar(Material.DRAGON_BREATH, "<light_purple><bold>Pacote de efeitos OP", List.of(
                "<gray>Força, resistência, regeneração, contra fogo,",
                "<gray>visão noturna, respirar na água, pressa,",
                "<gray>velocidade e super pulo (infinitos).",
                "<gray>Esquerdo: <green>dar <gray>| Direito: <red>tirar todos os efeitos")), alvo, false, "Efeitos alterados", (p, c) -> {
                    if (c.isRightClick()) {
                        for (PotionEffect e : alvo.getActivePotionEffects()) {
                            alvo.removePotionEffect(e.getType());
                        }
                        return;
                    }
                    for (PotionEffectType t : EFEITOS_OP) {
                        int nivel = t == PotionEffectType.RESISTANCE ? 3 : (t == PotionEffectType.HASTE ? 4 : 1);
                        alvo.addPotionEffect(new PotionEffect(t, PotionEffect.INFINITE_DURATION, nivel, false, false, true));
                    }
                }, pg);

        op.acao(m, 19, Itens.criar(Material.EXPERIENCE_BOTTLE, "<green><bold>Níveis de XP <gray>(" + alvo.getLevel() + ")",
                List.of("<gray>Esquerdo: <green>+30 níveis", "<gray>Shift: <green>+100 níveis", "<gray>Direito: <red>zerar")),
                alvo, false, "XP alterado", (p, c) -> {
                    if (c.isRightClick()) {
                        alvo.setLevel(0);
                        alvo.setExp(0);
                    } else {
                        alvo.giveExpLevels(c.isShiftClick() ? 100 : 30);
                    }
                }, pg);
        if (alvo != admin) {
            op.acao(m, 20, Itens.criar(Material.ENDER_PEARL, "<aqua><bold>Teleporte",
                    List.of("<gray>Esquerdo: <white>você vai até ele", "<gray>Direito: <white>traz ele até você")), alvo, false,
                    "Teleporte feito", (p, c) -> {
                        if (c.isRightClick()) {
                            alvo.teleport(p);
                        } else {
                            p.teleport(alvo);
                        }
                    }, null);
        }
        op.acao(m, 21, Itens.criar(Material.CHEST, "<gold><bold>Ver/editar inventário",
                List.of("<gray>Abre o inventário dele ao vivo.", "<gray>Dá para tirar e colocar itens.")), alvo, false,
                "Inventário aberto", (p, c) -> p.openInventory(alvo.getInventory()), null);
        op.acao(m, 22, Itens.criar(Material.ENDER_CHEST, "<dark_purple><bold>Ver/editar baú do End",
                List.of("<gray>Abre o baú do End dele.")), alvo, false, "Baú do End aberto",
                (p, c) -> p.openInventory(alvo.getEnderChest()), null);
        op.acaoConfirmada(m, 23, Itens.criar(Material.LAVA_BUCKET, "<red><bold>Limpar inventário",
                List.of("<gray>Apaga todos os itens do inventário", "<gray>e da armadura dele.", "", "<red>Shift + clique para confirmar")),
                alvo, false, "Inventário limpo", (p, c) -> alvo.getInventory().clear(), pg);
        op.acao(m, 24, Itens.criar(Material.COMPASS, "<aqua><bold>Mandar para um mundo",
                List.of("<gray>Esquerdo: <white>spawn do Rankup", "<gray>Direito: <white>Sobrevivência")), alvo, false,
                "Jogador teleportado", (p, c) -> {
                    if (c.isRightClick()) {
                        op.plugin.sobrevivencia().teleportar(alvo);
                    } else {
                        op.plugin.acoes().teleportarSpawn(alvo, true);
                    }
                }, pg);
        op.acaoConfirmada(m, 25, Itens.criar(Material.WITHER_SKELETON_SKULL, "<dark_red><bold>Matar",
                List.of("<gray>Mata o jogador na hora.", "", "<red>Shift + clique para confirmar")), alvo, false,
                "Jogador morto", (p, c) -> alvo.setHealth(0), pg);

        op.acao(m, 28, Itens.criar(Material.LIGHTNING_ROD, "<yellow><bold>Raio (só efeito)",
                List.of("<gray>Cai um raio nele, sem dano.")), alvo, false, "Raio lançado",
                (p, c) -> alvo.getWorld().strikeLightningEffect(alvo.getLocation()), pg);
        op.acao(m, 29, Itens.criar(Material.FIREWORK_ROCKET, "<gold><bold>Fogos de artifício",
                List.of("<gray>Solta fogos em cima dele.")), alvo, false, "Fogos lançados", (p, c) -> fogos(op.plugin, alvo), pg);
        m.item(30, Itens.criar(Material.WRITABLE_BOOK, "<white><bold>Fazer ele falar",
                List.of("<gray>O jogador manda no chat o que você", "<gray>digitar (ou um /comando).")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "O que " + alvo.getName() + " deve dizer?", this::abrirJogador, texto -> {
                            if (texto.startsWith("/")) {
                                alvo.performCommand(texto.substring(1));
                            } else {
                                alvo.chat(texto);
                            }
                        });
                    }
                });
        m.item(31, Itens.criar(Material.NAME_TAG, "<yellow><bold>Apelido (nick)",
                List.of("<gray>Esquerdo: <white>escolher apelido", "<gray>Direito: <white>tirar apelido",
                        "<gray>Aceita cores: &a &l &#FFAA00")), (p, c) -> {
                    if (!op.podeUsar(p, alvo)) {
                        return;
                    }
                    if (c.isRightClick()) {
                        MenuOp.console("nick " + alvo.getName() + " off");
                        op.concluir(p, alvo, false, "Apelido removido", this::abrirJogador);
                        return;
                    }
                    op.perguntar(p, alvo, "Qual o novo apelido de " + alvo.getName() + "?", this::abrirJogador,
                            texto -> MenuOp.console("nick " + alvo.getName() + " " + texto.replace(' ', '_')));
                });
        m.item(32, Itens.criar(Material.PAPER, "<white><bold>Mandar mensagem como Servidor",
                List.of("<gray>Aparece como título na tela dele", "<gray>e no chat (só ele vê).")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual mensagem mandar para " + alvo.getName() + "?", this::abrirJogador, texto -> {
                            Acoes.titulo(alvo, "<gold><bold>SERVIDOR", "<white>" + texto);
                            op.plugin.mensagens().enviar(alvo, "menuop-mensagem-servidor", "mensagem", texto);
                            Acoes.som(alvo, "block.note_block.pling", 1f, 1.2f);
                        });
                    }
                });
        op.acao(m, 33, Itens.criar(Material.SPYGLASS, "<aqua><bold>Informações completas",
                List.of("<gray>Mostra tudo sobre ele no chat", "<gray>(/whois do Essentials).")), alvo, false,
                "Informações mostradas", (p, c) -> {
                    p.closeInventory();
                    p.performCommand("whois " + alvo.getName());
                }, null);
        op.acao(m, 34, Itens.criar(Material.CAKE, "<light_purple><bold>Festa!",
                List.of("<gray>Fogos, raio, som e título", "<gray>de parabéns para ele.")), alvo, false, "Festa feita", (p, c) -> {
                    fogos(op.plugin, alvo);
                    Acoes.titulo(alvo, "<gradient:#FFD700:#FF8C00><bold>PARABÉNS!</bold></gradient>", "<white>Presente do Dono!");
                    Acoes.som(alvo, "ui.toast.challenge_complete", 1f, 1f);
                }, pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }

    // ------------------------------------------------------------------ MODERAÇÃO

    void abrirModeracao(Player admin, Player alvo) {
        Menu m = new Menu(6, Texto.cor("<dark_red><bold>MENU OP</bold> <dark_gray>» <red>Moderação <dark_gray>» <black>" + alvo.getName()));
        MenuOp.Pagina pg = this::abrirModeracao;
        String nome = alvo.getName();

        m.item(10, Itens.criar(Material.OAK_DOOR, "<gold><bold>Expulsar (kick)",
                List.of("<gray>Esquerdo: <white>expulsar agora", "<gray>Direito: <white>escrever o motivo")), (p, c) -> {
                    if (!op.podeUsar(p, alvo)) {
                        return;
                    }
                    if (c.isRightClick()) {
                        op.perguntar(p, alvo, "Motivo da expulsão de " + nome + "?", null, texto -> {
                            MenuOp.console("kick " + nome + " " + texto);
                            op.plugin.mensagens().enviar(p, "menuop-feito", "acao", "Expulso", "jogador", nome);
                        });
                    } else {
                        MenuOp.console("kick " + nome + " Expulso pela staff");
                        p.closeInventory();
                        op.plugin.mensagens().enviar(p, "menuop-feito", "acao", "Expulso", "jogador", nome);
                    }
                });
        op.acao(m, 11, Itens.criar(Material.STRING, "<yellow><bold>Silenciar / tirar silêncio",
                List.of("<gray>Esquerdo: <white>10 minutos", "<gray>Direito: <white>1 hora",
                        "<gray>Shift: <white>para sempre", "<gray>Clicar de novo tira o silêncio.")), alvo, false,
                "Silêncio alterado", (p, c) -> {
                    String tempo = c.isShiftClick() ? "" : (c.isRightClick() ? " 1h" : " 10m");
                    MenuOp.console("mute " + nome + tempo);
                }, pg);
        op.acaoConfirmada(m, 12, Itens.criar(Material.CLOCK, "<red><bold>Banir temporário",
                List.of("<gray>Shift + esquerdo: <white>1 dia", "<gray>Shift + direito: <white>7 dias")), alvo, false,
                "Banido temporariamente", (p, c) -> MenuOp.console("tempban " + nome + (c.isRightClick() ? " 7d" : " 1d") + " Banido pela staff"),
                null);
        op.acaoConfirmada(m, 13, Itens.criar(Material.ANVIL, "<dark_red><bold>Banir para sempre",
                List.of("<gray>O jogador não entra mais.", "", "<red>Shift + clique para confirmar")), alvo, false,
                "Banido", (p, c) -> {
                    p.closeInventory();
                    MenuOp.console("ban " + nome + " Banido pela staff");
                }, null);
        m.item(14, Itens.criar(Material.LIME_DYE, "<green><bold>Desbanir alguém",
                List.of("<gray>Digite o nick no chat.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual nick desbanir?", pg, texto -> {
                            String n = texto.split("\\s+")[0];
                            MenuOp.console("unban " + n);
                            MenuOp.console("pardon " + n);
                            op.plugin.mensagens().enviar(p, "menuop-feito-geral", "acao", "Desbanido: " + n);
                        });
                    }
                });
        boolean congelado = op.ferramentas.congelado(alvo);
        op.acao(m, 15, Itens.criar(Material.PACKED_ICE, "<aqua><bold>Congelar " + (congelado ? "<green>(congelado)" : "<gray>(livre)"),
                List.of("<gray>O jogador não consegue andar nem", "<gray>usar comandos (só /msg e /helpop).")), alvo, false,
                congelado ? "Jogador descongelado" : "Jogador congelado", (p, c) -> {
                    boolean agora = op.ferramentas.alternarCongelado(alvo);
                    Acoes.titulo(alvo, agora ? "<aqua><bold>CONGELADO" : "<green><bold>LIVRE",
                            agora ? "<gray>Aguarde a staff" : "<gray>Você pode andar de novo");
                }, pg);
        m.item(16, Itens.criar(Material.BELL, "<gold><bold>Dar um aviso",
                List.of("<gray>Mostra um aviso grande na tela", "<gray>dele com o texto que você digitar.")), (p, c) -> {
                    if (op.podeUsar(p, alvo)) {
                        op.perguntar(p, alvo, "Qual o aviso para " + nome + "?", pg, texto -> {
                            Acoes.titulo(alvo, "<red><bold>⚠ AVISO DA STAFF", "<white>" + texto);
                            op.plugin.mensagens().enviar(alvo, "menuop-aviso", "mensagem", texto);
                            Acoes.som(alvo, "block.anvil.land", 0.8f, 1f);
                        });
                    }
                });

        if (alvo != admin) {
            op.acao(m, 19, Itens.criar(Material.ENDER_EYE, "<gray><bold>Espiar (modo espectador)",
                    List.of("<gray>Esquerdo: <white>você vira espectador e vai até ele",
                            "<gray>Direito: <white>volta ao normal no spawn")), alvo, true, "Espiando", (p, c) -> {
                        if (c.isRightClick()) {
                            p.setGameMode(GameMode.SURVIVAL);
                            op.plugin.acoes().teleportarSpawn(p, false);
                        } else {
                            p.setGameMode(GameMode.SPECTATOR);
                            p.teleport(alvo);
                        }
                    }, null);
        }
        op.acao(m, 20, Itens.criar(Material.CHEST, "<gold><bold>Ver/editar inventário",
                List.of("<gray>Abre o inventário dele ao vivo.")), alvo, false, "Inventário aberto",
                (p, c) -> p.openInventory(alvo.getInventory()), null);
        op.acao(m, 21, Itens.criar(Material.ENDER_CHEST, "<dark_purple><bold>Ver/editar baú do End",
                List.of("<gray>Abre o baú do End dele.")), alvo, false, "Baú do End aberto",
                (p, c) -> p.openInventory(alvo.getEnderChest()), null);
        op.acao(m, 22, Itens.criar(Material.BOOK, "<white><bold>Lista de banidos",
                List.of("<gray>Mostra no chat quem está banido.")), alvo, true, "Lista mostrada", (p, c) -> {
                    p.closeInventory();
                    p.performCommand("minecraft:banlist");
                }, null);
        op.acao(m, 23, Itens.criar(Material.SPONGE, "<aqua><bold>Limpar o chat",
                List.of("<gray>Apaga o chat de todos os jogadores.")), alvo, true, "Chat limpo", (p, c) -> {
                    for (Player t : Bukkit.getOnlinePlayers()) {
                        for (int i = 0; i < 100; i++) {
                            t.sendMessage(" ");
                        }
                    }
                    op.plugin.mensagens().transmitir("menuop-chat-limpo", "jogador", p.getName());
                }, pg);
        op.acao(m, 24, Itens.criar(Material.IRON_SWORD, "<red><bold>Tirar do combate e do PvP",
                List.of("<gray>Cura, tira o fogo e manda para", "<gray>o spawn (resolver brigas).")), alvo, false,
                "Jogador resgatado", (p, c) -> {
                    curar(alvo);
                    op.plugin.acoes().teleportarSpawn(alvo, true);
                }, pg);
        op.rodape(m, alvo);
        m.abrir(admin);
    }
}
