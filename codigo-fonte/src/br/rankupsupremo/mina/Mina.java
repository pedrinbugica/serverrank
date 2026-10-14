package br.rankupsupremo.mina;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uma mina com reset automático. A geometria é calculada a partir do índice (posição fixa no mundo).
 */
public final class Mina {

    private final String id;
    private String nome;
    private String rankId;
    private Material icone;
    private Material parede;
    private Material detalhe;
    private int tamanho;
    private int profundidade;
    private int resetMinutos;
    private double resetPorcentagem;
    private List<Material> blocos = List.of();
    private double[] pesosAcumulados = new double[0];

    // Tipo especial (VIP, PvP, prestígio)
    private TipoMina tipo = TipoMina.NORMAL;
    private TemaMina tema = TemaMina.PADRAO;
    private String permissao;
    private String nomeCargo;
    private int prestigioMinimo;
    private int dropsRank;
    private double multiplicadorTokens = 1.0;
    private int larguraPlataforma = -1;

    // Geometria
    private World mundo;
    private int indice;
    private int cx;
    private int cz;
    private int yTopo;
    private int x0;
    private int x1;
    private int z0;
    private int z1;
    private int yBase;

    // Estado
    private int quebrados;
    private long proximoReset;
    private boolean resetando;
    private boolean avisado;

    public Mina(String id) {
        this.id = id;
    }

    public void configurar(String nome, String rankId, Material icone, Material parede, Material detalhe, int tamanho,
                           int profundidade, int resetMinutos, double resetPorcentagem, List<Material> blocos, List<Double> pesos) {
        this.nome = nome;
        this.rankId = rankId;
        this.icone = icone;
        this.parede = parede;
        this.detalhe = detalhe;
        this.tamanho = tamanho;
        this.profundidade = profundidade;
        this.resetMinutos = resetMinutos;
        this.resetPorcentagem = resetPorcentagem;
        this.blocos = List.copyOf(blocos);
        this.pesosAcumulados = new double[pesos.size()];
        double soma = 0;
        for (int i = 0; i < pesos.size(); i++) {
            soma += pesos.get(i);
            pesosAcumulados[i] = soma;
        }
    }

    /** Opções das minas especiais (VIP, PvP, prestígio). Minas normais usam os valores padrão. */
    public void configurarEspecial(TipoMina tipo, TemaMina tema, String permissao, String nomeCargo, int prestigioMinimo,
                                   int dropsRank, double multiplicadorTokens, int larguraPlataforma) {
        this.tipo = tipo;
        this.tema = tema;
        this.permissao = permissao == null || permissao.isBlank() ? null : permissao;
        this.nomeCargo = nomeCargo;
        this.prestigioMinimo = Math.max(0, prestigioMinimo);
        this.dropsRank = Math.max(0, dropsRank);
        this.multiplicadorTokens = Math.max(0, multiplicadorTokens);
        this.larguraPlataforma = larguraPlataforma;
    }

    public void posicionar(World mundo, int indice, int xInicial, int espacamento, int z, int yTopo) {
        this.mundo = mundo;
        this.indice = indice;
        this.cx = xInicial + indice * espacamento;
        this.cz = z;
        this.yTopo = yTopo;
        int metade = tamanho / 2;
        this.x0 = cx - metade;
        this.x1 = x0 + tamanho - 1;
        this.z0 = cz - metade;
        this.z1 = z0 + tamanho - 1;
        this.yBase = yTopo - profundidade + 1;
    }

