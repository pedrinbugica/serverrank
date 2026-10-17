package dev.pedrobugica.npcvivos.core;

import java.util.Locale;

/**
 * O que o NPC faz. Um NPC pode ter vários papéis ao mesmo tempo.
 * Os papéis marcados "em breve" já podem ser dados, mas só ganham função nas próximas fases do plano.
 */
public enum Papel {
    COMERCIANTE("Comerciante", true),
    FERREIRO("Ferreiro", true),
    GUIA("Guia", true),
    GUARDA("Guarda", true),
    MERCENARIO("Mercenário", true),
    MISSIONARIO("Missões", true),
    LIDER("Líder de facção", false),
    DUELISTA("Duelista", true),
    INVASOR("Invasor", true),
    BOSS("Chefe", true),
    MERCADOR_VIAJANTE("Mercador viajante", false),
    BANQUEIRO("Banqueiro", false);

    private final String nome;
    private final boolean pronto;

    Papel(String nome, boolean pronto) {
        this.nome = nome;
        this.pronto = pronto;
    }

    public String nome() {
        return nome;
    }

    /** Já funciona nesta versão? */
    public boolean pronto() {
        return pronto;
    }

    public static Papel buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Papel p : values()) {
            if (p.name().equals(n)) {
                return p;
            }
        }
        return null;
    }
}
