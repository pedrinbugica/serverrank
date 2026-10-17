package dev.pedrobugica.npcvivos.upgrade;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.upgrade.ItemAprimoravel.Tipo;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.tag.DamageTypeTags;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efeito dos itens aprimorados em combate (dano, defesa e habilidades do +9/+10), pedras que caem dos
 * monstros e proteção das pedras/pergaminho (não viram receita nem vão para bigorna).
 */
public final class UpgradeListener implements Listener {

    private final NpcVivosPlugin plugin;
    /** Ligado enquanto o dano em área está sendo aplicado (para não aplicar bônus/habilidade de novo). */
    private boolean emArea;

    public UpgradeListener(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private UpgradeService up() {
        return plugin.upgrades();
    }

    private record Golpe(Player atacante, int nivel, Habilidade habilidade) {
    }

    /** Quem bateu e com que arma aprimorada (null = arma comum ou não foi um jogador). */
    private Golpe golpe(EntityDamageByEntityEvent e) {
        ItemAprimoravel itens = up().itens();
        Entity d = e.getDamager();
        if (d instanceof Player p) {
            if (e.getCause() != DamageCause.ENTITY_ATTACK && e.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) {
                return null;
            }
            ItemStack arma = p.getInventory().getItemInMainHand();
            int n = ItemAprimoravel.tipo(arma) == Tipo.ARMA ? itens.nivel(arma) : 0;
            return n > 0 ? new Golpe(p, n, itens.habilidade(arma)) : null;
        }
        if (d instanceof Trident t && t.getShooter() instanceof Player p) {
            ItemStack arma = t.getItemStack();
            int n = itens.nivel(arma);
            return n > 0 ? new Golpe(p, n, itens.habilidade(arma)) : null;
        }
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            PersistentDataContainer pdc = proj.getPersistentDataContainer();
            int n = pdc.getOrDefault(itens.chaveNivel(), PersistentDataType.INTEGER, 0);
            return n > 0 ? new Golpe(p, n, Habilidade.buscar(pdc.get(itens.chaveHabilidade(), PersistentDataType.STRING))) : null;
        }
        return null;
    }

    private static boolean deJogador(Entity damager) {
        return damager instanceof Player || (damager instanceof Projectile pr && pr.getShooter() instanceof Player);
    }

    // ------------------------------------------------------------------ COMBATE

