package dev.pedrobugica.npcvivos.dados;

import java.util.UUID;

/**
 * Acesso aos dados que mudam com o jogo (estoque, limites, memória dos NPCs).
 * É uma interface para dar para trocar YAML por SQLite/MySQL sem mexer no resto do código.
 */
public interface Repositorio {

    /** Estoque atual de um item da loja; null se ainda não existe. */
    Double estoque(String npc, String chave);

    void setEstoque(String npc, String chave, double quantidade);

    /** Quantas unidades o jogador já vendeu hoje para este NPC. */
    int vendidoHoje(UUID jogador, String npc, String chave);

    void adicionarVendido(UUID jogador, String npc, String chave, int quantidade);

    /** Quantas vezes o jogador já falou com o NPC (memória do NPC). */
    int visitas(UUID jogador, String npc);

    void registrarVisita(UUID jogador, String npc);

    /** Próxima fala da lista que este jogador vai ouvir. */
    int proximaFala(UUID jogador, String npc, int total);

    // ------------------------------------------------------------------ MISSÕES

    /** Progresso de uma missão ativa; null se o jogador não está com ela. */
    Integer progressoMissao(UUID jogador, String missao);

    void setProgressoMissao(UUID jogador, String missao, int progresso);

    void removerMissaoAtiva(UUID jogador, String missao);

    /** Missões ativas do jogador: id -> progresso. */
    java.util.Map<String, Integer> missoesAtivas(UUID jogador);

    /** Quando o jogador concluiu a missão pela última vez (0 = nunca). */
    long concluidaEm(UUID jogador, String missao);

    int vezesConcluida(UUID jogador, String missao);

    void marcarConcluida(UUID jogador, String missao, long quando);

    // ------------------------------------------------------------------ FACÇÕES E REPUTAÇÃO

    /** Reputação do jogador com a facção (-1000 a +1000; 0 se nunca mudou). */
    int reputacao(UUID jogador, String faccao);

    void setReputacao(UUID jogador, String faccao, int valor);

    /** Reputação com todas as facções que o jogador já mexeu: facção -> pontos. */
    java.util.Map<String, Integer> reputacoes(UUID jogador);

    /** Facção à qual o jogador jurou lealdade (null = nenhuma). */
    String membro(UUID jogador);

    void setMembro(UUID jogador, String faccao);

    /** Quando o jogador saiu da última facção (0 = nunca). */
    long saiuDeFaccaoEm(UUID jogador);

    void setSaiuDeFaccaoEm(UUID jogador, long quando);

    /** Relação entre duas facções mudada em jogo (null = usa a do arquivo da facção). */
    String relacao(String a, String b);

    void setRelacao(String a, String b, String tipo);

    /** Pontos de reputação já ganhos hoje vendendo para a facção (para ter limite diário). */
    int reputacaoVendaHoje(UUID jogador, String faccao);

    void adicionarReputacaoVenda(UUID jogador, String faccao, int pontos);

    // ------------------------------------------------------------------ TÍTULOS (duelos e chefes)

    /** Títulos que o jogador já conquistou (na ordem em que ganhou). */
    java.util.List<String> titulos(UUID jogador);

    /** Guarda um título novo; repetido não entra duas vezes. */
    void guardarTitulo(UUID jogador, String titulo);

    // ------------------------------------------------------------------ MUNDO VIVO (fase 7)

    /** Dado do mundo vivo (mercado, pontuação entre facções, guerras...): texto livre por chave; null se não existe. */
    String mundo(String chave);

    /** Grava (ou apaga, com null) um dado do mundo vivo. */
    void setMundo(String chave, String valor);

    /** Todos os dados do mundo vivo cuja chave começa com o prefixo. */
    java.util.Map<String, String> mundoComPrefixo(String prefixo);

    boolean executado(String marca);

    void marcarExecutado(String marca);

    void esquecerNpc(String npc);

    void salvarAssincrono();

    void salvarSincrono();
}
