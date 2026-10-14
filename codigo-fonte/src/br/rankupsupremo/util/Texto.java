package br.rankupsupremo.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitário de textos: aceita MiniMessage e também os códigos clássicos (&a, &l, &#RRGGBB).
 */
public final class Texto {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGADO = new HashMap<>();

    static {
        LEGADO.put('0', "<reset><black>");
        LEGADO.put('1', "<reset><dark_blue>");
        LEGADO.put('2', "<reset><dark_green>");
        LEGADO.put('3', "<reset><dark_aqua>");
        LEGADO.put('4', "<reset><dark_red>");
        LEGADO.put('5', "<reset><dark_purple>");
        LEGADO.put('6', "<reset><gold>");
        LEGADO.put('7', "<reset><gray>");
        LEGADO.put('8', "<reset><dark_gray>");
        LEGADO.put('9', "<reset><blue>");
        LEGADO.put('a', "<reset><green>");
        LEGADO.put('b', "<reset><aqua>");
        LEGADO.put('c', "<reset><red>");
        LEGADO.put('d', "<reset><light_purple>");
        LEGADO.put('e', "<reset><yellow>");
        LEGADO.put('f', "<reset><white>");
        LEGADO.put('k', "<obfuscated>");
        LEGADO.put('l', "<bold>");
        LEGADO.put('m', "<strikethrough>");
        LEGADO.put('n', "<underlined>");
        LEGADO.put('o', "<italic>");
        LEGADO.put('r', "<reset>");
    }

    private Texto() {
    }

    /** Converte códigos clássicos (& e §) para tags MiniMessage. */
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

    /** Substitui {chave} pelos valores informados em pares (chave, valor, chave, valor...). */
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

    public static Component cor(String s, TagResolver resolver) {
        return MM.deserialize(converterLegado(s), resolver);
    }

    /** Remove o itálico padrão que o Minecraft aplica em nomes/lore de itens. */
    public static Component item(String s, String... pares) {
        return semItalico(cor(s, pares));
    }

    public static Component semItalico(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(List<String> linhas, String... pares) {
        return linhas.stream().map(l -> item(l, pares)).toList();
    }

    public static String plano(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Junta várias linhas em um único componente separado por quebras de linha. */
    public static Component linhas(List<Component> linhas) {
        Component r = Component.empty();
        for (int i = 0; i < linhas.size(); i++) {
            if (i > 0) {
                r = r.append(Component.newline());
            }
            r = r.append(linhas.get(i));
        }
        return r;
    }
}
