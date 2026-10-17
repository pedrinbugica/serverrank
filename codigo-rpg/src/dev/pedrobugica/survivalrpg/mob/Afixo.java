package dev.pedrobugica.survivalrpg.mob;

import java.util.Locale;

/** Poderes especiais de mobs elite e campeões. */
public enum Afixo {
    RAPIDO("Rápido"),
    VAMPIRO("Vampiro"),
    EXPLOSIVO("Explosivo"),
    INVOCADOR("Invocador"),
    BLINDADO("Blindado"),
    CONGELANTE("Congelante");

    private final String nome;

    Afixo(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }

    public static Afixo buscar(String s) {
        try {
            return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
