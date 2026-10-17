package dev.pedrobugica.survivalrpg.grupo;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos de até 5 jogadores: sem fogo amigo, XP dividida com bônus para quem joga junto e chat próprio (/g).
 * O grupo some quando todos saem (não fica salvo no disco).
 */
public final class GrupoService implements Listener {

    public static final class Grupo {
        private UUID lider;
        private final Set<UUID> membros = new LinkedHashSet<>();

        Grupo(UUID lider) {
            this.lider = lider;
            membros.add(lider);
        }

        public UUID lider() {
            return lider;
        }

        public Set<UUID> membros() {
            return membros;
        }

        public List<Player> online() {
            List<Player> l = new ArrayList<>();
            for (UUID u : membros) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    l.add(p);
                }
            }
            return l;
        }
    }

    private final SurvivalRpgPlugin plugin;
    private final Map<UUID, Grupo> porJogador = new HashMap<>();
    /** convidado -> {quem convidou, até quando} */
    private final Map<UUID, Object[]> convites = new HashMap<>();
    private final Map<UUID, Long> ultimoTeleporte = new HashMap<>();

    public GrupoService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    public Grupo grupo(Player p) {
        return porJogador.get(p.getUniqueId());
    }

    public boolean mesmoGrupo(Player a, Player b) {
        Grupo g = porJogador.get(a.getUniqueId());
        return g != null && g.membros().contains(b.getUniqueId());
    }

    /** Membros do grupo (incluindo o próprio jogador) no mesmo mundo e a até "raio" blocos. */
    public List<Player> pertoDe(Player p, double raio) {
        Grupo g = grupo(p);
        if (g == null) {
            return List.of(p);
        }
        List<Player> l = new ArrayList<>();
        for (Player m : g.online()) {
            if (m.getWorld().equals(p.getWorld()) && m.getLocation().distanceSquared(p.getLocation()) <= raio * raio) {
                l.add(m);
            }
        }
        return l;
    }

    private int maximo() {
        return plugin.getConfig().getInt("grupo.maximo", 5);
    }

    public void convidar(Player lider, Player alvo) {
        if (lider.equals(alvo)) {
            plugin.mensagens().enviar(lider, "grupo-voce-mesmo");
            return;
        }
        Grupo g = porJogador.get(lider.getUniqueId());
        if (g != null && !g.lider().equals(lider.getUniqueId())) {
            plugin.mensagens().enviar(lider, "grupo-so-lider");
            return;
        }
        if (porJogador.containsKey(alvo.getUniqueId())) {
            plugin.mensagens().enviar(lider, "grupo-ja-tem", "jogador", alvo.getName());
            return;
        }
        if (g != null && g.membros().size() >= maximo()) {
            plugin.mensagens().enviar(lider, "grupo-cheio", "maximo", String.valueOf(maximo()));
            return;
        }
        convites.put(alvo.getUniqueId(), new Object[]{lider.getUniqueId(), System.currentTimeMillis() + 60_000L});
        plugin.mensagens().enviar(lider, "grupo-convidou", "jogador", alvo.getName());
        plugin.mensagens().enviar(alvo, "grupo-convite", "jogador", lider.getName());
    }

    public void aceitar(Player p) {
        Object[] c = convites.remove(p.getUniqueId());
        if (c == null || (long) c[1] < System.currentTimeMillis()) {
            plugin.mensagens().enviar(p, "grupo-sem-convite");
            return;
        }
        Player lider = Bukkit.getPlayer((UUID) c[0]);
        if (lider == null) {
            plugin.mensagens().enviar(p, "grupo-sem-convite");
            return;
        }
        Grupo g = porJogador.computeIfAbsent(lider.getUniqueId(), k -> new Grupo(lider.getUniqueId()));
        if (g.membros().size() >= maximo()) {
            plugin.mensagens().enviar(p, "grupo-cheio", "maximo", String.valueOf(maximo()));
            return;
        }
        g.membros().add(p.getUniqueId());
        porJogador.put(p.getUniqueId(), g);
        for (Player m : g.online()) {
            plugin.mensagens().enviar(m, "grupo-entrou", "jogador", p.getName(), "total", String.valueOf(g.membros().size()));
        }
    }

    public void sair(Player p) {
        Grupo g = porJogador.remove(p.getUniqueId());
        if (g == null) {
            plugin.mensagens().enviar(p, "grupo-sem-grupo");
            return;
        }
        g.membros().remove(p.getUniqueId());
        plugin.mensagens().enviar(p, "grupo-saiu-voce");
        if (g.membros().size() <= 1) {
            for (UUID u : g.membros()) {
                porJogador.remove(u);
                Player o = Bukkit.getPlayer(u);
                if (o != null) {
                    plugin.mensagens().enviar(o, "grupo-desfeito");
                }
            }
            return;
        }
        if (g.lider().equals(p.getUniqueId())) {
            g.lider = g.membros().iterator().next();
        }
        for (Player m : g.online()) {
            plugin.mensagens().enviar(m, "grupo-saiu", "jogador", p.getName(), "lider", nome(g.lider()));
        }
    }

    public void expulsar(Player lider, String nome) {
        Grupo g = grupo(lider);
        if (g == null || !g.lider().equals(lider.getUniqueId())) {
            plugin.mensagens().enviar(lider, "grupo-so-lider");
            return;
        }
        for (UUID u : g.membros()) {
            if (nome.equalsIgnoreCase(nome(u)) && !u.equals(lider.getUniqueId())) {
                Player alvo = Bukkit.getPlayer(u);
                g.membros().remove(u);
                porJogador.remove(u);
                if (alvo != null) {
                    plugin.mensagens().enviar(alvo, "grupo-expulso");
                }
                for (Player m : g.online()) {
                    plugin.mensagens().enviar(m, "grupo-saiu", "jogador", nome, "lider", nome(g.lider()));
                }
                if (g.membros().size() <= 1) {
                    porJogador.remove(lider.getUniqueId());
                    plugin.mensagens().enviar(lider, "grupo-desfeito");
                }
                return;
            }
        }
        plugin.mensagens().enviar(lider, "grupo-nao-membro", "jogador", nome);
    }

    public void lista(Player p) {
        Grupo g = grupo(p);
        if (g == null) {
            plugin.mensagens().enviar(p, "grupo-sem-grupo");
            return;
        }
        plugin.mensagens().enviarSemPrefixo(p, "grupo-lista-cabecalho", "total", String.valueOf(g.membros().size()),
                "maximo", String.valueOf(maximo()));
        for (UUID u : g.membros()) {
            Player m = Bukkit.getPlayer(u);
            var perfil = m == null ? null : plugin.perfis().perfil(m);
            plugin.mensagens().enviarSemPrefixo(p, "grupo-lista-linha", "jogador", nome(u),
                    "lider", u.equals(g.lider()) ? " ♛" : "",
                    "info", m == null ? "<gray>offline" : "<gray>Nv " + perfil.nivel() + " " + (perfil.classe() == null ? "" : perfil.classe().formatado())
                            + " <red>❤ " + (int) Math.ceil(m.getHealth()));
        }
    }

    public void chat(Player p, String texto) {
        Grupo g = grupo(p);
        if (g == null) {
            plugin.mensagens().enviar(p, "grupo-sem-grupo");
            return;
        }
        for (Player m : g.online()) {
            m.sendMessage(Texto.cor(plugin.mensagens().bruta("grupo-chat"), "jogador", p.getName()).append(net.kyori.adventure.text.Component.text(texto)));
        }
    }

    /** Teleporta até o líder (com espera, para não virar teleporte grátis). */
    public void irAoLider(Player p) {
        Grupo g = grupo(p);
        if (g == null) {
            plugin.mensagens().enviar(p, "grupo-sem-grupo");
            return;
        }
        Player lider = Bukkit.getPlayer(g.lider());
        if (lider == null || lider.equals(p)) {
            plugin.mensagens().enviar(p, "grupo-lider-indisponivel");
            return;
        }
        long espera = plugin.getConfig().getLong("grupo.teleporte-espera-segundos", 300) * 1000L;
        long falta = ultimoTeleporte.getOrDefault(p.getUniqueId(), 0L) + espera - System.currentTimeMillis();
        if (falta > 0) {
            plugin.mensagens().enviar(p, "grupo-teleporte-espera", "segundos", String.valueOf(falta / 1000 + 1));
            return;
        }
        if (!plugin.mundoRpg(lider.getWorld())) {
            plugin.mensagens().enviar(p, "grupo-lider-indisponivel");
            return;
        }
        ultimoTeleporte.put(p.getUniqueId(), System.currentTimeMillis());
        p.teleport(lider.getLocation());
        plugin.mensagens().enviar(p, "grupo-teleportou", "jogador", lider.getName());
    }

    private static String nome(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    @EventHandler
    public void aoSairDoServidor(PlayerQuitEvent e) {
        convites.remove(e.getPlayer().getUniqueId());
        if (porJogador.containsKey(e.getPlayer().getUniqueId())) {
            sair(e.getPlayer());
        }
    }
}
