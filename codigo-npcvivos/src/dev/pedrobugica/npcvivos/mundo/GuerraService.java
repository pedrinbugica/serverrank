package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import dev.pedrobugica.npcvivos.missao.Missao;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guerras de facção (fase 7). Duram um tempo fixo (padrão 3 dias) ou até uma facção chegar na meta de pontos.
 * Pontos: matar NPCs e jogadores da inimiga, missões de guerra e invasões. No fim, quem fez mais pontos vence:
 * as lojas dela ficam mais baratas por uma semana e quem ajudou ganha dinheiro e reputação.
 */
public final class GuerraService {

    private final NpcVivosPlugin plugin;
    private final List<Guerra> guerras = new ArrayList<>();

    public GuerraService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private MundoVivoService cfg() {
        return plugin.mundo();
    }

    // ------------------------------------------------------------------ CONSULTAS

    public List<Guerra> ativas() {
        return List.copyOf(guerras);
    }

    public Guerra entre(String a, String b) {
        if (a == null || b == null) {
            return null;
        }
        for (Guerra g : guerras) {
            if (g.envolve(a) && g.envolve(b) && !a.equals(b)) {
                return g;
            }
        }
        return null;
    }

    public boolean emGuerra(String faccao) {
        for (Guerra g : guerras) {
            if (g.envolve(faccao)) {
                return true;
            }
        }
        return false;
    }

