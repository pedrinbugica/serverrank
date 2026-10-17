package dev.pedrobugica.survivalrpg.classe;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * As cinco classes. Qualquer classe usa qualquer item, mas com a arma da classe o dano sobe e as habilidades
 * funcionam (modo habilidade: tecla F com a arma na mão, depois 1, 2 ou 3).
 */
public enum Classe {
    GUERREIRO("Guerreiro", "<red>", Material.IRON_SWORD, Atributo.VITALIDADE, "Tanque: aguenta dano e protege o grupo",
            List.of("_SWORD", "_AXE", "MACE")),
    ARQUEIRO("Arqueiro", "<green>", Material.BOW, Atributo.DESTREZA, "Dano à distância com arco e besta",
            List.of("BOW", "CROSSBOW")),
    MAGO("Mago", "<aqua>", Material.BLAZE_ROD, Atributo.INTELIGENCIA, "Magias de área que gastam mana",
            List.of("BLAZE_ROD", "BREEZE_ROD", "STICK", "TORCH", "SOUL_TORCH")),
    CLERIGO("Clérigo", "<yellow>", Material.GOLDEN_AXE, Atributo.SABEDORIA, "Cura e protege os aliados",
            List.of("_AXE", "MACE", "_SHOVEL")),
    LADINO("Ladino", "<dark_gray>", Material.IRON_SWORD, Atributo.AGILIDADE, "Golpes rápidos, críticos e furtividade",
            List.of("_SWORD"));

    private final String nome;
    private final String cor;
    private final Material icone;
    private final Atributo principal;
    private final String papel;
    private final List<String> armas;

    Classe(String nome, String cor, Material icone, Atributo principal, String papel, List<String> armas) {
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.principal = principal;
        this.papel = papel;
        this.armas = armas;
    }

    public String nome() {
        return nome;
    }

    public String cor() {
        return cor;
    }

    public String formatado() {
        return cor + nome;
    }

    public Material icone() {
        return icone;
    }

    public Atributo principal() {
        return principal;
    }

    public String papel() {
        return papel;
    }

    /** O item é a arma desta classe? (ex.: Mago = cajado: vara de blaze, graveto ou tocha) */
    public boolean ehArma(Material m) {
        if (m == null || m.isAir()) {
            return false;
        }
        String n = m.name();
        for (String a : armas) {
            if (a.startsWith("_") ? n.endsWith(a) : n.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public static Classe buscar(String s) {
        if (s == null) {
            return null;
        }
        String n = s.trim().toUpperCase(Locale.ROOT).replace("É", "E");
        for (Classe c : values()) {
            if (c.name().equals(n)) {
                return c;
            }
        }
        return null;
    }
}
