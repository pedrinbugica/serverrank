package dev.pedrobugica.survivalrpg.mundo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Locale;

/** Um lugar marcado no mapa: a capital, vilas, tavernas, castelos, torres, masmorras, ruínas e acampamentos. */
public record Lugar(String id, String nome, Tipo tipo, String mundo, int x, int y, int z, int raioZona, boolean protegido,
                    String estrutura) {

    public enum Tipo {
        CAPITAL("Capital", "<gold>", Material.BEACON, true),
        VILA("Vila", "<green>", Material.BELL, true),
        TAVERNA("Taverna", "<yellow>", Material.HONEY_BOTTLE, true),
        CASTELO("Castelo", "<red>", Material.NETHERITE_HELMET, false),
        TORRE("Torre", "<aqua>", Material.SPYGLASS, false),
        MASMORRA("Masmorra", "<dark_red>", Material.SKELETON_SKULL, false),
        RUINA("Ruína", "<gray>", Material.CRACKED_STONE_BRICKS, false),
        ACAMPAMENTO("Acampamento", "<dark_green>", Material.CAMPFIRE, false);

        private final String nome;
        private final String cor;
        private final Material icone;
        private final boolean viagem;

        Tipo(String nome, String cor, Material icone, boolean viagem) {
            this.nome = nome;
            this.cor = cor;
            this.icone = icone;
            this.viagem = viagem;
        }

        public String nome() {
            return nome;
        }

        public String cor() {
            return cor;
        }

        public Material icone() {
            return icone;
        }

        /** Dá para viajar (viagem rápida) até lá? */
        public boolean viagem() {
            return viagem;
        }

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

    public String formatado() {
        return tipo.cor() + nome;
    }

    public Location local() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public double distancia(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) {
            return Double.MAX_VALUE;
        }
        double dx = l.getX() - x;
        double dz = l.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
