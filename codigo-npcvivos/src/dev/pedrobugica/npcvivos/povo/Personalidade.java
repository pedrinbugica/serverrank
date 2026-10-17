package dev.pedrobugica.npcvivos.povo;

import java.util.Locale;

/**
 * O jeito de cada morador (como no MCA): muda a chance de cada interação dar certo e o que ele fala.
 * Os números são somados à chance base (0,25 = +25%). "ganancia" multiplica o valor de presentes caros.
 */
public enum Personalidade {
    SIMPATICO("Simpático", "Simpática", 0.15, 0.05, 0.10, 0.10, 0.00, 1.0),
    RABUGENTO("Rabugento", "Rabugenta", -0.20, -0.30, -0.10, -0.25, -0.15, 1.0),
    TIMIDO("Tímido", "Tímida", -0.05, 0.00, 0.10, -0.25, -0.15, 1.0),
    ENGRACADO("Engraçado", "Engraçada", 0.05, 0.30, 0.00, 0.05, 0.05, 1.0),
    ROMANTICO("Romântico", "Romântica", 0.05, 0.00, 0.10, 0.15, 0.30, 1.0),
    ORGULHOSO("Orgulhoso", "Orgulhosa", -0.05, -0.10, 0.30, -0.10, 0.00, 1.2),
    PREGUICOSO("Preguiçoso", "Preguiçosa", -0.10, 0.10, 0.00, 0.10, 0.00, 1.0),
    CORAJOSO("Corajoso", "Corajosa", 0.05, 0.05, 0.05, 0.05, 0.05, 1.0),
    GANANCIOSO("Ganancioso", "Gananciosa", -0.05, -0.05, 0.00, -0.10, -0.05, 2.0),
    SONHADOR("Sonhador", "Sonhadora", 0.10, 0.10, 0.05, 0.05, 0.15, 1.0);

    private final String masculino;
    private final String feminino;
    final double conversa;
    final double piada;
    final double elogio;
    final double abraco;
    final double flerte;
    final double ganancia;

    Personalidade(String masculino, String feminino, double conversa, double piada, double elogio, double abraco,
                  double flerte, double ganancia) {
        this.masculino = masculino;
        this.feminino = feminino;
        this.conversa = conversa;
        this.piada = piada;
        this.elogio = elogio;
        this.abraco = abraco;
        this.flerte = flerte;
        this.ganancia = ganancia;
    }

    public String nome(boolean feminina) {
        return feminina ? feminino : masculino;
    }

    public String chave() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Personalidade buscar(String s) {
        if (s == null) {
            return SIMPATICO;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SIMPATICO;
        }
    }
}
