package br.rankupsupremo.dados;

import java.util.UUID;

/**
 * Dados de progresso de um jogador.
 */
public final class DadosJogador {

    private final UUID uuid;
    private volatile String nome;
    private volatile String rank;
    private volatile int prestigio;
    private volatile long blocos;
    private volatile boolean autovender;
    private volatile boolean recebeuPicareta;
    private volatile String ultimaSobrevivencia;
    private volatile boolean visitouSobrevivencia;
    private volatile long tokens;
    private volatile double boosterBonus;
    private volatile long boosterAte;
    private volatile double boosterTokensBonus;
    private volatile long boosterTokensAte;

    public DadosJogador(UUID uuid, String nome, String rank) {
        this.uuid = uuid;
        this.nome = nome;
        this.rank = rank;
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

    public String rank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public int prestigio() {
        return prestigio;
    }

    public void setPrestigio(int prestigio) {
        this.prestigio = Math.max(0, prestigio);
    }

    public long blocos() {
        return blocos;
    }

    public void setBlocos(long blocos) {
        this.blocos = Math.max(0, blocos);
    }

    public void adicionarBlocos(long quantidade) {
        this.blocos += quantidade;
    }

    public boolean autovender() {
        return autovender;
    }

    public void setAutovender(boolean autovender) {
        this.autovender = autovender;
    }

    public boolean recebeuPicareta() {
        return recebeuPicareta;
    }

    public void setRecebeuPicareta(boolean recebeuPicareta) {
        this.recebeuPicareta = recebeuPicareta;
    }

    /** Última posição no mundo Sobrevivência ("mundo;x;y;z;yaw;pitch") ou null. */
    public String ultimaSobrevivencia() {
        return ultimaSobrevivencia;
    }

    public void setUltimaSobrevivencia(String ultimaSobrevivencia) {
        this.ultimaSobrevivencia = ultimaSobrevivencia;
    }

    public boolean visitouSobrevivencia() {
        return visitouSobrevivencia;
    }

    public void setVisitouSobrevivencia(boolean visitouSobrevivencia) {
        this.visitouSobrevivencia = visitouSobrevivencia;
    }

    /** Tokens de mina (moeda das melhorias especiais da picareta). */
    public long tokens() {
        return tokens;
    }

    public void setTokens(long tokens) {
        this.tokens = Math.max(0, tokens);
    }

    public synchronized void adicionarTokens(long quantidade) {
        this.tokens = Math.max(0, this.tokens + quantidade);
    }

    /** Bônus de venda do booster ativo (0 se não houver). */
    public double boosterAtivo() {
        return System.currentTimeMillis() < boosterAte ? boosterBonus : 0;
    }

    public double boosterBonus() {
        return boosterBonus;
    }

    public long boosterAte() {
        return boosterAte;
    }

    public void setBooster(double bonus, long ate) {
        this.boosterBonus = bonus;
        this.boosterAte = ate;
    }

    /** Bônus de chance de tokens do booster de tokens ativo (0 se não houver). */
    public double boosterTokensAtivo() {
        return System.currentTimeMillis() < boosterTokensAte ? boosterTokensBonus : 0;
    }

    public double boosterTokensBonus() {
        return boosterTokensBonus;
    }

    public long boosterTokensAte() {
        return boosterTokensAte;
    }

    public void setBoosterTokens(double bonus, long ate) {
        this.boosterTokensBonus = bonus;
        this.boosterTokensAte = ate;
    }
}
