package dev.pedrobugica.survivalrpg.combate;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.item.ItemRpgService.Especial;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.mob.Afixo;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Todo o cálculo de dano do RPG num lugar só: atributos, arma da classe, crítico, esquiva, nível dos mobs,
 * afixos, fogo amigo do grupo, proteção de nível no PvP e números de dano. Na morte do mob: XP e loot.
 */
public final class CombateListener implements Listener {

    private final SurvivalRpgPlugin plugin;
    /** Ligado enquanto uma habilidade aplica dano (ela já fez a conta dela). */
    private boolean danoDeHabilidade;
    /** Próximo golpe vira crítico garantido (Passo Sombrio do Ladino). */
    private final Set<UUID> proximoCritico = new HashSet<>();

    public CombateListener(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    /** Aplica dano de habilidade (sem passar pelos multiplicadores de novo). */
    public void danoMagico(Player autor, LivingEntity alvo, double dano) {
        danoDeHabilidade = true;
        try {
            alvo.damage(dano, autor);
        } finally {
            danoDeHabilidade = false;
        }
        mostrarNumero(autor, alvo, dano, false, true);
    }

    public void garantirCritico(Player p) {
        proximoCritico.add(p.getUniqueId());
    }

    private static Player jogadorAtacante(Entity d) {
        if (d instanceof Player p) {
            return p;
        }
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private static LivingEntity mobAtacante(Entity d) {
        if (d instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le && !(le instanceof Player)) {
            return le;
        }
        if (d instanceof LivingEntity le && !(le instanceof Player)) {
            return le;
        }
        return null;
    }

    // ------------------------------------------------------------------ DANO

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoDano(EntityDamageByEntityEvent e) {
        if (!plugin.mundoRpg(e.getEntity().getWorld())) {
            return;
        }
        Entity vitima = e.getEntity();
        Player atacante = jogadorAtacante(e.getDamager());
        // Grupo: sem fogo amigo; PvP: proteção de nível
        if (atacante != null && vitima instanceof Player alvo && !atacante.equals(alvo)) {
            if (plugin.grupos().mesmoGrupo(atacante, alvo)) {
                e.setCancelled(true);
                return;
            }
            // Cidades nunca têm PvP; fora delas, só nas regiões marcadas com pvp (se a config pedir)
            var regiao = plugin.regioes().regiao(alvo.getLocation());
            if (plugin.regioes().zona(alvo.getLocation()) != null
                    || (plugin.getConfig().getBoolean("pvp.somente-em-regioes-pvp", false) && (regiao == null || !regiao.pvp()))) {
                e.setCancelled(true);
                atacante.sendActionBar(Texto.cor(plugin.mensagens().bruta("pvp-desligado-aqui")));
                plugin.hud().pausar(atacante, 1200);
                return;
            }
            int diferenca = plugin.perfis().perfil(atacante).nivel() - plugin.perfis().perfil(alvo).nivel();
            if (diferenca > plugin.getConfig().getInt("pvp.diferenca-maxima-de-nivel", 10)) {
                e.setCancelled(true);
                atacante.sendActionBar(Texto.cor(plugin.mensagens().bruta("pvp-nivel-baixo")));
                return;
            }
        }
        if (danoDeHabilidade) {
            return;
        }
        // Jogador batendo
        if (atacante != null && vitima instanceof LivingEntity alvo) {
            boolean distancia = e.getDamager() instanceof Projectile;
            double mult = multiplicadorDoJogador(atacante, distancia);
            PerfilRpg perfil = plugin.perfis().perfil(atacante);
            List<Especial> especiais = plugin.itens().especiais(atacante);
            double chanceCritico = 0.05 + 0.002 * (plugin.perfis().atributoTotal(atacante, Atributo.DESTREZA)
                    + plugin.perfis().atributoTotal(atacante, Atributo.AGILIDADE))
                    + (especiais.contains(Especial.GOLPE_CERTEIRO) ? 0.08 : 0)
                    + (perfil.classe() == Classe.LADINO ? 0.05 : 0);
            boolean critico = proximoCritico.remove(atacante.getUniqueId()) || ThreadLocalRandom.current().nextDouble() < Math.min(0.6, chanceCritico);
            if (critico) {
                mult *= perfil.classe() == Classe.LADINO ? 1.8 : 1.5;
                alvo.getWorld().spawnParticle(Particle.CRIT, alvo.getLocation().add(0, 1.2, 0), 12, 0.3, 0.4, 0.3, 0.1);
            }
            if (alvo instanceof LivingEntity && plugin.mobs().afixos(alvo).contains(Afixo.BLINDADO) && distancia) {
                mult *= 0.4;
            }
            e.setDamage(e.getDamage() * mult);
            double fim = e.getFinalDamage();
            if (especiais.contains(Especial.ROUBO_DE_VIDA)) {
                curar(atacante, fim * 0.06);
            }
            if (especiais.contains(Especial.SANGRAMENTO) && ThreadLocalRandom.current().nextDouble() < 0.12) {
                sangrar(atacante, alvo);
            }
            mostrarNumero(atacante, alvo, fim, critico, false);
            return;
        }
        // Mob batendo no jogador
        LivingEntity mob = mobAtacante(e.getDamager());
        if (e.getDamager() instanceof Creeper c) {
            mob = c;
        }
        if (mob != null && vitima instanceof Player alvo && plugin.mobs().nivel(mob) > 0) {
            // Esquiva (Agilidade): até 25%
            double esquiva = Math.min(0.25, 0.002 * plugin.perfis().atributoTotal(alvo, Atributo.AGILIDADE));
            if (ThreadLocalRandom.current().nextDouble() < esquiva) {
                e.setCancelled(true);
                alvo.sendActionBar(Texto.cor(plugin.mensagens().bruta("esquivou")));
                plugin.hud().pausar(alvo, 1200);
                alvo.playSound(alvo.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.8f, 1.4f);
                return;
            }
            e.setDamage(e.getDamage() * plugin.mobs().multiplicadorDano(mob));
            var afixos = plugin.mobs().afixos(mob);
            if (afixos.contains(Afixo.VAMPIRO)) {
                curar(mob, e.getFinalDamage() * 0.3);
            }
            if (afixos.contains(Afixo.CONGELANTE)) {
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
            }
        }
    }

    /** Força (corpo a corpo) ou Destreza (distância), mais o bônus da arma da classe. */
    public double multiplicadorDoJogador(Player p, boolean distancia) {
        PerfilRpg r = plugin.perfis().perfil(p);
        double m = 1 + 0.01 * plugin.perfis().atributoTotal(p, distancia ? Atributo.DESTREZA : Atributo.FORCA);
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (r.classe() != null && r.classe().ehArma(mao.getType())) {
            m *= 1 + plugin.getConfig().getDouble("classes.bonus-arma-da-classe", 0.15);
        }
        return m;
    }

    private void curar(LivingEntity e, double quanto) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && !e.isDead() && quanto > 0) {
            e.setHealth(Math.min(max.getValue(), e.getHealth() + quanto));
        }
    }

