package dev.pedrobugica.npcvivos.mundo;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento para outros plugins (ou outras partes deste) reagirem a uma guerra de facções sem depender do código
 * direto: chamado quando a guerra começa e quando termina (com o vencedor, ou null em caso de empate).
 */
public final class FaccaoGuerraEvent extends Event {

    public enum Momento {
        INICIO, FIM
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Momento momento;
    private final String faccaoA;
    private final String faccaoB;
    private final String vencedora;

    public FaccaoGuerraEvent(Momento momento, String faccaoA, String faccaoB, String vencedora) {
        this.momento = momento;
        this.faccaoA = faccaoA;
        this.faccaoB = faccaoB;
        this.vencedora = vencedora;
    }

    public Momento momento() {
        return momento;
    }

    public String faccaoA() {
        return faccaoA;
    }

    public String faccaoB() {
        return faccaoB;
    }

    /** Facção que venceu (só no FIM; null = empate). */
    public String vencedora() {
        return vencedora;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
