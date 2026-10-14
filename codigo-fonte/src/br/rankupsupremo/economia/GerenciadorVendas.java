package br.rankupsupremo.economia;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Preços de venda (vendas.yml) e multiplicadores (prestígio + VIP).
 */
public final class GerenciadorVendas {

    public record Resultado(long itens, double valor, double multiplicador) {
    }

    private record Bonus(String permissao, double valor) {
    }

    private final RankupSupremo plugin;
    private final Map<Material, Double> precos = new EnumMap<>(Material.class);
    private List<Bonus> bonus = List.of();

    public GerenciadorVendas(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        precos.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "vendas.yml"));
        ConfigurationSection sec = yml.getConfigurationSection("precos");
        if (sec != null) {
            for (String nome : sec.getKeys(false)) {
                Material m = Material.matchMaterial(nome);
                if (m == null) {
                    plugin.getLogger().warning("vendas.yml: material inválido '" + nome + "'.");
                    continue;
                }
                double v = sec.getDouble(nome, 0);
                if (v > 0) {
                    precos.put(m, v);
                }
            }
        }
        List<Bonus> lista = new ArrayList<>();
        ConfigurationSection mult = plugin.getConfig().getConfigurationSection("vendas.multiplicadores");
        if (mult != null) {
            for (String k : mult.getKeys(false)) {
                String perm = mult.getString(k + ".permissao");
                double v = mult.getDouble(k + ".bonus", 0);
                if (perm != null && v > 0) {
                    lista.add(new Bonus(perm, v));
                }
            }
        }
        bonus = List.copyOf(lista);
        plugin.getLogger().info(precos.size() + " preços de venda carregados.");
    }

    public double preco(Material m) {
        Double v = precos.get(m);
        return v == null ? 0 : v;
    }

    public boolean vendavel(ItemStack item) {
        return item != null && !item.getType().isAir() && precos.containsKey(item.getType()) && !item.hasItemMeta();
    }

    public double multiplicador(Player p, DadosJogador d) {
        double extra = 0;
        for (Bonus b : bonus) {
            if (b.valor() > extra && p.hasPermission(b.permissao())) {
                extra = b.valor();
            }
        }
        return 1.0 + d.prestigio() * plugin.ranks().bonusVendaPrestigio() + extra + d.boosterAtivo();
    }

    /** Vende todos os itens vendáveis do inventário. */
    public Resultado venderInventario(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        double mult = multiplicador(p, d);
        PlayerInventory inv = p.getInventory();
        ItemStack[] conteudo = inv.getStorageContents();
        long itens = 0;
        double valor = 0;
        for (int i = 0; i < conteudo.length; i++) {
            ItemStack it = conteudo[i];
            if (vendavel(it)) {
                itens += it.getAmount();
                valor += preco(it.getType()) * it.getAmount();
                conteudo[i] = null;
            }
        }
        if (itens == 0) {
            return new Resultado(0, 0, mult);
        }
        double total = valor * mult;
        if (!plugin.economia().depositar(p, total)) {
            return new Resultado(-1, 0, mult); // economia indisponível: nada é removido
        }
        inv.setStorageContents(conteudo);
        return new Resultado(itens, total, mult);
    }
}
