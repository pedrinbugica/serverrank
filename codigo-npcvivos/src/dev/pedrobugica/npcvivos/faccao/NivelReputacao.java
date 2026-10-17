package dev.pedrobugica.npcvivos.faccao;

import java.util.Locale;

/**
 * Níveis de reputação (de -1000 a +1000 por facção), como no documento de design:
 * Inimigo: guardas atacam e lojas fecham | Hostil: preços 50% mais caros e sem missões | Neutro: normal
 * Amigo: 10% de desconto e missões especiais | Herói: 20% de desconto.
 */
public enum NivelReputacao {
    INIMIGO("Inimigo", "<dark_red>", -1000, -501, 0, 0),
    HOSTIL("Hostil", "<red>", -500, -101, 1.5, 0.75),
    NEUTRO("Neutro", "<gray>", -100, 100, 1.0, 1.0),
    AMIGO("Amigo", "<green>", 101, 500, 0.9, 1.05),
    HEROI("Herói", "<gold>", 501, 1000, 0.8, 1.10);

    public static final int MINIMO = -1000;
    public static final int MAXIMO = 1000;

    private final String nome;
    private final String cor;
    private final int de;
    private final int ate;
    private final double precoCompra;
    private final double precoVenda;

    NivelReputacao(String nome, String cor, int de, int ate, double precoCompra, double precoVenda) {
        this.nome = nome;
        this.cor = cor;
        this.de = de;
        this.ate = ate;
        this.precoCompra = precoCompra;
        this.precoVenda = precoVenda;
    }

    public String nome() {
        return nome;
    }

    public String cor() {
        return cor;
    }

    public int de() {
        return de;
    }

    public int ate() {
        return ate;
    }

    /** Multiplicador do preço que o jogador paga ao comprar. */
    public double precoCompra() {
        return precoCompra;
    }

    /** Multiplicador do que o jogador recebe ao vender. */
    public double precoVenda() {
        return precoVenda;
    }

    public String formatado() {
        return cor + nome;
    }

    public static NivelReputacao de(int pontos) {
        for (NivelReputacao n : values()) {
            if (pontos >= n.de && pontos <= n.ate) {
                return n;
            }
        }
        return pontos < 0 ? INIMIGO : HEROI;
    }

    public static NivelReputacao buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("É", "E");
        for (NivelReputacao v : values()) {
            if (v.name().equals(n)) {
                return v;
            }
        }
        return null;
    }
}
