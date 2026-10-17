package dev.pedrobugica.npcvivos.comportamento;

import dev.pedrobugica.npcvivos.combate.AtributosCombate;
import org.bukkit.Location;

import java.util.UUID;

/**
 * O que o plugin lembra de um NPC enquanto o servidor está ligado: em que estado ele está,
 * quem é o alvo, quanta vida sobrou e onde é o posto dele. Nada disso vai para o disco:
 * quando o servidor liga de novo, todo NPC volta ocioso e com a vida cheia.
 */
public final class Cerebro {

    private final String npcId;
    private AtributosCombate atributos;
    private EstadoNpc estado = EstadoNpc.OCIOSO;
    private long estadoDesde = System.currentTimeMillis();
    private double vida;
    private Location posto;

    private UUID alvo;
    private UUID dono;
    private UUID jogadorDaConversa;
    private long ultimoGolpe;
    private long morreuEm;
    private long duelistaOcupadoAte;

    public Cerebro(String npcId, AtributosCombate atributos, Location posto) {
        this.npcId = npcId;
        this.atributos = atributos;
        this.posto = posto;
        this.vida = atributos.vida();
    }

    public String npcId() {
        return npcId;
    }

    public AtributosCombate atributos() {
        return atributos;
    }

    /** Troca os atributos (duelo por dificuldade, /npcv combate) e enche a vida. */
    public void setAtributos(AtributosCombate novos) {
        this.atributos = novos;
        this.vida = novos.vida();
    }

    public EstadoNpc estado() {
        return estado;
    }

    public void setEstado(EstadoNpc novo) {
        if (estado != novo) {
            estado = novo;
            estadoDesde = System.currentTimeMillis();
        }
    }

    public long segundosNoEstado() {
        return (System.currentTimeMillis() - estadoDesde) / 1000;
    }

    public double vida() {
        return vida;
    }

    public double vidaMaxima() {
        return atributos.vida();
    }

    public void setVida(double v) {
        vida = Math.min(atributos.vida(), Math.max(0, v));
    }

    public void encherVida() {
        vida = atributos.vida();
    }

    public boolean morto() {
        return vida <= 0;
    }

    public Location posto() {
        return posto;
    }

    public void setPosto(Location posto) {
        this.posto = posto;
    }

    public UUID alvo() {
        return alvo;
    }

    public void setAlvo(UUID alvo) {
        this.alvo = alvo;
    }

    /** Quem contratou o mercenário. */
    public UUID dono() {
        return dono;
    }

    public void setDono(UUID dono) {
        this.dono = dono;
    }

    public UUID jogadorDaConversa() {
        return jogadorDaConversa;
    }

    public void setJogadorDaConversa(UUID jogadorDaConversa) {
        this.jogadorDaConversa = jogadorDaConversa;
    }

    public boolean golpeNoPonto(long intervaloMs) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoGolpe < intervaloMs) {
            return false;
        }
        ultimoGolpe = agora;
        return true;
    }

    public long morreuEm() {
        return morreuEm;
    }

    public void marcarMorte() {
        morreuEm = System.currentTimeMillis();
        vida = 0;
    }

    /** O duelista está no meio de um duelo (ou esperando o próximo) até este instante. */
    public boolean duelistaOcupado() {
        return duelistaOcupadoAte > System.currentTimeMillis();
    }

    public void ocuparDuelista(long ms) {
        duelistaOcupadoAte = System.currentTimeMillis() + ms;
    }

    public void liberarDuelista() {
        duelistaOcupadoAte = 0;
    }
}
