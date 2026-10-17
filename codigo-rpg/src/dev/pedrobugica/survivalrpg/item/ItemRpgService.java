package dev.pedrobugica.survivalrpg.item;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.mob.MobService;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Itens de RPG: raridade, nível exigido, atributos sorteados e um efeito especial (Épico e Lendário).
 * Tudo fica dentro do item (PersistentDataContainer) e a lore é gerada a partir disso.
 */
public final class ItemRpgService {

    public enum Especial {
        SANGRAMENTO("Sangramento", "chance de fazer o alvo sangrar"),
        ROUBO_DE_VIDA("Roubo de vida", "parte do dano vira vida"),
        GOLPE_CERTEIRO("Golpe certeiro", "mais chance de crítico");

        private final String nome;
        private final String descricao;

        Especial(String nome, String descricao) {
            this.nome = nome;
            this.descricao = descricao;
        }

        public String nome() {
            return nome;
        }

        public String descricao() {
            return descricao;
        }
    }

    private enum Tipo {
        ESPADA("Espada", "_SWORD", true), MACHADO("Machado", "_AXE", true), ARCO("Arco", "BOW", true),
        BESTA("Besta", "CROSSBOW", true), CAJADO("Cajado", "BLAZE_ROD", true),
        ELMO("Elmo", "_HELMET", false), PEITORAL("Peitoral", "_CHESTPLATE", false),
        CALCAS("Calças", "_LEGGINGS", false), BOTAS("Botas", "_BOOTS", false);

        final String nome;
        final String sufixo;
        final boolean arma;

        Tipo(String nome, String sufixo, boolean arma) {
            this.nome = nome;
            this.sufixo = sufixo;
            this.arma = arma;
        }
    }

    private static final String[] NOMES = {"do Viajante", "da Aurora", "do Lobo Cinzento", "das Brasas", "do Vale Sombrio",
            "do Guardião", "da Tempestade", "do Rei Esquecido", "da Lua Pálida", "do Dragão Adormecido", "dos Confins",
            "da Rainha Afogada", "do Arcano", "do Caçador", "da Fortaleza"};

    private final SurvivalRpgPlugin plugin;
    private final NamespacedKey chaveRaridade;
    private final NamespacedKey chaveNivel;
    private final NamespacedKey chaveAtributos;
    private final NamespacedKey chaveEspecial;

