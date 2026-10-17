package dev.pedrobugica.survivalrpg.classe;

import org.bukkit.Material;

import java.util.Locale;

/** Os seis atributos do personagem. Cada ponto vale um pouco (veja o "efeito"). */
public enum Atributo {
    FORCA("Força", "<red>", Material.IRON_SWORD, "+1% de dano corpo a corpo"),
    DESTREZA("Destreza", "<green>", Material.BOW, "+1% de dano à distância e +0,2% de crítico"),
    INTELIGENCIA("Inteligência", "<aqua>", Material.ENCHANTED_BOOK, "+1% de dano mágico e +2 de mana"),
    VITALIDADE("Vitalidade", "<dark_red>", Material.GOLDEN_APPLE, "+1 de vida a cada 2 pontos"),
    SABEDORIA("Sabedoria", "<light_purple>", Material.AMETHYST_SHARD, "+1% de cura e +0,1 de mana por segundo"),
    AGILIDADE("Agilidade", "<yellow>", Material.FEATHER, "+0,2% de esquiva e +0,2% de crítico");

    private final String nome;
    private final String cor;
    private final Material icone;
    private final String efeito;

    Atributo(String nome, String cor, Material icone, String efeito) {
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.efeito = efeito;
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

    public String efeito() {
        return efeito;
    }

    /** Abreviação para a lore dos itens (FOR, DES...). */
    public String sigla() {
        return name().substring(0, 3);
    }

    public static Atributo buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("Ç", "C").replace("Ê", "E");
        for (Atributo a : values()) {
            if (a.name().equals(n) || a.sigla().equals(n)) {
                return a;
            }
        }
        return null;
    }
}
