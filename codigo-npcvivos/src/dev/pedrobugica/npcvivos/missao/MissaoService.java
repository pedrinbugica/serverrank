package dev.pedrobugica.npcvivos.missao;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.missao.Missao.Repeticao;
import dev.pedrobugica.npcvivos.missao.Missao.Tipo;
import dev.pedrobugica.npcvivos.upgrade.ItemAprimoravel;
import dev.pedrobugica.npcvivos.upgrade.Pedra;
import dev.pedrobugica.npcvivos.upgrade.Raridade;
import dev.pedrobugica.npcvivos.util.Texto;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Missões: quem oferece, quem recebe, progresso (matar mobs conta sozinho), entrega e recompensas.
 * Ficam em plugins/NpcVivos/missoes/<id>.yml.
 */
public final class MissaoService implements Listener {

    /** Missões que vêm de exemplo (copiadas na primeira vez). */
    public static final List<String> PADRAO = List.of("borin_minerio_perdido", "borin_ferro_diario", "borin_encomenda_especial", "marta_praga_zumbis",
            "marta_carta_para_borin", "borin_resposta_para_marta");

    private final NpcVivosPlugin plugin;
    private final File pasta;
    private final NamespacedKey chavePacote;
    private final Map<String, Missao> missoes = new LinkedHashMap<>();

