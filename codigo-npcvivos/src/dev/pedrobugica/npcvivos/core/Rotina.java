package dev.pedrobugica.npcvivos.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * O que o NPC faz quando ninguém está falando com ele: fica parado, passeia em volta de casa ou patrulha uma rota.
 * Também guarda as falas "soltas" (balão em cima da cabeça) e se ele volta para casa à noite.
 *
 * @param raio   PASSEAR: até quantos blocos de casa ele anda
 * @param pontos PATRULHA: os pontos da rota, na ordem
 * @param falas  frases que ele diz sozinho de vez em quando ({jogador} = quem está perto)
 * @param dormir à noite volta para casa e fica parado
 */
public record Rotina(Tipo tipo, double raio, List<Vector> pontos, List<String> falas, boolean dormir) {

    public enum Tipo {
        PARADO, PASSEAR, PATRULHA;

        public static Tipo buscar(String s) {
            if (s == null) {
                return null;
            }
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public boolean anda() {
        return tipo == Tipo.PASSEAR || (tipo == Tipo.PATRULHA && !pontos.isEmpty());
    }

    public Rotina comTipo(Tipo novo) {
        return new Rotina(novo, raio, pontos, falas, dormir);
    }

    public Rotina comRaio(double novo) {
        return new Rotina(tipo, novo, pontos, falas, dormir);
    }

    public static Rotina padrao() {
        return new Rotina(Tipo.PARADO, 6, new ArrayList<>(), new ArrayList<>(), false);
    }

    public static Rotina ler(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        Tipo t = Tipo.buscar(s.getString("tipo", "PARADO"));
        List<Vector> pontos = new ArrayList<>();
        for (Object o : s.getList("pontos", List.of())) {
            Vector v = vetor(o);
            if (v != null) {
                pontos.add(v);
            }
        }
        return new Rotina(t == null ? Tipo.PARADO : t, Math.max(1, s.getDouble("raio", 6)), pontos,
                new ArrayList<>(s.getStringList("falas")), s.getBoolean("dormir", false));
    }

    /** "x y z" ou {x: , y: , z: } */
    private static Vector vetor(Object o) {
        try {
            if (o instanceof Map<?, ?> m) {
                return new Vector(num(m.get("x")), num(m.get("y")), num(m.get("z")));
            }
            String[] p = String.valueOf(o).trim().split("[\\s,;]+");
            return p.length >= 3 ? new Vector(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }

    public void gravar(ConfigurationSection y) {
        y.set("tipo", tipo.name());
        y.set("raio", raio);
        y.set("pontos", pontos.stream().map(v -> fmt(v.getX()) + " " + fmt(v.getY()) + " " + fmt(v.getZ())).toList());
        y.set("falas", falas);
        y.set("dormir", dormir);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0);
    }
}
