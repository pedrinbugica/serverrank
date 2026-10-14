package br.rankupsupremo.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatação de números no padrão brasileiro (1.234.567,89) e abreviações (1,5K, 2,3M...).
 */
public final class Numeros {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final String[] SUFIXOS = {"", "K", "M", "B", "T", "Q", "QQ", "S", "SS", "O", "N", "D"};

    private static final ThreadLocal<DecimalFormat> DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(BR)));
    private static final ThreadLocal<DecimalFormat> INTEIRO =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(BR)));

    private Numeros() {
    }

    /** 1234 -> 1,23K | 2500000 -> 2,5M */
    public static String abreviar(double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            return "0";
        }
        double abs = Math.abs(valor);
        int i = 0;
        while (abs >= 1000 && i < SUFIXOS.length - 1) {
            abs /= 1000;
            i++;
        }
        String r = DECIMAL.get().format(abs) + SUFIXOS[i];
        return valor < 0 ? "-" + r : r;
    }

    /** 1234567.8 -> 1.234.567,8 */
    public static String completo(double valor) {
        return DECIMAL.get().format(valor);
    }

    public static String inteiro(long valor) {
        return INTEIRO.get().format(valor);
    }

    /** 1.35 -> 1,35 */
    public static String multiplicador(double valor) {
        return DECIMAL.get().format(valor);
    }

    /** Segundos para "mm:ss" ou "1h 05m". */
    public static String tempo(long segundos) {
        if (segundos < 0) {
            segundos = 0;
        }
        long h = segundos / 3600;
        long m = (segundos % 3600) / 60;
        long s = segundos % 60;
        if (h > 0) {
            return h + "h " + String.format("%02dm", m);
        }
        return String.format("%02d:%02d", m, s);
    }

    /** Barra de progresso em MiniMessage. */
    public static String barra(double fracao, int tamanho, String simbolo, String corCheia, String corVazia) {
        if (fracao < 0) {
            fracao = 0;
        }
        if (fracao > 1) {
            fracao = 1;
        }
        int cheios = (int) Math.round(fracao * tamanho);
        return corCheia + simbolo.repeat(cheios) + corVazia + simbolo.repeat(tamanho - cheios);
    }

    public static String porcentagem(double fracao) {
        if (fracao < 0) {
            fracao = 0;
        }
        if (fracao > 1) {
            fracao = 1;
        }
        return DECIMAL.get().format(Math.floor(fracao * 1000) / 10.0);
    }
}
