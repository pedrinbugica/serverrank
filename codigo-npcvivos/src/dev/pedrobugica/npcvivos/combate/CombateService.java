package dev.pedrobugica.npcvivos.combate;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.ComportamentoService;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.upgrade.Pedra;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A vida dos NPCs de combate é do plugin, não da entidade: o manequim nunca perde vida de verdade
 * (o dano do jogo é sempre cancelado) e nós controlamos vida, morte, drops e reaparecimento.
 * Assim o NPC não morre por queda, fogo ou flecha perdida, e o número fica igual em qualquer versão.
 */
public final class CombateService {

    private final NpcVivosPlugin plugin;
    private final NamespacedKey chaveFlecha;
    /** Quem bateu em cada NPC desde que ele (re)apareceu: chefe vencido junto aproxima as facções (fase 7). */
    private final Map<String, java.util.Set<UUID>> lutaram = new HashMap<>();
    /** Quantas recompensas de NPC morto cada jogador já pegou nesta hora (anti-farm). */
    private final Map<UUID, int[]> recompensasNaHora = new HashMap<>();

    public CombateService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.chaveFlecha = new NamespacedKey(plugin, "flecha-npc");
    }

    public NamespacedKey chaveFlecha() {
        return chaveFlecha;
    }

    // ------------------------------------------------------------------ DANO NO NPC

    /** Chamado pelo NpcListener para todo dano que chega num NPC (o evento já foi cancelado). */
    public void aoLevarDano(NpcVivo npc, EntityDamageEvent e) {
        Cerebro c = plugin.comportamento().cerebroExistente(npc.id());
        if (c == null || c.morto() || !plugin.comportamento().ativo()) {
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return; // varredura da espada que pegou o NPC sem querer
        }
        Player autor = autorDoDano(e);
        if (autor == null || !ComportamentoService.atacavel(autor)) {
            return; // fogo, queda, flecha de outro NPC: NPC de combate não se machuca com isso
        }
        if (npc.tem(Papel.MERCENARIO) && autor.getUniqueId().equals(c.dono())) {
            return; // o dono não machuca o próprio mercenário
        }
        if (plugin.duelos().emDuelo(npc.id()) && !plugin.duelos().ehDesafiante(npc.id(), autor)) {
            plugin.mensagens().enviar(autor, "duelo-de-outro");
            return;
        }
        // O bônus da arma aprimorada já entrou no dano: o UpgradeListener roda antes (prioridade HIGH)
        aplicarDano(npc, c, e.getDamage(), autor);
    }

    /** Tira vida do NPC. Devolve true se ele morreu com este golpe. */
    public boolean aplicarDano(NpcVivo npc, Cerebro c, double bruto, Player autor) {
        double dano = c.atributos().danoRecebido(bruto);
        c.setVida(c.vida() - dano);
        if (autor != null) {
            c.setAlvo(autor.getUniqueId());
            lutaram.computeIfAbsent(npc.id(), k -> new java.util.LinkedHashSet<>()).add(autor.getUniqueId());
            if (!c.estado().lutando()) {
                c.setEstado(EstadoNpc.COMBATE);
            }
        }
        Mannequin corpo = plugin.comportamento().corpo(npc);
        if (corpo != null) {
            corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 1.1f);
            corpo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, corpo.getLocation().add(0, 1.2, 0), 4, 0.2, 0.3, 0.2, 0);
        }
        atualizarNome(npc, c);
        if (c.vida() > 0) {
            return false;
        }
        morrer(npc, c, autor);
        return true;
    }

    private Player autorDoDano(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent ed)) {
            return null;
        }
        Entity dano = ed.getDamager();
        if (dano instanceof Player p) {
            return p;
        }
        if (dano instanceof Projectile proj) {
            if (proj.getPersistentDataContainer().has(chaveFlecha, PersistentDataType.STRING)) {
                return null; // flecha de outro NPC
            }
            ProjectileSource atirador = proj.getShooter();
            if (atirador instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ MORTE E REAPARECIMENTO

    private void morrer(NpcVivo npc, Cerebro c, Player autor) {
        c.marcarMorte();
        c.setAlvo(null);
        c.setDono(null);
        Mannequin corpo = plugin.comportamento().corpo(npc);
        Location onde = corpo == null ? c.posto() : corpo.getLocation();
        if (corpo != null) {
            corpo.getWorld().playSound(onde, Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
            corpo.getWorld().spawnParticle(Particle.CLOUD, onde.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            corpo.remove();
            npc.setEntidade(null);
        }
        if (plugin.duelos().emDuelo(npc.id())) {
            plugin.duelos().npcPerdeu(npc, autor);
        }
        if (autor != null && onde != null) {
            if (dentroDoLimitePorHora(autor)) {
                darDrops(npc, onde, autor);
            } else {
                plugin.mensagens().enviar(autor, "combate-limite-por-hora");
            }
        }
        if (npc.tem(Papel.BOSS)) {
            plugin.bosses().aoMorrer(npc, c, autor);
        }
        if (autor != null) {
            plugin.comercio().registrar(autor, npc, "MORTE", "npc", 1, 0);
        }
        // Mundo vivo (fase 7): pontos de guerra, diplomacia e invasões
        java.util.Set<UUID> quem = lutaram.remove(npc.id());
        if (npc.tem(Papel.BOSS) && quem != null) {
            plugin.diplomacia().aoVencerChefe(quem, npc);
        }
        if (autor != null && !plugin.duelos().emDuelo(npc.id())) {
            plugin.guerras().aoMatarNpc(autor, npc);
            plugin.diplomacia().aoMatarNpc(autor, npc);
        }
        plugin.invasoes().aoMorrer(npc, autor);
    }

    /** Volta o NPC para o posto depois do tempo de respawn. */
    public void tentarReaparecer(NpcVivo npc, Cerebro c) {
        int segundos = c.atributos().respawnSegundos();
        if (segundos <= 0 || System.currentTimeMillis() - c.morreuEm() < segundos * 1000L) {
            return;
        }
        c.setAtributos(plugin.comportamento().atributosDe(npc));
        c.setEstado(EstadoNpc.OCIOSO);
        c.setAlvo(null);
        if (c.posto() != null) {
            npc.setLocal(c.posto().getWorld().getName(), c.posto().getX(), c.posto().getY(), c.posto().getZ(), c.posto().getYaw());
        }
        plugin.npcs().spawnar(npc);
        atualizarNome(npc, c);
        if (npc.tem(Papel.BOSS)) {
            plugin.bosses().aoReaparecer(npc);
        }
    }

    private boolean dentroDoLimitePorHora(Player p) {
        int limite = plugin.comportamento().inteiro("limite-de-recompensa-por-hora", 10);
        if (limite <= 0) {
            return true;
        }
        int horaAtual = (int) (System.currentTimeMillis() / 3_600_000L);
        int[] atual = recompensasNaHora.get(p.getUniqueId());
        if (atual == null || atual[0] != horaAtual) {
            atual = new int[]{horaAtual, 0};
            recompensasNaHora.put(p.getUniqueId(), atual);
        }
        if (atual[1] >= limite) {
            return false;
        }
        atual[1]++;
        return true;
    }

    /** Tabela de drops do NPC: dinheiro, pedras de aprimoramento e itens. */
    private void darDrops(NpcVivo npc, Location onde, Player autor) {
        ConfigurationSection drops = npc.combate() == null ? null : npc.combate().getConfigurationSection("drops");
        if (drops == null) {
            drops = plugin.comportamento().cfg().getConfigurationSection("drops."
                    + (npc.tem(Papel.BOSS) ? "boss" : npc.tem(Papel.DUELISTA) ? "duelista" : "padrao"));
        }
        if (drops == null) {
            return;
        }
        double dinheiro = drops.getDouble("dinheiro", 0);
        if (dinheiro > 0 && plugin.economia().ativa()) {
            plugin.economia().depositar(autor, dinheiro);
            plugin.mensagens().enviar(autor, "combate-recompensa", "valor", Texto.dinheiro(dinheiro), "npc", npc.nome());
            plugin.comercio().registrar(autor, npc, "COMBATE", "npc-morto", 1, dinheiro);
        }
        ConfigurationSection pedras = drops.getConfigurationSection("pedras");
        if (pedras != null) {
            for (String k : pedras.getKeys(false)) {
                Pedra pedra = Pedra.buscar(k);
                int qtd = pedras.getInt(k, 0);
                if (pedra != null && qtd > 0) {
                    onde.getWorld().dropItemNaturally(onde, plugin.upgrades().itens().criarPedra(pedra, qtd));
                }
            }
        }
        for (String nome : drops.getStringList("itens")) {
            String[] partes = nome.split("\\s+");
            Material mat = Material.matchMaterial(partes[0]);
            int qtd = partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1;
            if (mat == null || !mat.isItem() || mat.isAir()) {
                plugin.getLogger().warning("combate.yml: item de drop inválido '" + nome + "'.");
                continue;
            }
            onde.getWorld().dropItemNaturally(onde, new ItemStack(mat, Math.max(1, qtd)));
        }
    }

    // ------------------------------------------------------------------ AJUDA

    /** Mostra a vida na plaquinha do NPC enquanto ele está lutando. */
    public void atualizarNome(NpcVivo npc, Cerebro c) {
        Mannequin corpo = plugin.comportamento().corpo(npc);
        if (corpo == null) {
            return;
        }
        if (c.vida() >= c.vidaMaxima() || c.morto()) {
            corpo.customName(Texto.cor(npc.nome()));
            return;
        }
        int cheios = (int) Math.max(1, Math.round(c.vida() / c.vidaMaxima() * 10));
        corpo.customName(Texto.cor(npc.nome() + " <gray>| <red>" + "❤".repeat(cheios) + "<dark_gray>" + "❤".repeat(10 - cheios)));
    }

    /** NPC fugindo chama os guardas e mercenários aliados que estiverem por perto. */
    public void chamarAliados(NpcVivo npc, Cerebro c) {
        if (c.alvo() == null) {
            return;
        }
        Player alvo = Bukkit.getPlayer(c.alvo());
        Mannequin corpo = plugin.comportamento().corpo(npc);
        if (alvo == null || corpo == null) {
            return;
        }
        double raio = plugin.comportamento().num("comportamento.raio-de-ajuda", 20);
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
        for (NpcVivo outro : plugin.npcs().todos()) {
            if (outro == npc || !outro.mundo().equals(npc.mundo())) {
                continue;
            }
            Cerebro co = plugin.comportamento().cerebroExistente(outro.id());
            Mannequin corpoOutro = plugin.comportamento().corpo(outro);
            boolean mesmaFaccao = npc.faccao() != null && npc.faccao().equals(outro.faccao());
            if (co == null || co.morto() || corpoOutro == null || !mesmaFaccao
                    || corpoOutro.getLocation().distanceSquared(corpo.getLocation()) > raio * raio) {
                continue;
            }
            co.setAlvo(alvo.getUniqueId());
            co.setEstado(EstadoNpc.COMBATE);
        }
    }

    /** Sorteia com uma chance em % (usado pelas habilidades dos chefes). */
    public static boolean sorte(double porcento) {
        return ThreadLocalRandom.current().nextDouble() * 100 < porcento;
    }
}