    /** Guerra da facção do jogador (a primeira, se houver mais de uma). */
    public Guerra daFaccao(String faccao) {
        for (Guerra g : guerras) {
            if (faccao != null && g.envolve(faccao)) {
                return g;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ COMEÇO

    /**
     * Começa uma guerra (horas <= 0 = duração padrão). Anuncia, muda a relação para "Em guerra" e mostra o placar.
     * Devolve false se as duas já estavam em guerra.
     */
    public boolean iniciar(Faccao a, Faccao b, double horas, String motivo) {
        if (a == null || b == null || a.id().equals(b.id()) || entre(a.id(), b.id()) != null) {
            return false;
        }
        double h = horas > 0 ? horas : cfg().num("guerras.duracao-horas", 72);
        long agora = System.currentTimeMillis();
        Guerra g = new Guerra(a.id(), b.id(), agora, agora + (long) (h * 3_600_000L), Math.max(1, cfg().inteiro("guerras.meta-de-pontos", 500)));
        guerras.add(g);
        gravar(g);
        plugin.faccoes().mudarRelacao(a, b, Relacao.GUERRA);
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(p, "guerra-iniciada", "a", a.nome(), "b", b.nome(), "tempo", tempo(g.fim() - agora),
                    "meta", String.valueOf(g.meta()));
            if (motivo != null && !motivo.isBlank()) {
                plugin.mensagens().enviarSemPrefixo(p, "guerra-motivo", "motivo", motivo);
            }
        }
        criarBarra(g);
        Bukkit.getPluginManager().callEvent(new FaccaoGuerraEvent(FaccaoGuerraEvent.Momento.INICIO, a.id(), b.id(), null));
        plugin.repositorio().salvarAssincrono();
        return true;
    }

    // ------------------------------------------------------------------ PONTOS

    /** Soma pontos de guerra para a facção (contra a inimiga). O jogador recebe o aviso e entra na recompensa. */
    public void pontuar(String faccao, String inimiga, int pontos, Player jogador) {
        Guerra g = entre(faccao, inimiga);
        if (g == null || pontos <= 0) {
            return;
        }
        g.somar(faccao, pontos, jogador == null ? null : jogador.getUniqueId());
        gravar(g);
        if (jogador != null) {
            Faccao f = plugin.faccoes().obter(faccao);
            jogador.sendActionBar(Texto.cor(plugin.mensagens().bruta("guerra-pontos"), "pontos", String.valueOf(pontos),
                    "faccao", f == null ? faccao : f.nome(), "placar", g.pontosA() + " x " + g.pontosB()));
        }
        atualizarBarra(g);
        if (g.pontosA() >= g.meta() || g.pontosB() >= g.meta()) {
            terminar(g);
        }
    }

    /** Membro de uma facção matou um NPC de combate da inimiga. */
    public void aoMatarNpc(Player autor, NpcVivo npc) {
        Faccao f = plugin.faccoes().membro(autor);
        if (f != null && npc.faccao() != null) {
            pontuar(f.id(), npc.faccao(), cfg().inteiro("guerras.pontos.matar-npc", 10), autor);
        }
    }

    public void aoMatarJogador(Player autor, Player vitima) {
        Faccao a = plugin.faccoes().membro(autor);
        Faccao b = plugin.faccoes().membro(vitima);
        if (a != null && b != null) {
            pontuar(a.id(), b.id(), cfg().inteiro("guerras.pontos.matar-jogador", 15), autor);
        }
    }

    /** Missão com "pontos_guerra": vale para a facção do NPC que deu a missão, contra cada inimiga em guerra. */
    public void aoConcluirMissao(Player p, NpcVivo npc, Missao m) {
        if (m.pontosGuerra() <= 0 || npc.faccao() == null) {
            return;
        }
        for (Guerra g : List.copyOf(guerras)) {
            if (g.envolve(npc.faccao())) {
                pontuar(npc.faccao(), g.inimiga(npc.faccao()), m.pontosGuerra(), p);
            }
        }
    }

    // ------------------------------------------------------------------ FIM

    /** Chamado a cada 20 segundos: termina guerras que passaram do prazo e atualiza o placar no topo da tela. */
    public void verificar() {
        long agora = System.currentTimeMillis();
        for (Guerra g : List.copyOf(guerras)) {
            if (agora >= g.fim()) {
                terminar(g);
            } else {
                atualizarBarra(g);
            }
        }
    }

    /** Termina a guerra: decide quem venceu, paga quem ajudou e volta as facções para rivais. */
    public void terminar(Guerra g) {
        if (!guerras.remove(g)) {
            return;
        }
        esconderBarra(g);
        apagar(g);
        Faccao fa = plugin.faccoes().obter(g.a());
        Faccao fb = plugin.faccoes().obter(g.b());
        String vencedora = g.pontosA() == g.pontosB() ? null : (g.pontosA() > g.pontosB() ? g.a() : g.b());
        int dias = cfg().inteiro("precos.vitoria-dias", 7);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (vencedora == null) {
                plugin.mensagens().enviar(p, "guerra-empate", "a", nome(fa, g.a()), "b", nome(fb, g.b()),
                        "pa", String.valueOf(g.pontosA()), "pb", String.valueOf(g.pontosB()));
            } else {
                Faccao v = plugin.faccoes().obter(vencedora);
                Faccao perdedora = plugin.faccoes().obter(g.inimiga(vencedora));
                plugin.mensagens().enviar(p, "guerra-fim", "vencedor", nome(v, vencedora), "perdedor", nome(perdedora, g.inimiga(vencedora)),
                        "pv", String.valueOf(g.pontosDe(vencedora)), "pp", String.valueOf(g.pontosDe(g.inimiga(vencedora))),
                        "dias", String.valueOf(dias));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        if (vencedora != null) {
            plugin.mercado().darBonusVitoria(vencedora, dias);
            recompensar(g, vencedora);
        }
        Relacao depois = Relacao.buscar(cfg().cfg().getString("guerras.relacao-depois", "RIVAL"));
        if (depois == null || depois == Relacao.GUERRA) {
            depois = Relacao.RIVAL;
        }
        if (fa != null && fb != null) {
            plugin.faccoes().mudarRelacao(fa, fb, depois);
            plugin.diplomacia().definirPelaRelacao(fa.id(), fb.id(), depois);
        }
        Bukkit.getPluginManager().callEvent(new FaccaoGuerraEvent(FaccaoGuerraEvent.Momento.FIM, g.a(), g.b(), vencedora));
        plugin.repositorio().salvarAssincrono();
    }

    private void recompensar(Guerra g, String vencedora) {
        double dinheiro = cfg().num("guerras.recompensa.dinheiro", 300);
        int rep = cfg().inteiro("guerras.recompensa.reputacao", 50);
        Faccao f = plugin.faccoes().obter(vencedora);
        for (Map.Entry<UUID, Integer> e : g.ajuda(vencedora).entrySet()) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(e.getKey());
            if (dinheiro > 0) {
                plugin.economia().depositar(o, dinheiro);
            }
            if (f != null && rep != 0) {
                plugin.faccoes().definirReputacao(e.getKey(), f, plugin.faccoes().reputacao(e.getKey(), vencedora) + rep);
            }
            Player p = o.getPlayer();
            if (p != null) {
                plugin.mensagens().enviar(p, "guerra-recompensa", "faccao", nome(f, vencedora), "dinheiro", Texto.dinheiro(dinheiro),
                        "rep", String.valueOf(rep));
            }
        }
    }

    private static String nome(Faccao f, String id) {
        return f == null ? id : f.nome();
    }

    // ------------------------------------------------------------------ PLACAR NO TOPO DA TELA

    private void criarBarra(Guerra g) {
        if (!cfg().sim("guerras.barra", true)) {
            return;
        }
        g.setBarra(BossBar.bossBar(Texto.cor(""), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS));
        atualizarBarra(g);
        for (Player p : Bukkit.getOnlinePlayers()) {
            mostrarSeFor(p, g);
        }
    }

    private void atualizarBarra(Guerra g) {
        BossBar b = g.barra();
        if (b == null) {
            return;
        }
        long agora = System.currentTimeMillis();
        Faccao fa = plugin.faccoes().obter(g.a());
        Faccao fb = plugin.faccoes().obter(g.b());
        b.name(Texto.cor(plugin.mensagens().bruta("guerra-barra"), "a", nome(fa, g.a()), "b", nome(fb, g.b()),
                "pa", String.valueOf(g.pontosA()), "pb", String.valueOf(g.pontosB()), "tempo", tempo(g.fim() - agora),
                "meta", String.valueOf(g.meta())));
        double total = Math.max(1, g.fim() - g.inicio());
        b.progress((float) Math.max(0, Math.min(1, (g.fim() - agora) / total)));
    }

    private void mostrarSeFor(Player p, Guerra g) {
        if (g.barra() == null) {
            return;
        }
        Faccao m = plugin.faccoes().membro(p);
        if (m != null && g.envolve(m.id())) {
            p.showBossBar(g.barra());
        } else {
            p.hideBossBar(g.barra());
        }
    }

    /** Jogador entrou ou trocou de facção: mostra (ou esconde) o placar das guerras dele. */
    public void atualizarJogador(Player p) {
        for (Guerra g : guerras) {
            mostrarSeFor(p, g);
        }
    }

    private void esconderBarra(Guerra g) {
        if (g.barra() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(g.barra());
            }
        }
    }

    public static String tempo(long ms) {
        long min = Math.max(0, ms / 60_000L);
        long dias = min / 1440;
        long horas = (min % 1440) / 60;
        long minutos = min % 60;
        if (dias > 0) {
            return dias + "d " + horas + "h";
        }
        if (horas > 0) {
            return horas + "h " + minutos + "min";
        }
        return minutos + "min";
    }

    // ------------------------------------------------------------------ DISCO

    private void gravar(Guerra g) {
        String par = DiplomaciaService.par(g.a(), g.b());
        plugin.repositorio().setMundo("guerra|" + par, g.a() + ";" + g.b() + ";" + g.inicio() + ";" + g.fim() + ";"
                + g.pontosA() + ";" + g.pontosB() + ";" + g.meta());
        for (String f : List.of(g.a(), g.b())) {
            for (Map.Entry<UUID, Integer> e : g.ajuda(f).entrySet()) {
                plugin.repositorio().setMundo("guerra-ajuda|" + par + "|" + e.getKey(), f + ";" + e.getValue());
            }
        }
    }

    private void apagar(Guerra g) {
        String par = DiplomaciaService.par(g.a(), g.b());
        plugin.repositorio().setMundo("guerra|" + par, null);
        for (String k : plugin.repositorio().mundoComPrefixo("guerra-ajuda|" + par + "|").keySet()) {
            plugin.repositorio().setMundo(k, null);
        }
    }

    /** Ao ligar o servidor: volta as guerras que estavam acontecendo. */
    public void carregar() {
        for (Guerra g : guerras) {
            esconderBarra(g);
        }
        guerras.clear();
        for (Map.Entry<String, String> e : plugin.repositorio().mundoComPrefixo("guerra|").entrySet()) {
            String[] v = e.getValue().split(";");
            try {
                Guerra g = new Guerra(v[0], v[1], Long.parseLong(v[2]), Long.parseLong(v[3]), Integer.parseInt(v[6]));
                g.definirPontos(Integer.parseInt(v[4]), Integer.parseInt(v[5]));
                String par = e.getKey().substring("guerra|".length());
                for (Map.Entry<String, String> a : plugin.repositorio().mundoComPrefixo("guerra-ajuda|" + par + "|").entrySet()) {
                    String[] av = a.getValue().split(";");
                    UUID u = UUID.fromString(a.getKey().substring(a.getKey().lastIndexOf('|') + 1));
                    g.ajuda(av[0]).put(u, Integer.parseInt(av[1]));
                }
                guerras.add(g);
                criarBarra(g);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Guerra inválida nos dados: " + e.getKey() + " = " + e.getValue());
            }
        }
    }

    /** Desligando: esconde os placares (a guerra continua salva e volta quando o servidor ligar). */
    public void parar() {
        for (Guerra g : guerras) {
            esconderBarra(g);
        }
    }
}
