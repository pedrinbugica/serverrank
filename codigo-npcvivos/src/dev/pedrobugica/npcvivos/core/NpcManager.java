package dev.pedrobugica.npcvivos.core;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.util.Texto;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Cria, guarda e acha os NPCs. Cada NPC é um arquivo em plugins/NpcVivos/npcs/<id>.yml e aparece no mundo
 * como um Manequim (entidade com corpo de jogador do próprio Minecraft). A entidade não é salva no mundo:
 * ela é recriada sempre que o chunk carrega, então nunca duplica.
 */
public final class NpcManager {

    /** Skins que já vêm no jogo (funcionam sem internet). */
    public static final Set<String> SKINS_PADRAO = Set.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");

    private final NpcVivosPlugin plugin;
    private final File pasta;
    private final NamespacedKey chaveId;
    private final Map<String, NpcVivo> npcs = new LinkedHashMap<>();
    private final Map<UUID, NpcVivo> porEntidade = new HashMap<>();
    private BukkitTask tarefaOlhar;

    public NpcManager(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "npcs");
        this.chaveId = new NamespacedKey(plugin, "npc");
    }

    // ------------------------------------------------------------------ ARQUIVOS

    public void carregar() {
        removerTodasEntidades();
        npcs.clear();
        if (!pasta.exists() && !pasta.mkdirs()) {
            plugin.getLogger().warning("Não foi possível criar a pasta npcs/.");
        }
        File[] arquivos = pasta.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (arquivos != null) {
            for (File f : arquivos) {
                try {
                    NpcVivo npc = ler(YamlConfiguration.loadConfiguration(f), f.getName().substring(0, f.getName().length() - 4));
                    if (npc != null) {
                        npcs.put(npc.id(), npc);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "NPC inválido em npcs/" + f.getName(), e);
                }
            }
        }
        plugin.getLogger().info(npcs.size() + " NPC(s) carregado(s).");
    }

    private NpcVivo ler(YamlConfiguration y, String idArquivo) {
        String id = y.getString("id", idArquivo).toLowerCase(Locale.ROOT);
        NpcVivo npc = new NpcVivo(id);
        npc.setNome(y.getString("nome", id));
        npc.setSkin(y.getString("skin", "steve"));
        npc.setLocal(y.getString("local.mundo", "world"), y.getDouble("local.x"), y.getDouble("local.y"), y.getDouble("local.z"),
                (float) y.getDouble("local.yaw", 0));
        npc.setFaccao(y.getString("faccao"));
        Material mao = Material.matchMaterial(y.getString("equipamento.mao", ""));
        npc.setItemNaMao(mao != null && mao.isItem() ? mao : null);
        for (String p : y.getStringList("papeis")) {
            Papel papel = Papel.buscar(p);
            if (papel != null) {
                npc.papeis().add(papel);
            } else {
                plugin.getLogger().warning("NPC '" + id + "': papel desconhecido '" + p + "'.");
            }
        }
        npc.setArvore(y.getString("dialogo.arvore"));
        npc.setCombate(y.getConfigurationSection("combate"));
        npc.setRotina(Rotina.ler(y.getConfigurationSection("rotina")));
        npc.saudacao().addAll(y.getStringList("dialogo.saudacao"));
        npc.retorno().addAll(y.getStringList("dialogo.retorno"));
        npc.falas().addAll(y.getStringList("dialogo.falas"));
        lerItens(y.getMapList("loja.compra"), "compra", npc.compra(), id);
        lerItens(y.getMapList("loja.venda"), "venda", npc.venda(), id);
        return npc;
    }

    private void lerItens(List<Map<?, ?>> lista, String tipo, List<ItemLoja> destino, String npc) {
        int i = 0;
        for (Map<?, ?> m : lista) {
            String chave = tipo + "-" + i++;
            ItemStack item = null;
            Object completo = m.get("item_completo");
            if (completo instanceof String b64 && !b64.isBlank()) {
                try {
                    item = ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("NPC '" + npc + "': item_completo inválido em " + chave + ".");
                }
            }
            if (item == null) {
                Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
                if (mat == null || !mat.isItem() || mat.isAir()) {
                    plugin.getLogger().warning("NPC '" + npc + "': item inválido '" + m.get("item") + "' em " + chave + ".");
                    continue;
                }
                item = new ItemStack(mat);
            }
            Object importado = m.get("importado_de");
            destino.add(new ItemLoja(chave, item, Math.max(1, num(m.get("quantidade"), 1).intValue()),
                    Math.max(0, num(m.get("preco_base"), 0).doubleValue()), Math.max(0, num(m.get("estoque_max"), 0).intValue()),
                    Math.max(0, num(m.get("repoe_por_hora"), 0).doubleValue()), Math.max(0, num(m.get("limite_diario"), 0).intValue()),
                    importado == null || String.valueOf(importado).isBlank() ? null : String.valueOf(importado).toLowerCase(Locale.ROOT)));
        }
    }

    private static Number num(Object o, Number padrao) {
        if (o instanceof Number n) {
            return n;
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.replace(',', '.'));
            } catch (NumberFormatException e) {
                return padrao;
            }
        }
        return padrao;
    }

    public void salvar(NpcVivo npc) {
        if (npc.temporario()) {
            return; // soldados de invasão vivem só na memória
        }
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "NPC do plugin NpcVivos. Depois de editar, use /npcv reload.",
                "skin: steve, alex, ari, efe, kai, makena, noor, sunny, zuri (+ \"-fino\" para braço fino)",
                "      ou jogador:Nick para usar a skin de um jogador (precisa de internet)",
                "papeis: COMERCIANTE, FERREIRO, GUIA, MISSIONARIO, GUARDA, MERCENARIO, DUELISTA, BOSS",
                "combate: vida, dano, armadura, velocidade, alcance, arqueiro e respawn-segundos só deste NPC",
                "         (o que não estiver aqui vem do atributos: do combate.yml)",
                "faccao: id de um arquivo em faccoes/ (guardas só protegem a própria facção). Vazio = sem facção.",
                "dialogo.arvore: nome de um arquivo em dialogos/ (conversa com opções). Sem ele, a conversa é simples.",
                "rotina: tipo PARADO, PASSEAR (anda até 'raio' blocos de casa) ou PATRULHA (segue os 'pontos' \"x y z\"),",
                "        falas = o que ele diz sozinho num balão, dormir = à noite volta para casa",
                "loja.compra = o que o JOGADOR COMPRA do NPC | loja.venda = o que o JOGADOR VENDE ao NPC",
                "preco_base = preço do lote inteiro (quantidade). estoque_max 0 = infinito. limite_diario 0 = sem limite."));
        y.set("id", npc.id());
        y.set("nome", npc.nome());
        y.set("skin", npc.skin());
        y.set("local.mundo", npc.mundo());
        y.set("local.x", npc.x());
        y.set("local.y", npc.y());
        y.set("local.z", npc.z());
        y.set("local.yaw", (double) npc.yaw());
        y.set("faccao", npc.faccao());
        y.set("papeis", npc.papeis().stream().map(Enum::name).toList());
        if (npc.itemNaMao() != null) {
            y.set("equipamento.mao", npc.itemNaMao().name());
        }
        y.set("dialogo.arvore", npc.arvore());
        if (npc.combate() != null) {
            for (String k : npc.combate().getKeys(true)) {
                if (!npc.combate().isConfigurationSection(k)) {
                    y.set("combate." + k, npc.combate().get(k));
                }
            }
        }
        if (npc.rotina() != null) {
            npc.rotina().gravar(y.createSection("rotina"));
        }
        y.set("dialogo.saudacao", npc.saudacao());
        y.set("dialogo.retorno", npc.retorno());
        y.set("dialogo.falas", npc.falas());
        y.set("loja.compra", escreverItens(npc.compra(), true));
        y.set("loja.venda", escreverItens(npc.venda(), false));
        try {
            y.save(new File(pasta, npc.id() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar o NPC " + npc.id(), e);
        }
    }

    private static List<Map<String, Object>> escreverItens(List<ItemLoja> itens, boolean compra) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (ItemLoja i : itens) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (i.simples()) {
                m.put("item", i.material().name());
            } else {
                m.put("item", i.material().name());
                m.put("item_completo", Base64.getEncoder().encodeToString(i.item().serializeAsBytes()));
            }
            m.put("quantidade", i.quantidade());
            m.put("preco_base", i.precoBase());
            if (compra) {
                m.put("estoque_max", i.estoqueMax());
                m.put("repoe_por_hora", i.repoePorHora());
                if (i.importadoDe() != null) {
                    m.put("importado_de", i.importadoDe());
                }
            } else {
                m.put("limite_diario", i.limiteDiario());
            }
            l.add(m);
        }
        return l;
    }

    // ------------------------------------------------------------------ CONSULTAS

    public Collection<NpcVivo> todos() {
        return npcs.values();
    }

    public NpcVivo obter(String id) {
        return id == null ? null : npcs.get(id.toLowerCase(Locale.ROOT));
    }

    public NpcVivo porEntidade(Entity e) {
        NpcVivo npc = porEntidade.get(e.getUniqueId());
        if (npc != null) {
            return npc;
        }
        String id = e.getPersistentDataContainer().get(chaveId, PersistentDataType.STRING);
        return id == null ? null : npcs.get(id);
    }

    public boolean ehEntidadeNpc(Entity e) {
        return e.getPersistentDataContainer().has(chaveId, PersistentDataType.STRING);
    }

    public NpcVivo criar(String id, Papel papel, String nome, Location l) {
        NpcVivo npc = new NpcVivo(id.toLowerCase(Locale.ROOT));
        npc.setNome(nome);
        npc.setSkin(switch (papel) {
            case COMERCIANTE -> "kai";
            case FERREIRO -> "steve";
            case GUIA -> "sunny";
            default -> "noor";
        });
        npc.setLocal(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw());
        npc.papeis().add(papel);
        npc.saudacao().add("Olá, {jogador}! Muito prazer.");
        npc.retorno().add("Olá de novo, {jogador}!");
        npcs.put(npc.id(), npc);
        salvar(npc);
        spawnar(npc);
        return npc;
    }

    /** NPC que existe só enquanto algo acontece (soldado de invasão): entra no mapa sem arquivo. */
    public void registrarTemporario(NpcVivo npc) {
        npc.setTemporario(true);
        npcs.put(npc.id(), npc);
        spawnar(npc);
    }

    public void removerTemporario(NpcVivo npc) {
        despawnar(npc);
        npcs.remove(npc.id());
        plugin.repositorio().esquecerNpc(npc.id());
    }

    public void remover(NpcVivo npc) {
        despawnar(npc);
        npcs.remove(npc.id());
        File f = new File(pasta, npc.id() + ".yml");
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Não foi possível apagar npcs/" + npc.id() + ".yml");
        }
        plugin.repositorio().esquecerNpc(npc.id());
    }

    // ------------------------------------------------------------------ ENTIDADES

    public void spawnarTodos() {
        for (NpcVivo npc : npcs.values()) {
            Location l = npc.local();
            if (l != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
                spawnar(npc);
            }
        }
    }

    /** Cria (ou recria) o manequim do NPC, se o chunk estiver carregado. */
    public void spawnar(NpcVivo npc) {
        despawnar(npc);
        Location l = npc.local();
        if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            return;
        }
        Mannequin m = l.getWorld().spawn(l, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setImmovable(true);
            // sem setInvulnerable: assim o golpe chega no plugin (perde reputação) e o NpcListener cancela todo dano
            e.setSilent(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Texto.cor(npc.nome()));
            e.setCustomNameVisible(true);
            e.setDescription(Texto.cor(descricao(npc)));
            e.setProfile(perfil(npc.skin()));
            e.getPersistentDataContainer().set(chaveId, PersistentDataType.STRING, npc.id());
            if (npc.itemNaMao() != null) {
                e.getEquipment().setItemInMainHand(new ItemStack(npc.itemNaMao()));
            }
        });
        npc.setEntidade(m.getUniqueId());
        porEntidade.put(m.getUniqueId(), npc);
    }

    public void despawnar(NpcVivo npc) {
        if (npc.entidade() != null) {
            Entity e = Bukkit.getEntity(npc.entidade());
            if (e != null) {
                e.remove();
            }
            porEntidade.remove(npc.entidade());
            npc.setEntidade(null);
        }
    }

    public void removerTodasEntidades() {
        for (NpcVivo npc : npcs.values()) {
            despawnar(npc);
        }
        porEntidade.clear();
        // Sobras (por exemplo, se o servidor caiu): qualquer manequim com a nossa marca
        for (World w : Bukkit.getWorlds()) {
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (ehEntidadeNpc(m)) {
                    m.remove();
                }
            }
        }
    }

    /** Chunk carregou: recria os NPCs dele e apaga manequins nossos que sobraram. */
    public void aoCarregarChunk(World w, int cx, int cz) {
        for (NpcVivo npc : npcs.values()) {
            if (npc.noChunk(w.getName(), cx, cz)) {
                Entity atual = npc.entidade() == null ? null : Bukkit.getEntity(npc.entidade());
                if (atual == null || !atual.isValid()) {
                    spawnar(npc);
                }
            }
        }
    }

    public void entidadeCarregada(Entity e) {
        if (e instanceof Mannequin && ehEntidadeNpc(e) && !porEntidade.containsKey(e.getUniqueId())) {
            e.remove(); // cópia antiga que ficou salva
        }
    }

    private String descricao(NpcVivo npc) {
        List<String> nomes = new ArrayList<>();
        for (Papel p : npc.papeis()) {
            nomes.add(p.nome());
        }
        String texto = "<gray>" + (nomes.isEmpty() ? "NPC" : String.join(" • ", nomes));
        var faccao = plugin.faccoes() == null ? null : plugin.faccoes().daNpc(npc);
        return faccao == null ? texto : texto + " <dark_gray>| <reset>" + faccao.nome();
    }

    /** Skin do NPC: as padrão do jogo ("zuri", "alex-fino") ou "jogador:Nick". */
    public static ResolvableProfile perfil(String skin) {
        String s = skin == null ? "steve" : skin.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("jogador:")) {
            return ResolvableProfile.resolvableProfile().name(s.substring(8)).build();
        }
        boolean fino = s.toLowerCase(Locale.ROOT).endsWith("-fino");
        String base = (fino ? s.substring(0, s.length() - 5) : s).toLowerCase(Locale.ROOT);
        if (!SKINS_PADRAO.contains(base)) {
            base = "steve";
        }
        String nome = base;
        return ResolvableProfile.resolvableProfile().skinPatch(p -> p
                .body(Key.key("minecraft", "entity/player/" + (fino ? "slim/" : "wide/") + nome))
                .model(fino ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC)).build();
    }

    // ------------------------------------------------------------------ OLHAR PARA OS JOGADORES

    public void iniciarTarefas() {
        if (tarefaOlhar != null) {
            tarefaOlhar.cancel();
        }
        tarefaOlhar = Bukkit.getScheduler().runTaskTimer(plugin, this::olhar, 20L, 4L);
    }

    public void pararTarefas() {
        if (tarefaOlhar != null) {
            tarefaOlhar.cancel();
            tarefaOlhar = null;
        }
    }

    private void olhar() {
        double raio = plugin.getConfig().getDouble("npc.olhar-distancia", 8);
        for (NpcVivo npc : npcs.values()) {
            if (npc.entidade() == null || (plugin.vida() != null && plugin.vida().andando(npc))) {
                continue; // quem está passeando olha para onde anda
            }
            Entity e = Bukkit.getEntity(npc.entidade());
            if (!(e instanceof Mannequin m) || !m.isValid()) {
                continue;
            }
            Player perto = null;
            double melhor = raio * raio;
            for (Player p : m.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                double d = p.getLocation().distanceSquared(m.getLocation());
                if (d < melhor) {
                    melhor = d;
                    perto = p;
                }
            }
            if (perto == null) {
                if (Math.abs(m.getLocation().getYaw() - npc.yaw()) > 1 || Math.abs(m.getLocation().getPitch()) > 1) {
                    m.setRotation(npc.yaw(), 0);
                }
                continue;
            }
            Location olho = m.getEyeLocation();
            Vector dir = perto.getEyeLocation().toVector().subtract(olho.toVector());
            if (dir.lengthSquared() < 0.01) {
                continue;
            }
            Location alvo = olho.clone().setDirection(dir);
            m.setRotation(alvo.getYaw(), alvo.getPitch());
        }
    }

    /** Acha um lugar seguro (chão firme e 2 blocos de ar) perto de um ponto. */
    public static Location lugarSeguro(Location base, int raio) {
        World w = base.getWorld();
        for (int r = 0; r <= raio; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 2; dy >= -3; dy--) {
                        int x = base.getBlockX() + dx;
                        int y = base.getBlockY() + dy;
                        int z = base.getBlockZ() + dz;
                        if (w.getBlockAt(x, y - 1, z).isSolid() && w.getBlockAt(x, y, z).isPassable()
                                && w.getBlockAt(x, y + 1, z).isPassable() && !w.getBlockAt(x, y, z).isLiquid()) {
                            return new Location(w, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }
        return base.clone();
    }
}
