package dev.pedrobugica.npcvivos.faccao;

import java.util.Locale;

/** Como duas facções se tratam. */
public enum Relacao {
    ALIADA("Aliada", "<green>"),
    NEUTRA("Neutra", "<gray>"),
    RIVAL("Rival", "<gold>"),
    GUERRA("Em guerra", "<dark_red>");

    private final String nome;
    private final String cor;

    Relacao(String nome, String cor) {
        this.nome = nome;
        this.cor = cor;
    }

    public String nome() {
        return nome;
    }

    public String cor() {
        return cor;
    }

    public static Relacao buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT);
        if (n.equals("ALIADO") || n.equals("ALIADOS")) {
            n = "ALIADA";
        } else if (n.equals("NEUTRO")) {
            n = "NEUTRA";
        } else if (n.equals("EM_GUERRA") || n.equals("INIMIGA")) {
            n = "GUERRA";
        }
        for (Relacao r : values()) {
            if (r.name().equals(n)) {
                return r;
            }
        }
        return null;
    }
}
