package dev.pedrobugica.npcvivos.combate;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.ComportamentoService;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.comportamento.Movimento;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Chefes: NPCs com muita vida, barra no topo da tela e habilidades (investida, puxar o jogador,
 * invocar ajudantes e área de fogo). Tudo sai do combate.yml, então a staff ajusta sem mexer no código.
 */
public final class BossService {

    private final NpcVivosPlugin plugin;
    /** id do NPC -> barra de vida mostrada para quem está perto. */
    private final Map<String, BossBar> barras = new HashMap<>();
    /** id do NPC -> quem está vendo a barra agora. */
    private final Map<String, List<UUID>> vendo = new HashMap<>();
    /** id do NPC -> habilidade -> instante em que ela pode sair de novo. */
    private final Map<String, Map<String, Long>> espera = new HashMap<>();
    /** id do NPC -> ajudantes invocados (para sumirem quando o chefe cai). */
    private final Map<String, List<UUID>> ajudantes = new HashMap<>();
    private final NamespacedKey chaveAjudante;
    private BukkitTask tarefa;

    public BossService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.chaveAjudante = new NamespacedKey(plugin, "ajudante-de-chefe");
    }

    public NamespacedKey chaveAjudante() {
        return chaveAjudante;
    }

    public void iniciar() {
        parar();
        if (!plugin.comportamento().ativo()) {
            return;
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tique, 100L, 10L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (String id : new ArrayList<>(barras.keySet())) {
            esconderBarra(id);
        }
        barras.clear();
        for (String id : new ArrayList<>(ajudantes.keySet())) {
            limparAjudantes(id);
        }
    }

    // ------------------------------------------------------------------ TIQUE (2x por segundo)

    private void tique() {
        double raio = plugin.comportamento().num("bosses.raio-da-barra", 48);
        for (NpcVivo npc : plugin.npcs().todos()) {
            if (!npc.tem(Papel.BOSS)) {
                continue;
            }
            Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
            Mannequin corpo = c == null ? null : plugin.comportamento().corpo(npc);
            if (c == null || c.morto() || corpo == null) {
                esconderBarra(npc.id());
                continue;
            }
            atualizarBarra(npc, c, corpo, raio);
            if (c.estado() == EstadoNpc.COMBATE && c.alvo() != null) {
                Player alvo = Bukkit.getPlayer(c.alvo());
                if (alvo != null && ComportamentoService.atacavel(alvo) && alvo.getWorld().equals(corpo.getWorld())) {
                    talvezHabilidade(npc, c, corpo, alvo);
                }
            }
        }
    }

    // ------------------------------------------------------------------ BARRA DE VIDA

    private void atualizarBarra(NpcVivo npc, Cerebro c, Mannequin corpo, double raio) {
        BossBar barra = barras.computeIfAbsent(npc.id(), id -> BossBar.bossBar(
                Texto.cor(npc.nome()), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
        float fracao = (float) Math.max(0, Math.min(1, c.vida() / c.vidaMaxima()));
        barra.progress(fracao);
        barra.name(Texto.cor(npc.nome() + " <gray>| <red>" + Texto.dinheiro(Math.ceil(c.vida()))
                + "<dark_gray>/<red>" + Texto.dinheiro(c.vidaMaxima())));
        List<UUID> jaVendo = vendo.computeIfAbsent(npc.id(), id -> new ArrayList<>());
        List<UUID> perto = new ArrayList<>();
        for (Player p : corpo.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(corpo.getLocation()) <= raio * raio) {
                perto.add(p.getUniqueId());
                if (!jaVendo.contains(p.getUniqueId())) {
                    p.showBossBar(barra);
                }
            }
        }
        for (UUID u : jaVendo) {
            if (!perto.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.hideBossBar(barra);
                }
            }
        }
        jaVendo.clear();
        jaVendo.addAll(perto);
    }

    private void esconderBarra(String npcId) {
        BossBar barra = barras.get(npcId);
        List<UUID> jaVendo = vendo.remove(npcId);
        if (barra == null || jaVendo == null) {
            return;
        }
        for (UUID u : jaVendo) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.hideBossBar(barra);
            }
        }
    }

    // ------------------------------------------------------------------ HABILIDADES

    private void talvezHabilidade(NpcVivo npc, Cerebro c, Mannequin corpo, Player alvo) {
        ConfigurationSection todas = secaoHabilidades(npc);
        if (todas == null) {
            return;
        }
        double intervalo = plugin.comportamento().num("bosses.intervalo-habilidade-segundos", 8);
        Map<String, Long> minhas = espera.computeIfAbsent(npc.id(), id -> new HashMap<>());
        long agora = System.currentTimeMillis();
        if (minhas.getOrDefault("qualquer", 0L) > agora) {
            return;
        }
        for (String nome : todas.getKeys(false)) {
            ConfigurationSection h = todas.getConfigurationSection(nome);
            if (h == null || minhas.getOrDefault(nome, 0L) > agora) {
                continue;
            }
            if (c.vida() > c.vidaMaxima() * h.getDouble("vida-maxima-para-usar", 1.0)) {
                continue; // habilidade só entra quando o chefe está mais machucado
            }
            if (!CombateService.sorte(h.getDouble("chance", 25))) {
                continue;
            }
            if (!usar(nome, npc, c, corpo, alvo, h)) {
                continue;
            }
            minhas.put(nome, agora + (long) (h.getDouble("espera-segundos", intervalo * 2) * 1000));
            minhas.put("qualquer", agora + (long) (intervalo * 1000));
            return;
        }
    }

    /** O bloco "habilidades:" do npcs/<id>.yml ganha do bloco do combate.yml. */
    private ConfigurationSection secaoHabilidades(NpcVivo npc) {
        ConfigurationSection propria = npc.combate() == null ? null : npc.combate().getConfigurationSection("habilidades");
        return propria != null ? propria : plugin.comportamento().cfg().getConfigurationSection("bosses.habilidades");
    }

    /** Devolve true se a habilidade realmente saiu. */
    private boolean usar(String nome, NpcVivo npc, Cerebro c, Mannequin corpo, Player alvo, ConfigurationSection h) {
        double dist = corpo.getLocation().distance(alvo.getLocation());
        switch (nome.toLowerCase(Locale.ROOT)) {
            case "investida" -> {
                if (dist < 4 || dist > h.getDouble("alcance", 20)) {
                    return false;
                }
                investida(npc, corpo, alvo, h);
            }
            case "puxar" -> {
                if (dist < 5 || dist > h.getDouble("alcance", 24)) {
                    return false;
                }
                puxar(corpo, alvo, h);
            }
            case "invocar" -> {
                if (!invocar(npc, corpo, alvo, h)) {
                    return false;
                }
            }
            case "area-de-fogo" -> {
                if (dist > h.getDouble("raio", 5) + 3) {
                    return false;
                }
                areaDeFogo(npc, corpo, h);
            }
            default -> {
                return false;
            }
        }
        avisarPerto(npc, corpo, nome);
        return true;
    }

    /** O chefe corre até o alvo em teleportes curtos e dá um golpe forte ao chegar. */
    private void investida(NpcVivo npc, Mannequin corpo, Player alvo, ConfigurationSection h) {
        double dano = h.getDouble("dano", 6);
        int passos = h.getInt("passos", 14);
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int feitos;

            @Override
            public void run() {
                Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
                Mannequin agora = plugin.comportamento().corpo(npc);
                if (c == null || c.morto() || agora == null || !ComportamentoService.atacavel(alvo)
                        || !alvo.getWorld().equals(agora.getWorld()) || feitos++ >= passos) {
                    cancel();
                    return;
                }
                agora.getWorld().spawnParticle(Particle.CRIT, agora.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                double d = agora.getLocation().distance(alvo.getLocation());
                if (d <= c.atributos().alcance() + 0.5) {
                    plugin.comportamento().golpear(npc, agora, alvo, dano);
                    cancel();
                    return;
                }
                Movimento.olhar(agora, alvo.getEyeLocation());
                Movimento.passo(agora, alvo.getLocation(), 0.9, plugin.comportamento().inteiro("comportamento.queda-maxima", 3));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Puxa o jogador para perto do chefe (ele não escapa só correndo). */
    private void puxar(Mannequin corpo, Player alvo, ConfigurationSection h) {
        Vector dir = corpo.getLocation().toVector().subtract(alvo.getLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        alvo.setVelocity(dir.normalize().multiply(h.getDouble("forca", 1.3)).setY(h.getDouble("altura", 0.45)));
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        corpo.getWorld().spawnParticle(Particle.PORTAL, alvo.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
    }

    /** Invoca bichos ajudantes marcados no PDC, que somem quando o chefe cai ou o tempo acaba. */
    private boolean invocar(NpcVivo npc, Mannequin corpo, Player alvo, ConfigurationSection h) {
        List<UUID> meus = ajudantes.computeIfAbsent(npc.id(), id -> new ArrayList<>());
        meus.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || !e.isValid();
        });
        int limite = h.getInt("limite-vivos", 4);
        int quantidade = Math.min(h.getInt("quantidade", 2), limite - meus.size());
        if (quantidade <= 0) {
            return false;
        }
        EntityType tipo;
        try {
            tipo = EntityType.valueOf(h.getString("tipo", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("combate.yml: tipo de ajudante inválido '" + h.getString("tipo") + "'.");
            return false;
        }
        int segundos = h.getInt("segundos", 60);
        for (int i = 0; i < quantidade; i++) {
            Location onde = Movimento.aoLado(corpo.getLocation(), 2.5, i, quantidade);
            Entity bicho;
            try {
                bicho = corpo.getWorld().spawnEntity(onde, tipo);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("combate.yml: " + tipo + " não pode ser invocado aqui.");
                return false;
            }
            bicho.getPersistentDataContainer().set(chaveAjudante, PersistentDataType.STRING, npc.id());
            if (bicho instanceof Mob m) {
                m.setTarget(alvo);
                m.setRemoveWhenFarAway(true);
            }
            bicho.getWorld().spawnParticle(Particle.SOUL, onde.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            meus.add(bicho.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (bicho.isValid()) {
                    bicho.getWorld().spawnParticle(Particle.CLOUD, bicho.getLocation(), 10, 0.3, 0.3, 0.3, 0.02);
                    bicho.remove();
                }
            }, segundos * 20L);
        }
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
        return true;
    }

    /** Acende o chão em volta do chefe por alguns segundos: quem ficar perto se queima. */
    private void areaDeFogo(NpcVivo npc, Mannequin corpo, ConfigurationSection h) {
        double raio = h.getDouble("raio", 5);
        double dano = h.getDouble("dano", 4);
        int segundos = h.getInt("segundos", 4);
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int tiques;

            @Override
            public void run() {
                Mannequin agora = plugin.comportamento().corpo(npc);
                Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
                if (agora == null || c == null || c.morto() || tiques++ >= segundos * 4) {
                    cancel();
                    return;
                }
                Location centro = agora.getLocation();
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    centro.getWorld().spawnParticle(Particle.FLAME,
                            centro.clone().add(Math.cos(a) * raio, 0.4, Math.sin(a) * raio), 2, 0.1, 0.1, 0.1, 0.01);
                }
                if (tiques % 4 != 0) {
                    return; // o dano é 1x por segundo; as chamas aparecem 4x
                }
                for (Player p : centro.getWorld().getPlayers()) {
                    if (ComportamentoService.atacavel(p) && p.getLocation().distanceSquared(centro) <= raio * raio) {
                        p.damage(dano, agora);
                        p.setFireTicks(Math.max(p.getFireTicks(), 40));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void avisarPerto(NpcVivo npc, Mannequin corpo, String habilidade) {
        String chave = "boss-habilidade-" + habilidade.toLowerCase(Locale.ROOT);
        double raio = plugin.comportamento().num("bosses.raio-da-barra", 48);
        for (Player p : corpo.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(corpo.getLocation()) <= raio * raio) {
                plugin.mensagens().enviar(p, chave, "npc", npc.nome());
            }
        }
    }

    // ------------------------------------------------------------------ MORTE E VOLTA

    public void aoMorrer(NpcVivo npc, Cerebro c, Player autor) {
        esconderBarra(npc.id());
        limparAjudantes(npc.id());
        espera.remove(npc.id());
        if (!plugin.comportamento().cfg().getBoolean("bosses.anunciar-no-servidor", true)) {
            return;
        }
        String quem = autor == null ? plugin.mensagens().bruta("boss-sem-dono") : autor.getName();
        int minutos = Math.max(1, c.atributos().respawnSegundos() / 60);
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviarSemPrefixo(p, "boss-morreu", "npc", npc.nome(), "jogador", quem,
                    "minutos", String.valueOf(minutos));
        }
        if (autor != null) {
            autor.playSound(autor.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        }
    }

    public void aoReaparecer(NpcVivo npc) {
        if (!plugin.comportamento().cfg().getBoolean("bosses.anunciar-no-servidor", true)) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviarSemPrefixo(p, "boss-voltou", "npc", npc.nome(), "mundo", npc.mundo());
        }
    }

    private void limparAjudantes(String npcId) {
        List<UUID> meus = ajudantes.remove(npcId);
        if (meus == null) {
            return;
        }
        for (UUID u : meus) {
            Entity e = Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
    }

    /** O bicho é ajudante de chefe? (não dá recompensa de missão nem conta como mob normal). */
    public boolean ehAjudante(Entity e) {
        return e.getPersistentDataContainer().has(chaveAjudante, PersistentDataType.STRING);
    }

    public void esquecer(String npcId) {
        esconderBarra(npcId);
        limparAjudantes(npcId);
        barras.remove(npcId);
        espera.remove(npcId);
    }
}
