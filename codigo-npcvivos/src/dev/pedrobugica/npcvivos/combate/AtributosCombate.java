package dev.pedrobugica.npcvivos.combate;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Os números de combate de um NPC. Todo NPC que luta usa a mesma base, só mudando os valores:
 * assim um mercenário, um duelista e um chefe são o mesmo código com atributos diferentes.
 */
public final class AtributosCombate {

    private final double vida;
    private final double dano;
    private final double armadura;
    private final double velocidade;
    private final double alcance;
    private final boolean arqueiro;
    private final int respawnSegundos;

    public AtributosCombate(double vida, double dano, double armadura, double velocidade,
                            double alcance, boolean arqueiro, int respawnSegundos) {
        this.vida = Math.max(1, vida);
        this.dano = Math.max(0, dano);
        this.armadura = Math.max(0, armadura);
        this.velocidade = Math.min(0.6, Math.max(0.05, velocidade));
        this.alcance = Math.max(1, alcance);
        this.arqueiro = arqueiro;
        this.respawnSegundos = Math.max(0, respawnSegundos);
    }

    /** Lê uma seção do combate.yml usando outro atributo como padrão para o que faltar. */
    public static AtributosCombate deConfig(ConfigurationSection s, AtributosCombate padrao) {
        if (s == null) {
            return padrao;
        }
        return new AtributosCombate(
                s.getDouble("vida", padrao.vida),
                s.getDouble("dano", padrao.dano),
                s.getDouble("armadura", padrao.armadura),
                s.getDouble("velocidade", padrao.velocidade),
                s.getDouble("alcance", padrao.alcance),
                s.getBoolean("arqueiro", padrao.arqueiro),
                s.getInt("respawn-segundos", padrao.respawnSegundos));
    }

    public AtributosCombate comVidaEDano(double novaVida, double novoDano) {
        return new AtributosCombate(novaVida, novoDano, armadura, velocidade, alcance, arqueiro, respawnSegundos);
    }

    public double vida() {
        return vida;
    }

    public double dano() {
        return dano;
    }

    /** Cada ponto de armadura tira 2% do dano recebido, até no máximo 60%. */
    public double danoRecebido(double bruto) {
        return bruto * (1 - Math.min(0.6, armadura * 0.02));
    }

    public double armadura() {
        return armadura;
    }

    /** Blocos por tick que o NPC anda. */
    public double velocidade() {
        return velocidade;
    }

    /** Distância para acertar no corpo a corpo (ou para atirar, se for arqueiro). */
    public double alcance() {
        return alcance;
    }

    public boolean arqueiro() {
        return arqueiro;
    }

    public int respawnSegundos() {
        return respawnSegundos;
    }
}