    /** Arma aprimorada: dano x (1 + bônus). Contra jogadores, o bônus tem teto (pvp.bonus-maximo). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bonusDeDano(EntityDamageByEntityEvent e) {
        if (emArea || !up().ativo() || up().mundoSemUpgrade(e.getEntity().getWorld())) {
            return;
        }
        Golpe g = golpe(e);
        if (g == null) {
            return;
        }
        double bonus = up().bonusTotal(g.nivel()) / 100.0;
        if (e.getEntity() instanceof Player) {
            bonus = Math.min(bonus, up().pvpMaximo());
        }
        e.setDamage(e.getDamage() * (1 + bonus));
    }

    /**
     * Armadura aprimorada: o dano é dividido por (1 + soma dos bônus das peças / 4). Vale para o mesmo dano que
     * a armadura normal reduz (golpes, flechas, explosões...); queda, fogo, veneno etc. passam direto, como no jogo.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void defesa(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v) || DamageTypeTags.BYPASSES_ARMOR.isTagged(e.getDamageSource().getDamageType())
                || !up().ativo()
                || up().mundoSemUpgrade(v.getWorld())) {
            return;
        }
        ItemAprimoravel itens = up().itens();
        double soma = 0;
        for (ItemStack peca : v.getInventory().getArmorContents()) {
            if (ItemAprimoravel.tipo(peca) == Tipo.ARMADURA) {
                soma += up().bonusTotal(itens.nivel(peca));
            }
        }
        if (soma <= 0) {
            return;
        }
        double efetivo = soma / 400.0;
        if (e instanceof EntityDamageByEntityEvent ed && deJogador(ed.getDamager())) {
            efetivo = Math.min(efetivo, up().pvpMaximo());
        }
        e.setDamage(e.getDamage() / (1 + efetivo));
    }

    /** Habilidades das armas +9/+10 (depois que o dano final já foi decidido). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void habilidades(EntityDamageByEntityEvent e) {
        if (emArea || !up().ativo() || e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK
                || !(e.getEntity() instanceof LivingEntity alvo) || up().mundoSemUpgrade(alvo.getWorld())) {
            return;
        }
        Golpe g = golpe(e);
        if (g == null || g.habilidade() == null || g.nivel() < 9) {
            return;
        }
        Player p = g.atacante();
        double dano = e.getFinalDamage();
        double valor = up().valorHabilidade(g.habilidade(), g.nivel());
        switch (g.habilidade()) {
            case ROUBO_DE_VIDA -> {
                AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
                double max = vida == null ? 20 : vida.getValue();
                if (!p.isDead() && p.getHealth() < max) {
                    p.setHealth(Math.min(max, p.getHealth() + dano * valor / 100.0));
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
                }
            }
            case DANO_EM_AREA -> {
                double raio = up().raioArea();
                double extra = dano * valor / 100.0;
                if (extra <= 0) {
                    return;
                }
                emArea = true;
                try {
                    for (Entity en : alvo.getNearbyEntities(raio, raio, raio)) {
                        if (en instanceof LivingEntity le && en instanceof Enemy && !en.equals(alvo) && !en.equals(p)
                                && !plugin.npcs().ehEntidadeNpc(en)) {
                            le.damage(extra, p);
                        }
                    }
                } finally {
                    emArea = false;
                }
                alvo.getWorld().spawnParticle(Particle.SWEEP_ATTACK, alvo.getLocation().add(0, 1, 0), 3, 1, 0.3, 1, 0);
            }
            case LENTIDAO -> alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.round(valor * 20),
                    g.nivel() >= 10 ? 1 : 0, false, true, true));
        }
    }

    /** O nível do arco/besta vai junto com a flecha. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoAtirar(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getProjectile() instanceof Projectile proj)) {
            return;
        }
        ItemStack arco = e.getBow();
        ItemAprimoravel itens = up().itens();
        int n = ItemAprimoravel.tipo(arco) == Tipo.ARMA ? itens.nivel(arco) : 0;
        if (n <= 0) {
            return;
        }
        PersistentDataContainer pdc = proj.getPersistentDataContainer();
        pdc.set(itens.chaveNivel(), PersistentDataType.INTEGER, n);
        Habilidade h = itens.habilidade(arco);
        if (h != null) {
            pdc.set(itens.chaveHabilidade(), PersistentDataType.STRING, h.name());
        }
    }

    // ------------------------------------------------------------------ PEDRAS QUE CAEM

    @EventHandler(priority = EventPriority.NORMAL)
    public void aoMorrer(EntityDeathEvent e) {
        LivingEntity morto = e.getEntity();
        Player k = morto.getKiller();
        if (k == null || !up().ativo() || morto instanceof Player || up().dropProibido(morto.getWorld()) || morto.fromMobSpawner()
                || plugin.npcs().ehEntidadeNpc(morto)) {
            return;
        }
        ItemAprimoravel itens = up().itens();
        Map<Pedra, Integer> chefe = up().dropChefe(morto.getType());
        if (chefe != null) {
            for (Map.Entry<Pedra, Integer> d : chefe.entrySet()) {
                e.getDrops().add(itens.criarPedra(d.getKey(), d.getValue()));
                avisar(k, d.getKey().nomeCompleto(), d.getValue());
            }
            return;
        }
        if (!(morto instanceof Enemy)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Pedra p : Pedra.values()) {
            if (r.nextDouble() * 100 < up().chanceDrop(p)) {
                e.getDrops().add(itens.criarPedra(p, 1));
                avisar(k, p.nomeCompleto(), 1);
            }
        }
        if (r.nextDouble() * 100 < up().chancePergaminho()) {
            e.getDrops().add(itens.criarPergaminho(1));
            avisar(k, "<gold>Pergaminho de Proteção", 1);
        }
    }

    private void avisar(Player p, String item, int quantidade) {
        p.sendActionBar(Texto.cor(plugin.mensagens().bruta("pedra-caiu"), "item", item, "quantidade", String.valueOf(quantidade)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.7f);
    }

    // ------------------------------------------------------------------ PROTEÇÃO DAS PEDRAS / BIGORNA

    /** Pedras e pergaminho não servem de ingrediente (são sílex, prismarinho, ametista, eco e papel por baixo). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoPrepararReceita(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (up().itens().especial(s)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Bigorna, mesa de ferraria, rebolo, cartografia...: bloqueia as pedras e mantém nome/lore do nível certos. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoPrepararResultado(PrepareResultEvent e) {
        ItemAprimoravel itens = up().itens();
        for (ItemStack s : e.getInventory().getContents()) {
            if (itens.especial(s)) {
                e.setResult(null);
                return;
            }
        }
        ItemStack res = e.getResult();
        if (res == null || itens.nivel(res) <= 0) {
            return;
        }
        res = res.clone();
        if (e instanceof PrepareAnvilEvent pa) {
            // O jogo só troca o nome do resultado quando o jogador renomeia de verdade (ou apaga o nome)
            ItemStack original = e.getInventory().getItem(0);
            Component antes = original != null && original.hasItemMeta() ? original.getItemMeta().customName() : null;
            Component depois = res.hasItemMeta() ? res.getItemMeta().customName() : null;
            if (!Objects.equals(antes, depois)) {
                String texto = pa.getView().getRenameText();
                itens.renomear(res, texto == null ? "" : texto.replaceAll("\\s*\\+\\d+$", "").trim());
            } else {
                itens.redesenhar(res);
            }
        } else {
            itens.redesenhar(res);
        }
        e.setResult(res);
    }
}
