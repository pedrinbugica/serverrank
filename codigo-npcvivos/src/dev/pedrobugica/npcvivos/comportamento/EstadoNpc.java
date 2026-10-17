package dev.pedrobugica.npcvivos.comportamento;

/**
 * O "cérebro" do NPC está sempre em um estado só, e um acontecimento faz ele passar para outro
 * (fase 6 do documento de design). A máquina decide o próximo estado uma vez por segundo.
 */
public enum EstadoNpc {

    /** Parado no posto, olhando para quem chega perto. */
    OCIOSO("Ocioso"),
    /** Viu um inimigo: vira para ele e avisa. */
    ALERTA("Alerta"),
    /** Persegue e ataca o alvo. */
    COMBATE("Combate"),
    /** Com pouca vida: corre do alvo e chama os aliados. */
    FUGINDO("Fugindo"),
    /** Só mercenários: anda atrás do jogador que contratou. */
    SEGUINDO_DONO("Seguindo o dono"),
    /** Voltando para o lugar de origem e recuperando a vida. */
    VOLTANDO_AO_POSTO("Voltando ao posto"),
    /** Conversando com um jogador (não anda nem ataca). */
    CONVERSANDO("Conversando"),
    /** Com a loja aberta para um jogador. */
    NEGOCIANDO("Negociando");

    private final String nome;

    EstadoNpc(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }

    /** Nestes estados o NPC fica parado esperando o jogador. */
    public boolean ocupadoComJogador() {
        return this == CONVERSANDO || this == NEGOCIANDO;
    }

    public boolean lutando() {
        return this == COMBATE || this == FUGINDO;
    }
}
