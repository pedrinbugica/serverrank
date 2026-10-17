package dev.pedrobugica.survivalrpg.item;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/** Raridade dos itens de RPG: quanto mais rara, mais atributos e mais fortes. */
public enum Raridade {
    COMUM("Comum", NamedTextColor.WHITE, 0, 1),
    INCOMUM("Incomum", NamedTextColor.GREEN, 1, 2),
    RARO("Raro", NamedTextColor.AQUA, 2, 3),
    EPICO("Épico", NamedTextColor.LIGHT_PURPLE, 3, 4),
    LENDARIO("Lendário", NamedTextColor.GOLD, 4, 4);

    private final String nome;
    private final NamedTextColor cor;
    private final int minAtributos;
    private final int maxAtributos;

    Raridade(String nome, NamedTextColor cor, int minAtributos, int maxAtributos) {
        this.nome = nome;
        this.cor = cor;
        this.minAtributos = minAtributos;
        this.maxAtributos = maxAtributos;
    }

    public String nome() {
        return nome;
    }

    public NamedTextColor cor() {
        return cor;
    }

    public String tag() {
        return "<" + NamedTextColor.NAMES.key(cor) + ">";
    }

    public int minAtributos() {
        return minAtributos;
    }

    public int maxAtributos() {
        return maxAtributos;
    }

    /** Multiplica os valores dos atributos (Lendário = mais forte). */
    public double forca() {
        return 1 + ordinal() * 0.35;
    }

    public boolean temEspecial() {
        return ordinal() >= EPICO.ordinal();
    }

    public static Raridade buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("É", "E").replace("Á", "A");
        for (Raridade r : values()) {
            if (r.name().equals(n)) {
                return r;
            }
        }
        return null;
    }
}
