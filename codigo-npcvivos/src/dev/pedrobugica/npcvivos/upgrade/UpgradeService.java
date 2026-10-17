package dev.pedrobugica.npcvivos.upgrade;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.menu.Menu;
import dev.pedrobugica.npcvivos.upgrade.ItemAprimoravel.Tipo;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Upgrade de itens (+1 a +10) no NPC ferreiro: regras do upgrades.yml, a forja (menu) e a tentativa.
 * O bônus em combate e as pedras que caem dos monstros ficam no UpgradeListener.
 */
public final class UpgradeService {

    private final NpcVivosPlugin plugin;
    private final ItemAprimoravel itens;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final Map<Integer, NivelUpgrade> niveis = new TreeMap<>();
    private final Map<Raridade, Integer> maximos = new EnumMap<>(Raridade.class);
    private final Map<String, Raridade> porMaterial = new LinkedHashMap<>();
    /** Quem desligou o pergaminho de proteção na forja (o padrão é ligado). */
    private final Set<UUID> semPergaminho = ConcurrentHashMap.newKeySet();

    public UpgradeService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.itens = new ItemAprimoravel(plugin, this);
    }

    public ItemAprimoravel itens() {
        return itens;
    }

    // ------------------------------------------------------------------ CONFIGURAÇÃO

    public void carregar() {
        File f = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!f.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(f);
        niveis.clear();
        ConfigurationSection ns = cfg.getConfigurationSection("niveis");
        if (ns != null) {
            for (String k : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(k);
                int n;
                try {
                    n = Integer.parseInt(k.trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("upgrades.yml: nível inválido '" + k + "'.");
                    continue;
                }
                if (s == null || n < 1 || n > 100) {
                    continue;
                }
                String nomePedra = s.getString("pedra", "");
                Pedra pedra = nomePedra.isBlank() || nomePedra.equalsIgnoreCase("nenhuma") ? null : Pedra.buscar(nomePedra);
                if (pedra == null && !nomePedra.isBlank() && !nomePedra.equalsIgnoreCase("nenhuma")) {
                    plugin.getLogger().warning("upgrades.yml: pedra inválida '" + nomePedra + "' no nível " + n
                            + " (use comum, rara, epica ou lendaria).");
                }
                niveis.put(n, new NivelUpgrade(n, Math.max(0, Math.min(100, s.getDouble("chance", 100))),
                        Math.max(0, s.getDouble("dinheiro", 0)), pedra, Math.max(0, s.getInt("quantidade", 1)),
                        Math.max(0, s.getInt("se-falhar", 0)), Math.max(0, s.getDouble("bonus", 5))));
            }
        }
        maximos.clear();
        for (Raridade r : Raridade.values()) {
            maximos.put(r, Math.max(0, cfg.getInt("raridade.nivel-maximo." + r.name(), nivelMaximo())));
        }
        porMaterial.clear();
        ConfigurationSection ms = cfg.getConfigurationSection("raridade.materiais");
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                Raridade r = Raridade.buscar(ms.getString(k));
                if (r != null) {
                    porMaterial.put(k.toUpperCase(Locale.ROOT), r);
                }
            }
        }
        plugin.getLogger().info("Upgrade de itens: " + niveis.size() + " nível(is) configurado(s).");
    }

    public boolean ativo() {
        return cfg.getBoolean("ativo", true) && !niveis.isEmpty();
    }

    /** Nível mais alto que existe no upgrades.yml (normalmente 10). */
    public int nivelMaximo() {
        return niveis.isEmpty() ? 0 : ((TreeMap<Integer, NivelUpgrade>) niveis).lastKey();
    }

    public NivelUpgrade nivel(int n) {
        return niveis.get(n);
    }

    /** Bônus somado de todos os níveis até n (em %). */
    public double bonusTotal(int n) {
        double s = 0;
        for (int i = 1; i <= n; i++) {
            NivelUpgrade nu = niveis.get(i);
            s += nu == null ? 0 : nu.bonus();
        }
        return s;
    }

    public int maximo(Raridade r) {
        return Math.min(nivelMaximo(), maximos.getOrDefault(r, nivelMaximo()));
    }

    /** Raridade pelo começo do nome do material (NETHERITE_ = Lendário...). O resto é Comum. */
    public Raridade raridadePorMaterial(Material m) {
        String nome = m.name();
        for (Map.Entry<String, Raridade> e : porMaterial.entrySet()) {
            if (nome.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return Raridade.COMUM;
    }

    /** Valor da habilidade: % (roubo de vida, área) ou segundos (lentidão). */
    public double valorHabilidade(Habilidade h, int nivel) {
        String base = "habilidades." + h.chave() + ".";
        return cfg.getDouble(base + (nivel >= 10 ? "nivel-10" : "nivel-9"), switch (h) {
            case ROUBO_DE_VIDA -> nivel >= 10 ? 12 : 8;
            case DANO_EM_AREA -> nivel >= 10 ? 40 : 25;
            case LENTIDAO -> nivel >= 10 ? 3 : 2;
        });
    }

    public double raioArea() {
        return cfg.getDouble("habilidades.dano-em-area.raio", 3);
    }

    public String descricaoHabilidade(Habilidade h, int nivel) {
        String v = Texto.dinheiro(valorHabilidade(h, nivel));
        return switch (h) {
            case ROUBO_DE_VIDA -> v + "% do dano vira vida";
            case DANO_EM_AREA -> v + "% do dano nos monstros em volta";
            case LENTIDAO -> "lentidão " + (nivel >= 10 ? "II" : "I") + " por " + v + "s";
        };
    }

    /** Teto do bônus (dano e defesa) quando é jogador contra jogador. */
    public double pvpMaximo() {
        return Math.max(0, cfg.getDouble("pvp.bonus-maximo", 0.40));
    }

    public boolean mundoSemUpgrade(World w) {
        return w != null && cfg.getStringList("mundos-sem-upgrade").contains(w.getName());
    }

    // drops
    public boolean dropProibido(World w) {
        return w == null || cfg.getStringList("drops.mundos-proibidos").contains(w.getName());
    }

    public double chanceDrop(Pedra p) {
        return Math.max(0, cfg.getDouble("drops.chance." + p.name().toLowerCase(Locale.ROOT), 0));
    }

    public double chancePergaminho() {
        return Math.max(0, cfg.getDouble("drops.pergaminho-chance", 0));
    }

    /** Pedras garantidas de um chefe (null = não é chefe). */
    public Map<Pedra, Integer> dropChefe(EntityType tipo) {
        ConfigurationSection s = cfg.getConfigurationSection("drops.chefes." + tipo.name());
        if (s == null) {
            return null;
        }
        Map<Pedra, Integer> r = new EnumMap<>(Pedra.class);
        for (String k : s.getKeys(false)) {
            Pedra p = Pedra.buscar(k);
            if (p != null && s.getInt(k) > 0) {
                r.put(p, s.getInt(k));
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ CHANCE E CUSTO

    /** Bônus de chance pela reputação com a facção do ferreiro (pontos de %). */
    public double bonusReputacao(Player p, NpcVivo npc) {
        Faccao f = plugin.faccoes().daNpc(npc);
        if (f == null) {
            return 0;
        }
        return cfg.getDouble("chance-por-reputacao." + plugin.faccoes().nivel(p.getUniqueId(), f.id()).name(), 0);
    }

    public double chance(Player p, NpcVivo npc, NivelUpgrade nu) {
        return Math.min(100, nu.chance() + (nu.chance() < 100 ? bonusReputacao(p, npc) : 0));
    }

    public double custo(Player p, NpcVivo npc, NivelUpgrade nu) {
        double m = cfg.getBoolean("desconto-da-loja", true) ? plugin.faccoes().multiplicadorCompra(p, npc) : 1.0;
        return Math.round(nu.dinheiro() * m * 100) / 100.0;
    }

    // ------------------------------------------------------------------ INVENTÁRIO

    public int contarPedras(Player p, Pedra pedra) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && itens.pedra(s) == pedra) {
                n += s.getAmount();
            }
        }
        return n;
    }

    public int contarPergaminhos(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (itens.ehPergaminho(s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void remover(Player p, java.util.function.Predicate<ItemStack> qual, int quantidade) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] conteudo = inv.getStorageContents();
        int falta = quantidade;
        for (int i = 0; i < conteudo.length && falta > 0; i++) {
            ItemStack s = conteudo[i];
            if (s != null && qual.test(s)) {
                int tira = Math.min(falta, s.getAmount());
                s.setAmount(s.getAmount() - tira);
                conteudo[i] = s.getAmount() <= 0 ? null : s;
                falta -= tira;
            }
        }
        inv.setStorageContents(conteudo);
    }

    /** Dá itens ao jogador; o que não couber cai no chão. */
    public void dar(Player p, ItemStack item) {
        for (ItemStack sobra : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
    }

    // ------------------------------------------------------------------ FORJA (MENU)

    public void abrirForja(Player p, NpcVivo npc) {
        String recusa = plugin.faccoes().recusaNegociar(p, npc);
        if (recusa != null) {
            plugin.mensagens().enviar(p, recusa, "npc", npc.nome());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (!ativo()) {
            plugin.mensagens().enviar(p, "upgrade-desligado");
            return;
        }
        if (!plugin.economia().ativa()) {
            plugin.mensagens().enviar(p, "economia-indisponivel");
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        Tipo tipo = ItemAprimoravel.tipo(mao);
        if (tipo == null) {
            plugin.mensagens().enviar(p, "upgrade-segure-item");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        ItemStack copia = mao.clone();
        int n = itens.nivel(mao);
        Raridade r = itens.raridade(mao);
        int max = maximo(r);
        Menu m = new Menu(3, Texto.cor(Texto.substituir(plugin.mensagens().bruta("forja-titulo"), "npc", Texto.semCor(npc.nome()))));

        ItemStack mostra = mao.clone();
        adicionarLore(mostra, List.of("<gray>Raridade: " + r.formatado() + " <dark_gray>(pode ir até +" + max + ")"));
        m.item(10, mostra, null);
        m.item(4, Menu.criar(Material.BOOK, "<yellow>Como funciona", List.of(
                "<gray>Pague e gaste pedras para subir o nível",
                "<gray>do item que está na sua mão (até +" + nivelMaximo() + ").",
                "<gray>Quanto mais alto, mais caro e mais",
                "<gray>difícil. A partir do +7 o item pode",
                "<gray>voltar de nível se der errado.",
                "",
                "<gray>Pedras caem de monstros e chefes",
                "<gray>e vêm de missões.",
                "<gray>A raridade limita o nível máximo:",
                "<gray>Comum +" + maximo(Raridade.COMUM) + ", Raro +" + maximo(Raridade.RARO) + ", Épico +"
                        + maximo(Raridade.EPICO) + ", Lendário +" + maximo(Raridade.LENDARIO) + ".")), null);
        m.item(26, Menu.criar(Material.BARRIER, "<red>Fechar", List.of()), (pl, c) -> pl.closeInventory());
        if (npc.temLoja()) {
            m.item(18, Menu.criar(Material.CHEST, "<yellow>Voltar à loja", List.of()), (pl, c) -> plugin.comercio().abrir(pl, npc, true));
        }

        NivelUpgrade nu = n >= max ? null : nivel(n + 1);
        if (nu == null) {
            m.item(13, Menu.criar(Material.NETHER_STAR, "<gold>Nível máximo!", List.of(
                    "<gray>Este item já chegou ao máximo",
                    "<gray>da raridade dele (+" + max + ").",
                    "",
                    "<gray>Itens de diamante vão até +" + maximo(Raridade.EPICO) + " e",
                    "<gray>de netherite até +" + maximo(Raridade.LENDARIO) + ".")), null);
            m.preencher(Material.GRAY_STAINED_GLASS_PANE);
            m.abrir(p);
            return;
        }
        String palavra = tipo == Tipo.ARMADURA ? "Defesa" : "Dano";
        List<String> prox = new ArrayList<>();
        prox.add("<gray>" + palavra + ": <white>+" + Texto.dinheiro(bonusTotal(n)) + "% <gray>→ <green>+" + Texto.dinheiro(bonusTotal(n + 1)) + "%");
        if (tipo == Tipo.ARMA && n + 1 == 9) {
            prox.add("");
            prox.add("<gold>No +9 a arma desperta uma habilidade:");
            prox.add("<gold>roubo de vida, dano em área ou lentidão.");
        }
        if (n + 1 >= 7 && n < 7) {
            prox.add("<light_purple>No +7 o item passa a brilhar.");
        }
        String cor = "<" + NamedTextColor.NAMES.key(ItemAprimoravel.corDoNivel(n + 1)) + ">";
        m.item(12, Menu.criar(Material.EXPERIENCE_BOTTLE, cor + "<bold>Próximo: +" + (n + 1), prox), null);

        double custo = custo(p, npc, nu);
        double saldo = plugin.economia().saldo(p);
        List<String> lc = new ArrayList<>();
        lc.add("<gray>Dinheiro: " + (saldo >= custo ? "<green>" : "<red>") + "$" + Texto.dinheiro(custo)
                + (custo < nu.dinheiro() - 0.001 ? " <dark_gray><st>$" + Texto.dinheiro(nu.dinheiro()) + "</st>" : ""));
        if (nu.pedra() != null && nu.quantidade() > 0) {
            int tem = contarPedras(p, nu.pedra());
            lc.add("<gray>Pedras: " + (tem >= nu.quantidade() ? "<green>" : "<red>") + nu.quantidade() + "x " + nu.pedra().nomeCompleto());
            lc.add("<dark_gray>(você tem " + tem + ")");
        }
        m.item(14, Menu.criar(Material.SUNFLOWER, "<yellow>Custo", lc), null);

        double chance = chance(p, npc, nu);
        double bonusRep = chance - nu.chance();
        List<String> lch = new ArrayList<>();
        if (bonusRep > 0.001) {
            lch.add("<green>+" + Texto.dinheiro(bonusRep) + "% pela sua reputação com o ferreiro");
        }
        lch.add("<gray>Se falhar: " + (nu.seFalhar() <= 0 ? "<white>o item não perde nível" : "<red>o item volta "
                + nu.seFalhar() + (nu.seFalhar() == 1 ? " nível" : " níveis")));
        lch.add("<dark_gray>(o custo é gasto mesmo se falhar)");
        Material corChance = chance >= 70 ? Material.LIME_DYE : chance >= 40 ? Material.YELLOW_DYE : Material.RED_DYE;
        m.item(16, Menu.criar(corChance, "<white>Chance de sucesso: <bold>" + Texto.dinheiro(chance) + "%", lch), null);

        if (nu.seFalhar() > 0) {
            int perg = contarPergaminhos(p);
            boolean ligado = perg > 0 && !semPergaminho.contains(p.getUniqueId());
            ItemStack icone = Menu.criar(Material.PAPER, "<gold>Pergaminho de Proteção: " + (ligado ? "<green>LIGADO" : "<red>DESLIGADO"),
                    perg > 0 ? List.of("<gray>Você tem " + perg + ".", "<gray>Se falhar, o pergaminho se desfaz",
                            "<gray>e o item não perde nível.", "", "<yellow>Clique para " + (ligado ? "desligar" : "ligar"))
                            : List.of("<gray>Você não tem nenhum.", "<gray>Ele protege o item de perder nível",
                            "<gray>quando o aprimoramento falha.", "<dark_gray>Cai de monstros e vem de missões."));
            if (ligado) {
                icone.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            m.item(20, icone, (pl, c) -> {
                if (!semPergaminho.remove(pl.getUniqueId())) {
                    semPergaminho.add(pl.getUniqueId());
                }
                abrirForja(pl, npc);
            });
        }
        m.item(22, Menu.criar(Material.ANVIL, "<green><bold>Aprimorar para +" + (n + 1), List.of(
                "<gray>Chance: <white>" + Texto.dinheiro(chance) + "%", "", "<yellow>Clique para tentar!")), (pl, c) -> tentar(pl, npc, copia));
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
    }

    private static void adicionarLore(ItemStack icone, List<String> linhas) {
        icone.editMeta(meta -> {
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.addAll(Texto.lore(linhas));
            meta.lore(lore);
        });
    }

    // ------------------------------------------------------------------ TENTATIVA

    private void tentar(Player p, NpcVivo npc, ItemStack copia) {
        if (!plugin.comercio().perto(p, npc)) {
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (!mao.isSimilar(copia) || mao.getAmount() != copia.getAmount()) {
            plugin.mensagens().enviar(p, "upgrade-item-mudou");
            abrirForja(p, npc);
            return;
        }
        int n = itens.nivel(mao);
        int max = maximo(itens.raridade(mao));
        NivelUpgrade nu = n >= max ? null : nivel(n + 1);
        if (nu == null) {
            plugin.mensagens().enviar(p, "upgrade-maximo", "maximo", String.valueOf(max));
            return;
        }
        double custo = custo(p, npc, nu);
        if (plugin.economia().saldo(p) < custo) {
            plugin.mensagens().enviar(p, "sem-dinheiro", "preco", Texto.dinheiro(custo));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (nu.pedra() != null && nu.quantidade() > 0 && contarPedras(p, nu.pedra()) < nu.quantidade()) {
            plugin.mensagens().enviar(p, "upgrade-sem-pedras", "quantidade", String.valueOf(nu.quantidade()),
                    "pedra", nu.pedra().nomeCompleto());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (custo > 0 && !plugin.economia().sacar(p, custo)) {
            plugin.mensagens().enviar(p, "erro-transacao");
            return;
        }
        if (nu.pedra() != null && nu.quantidade() > 0) {
            Pedra pedra = nu.pedra();
            remover(p, s -> itens.pedra(s) == pedra, nu.quantidade());
        }
        String nome = itens.nomeBase(mao);
        boolean sucesso = ThreadLocalRandom.current().nextDouble() * 100 < chance(p, npc, nu);
        if (sucesso) {
            Habilidade nova = itens.definir(mao, n + 1);
            p.getInventory().setItemInMainHand(mao);
            plugin.mensagens().enviar(p, "upgrade-sucesso", "item", nome, "nivel", String.valueOf(n + 1));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            p.getWorld().spawnParticle(n + 1 >= 9 ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER,
                    p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0.1);
            if (nova != null && n + 1 >= 9) {
                plugin.mensagens().enviar(p, "upgrade-habilidade", "habilidade", nova.nome(),
                        "descricao", descricaoHabilidade(nova, n + 1));
            }
            if (n + 1 >= cfg.getInt("anunciar-a-partir-do-nivel", 9)) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    plugin.mensagens().enviar(o, "upgrade-anuncio", "jogador", p.getName(), "item", nome, "nivel", String.valueOf(n + 1));
                }
            }
        } else {
            int queda = nu.seFalhar();
            boolean protegido = false;
            if (queda > 0 && !semPergaminho.contains(p.getUniqueId()) && contarPergaminhos(p) > 0) {
                remover(p, itens::ehPergaminho, 1);
                queda = 0;
                protegido = true;
            }
            int novo = Math.max(0, n - queda);
            if (novo != n) {
                itens.definir(mao, novo);
                p.getInventory().setItemInMainHand(mao);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.8f, 1f);
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.2, 0), 25, 0.3, 0.4, 0.3, 0.02);
            plugin.mensagens().enviar(p, protegido ? "upgrade-falhou-protegido" : queda > 0 ? "upgrade-falhou-caiu" : "upgrade-falhou",
                    "item", nome, "nivel", String.valueOf(novo));
        }
        plugin.comercio().registrar(p, npc, sucesso ? "UPGRADE" : "FALHOU", mao.getType().name() + " +" + (n + 1), 1, custo);
        abrirForja(p, npc);
    }
}
