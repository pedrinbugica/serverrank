package dev.pedrobugica.npcvivos.faccao;

import java.util.List;
import java.util.Map;

/**
 * Uma facção (faccoes/<id>.yml): um "time" de NPCs com relações com as outras facções.
 *
 * @param tag            tag que os membros ganham no chat
 * @param lider          id do NPC líder
 * @param relacoes       relação com outras facções (as que não aparecem são neutras)
 * @param descontoMembro desconto nas lojas da facção para quem jura lealdade (0.10 = 10%)
 */
public record Faccao(String id, String nome, String tag, List<String> descricao, String lider,
                     Map<String, Relacao> relacoes, double descontoMembro) {
}
