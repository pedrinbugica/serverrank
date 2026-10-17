package dev.pedrobugica.npcvivos.missao;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Uma missão (missoes/<id>.yml).
 *
 * @param npc           NPC que oferece a missão
 * @param entregarPara  NPC onde a missão é entregue (normalmente o mesmo que deu)
 * @param item          COLETAR: o que levar; ENTREGAR: o pacote recebido ao aceitar (com nome)
 * @param mob           MATAR: qual mob
 * @param mundo         MATAR: só conta neste mundo (null = qualquer)
 * @param requisito     missão que precisa ter sido concluída antes (cadeia de missões)
 * @param reputacaoMinima reputação mínima com a facção do NPC (null = qualquer, menos Hostil/Inimigo)
 * @param apenasMembros  só para quem jurou lealdade à facção do NPC
 * @param pontosGuerra   missão de guerra: pontos para a facção do NPC se ela estiver em guerra (fase 7)
 */
public record Missao(String id, String nome, List<String> descricao, String npc, String entregarPara, Tipo tipo,
                     Material item, String nomeItem, int quantidade, EntityType mob, String mundo, String requisito,
                     Repeticao repetir, double dinheiro, List<ItemStack> itens, List<String> comandos, int reputacao,
                     String falaAceitar, String falaConclusao, Integer reputacaoMinima, boolean apenasMembros,
                     int pontosGuerra) {

    public enum Tipo {
        MATAR("Matar"), COLETAR("Coletar"), ENTREGAR("Entregar");

        private final String nome;

        Tipo(String nome) {
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

    public enum Repeticao {
        NUNCA, DIARIA, SEMANAL
    }

    /** NPC onde a missão termina. */
    public String npcEntrega() {
        return entregarPara != null ? entregarPara : npc;
    }
}
