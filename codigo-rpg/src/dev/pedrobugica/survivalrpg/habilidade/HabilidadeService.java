package dev.pedrobugica.survivalrpg.habilidade;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Modo habilidade: com a arma da classe na mão, F (trocar de mão) liga o modo; 1, 2 e 3 usam as habilidades
 * (o item na mão não muda); F de novo ou 4-9 sai. Agachado + F troca de mão normalmente.
 * Se a arma estiver nos slots 1-3 ela vai para o slot 9 enquanto o modo estiver ligado e volta ao sair.
 */
public final class HabilidadeService implements Listener {

    private final SurvivalRpgPlugin plugin;
    private final NamespacedKey chaveFlecha;
    /** Slot original da arma, quando ela foi para o slot 9 durante o modo. */
    private final Map<UUID, Integer> slotOriginal = new HashMap<>();
    private final Map<UUID, Long> semQueda = new HashMap<>();
    private final Map<UUID, Long> escudoGelo = new HashMap<>();
    private final Map<UUID, Integer> golpesVenenosos = new HashMap<>();

    public HabilidadeService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.chaveFlecha = new NamespacedKey(plugin, "flecha-habilidade");
    }

    // ------------------------------------------------------------------ MODO HABILIDADE

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoApertarF(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!plugin.mundoRpg(p.getWorld())) {
            return;
        }
        PerfilRpg r = plugin.perfis().perfil(p);
        if (r.modoHabilidade()) {
            e.setCancelled(true);
            sairDoModo(p, r, true);
            return;
        }
        if (r.classe() == null || p.isSneaking() || !r.classe().ehArma(p.getInventory().getItemInMainHand().getType())) {
            return;
        }
        e.setCancelled(true);
        entrarNoModo(p, r);
    }

    private void entrarNoModo(Player p, PerfilRpg r) {
        PlayerInventory inv = p.getInventory();
        int atual = inv.getHeldItemSlot();
        if (atual <= 2) {
            // As teclas 1-3 viram habilidades: a arma vai para o slot 9 (troca com o que estiver lá)
            ItemStack arma = inv.getItem(atual);
            ItemStack nove = inv.getItem(8);
            inv.setItem(8, arma);
            inv.setItem(atual, nove);
            inv.setHeldItemSlot(8);
            slotOriginal.put(p.getUniqueId(), atual);
        }
        r.setModoHabilidade(true);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
        p.sendActionBar(Texto.cor(plugin.mensagens().bruta("modo-habilidade-ligado")));
        plugin.hud().pausar(p, 1500);
    }

    /** Desliga o modo e devolve a arma ao slot de antes (se ela ainda estiver no slot 9). */
    public void sairDoModo(Player p, PerfilRpg r, boolean avisar) {
        if (r == null || !r.modoHabilidade()) {
            return;
        }
        r.setModoHabilidade(false);
        Integer original = slotOriginal.remove(p.getUniqueId());
        if (original != null) {
            PlayerInventory inv = p.getInventory();
            ItemStack nove = inv.getItem(8);
            ItemStack outro = inv.getItem(original);
            inv.setItem(original, nove);
            inv.setItem(8, outro);
            inv.setHeldItemSlot(original);
        }
        if (avisar) {
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 0.8f);
            p.sendActionBar(Texto.cor(plugin.mensagens().bruta("modo-habilidade-desligado")));
            plugin.hud().pausar(p, 1200);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoTrocarSlot(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        PerfilRpg r = plugin.perfis().perfilSeExistir(p.getUniqueId());
        if (r == null || !r.modoHabilidade()) {
            return;
        }
        e.setCancelled(true);
        int n = e.getNewSlot();
        if (n > 2) {
            sairDoModo(p, r, true);
            return;
        }
        List<Habilidade> l = r.classe() == null ? List.of() : Habilidade.daClasse(r.classe());
        if (n < l.size()) {
            usar(p, r, l.get(n));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoClicarInventario(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            PerfilRpg r = plugin.perfis().perfilSeExistir(p.getUniqueId());
            if (r != null && r.modoHabilidade()) {
                e.setCancelled(true);
                sairDoModo(p, r, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoDropar(PlayerDropItemEvent e) {
        PerfilRpg r = plugin.perfis().perfilSeExistir(e.getPlayer().getUniqueId());
        if (r != null && r.modoHabilidade()) {
            e.setCancelled(true);
            sairDoModo(e.getPlayer(), r, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoMorrer(PlayerDeathEvent e) {
        sairDoModo(e.getPlayer(), plugin.perfis().perfilSeExistir(e.getPlayer().getUniqueId()), false);
    }

    @EventHandler
    public void aoMudarDeMundo(PlayerChangedWorldEvent e) {
        sairDoModo(e.getPlayer(), plugin.perfis().perfilSeExistir(e.getPlayer().getUniqueId()), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent e) {
        sairDoModo(e.getPlayer(), plugin.perfis().perfilSeExistir(e.getPlayer().getUniqueId()), false);
        UUID u = e.getPlayer().getUniqueId();
        semQueda.remove(u);
        escudoGelo.remove(u);
        golpesVenenosos.remove(u);
    }

    /** Ao desligar o plugin: devolve as armas de quem estava no modo. */
    public void desligarTodos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sairDoModo(p, plugin.perfis().perfilSeExistir(p.getUniqueId()), false);
        }
    }

    // ------------------------------------------------------------------ USAR

    private void falhar(Player p, String chave, String... pares) {
        p.sendActionBar(Texto.cor(plugin.mensagens().bruta(chave), pares));
        plugin.hud().pausar(p, 1000);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    public void usar(Player p, PerfilRpg r, Habilidade h) {
        if (r.classe() == null || h.classe() != r.classe()) {
            return;
        }
        if (!r.classe().ehArma(p.getInventory().getItemInMainHand().getType())) {
            sairDoModo(p, r, false);
            falhar(p, "habilidade-sem-arma");
            return;
        }
        if (r.nivel() < h.nivelMinimo()) {
            falhar(p, "habilidade-nivel", "habilidade", h.nome(), "nivel", String.valueOf(h.nivelMinimo()));
            return;
        }
        long falta = r.recarga(h.name());
        if (falta > 0) {
            falhar(p, "habilidade-recarga", "habilidade", h.nome(), "segundos", String.valueOf(falta / 1000 + 1));
            return;
        }
        if (r.mana() < h.mana()) {
            falhar(p, "habilidade-sem-mana", "habilidade", h.nome(), "mana", String.valueOf(h.mana()));
            return;
        }
        boolean funcionou = executar(p, r, h);
        if (!funcionou) {
            return;
        }
        r.setMana(r.mana() - h.mana());
        r.usarRecarga(h.name(), h.recarga());
    }

    /** Poder base das habilidades: cresce com o nível e com o atributo que a classe usa. */
    public double poder(Player p, Atributo a) {
        int n = plugin.perfis().perfil(p).nivel();
        return (4 + n * 0.6) * (1 + 0.012 * plugin.perfis().atributoTotal(p, a));
    }

    private boolean executar(Player p, PerfilRpg r, Habilidade h) {
        Location l = p.getLocation();
        switch (h) {
            case GRITO_DE_GUERRA -> {
                for (LivingEntity m : inimigosPerto(l, 8)) {
                    if (m instanceof Mob mob) {
                        mob.setTarget(p);
                    }
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, true));
                p.getWorld().playSound(l, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
                p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, l.clone().add(0, 2, 0), 8, 0.6, 0.3, 0.6, 0);
                anel(l, 4, Particle.CRIT);
            }
            case INVESTIDA -> {
                Vector dir = l.getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) {
                    dir = new Vector(0, 0, 1);
                }
                p.setVelocity(dir.normalize().multiply(1.6).setY(0.35));
                semQueda(p, 3);
                double dano = poder(p, Atributo.FORCA) * 0.9;
                Set<UUID> atingidos = new HashSet<>();
                p.getWorld().playSound(l, Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
                new BukkitRunnable() {
                    int t;

                    @Override
                    public void run() {
                        if (!p.isOnline() || ++t > 12) {
                            cancel();
                            return;
                        }
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
                        for (LivingEntity m : inimigosPerto(p.getLocation(), 2.2)) {
                            if (atingidos.add(m.getUniqueId())) {
                                plugin.combate().danoMagico(p, m, dano);
                                m.setVelocity(m.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0)
                                        .normalize().multiply(0.9).setY(0.4));
                            }
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }
            case FURIA -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, true));
                curar(p, vidaMaxima(p) * 0.3);
                p.getWorld().playSound(l, Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.4f);
                p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, l.clone().add(0, 1.2, 0), 15, 0.5, 0.6, 0.5, 0);
            }
            case TIRO_TRIPLO -> {
                double dano = 2 + r.nivel() * 0.12;
                for (int ang : new int[]{-10, 0, 10}) {
                    Vector v = l.getDirection().clone().rotateAroundY(Math.toRadians(ang)).multiply(2.6);
                    flecha(p, p.getEyeLocation(), v, dano);
                }
                p.getWorld().playSound(l, Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
            }
            case CHUVA_DE_FLECHAS -> {
                RayTraceResult rt = p.rayTraceBlocks(30, FluidCollisionMode.NEVER);
                Location alvo = rt != null ? rt.getHitPosition().toLocation(p.getWorld())
                        : p.getEyeLocation().add(l.getDirection().multiply(20));
                double dano = 2 + r.nivel() * 0.1;
                p.getWorld().playSound(l, Sound.ENTITY_ARROW_SHOOT, 1f, 0.7f);
                new BukkitRunnable() {
                    int t;

                    @Override
                    public void run() {
                        if (!p.isOnline() || ++t > 10) {
                            cancel();
                            return;
                        }
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        for (int i = 0; i < 3; i++) {
                            Location de = alvo.clone().add(rnd.nextDouble(-3, 3), 12, rnd.nextDouble(-3, 3));
                            flecha(p, de, new Vector(0, -1.8, 0), dano);
                        }
                    }
                }.runTaskTimer(plugin, 2L, 2L);
            }
            case SALTO_EVASIVO -> {
                Vector tras = l.getDirection().setY(0);
                if (tras.lengthSquared() < 0.01) {
                    tras = new Vector(0, 0, 1);
                }
                p.setVelocity(tras.normalize().multiply(-1.4).setY(0.55));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, true));
                semQueda(p, 4);
                p.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.02);
                p.getWorld().playSound(l, Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            }
            case BOLA_DE_FOGO -> {
                double dano = poder(p, Atributo.INTELIGENCIA) * 1.2;
                p.getWorld().playSound(l, Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                projetil(p, Particle.FLAME, 1.4, 30, (onde, atingido) -> {
                    onde.getWorld().spawnParticle(Particle.EXPLOSION, onde, 1);
                    onde.getWorld().spawnParticle(Particle.FLAME, onde, 25, 0.8, 0.8, 0.8, 0.05);
                    onde.getWorld().playSound(onde, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                    for (LivingEntity m : inimigosPerto(onde, 3)) {
                        plugin.combate().danoMagico(p, m, dano);
                        m.setFireTicks(Math.max(m.getFireTicks(), 60));
                    }
                });
            }
            case ESCUDO_DE_GELO -> {
                int amp = Math.min(4, 1 + r.nivel() / 15);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, amp, false, true));
                escudoGelo.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
                p.getWorld().playSound(l, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.02);
            }
            case TELEPORTE_CURTO -> {
                Location destino = destinoTeleporte(p, 8);
                if (destino == null) {
                    falhar(p, "habilidade-sem-espaco");
                    return false;
                }
                p.getWorld().spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);
                p.teleport(destino);
                p.setFallDistance(0);
                semQueda(p, 3);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destino.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
                p.getWorld().playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
            }
            case LUZ_CURATIVA -> {
                double cura = (3 + r.nivel() * 0.3) * (1 + 0.012 * plugin.perfis().atributoTotal(p, Atributo.SABEDORIA));
                for (Player a : aliados(p, 10)) {
                    curar(a, cura);
                    a.getWorld().spawnParticle(Particle.HEART, a.getLocation().add(0, 2, 0), 4, 0.4, 0.3, 0.4, 0);
                }
                p.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
                anel(l, 3, Particle.END_ROD);
            }
            case BENCAO -> {
                for (Player a : aliados(p, 10)) {
                    a.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true));
                    a.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0, false, true));
                    a.getWorld().spawnParticle(Particle.WAX_ON, a.getLocation().add(0, 1.2, 0), 15, 0.4, 0.6, 0.4, 0.1);
                }
                p.getWorld().playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
            }
            case MARTELO_SAGRADO -> {
                double dano = poder(p, Atributo.SABEDORIA) * 1.4;
                for (LivingEntity m : inimigosPerto(l, 5)) {
                    boolean mortoVivo = Tag.ENTITY_TYPES_UNDEAD.isTagged(m.getType());
                    plugin.combate().danoMagico(p, m, mortoVivo ? dano * 2 : dano);
                    m.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, m.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                }
                p.getWorld().playSound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f);
                anel(l, 5, Particle.END_ROD);
            }
            case PASSO_SOMBRIO -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
                plugin.combate().garantirCritico(p);
                for (LivingEntity m : inimigosPerto(l, 16)) {
                    if (m instanceof Mob mob && p.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
                p.getWorld().spawnParticle(Particle.SQUID_INK, l.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                p.getWorld().playSound(l, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1f);
            }
            case LAMINAS_VENENOSAS -> {
                golpesVenenosos.put(p.getUniqueId(), 4);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
                p.getWorld().playSound(l, Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 1.5f);
            }
            case BOMBA_DE_FUMACA -> {
                for (LivingEntity m : inimigosPerto(l, 6)) {
                    m.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, true));
                    m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true));
                    if (m instanceof Mob mob) {
                        mob.setTarget(null);
                    }
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, true));
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0, 0.5, 0), 40, 2, 0.6, 2, 0.01);
                p.getWorld().playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.7f);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ AJUDANTES

    /** Monstros hostis em volta (nunca jogadores, NPCs, animais ou pets). */
    private List<LivingEntity> inimigosPerto(Location l, double raio) {
        List<LivingEntity> r = new ArrayList<>();
        for (Entity e : l.getWorld().getNearbyEntities(l, raio, raio, raio)) {
            if (e instanceof LivingEntity le && e instanceof Enemy && !(e instanceof Player) && !le.isDead()
                    && e.getLocation().distanceSquared(l) <= raio * raio) {
                r.add(le);
            }
        }
        return r;
    }

    private List<Player> aliados(Player p, double raio) {
        List<Player> l = new ArrayList<>(plugin.grupos().pertoDe(p, raio));
        if (!l.contains(p)) {
            l.add(p);
        }
        return l;
    }

    private static double vidaMaxima(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static void curar(LivingEntity e, double quanto) {
        if (!e.isDead()) {
            e.setHealth(Math.min(vidaMaxima(e), e.getHealth() + quanto));
        }
    }

    private void semQueda(Player p, int segundos) {
        semQueda.put(p.getUniqueId(), System.currentTimeMillis() + segundos * 1000L);
    }

    private static void anel(Location c, double raio, Particle part) {
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            c.getWorld().spawnParticle(part, c.clone().add(Math.cos(a) * raio, 0.3, Math.sin(a) * raio), 1, 0, 0, 0, 0);
        }
    }

    private void flecha(Player p, Location de, Vector velocidade, double dano) {
        Arrow a = p.getWorld().spawn(de, Arrow.class, f -> {
            f.setShooter(p);
            f.setVelocity(velocidade);
            f.setDamage(dano);
            f.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            f.setPersistent(false);
            f.getPersistentDataContainer().set(chaveFlecha, PersistentDataType.BYTE, (byte) 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, a::remove, 100L);
    }

    /** Projétil de partículas: anda em linha reta até bater num bloco ou num monstro. */
    private void projetil(Player p, Particle rastro, double passo, double alcance, BiConsumer<Location, LivingEntity> aoAcertar) {
        Location inicio = p.getEyeLocation();
        Vector dir = inicio.getDirection().normalize().multiply(passo);
        new BukkitRunnable() {
            final Location atual = inicio.clone();
            double andou;

            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    atual.add(dir);
                    andou += passo;
                    atual.getWorld().spawnParticle(rastro, atual, 3, 0.08, 0.08, 0.08, 0.01);
                    Block b = atual.getBlock();
                    if (b.getType().isSolid() || andou >= alcance) {
                        aoAcertar.accept(atual.clone().subtract(dir.clone().multiply(0.5)), null);
                        cancel();
                        return;
                    }
                    for (LivingEntity m : inimigosPerto(atual, 1.3)) {
                        aoAcertar.accept(atual.clone(), m);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Até 'max' blocos para onde olha, sem atravessar paredes, num lugar com espaço para o jogador. */
    private static Location destinoTeleporte(Player p, double max) {
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        RayTraceResult rt = p.getWorld().rayTraceBlocks(olho, dir, max, FluidCollisionMode.NEVER, true);
        double dist = rt == null ? max : Math.max(0, rt.getHitPosition().distance(olho.toVector()) - 0.8);
        for (double d = dist; d >= 1.5; d -= 0.5) {
            Location pe = olho.clone().add(dir.clone().multiply(d)).subtract(0, p.getEyeHeight(), 0);
            Block corpo = pe.getBlock();
            if (corpo.isPassable() && corpo.getRelative(0, 1, 0).isPassable() && !corpo.isLiquid()) {
                pe.setYaw(olho.getYaw());
                pe.setPitch(olho.getPitch());
                // não fica preso no meio de um bloco baixo (laje, neve): sobe para a superfície
                if (!corpo.getRelative(0, -1, 0).isPassable() && pe.getY() - pe.getBlockY() < 0.2) {
                    pe.setY(pe.getBlockY());
                }
                return pe;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ EFEITOS QUE DURAM

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoCair(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && semQueda.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoFlechaDeHabilidade(EntityDamageByEntityEvent e) {
        // A flecha da habilidade nunca acerta quem atirou
        if (e.getDamager() instanceof Projectile pr && pr.getPersistentDataContainer().has(chaveFlecha)
                && e.getEntity().equals(pr.getShooter())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoBater(EntityDamageByEntityEvent e) {
        // Lâminas Venenosas: golpe corpo a corpo do ladino envenena
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity alvo) {
            Integer resta = golpesVenenosos.get(p.getUniqueId());
            if (resta != null) {
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, false, true));
                alvo.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, alvo.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
                if (resta <= 1) {
                    golpesVenenosos.remove(p.getUniqueId());
                } else {
                    golpesVenenosos.put(p.getUniqueId(), resta - 1);
                }
            }
        }
        // Escudo de Gelo: quem bate no mago fica lento
        if (e.getEntity() instanceof Player alvo && escudoGelo.getOrDefault(alvo.getUniqueId(), 0L) > System.currentTimeMillis()) {
            Entity d = e.getDamager();
            LivingEntity atacante = d instanceof LivingEntity le ? le
                    : d instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2 ? le2 : null;
            if (atacante != null && !atacante.equals(alvo)) {
                atacante.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true));
                atacante.getWorld().spawnParticle(Particle.SNOWFLAKE, atacante.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    @EventHandler
    public void aoAcertarFlecha(ProjectileHitEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(chaveFlecha) && e.getHitBlock() != null) {
            e.getEntity().remove();
        }
    }
}
