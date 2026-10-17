package dev.pedrobugica.npcvivos.comandos;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.menu.Menu;
import dev.pedrobugica.npcvivos.mundo.Guerra;
import dev.pedrobugica.npcvivos.reino.Reino;
import dev.pedrobugica.npcvivos.reino.ReinoService;
import dev.pedrobugica.npcvivos.reino.Territorio;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /reino (jogadores: menu dos reinos, jurar lealdade, guerras) e /reinoadmin (staff). */
public final class ReinoCommand implements CommandExecutor, TabCompleter {

    public static final String PERM_ADMIN = "npcvivos.admin.reino";

    private final NpcVivosPlugin plugin;

    public ReinoCommand(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private ReinoService r() {
        return plugin.reinos();
    }

    private static void msg(CommandSender s, String t) {
        s.sendMessage(Texto.cor(t));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("reinoadmin")) {
            if (!s.hasPermission(PERM_ADMIN)) {
                msg(s, "<red>Sem permissão.");
                return true;
            }
            admin(s, a);
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "noticias", "guerras" -> {
                CommandSender alvo = s;
                if (a.length > 1 && (s.hasPermission(PERM_ADMIN) || !(s instanceof Player))) {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    if (p != null) {
                        alvo = p;
                    }
                }
                guerras(alvo);
            }
            case "entrar" -> {
                if (!(s instanceof Player p) || a.length < 2) {
                    msg(s, "<yellow>Use: /reino entrar <reino>");
                    return true;
                }
                Faccao f = plugin.faccoes().obter(a[1].toLowerCase(Locale.ROOT));
                if (f == null || r().obter(f.id()) == null) {
                    msg(s, "<red>Reino não encontrado.");
                    return true;
                }
                plugin.faccoes().entrar(p, f);
            }
            case "sair" -> {
                if (s instanceof Player p) {
                    plugin.faccoes().sair(p);
                }
            }
            case "lista" -> lista(s);
            default -> {
                if (s instanceof Player p) {
                    menu(p);
                } else {
                    lista(s);
                }
            }
        }
        return true;
    }

    private void guerras(CommandSender s) {
        List<String> l = r().resumoGuerras();
        msg(s, "<gold><bold>⚔ Guerras dos reinos");
        if (l.isEmpty()) {
            msg(s, "<gray>Os reinos estão em paz... por enquanto.");
        }
        for (String x : l) {
            msg(s, "<dark_gray> • " + x);
        }
    }

    private void lista(CommandSender s) {
        msg(s, "<gold><bold>Reinos");
        for (Reino x : r().todos()) {
            msg(s, " " + x.nome() + " <dark_gray>- <gray>" + x.lema() + " <dark_gray>(" + r().castelosDe(x.id()).size() + " castelo(s), "
                    + r().territorios().stream().filter(t -> x.id().equals(t.dono()) && t.tipo() == Territorio.Tipo.VILA).count() + " vila(s))");
        }
    }

    private static Material bandeira(Reino x) {
        Material m = Material.matchMaterial(x.bandeira().name() + "_BANNER");
        return m == null ? Material.WHITE_BANNER : m;
    }

