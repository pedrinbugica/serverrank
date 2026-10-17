package dev.pedrobugica.survivalrpg.mob;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.mundo.Regiao;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mobs com nível: ao nascer, cada monstro ganha o nível da região (mais longe do spawn = mais forte),
 * vida e dano proporcionais, e às vezes vira Elite ou Campeão com poderes (afixos). O nome mostra tudo:
 * [Nv 23] Zumbi Elite ❤ 180. Nas zonas seguras (cidades) monstros não nascem.
 */
public final class MobService implements Listener {

    public enum Tier {
        NORMAL, ELITE, CAMPEAO
    }

    private final SurvivalRpgPlugin plugin;
    private final NamespacedKey chaveNivel;
    private final NamespacedKey chaveTier;
    private final NamespacedKey chaveAfixos;
    private final NamespacedKey chaveSpawner;
    private final NamespacedKey chaveInvocou;
    private final NamespacedKey chaveNomeFixo;

    public MobService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.chaveNivel = new NamespacedKey(plugin, "nivel");
        this.chaveTier = new NamespacedKey(plugin, "tier");
        this.chaveAfixos = new NamespacedKey(plugin, "afixos");
        this.chaveSpawner = new NamespacedKey(plugin, "spawner");
        this.chaveInvocou = new NamespacedKey(plugin, "invocou");
        this.chaveNomeFixo = new NamespacedKey(plugin, "nome-fixo");
    }

    private double cfg(String k, double padrao) {
        return plugin.getConfig().getDouble("mobs." + k, padrao);
    }

    /** Mob que entra no RPG: hostil, não é chefe do jogo (Wither/Dragão ficam como são). */
    public static boolean ehMobRpg(Entity e) {
        return e instanceof LivingEntity && e instanceof Enemy && !(e instanceof Wither) && !(e instanceof EnderDragon);
    }

    // ------------------------------------------------------------------ DADOS DO MOB

    public int nivel(Entity e) {
        return e.getPersistentDataContainer().getOrDefault(chaveNivel, PersistentDataType.INTEGER, 0);
    }

    public Tier tier(Entity e) {
        String t = e.getPersistentDataContainer().get(chaveTier, PersistentDataType.STRING);
        try {
            return t == null ? Tier.NORMAL : Tier.valueOf(t);
        } catch (IllegalArgumentException ex) {
            return Tier.NORMAL;
        }
    }

    public Set<Afixo> afixos(Entity e) {
        Set<Afixo> r = EnumSet.noneOf(Afixo.class);
        String s = e.getPersistentDataContainer().get(chaveAfixos, PersistentDataType.STRING);
        if (s != null) {
            for (String a : s.split(",")) {
                Afixo f = Afixo.buscar(a);
                if (f != null) {
                    r.add(f);
                }
            }
        }
        return r;
    }

    public boolean deSpawner(Entity e) {
        return e.getPersistentDataContainer().has(chaveSpawner);
    }

    /** Multiplicador do dano que este mob causa (nível e categoria). */
    public double multiplicadorDano(Entity e) {
        int n = nivel(e);
        if (n <= 0) {
            return 1;
        }
        double m = 1 + cfg("dano-por-nivel", 0.06) * (n - 1);
        return switch (tier(e)) {
            case ELITE -> m * cfg("elite.dano", 1.5);
            case CAMPEAO -> m * cfg("campeao.dano", 2.0);
            default -> m;
        };
    }

    public double multiplicadorXp(Entity e) {
        return switch (tier(e)) {
            case ELITE -> cfg("elite.xp", 4);
            case CAMPEAO -> cfg("campeao.xp", 15);
            default -> 1;
        };
    }

    // ------------------------------------------------------------------ NASCER

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoNascer(CreatureSpawnEvent e) {
        LivingEntity m = e.getEntity();
        if (!ehMobRpg(m) || !plugin.mundoRpg(m.getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason motivo = e.getSpawnReason();
        if (motivo == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return; // outro plugin (ou este, nas masmorras) decide o nível
        }
        // Nas cidades não nascem monstros sozinhos
        if ((motivo == CreatureSpawnEvent.SpawnReason.NATURAL || motivo == CreatureSpawnEvent.SpawnReason.PATROL
                || motivo == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) && plugin.regioes().zona(m.getLocation()) != null) {
            e.setCancelled(true);
            return;
        }
        Regiao r = plugin.regioes().regiao(m.getLocation());
        if (r == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int nivel = r.nivelNaDistancia(plugin.regioes().distanciaDoSpawn(m.getLocation()), rnd.nextDouble());
        Tier tier = Tier.NORMAL;
        if (motivo != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            double s = rnd.nextDouble();
            if (s < cfg("campeao.chance", 0.005)) {
                tier = Tier.CAMPEAO;
            } else if (s < cfg("campeao.chance", 0.005) + cfg("elite.chance", 0.05)) {
                tier = Tier.ELITE;
            }
        }
        aplicar(m, nivel, tier, motivo == CreatureSpawnEvent.SpawnReason.SPAWNER);
    }

    /** Dá nível, categoria e afixos a um mob (usado ao nascer e pelas masmorras). */
    public void aplicar(LivingEntity m, int nivel, Tier tier, boolean spawner) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(chaveNivel, PersistentDataType.INTEGER, Math.max(1, nivel));
        pdc.set(chaveTier, PersistentDataType.STRING, tier.name());
        if (spawner) {
            pdc.set(chaveSpawner, PersistentDataType.BYTE, (byte) 1);
        }
        List<Afixo> escolhidos = new ArrayList<>();
        int quantos = switch (tier) {
            case ELITE -> ThreadLocalRandom.current().nextInt(1, 3);
            case CAMPEAO -> 3;
            default -> 0;
        };
        List<Afixo> todos = new ArrayList<>(List.of(Afixo.values()));
        java.util.Collections.shuffle(todos);
        for (int i = 0; i < quantos && i < todos.size(); i++) {
            escolhidos.add(todos.get(i));
        }
        if (!escolhidos.isEmpty()) {
            pdc.set(chaveAfixos, PersistentDataType.STRING, String.join(",", escolhidos.stream().map(Enum::name).toList()));
        }
        AttributeInstance vida = m.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            double fator = 1 + cfg("vida-por-nivel", 0.12) * (Math.max(1, nivel) - 1);
            fator *= switch (tier) {
                case ELITE -> cfg("elite.vida", 3);
                case CAMPEAO -> cfg("campeao.vida", 8);
                default -> 1;
            };
            vida.setBaseValue(Math.min(2048, vida.getBaseValue() * fator));
            m.setHealth(vida.getValue());
        }
        if (escolhidos.contains(Afixo.RAPIDO)) {
            m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        }
        if (tier == Tier.CAMPEAO) {
            m.setGlowing(true);
        }
        m.setRemoveWhenFarAway(true); // o nome não pode deixar o mob "eterno" (senão acumulam)
        atualizarNome(m, m.getHealth());
    }

    /** Nome sobre o mob: [Nv 23] Zumbi Elite ❤ 180 (Rápido, Vampiro). */
    public void atualizarNome(LivingEntity m, double vidaAtual) {
        int n = nivel(m);
        if (n <= 0 || !plugin.getConfig().getBoolean("mobs.mostrar-nome", true)) {
            return;
        }
        String fixo = m.getPersistentDataContainer().get(chaveNomeFixo, PersistentDataType.STRING);
        if (fixo != null) {
            m.customName(dev.pedrobugica.survivalrpg.util.Texto.cor(fixo + " <dark_gray>[Nv " + n + "] <red>❤ " + (int) Math.ceil(Math.max(0, vidaAtual))));
            m.setCustomNameVisible(true);
            return;
        }
        Tier t = tier(m);
        NamedTextColor cor = switch (t) {
            case ELITE -> NamedTextColor.GOLD;
            case CAMPEAO -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.GRAY;
        };
        Component nome = Component.text("[Nv " + n + "] ", cor)
                .append(Component.translatable(m.getType().translationKey(), t == Tier.NORMAL ? NamedTextColor.WHITE : cor))
                .append(Component.text(t == Tier.ELITE ? " Elite" : t == Tier.CAMPEAO ? " Campeão" : "", cor, TextDecoration.BOLD))
                .append(Component.text(" ❤ " + (int) Math.ceil(Math.max(0, vidaAtual)), NamedTextColor.RED));
        Set<Afixo> af = afixos(m);
        if (!af.isEmpty()) {
            nome = nome.append(Component.text(" (" + String.join(", ", af.stream().map(Afixo::nome).toList()) + ")", NamedTextColor.DARK_GRAY));
        }
        m.customName(nome);
        m.setCustomNameVisible(t != Tier.NORMAL);
    }

    // ------------------------------------------------------------------ VIDA NO NOME E INVOCADOR

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoLevarDano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity m) || nivel(m) <= 0) {
            return;
        }
        double depois = m.getHealth() - e.getFinalDamage();
        atualizarNome(m, depois);
        AttributeInstance max = m.getAttribute(Attribute.MAX_HEALTH);
        if (depois > 0 && max != null && depois <= max.getValue() / 2 && afixos(m).contains(Afixo.INVOCADOR)
                && !m.getPersistentDataContainer().has(chaveInvocou)) {
            m.getPersistentDataContainer().set(chaveInvocou, PersistentDataType.BYTE, (byte) 1);
            Bukkit.getScheduler().runTask(plugin, () -> invocar(m));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCurar(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof LivingEntity m && nivel(m) > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> atualizarNome(m, m.getHealth()));
        }
    }

    private void invocar(LivingEntity m) {
        if (!m.isValid()) {
            return;
        }
        EntityType tipo = m.getType();
        Location l = m.getLocation();
        m.getWorld().spawnParticle(Particle.WITCH, l.clone().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.05);
        for (int i = 0; i < 2; i++) {
            Location onde = l.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
            Entity ajudante = m.getWorld().spawnEntity(onde, tipo, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (ajudante instanceof LivingEntity le) {
                aplicar(le, Math.max(1, nivel(m) - 3), Tier.NORMAL, false);
            }
        }
    }

    /** Nome próprio (chefes): "Ossian, o Rei Esqueleto [Nv 13] ❤ 900". */
    public void fixarNome(LivingEntity m, String nome) {
        m.getPersistentDataContainer().set(chaveNomeFixo, PersistentDataType.STRING, nome);
        atualizarNome(m, m.getHealth());
    }

    public NamespacedKey chaveNivel() {
        return chaveNivel;
    }
}
