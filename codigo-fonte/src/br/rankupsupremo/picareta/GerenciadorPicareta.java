package br.rankupsupremo.picareta;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picareta com melhorias compráveis. Com dinheiro: Eficiência (vanilla), Fortuna e Explosão.
 * Com tokens de mina: Laser, Sorte de Token, Velocidade, Visão Noturna e Fundição.
 */
public final class GerenciadorPicareta {

    public enum Moeda {
        DINHEIRO, TOKENS
    }

    public enum Tipo {
        EFICIENCIA("eficiencia", Moeda.DINHEIRO),
        FORTUNA("fortuna", Moeda.DINHEIRO),
        EXPLOSAO("explosao", Moeda.DINHEIRO),
        LASER("laser", Moeda.TOKENS),
        SORTE_TOKEN("sorte-token", Moeda.TOKENS),
        VELOCIDADE("velocidade", Moeda.TOKENS),
        VISAO_NOTURNA("visao-noturna", Moeda.TOKENS),
        FUNDICAO("fundicao", Moeda.TOKENS);

        private final String chave;
        private final Moeda moedaPadrao;

        Tipo(String chave, Moeda moedaPadrao) {
            this.chave = chave;
            this.moedaPadrao = moedaPadrao;
        }

        public String chave() {
            return chave;
        }

        public Moeda moedaPadrao() {
            return moedaPadrao;
        }
    }

    public record Melhoria(Tipo tipo, String nome, String descricao, Material icone, int maximo, double custoBase,
                           double multiplicadorCusto, Moeda moeda, String simbolo, String cor) {
        /** Custo para ir do nível atual para o próximo. */
        public double custo(int nivelAtual) {
            double c = custoBase * Math.pow(multiplicadorCusto, nivelAtual);
            return moeda == Moeda.TOKENS ? Math.ceil(c) : c;
        }
    }

    private static final Map<Material, Material> FUNDIDOS = new EnumMap<>(Material.class);