    public ItemRpgService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.chaveRaridade = new NamespacedKey(plugin, "raridade");
        this.chaveNivel = new NamespacedKey(plugin, "item-nivel");
        this.chaveAtributos = new NamespacedKey(plugin, "atributos");
        this.chaveEspecial = new NamespacedKey(plugin, "especial");
    }

    // ------------------------------------------------------------------ LEITURA

    public boolean ehItemRpg(ItemStack it) {
        return it != null && !it.getType().isAir() && it.getPersistentDataContainer().has(chaveRaridade);
    }

    public Raridade raridade(ItemStack it) {
        return ehItemRpg(it) ? Raridade.buscar(it.getPersistentDataContainer().get(chaveRaridade, PersistentDataType.STRING)) : null;
    }

    public int nivelExigido(ItemStack it) {
        return ehItemRpg(it) ? it.getPersistentDataContainer().getOrDefault(chaveNivel, PersistentDataType.INTEGER, 1) : 0;
    }

    public Map<Atributo, Integer> atributos(ItemStack it) {
        Map<Atributo, Integer> r = new EnumMap<>(Atributo.class);
        if (!ehItemRpg(it)) {
            return r;
        }
        String s = it.getPersistentDataContainer().get(chaveAtributos, PersistentDataType.STRING);
        if (s != null && !s.isBlank()) {
            for (String par : s.split(",")) {
                String[] kv = par.split("=");
                Atributo a = kv.length == 2 ? Atributo.buscar(kv[0]) : null;
                if (a != null) {
                    try {
                        r.merge(a, Integer.parseInt(kv[1]), Integer::sum);
                    } catch (NumberFormatException ignorado) {
                        // valor estranho: ignora
                    }
                }
            }
        }
        return r;
    }

    public Especial especial(ItemStack it) {
        if (!ehItemRpg(it)) {
            return null;
        }
        String s = it.getPersistentDataContainer().get(chaveEspecial, PersistentDataType.STRING);
        try {
            return s == null ? null : Especial.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** O jogador pode usar os atributos deste item? (nível exigido) */
    public boolean podeUsar(Player p, ItemStack it) {
        return nivelExigido(it) <= plugin.perfis().perfil(p).nivel();
    }

    /** Soma de um atributo em tudo que o jogador está usando (arma na mão + armadura), respeitando o nível. */
    public int somaAtributo(Player p, Atributo a) {
        int s = 0;
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (ehItemRpg(mao) && ehArma(mao) && podeUsar(p, mao)) {
            s += atributos(mao).getOrDefault(a, 0);
        }
        for (ItemStack peca : p.getInventory().getArmorContents()) {
            if (ehItemRpg(peca) && podeUsar(p, peca)) {
                s += atributos(peca).getOrDefault(a, 0);
            }
        }
        return s;
    }

    /** Efeitos especiais ativos (arma na mão e armaduras). */
    public List<Especial> especiais(Player p) {
        List<Especial> l = new ArrayList<>();
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (ehArma(mao) && podeUsar(p, mao) && especial(mao) != null) {
            l.add(especial(mao));
        }
        for (ItemStack peca : p.getInventory().getArmorContents()) {
            if (ehItemRpg(peca) && podeUsar(p, peca) && especial(peca) != null) {
                l.add(especial(peca));
            }
        }
        return l;
    }

    private static boolean ehArma(ItemStack it) {
        if (it == null) {
            return false;
        }
        for (Tipo t : Tipo.values()) {
            if (t.arma && (t.sufixo.startsWith("_") ? it.getType().name().endsWith(t.sufixo) : it.getType().name().equals(t.sufixo))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ CRIAÇÃO

    /** Item de RPG novo: tipo sorteado (ou da classe, às vezes), material pelo nível, atributos pela raridade. */
    public ItemStack gerar(int nivel, Raridade raridade, Classe preferida) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        nivel = Math.max(1, Math.min(plugin.perfis().nivelMaximo(), nivel));
        Tipo tipo = Tipo.values()[r.nextInt(Tipo.values().length)];
        if (preferida != null && r.nextDouble() < 0.4) {
            tipo = switch (preferida) {
                case ARQUEIRO -> r.nextBoolean() ? Tipo.ARCO : Tipo.BESTA;
                case MAGO -> Tipo.CAJADO;
                case CLERIGO -> Tipo.MACHADO;
                default -> Tipo.ESPADA;
            };
        }
        Material mat = material(tipo, nivel, raridade);
        ItemStack it = new ItemStack(mat);
        // Atributos: a classe "dona" do tipo puxa o atributo principal dela
        List<Atributo> possiveis = new ArrayList<>(List.of(Atributo.values()));
        Collections.shuffle(possiveis);
        int quantos = r.nextInt(raridade.minAtributos(), raridade.maxAtributos() + 1);
        Map<Atributo, Integer> atrib = new EnumMap<>(Atributo.class);
        Atributo principal = principalDoTipo(tipo);
        if (quantos > 0 && principal != null) {
            atrib.put(principal, valor(nivel, raridade, r));
        }
        for (Atributo a : possiveis) {
            if (atrib.size() >= quantos) {
                break;
            }
            atrib.putIfAbsent(a, valor(nivel, raridade, r));
        }
        Especial esp = raridade.temEspecial() ? Especial.values()[r.nextInt(Especial.values().length)] : null;
        String nome = tipo.nome + " " + NOMES[r.nextInt(NOMES.length)];
        final int nivelFinal = nivel;
        final Tipo tipoFinal = tipo;
        it.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(chaveRaridade, PersistentDataType.STRING, raridade.name());
            pdc.set(chaveNivel, PersistentDataType.INTEGER, nivelFinal);
            pdc.set(chaveAtributos, PersistentDataType.STRING, String.join(",",
                    atrib.entrySet().stream().map(e -> e.getKey().name() + "=" + e.getValue()).toList()));
            if (esp != null) {
                pdc.set(chaveEspecial, PersistentDataType.STRING, esp.name());
            }
            meta.customName(Component.text(nome, raridade.cor()).decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, raridade == Raridade.LENDARIO));
            List<String> lore = new ArrayList<>();
            lore.add(raridade.tag() + raridade.nome() + " <dark_gray>• <gray>" + tipoFinal.nome + " de nível " + nivelFinal);
            lore.add("<gray>Exige nível <white>" + nivelFinal);
            if (!atrib.isEmpty()) {
                lore.add("");
            }
            for (Map.Entry<Atributo, Integer> e : atrib.entrySet()) {
                lore.add(e.getKey().cor() + "+" + e.getValue() + " " + e.getKey().nome());
            }
            if (esp != null) {
                lore.add("");
                lore.add("<gold>✦ " + esp.nome() + ": <gray>" + esp.descricao());
            }
            meta.lore(Texto.lore(lore));
            if (raridade == Raridade.LENDARIO) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return it;
    }

    private static int valor(int nivel, Raridade raridade, ThreadLocalRandom r) {
        double base = 1 + nivel / 4.0;
        return Math.max(1, (int) Math.round(base * raridade.forca() * r.nextDouble(0.75, 1.25)));
    }

    private static Atributo principalDoTipo(Tipo t) {
        return switch (t) {
            case ESPADA -> Atributo.FORCA;
            case MACHADO -> Atributo.FORCA;
            case ARCO, BESTA -> Atributo.DESTREZA;
            case CAJADO -> Atributo.INTELIGENCIA;
            default -> Atributo.VITALIDADE;
        };
    }

    private static Material material(Tipo tipo, int nivel, Raridade raridade) {
        if (tipo == Tipo.ARCO) {
            return Material.BOW;
        }
        if (tipo == Tipo.BESTA) {
            return Material.CROSSBOW;
        }
        if (tipo == Tipo.CAJADO) {
            return nivel >= 30 ? Material.BREEZE_ROD : Material.BLAZE_ROD;
        }
        String metal;
        if (nivel >= 40 && raridade.ordinal() >= Raridade.EPICO.ordinal()) {
            metal = "NETHERITE";
        } else if (nivel >= 25) {
            metal = "DIAMOND";
        } else if (nivel >= 12) {
            metal = "IRON";
        } else {
            metal = tipo == Tipo.ESPADA || tipo == Tipo.MACHADO ? "STONE" : "CHAINMAIL";
        }
        Material m = Material.matchMaterial(metal + tipo.sufixo);
        return m != null ? m : Material.matchMaterial("IRON" + tipo.sufixo);
    }

    // ------------------------------------------------------------------ LOOT

    /** Sorteia (ou não) um item para o que caiu de um mob. */
    public ItemStack loot(int nivel, MobService.Tier tier, Classe preferida) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String k = tier.name().toLowerCase(Locale.ROOT);
        double chance = plugin.getConfig().getDouble("loot.chance." + k, switch (tier) {
            case NORMAL -> 0.03;
            case ELITE -> 0.35;
            case CAMPEAO -> 1.0;
        });
        if (r.nextDouble() >= chance) {
            return null;
        }
        return gerar(nivel, sortearRaridade(plugin.getConfig().getIntegerList("loot.pesos." + k), tier), preferida);
    }

    public Raridade sortearRaridade(List<Integer> pesos, MobService.Tier tier) {
        if (pesos.size() < 5) {
            pesos = switch (tier) {
                case NORMAL -> List.of(60, 30, 9, 1, 0);
                case ELITE -> List.of(0, 40, 45, 13, 2);
                case CAMPEAO -> List.of(0, 0, 40, 50, 10);
            };
        }
        int total = 0;
        for (int p : pesos) {
            total += Math.max(0, p);
        }
        int s = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (int i = 0; i < 5; i++) {
            s -= Math.max(0, pesos.get(i));
            if (s < 0) {
                return Raridade.values()[i];
            }
        }
        return Raridade.COMUM;
    }
}