    private void sangrar(Player autor, LivingEntity alvo) {
        double porTique = Math.max(1, plugin.perfis().perfil(autor).nivel() / 10.0);
        for (int i = 1; i <= 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (alvo.isValid() && !alvo.isDead()) {
                    alvo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, alvo.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);
                    danoMagico(autor, alvo, porTique);
                }
            }, 20L * i);
        }
    }

    // ------------------------------------------------------------------ NÚMEROS DE DANO

    private void mostrarNumero(Player autor, LivingEntity alvo, double dano, boolean critico, boolean magia) {
        if (autor == null || dano <= 0 || !plugin.perfis().perfil(autor).numerosDeDano()) {
            return;
        }
        Location l = alvo.getLocation().add(ThreadLocalRandom.current().nextDouble(-0.4, 0.4), alvo.getHeight() + 0.3,
                ThreadLocalRandom.current().nextDouble(-0.4, 0.4));
        String cor = critico ? "<yellow><bold>" : magia ? "<aqua>" : "<white>";
        TextDisplay d = alvo.getWorld().spawn(l, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.text(Texto.cor(cor + Texto.numero(dano) + (critico ? "!" : "")));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setTeleportDuration(10);
        });
        autor.showEntity(plugin, d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.teleport(d.getLocation().add(0, 0.6, 0));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 16L);
    }

    // ------------------------------------------------------------------ MORTE: XP E LOOT

    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrer(EntityDeathEvent e) {
        LivingEntity m = e.getEntity();
        int nivel = plugin.mobs().nivel(m);
        if (nivel <= 0) {
            return;
        }
        if (plugin.mobs().afixos(m).contains(Afixo.EXPLOSIVO)) {
            Location l = m.getLocation();
            l.getWorld().spawnParticle(Particle.SMOKE, l.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
            Bukkit.getScheduler().runTaskLater(plugin, () -> l.getWorld().createExplosion(l, 2.5f, false, false), 20L);
        }
        Player k = m.getKiller();
        if (k == null) {
            return;
        }
        boolean spawner = plugin.mobs().deSpawner(m);
        double base = plugin.getConfig().getDouble("xp.por-mob", 8) * nivel * plugin.mobs().multiplicadorXp(m);
        if (spawner) {
            base *= plugin.getConfig().getDouble("xp.fator-spawner", 0.1);
        }
        List<Player> grupo = plugin.grupos().pertoDe(k, plugin.getConfig().getDouble("grupo.raio-xp", 30));
        double bonusGrupo = 1 + plugin.getConfig().getDouble("grupo.bonus-xp-por-membro", 0.10) * (grupo.size() - 1);
        for (Player p : grupo) {
            int diff = plugin.perfis().perfil(p).nivel() - nivel;
            double fator = diff > 5 ? Math.max(0.1, 1 - 0.15 * (diff - 5)) : diff < 0 ? Math.min(1.5, 1 + 0.05 * -diff) : 1;
            plugin.perfis().darXp(p, base * bonusGrupo * fator / grupo.size(), nomeMob(m));
        }
        if (!spawner) {
            ItemStack item = plugin.itens().loot(nivel, plugin.mobs().tier(m), plugin.perfis().perfil(k).classe());
            if (item != null) {
                e.getDrops().add(item);
                var rar = plugin.itens().raridade(item);
                if (rar != null && rar.ordinal() >= 3) {
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        plugin.mensagens().enviar(o, "loot-raro", "jogador", k.getName(), "item",
                                rar.tag() + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                        .serialize(item.getItemMeta().customName()));
                    }
                }
            }
        }
    }

    private static String nomeMob(LivingEntity m) {
        return "<lang:" + m.getType().translationKey() + ">";
    }

    public void esquecer(UUID u) {
        proximoCritico.remove(u);
    }
}
