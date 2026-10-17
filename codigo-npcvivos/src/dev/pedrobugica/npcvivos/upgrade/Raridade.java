package dev.pedrobugica.npcvivos.upgrade;

import java.util.Locale;

/**
 * Raridade de um item: define até que nível ele pode ser aprimorado (configurável no upgrades.yml).
 * Vem do material (ferro = Raro, diamante = Épico...) ou de uma recompensa de missão.
 */
public enum Raridade {
    COMUM("Comum", "<gray>"),
    RARO("Raro", "<aqua>"),
    EPICO("Épico", "<light_purple>"),
    LENDARIO("Lendário", "<gold>");

    private final String nome;
    private final String cor;

    Raridade(String nome, String cor) {
        this.nome = nome;
        this.cor = cor;
    }

    public String nome() {
        return nome;
    }

    public String cor() {
        return cor;
    }

    public String formatado() {
        return cor + nome;
    }

    public static Raridade buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("É", "E").replace("Á", "A");
        if (n.equals("RARA")) {
            n = "RARO";
        } else if (n.equals("EPICA")) {
            n = "EPICO";
        } else if (n.equals("LENDARIA")) {
            n = "LENDARIO";
        }
        for (Raridade r : values()) {
            if (r.name().equals(n)) {
                return r;
            }
        }
        return null;
    }
}
