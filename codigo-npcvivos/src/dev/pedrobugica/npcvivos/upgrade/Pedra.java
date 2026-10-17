package dev.pedrobugica.npcvivos.upgrade;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Pedras de aprimoramento (o "material" do upgrade). Caem de monstros e chefes, vêm de missões e podem
 * ser vendidas por NPCs. São reconhecidas pelo dado guardado no item, não pelo nome.
 */
public enum Pedra {
    COMUM("Comum", "<green>", Material.FLINT),
    RARA("Rara", "<aqua>", Material.PRISMARINE_SHARD),
    EPICA("Épica", "<light_purple>", Material.AMETHYST_SHARD),
    LENDARIA("Lendária", "<gold>", Material.ECHO_SHARD);

    private final String nome;
    private final String cor;
    private final Material material;

    Pedra(String nome, String cor, Material material) {
        this.nome = nome;
        this.cor = cor;
        this.material = material;
    }

    public String nome() {
        return nome;
    }

    public String cor() {
        return cor;
    }

    public Material material() {
        return material;
    }

    /** "Pedra Rara" já com a cor. */
    public String nomeCompleto() {
        return cor + "Pedra de Aprimoramento " + nome;
    }

    public static Pedra buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("É", "E").replace("Á", "A");
        if (n.startsWith("PEDRA_")) {
            n = n.substring(6);
        }
        if (n.equals("RARO")) {
            n = "RARA";
        } else if (n.equals("EPICO")) {
            n = "EPICA";
        } else if (n.equals("LENDARIO")) {
            n = "LENDARIA";
        }
        for (Pedra p : values()) {
            if (p.name().equals(n)) {
                return p;
            }
        }
        return null;
    }
}
