package dev.pedrobugica.survivalrpg.mundo;

/**
 * Uma região do mapa de RPG. No mundo normal ela é um anel de distância do spawn; no Nether e no End vale o mundo
 * inteiro (de = -1).
 */
public record Regiao(String id, String nome, String mundo, double de, double ate, int nivelMin, int nivelMax, boolean pvp) {

    /** Nível sorteado de um mob nesta região, mais alto quanto mais longe do spawn dentro do anel. */
    public int nivelNaDistancia(double distancia, double sorte) {
        double fracao = ate <= de || de < 0 ? sorte : Math.max(0, Math.min(1, (distancia - de) / (ate - de)));
        double base = nivelMin + (nivelMax - nivelMin) * (0.65 * fracao + 0.35 * sorte);
        return (int) Math.round(Math.max(nivelMin, Math.min(nivelMax, base)));
    }

    public String faixa() {
        return nivelMin + " a " + nivelMax;
    }
}
