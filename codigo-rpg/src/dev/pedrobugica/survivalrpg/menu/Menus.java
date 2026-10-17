package dev.pedrobugica.survivalrpg.menu;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.grupo.GrupoService;
import dev.pedrobugica.survivalrpg.habilidade.Habilidade;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.mundo.Regiao;
import dev.pedrobugica.survivalrpg.util.Menu;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Os menus do jogador: perfil (/rpg), classes (/classe), atributos (/atributos) e habilidades (/poderes). */
public final class Menus {

    private final SurvivalRpgPlugin plugin;

    public Menus(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    private static void clique(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    // ------------------------------------------------------------------ PERFIL

    public void perfil(Player p) {
        PerfilRpg r = plugin.perfis().perfil(p);
        Menu m = new Menu(5, Texto.cor(plugin.mensagens().bruta("menu-perfil-titulo")));
        double precisa = plugin.perfis().xpPara(r.nivel());
        boolean maximo = r.nivel() >= plugin.perfis().nivelMaximo();
        Regiao reg = plugin.regioes().regiao(p.getLocation());
        List<String> resumo = new ArrayList<>(List.of(
                "<gray>Classe: " + (r.classe() == null ? "<red>nenhuma <gray>(/classe)" : r.classe().formatado()),
                "<gray>Nível: <yellow>" + r.nivel() + (maximo ? " <gold>(máximo)" : ""),
                maximo ? "<gray>XP: <gold>—" : "<gray>XP: <green>" + Texto.numero(r.xp()) + "<dark_gray>/<green>" + Texto.numero(precisa)
                        + " <dark_gray>(" + (int) (100 * r.xp() / Math.max(1, precisa)) + "%)",
                barra(maximo ? 1 : r.xp() / Math.max(1, precisa)),
                "",
                "<gray>Mana: <aqua>" + (int) r.mana() + "<dark_aqua>/" + (int) plugin.perfis().manaMaxima(p),
                "<gray>Pontos para distribuir: <yellow>" + r.pontosLivres(),
                "<gray>Região: " + (reg == null ? "<dark_gray>fora do RPG" : reg.nome() + " <dark_gray>(" + reg.faixa() + ")"),
                "<gray>Lugares descobertos: <white>" + r.regioesDescobertas().size()));
        ItemStack cabeca = Menu.criar(Material.PLAYER_HEAD, "<gold><bold>" + p.getName(), resumo);
        cabeca.editMeta(SkullMeta.class, s -> s.setOwningPlayer(p));
        m.item(4, cabeca, null);

        m.item(19, Menu.criar(r.classe() == null ? Material.NETHER_STAR : r.classe().icone(), "<yellow><bold>Classe",
                List.of(r.classe() == null ? "<red>Você ainda não escolheu!" : "<gray>Atual: " + r.classe().formatado(),
                        "", "<yellow>Clique para ver as classes")), (q, t) -> {
            clique(q);
            classes(q);
        });
        m.item(21, Menu.criar(Material.ENCHANTED_BOOK, "<yellow><bold>Atributos",
                List.of("<gray>Pontos livres: <yellow>" + r.pontosLivres(), "", "<yellow>Clique para distribuir")), (q, t) -> {
            clique(q);
            atributos(q);
        });
        m.item(23, Menu.criar(Material.BLAZE_POWDER, "<yellow><bold>Habilidades",
                List.of("<gray>Com a arma da classe na mão:", "<white>F <gray>liga o modo habilidade,", "<white>1, 2 e 3 <gray>usam as habilidades.",
                        "", "<yellow>Clique para ver as suas")), (q, t) -> {
            clique(q);
            habilidades(q);
        });
        GrupoService.Grupo g = plugin.grupos().grupo(p);
        m.item(25, Menu.criar(Material.TOTEM_OF_UNDYING, "<yellow><bold>Grupo",
                List.of(g == null ? "<gray>Você está sozinho." : "<gray>Membros: <white>" + g.membros().size(),
                        "<gray>/grupo convidar <jogador>", "<gray>/g <mensagem> <dark_gray>(chat do grupo)",
                        "", "<gray>Em grupo: <green>sem fogo amigo <gray>e <green>+XP")), (q, t) -> {
            clique(q);
            q.closeInventory();
            plugin.grupos().lista(q);
        });
        m.item(29, Menu.criar(r.numerosDeDano() ? Material.LIME_DYE : Material.GRAY_DYE, "<yellow><bold>Números de dano",
                List.of(r.numerosDeDano() ? "<green>Ligados" : "<red>Desligados", "", "<yellow>Clique para trocar")), (q, t) -> {
            clique(q);
            r.setNumerosDeDano(!r.numerosDeDano());
            perfil(q);
        });
        m.item(31, Menu.criar(Material.FILLED_MAP, "<yellow><bold>Lugares do mundo",
                List.of("<gray>Cidades, vilas, castelos e masmorras", "<gray>que você já descobriu.", "", "<yellow>Clique para ver")), (q, t) -> {
            clique(q);
            plugin.lugares().menu(q, 0);
        });
        m.item(33, Menu.criar(Material.IRON_BARS, "<yellow><bold>Masmorras",
                List.of("<gray>Entre com o seu grupo, limpe as salas", "<gray>e derrote o chefe para ganhar itens raros.",
                        "<gray>A entrada fica na capital.", "", "<yellow>Clique para ver")), (q, t) -> {
            clique(q);
            plugin.masmorras().menu(q, false);
        });
        m.item(40, Menu.criar(Material.BOOK, "<yellow><bold>Como jogar", List.of("<yellow>Clique para ver a ajuda")), (q, t) -> {
            clique(q);
            q.closeInventory();
            plugin.mensagens().enviarLista(q, "ajuda");
        });
        m.preencher(Material.BLACK_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    private static String barra(double f) {
        int cheio = (int) Math.round(Math.max(0, Math.min(1, f)) * 20);
        return "<green>" + "|".repeat(cheio) + "<dark_gray>" + "|".repeat(20 - cheio);
    }

    // ------------------------------------------------------------------ CLASSES

    public void classes(Player p) {
        PerfilRpg r = plugin.perfis().perfil(p);
        Menu m = new Menu(3, Texto.cor(plugin.mensagens().bruta("menu-classes-titulo")));
        int slot = 11;
        for (Classe c : Classe.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + c.papel());
            lore.add("");
            lore.add("<gray>Atributo principal: " + c.principal().cor() + c.principal().nome());
            lore.add("<dark_gray>(+1 por nível, automático)");
            lore.add("");
            lore.add("<gray>Habilidades:");
            for (Habilidade h : Habilidade.daClasse(c)) {
                lore.add(" <white>" + h.nome() + " <dark_gray>(nível " + h.nivelMinimo() + ")");
                lore.add("   <gray>" + h.descricao());
            }
            lore.add("");
            if (c == r.classe()) {
                lore.add("<green>✔ Sua classe atual");
            } else if (r.classe() == null) {
                lore.add("<yellow>Clique para escolher (grátis)");
            } else {
                lore.add("<gray>Trocar custa <gold>$" + Texto.dinheiro(plugin.getConfig().getDouble("classes.troca.custo", 5000)));
                lore.add("<gray>e devolve todos os seus pontos.");
                lore.add("<yellow>Clique para trocar");
            }
            ItemStack it = Menu.criar(c.icone(), c.formatado() + (c == r.classe() ? " <green>✔" : ""), lore);
            if (c == r.classe()) {
                it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            m.item(slot++, it, (q, t) -> {
                clique(q);
                if (c == r.classe()) {
                    return;
                }
                if (r.classe() == null) {
                    q.closeInventory();
                    plugin.escolherClasse(q, c, false);
                } else {
                    confirmarTroca(q, c);
                }
            });
        }
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    private void confirmarTroca(Player p, Classe c) {
        Menu m = new Menu(3, Texto.cor(plugin.mensagens().bruta("menu-troca-titulo"), "classe", c.nome()));
        double custo = plugin.getConfig().getDouble("classes.troca.custo", 5000);
        m.item(11, Menu.criar(Material.LIME_CONCRETE, "<green><bold>Trocar para " + c.formatado(),
                List.of("<gray>Custa <gold>$" + Texto.dinheiro(custo), "<gray>Seus pontos de atributo voltam", "<gray>para você distribuir de novo.")),
                (q, t) -> {
                    q.closeInventory();
                    plugin.escolherClasse(q, c, false);
                });
        m.item(15, Menu.criar(Material.RED_CONCRETE, "<red><bold>Cancelar", List.of()), (q, t) -> {
            clique(q);
            classes(q);
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    // ------------------------------------------------------------------ ATRIBUTOS

    public void atributos(Player p) {
        PerfilRpg r = plugin.perfis().perfil(p);
        Menu m = new Menu(4, Texto.cor(plugin.mensagens().bruta("menu-atributos-titulo"), "pontos", String.valueOf(r.pontosLivres())));
        int[] slots = {10, 11, 12, 14, 15, 16};
        Atributo[] todos = Atributo.values();
        for (int i = 0; i < todos.length && i < slots.length; i++) {
            Atributo a = todos[i];
            int base = r.atributo(a);
            int itens = plugin.itens().somaAtributo(p, a);
            List<String> lore = new ArrayList<>(List.of(
                    "<gray>" + a.efeito(),
                    "",
                    "<gray>Seus pontos: <white>" + base,
                    "<gray>Dos itens: <white>+" + itens,
                    "<gray>Total: " + a.cor() + (base + itens)));
            if (r.classe() != null && r.classe().principal() == a) {
                lore.add("<dark_gray>(atributo principal da sua classe)");
            }
            lore.add("");
            lore.add(r.pontosLivres() > 0 ? "<yellow>Clique: +1 <dark_gray>• <yellow>Shift+clique: +5" : "<red>Sem pontos livres");
            ItemStack it = Menu.criar(a.icone(), a.cor() + "<bold>" + a.nome() + " <gray>(" + (base + itens) + ")", lore);
            it.setAmount(Math.max(1, Math.min(64, base + itens)));
            m.item(slots[i], it, (q, t) -> {
                int quanto = t.isShiftClick() ? 5 : 1;
                quanto = Math.min(quanto, r.pontosLivres());
                if (quanto <= 0) {
                    plugin.mensagens().enviar(q, "atributo-sem-pontos");
                    q.playSound(q.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                    return;
                }
                r.setPontosLivres(r.pontosLivres() - quanto);
                r.setAtributo(a, r.atributo(a) + quanto);
                q.playSound(q.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                if (a == Atributo.VITALIDADE) {
                    plugin.perfis().aplicarVida(q);
                }
                atributos(q);
            });
        }
        m.item(22, Menu.criar(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Pontos livres: " + r.pontosLivres(),
                List.of("<gray>Você ganha <white>" + plugin.getConfig().getInt("pontos-por-nivel", 3) + " pontos <gray>a cada nível.")), null);
        double custo = plugin.getConfig().getDouble("atributos.custo-resetar", 2000);
        m.item(31, Menu.criar(Material.BARRIER, "<red><bold>Redistribuir pontos",
                List.of("<gray>Devolve todos os pontos que você", "<gray>colocou para distribuir de novo.", "",
                        "<gray>Custa <gold>$" + Texto.dinheiro(custo), "<yellow>Shift+clique para confirmar")), (q, t) -> {
            if (!t.isShiftClick()) {
                plugin.mensagens().enviar(q, "atributos-confirmar");
                return;
            }
            q.closeInventory();
            plugin.redistribuir(q, true);
        });
        m.item(27, Menu.criar(Material.ARROW, "<gray>Voltar", List.of()), (q, t) -> {
            clique(q);
            perfil(q);
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    // ------------------------------------------------------------------ HABILIDADES

    public void habilidades(Player p) {
        PerfilRpg r = plugin.perfis().perfil(p);
        Menu m = new Menu(3, Texto.cor(plugin.mensagens().bruta("menu-habilidades-titulo")));
        if (r.classe() == null) {
            m.item(13, Menu.criar(Material.NETHER_STAR, "<yellow>Escolha uma classe primeiro", List.of("<yellow>Clique para escolher")),
                    (q, t) -> classes(q));
        } else {
            List<Habilidade> l = Habilidade.daClasse(r.classe());
            for (int i = 0; i < l.size(); i++) {
                Habilidade h = l.get(i);
                boolean liberada = r.nivel() >= h.nivelMinimo();
                m.item(11 + i * 2, Menu.criar(liberada ? Material.ENCHANTED_BOOK : Material.BOOK,
                        (liberada ? "<green>" : "<gray>") + "<bold>[" + (i + 1) + "] " + h.nome(),
                        List.of("<gray>" + h.descricao(), "",
                                "<gray>Mana: <aqua>" + h.mana(),
                                "<gray>Recarga: <white>" + Texto.numero(h.recarga()) + "s",
                                "<gray>Nível: " + (liberada ? "<green>" : "<red>") + h.nivelMinimo(),
                                "", liberada ? "<green>Liberada" : "<red>Libera no nível " + h.nivelMinimo())), null);
            }
        }
        m.item(22, Menu.criar(Material.OAK_SIGN, "<yellow><bold>Como usar",
                List.of("<gray>1. Segure a arma da sua classe", "<gray>2. Aperte <white>F <gray>(trocar de mão)",
                        "<gray>3. Aperte <white>1<gray>, <white>2 <gray>ou <white>3", "<gray>4. <white>F <gray>de novo para sair",
                        "", "<dark_gray>Agachado + F troca de mão normalmente.")), null);
        m.item(18, Menu.criar(Material.ARROW, "<gray>Voltar", List.of()), (q, t) -> {
            clique(q);
            perfil(q);
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    /** Usado pelos menus de outras partes (lugares, masmorras) para o botão "voltar". */
    public void voltar(Player p, ClickType t) {
        clique(p);
        perfil(p);
    }
}
