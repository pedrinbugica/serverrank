package dev.pedrobugica.npcvivos.mundo;

import net.kyori.adventure.bossbar.BossBar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Uma guerra entre duas facções: placar, prazo, meta e quem fez pontos (para a recompensa no fim). */
public final class Guerra {

    private final String a;
    private final String b;
    private final long inicio;
    private final long fim;
    private final int meta;
    private int pontosA;
    private int pontosB;
    /** jogador -> pontos que fez pela facção dele (a facção fica no mapa certo). */
    private final Map<UUID, Integer> ajudaA = new LinkedHashMap<>();
    private final Map<UUID, Integer> ajudaB = new LinkedHashMap<>();
    private BossBar barra;

    public Guerra(String a, String b, long inicio, long fim, int meta) {
        this.a = a;
        this.b = b;
        this.inicio = inicio;
        this.fim = fim;
        this.meta = meta;
    }

    public String a() {
        return a;
    }

    public String b() {
        return b;
    }

    public long inicio() {
        return inicio;
    }

    public long fim() {
        return fim;
    }

    public int meta() {
        return meta;
    }

    public int pontosA() {
        return pontosA;
    }

    public int pontosB() {
        return pontosB;
    }

    public boolean envolve(String faccao) {
        return a.equals(faccao) || b.equals(faccao);
    }

    public String inimiga(String faccao) {
        return a.equals(faccao) ? b : a;
    }

    public int pontosDe(String faccao) {
        return a.equals(faccao) ? pontosA : pontosB;
    }

    /** Soma pontos para a facção (e para o jogador que fez, se houver). */
    public void somar(String faccao, int pontos, UUID jogador) {
        if (a.equals(faccao)) {
            pontosA += pontos;
            if (jogador != null) {
                ajudaA.merge(jogador, pontos, Integer::sum);
            }
        } else if (b.equals(faccao)) {
            pontosB += pontos;
            if (jogador != null) {
                ajudaB.merge(jogador, pontos, Integer::sum);
            }
        }
    }

    void definirPontos(int pa, int pb) {
        this.pontosA = pa;
        this.pontosB = pb;
    }

    public Map<UUID, Integer> ajuda(String faccao) {
        return a.equals(faccao) ? ajudaA : ajudaB;
    }

    BossBar barra() {
        return barra;
    }

    void setBarra(BossBar barra) {
        this.barra = barra;
    }
}