    public MissaoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "missoes");
        this.chavePacote = new NamespacedKey(plugin, "pacote-missao");
    }

    // ------------------------------------------------------------------ ARQUIVOS

    public void copiarExemplo(String id) {
        if (!new File(pasta, id + ".yml").exists()) {
            plugin.saveResource("missoes/" + id + ".yml", false);
        }
    }

    public void copiarExemplos() {
        for (String id : PADRAO) {
            if (!new File(pasta, id + ".yml").exists()) {
                plugin.saveResource("missoes/" + id + ".yml", false);
            }
        }
    }

    public void carregar() {
        missoes.clear();
        if (!pasta.exists() && !pasta.mkdirs()) {
            plugin.getLogger().warning("Não foi possível criar a pasta missoes/.");
        }
        File[] arquivos = pasta.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (arquivos != null) {
            for (File f : arquivos) {
                try {
                    Missao m = ler(YamlConfiguration.loadConfiguration(f), f.getName().substring(0, f.getName().length() - 4));
                    if (m != null) {
                        missoes.put(m.id(), m);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Missão inválida em missoes/" + f.getName(), e);
                }
            }
        }
        plugin.getLogger().info(missoes.size() + " missão(ões) carregada(s).");
    }

    private Missao ler(YamlConfiguration y, String idArquivo) {
        String id = y.getString("id", idArquivo).toLowerCase(Locale.ROOT);
        Tipo tipo;
        try {
            tipo = Tipo.valueOf(y.getString("tipo", "COLETAR").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Missão '" + id + "': tipo inválido (use MATAR, COLETAR ou ENTREGAR).");
            return null;
        }
        Repeticao rep;
        try {
            rep = Repeticao.valueOf(y.getString("repetir", "NUNCA").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            rep = Repeticao.NUNCA;
        }
        Material item = Material.matchMaterial(y.getString("objetivo.item", ""));
        EntityType mob = null;
        if (tipo == Tipo.MATAR) {
            try {
                mob = EntityType.valueOf(y.getString("objetivo.mob", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Missão '" + id + "': mob inválido '" + y.getString("objetivo.mob") + "'.");
                return null;
            }
        } else if (item == null || !item.isItem()) {
            plugin.getLogger().warning("Missão '" + id + "': item inválido '" + y.getString("objetivo.item") + "'.");
            return null;
        }
        List<ItemStack> itens = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("recompensa.itens")) {
            ItemStack it = itemRecompensa(m, id);
            if (it != null) {
                itens.add(it);
            }
        }
        return new Missao(id, y.getString("nome", id), y.getStringList("descricao"), y.getString("npc", ""),
                y.getString("entregar_para"), tipo, item, y.getString("objetivo.nome_item"),
                Math.max(1, y.getInt("objetivo.quantidade", 1)), mob, y.getString("objetivo.mundo"), y.getString("requisito"),
                rep, Math.max(0, y.getDouble("recompensa.dinheiro", 0)), itens, y.getStringList("recompensa.comandos"),
                y.getInt("recompensa.reputacao", 0), y.getString("fala_aceitar"), y.getString("fala_conclusao"),
                y.contains("reputacao_minima") ? y.getInt("reputacao_minima") : null, y.getBoolean("apenas_membros", false),
                Math.max(0, y.getInt("pontos_guerra", 0)));
    }

    private ItemStack itemRecompensa(Map<?, ?> m, String missao) {
        String nomeItem = String.valueOf(m.get("item")).trim().toUpperCase(Locale.ROOT);
        Object qtd = m.get("quantidade");
        int quantidade = qtd instanceof Number n ? Math.max(1, n.intValue()) : 1;
        // Pedras de aprimoramento e pergaminho de proteção (fase 5)
        if (nomeItem.startsWith("PEDRA_")) {
            Pedra pedra = Pedra.buscar(nomeItem);
            if (pedra == null) {
                plugin.getLogger().warning("Missão '" + missao + "': pedra inválida '" + m.get("item")
                        + "' (use PEDRA_COMUM, PEDRA_RARA, PEDRA_EPICA ou PEDRA_LENDARIA).");
                return null;
            }
            return plugin.upgrades().itens().criarPedra(pedra, quantidade);
        }
        if (nomeItem.startsWith("PERGAMINHO")) {
            return plugin.upgrades().itens().criarPergaminho(quantidade);
        }
        Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("Missão '" + missao + "': item de recompensa inválido '" + m.get("item") + "'.");
            return null;
        }
        ItemStack it = new ItemStack(mat, quantidade);
        Object nome = m.get("nome");
        Object enc = m.get("encantamentos");
        it.editMeta(meta -> {
            if (nome != null) {
                meta.displayName(Texto.item(String.valueOf(nome)));
            }
            if (enc instanceof Map<?, ?> encs) {
                for (Map.Entry<?, ?> e : encs.entrySet()) {
                    Enchantment ench = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                            .get(NamespacedKey.minecraft(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT)));
                    if (ench != null && e.getValue() instanceof Number nivel) {
                        meta.addEnchant(ench, nivel.intValue(), true);
                    }
                }
            }
        });
        // Arma/armadura que já vem aprimorada ou com raridade própria (ex.: raridade: LENDARIO, nivel: 3)
        if (ItemAprimoravel.tipo(it) != null) {
            Raridade r = Raridade.buscar(m.get("raridade") == null ? null : String.valueOf(m.get("raridade")));
            if (r != null) {
                plugin.upgrades().itens().definirRaridade(it, r);
            }
            if (m.get("nivel") instanceof Number nv && nv.intValue() > 0) {
                plugin.upgrades().itens().definir(it, Math.min(nv.intValue(), plugin.upgrades().nivelMaximo()));
            }
        }
        return it;
    }

    // ------------------------------------------------------------------ CONSULTAS

    public Missao obter(String id) {
        return id == null ? null : missoes.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Missao> todas() {
        return new ArrayList<>(missoes.values());
    }

    public boolean ativa(Player p, Missao m) {
        return plugin.repositorio().progressoMissao(p.getUniqueId(), m.id()) != null;
    }

    public boolean concluida(Player p, Missao m) {
        return plugin.repositorio().vezesConcluida(p.getUniqueId(), m.id()) > 0;
    }

    /** O jogador pode pegar esta missão agora? */
    public boolean disponivel(Player p, Missao m) {
        if (ativa(p, m)) {
            return false;
        }
        // Reputação com a facção de quem dá a missão
        var doador = plugin.npcs().obter(m.npc());
        if (doador != null) {
            if (!plugin.faccoes().daMissoes(p, doador)) {
                return false;
            }
            var f = plugin.faccoes().daNpc(doador);
            if (f != null && m.reputacaoMinima() != null && plugin.faccoes().reputacao(p.getUniqueId(), f.id()) < m.reputacaoMinima()) {
                return false;
            }
            if (m.apenasMembros()) {
                var membro = plugin.faccoes().membro(p);
                if (f == null || membro == null || !membro.id().equals(f.id())) {
                    return false;
                }
            }
        }
        Missao req = obter(m.requisito());
        if (m.requisito() != null && (req == null || !concluida(p, req))) {
            return false;
        }
        long ultima = plugin.repositorio().concluidaEm(p.getUniqueId(), m.id());
        if (ultima == 0) {
            return true;
        }
        LocalDate dia = Instant.ofEpochMilli(ultima).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate hoje = LocalDate.now();
        return switch (m.repetir()) {
            case NUNCA -> false;
            case DIARIA -> dia.isBefore(hoje);
            case SEMANAL -> dia.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != hoje.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    || dia.get(IsoFields.WEEK_BASED_YEAR) != hoje.get(IsoFields.WEEK_BASED_YEAR);
        };
    }

    /** Missões que este NPC pode dar ao jogador agora. */
    public List<Missao> oferecidas(Player p, NpcVivo npc) {
        List<Missao> l = new ArrayList<>();
        for (Missao m : missoes.values()) {
            if (m.npc().equalsIgnoreCase(npc.id()) && disponivel(p, m)) {
                l.add(m);
            }
        }
        return l;
    }

    /** Missões ativas que terminam neste NPC. */
    public List<Missao> entregaveis(Player p, NpcVivo npc) {
        List<Missao> l = new ArrayList<>();
        for (String id : plugin.repositorio().missoesAtivas(p.getUniqueId()).keySet()) {
            Missao m = obter(id);
            if (m != null && m.npcEntrega().equalsIgnoreCase(npc.id())) {
                l.add(m);
            }
        }
        return l;
    }

    public List<Missao> ativas(Player p) {
        List<Missao> l = new ArrayList<>();
        for (String id : plugin.repositorio().missoesAtivas(p.getUniqueId()).keySet()) {
            Missao m = obter(id);
            if (m != null) {
                l.add(m);
            }
        }
        return l;
    }

    public int progresso(Player p, Missao m) {
        return switch (m.tipo()) {
            case MATAR -> {
                Integer v = plugin.repositorio().progressoMissao(p.getUniqueId(), m.id());
                yield v == null ? 0 : v;
            }
            case COLETAR -> contarItens(p.getInventory(), m.item());
            case ENTREGAR -> contarPacotes(p.getInventory(), m);
        };
    }

    public boolean pronta(Player p, Missao m) {
        return progresso(p, m) >= m.quantidade();
    }

    // ------------------------------------------------------------------ AÇÕES

    public void aceitar(Player p, NpcVivo npc, Missao m) {
        if (!disponivel(p, m)) {
            plugin.mensagens().enviar(p, "missao-indisponivel", "missao", m.nome());
            return;
        }
        int max = plugin.getConfig().getInt("missoes.maximo-ativas", 5);
        if (plugin.repositorio().missoesAtivas(p.getUniqueId()).size() >= max) {
            plugin.mensagens().enviar(p, "missao-limite", "maximo", String.valueOf(max));
            return;
        }
        plugin.repositorio().setProgressoMissao(p.getUniqueId(), m.id(), 0);
        if (m.tipo() == Tipo.ENTREGAR) {
            for (int i = 0; i < m.quantidade(); i++) {
                for (ItemStack sobra : p.getInventory().addItem(pacote(m)).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), sobra);
                }
            }
        }
        if (m.falaAceitar() != null) {
            falar(p, npc, m.falaAceitar());
        }
        plugin.mensagens().enviar(p, "missao-aceita", "missao", m.nome(), "objetivo", objetivo(m));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    public void entregar(Player p, NpcVivo npc, Missao m) {
        if (!ativa(p, m)) {
            return;
        }
        if (!pronta(p, m)) {
            plugin.mensagens().enviar(p, "missao-faltando", "missao", m.nome(),
                    "progresso", progresso(p, m) + "/" + m.quantidade());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (m.tipo() == Tipo.COLETAR) {
            removerItens(p.getInventory(), m.item(), m.quantidade());
        } else if (m.tipo() == Tipo.ENTREGAR) {
            removerPacotes(p.getInventory(), m, m.quantidade());
        }
        plugin.repositorio().removerMissaoAtiva(p.getUniqueId(), m.id());
        plugin.repositorio().marcarConcluida(p.getUniqueId(), m.id(), System.currentTimeMillis());
        if (m.dinheiro() > 0) {
            plugin.economia().depositar(p, m.dinheiro());
        }
        for (ItemStack it : m.itens()) {
            for (ItemStack sobra : p.getInventory().addItem(it.clone()).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            }
        }
        for (String cmd : m.comandos()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{jogador}", p.getName()));
        }
        if (m.reputacao() != 0) {
            var doador = plugin.npcs().obter(m.npc());
            plugin.faccoes().alterarReputacao(p, doador != null ? plugin.faccoes().daNpc(doador) : null, m.reputacao());
        }
        if (m.falaConclusao() != null) {
            falar(p, npc, m.falaConclusao());
        }
        plugin.mensagens().enviar(p, "missao-concluida", "missao", m.nome(), "recompensa", recompensa(m));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.1f);
        plugin.comercio().registrar(p, npc, "MISSAO", m.id(), 1, m.dinheiro());
        plugin.guerras().aoConcluirMissao(p, npc, m);
        plugin.repositorio().salvarAssincrono();
    }

    public void abandonar(Player p, Missao m) {
        plugin.repositorio().removerMissaoAtiva(p.getUniqueId(), m.id());
        if (m.tipo() == Tipo.ENTREGAR) {
            removerPacotes(p.getInventory(), m, Integer.MAX_VALUE);
        }
        plugin.mensagens().enviar(p, "missao-abandonada", "missao", m.nome());
    }

    private void falar(Player p, NpcVivo npc, String fala) {
        p.sendMessage(Texto.cor(plugin.mensagens().bruta("fala"), "npc", npc.nome(),
                "fala", Texto.substituir(fala, "jogador", p.getName(), "npc", Texto.semCor(npc.nome()))));
    }

    // ------------------------------------------------------------------ PROGRESSO (MATAR)

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMatar(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        for (Missao m : ativas(p)) {
            if (m.tipo() != Tipo.MATAR || m.mob() != e.getEntityType()
                    || (m.mundo() != null && !m.mundo().equalsIgnoreCase(e.getEntity().getWorld().getName()))) {
                continue;
            }
            int atual = progresso(p, m);
            if (atual >= m.quantidade()) {
                continue;
            }
            plugin.repositorio().setProgressoMissao(p.getUniqueId(), m.id(), atual + 1);
            p.sendActionBar(Texto.cor(plugin.mensagens().bruta("missao-progresso"), "missao", Texto.semCor(m.nome()),
                    "progresso", (atual + 1) + "/" + m.quantidade()));
            if (atual + 1 >= m.quantidade()) {
                var npc = plugin.npcs().obter(m.npcEntrega());
                plugin.mensagens().enviar(p, "missao-pronta", "missao", m.nome(), "npc", npc != null ? npc.nome() : m.npcEntrega());
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            }
        }
    }

    // ------------------------------------------------------------------ TEXTOS

    public String objetivo(Missao m) {
        return switch (m.tipo()) {
            case MATAR -> "matar " + m.quantidade() + "x <lang:" + m.mob().translationKey() + ">"
                    + (m.mundo() != null ? " (" + m.mundo() + ")" : "");
            case COLETAR -> "trazer " + m.quantidade() + "x <lang:" + m.item().translationKey() + ">";
            case ENTREGAR -> {
                var npc = plugin.npcs().obter(m.npcEntrega());
                yield "levar " + (m.nomeItem() != null ? m.nomeItem() : "<lang:" + m.item().translationKey() + ">")
                        + "<reset> até " + (npc != null ? npc.nome() : m.npcEntrega());
            }
        };
    }

    public String recompensa(Missao m) {
        List<String> partes = new ArrayList<>();
        if (m.dinheiro() > 0) {
            partes.add("<green>$" + Texto.dinheiro(m.dinheiro()) + "<reset>");
        }
        for (ItemStack it : m.itens()) {
            // nome personalizado (pedras, pergaminho, armas de recompensa) ou o nome do jogo
            String nome = it.hasItemMeta() && it.getItemMeta().hasCustomName()
                    ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(it.getItemMeta().customName())
                    : "<lang:" + it.getType().translationKey() + ">";
            partes.add(it.getAmount() + "x " + nome + "<reset>");
        }
        return partes.isEmpty() ? "nada" : String.join(", ", partes);
    }

    // ------------------------------------------------------------------ ITENS

    private ItemStack pacote(Missao m) {
        ItemStack it = new ItemStack(m.item());
        it.editMeta(meta -> {
            if (m.nomeItem() != null) {
                meta.displayName(Texto.item(m.nomeItem()));
            }
            meta.lore(Texto.lore(List.of("<gray>Missão: " + m.nome())));
            meta.getPersistentDataContainer().set(chavePacote, PersistentDataType.STRING, m.id());
        });
        return it;
    }

    private boolean ehPacote(ItemStack s, Missao m) {
        return s != null && s.hasItemMeta()
                && m.id().equals(s.getItemMeta().getPersistentDataContainer().get(chavePacote, PersistentDataType.STRING));
    }

    private int contarPacotes(PlayerInventory inv, Missao m) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (ehPacote(s, m)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void removerPacotes(PlayerInventory inv, Missao m, int quantidade) {
        int falta = quantidade;
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length && falta > 0; i++) {
            if (ehPacote(c[i], m)) {
                int q = Math.min(falta, c[i].getAmount());
                falta -= q;
                if (q == c[i].getAmount()) {
                    inv.setItem(i, null);
                } else {
                    c[i].setAmount(c[i].getAmount() - q);
                    inv.setItem(i, c[i]);
                }
            }
        }
    }

    private static int contarItens(PlayerInventory inv, Material mat) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && s.getType() == mat && !s.hasItemMeta()) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static void removerItens(PlayerInventory inv, Material mat, int quantidade) {
        int falta = quantidade;
        ItemStack[] c = inv.getStorageContents();
        for (int i = 0; i < c.length && falta > 0; i++) {
            ItemStack s = c[i];
            if (s != null && s.getType() == mat && !s.hasItemMeta()) {
                int q = Math.min(falta, s.getAmount());
                falta -= q;
                if (q == s.getAmount()) {
                    c[i] = null;
                } else {
                    ItemStack resto = s.clone();
                    resto.setAmount(s.getAmount() - q);
                    c[i] = resto;
                }
            }
        }
        inv.setStorageContents(c);
    }
}
