package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.core.NpcManager;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.menu.Menu;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /npcv editar (fase 8): um menu para a staff mexer no NPC sem decorar comandos. Papéis, facção, skin,
 * conversa, item na mão, preços da loja, mover, teleportar e remover. Tudo é salvo em npcs/<id>.yml na hora.
 */
public final class EditorNpc {

    private final NpcVivosPlugin plugin;

    public EditorNpc(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean pode(Player p, String acao) {
        if (p.hasPermission("npcvivos.admin." + acao)) {
            return true;
        }
        plugin.mensagens().enviar(p, "sem-permissao");
        return false;
    }

    private void salvar(NpcVivo npc, boolean redesenhar) {
        plugin.npcs().salvar(npc);
        if (redesenhar) {
            plugin.npcs().spawnar(npc);
        }
    }

    private static void clique(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    /** Escolhe o próximo (botão esquerdo) ou o anterior (direito) de uma lista circular. */
    private static <T> T girar(List<T> lista, T atual, ClickType c) {
        if (lista.isEmpty()) {
            return atual;
        }
        int i = lista.indexOf(atual);
        int passo = c.isRightClick() ? -1 : 1;
        int n = ((i < 0 ? (passo > 0 ? -1 : 0) : i) + passo + lista.size()) % lista.size();
        return lista.get(n);
    }

    // ------------------------------------------------------------------ MENU PRINCIPAL

    public void abrir(Player p, NpcVivo npc) {
        Menu m = new Menu(6, Texto.cor(Texto.substituir(plugin.mensagens().bruta("editor-titulo"), "npc", Texto.semCor(npc.nome()))));
        Faccao f = plugin.faccoes().obter(npc.faccao());
        List<String> papeis = new ArrayList<>();
        npc.papeis().forEach(x -> papeis.add(x.nome()));
        Location l = npc.local();
        m.item(4, Menu.criar(Material.NAME_TAG, npc.nome(), List.of(
                "<gray>Id: <white>" + npc.id(),
                "<gray>Local: <white>" + npc.mundo() + " " + (l == null ? "?" : l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()),
                "<gray>Papéis: <white>" + (papeis.isEmpty() ? "nenhum" : String.join(", ", papeis)),
                "<gray>Facção: " + (f == null ? "<white>nenhuma" : f.nome()),
                "<gray>Combate: <white>" + plugin.comportamento().estadoDe(npc),
                "",
                "<dark_gray>Nome: /npcv nome " + npc.id() + " <novo nome>")), null);

        // Papéis (liga/desliga)
        int slot = 9;
        for (Papel pa : Papel.values()) {
            if (!pa.pronto() || pa == Papel.INVASOR || slot > 17) {
                continue;
            }
            boolean tem = npc.tem(pa);
            m.item(slot++, Menu.criar(tem ? Material.LIME_DYE : Material.GRAY_DYE, (tem ? "<green>✔ " : "<gray>✖ ") + pa.nome(),
                    List.of(tem ? "<gray>Clique para tirar este papel" : "<gray>Clique para dar este papel")), (pl, c) -> {
                if (!pode(pl, "editar")) {
                    return;
                }
                if (!npc.papeis().remove(pa)) {
                    npc.papeis().add(pa);
                }
                salvar(npc, true);
                plugin.comportamento().prepararTodos(); // quem passou a lutar ganha cérebro (e quem parou perde)
                clique(pl);
                abrir(pl, npc);
            });
        }

        // Facção
        List<String> faccoes = new ArrayList<>();
        faccoes.add(null);
        plugin.faccoes().todas().forEach(x -> faccoes.add(x.id()));
        m.item(19, Menu.criar(Material.WHITE_BANNER, "<yellow>Facção: " + (f == null ? "<white>nenhuma" : f.nome()),
                List.of("<gray>Esquerdo: próxima | Direito: anterior", "<dark_gray>Guardas protegem a própria facção")), (pl, c) -> {
            if (!pode(pl, "editar")) {
                return;
            }
            npc.setFaccao(girar(faccoes, npc.faccao(), c));
            salvar(npc, true);
            clique(pl);
            abrir(pl, npc);
        });

        // Skin
        List<String> skins = new ArrayList<>(NpcManager.SKINS_PADRAO);
        skins.sort(null);
        m.item(20, Menu.criar(Material.LEATHER_CHESTPLATE, "<yellow>Skin: <white>" + npc.skin(),
                List.of("<gray>Esquerdo: próxima | Direito: anterior", "<dark_gray>Skin de jogador: /npcv skin " + npc.id() + " jogador:Nick")),
                (pl, c) -> {
                    if (!pode(pl, "editar")) {
                        return;
                    }
                    npc.setSkin(girar(skins, npc.skin().toLowerCase(Locale.ROOT), c));
                    salvar(npc, true);
                    clique(pl);
                    abrir(pl, npc);
                });

        // Item na mão
        m.item(21, Menu.criar(npc.itemNaMao() == null ? Material.STICK : npc.itemNaMao(), "<yellow>Item na mão: <white>"
                + (npc.itemNaMao() == null ? "nada" : npc.itemNaMao().name().toLowerCase(Locale.ROOT)),
                List.of("<gray>Clique: o NPC segura o item da SUA mão", "<gray>Shift + clique: mão vazia")), (pl, c) -> {
            if (!pode(pl, "editar")) {
                return;
            }
            Material mao = pl.getInventory().getItemInMainHand().getType();
            npc.setItemNaMao(c.isShiftClick() || mao.isAir() ? null : mao);
            salvar(npc, true);
            clique(pl);
            abrir(pl, npc);
        });

        // Conversa em árvore
        List<String> conversas = new ArrayList<>();
        conversas.add(null);
        conversas.addAll(plugin.dialogo().ids());
        m.item(22, Menu.criar(Material.BOOK, "<yellow>Conversa: <white>" + (npc.arvore() == null ? "simples (sem opções)" : npc.arvore()),
                List.of("<gray>Esquerdo: próxima | Direito: anterior", "<dark_gray>Arquivos em plugins/NpcVivos/dialogos/")), (pl, c) -> {
            if (!pode(pl, "editar")) {
                return;
            }
            npc.setArvore(girar(conversas, npc.arvore(), c));
            salvar(npc, false);
            clique(pl);
            abrir(pl, npc);
        });

        // Falas
        m.item(23, Menu.criar(Material.WRITABLE_BOOK, "<yellow>Falas", List.of(
                "<gray>Saudação: <white>" + npc.saudacao().size() + " <gray>| Retorno: <white>" + npc.retorno().size()
                        + " <gray>| Dicas: <white>" + npc.falas().size(),
                "",
                "<dark_gray>/npcv fala " + npc.id() + " adicionar|saudacao|retorno <texto>",
                "<red>Shift + direito: apagar todas as falas")), (pl, c) -> {
            if (c == ClickType.SHIFT_RIGHT && pode(pl, "editar")) {
                npc.falas().clear();
                npc.saudacao().clear();
                npc.retorno().clear();
                salvar(npc, false);
                plugin.mensagens().enviar(pl, "npc-editado", "npc", npc.nome());
                abrir(pl, npc);
            }
        });

        // Rotina (andar e falar sozinho)
        var rot = npc.rotina() == null ? dev.pedrobugica.npcvivos.core.Rotina.padrao() : npc.rotina();
        m.item(24, Menu.criar(Material.LEATHER_BOOTS, "<yellow>Rotina: <white>" + rot.tipo().name().toLowerCase(Locale.ROOT)
                + (rot.tipo() == dev.pedrobugica.npcvivos.core.Rotina.Tipo.PASSEAR ? " (raio " + Texto.dinheiro(rot.raio()) + ")" : ""), List.of(
                "<gray>Esquerdo: parado / passear / patrulha",
                "<gray>Direito: raio do passeio +2 (até 16)",
                "<gray>Shift + direito: volta para casa à noite: " + (rot.dormir() ? "<green>sim" : "<red>não"),
                "<gray>Falas soltas: <white>" + rot.falas().size() + " <gray>| Pontos da ronda: <white>" + rot.pontos().size(),
                "<dark_gray>Pontos da ronda: /npcv rotina " + npc.id() + " ponto")), (pl, c) -> {
            if (!pode(pl, "editar")) {
                return;
            }
            var r0 = npc.rotina() == null ? dev.pedrobugica.npcvivos.core.Rotina.padrao() : npc.rotina();
            var tipos = dev.pedrobugica.npcvivos.core.Rotina.Tipo.values();
            var novo = switch (c) {
                case SHIFT_RIGHT -> new dev.pedrobugica.npcvivos.core.Rotina(r0.tipo(), r0.raio(), r0.pontos(), r0.falas(), !r0.dormir());
                case RIGHT -> r0.comRaio(r0.raio() + 2 > 16 ? 2 : r0.raio() + 2);
                default -> r0.comTipo(tipos[(r0.tipo().ordinal() + 1) % tipos.length]);
            };
            npc.setRotina(novo);
            salvar(npc, false);
            if (plugin.vida() != null) {
                plugin.vida().esquecer(npc.id());
            }
            clique(pl);
            abrir(pl, npc);
        });

        // Loja
        m.item(28, Menu.criar(Material.CHEST, "<green>Loja: o que ele VENDE <gray>(" + npc.compra().size() + ")",
                List.of("<gray>Preços, retirar e adicionar itens")), (pl, c) -> abrirLoja(pl, npc, true));
        m.item(29, Menu.criar(Material.BARREL, "<gold>Loja: o que ele COMPRA <gray>(" + npc.venda().size() + ")",
                List.of("<gray>Preços, retirar e adicionar itens")), (pl, c) -> abrirLoja(pl, npc, false));
        m.item(31, Menu.criar(Material.GOLD_INGOT, "<yellow>Mercado (procura e oferta)",
                List.of("<gray>Clique para ver no chat como os", "<gray>preços desta loja estão agora")), (pl, c) -> {
            pl.closeInventory();
            plugin.mensagens().enviar(pl, "mercado-cabecalho", "npc", npc.nome(),
                    "estado", plugin.mercado().ativo() ? "<green>ligado" : "<red>desligado");
            for (String linha : plugin.mercado().resumo(npc)) {
                pl.sendMessage(Texto.cor(linha));
            }
        });

        // Ações
        m.item(45, Menu.criar(Material.ENDER_PEARL, "<aqua>Ir até o NPC", List.of()), (pl, c) -> {
            if (pode(pl, "editar") && npc.local() != null) {
                pl.closeInventory();
                pl.teleport(npc.local());
            }
        });
        m.item(46, Menu.criar(Material.LEAD, "<aqua>Trazer o NPC até você", List.of("<gray>Ele passa a morar aqui")), (pl, c) -> {
            if (!pode(pl, "editar")) {
                return;
            }
            Location aqui = pl.getLocation();
            npc.setLocal(aqui.getWorld().getName(), aqui.getX(), aqui.getY(), aqui.getZ(), aqui.getYaw());
            plugin.comportamento().marcarPosto(npc);
            salvar(npc, true);
            plugin.mensagens().enviar(pl, "npc-editado", "npc", npc.nome());
            abrir(pl, npc);
        });
        m.item(49, Menu.criar(Material.BARRIER, "<red>Fechar", List.of()), (pl, c) -> pl.closeInventory());
        m.item(53, Menu.criar(Material.TNT, "<red><bold>Remover NPC", List.of("<gray>Pede confirmação")), (pl, c) -> {
            if (pode(pl, "remover")) {
                confirmarRemocao(pl, npc);
            }
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    // ------------------------------------------------------------------ LOJA

    private void abrirLoja(Player p, NpcVivo npc, boolean compra) {
        if (!pode(p, "loja")) {
            return;
        }
        List<ItemLoja> lista = compra ? npc.compra() : npc.venda();
        Menu m = new Menu(6, Texto.cor(Texto.substituir(plugin.mensagens().bruta(compra ? "editor-loja-vende" : "editor-loja-compra"),
                "npc", Texto.semCor(npc.nome()))));
        for (int i = 0; i < lista.size() && i < 45; i++) {
            ItemLoja it = lista.get(i);
            final int indice = i;
            ItemStack icone = it.lote();
            icone.setAmount(Math.max(1, Math.min(icone.getMaxStackSize(), it.quantidade())));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Lote: <white>" + it.quantidade() + " <gray>| Preço base: <green>$" + Texto.dinheiro(it.precoBase()));
            if (compra) {
                lore.add("<gray>Estoque: <white>" + (it.estoqueMax() <= 0 ? "infinito" : plugin.comercio().estoque(npc, it) + "/" + it.estoqueMax())
                        + (it.repoePorHora() > 0 ? " <gray>(repõe " + Texto.dinheiro(it.repoePorHora()) + "/h)" : ""));
                if (it.importadoDe() != null) {
                    lore.add("<gray>Importado de: <white>" + it.importadoDe());
                }
            } else {
                lore.add("<gray>Limite por dia: <white>" + (it.limiteDiario() <= 0 ? "sem limite" : it.limiteDiario()));
            }
            lore.add("");
            lore.add("<yellow>Esquerdo: <white>+10% <yellow>| Direito: <white>-10%");
            lore.add("<yellow>Q: <white>dobrar <yellow>| Ctrl+Q: <white>metade");
            lore.add("<red>Shift + direito: retirar da loja");
            icone.editMeta(meta -> meta.lore(Texto.lore(lore)));
            m.item(i, icone, (pl, c) -> {
                if (!pode(pl, "loja") || indice >= lista.size()) {
                    return;
                }
                ItemLoja atual = lista.get(indice);
                if (c == ClickType.SHIFT_RIGHT) {
                    lista.remove(indice);
                } else {
                    double fator = switch (c) {
                        case LEFT, SHIFT_LEFT -> 1.10;
                        case RIGHT -> 1 / 1.10;
                        case DROP -> 2.0;
                        case CONTROL_DROP -> 0.5;
                        default -> 1.0;
                    };
                    if (fator == 1.0) {
                        return;
                    }
                    double novo = Math.max(0.01, Math.round(atual.precoBase() * fator * 100) / 100.0);
                    lista.set(indice, new ItemLoja(atual.chave(), atual.item(), atual.quantidade(), novo, atual.estoqueMax(),
                            atual.repoePorHora(), atual.limiteDiario(), atual.importadoDe()));
                }
                salvar(npc, false);
                clique(pl);
                abrirLoja(pl, npc, compra);
            });
        }
        m.item(45, Menu.criar(Material.ARROW, "<yellow>Voltar", List.of()), (pl, c) -> abrir(pl, npc));
        m.item(49, Menu.criar(Material.EMERALD, "<green>Adicionar o item da sua mão", List.of(
                "<gray>A quantidade na mão vira o lote.",
                "<gray>Começa com $10 por unidade: ajuste",
                "<gray>com os cliques depois.",
                compra ? "<gray>Estoque 16, repõe 4 por hora." : "<gray>Compra até 256 por dia.")), (pl, c) -> {
            ItemStack mao = pl.getInventory().getItemInMainHand();
            if (mao.getType().isAir()) {
                plugin.mensagens().enviar(pl, "segure-item");
                return;
            }
            ItemStack modelo = mao.clone();
            int qtd = modelo.getAmount();
            modelo.setAmount(1);
            String chave = (compra ? "compra-" : "venda-") + lista.size();
            lista.add(new ItemLoja(chave, modelo, qtd, 10.0 * qtd, compra ? 16 : 0, compra ? 4 : 0, compra ? 0 : 256));
            if (!npc.tem(Papel.COMERCIANTE)) {
                npc.papeis().add(Papel.COMERCIANTE);
            }
            salvar(npc, false);
            plugin.mensagens().enviar(pl, compra ? "loja-item-compra" : "loja-item-venda", "npc", npc.nome(),
                    "quantidade", String.valueOf(qtd), "preco", Texto.dinheiro(10.0 * qtd));
            abrirLoja(pl, npc, compra);
        });
        m.item(53, Menu.criar(Material.BARRIER, "<red>Fechar", List.of()), (pl, c) -> pl.closeInventory());
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    // ------------------------------------------------------------------ REMOVER

    private void confirmarRemocao(Player p, NpcVivo npc) {
        Menu m = new Menu(3, Texto.cor(Texto.substituir(plugin.mensagens().bruta("editor-remover-titulo"), "npc", Texto.semCor(npc.nome()))));
        m.item(11, Menu.criar(Material.LIME_CONCRETE, "<green>Não, voltar", List.of()), (pl, c) -> abrir(pl, npc));
        m.item(15, Menu.criar(Material.RED_CONCRETE, "<red><bold>Sim, remover " + Texto.semCor(npc.nome()), List.of(
                "<gray>Apaga npcs/" + npc.id() + ".yml e a memória dele.", "<red>Não dá para desfazer.")), (pl, c) -> {
            if (!pode(pl, "remover")) {
                return;
            }
            pl.closeInventory();
            plugin.comportamento().esquecer(npc.id());
            plugin.npcs().remover(npc);
            plugin.mensagens().enviar(pl, "npc-removido", "id", npc.id());
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    /** Usado pelo /npcv editar sem id: o NPC mais perto (até 6 blocos). */
    public NpcVivo maisPerto(Player p) {
        NpcVivo melhor = null;
        double menor = 36;
        for (NpcVivo n : plugin.npcs().todos()) {
            Location l = n.local();
            if (n.temporario() || l == null || !l.getWorld().equals(p.getWorld())) {
                continue;
            }
            double d = l.distanceSquared(p.getLocation());
            if (d < menor) {
                menor = d;
                melhor = n;
            }
        }
        return melhor;
    }
}
