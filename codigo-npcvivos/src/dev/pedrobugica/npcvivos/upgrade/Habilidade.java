package dev.pedrobugica.npcvivos.upgrade;

import java.util.Locale;

/** Habilidade especial que uma arma ganha ao chegar no +9 (fica mais forte no +10). */
public enum Habilidade {
    ROUBO_DE_VIDA("Roubo de vida", "roubo-de-vida"),
    DANO_EM_AREA("Dano em área", "dano-em-area"),
    LENTIDAO("Lentidão no alvo", "lentidao");

    private final String nome;
    private final String chave;

    Habilidade(String nome, String chave) {
        this.nome = nome;
        this.chave = chave;
    }

    public String nome() {
        return nome;
    }

    /** Seção no upgrades.yml (habilidades.<chave>). */
    public String chave() {
        return chave;
    }

    public static Habilidade buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Habilidade h : values()) {
            if (h.name().equals(n)) {
                return h;
            }
        }
        return null;
    }
}
