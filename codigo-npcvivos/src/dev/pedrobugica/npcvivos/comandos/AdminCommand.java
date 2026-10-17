package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comercio.ComercioService;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcManager;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.NivelReputacao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import dev.pedrobugica.npcvivos.upgrade.ItemAprimoravel;
import dev.pedrobugica.npcvivos.upgrade.Pedra;
import dev.pedrobugica.npcvivos.upgrade.Raridade;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /npcv - criar e editar NPCs sem programar. Tudo é salvo em plugins/NpcVivos/npcs/<id>.yml.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("criar", "editar", "remover", "lista", "info", "mover", "tp", "nome", "skin",
            "papel", "fala", "mao", "loja", "faccao", "relacao", "reputacao", "membro", "dar", "aprimorar", "combate", "rotina", "guerra", "invasao", "mercado", "pontuacao", "promocao", "reload");

    private final NpcVivosPlugin plugin;
    private final EditorNpc editor;

    public AdminCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.editor = new EditorNpc(plugin);
    }

    private boolean pode(CommandSender s, String acao) {
        if (s.hasPermission("npcvivos.admin." + acao)) {
            return true;
        }
        plugin.mensagens().enviar(s, "sem-permissao");
        return false;
    }

    private NpcVivo npc(CommandSender s, String id) {
        NpcVivo n = plugin.npcs().obter(id);
        if (n == null) {
            plugin.mensagens().enviar(s, "npc-nao-existe", "id", id);
        }
        return n;
    }

    private Faccao faccao(CommandSender s, String id) {
        Faccao f = plugin.faccoes().obter(id);
        if (f == null) {
            plugin.mensagens().enviar(s, "faccao-nao-existe", "id", id);
        }
        return f;
    }

    /** Jogador online ou que já entrou no servidor (sem consultar a Mojang). */
    private OfflinePlayer jogador(CommandSender s, String nome) {
        OfflinePlayer o = Bukkit.getPlayerExact(nome);
        if (o == null) {
            o = Bukkit.getOfflinePlayerIfCached(nome);
        }
        if (o == null) {
            plugin.mensagens().enviar(s, "jogador-nao-encontrado", "jogador", nome);
        }
        return o;
    }

    private static boolean nenhuma(String s) {
        return s.equalsIgnoreCase("nenhuma") || s.equalsIgnoreCase("nenhum") || s.equals("-");
    }

    private static String juntar(String[] a, int de) {
        return String.join(" ", Arrays.copyOfRange(a, de, a.length));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            ajuda(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        Player p = s instanceof Player pl ? pl : null;
        switch (sub) {
            case "criar" -> {
                if (!pode(s, "criar")) {
                    return true;
                }
                if (p == null || a.length < 3) {
                    uso(s, "/npcv criar <papel> <id> [nome...]");
                    return true;
                }
                Papel papel = Papel.buscar(a[1]);
                String id = a[2].toLowerCase(Locale.ROOT);
                if (papel == null) {
                    plugin.mensagens().enviar(s, "papel-invalido", "papeis", papeis());
                    return true;
                }
                if (!id.matches("[a-z0-9_]{2,32}")) {
                    plugin.mensagens().enviar(s, "id-invalido");
                    return true;
                }
                if (plugin.npcs().obter(id) != null) {
                    plugin.mensagens().enviar(s, "npc-ja-existe", "id", id);
                    return true;
                }
                String nome = a.length > 3 ? juntar(a, 3) : "<yellow>" + id;
                Location l = p.getLocation();
                NpcVivo n = plugin.npcs().criar(id, papel, nome, l);
                plugin.mensagens().enviar(s, "npc-criado", "npc", n.nome(), "id", id, "papel", papel.nome());
            }
            case "remover" -> {
                if (!pode(s, "remover") || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv remover <id>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    plugin.npcs().remover(n);
                    plugin.mensagens().enviar(s, "npc-removido", "id", n.id());
                }
            }
            case "lista" -> {
                if (!pode(s, "editar")) {
                    return true;
                }
                plugin.mensagens().enviar(s, "lista-cabecalho", "total", String.valueOf(plugin.npcs().todos().size()));
                for (NpcVivo n : plugin.npcs().todos()) {
                    plugin.mensagens().enviarSemPrefixo(s, "lista-linha", "id", n.id(), "npc", n.nome(), "mundo", n.mundo(),
                            "x", String.valueOf((int) n.x()), "y", String.valueOf((int) n.y()), "z", String.valueOf((int) n.z()));
                }
            }
            case "info" -> {
                if (!pode(s, "editar") || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv info <id>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    List<String> papeis = new ArrayList<>();
                    n.papeis().forEach(x -> papeis.add(x.nome() + (x.pronto() ? "" : " (em breve)")));
                    plugin.mensagens().enviarLista(s, "info", "id", n.id(), "npc", n.nome(), "skin", n.skin(),
                            "papeis", String.join(", ", papeis), "mundo", n.mundo(),
                            "local", (int) n.x() + ", " + (int) n.y() + ", " + (int) n.z(),
                            "compra", String.valueOf(n.compra().size()), "venda", String.valueOf(n.venda().size()),
                            "falas", String.valueOf(n.falas().size()));
                    // linha separada: quem já tem o mensagens.yml antigo também vê (chaves novas entram sozinhas)
                    plugin.mensagens().enviarSemPrefixo(s, "info-faccao", "faccao", nomeFaccao(n.faccao()),
                            "conversa", n.arvore() == null ? "-" : n.arvore());
                }
            }
            case "mover" -> {
                if (!pode(s, "editar") || p == null || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv mover <id>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    Location l = p.getLocation();
                    n.setLocal(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw());
                    salvarEMostrar(n);
                    plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
                }
            }
            case "tp" -> {
                if (!pode(s, "editar") || p == null || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv tp <id>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null && n.local() != null) {
                    p.teleport(n.local());
                }
            }
            case "nome" -> {
                if (!pode(s, "editar") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv nome <id> <nome...>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    n.setNome(juntar(a, 2));
                    salvarEMostrar(n);
                    plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
                }
            }
            case "skin" -> {
                if (!pode(s, "editar") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv skin <id> <steve|alex|ari|efe|kai|makena|noor|sunny|zuri|jogador:Nick>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    n.setSkin(a[2]);
                    salvarEMostrar(n);
                    plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
                }
            }
            case "mao" -> {
                if (!pode(s, "editar") || p == null || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv mao <id> (segurando o item; mão vazia tira)");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n != null) {
                    Material m = p.getInventory().getItemInMainHand().getType();
                    n.setItemNaMao(m.isAir() ? null : m);
                    salvarEMostrar(n);
                    plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
                }
            }
            case "papel" -> {
                if (!pode(s, "editar") || a.length < 4) {
                    if (a.length < 4) {
                        uso(s, "/npcv papel <id> <adicionar|remover> <papel>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                Papel papel = Papel.buscar(a[3]);
                if (n == null) {
                    return true;
                }
                if (papel == null) {
                    plugin.mensagens().enviar(s, "papel-invalido", "papeis", papeis());
                    return true;
                }
                if (a[2].toLowerCase(Locale.ROOT).startsWith("r")) {
                    n.papeis().remove(papel);
                } else {
                    n.papeis().add(papel);
                }
                salvarEMostrar(n);
                plugin.mensagens().enviar(s, papel.pronto() ? "npc-editado" : "papel-em-breve", "npc", n.nome(), "papel", papel.nome());
            }
            case "fala" -> {
                if (!pode(s, "editar") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv fala <id> <adicionar|saudacao|retorno|ambiente|limpar> [texto...]");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n == null) {
                    return true;
                }
                String tipo = a[2].toLowerCase(Locale.ROOT);
                String texto = a.length > 3 ? juntar(a, 3) : "";
                switch (tipo) {
                    case "limpar" -> {
                        n.falas().clear();
                        n.saudacao().clear();
                        n.retorno().clear();
                    }
                    case "saudacao" -> n.saudacao().add(texto);
                    case "ambiente" -> {
                        if (n.rotina() == null) {
                            n.setRotina(dev.pedrobugica.npcvivos.core.Rotina.padrao());
                        }
                        n.rotina().falas().add(texto);
                    }
                    case "retorno" -> n.retorno().add(texto);
                    default -> n.falas().add(texto);
                }
                plugin.npcs().salvar(n);
                plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
            }
            case "loja" -> loja(s, p, a);
            case "faccao" -> {
                if (!pode(s, "editar") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv faccao <npc> <facção|nenhuma>");
                    }
                    return true;
                }
                NpcVivo n = npc(s, a[1]);
                if (n == null) {
                    return true;
                }
                Faccao f = nenhuma(a[2]) ? null : faccao(s, a[2]);
                if (f == null && !nenhuma(a[2])) {
                    return true;
                }
                n.setFaccao(f == null ? null : f.id());
                salvarEMostrar(n);
                plugin.mensagens().enviar(s, "npc-faccao", "npc", n.nome(),
                        "faccao", f == null ? plugin.mensagens().bruta("faccoes-nenhuma") : f.nome());
            }
            case "relacao" -> {
                if (!pode(s, "faccao") || a.length < 4) {
                    if (a.length < 4) {
                        uso(s, "/npcv relacao <facção> <outra> <aliada|neutra|rival|guerra>");
                    }
                    return true;
                }
                Faccao fa = faccao(s, a[1]);
                Faccao fb = fa == null ? null : faccao(s, a[2]);
                if (fb == null) {
                    return true;
                }
                Relacao r = Relacao.buscar(a[3]);
                if (r == null || fa.id().equals(fb.id())) {
                    plugin.mensagens().enviar(s, "relacao-invalida");
                    return true;
                }
                var guerraAtual = plugin.guerras().entre(fa.id(), fb.id());
                if (r == Relacao.GUERRA) {
                    // guerra de verdade: prazo, placar e invasões (fase 7)
                    if (!plugin.guerras().iniciar(fa, fb, -1, plugin.mensagens().bruta("motivo-staff"))) {
                        plugin.mensagens().enviar(s, "guerra-ja-existe", "a", fa.nome(), "b", fb.nome());
                        return true;
                    }
                } else {
                    if (guerraAtual != null) {
                        plugin.guerras().terminar(guerraAtual); // acaba a guerra (quem tem mais pontos vence)
                    }
                    plugin.faccoes().mudarRelacao(fa, fb, r);
                }
                plugin.diplomacia().definirPelaRelacao(fa.id(), fb.id(), r);
                plugin.mensagens().enviar(s, "relacao-mudada", "a", fa.nome(), "b", fb.nome(), "relacao", r.cor() + r.nome());
            }
            case "editar" -> {
                if (!pode(s, "editar")) {
                    return true;
                }
                if (p == null) {
                    plugin.mensagens().enviar(s, "apenas-jogador");
                    return true;
                }
                NpcVivo n = a.length > 1 ? npc(s, a[1]) : editor.maisPerto(p);
                if (n == null) {
                    if (a.length <= 1) {
                        uso(s, "/npcv editar <id>   (ou chegue perto do NPC e use só /npcv editar)");
                    }
                    return true;
                }
                editor.abrir(p, n);
            }
            case "rotina" -> rotina(s, p, a);
            case "guerra" -> guerra(s, a);
            case "invasao" -> invasao(s, a);
            case "mercado" -> mercado(s, a);
            case "promocao" -> {
                if (!pode(s, "loja") || a.length < 4) {
                    if (a.length < 4) {
                        uso(s, "/npcv promocao <facção|todas> <desconto %> <minutos>");
                    }
                    return true;
                }
                Faccao f = a[1].equalsIgnoreCase("todas") ? null : faccao(s, a[1]);
                if (f == null && !a[1].equalsIgnoreCase("todas")) {
                    return true;
                }
                double pct;
                int min;
                try {
                    pct = Double.parseDouble(a[2].replace("%", "").replace(',', '.'));
                    min = Integer.parseInt(a[3]);
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return true;
                }
                plugin.mercado().iniciarPromocao(f == null ? "*" : f.id(), pct / 100.0, Math.max(1, min));
                for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
                    plugin.mensagens().enviar(o, "promocao-anuncio", "desconto", Texto.dinheiro(Math.min(90, pct)),
                            "lojas", f == null ? plugin.mensagens().bruta("promocao-todas") : f.nome(), "minutos", String.valueOf(min));
                }
            }
            case "pontuacao" -> {
                if (!pode(s, "faccao") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv pontuacao <facção> <outra> [valor | ~n]   (-100 a 100)");
                    }
                    return true;
                }
                Faccao fa = faccao(s, a[1]);
                Faccao fb = fa == null ? null : faccao(s, a[2]);
                if (fb == null || fa.id().equals(fb.id())) {
                    return true;
                }
                if (a.length >= 4) {
                    try {
                        boolean relativo = a[3].startsWith("~");
                        String num = relativo ? a[3].substring(1) : a[3];
                        double v = num.isEmpty() ? 0 : Double.parseDouble(num.replace(',', '.'));
                        if (relativo) {
                            plugin.diplomacia().alterar(fa.id(), fb.id(), v, plugin.mensagens().bruta("motivo-staff"));
                        } else {
                            plugin.diplomacia().definir(fa.id(), fb.id(), v);
                        }
                    } catch (NumberFormatException e) {
                        plugin.mensagens().enviar(s, "numero-invalido");
                        return true;
                    }
                }
                double pts = plugin.diplomacia().pontuacao(fa.id(), fb.id());
                Relacao atual = plugin.faccoes().relacao(fa.id(), fb.id());
                plugin.mensagens().enviar(s, "pontuacao-admin", "a", fa.nome(), "b", fb.nome(), "pontos", Texto.dinheiro(Math.round(pts * 10) / 10.0),
                        "relacao", atual.cor() + atual.nome());
            }
            case "reputacao" -> {
                if (!pode(s, "faccao") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv reputacao <jogador> <facção> [valor | ~n]   (ex.: 200, -600, ~50 soma, ~-50 tira)");
                    }
                    return true;
                }
                OfflinePlayer o = jogador(s, a[1]);
                Faccao f = o == null ? null : faccao(s, a[2]);
                if (f == null) {
                    return true;
                }
                int atual = plugin.faccoes().reputacao(o.getUniqueId(), f.id());
                if (a.length >= 4) {
                    try {
                        // ~n soma ao valor atual (como as coordenadas relativas do Minecraft); sem ~ define o valor
                        boolean relativo = a[3].startsWith("~");
                        String num = relativo ? a[3].substring(1) : a[3];
                        int v = num.isEmpty() ? 0 : Integer.parseInt(num.replace("+", ""));
                        atual = plugin.faccoes().definirReputacao(o.getUniqueId(), f, relativo ? atual + v : v);
                    } catch (NumberFormatException e) {
                        plugin.mensagens().enviar(s, "numero-invalido");
                        return true;
                    }
                }
                plugin.mensagens().enviar(s, "reputacao-admin", "jogador", String.valueOf(o.getName()), "faccao", f.nome(),
                        "reputacao", String.valueOf(atual), "nivel", NivelReputacao.de(atual).formatado());
            }
            case "membro" -> {
                if (!pode(s, "faccao") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv membro <jogador> <facção|nenhuma>");
                    }
                    return true;
                }
                OfflinePlayer o = jogador(s, a[1]);
                if (o == null) {
                    return true;
                }
                Faccao f = nenhuma(a[2]) ? null : faccao(s, a[2]);
                if (f == null && !nenhuma(a[2])) {
                    return true;
                }
                plugin.faccoes().definirMembro(o.getUniqueId(), f);
                plugin.mensagens().enviar(s, "membro-admin", "jogador", String.valueOf(o.getName()),
                        "faccao", f == null ? plugin.mensagens().bruta("faccoes-nenhuma") : f.nome());
            }
            case "dar" -> {
                if (!pode(s, "upgrade") || a.length < 3) {
                    if (a.length < 3) {
                        uso(s, "/npcv dar <jogador> <comum|rara|epica|lendaria|pergaminho> [quantidade]");
                    }
                    return true;
                }
                Player alvo = org.bukkit.Bukkit.getPlayerExact(a[1]);
                if (alvo == null) {
                    plugin.mensagens().enviar(s, "jogador-offline", "jogador", a[1]);
                    return true;
                }
                int qtd;
                try {
                    qtd = a.length > 3 ? Math.max(1, Math.min(2304, Integer.parseInt(a[3]))) : 1;
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return true;
                }
                ItemStack item;
                if (a[2].toLowerCase(Locale.ROOT).startsWith("pergaminho")) {
                    item = plugin.upgrades().itens().criarPergaminho(1);
                } else {
                    Pedra pedra = Pedra.buscar(a[2]);
                    if (pedra == null) {
                        uso(s, "/npcv dar <jogador> <comum|rara|epica|lendaria|pergaminho> [quantidade]");
                        return true;
                    }
                    item = plugin.upgrades().itens().criarPedra(pedra, 1);
                }
                String nomeItem = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(item.getItemMeta().customName());
                for (int resta = qtd; resta > 0; resta -= 64) {
                    ItemStack lote = item.clone();
                    lote.setAmount(Math.min(64, resta));
                    plugin.upgrades().dar(alvo, lote);
                }
                plugin.mensagens().enviar(s, "dar-admin", "jogador", alvo.getName(), "quantidade", String.valueOf(qtd), "item", nomeItem);
            }
            case "aprimorar" -> {
                if (!pode(s, "upgrade") || p == null || a.length < 2) {
                    if (a.length < 2) {
                        uso(s, "/npcv aprimorar <nível 0-" + plugin.upgrades().nivelMaximo() + "> [comum|raro|epico|lendario]");
                    }
                    return true;
                }
                ItemStack mao = p.getInventory().getItemInMainHand();
                if (ItemAprimoravel.tipo(mao) == null) {
                    plugin.mensagens().enviar(s, "upgrade-segure-item");
                    return true;
                }
                int nivel;
                try {
                    nivel = Math.max(0, Math.min(plugin.upgrades().nivelMaximo(), Integer.parseInt(a[1])));
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return true;
                }
                if (a.length > 2) {
                    Raridade r = Raridade.buscar(a[2]);
                    if (r == null) {
                        uso(s, "/npcv aprimorar <nível> [comum|raro|epico|lendario]");
                        return true;
                    }
                    plugin.upgrades().itens().definirRaridade(mao, r);
                }
                plugin.upgrades().itens().definir(mao, nivel);
                p.getInventory().setItemInMainHand(mao);
                plugin.mensagens().enviar(s, "aprimorar-admin", "nivel", String.valueOf(nivel),
                        "raridade", plugin.upgrades().itens().raridade(mao).formatado());
            }
            case "combate" -> combate(s, a);
            case "reload" -> {
                if (!pode(s, "reload")) {
                    return true;
                }
                plugin.recarregar();
                plugin.mensagens().enviar(s, "recarregado", "total", String.valueOf(plugin.npcs().todos().size()));
            }
            default -> ajuda(s);
        }
        return true;
    }

    /** /npcv rotina <id> <parado|passear|patrulha> [raio] | ponto | limpar-pontos | dormir */
    private void rotina(CommandSender s, Player p, String[] a) {
        if (!pode(s, "editar")) {
            return;
        }
        if (a.length < 3) {
            uso(s, "/npcv rotina <id> <parado|passear|patrulha> [raio] | ponto | limpar-pontos | dormir");
            return;
        }
        NpcVivo n = npc(s, a[1]);
        if (n == null) {
            return;
        }
        var r = n.rotina() == null ? dev.pedrobugica.npcvivos.core.Rotina.padrao() : n.rotina();
        String acao = a[2].toLowerCase(Locale.ROOT);
        switch (acao) {
            case "ponto" -> {
                if (p == null) {
                    plugin.mensagens().enviar(s, "apenas-jogador");
                    return;
                }
                Location l = p.getLocation();
                r.pontos().add(new org.bukkit.util.Vector(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                r = r.comTipo(dev.pedrobugica.npcvivos.core.Rotina.Tipo.PATRULHA);
            }
            case "limpar-pontos" -> r.pontos().clear();
            case "dormir" -> r = new dev.pedrobugica.npcvivos.core.Rotina(r.tipo(), r.raio(), r.pontos(), r.falas(), !r.dormir());
            default -> {
                var tipo = dev.pedrobugica.npcvivos.core.Rotina.Tipo.buscar(acao);
                if (tipo == null) {
                    uso(s, "/npcv rotina <id> <parado|passear|patrulha> [raio] | ponto | limpar-pontos | dormir");
                    return;
                }
                r = r.comTipo(tipo);
                if (a.length > 3) {
                    try {
                        r = r.comRaio(Math.max(1, Math.min(48, Double.parseDouble(a[3].replace(',', '.')))));
                    } catch (NumberFormatException e) {
                        plugin.mensagens().enviar(s, "numero-invalido");
                        return;
                    }
                }
            }
        }
        n.setRotina(r);
        plugin.npcs().salvar(n);
        if (plugin.vida() != null) {
            plugin.vida().esquecer(n.id());
        }
        plugin.mensagens().enviar(s, "rotina-definida", "npc", n.nome(), "tipo", r.tipo().name().toLowerCase(Locale.ROOT),
                "raio", Texto.dinheiro(r.raio()), "pontos", String.valueOf(r.pontos().size()), "dormir", r.dormir() ? "sim" : "não");
    }

    /** /npcv guerra iniciar <A> <B> [horas] | parar <A> <B> | lista */
    private void guerra(CommandSender s, String[] a) {
        if (!pode(s, "guerra")) {
            return;
        }
        String acao = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        if (acao.equals("iniciar") || acao.equals("parar")) {
            if (a.length < 4) {
                uso(s, "/npcv guerra " + acao + " <facção> <outra>" + (acao.equals("iniciar") ? " [horas]" : ""));
                return;
            }
            Faccao fa = faccao(s, a[2]);
            Faccao fb = fa == null ? null : faccao(s, a[3]);
            if (fb == null || fa.id().equals(fb.id())) {
                return;
            }
            if (acao.equals("iniciar")) {
                double horas = -1;
                if (a.length > 4) {
                    try {
                        horas = Double.parseDouble(a[4].replace(',', '.'));
                    } catch (NumberFormatException e) {
                        plugin.mensagens().enviar(s, "numero-invalido");
                        return;
                    }
                }
                if (!plugin.guerras().iniciar(fa, fb, horas, plugin.mensagens().bruta("motivo-staff"))) {
                    plugin.mensagens().enviar(s, "guerra-ja-existe", "a", fa.nome(), "b", fb.nome());
                    return;
                }
                plugin.diplomacia().definirPelaRelacao(fa.id(), fb.id(), Relacao.GUERRA);
            } else {
                var g = plugin.guerras().entre(fa.id(), fb.id());
                if (g == null) {
                    plugin.mensagens().enviar(s, "guerra-nao-existe", "a", fa.nome(), "b", fb.nome());
                    return;
                }
                plugin.guerras().terminar(g);
            }
            return;
        }
        listarGuerras(s);
    }

    private void listarGuerras(CommandSender s) {
        var lista = plugin.guerras().ativas();
        if (lista.isEmpty()) {
            plugin.mensagens().enviar(s, "guerras-nenhuma");
            return;
        }
        plugin.mensagens().enviarSemPrefixo(s, "guerras-cabecalho");
        for (var g : lista) {
            Faccao fa = plugin.faccoes().obter(g.a());
            Faccao fb = plugin.faccoes().obter(g.b());
            plugin.mensagens().enviarSemPrefixo(s, "guerras-linha", "a", fa == null ? g.a() : fa.nome(), "b", fb == null ? g.b() : fb.nome(),
                    "pa", String.valueOf(g.pontosA()), "pb", String.valueOf(g.pontosB()), "meta", String.valueOf(g.meta()),
                    "tempo", dev.pedrobugica.npcvivos.mundo.GuerraService.tempo(g.fim() - System.currentTimeMillis()));
        }
    }

    /** /npcv invasao <atacante> <defensor> | parar | info */
    private void invasao(CommandSender s, String[] a) {
        if (!pode(s, "guerra")) {
            return;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("parar")) {
            boolean havia = plugin.invasoes().acontecendo();
            plugin.invasoes().encerrar();
            plugin.mensagens().enviar(s, havia ? "invasao-parada" : "invasao-nenhuma");
            return;
        }
        if (a.length < 3) {
            String d = plugin.invasoes().descricao();
            if (d != null) {
                plugin.mensagens().enviar(s, "invasao-info", "info", d);
            }
            uso(s, "/npcv invasao <atacante> <defensor> | parar");
            return;
        }
        Faccao atacante = faccao(s, a[1]);
        Faccao defensor = atacante == null ? null : faccao(s, a[2]);
        if (defensor == null) {
            return;
        }
        String erro = plugin.invasoes().iniciar(atacante, defensor);
        if (erro != null) {
            plugin.mensagens().enviar(s, erro, "a", atacante.nome(), "b", defensor.nome());
        }
    }

    /** /npcv mercado ligar | desligar | ver <npc> | trocar */
    private void mercado(CommandSender s, String[] a) {
        if (!pode(s, "loja")) {
            return;
        }
        String acao = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (acao) {
            case "ligar", "desligar" -> {
                plugin.mundo().setPrecoDinamico(acao.equals("ligar"));
                plugin.mensagens().enviar(s, acao.equals("ligar") ? "mercado-ligado" : "mercado-desligado");
            }
            case "ver" -> {
                NpcVivo n = a.length > 2 ? npc(s, a[2]) : null;
                if (n == null) {
                    if (a.length <= 2) {
                        uso(s, "/npcv mercado ver <npc>");
                    }
                    return;
                }
                plugin.mensagens().enviar(s, "mercado-cabecalho", "npc", n.nome(),
                        "estado", plugin.mercado().ativo() ? "<green>ligado" : "<red>desligado");
                for (String l : plugin.mercado().resumo(n)) {
                    s.sendMessage(Texto.cor(l));
                }
            }
            case "trocar" -> plugin.mensagens().enviar(s, "mercado-trocas", "trocas", String.valueOf(plugin.mercado().trocarEntreNpcs()));
            default -> uso(s, "/npcv mercado <ligar|desligar|ver <npc>|trocar>");
        }
    }

    private void ajuda(CommandSender s) {
        plugin.mensagens().enviarLista(s, "ajuda");
        plugin.mensagens().enviarLista(s, "ajuda-editor");
        plugin.mensagens().enviarLista(s, "ajuda-faccoes");
        plugin.mensagens().enviarLista(s, "ajuda-upgrade");
        plugin.mensagens().enviarLista(s, "ajuda-mundo");
        plugin.mensagens().enviarLista(s, "ajuda-combate");
    }

    /**
     * /npcv combate [id] - mostra o estado de luta dos NPCs; /npcv combate <id> vida <n> ajusta a vida,
     * curar enche, matar derruba e posto remarca o lugar de origem. Serve para a staff conferir e para os testes.
     */
    private void combate(CommandSender s, String[] a) {
        if (!pode(s, "combate")) {
            return;
        }
        if (a.length < 2) {
            int vivos = 0;
            for (NpcVivo n : plugin.npcs().todos()) {
                Cerebro c = plugin.comportamento().cerebroExistente(n.id());
                if (c == null) {
                    continue;
                }
                vivos++;
                plugin.mensagens().enviarSemPrefixo(s, "combate-linha", "id", n.id(), "npc", n.nome(),
                        "estado", c.estado().nome(), "vida", String.valueOf(Math.round(c.vida())),
                        "vida-maxima", String.valueOf(Math.round(c.vidaMaxima())));
            }
            plugin.mensagens().enviar(s, "combate-cabecalho", "total", String.valueOf(vivos),
                    "ligado", plugin.comportamento().ativo() ? "sim" : "não");
            return;
        }
        NpcVivo n = npc(s, a[1]);
        if (n == null) {
            return;
        }
        Cerebro c = plugin.comportamento().cerebro(n);
        if (c == null) {
            plugin.mensagens().enviar(s, "combate-sem-cerebro", "npc", n.nome());
            return;
        }
        String acao = a.length < 3 ? "ver" : a[2].toLowerCase(Locale.ROOT);
        switch (acao) {
            case "vida" -> {
                if (a.length < 4) {
                    uso(s, "/npcv combate <id> vida <número>");
                    return;
                }
                try {
                    c.setVida(Double.parseDouble(a[3]));
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return;
                }
            }
            case "curar" -> {
                c.encherVida();
                c.setAlvo(null);
                c.liberarDuelista();
                c.setEstado(EstadoNpc.OCIOSO);
            }
            case "matar" -> plugin.combate().aplicarDano(n, c, c.vida() * 1000, null);
            case "posto" -> plugin.comportamento().marcarPosto(n);
            case "ver" -> { /* só mostra as linhas abaixo */ }
            default -> {
                uso(s, "/npcv combate <id> [ver|vida <n>|curar|matar|posto]");
                return;
            }
        }
        Player alvo = c.alvo() == null ? null : Bukkit.getPlayer(c.alvo());
        Player dono = c.dono() == null ? null : Bukkit.getPlayer(c.dono());
        plugin.mensagens().enviarLista(s, "combate-info", "id", n.id(), "npc", n.nome(),
                "estado", c.estado().nome(), "vida", String.valueOf(Math.round(c.vida())),
                "vida-maxima", String.valueOf(Math.round(c.vidaMaxima())),
                "dano", String.valueOf(c.atributos().dano()), "armadura", String.valueOf(c.atributos().armadura()),
                "alcance", String.valueOf(c.atributos().alcance()),
                "alvo", alvo == null ? "-" : alvo.getName(), "dono", dono == null ? "-" : dono.getName(),
                "respawn", String.valueOf(c.atributos().respawnSegundos()));
    }

    /** /npcv loja <id> compra <preço> [estoque] [repõe/h] | venda <preço> [limite/dia] | limpar | ver */
    private void loja(CommandSender s, Player p, String[] a) {
        if (!pode(s, "loja")) {
            return;
        }
        if (a.length < 3) {
            uso(s, "/npcv loja <id> <compra|venda|limpar|ver> ...");
            return;
        }
        NpcVivo n = npc(s, a[1]);
        if (n == null) {
            return;
        }
        String tipo = a[2].toLowerCase(Locale.ROOT);
        if (tipo.equals("limpar")) {
            n.compra().clear();
            n.venda().clear();
            plugin.npcs().salvar(n);
            plugin.mensagens().enviar(s, "npc-editado", "npc", n.nome());
            return;
        }
        if (tipo.equals("ver")) {
            for (ItemLoja i : n.compra()) {
                plugin.mensagens().enviarSemPrefixo(s, "loja-linha", "tipo", "vende", "item", ComercioService.nomeItem(i),
                        "quantidade", String.valueOf(i.quantidade()), "preco", Texto.dinheiro(i.precoBase()));
            }
            for (ItemLoja i : n.venda()) {
                plugin.mensagens().enviarSemPrefixo(s, "loja-linha", "tipo", "compra", "item", ComercioService.nomeItem(i),
                        "quantidade", String.valueOf(i.quantidade()), "preco", Texto.dinheiro(i.precoBase()));
            }
            return;
        }
        if (p == null || a.length < 4 || (!tipo.equals("compra") && !tipo.equals("venda"))) {
            uso(s, "/npcv loja <id> compra <preço> [estoque] [repõe por hora]   (o jogador COMPRA o item da sua mão)");
            uso(s, "/npcv loja <id> venda <preço> [limite por dia]   (o jogador VENDE o item da sua mão)");
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (mao.getType().isAir()) {
            plugin.mensagens().enviar(s, "segure-item");
            return;
        }
        double preco;
        int extra1;
        double extra2;
        try {
            preco = Double.parseDouble(a[3].replace(',', '.'));
            extra1 = a.length > 4 ? Integer.parseInt(a[4]) : 0;
            extra2 = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : 0;
        } catch (NumberFormatException e) {
            plugin.mensagens().enviar(s, "numero-invalido");
            return;
        }
        ItemStack modelo = mao.clone();
        int quantidade = modelo.getAmount();
        modelo.setAmount(1);
        boolean compra = tipo.equals("compra");
        List<ItemLoja> lista = compra ? n.compra() : n.venda();
        lista.add(new ItemLoja((compra ? "compra-" : "venda-") + lista.size(), modelo, quantidade, Math.max(0, preco),
                compra ? Math.max(0, extra1) : 0, compra ? Math.max(0, extra2) : 0, compra ? 0 : Math.max(0, extra1)));
        if (!n.tem(Papel.COMERCIANTE)) {
            n.papeis().add(Papel.COMERCIANTE);
        }
        salvarEMostrar(n);
        plugin.mensagens().enviar(s, compra ? "loja-item-compra" : "loja-item-venda", "npc", n.nome(),
                "quantidade", String.valueOf(quantidade), "preco", Texto.dinheiro(preco));
    }

    private String nomeFaccao(String id) {
        if (id == null) {
            return plugin.mensagens().bruta("faccoes-nenhuma");
        }
        Faccao f = plugin.faccoes().obter(id);
        return f == null ? id + " (não existe)" : f.nome();
    }

    private void salvarEMostrar(NpcVivo n) {
        plugin.npcs().salvar(n);
        plugin.npcs().spawnar(n);
    }

    private void uso(CommandSender s, String uso) {
        plugin.mensagens().enviar(s, "uso", "uso", uso);
    }

    private static String papeis() {
        List<String> l = new ArrayList<>();
        for (Papel p : Papel.values()) {
            l.add(p.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", l);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (!s.hasPermission("npcvivos.admin.editar") && !s.hasPermission("npcvivos.admin.criar")) {
            return r;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        List<String> faccoes = new ArrayList<>();
        plugin.faccoes().todas().forEach(f -> faccoes.add(f.id()));
        if (a.length == 1) {
            filtrar(r, SUBS, a[0]);
        } else if (sub.equals("guerra")) {
            if (a.length == 2) {
                filtrar(r, List.of("iniciar", "parar", "lista"), a[1]);
            } else if (a.length == 3 || a.length == 4) {
                filtrar(r, faccoes, a[a.length - 1]);
            }
        } else if (sub.equals("invasao")) {
            if (a.length == 2) {
                List<String> op = new ArrayList<>(faccoes);
                op.add("parar");
                filtrar(r, op, a[1]);
            } else if (a.length == 3) {
                filtrar(r, faccoes, a[2]);
            }
        } else if (sub.equals("mercado")) {
            if (a.length == 2) {
                filtrar(r, List.of("ligar", "desligar", "ver", "trocar"), a[1]);
            } else if (a.length == 3 && a[1].equalsIgnoreCase("ver")) {
                List<String> ids = new ArrayList<>();
                plugin.npcs().todos().forEach(n -> ids.add(n.id()));
                filtrar(r, ids, a[2]);
            }
        } else if (sub.equals("pontuacao") && (a.length == 2 || a.length == 3)) {
            filtrar(r, faccoes, a[a.length - 1]);
        } else if (sub.equals("promocao") && a.length == 2) {
            List<String> op = new ArrayList<>(faccoes);
            op.add("todas");
            filtrar(r, op, a[1]);
        } else if (sub.equals("relacao")) {
            if (a.length == 2 || a.length == 3) {
                filtrar(r, faccoes, a[a.length - 1]);
            } else if (a.length == 4) {
                filtrar(r, List.of("aliada", "neutra", "rival", "guerra"), a[3]);
            }
        } else if (sub.equals("reputacao") || sub.equals("membro")) {
            if (a.length == 2) {
                List<String> nomes = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(pl -> nomes.add(pl.getName()));
                filtrar(r, nomes, a[1]);
            } else if (a.length == 3) {
                List<String> op = new ArrayList<>(faccoes);
                if (sub.equals("membro")) {
                    op.add("nenhuma");
                }
                filtrar(r, op, a[2]);
            }
        } else if (sub.equals("dar") && a.length == 2) {
            List<String> nomes = new ArrayList<>();
            org.bukkit.Bukkit.getOnlinePlayers().forEach(pl -> nomes.add(pl.getName()));
            filtrar(r, nomes, a[1]);
        } else if (sub.equals("dar") && a.length == 3) {
            filtrar(r, List.of("comum", "rara", "epica", "lendaria", "pergaminho"), a[2]);
        } else if (sub.equals("aprimorar") && a.length == 2) {
            List<String> ns = new ArrayList<>();
            for (int i = 0; i <= plugin.upgrades().nivelMaximo(); i++) {
                ns.add(String.valueOf(i));
            }
            filtrar(r, ns, a[1]);
        } else if (sub.equals("aprimorar") && a.length == 3) {
            filtrar(r, List.of("comum", "raro", "epico", "lendario"), a[2]);
        } else if (sub.equals("faccao") && a.length == 3) {
            List<String> op = new ArrayList<>(faccoes);
            op.add("nenhuma");
            filtrar(r, op, a[2]);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("criar")) {
            List<String> ps = new ArrayList<>();
            for (Papel p : Papel.values()) {
                ps.add(p.name().toLowerCase(Locale.ROOT));
            }
            filtrar(r, ps, a[1]);
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("lista") && !a[0].equalsIgnoreCase("reload")) {
            List<String> ids = new ArrayList<>();
            plugin.npcs().todos().forEach(n -> ids.add(n.id()));
            filtrar(r, ids, a[1]);
        } else if (a.length == 3) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "skin" -> {
                    List<String> skins = new ArrayList<>(NpcManager.SKINS_PADRAO);
                    skins.add("jogador:");
                    filtrar(r, skins, a[2]);
                }
                case "papel" -> filtrar(r, List.of("adicionar", "remover"), a[2]);
                case "fala" -> filtrar(r, List.of("adicionar", "saudacao", "retorno", "ambiente", "limpar"), a[2]);
                case "rotina" -> filtrar(r, List.of("parado", "passear", "patrulha", "ponto", "limpar-pontos", "dormir"), a[2]);
                case "loja" -> filtrar(r, List.of("compra", "venda", "limpar", "ver"), a[2]);
                case "combate" -> filtrar(r, List.of("ver", "vida", "curar", "matar", "posto"), a[2]);
                default -> {
                }
            }
        } else if (a.length == 4 && a[0].equalsIgnoreCase("papel")) {
            List<String> ps = new ArrayList<>();
            for (Papel p : Papel.values()) {
                ps.add(p.name().toLowerCase(Locale.ROOT));
            }
            filtrar(r, ps, a[3]);
        }
        return r;
    }

    private static void filtrar(List<String> destino, List<String> opcoes, String inicio) {
        String i = inicio.toLowerCase(Locale.ROOT);
        for (String o : opcoes) {
            if (o.toLowerCase(Locale.ROOT).startsWith(i)) {
                destino.add(o);
            }
        }
    }
}
