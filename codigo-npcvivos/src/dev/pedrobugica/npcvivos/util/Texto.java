package dev.pedrobugica.npcvivos.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Textos com MiniMessage (<red>, <bold>...) e também com os códigos clássicos (&a, &l, &#FFAA00).
 */
public final class Texto {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGADO = new HashMap<>();

    static {
        String[][] tabela = {{"0", "black"}, {"1", "dark_blue"}, {"2", "dark_green"}, {"3", "dark_aqua"}, {"4", "dark_red"},
                {"5", "dark_purple"}, {"6", "gold"}, {"7", "gray"}, {"8", "dark_gray"}, {"9", "blue"}, {"a", "green"},
                {"b", "aqua"}, {"c", "red"}, {"d", "light_purple"}, {"e", "yellow"}, {"f", "white"}};
        for (String[] t : tabela) {
            LEGADO.put(t[0].charAt(0), "<reset><" + t[1] + ">");
        }
        LEGADO.put('k', "<obfuscated>");
        LEGADO.put('l', "<bold>");
        LEGADO.put('m', "<strikethrough>");
        LEGADO.put('n', "<underlined>");
        LEGADO.put('o', "<italic>");
        LEGADO.put('r', "<reset>");
    }

    private Texto() {
    }

    public static String converterLegado(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('&') < 0 && s.indexOf('§') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                char n = Character.toLowerCase(s.charAt(i + 1));
                if (n == '#' && i + 7 < s.length() && hex(s, i + 2)) {
                    sb.append("<reset><#").append(s, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = LEGADO.get(n);
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean hex(String s, int inicio) {
        for (int i = inicio; i < inicio + 6; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Substitui {chave} pelos valores (chave, valor, chave, valor...). */
    public static String substituir(String s, String... pares) {
        if (s == null) {
            return "";
        }
        String r = s;
        for (int i = 0; i + 1 < pares.length; i += 2) {
            r = r.replace("{" + pares[i] + "}", pares[i + 1] == null ? "" : pares[i + 1]);
        }
        return r;
    }

    public static Component cor(String s, String... pares) {
        return MM.deserialize(converterLegado(substituir(s, pares)));
    }

    /** Para nomes e lore de itens: sem o itálico que o jogo põe sozinho. */
    public static Component item(String s, String... pares) {
        return cor(s, pares).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(List<String> linhas, String... pares) {
        return linhas.stream().map(l -> item(l, pares)).toList();
    }

    public static String plano(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Nome sem cores (para logs e comparações). */
    public static String semCor(String s) {
        return plano(cor(s));
    }

    public static String dinheiro(double v) {
        if (v >= 1_000_000_000) {
            return String.format(java.util.Locale.ROOT, "%.2fB", v / 1e9).replace('.', ',');
        }
        if (v >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.2fM", v / 1e6).replace('.', ',');
        }
        if (v >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", v / 1e3).replace('.', ',');
        }
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',');
    }
}
