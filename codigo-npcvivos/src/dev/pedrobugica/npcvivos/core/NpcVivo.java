package dev.pedrobugica.npcvivos.core;

import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Um NPC vivo: quem ele é (nome, skin, papéis, facção), onde fica, o que fala e o que vende/compra.
 * Só guarda dados; quem decide o que acontece são os serviços.
 */
public final class NpcVivo {

    private final String id;
    private String nome;
    private String skin;
    private String mundo;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private String faccao;
    private Material itemNaMao;
    private String arvore;
    private ConfigurationSection combate;
    /** O que ele faz sozinho (passear, patrulhar, falar). null = parado como sempre. */
    private Rotina rotina;
    private final Set<Papel> papeis = EnumSet.noneOf(Papel.class);
    private final List<String> saudacao = new ArrayList<>();
    private final List<String> retorno = new ArrayList<>();
    private final List<String> falas = new ArrayList<>();
    private final List<ItemLoja> compra = new ArrayList<>();
    private final List<ItemLoja> venda = new ArrayList<>();

    /** Entidade (manequim) que representa o NPC no mundo; null quando o chunk não está carregado. */
    private UUID entidade;
    /** Criado por uma invasão: some quando ela acaba e nunca é salvo em npcs/. */
    private boolean temporario;

    public NpcVivo(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String skin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public Location local() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, x, y, z, yaw, 0f);
    }

    public String mundo() {
        return mundo;
    }

    public void setLocal(String mundo, double x, double y, double z, float yaw) {
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public String faccao() {
        return faccao;
    }

    public void setFaccao(String faccao) {
        this.faccao = faccao;
    }

    public Material itemNaMao() {
        return itemNaMao;
    }

    public void setItemNaMao(Material itemNaMao) {
        this.itemNaMao = itemNaMao;
    }

    /** Diálogo em árvore (dialogos/<arvore>.yml); null = conversa simples. */
    public String arvore() {
        return arvore;
    }

    public void setArvore(String arvore) {
        this.arvore = arvore;
    }

    /** Bloco "combate:" do npcs/&lt;id&gt;.yml: muda vida, dano, armadura... só deste NPC. null = usa o padrão do papel. */
    public Rotina rotina() {
        return rotina;
    }

    public void setRotina(Rotina rotina) {
        this.rotina = rotina;
    }

    public ConfigurationSection combate() {
        return combate;
    }

    public void setCombate(ConfigurationSection combate) {
        this.combate = combate;
    }

    public Set<Papel> papeis() {
        return papeis;
    }

    public boolean tem(Papel p) {
        return papeis.contains(p);
    }

    public List<String> saudacao() {
        return saudacao;
    }

    public List<String> retorno() {
        return retorno;
    }

    public List<String> falas() {
        return falas;
    }

    /** O que o jogador compra do NPC. */
    public List<ItemLoja> compra() {
        return compra;
    }

    /** O que o jogador vende ao NPC. */
    public List<ItemLoja> venda() {
        return venda;
    }

    public boolean temLoja() {
        return tem(Papel.COMERCIANTE) && (!compra.isEmpty() || !venda.isEmpty());
    }

    public ItemLoja itemLoja(String chave) {
        for (ItemLoja i : compra) {
            if (i.chave().equals(chave)) {
                return i;
            }
        }
        for (ItemLoja i : venda) {
            if (i.chave().equals(chave)) {
                return i;
            }
        }
        return null;
    }

    public boolean temporario() {
        return temporario;
    }

    public void setTemporario(boolean temporario) {
        this.temporario = temporario;
    }

    public UUID entidade() {
        return entidade;
    }

    public void setEntidade(UUID entidade) {
        this.entidade = entidade;
    }

    /** O NPC fica neste chunk? */
    public boolean noChunk(String mundo, int cx, int cz) {
        return this.mundo.equals(mundo) && ((int) Math.floor(x) >> 4) == cx && ((int) Math.floor(z) >> 4) == cz;
    }
}
