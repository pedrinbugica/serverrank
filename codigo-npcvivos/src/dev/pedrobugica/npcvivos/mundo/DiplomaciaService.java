package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Como as alianças mudam sozinhas (fase 7): cada par de facções tem uma pontuação de -100 a +100.
 * Eventos mexem nela (um membro de A mata um NPC de B: -20; as duas vencem um chefe juntas: +30; uma troca
 * entre comerciantes: +5) e, quando ela passa de um limite, o líder "decide" mudar a relação e o servidor anuncia.
 * Com o tempo a pontuação volta devagar para a relação dos arquivos (as mágoas cicatrizam).
 */
public final class DiplomaciaService {

    private final NpcVivosPlugin plugin;

    public DiplomaciaService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private MundoVivoService cfg() {
        return plugin.mundo();
    }

    static String par(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Pontuação "natural" de cada relação (onde ela começa e para onde volta). */
    public double inicial(Relacao r) {
        return cfg().num("relacoes.inicial." + r.name(), switch (r) {
            case ALIADA -> 70;
            case NEUTRA -> 0;
            case RIVAL -> -40;
            case GUERRA -> -80;
        });
    }

    public double pontuacao(String a, String b) {
        String v = plugin.repositorio().mundo("relacao|" + par(a, b));
        if (v != null) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException ignorado) {
                // cai no valor inicial
            }
        }
        return inicial(plugin.faccoes().relacao(a, b));
    }

    private void gravar(String a, String b, double valor) {
        plugin.repositorio().setMundo("relacao|" + par(a, b), String.valueOf(Math.round(valor * 10) / 10.0));
    }

    /** A relação que esta pontuação pede. */
    public Relacao relacaoPara(double pontos) {
        if (pontos >= cfg().num("relacoes.limites.ALIADA", 50)) {
            return Relacao.ALIADA;
        }
        if (pontos >= cfg().num("relacoes.limites.NEUTRA", -20)) {
            return Relacao.NEUTRA;
        }
        if (pontos >= cfg().num("relacoes.limites.RIVAL", -60)) {
            return Relacao.RIVAL;
        }
        return Relacao.GUERRA;
    }

    /** Soma (ou tira) pontos entre duas facções e, se passou do limite, muda a relação. */
    public void alterar(String a, String b, double delta, String motivo) {
        if (a == null || b == null || a.equals(b) || delta == 0
                || plugin.faccoes().obter(a) == null || plugin.faccoes().obter(b) == null) {
            return;
        }
        double novo = Math.max(-100, Math.min(100, pontuacao(a, b) + delta));
        gravar(a, b, novo);
        avaliar(a, b, motivo);
    }

    /** Staff mudou a relação na mão: a pontuação vai para o valor natural dela (a automação não desfaz na hora). */
    public void definirPelaRelacao(String a, String b, Relacao r) {
        gravar(a, b, inicial(r));
        plugin.repositorio().setMundo("relacao-mudou|" + par(a, b), String.valueOf(System.currentTimeMillis()));
    }

    public void definir(String a, String b, double valor) {
        gravar(a, b, Math.max(-100, Math.min(100, valor)));
        avaliar(a, b, "decisão da staff");
    }

    private void avaliar(String a, String b, String motivo) {
        if (!cfg().sim("relacoes.automaticas", true) || plugin.guerras().entre(a, b) != null) {
            return; // durante a guerra a relação fica travada até ela acabar
        }
        Relacao atual = plugin.faccoes().relacao(a, b);
        Relacao alvo = relacaoPara(pontuacao(a, b));
        if (alvo == atual) {
            return;
        }
        long espera = (long) (cfg().num("relacoes.espera-entre-mudancas-horas", 6) * 3_600_000L);
        String ultima = plugin.repositorio().mundo("relacao-mudou|" + par(a, b));
        if (ultima != null && System.currentTimeMillis() - Long.parseLong(ultima) < espera) {
            return;
        }
        Faccao fa = plugin.faccoes().obter(a);
        Faccao fb = plugin.faccoes().obter(b);
        plugin.repositorio().setMundo("relacao-mudou|" + par(a, b), String.valueOf(System.currentTimeMillis()));
        String lider = nomeLider(fa);
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(p, "diplomacia-decisao", "lider", lider, "a", fa.nome(), "b", fb.nome(),
                    "motivo", motivo == null ? "" : motivo);
        }
        if (alvo == Relacao.GUERRA) {
            plugin.guerras().iniciar(fa, fb, -1, motivo);
        } else {
            plugin.faccoes().mudarRelacao(fa, fb, alvo);
        }
        plugin.repositorio().salvarAssincrono();
    }

    private String nomeLider(Faccao f) {
        NpcVivo l = f.lider() == null || f.lider().isBlank() ? null : plugin.npcs().obter(f.lider());
        return l != null ? l.nome() : "<gray>O conselho";
    }

    /** A cada hora a pontuação anda de volta para o natural da relação dos arquivos. */
    public void cicatrizar() {
        double passo = cfg().num("relacoes.volta-por-hora", 2);
        if (passo <= 0) {
            return;
        }
        List<Faccao> todas = plugin.faccoes().todas();
        for (int i = 0; i < todas.size(); i++) {
            for (int j = i + 1; j < todas.size(); j++) {
                String a = todas.get(i).id();
                String b = todas.get(j).id();
                if (plugin.guerras().entre(a, b) != null) {
                    continue;
                }
                double atual = pontuacao(a, b);
                double natural = inicial(plugin.faccoes().relacaoPadrao(a, b));
                if (Math.abs(atual - natural) < 0.01) {
                    continue;
                }
                double novo = atual < natural ? Math.min(natural, atual + passo) : Math.max(natural, atual - passo);
                gravar(a, b, novo);
                avaliar(a, b, "o tempo acalmou os ânimos");
            }
        }
    }

    // ------------------------------------------------------------------ EVENTOS

    /** Um jogador matou um NPC de combate: a facção dele perde pontos com a do NPC. */
    public void aoMatarNpc(Player autor, NpcVivo npc) {
        Faccao doJogador = plugin.faccoes().membro(autor);
        if (doJogador != null && npc.faccao() != null) {
            alterar(doJogador.id(), npc.faccao(), cfg().num("relacoes.eventos.matar-npc", -20),
                    Texto.semCor(doJogador.nome()) + " matou alguém de " + Texto.semCor(nomeFaccao(npc.faccao())));
        }
    }

    public void aoMatarJogador(Player autor, Player vitima) {
        Faccao a = plugin.faccoes().membro(autor);
        Faccao b = plugin.faccoes().membro(vitima);
        if (a != null && b != null) {
            alterar(a.id(), b.id(), cfg().num("relacoes.eventos.matar-membro", -5),
                    "sangue derramado entre " + Texto.semCor(a.nome()) + " e " + Texto.semCor(b.nome()));
        }
    }

    /** Um chefe caiu: toda dupla de facções que lutou junto ganha pontos. */
    public void aoVencerChefe(Collection<UUID> lutaram, NpcVivo chefe) {
        Set<String> faccoes = new LinkedHashSet<>();
        for (UUID u : lutaram) {
            String f = plugin.repositorio().membro(u);
            if (f != null && plugin.faccoes().obter(f) != null) {
                faccoes.add(f);
            }
        }
        List<String> l = List.copyOf(faccoes);
        for (int i = 0; i < l.size(); i++) {
            for (int j = i + 1; j < l.size(); j++) {
                alterar(l.get(i), l.get(j), cfg().num("relacoes.eventos.boss-juntos", 30),
                        "venceram " + Texto.semCor(chefe.nome()) + " juntas");
            }
        }
    }

    private String nomeFaccao(String id) {
        Faccao f = plugin.faccoes().obter(id);
        return f == null ? id : f.nome();
    }
}
