package dev.pedrobugica.survivalrpg.comandos;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.construcao.Terreno;
import dev.pedrobugica.survivalrpg.item.Raridade;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.mundo.Lugar;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /rpgadmin: níveis, itens, classe, capital, lugares, masmorras, desfazer obras e mapa em imagem. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final SurvivalRpgPlugin plugin;

    public AdminCommand(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            plugin.mensagens().enviarLista(s, "ajuda-admin");
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "nivel", "xp", "pontos" -> numero(s, sub, a);
            case "item" -> item(s, a);
            case "classe" -> {
                Player alvo = a.length >= 3 ? Bukkit.getPlayerExact(a[1]) : null;
                Classe c = a.length >= 3 ? Classe.buscar(a[2]) : null;
                if (alvo == null || c == null) {
                    plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin classe <jogador> <classe>");
                    return true;
                }
                plugin.escolherClasse(alvo, c, true);
                plugin.mensagens().enviar(s, "admin-ok");
            }
            case "resetar" -> {
                Player alvo = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (alvo == null) {
                    plugin.mensagens().enviar(s, "jogador-offline");
                    return true;
                }
                PerfilRpg r = plugin.perfis().perfil(alvo);
                r.setClasse(null);
                r.setNivel(1);
                r.setXp(0);
                r.setPontosLivres(0);
                for (Atributo at : Atributo.values()) {
                    r.setAtributo(at, 0);
                }
                r.setTrocouClasseEm(0);
                r.regioesDescobertas().clear();
                plugin.perfis().aplicarVida(alvo);
                plugin.mensagens().enviar(s, "admin-ok");
            }
            case "capital" -> capital(s, a);
            case "lugares", "lugar" -> lugares(s, a);
            case "desfazer" -> {
                if (a.length < 2) {
                    plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin desfazer <id do lugar>");
                    return true;
                }
                plugin.lugares().desfazer(s, a[1].toLowerCase(Locale.ROOT));
            }
            case "masmorra", "masmorras" -> masmorra(s, a);
            case "reinos", "reino" -> {
                String acao = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                if (acao.equals("construir")) {
                    plugin.castelos().construirFaltando(s, () -> plugin.npcHook().sincronizarReinos());
                } else if (acao.equals("sincronizar")) {
                    plugin.npcHook().sincronizarReinos();
                    plugin.mensagens().enviar(s, "admin-ok");
                } else {
                    s.sendMessage(Texto.cor("<yellow>/rpgadmin reinos construir <gray>- constrói os castelos que faltam"));
                    s.sendMessage(Texto.cor("<yellow>/rpgadmin reinos sincronizar <gray>- manda vilas e castelos para o NpcVivos"));
                    s.sendMessage(Texto.cor("<gray>Guerras, exércitos e donos: <yellow>/reinoadmin"));
                }
            }
            case "lobby" -> {
                String acao = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                if (acao.equals("reconstruir")) {
                    plugin.lobby().reconstruir(() -> plugin.mensagens().enviar(s, "admin-ok"));
                } else if (s instanceof Player p && plugin.lobby().ativo()) {
                    plugin.lobby().teleportar(p);
                } else {
                    s.sendMessage(Texto.cor("<yellow>/rpgadmin lobby [ir|reconstruir]"));
                }
            }
            case "mapa" -> mapa(s, a);
            case "reload" -> {
                plugin.recarregar();
                plugin.mensagens().enviar(s, "admin-recarregado");
            }
            case "status" -> plugin.mensagens().enviarLista(s, "admin-status",
                    "capital", plugin.capital().construida() ? "<green>pronta" : plugin.capital().construindo() ? "<yellow>construindo" : "<red>não construída",
                    "lugares", String.valueOf(plugin.lugares().todos().size()),
                    "populando", plugin.lugares().populando() ? "<yellow>sim" : "<gray>não",
                    "masmorras", plugin.masmorras().instanciasConstruidas() + "/" + plugin.masmorras().instanciasTotal(),
                    "npcs", plugin.npcHook().ativo() ? "<green>sim" : "<red>não");
            default -> plugin.mensagens().enviarLista(s, "ajuda-admin");
        }
        return true;
    }

    private void numero(CommandSender s, String sub, String[] a) {
        Player alvo = a.length >= 3 ? Bukkit.getPlayerExact(a[1]) : null;
        if (alvo == null) {
            plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin " + sub + " <jogador> <valor>");
            return;
        }
        double v;
        try {
            v = Double.parseDouble(a[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            plugin.mensagens().enviar(s, "numero-invalido");
            return;
        }
        PerfilRpg r = plugin.perfis().perfil(alvo);
        switch (sub) {
            case "nivel" -> {
                int n = (int) Math.max(1, Math.min(plugin.perfis().nivelMaximo(), v));
                int antes = r.nivel();
                r.setNivel(n);
                r.setXp(0);
                int dif = n - antes;
                r.setPontosLivres(Math.max(0, r.pontosLivres() + dif * plugin.getConfig().getInt("pontos-por-nivel", 3)));
                if (r.classe() != null) {
                    r.setAtributo(r.classe().principal(), Math.max(0, r.atributo(r.classe().principal()) + dif));
                }
                plugin.perfis().aplicarVida(alvo);
                plugin.hud().atualizarXp(alvo);
            }
            case "xp" -> plugin.perfis().darXp(alvo, v, "admin");
            default -> r.setPontosLivres((int) Math.max(0, v));
        }
        plugin.mensagens().enviar(s, "admin-ok");
    }

    private void item(CommandSender s, String[] a) {
        Player alvo = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
        if (alvo == null) {
            plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin item <jogador> [nível] [raridade]");
            return;
        }
        int nivel = plugin.perfis().perfil(alvo).nivel();
        Raridade rar = Raridade.RARO;
        try {
            if (a.length >= 3) {
                nivel = Integer.parseInt(a[2]);
            }
        } catch (NumberFormatException e) {
            plugin.mensagens().enviar(s, "numero-invalido");
            return;
        }
        if (a.length >= 4 && Raridade.buscar(a[3]) != null) {
            rar = Raridade.buscar(a[3]);
        }
        ItemStack it = plugin.itens().gerar(nivel, rar, plugin.perfis().perfil(alvo).classe());
        alvo.getInventory().addItem(it);
        plugin.mensagens().enviar(s, "admin-ok");
    }

    private void capital(CommandSender s, String[] a) {
        String acao = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (acao) {
            case "construir" -> {
                if (plugin.capital().construida() && !(a.length >= 3 && a[2].equalsIgnoreCase("forcar"))) {
                    plugin.mensagens().enviar(s, "capital-ja-existe");
                    return;
                }
                plugin.capital().construir(s, a.length >= 3 && a[2].equalsIgnoreCase("forcar"), null);
            }
            case "ir" -> {
                Lugar c = plugin.lugares().capital();
                if (s instanceof Player p && c != null) {
                    p.teleport(plugin.lugares().pontoSeguro(c));
                }
            }
            default -> plugin.mensagens().enviar(s, plugin.capital().construida() ? "capital-pronta" : "capital-nao-existe");
        }
    }

    private void lugares(CommandSender s, String[] a) {
        String acao = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        switch (acao) {
            case "popular" -> plugin.lugares().popular(s, null);
            case "lista" -> {
                for (Lugar l : plugin.lugares().todos()) {
                    plugin.mensagens().enviarSemPrefixo(s, "admin-lugar-linha", "id", l.id(), "lugar", l.formatado(),
                            "tipo", l.tipo().nome(), "x", String.valueOf(l.x()), "y", String.valueOf(l.y()), "z", String.valueOf(l.z()),
                            "estrutura", l.estrutura());
                }
            }
            case "ir" -> {
                Lugar l = a.length >= 3 ? plugin.lugares().obter(a[2]) : null;
                if (l != null && s instanceof Player p) {
                    Location destino = plugin.lugares().pontoSeguro(l);
                    if (destino != null) {
                        p.teleport(destino);
                    }
                }
            }
            case "remover" -> {
                if (a.length >= 3 && plugin.lugares().remover(a[2].toLowerCase(Locale.ROOT))) {
                    plugin.mensagens().enviar(s, "admin-ok");
                }
            }
            case "colocar" -> {
                // /rpgadmin lugar colocar <estrutura> <tipo> <raio-zona> [nome...]
                if (!(s instanceof Player p) || a.length < 5) {
                    plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin lugar colocar <estrutura> <tipo> <raio-zona> [nome...]");
                    return;
                }
                Lugar.Tipo t = Lugar.Tipo.buscar(a[3]);
                if (t == null) {
                    plugin.mensagens().enviar(s, "tipo-invalido");
                    return;
                }
                int raio;
                try {
                    raio = Integer.parseInt(a[4]);
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return;
                }
                String nome = a.length > 5 ? String.join(" ", Arrays.copyOfRange(a, 5, a.length)) : null;
                plugin.lugares().colocarAqui(p, a[2], t, raio, nome);
            }
            case "marcar" -> {
                // /rpgadmin lugar marcar <tipo> <raio-zona> <nome...> (marca algo que já existe, sem construir)
                if (!(s instanceof Player p) || a.length < 5) {
                    plugin.mensagens().enviar(s, "uso", "uso", "/rpgadmin lugar marcar <tipo> <raio-zona> <nome...>");
                    return;
                }
                Lugar.Tipo t = Lugar.Tipo.buscar(a[2]);
                int raio;
                try {
                    raio = Integer.parseInt(a[3]);
                } catch (NumberFormatException e) {
                    plugin.mensagens().enviar(s, "numero-invalido");
                    return;
                }
                if (t == null) {
                    plugin.mensagens().enviar(s, "tipo-invalido");
                    return;
                }
                String nome = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                String id = Texto.semCor(nome).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                if (id.isEmpty() || plugin.lugares().obter(id) != null) {
                    id = t.name().toLowerCase(Locale.ROOT) + "_" + System.currentTimeMillis() % 100000;
                }
                Location l = p.getLocation();
                plugin.lugares().registrar(new Lugar(id, nome, t, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                        raio, raio > 0 && t.viagem(), "manual"));
                plugin.mensagens().enviar(s, "lugar-criado", "lugar", nome, "x", String.valueOf(l.getBlockX()), "z", String.valueOf(l.getBlockZ()));
            }
            default -> plugin.mensagens().enviarLista(s, "ajuda-admin");
        }
    }

    private void masmorra(CommandSender s, String[] a) {
        String acao = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "lista";
        switch (acao) {
            case "construir" -> {
                plugin.masmorras().prepararTodas();
                plugin.mensagens().enviar(s, "admin-ok");
            }
            case "reconstruir" -> plugin.masmorras().reconstruir(() -> plugin.mensagens().enviar(s, "admin-ok"));
            case "ir" -> {
                Location l = a.length >= 3 ? plugin.masmorras().inicioDe(a[2]) : null;
                if (l != null && s instanceof Player p) {
                    p.teleport(l);
                }
            }
            default -> s.sendMessage(Texto.cor("<gray>Instâncias: <white>" + String.join(", ", plugin.masmorras().chaves())
                    + " <gray>(" + plugin.masmorras().instanciasConstruidas() + " construídas)"));
        }
    }

    /** Desenha a área vista de cima num PNG (para conferir construções sem entrar no jogo). */
    private void mapa(CommandSender s, String[] a) {
        int raio = 80;
        World w;
        int cx;
        int cz;
        try {
            if (a.length >= 2) {
                raio = Math.max(16, Math.min(512, Integer.parseInt(a[1])));
            }
            if (a.length >= 5) {
                w = Bukkit.getWorld(a[2]);
                cx = Integer.parseInt(a[3]);
                cz = Integer.parseInt(a[4]);
            } else if (s instanceof Player p) {
                w = p.getWorld();
                cx = p.getLocation().getBlockX();
                cz = p.getLocation().getBlockZ();
            } else {
                w = Bukkit.getWorld(plugin.regioes().mundoPrincipal());
                Location sp = w == null ? null : w.getSpawnLocation();
                cx = sp == null ? 0 : sp.getBlockX();
                cz = sp == null ? 0 : sp.getBlockZ();
            }
        } catch (NumberFormatException e) {
            plugin.mensagens().enviar(s, "numero-invalido");
            return;
        }
        if (w == null) {
            plugin.mensagens().enviar(s, "mundo-nao-encontrado", "mundo", a.length >= 3 ? a[2] : "?");
            return;
        }
        final int r = raio;
        final int x0 = cx;
        final int z0 = cz;
        final World mundo = w;
        Terreno.carregar(w, cx - r, cz - r, cx + r, cz + r).thenAccept(t -> {
            int tam = r * 2 + 1;
            int escala = tam <= 300 ? 3 : 1;
            BufferedImage img = new BufferedImage(tam * escala, tam * escala, BufferedImage.TYPE_INT_RGB);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = x0 + dx;
                    int z = z0 + dz;
                    int y = t.topo(x, z);
                    Color c = t.dados(x, y, z).getMapColor();
                    int yN = t.topo(x, z - 1);
                    double sombra = y > yN ? 1.12 : y < yN ? 0.82 : 1.0;
                    int rgb = (cor(c.getRed() * sombra) << 16) | (cor(c.getGreen() * sombra) << 8) | cor(c.getBlue() * sombra);
                    if (dx == 0 && dz == 0) {
                        rgb = 0xFF0000;
                    }
                    for (int i = 0; i < escala; i++) {
                        for (int j = 0; j < escala; j++) {
                            img.setRGB((dx + r) * escala + i, (dz + r) * escala + j, rgb);
                        }
                    }
                }
            }
            File f = new File(plugin.getDataFolder(), "mapas/mapa_" + mundo.getName() + "_" + x0 + "_" + z0 + ".png");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    f.getParentFile().mkdirs();
                    ImageIO.write(img, "png", f);
                    plugin.mensagens().enviar(s, "admin-mapa", "arquivo", f.getPath());
                } catch (IOException e) {
                    plugin.mensagens().enviar(s, "admin-erro", "erro", e.getMessage());
                }
            });
        });
    }

    private static int cor(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> o = new ArrayList<>();
        if (a.length == 1) {
            o.addAll(List.of("nivel", "xp", "pontos", "item", "classe", "resetar", "capital", "lugar", "desfazer", "masmorra", "reinos", "lobby", "mapa",
                    "reload", "status"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "capital" -> o.addAll(List.of("construir", "ir", "status"));
                case "lugar", "lugares" -> o.addAll(List.of("popular", "lista", "ir", "remover", "colocar", "marcar"));
                case "masmorra", "masmorras" -> o.addAll(List.of("construir", "reconstruir", "ir", "lista"));
                case "reinos", "reino" -> o.addAll(List.of("construir", "sincronizar"));
                case "lobby" -> o.addAll(List.of("ir", "reconstruir"));
                case "desfazer" -> {
                    for (Lugar l : plugin.lugares().todos()) {
                        o.add(l.id());
                    }
                }
                case "nivel", "xp", "pontos", "item", "classe", "resetar" -> {
                    return null;
                }
                default -> {
                }
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "classe" -> {
                    for (Classe c : Classe.values()) {
                        o.add(c.name().toLowerCase(Locale.ROOT));
                    }
                }
                case "lugar", "lugares" -> {
                    if (a[1].equalsIgnoreCase("ir") || a[1].equalsIgnoreCase("remover")) {
                        for (Lugar l : plugin.lugares().todos()) {
                            o.add(l.id());
                        }
                    } else if (a[1].equalsIgnoreCase("marcar")) {
                        for (Lugar.Tipo t : Lugar.Tipo.values()) {
                            o.add(t.name().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                case "masmorra", "masmorras" -> o.addAll(plugin.masmorras().chaves());
                default -> {
                }
            }
        } else if (a.length == 4 && a[0].equalsIgnoreCase("item")) {
            for (Raridade r : Raridade.values()) {
                o.add(r.name().toLowerCase(Locale.ROOT));
            }
        } else if (a.length == 4 && a[1].equalsIgnoreCase("colocar")) {
            for (Lugar.Tipo t : Lugar.Tipo.values()) {
                o.add(t.name().toLowerCase(Locale.ROOT));
            }
        }
        String u = a[a.length - 1].toLowerCase(Locale.ROOT);
        o.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(u));
        return o;
    }
}
