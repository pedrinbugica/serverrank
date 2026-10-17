package dev.pedrobugica.survivalrpg.masmorra;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/** A cara de cada masmorra: blocos, luz, decoração, monstros e o chefe. */
public enum Tema {
    CRIPTA(new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE},
            new Material[]{Material.ANDESITE, Material.COBBLESTONE, Material.GRAVEL, Material.MOSSY_COBBLESTONE},
            Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, "minecraft:soul_lantern[hanging=true]",
            new Material[]{Material.COBWEB, Material.BONE_BLOCK, Material.SKELETON_SKULL, Material.CANDLE},
            List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.STRAY),
            EntityType.SKELETON, "<gray><bold>Ossian, o Rei Esqueleto", 1.7, Material.GOLDEN_HELMET, Material.BOW),
    ARANHAS(new Material[]{Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE},
            new Material[]{Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DEEPSLATE, Material.GRAVEL},
            Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE, "minecraft:lantern[hanging=true]",
            new Material[]{Material.COBWEB, Material.COBWEB, Material.COBWEB, Material.MOSS_CARPET},
            List.of(EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.ZOMBIE, EntityType.SILVERFISH),
            EntityType.SPIDER, "<dark_green><bold>Aracna, a Rainha Aranha", 2.6, null, null),
    FORTALEZA(new Material[]{Material.NETHER_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Material.RED_NETHER_BRICKS},
            new Material[]{Material.POLISHED_BLACKSTONE, Material.BLACKSTONE, Material.NETHER_BRICKS, Material.MAGMA_BLOCK},
            Material.POLISHED_BLACKSTONE, Material.CHISELED_POLISHED_BLACKSTONE, "minecraft:lantern[hanging=true]",
            new Material[]{Material.IRON_CHAIN, Material.SOUL_CAMPFIRE, Material.WITHER_SKELETON_SKULL, Material.IRON_BARS},
            List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.VINDICATOR, EntityType.PIGLIN_BRUTE),
            EntityType.WITHER_SKELETON, "<dark_red><bold>Malakar, o Rei Caído", 1.8, Material.NETHERITE_HELMET, Material.NETHERITE_SWORD);

    private final Material[] paredes;
    private final Material[] chao;
    private final Material teto;
    private final Material pilar;
    private final String luz;
    private final Material[] enfeites;
    private final List<EntityType> mobs;
    private final EntityType chefe;
    private final String nomeChefe;
    private final double escalaChefe;
    private final Material capaceteChefe;
    private final Material armaChefe;

    Tema(Material[] paredes, Material[] chao, Material teto, Material pilar, String luz, Material[] enfeites, List<EntityType> mobs,
         EntityType chefe, String nomeChefe, double escalaChefe, Material capaceteChefe, Material armaChefe) {
        this.paredes = paredes;
        this.chao = chao;
        this.teto = teto;
        this.pilar = pilar;
        this.luz = luz;
        this.enfeites = enfeites;
        this.mobs = mobs;
        this.chefe = chefe;
        this.nomeChefe = nomeChefe;
        this.escalaChefe = escalaChefe;
        this.capaceteChefe = capaceteChefe;
        this.armaChefe = armaChefe;
    }

    public Material parede(Random r) {
        int i = r.nextInt(100);
        return i < 60 ? paredes[0] : i < 78 ? paredes[1] : i < 92 ? paredes[2] : paredes[3];
    }

    public Material chao(Random r) {
        int i = r.nextInt(100);
        return i < 55 ? chao[0] : i < 78 ? chao[1] : i < 93 ? chao[2] : chao[3];
    }

    public Material teto() {
        return teto;
    }

    public Material pilar() {
        return pilar;
    }

    public String luz() {
        return luz;
    }

    public Material enfeite(Random r) {
        return enfeites[r.nextInt(enfeites.length)];
    }

    public List<EntityType> mobs() {
        return mobs;
    }

    public EntityType chefe() {
        return chefe;
    }

    public String nomeChefe() {
        return nomeChefe;
    }

    public double escalaChefe() {
        return escalaChefe;
    }

    public Material capaceteChefe() {
        return capaceteChefe;
    }

    public Material armaChefe() {
        return armaChefe;
    }

    public static Tema buscar(String s) {
        try {
            return s == null ? CRIPTA : valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CRIPTA;
        }
    }
}