    public void menu(Player p) {
        List<Reino> todos = new ArrayList<>(r().todos());
        Menu m = new Menu(4, Texto.cor("<dark_gray>Reinos de Aurora"));
        Faccao membro = plugin.faccoes().membro(p);
        int[] slots = todos.size() <= 3 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < todos.size() && i < slots.length; i++) {
            Reino x = todos.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray><italic>\"" + x.lema() + "\"");
            lore.add("");
            for (String d : x.descricao()) {
                lore.add("<gray>" + d);
            }
            lore.add("");
            lore.add("<gray>Soberano: <white>" + x.rei());
            List<Territorio> castelos = r().castelosDe(x.id());
            lore.add("<gray>Castelos: <white>" + (castelos.isEmpty() ? "nenhum (exilado!)" : String.join(", ", castelos.stream().map(t -> Texto.semCor(t.nome())).toList())));
            long vilas = r().territorios().stream().filter(t -> x.id().equals(t.dono()) && t.tipo() == Territorio.Tipo.VILA).count();
            lore.add("<gray>Vilas: <white>" + vilas);
            List<String> guerras = new ArrayList<>();
            for (Guerra g : plugin.guerras().ativas()) {
                if (g.envolve(x.id())) {
                    Reino o = r().obter(g.inimiga(x.id()));
                    guerras.add(o == null ? g.inimiga(x.id()) : o.curto());
                }
            }
            lore.add("<gray>Guerra: " + (guerras.isEmpty() ? "<green>em paz" : "<red>contra " + String.join(", ", guerras)));
            if (!castelos.isEmpty()) {
                Territorio c = castelos.get(0);
                lore.add("<gray>Castelo em: <white>" + c.x() + ", " + c.z());
            }
            lore.add("<gray>Sua reputação: " + plugin.faccoes().nivel(p.getUniqueId(), x.id()).formatado()
                    + " <dark_gray>(" + plugin.faccoes().reputacao(p.getUniqueId(), x.id()) + ")");
            lore.add("");
            if (membro != null && membro.id().equals(x.id())) {
                lore.add("<green><bold>✔ Você serve a este reino");
            } else {
                lore.add("<yellow>Clique para jurar lealdade!");
                lore.add("<dark_gray>(ou fale com o rei no castelo)");
            }
            m.item(slots[i], Menu.criar(bandeira(x), x.nome(), lore), (pl, c) -> {
                pl.closeInventory();
                Faccao f = plugin.faccoes().obter(x.id());
                if (f != null && (membro == null || !membro.id().equals(x.id()))) {
                    plugin.faccoes().entrar(pl, f);
                }
            });
        }
        List<String> guerras = new ArrayList<>(r().resumoGuerras());
        if (guerras.isEmpty()) {
            guerras.add("<gray>Os reinos estão em paz.");
        }
        guerras.add("");
        guerras.add("<gray>Membros de um reino em guerra ganham");
        guerras.add("<gray>dinheiro e pontos matando soldados inimigos");
        guerras.add("<gray>e ajudando nos cercos aos castelos!");
        m.item(31, Menu.criar(Material.DIAMOND_SWORD, "<red><bold>⚔ Guerras e exércitos", guerras), null);
        if (membro != null && r().obter(membro.id()) != null) {
            m.item(35, Menu.criar(Material.BARRIER, "<red>Abandonar o reino", List.of("<gray>Shift + clique.",
                    "<red>Perde reputação e espera para entrar em outro.")), (pl, c) -> {
                if (c.isShiftClick()) {
                    pl.closeInventory();
                    plugin.faccoes().sair(pl);
                }
            });
        }
        m.preencher(Material.BLACK_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    // ------------------------------------------------------------------ ADMIN

    private void ajuda(CommandSender s) {
        msg(s, "<gold><bold>/reinoadmin");
        msg(s, "<yellow>lista <gray>- castelos e vilas com dono e coordenadas");
        msg(s, "<yellow>guerra <a> <b> [horas] <gray>- começa uma guerra entre reinos");
        msg(s, "<yellow>paz <a> <b> <gray>- termina a guerra");
        msg(s, "<yellow>ataque <reino> <castelo|vila> [soldados] <gray>- manda um exército agora");
        msg(s, "<yellow>dono <castelo|vila> <reino|nenhum> <gray>- muda o dono (pinta as bandeiras)");
        msg(s, "<yellow>castelo <reino> [raio] <gray>- marca um castelo onde você está (você = trono)");
        msg(s, "<yellow>vila <raio> <nome...> <gray>- marca uma vila onde você está");
        msg(s, "<yellow>remover <id> <gray>| <yellow>tp <id> <gray>| <yellow>guarnicao [id] <gray>| <yellow>limpar [raio]");
        msg(s, "<yellow>recarregar <gray>- relê o reinos.yml");
    }

    private Reino reino(CommandSender s, String id) {
        Reino x = r().obter(id);
        if (x == null) {
            msg(s, "<red>Reino '" + id + "' não existe. Reinos: " + String.join(", ", r().todos().stream().map(Reino::id).toList()));
        }
        return x;
    }

    private Territorio territorio(CommandSender s, String id) {
        Territorio t = r().territorio(id);
        if (t == null) {
            msg(s, "<red>Lugar '" + id + "' não existe. Veja /reinoadmin lista");
        }
        return t;
    }

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) {
            ajuda(s);
            return;
        }
        Player p = s instanceof Player pl ? pl : null;
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "lista" -> {
                msg(s, "<gold><bold>Territórios");
                for (Territorio t : r().territorios()) {
                    Reino d = r().obter(t.dono());
                    msg(s, "<dark_gray> • <white>" + t.id() + " <gray>" + t.nome() + " <dark_gray>[" + t.tipo().name().toLowerCase(Locale.ROOT) + "] <gray>dono: "
                            + (d == null ? "<white>ninguém" : d.nome()) + " <dark_gray>(" + t.x() + ", " + t.y() + ", " + t.z() + ")");
                }
                guerras(s);
            }
            case "guerra" -> {
                if (a.length < 3) {
                    msg(s, "<yellow>/reinoadmin guerra <a> <b> [horas]");
                    return;
                }
                Reino x = reino(s, a[1]);
                Reino y = reino(s, a[2]);
                if (x == null || y == null) {
                    return;
                }
                double horas = a.length > 3 ? parse(a[3], 48) : r().cfg().getDouble("guerra.duracao-horas", 48);
                boolean ok = plugin.guerras().iniciar(r().faccao(x), r().faccao(y), horas, "Decreto real");
                msg(s, ok ? "<green>Guerra iniciada." : "<red>Já estão em guerra (ou a facção não existe).");
            }
            case "paz" -> {
                if (a.length < 3) {
                    msg(s, "<yellow>/reinoadmin paz <a> <b>");
                    return;
                }
                Guerra g = plugin.guerras().entre(a[1].toLowerCase(Locale.ROOT), a[2].toLowerCase(Locale.ROOT));
                if (g == null) {
                    msg(s, "<red>Não estão em guerra.");
                    return;
                }
                plugin.guerras().terminar(g);
                msg(s, "<green>Guerra terminada.");
            }
            case "ataque" -> {
                if (a.length < 3) {
                    msg(s, "<yellow>/reinoadmin ataque <reino> <castelo|vila> [soldados]");
                    return;
                }
                Reino x = reino(s, a[1]);
                Territorio t = territorio(s, a[2]);
                if (x == null || t == null) {
                    return;
                }
                if (t.dono() != null && !r().emGuerra(x.id(), t.dono())) {
                    Reino d = r().obter(t.dono());
                    if (d != null) {
                        plugin.guerras().iniciar(r().faccao(x), r().faccao(d), r().cfg().getDouble("guerra.duracao-horas", 48), "Ataque surpresa");
                    }
                }
                String erro = r().campanhas().lancar(x, t, a.length > 3 ? (int) parse(a[3], 10) : r().cfg().getInt("guerra.exercito-tamanho", 10));
                msg(s, erro == null ? "<green>Exército a caminho!" : "<red>" + erro);
            }
            case "dono" -> {
                if (a.length < 3) {
                    msg(s, "<yellow>/reinoadmin dono <castelo|vila> <reino|nenhum>");
                    return;
                }
                Territorio t = territorio(s, a[1]);
                if (t == null) {
                    return;
                }
                if (a[2].equalsIgnoreCase("nenhum")) {
                    r().definirDono(t, null);
                } else {
                    Reino x = reino(s, a[2]);
                    if (x == null) {
                        return;
                    }
                    r().definirDono(t, x.id());
                }
                msg(s, "<green>Dono alterado.");
            }
            case "castelo" -> {
                if (p == null || a.length < 2) {
                    msg(s, "<yellow>/reinoadmin castelo <reino> [raio] <gray>(em jogo, de pé no trono)");
                    return;
                }
                Reino x = reino(s, a[1]);
                if (x == null) {
                    return;
                }
                int raio = a.length > 2 ? (int) parse(a[2], 32) : 32;
                r().registrarCastelo(x.id(), x.cor() + "Castelo de " + x.curto(), p.getLocation(), raio, p.getLocation());
                msg(s, "<green>Castelo de " + x.curto() + " marcado aqui (raio " + raio + "). O rei aparece no trono.");
            }
            case "vila" -> {
                if (p == null || a.length < 3) {
                    msg(s, "<yellow>/reinoadmin vila <raio> <nome...>");
                    return;
                }
                int raio = (int) parse(a[1], 40);
                String nome = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                String id = Texto.semCor(nome).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
                Territorio t = r().registrarVila(id, "<green>" + nome, p.getLocation(), raio);
                Reino d = r().obter(t.dono());
                msg(s, "<green>Vila " + t.id() + " marcada. Dono: " + (d == null ? "ninguém (longe dos castelos)" : d.nome()));
            }
            case "remover" -> {
                if (a.length < 2) {
                    msg(s, "<yellow>/reinoadmin remover <id>");
                    return;
                }
                r().removerTerritorio(a[1].toLowerCase(Locale.ROOT));
                msg(s, "<green>Removido (se existia).");
            }
            case "tp" -> {
                if (p == null || a.length < 2) {
                    return;
                }
                Territorio t = territorio(s, a[1]);
                if (t != null && t.centro() != null) {
                    Location l = t.centro();
                    l.setY(l.getWorld().getHighestBlockYAt(l) + 1);
                    p.teleport(l);
                }
            }
            case "guarnicao" -> {
                int n = 0;
                for (Territorio t : r().territorios()) {
                    if ((a.length < 2 || t.id().equalsIgnoreCase(a[1])) && t.carregado()) {
                        n += r().completarGuarnicao(t);
                    }
                }
                msg(s, "<green>" + n + " soldado(s) a mais nas guarnições carregadas.");
            }
            case "limpar" -> {
                if (p == null) {
                    return;
                }
                double raio = a.length > 1 ? parse(a[1], 64) : 64;
                int n = 0;
                for (Entity e : p.getNearbyEntities(raio, raio, raio)) {
                    if (r().ehSoldado(e)) {
                        e.remove();
                        n++;
                    }
                }
                msg(s, "<green>" + n + " soldado(s) removido(s).");
            }
            case "recarregar" -> {
                r().carregar();
                r().iniciar();
                msg(s, "<green>reinos.yml recarregado.");
            }
            default -> ajuda(s);
        }
    }

    private static double parse(String s, double padrao) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        List<String> reinos = r().todos().stream().map(Reino::id).toList();
        List<String> lugares = r().territorios().stream().map(Territorio::id).toList();
        if (cmd.getName().equalsIgnoreCase("reinoadmin")) {
            if (!s.hasPermission(PERM_ADMIN)) {
                return op;
            }
            if (a.length == 1) {
                op.addAll(List.of("lista", "guerra", "paz", "ataque", "dono", "castelo", "vila", "remover", "tp", "guarnicao", "limpar", "recarregar"));
            } else if (a.length == 2) {
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "guerra", "paz", "ataque", "castelo" -> op.addAll(reinos);
                    case "dono", "remover", "tp", "guarnicao" -> op.addAll(lugares);
                    default -> {
                    }
                }
            } else if (a.length == 3) {
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "guerra", "paz" -> op.addAll(reinos);
                    case "ataque" -> op.addAll(lugares);
                    case "dono" -> {
                        op.addAll(reinos);
                        op.add("nenhum");
                    }
                    default -> {
                    }
                }
            }
        } else if (a.length == 1) {
            op.addAll(List.of("entrar", "sair", "guerras", "lista"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("entrar")) {
            op.addAll(reinos);
        }
        String ultimo = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return op.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(ultimo)).toList();
    }
}