    static {
        FUNDIDOS.put(Material.COAL_ORE, Material.COAL);
        FUNDIDOS.put(Material.DEEPSLATE_COAL_ORE, Material.COAL);
        FUNDIDOS.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        FUNDIDOS.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        FUNDIDOS.put(Material.IRON_ORE, Material.IRON_INGOT);
        FUNDIDOS.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        FUNDIDOS.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        FUNDIDOS.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        FUNDIDOS.put(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT);
        FUNDIDOS.put(Material.LAPIS_ORE, Material.LAPIS_LAZULI);
        FUNDIDOS.put(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI);
        FUNDIDOS.put(Material.REDSTONE_ORE, Material.REDSTONE);
        FUNDIDOS.put(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE);
        FUNDIDOS.put(Material.DIAMOND_ORE, Material.DIAMOND);
        FUNDIDOS.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND);
        FUNDIDOS.put(Material.EMERALD_ORE, Material.EMERALD);
        FUNDIDOS.put(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD);
        FUNDIDOS.put(Material.NETHER_QUARTZ_ORE, Material.QUARTZ);
        FUNDIDOS.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        FUNDIDOS.put(Material.COBBLESTONE, Material.STONE);
        FUNDIDOS.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
    }

    private final RankupSupremo plugin;
    private final NamespacedKey chaveMarca;
    private final NamespacedKey chaveNomeEspecial;
    private final Map<Tipo, NamespacedKey> chaves = new EnumMap<>(Tipo.class);
    private final Map<Tipo, Melhoria> melhorias = new EnumMap<>(Tipo.class);

    private Material material;
    private String nome;
    private List<String> lore;
    private String formatoEncantamento;
    private String semEncantamentos;
    private double chanceExplosaoPorNivel;
    private int raioExplosao;
    private double chanceLaserPorNivel;
    private double bonusSorteTokenPorNivel;

    public GerenciadorPicareta(RankupSupremo plugin) {
        this.plugin = plugin;
        this.chaveMarca = new NamespacedKey(plugin, "picareta");
        this.chaveNomeEspecial = new NamespacedKey(plugin, "nome-especial");
        for (Tipo t : Tipo.values()) {
            if (t != Tipo.EFICIENCIA) {
                chaves.put(t, new NamespacedKey(plugin, t.chave()));
            }
        }
    }

    public void carregar() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("picareta");
        if (c == null) {
            c = plugin.getConfig().createSection("picareta");
        }
        Material m = Material.matchMaterial(c.getString("material", "DIAMOND_PICKAXE"));
        material = m != null && m.name().endsWith("_PICKAXE") ? m : Material.DIAMOND_PICKAXE;
        nome = c.getString("nome", "<aqua>Picareta Suprema");
        lore = c.getStringList("lore");
        formatoEncantamento = c.getString("formato-encantamento", " {cor}{simbolo} {nome} <white>{nivel}");
        semEncantamentos = c.getString("sem-encantamentos", " <gray>Nenhuma melhoria ainda");
        chanceExplosaoPorNivel = c.getDouble("explosao.chance-por-nivel", 1.5);
        raioExplosao = Math.max(1, Math.min(3, c.getInt("explosao.raio", 1)));
        chanceLaserPorNivel = c.getDouble("laser.chance-por-nivel", 0.1);
        bonusSorteTokenPorNivel = c.getDouble("sorte-token.bonus-por-nivel", 0.10);
        melhorias.clear();
        for (Tipo t : Tipo.values()) {
            ConfigurationSection s = c.getConfigurationSection("melhorias." + t.chave());
            String nomeM = s == null ? t.chave() : s.getString("nome", t.chave());
            String desc = s == null ? "" : s.getString("descricao", "");
            Material icone = Material.matchMaterial(s == null ? "BOOK" : s.getString("icone", "BOOK"));
            if (icone == null || !icone.isItem() || icone.isAir()) {
                icone = Material.BOOK;
            }
            int max = s == null ? 10 : Math.max(1, s.getInt("maximo", 10));
            if (t == Tipo.EFICIENCIA) {
                max = Math.min(max, 255);
            }
            double base = s == null ? 1000 : Math.max(0, s.getDouble("custo-base", 1000));
            double mult = s == null ? 1.5 : Math.max(1, s.getDouble("multiplicador-custo", 1.5));
            Moeda moeda = t.moedaPadrao();
            if (s != null && s.contains("moeda")) {
                moeda = "tokens".equalsIgnoreCase(s.getString("moeda")) ? Moeda.TOKENS : Moeda.DINHEIRO;
            }
            String simbolo = s == null ? "✦" : s.getString("simbolo", "✦");
            String cor = s == null ? "<aqua>" : s.getString("cor", "<aqua>");
            melhorias.put(t, new Melhoria(t, nomeM, desc, icone, max, base, mult, moeda, simbolo, cor));
        }
    }

    public Melhoria melhoria(Tipo t) {
        return melhorias.get(t);
    }

    public static boolean ehPicareta(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    /** É uma picareta do servidor (Suprema, MAX ou Divina)? Usada para não perder no PvP. */
    public boolean ehPicaretaDoServidor(ItemStack item) {
        return ehPicareta(item) && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(chaveMarca, PersistentDataType.BYTE);
    }

    public int nivel(ItemStack item, Tipo t) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (t == Tipo.EFICIENCIA) {
            return meta.getEnchantLevel(Enchantment.EFFICIENCY);
        }
        return meta.getPersistentDataContainer().getOrDefault(chaves.get(t), PersistentDataType.INTEGER, 0);
    }

    /** Aplica um nível e reescreve o visual do item. */
    public void definirNivel(ItemStack item, Tipo t, int nivel) {
        item.editMeta(meta -> {
            if (t == Tipo.EFICIENCIA) {
                if (nivel <= 0) {
                    meta.removeEnchant(Enchantment.EFFICIENCY);
                } else {
                    meta.addEnchant(Enchantment.EFFICIENCY, Math.min(255, nivel), true);
                }
            } else {
                meta.getPersistentDataContainer().set(chaves.get(t), PersistentDataType.INTEGER, Math.max(0, nivel));
            }
        });
        atualizarVisual(item);
    }

    public ItemStack criar() {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(chaveMarca, PersistentDataType.BYTE, (byte) 1);
        });
        atualizarVisual(item);
        return item;
    }

    /** Picareta de admin com níveis acima do normal (usada no /menuop). */
    public ItemStack criarEspecial(Material material, String nomeEspecial, Map<Tipo, Integer> niveis) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(chaveMarca, PersistentDataType.BYTE, (byte) 1);
            pdc.set(chaveNomeEspecial, PersistentDataType.STRING, nomeEspecial);
            for (Map.Entry<Tipo, Integer> e : niveis.entrySet()) {
                if (e.getKey() == Tipo.EFICIENCIA) {
                    if (e.getValue() > 0) {
                        meta.addEnchant(Enchantment.EFFICIENCY, Math.min(255, e.getValue()), true);
                    }
                } else {
                    pdc.set(chaves.get(e.getKey()), PersistentDataType.INTEGER, Math.max(0, e.getValue()));
                }
            }
        });
        atualizarVisual(item);
        return item;
    }

    /** Níveis máximos de todas as melhorias (para a Picareta MAX). */
    public Map<Tipo, Integer> niveisMaximos() {
        Map<Tipo, Integer> m = new EnumMap<>(Tipo.class);
        for (Tipo t : Tipo.values()) {
            m.put(t, melhorias.get(t).maximo());
        }
        return m;
    }

    public void atualizarVisual(ItemStack item) {
        Map<Tipo, Integer> niveis = new EnumMap<>(Tipo.class);
        List<String> pares = new ArrayList<>();
        int soma = 0;
        for (Tipo t : Tipo.values()) {
            int n = nivel(item, t);
            niveis.put(t, n);
            soma += n;
            pares.add(t.chave());
            pares.add(String.valueOf(n));
        }
        String[] arr = pares.toArray(new String[0]);
        String nomeEspecial = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(chaveNomeEspecial, PersistentDataType.STRING) : null;
        String nomeFinal = nomeEspecial != null ? nomeEspecial : nome;
        List<String> linhas = new ArrayList<>();
        for (String linha : lore) {
            if (linha.contains("{encantamentos}")) {
                boolean algum = false;
                for (Tipo t : Tipo.values()) {
                    int n = niveis.get(t);
                    if (n > 0) {
                        Melhoria m = melhorias.get(t);
                        linhas.add(Texto.substituir(formatoEncantamento, "cor", m.cor(), "simbolo", m.simbolo(),
                                "nome", m.nome(), "nivel", String.valueOf(n)));
                        algum = true;
                    }
                }
                if (!algum) {
                    linhas.add(semEncantamentos);
                }
            } else {
                linhas.add(linha);
            }
        }
        final int total = soma;
        item.editMeta(meta -> {
            meta.displayName(Texto.item(nomeFinal, arr));
            meta.lore(Texto.lore(linhas, arr));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.setEnchantmentGlintOverride(total > 0 ? Boolean.TRUE : null);
        });
    }

    /** Quantidade de itens obtidos ao quebrar um bloco com determinada Fortuna. */
    public int quantidade(int fortuna) {
        if (fortuna <= 0) {
            return 1;
        }
        return 1 + ThreadLocalRandom.current().nextInt(fortuna / 2 + 1);
    }

    public boolean sortearExplosao(int nivel) {
        return nivel > 0 && ThreadLocalRandom.current().nextDouble(100.0) < nivel * chanceExplosaoPorNivel;
    }

    public boolean sortearLaser(int nivel) {
        return nivel > 0 && ThreadLocalRandom.current().nextDouble(100.0) < nivel * chanceLaserPorNivel;
    }

    /** Multiplicador da chance de ganhar tokens (Sorte de Token). */
    public double multiplicadorSorteToken(int nivel) {
        return 1.0 + Math.max(0, nivel) * bonusSorteTokenPorNivel;
    }

    /** Fundição: minério vira barra/gema (blocos comuns não mudam). */
    public static Material fundir(Material m) {
        return FUNDIDOS.getOrDefault(m, m);
    }

    public int raioExplosao() {
        return raioExplosao;
    }

    public double chanceExplosaoPorNivel() {
        return chanceExplosaoPorNivel;
    }

    public double chanceLaserPorNivel() {
        return chanceLaserPorNivel;
    }

    public double bonusSorteTokenPorNivel() {
        return bonusSorteTokenPorNivel;
    }

    /** O jogador possui alguma picareta no inventário? */
    public boolean temPicareta(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (ehPicareta(it)) {
                return true;
            }
        }
        return false;
    }

    public static Tipo tipoPorNome(String s) {
        String n = s.toLowerCase(Locale.ROOT);
        for (Tipo t : Tipo.values()) {
            if (t.chave().equals(n) || t.name().equalsIgnoreCase(n.replace('-', '_'))) {
                return t;
            }
        }
        return null;
    }
}
