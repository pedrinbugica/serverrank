package dev.pedrobugica.npcvivos.dialogo;

import java.util.List;
import java.util.Map;

/**
 * Conversa em árvore (dialogos/<id>.yml): cada nó tem falas e opções; cada opção leva a outro nó
 * ou faz uma ação (abrir loja, dar/entregar missão, comando, fechar).
 */
public record Dialogo(String id, String inicio, Map<String, No> nos) {

    /** @param falasRetorno falas usadas quando o jogador já conversou antes (memória do NPC) */
    public record No(String id, List<String> falas, List<String> falasRetorno, List<Opcao> opcoes) {
    }

    /**
     * @param condicoes "se:" da opção (todas precisam ser verdadeiras para a opção aparecer)
     * @param ir        próximo nó
     * @param acao      loja, vender, fechar, dar_missao, entregar_missao, comando
     * @param fala      resposta do NPC antes de seguir (opcional)
     */
    public record Opcao(String texto, Map<String, Object> condicoes, String ir, String acao, String missao,
                        List<String> comandos, String fala) {
    }
}
