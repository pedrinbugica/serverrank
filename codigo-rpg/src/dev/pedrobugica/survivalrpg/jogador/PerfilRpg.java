package dev.pedrobugica.survivalrpg.jogador;

import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** O personagem de RPG de um jogador (fica na memória enquanto ele está online). */
public final class PerfilRpg {

    private final UUID uuid;
    private String nome;
    private Classe classe;
    private int nivel = 1;
    private double xp;
    private int pontosLivres;
    private final Map<Atributo, Integer> atributos = new EnumMap<>(Atributo.class);
    private double mana = 100;
    private long trocouClasseEm;
    private final Set<String> regioesDescobertas = new LinkedHashSet<>();
    private boolean numerosDeDano = true;
    // ---- só na memória
    private String regiaoAtual;
    private boolean modoHabilidade;
    private final Map<String, Long> recargas = new HashMap<>();
    private boolean sujo;

    public PerfilRpg(UUID uuid) {
        this.uuid = uuid;
        for (Atributo a : Atributo.values()) {
            atributos.put(a, 0);
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public String nome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Classe classe() {
        return classe;
    }

    public void setClasse(Classe classe) {
        this.classe = classe;
        sujo = true;
    }

    public int nivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        this.nivel = nivel;
        sujo = true;
    }

    public double xp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
        sujo = true;
    }

    public int pontosLivres() {
        return pontosLivres;
    }

    public void setPontosLivres(int pontosLivres) {
        this.pontosLivres = pontosLivres;
        sujo = true;
    }

    public int atributo(Atributo a) {
        return atributos.getOrDefault(a, 0);
    }

    public void setAtributo(Atributo a, int valor) {
        atributos.put(a, Math.max(0, valor));
        sujo = true;
    }

    public double mana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = mana;
    }

    public long trocouClasseEm() {
        return trocouClasseEm;
    }

    public void setTrocouClasseEm(long trocouClasseEm) {
        this.trocouClasseEm = trocouClasseEm;
        sujo = true;
    }

    public Set<String> regioesDescobertas() {
        return regioesDescobertas;
    }

    public boolean numerosDeDano() {
        return numerosDeDano;
    }

    public void setNumerosDeDano(boolean numerosDeDano) {
        this.numerosDeDano = numerosDeDano;
        sujo = true;
    }

    public String regiaoAtual() {
        return regiaoAtual;
    }

    public void setRegiaoAtual(String regiaoAtual) {
        this.regiaoAtual = regiaoAtual;
    }

    public boolean modoHabilidade() {
        return modoHabilidade;
    }

    public void setModoHabilidade(boolean modoHabilidade) {
        this.modoHabilidade = modoHabilidade;
    }

    /** Milissegundos que faltam para a habilidade poder ser usada de novo (0 = pronta). */
    public long recarga(String habilidade) {
        return Math.max(0, recargas.getOrDefault(habilidade, 0L) - System.currentTimeMillis());
    }

    public void usarRecarga(String habilidade, double segundos) {
        recargas.put(habilidade, System.currentTimeMillis() + (long) (segundos * 1000));
    }

    public boolean sujo() {
        return sujo;
    }

    public void marcarSujo() {
        sujo = true;
    }

    public void limpar() {
        sujo = false;
    }
}