    /** Sorteia um bloco conforme os pesos configurados. */
    public Material sortear() {
        if (blocos.isEmpty()) {
            return Material.STONE;
        }
        double total = pesosAcumulados[pesosAcumulados.length - 1];
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < pesosAcumulados.length; i++) {
            if (r < pesosAcumulados[i]) {
                return blocos.get(i);
            }
        }
        return blocos.get(blocos.size() - 1);
    }

    /** O bloco está dentro da área minerável? */
    public boolean contem(World w, int x, int y, int z) {
        return w == mundo && x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= yBase && y <= yTopo;
    }

    /** O jogador está na região da mina (incluindo a plataforma)? */
    public boolean naRegiao(Location l) {
        int m = Math.max(6, larguraPlataforma) + 4;
        return l.getWorld() == mundo && l.getX() >= x0 - m && l.getX() <= x1 + m + 1 && l.getZ() >= z0 - m
                && l.getZ() <= z1 + m + 1 && l.getY() >= yBase - 3 && l.getY() <= yTopo + 20;
    }

    /** O jogador está dentro do buraco da mina (seria soterrado no reset)? */
    public boolean dentro(Location l) {
        return l.getWorld() == mundo && l.getX() >= x0 && l.getX() < x1 + 1 && l.getZ() >= z0 && l.getZ() < z1 + 1
                && l.getY() >= yBase - 1 && l.getY() <= yTopo + 2;
    }

    /** Ponto de teleporte: na plataforma, de frente para a mina. */
    public Location spawn() {
        return new Location(mundo, cx + 0.5, yTopo + 1, z1 + 4.5, 180f, 15f);
    }

    public Location localHolograma() {
        return new Location(mundo, cx + 0.5, yTopo + 3.4, z1 + 1.5);
    }

    public int volume() {
        return tamanho * tamanho * profundidade;
    }

    public double restante() {
        double r = 1.0 - (double) quebrados / volume();
        return Math.max(0, Math.min(1, r));
    }

    public void adicionarQuebrados(int q) {
        quebrados += q;
    }

    public String id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public String rankId() {
        return rankId;
    }

    public Material icone() {
        return icone;
    }

    public Material parede() {
        return parede;
    }

    public Material detalhe() {
        return detalhe;
    }

    public int tamanho() {
        return tamanho;
    }

    public int profundidade() {
        return profundidade;
    }

    public int resetMinutos() {
        return resetMinutos;
    }

    public double resetPorcentagem() {
        return resetPorcentagem;
    }

    public List<Material> blocos() {
        return blocos;
    }

    public double peso(int i) {
        return i == 0 ? pesosAcumulados[0] : pesosAcumulados[i] - pesosAcumulados[i - 1];
    }

    public double pesoTotal() {
        return pesosAcumulados.length == 0 ? 0 : pesosAcumulados[pesosAcumulados.length - 1];
    }

    public World mundo() {
        return mundo;
    }

    public int indice() {
        return indice;
    }

    public int cx() {
        return cx;
    }

    public int cz() {
        return cz;
    }

    public int yTopo() {
        return yTopo;
    }

    public int yBase() {
        return yBase;
    }

    public int x0() {
        return x0;
    }

    public int x1() {
        return x1;
    }

    public int z0() {
        return z0;
    }

    public int z1() {
        return z1;
    }

    public int quebrados() {
        return quebrados;
    }

    public void setQuebrados(int quebrados) {
        this.quebrados = quebrados;
    }

    public long proximoReset() {
        return proximoReset;
    }

    public void setProximoReset(long proximoReset) {
        this.proximoReset = proximoReset;
    }

    public boolean resetando() {
        return resetando;
    }

    public void setResetando(boolean resetando) {
        this.resetando = resetando;
    }

    public boolean avisado() {
        return avisado;
    }

    public void setAvisado(boolean avisado) {
        this.avisado = avisado;
    }

    public TipoMina tipo() {
        return tipo;
    }

    public TemaMina tema() {
        return tema;
    }

    public boolean especial() {
        return tipo != TipoMina.NORMAL;
    }

    public boolean pvp() {
        return tipo == TipoMina.PVP;
    }

    public String permissao() {
        return permissao;
    }

    public String nomeCargo() {
        return nomeCargo;
    }

    public int prestigioMinimo() {
        return prestigioMinimo;
    }

    /** 0 = o jogador recebe o próprio bloco; N = recebe minérios da mina do rank dele + N. */
    public int dropsRank() {
        return dropsRank;
    }

    public double multiplicadorTokens() {
        return multiplicadorTokens;
    }

    /** -1 = usar o valor do config.yml. */
    public int larguraPlataforma() {
        return larguraPlataforma;
    }
}
