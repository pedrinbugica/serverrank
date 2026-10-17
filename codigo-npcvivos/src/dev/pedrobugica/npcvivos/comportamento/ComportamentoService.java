package dev.pedrobugica.npcvivos.comportamento;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.combate.AtributosCombate;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * O cérebro de todos os NPCs que lutam (fase 6): uma vez por segundo decide o estado de cada um e,
 * a cada 2 ticks, faz ele andar e bater. Também guarda o combate.yml, que é a fonte dos atributos.
 *
 * <p>Os guardas da fase 4 continuam no GuardaService: eles ficam no posto e não precisam andar.</p>
 */
public final class ComportamentoService {

    private final NpcVivosPlugin plugin;
    private final Map<String, Cerebro> cerebros = new HashMap<>();
    private YamlConfiguration cfg = new YamlConfiguration();
    private AtributosCombate padrao = new AtributosCombate(40, 4, 0, 0.28, 3.0, false, 120);
    private final Map<String, AtributosCombate> porPapel = new HashMap<>();
    private BukkitTask tarefaDecidir;
    private BukkitTask tarefaAgir;

    public ComportamentoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ CONFIGURAÇÃO

    public void carregar() {
        File arquivo = new File(plugin.getDataFolder(), "combate.yml");
        if (!arquivo.exists()) {
            plugin.saveResource("combate.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(arquivo);
        try (InputStream in = plugin.getResource("combate.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler o combate.yml padrão", e);
        }
        padrao = AtributosCombate.deConfig(cfg.getConfigurationSection("atributos.padrao"),
                new AtributosCombate(40, 4, 0, 0.28, 3.0, false, 120));
        porPapel.clear();
        ConfigurationSection s = cfg.getConfigurationSection("atributos");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (!k.equals("padrao")) {
                    porPapel.put(k.toLowerCase(Locale.ROOT), AtributosCombate.deConfig(s.getConfigurationSection(k), padrao));
                }
            }
        }
        // Os NPCs que já existem passam a usar os números novos (vida cheia)
        for (Cerebro c : cerebros.values()) {
            NpcVivo npc = plugin.npcs().obter(c.npcId());
            if (npc != null) {
                c.setAtributos(atributosDe(npc));
            }
        }
        plugin.getLogger().info("Combate: " + (ativo() ? porPapel.size() + " conjunto(s) de atributos." : "desligado no combate.yml."));
    }

    public boolean ativo() {
        return cfg.getBoolean("ativo", true);
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public double num(String caminho, double padraoValor) {
        return cfg.getDouble(caminho, padraoValor);
    }

    public int inteiro(String caminho, int padraoValor) {
        return cfg.getInt(caminho, padraoValor);
    }

    public boolean mundoProibido(String mundo) {
        return cfg.getStringList("mundos-sem-combate").contains(mundo);
    }

    /** Atributos do NPC: o bloco "combate:" do npcs/<id>.yml ganha do padrão do papel. */
    public AtributosCombate atributosDe(NpcVivo npc) {
        AtributosCombate base = padrao;
        for (Papel p : List.of(Papel.BOSS, Papel.DUELISTA, Papel.MERCENARIO, Papel.INVASOR, Papel.GUARDA)) {
            if (npc.tem(p) && porPapel.containsKey(p.name().toLowerCase(Locale.ROOT))) {
                base = porPapel.get(p.name().toLowerCase(Locale.ROOT));
                break;
            }
        }
        return AtributosCombate.deConfig(npc.combate(), base);
    }

    // ------------------------------------------------------------------ CÉREBROS

    /** O NPC luta (e por isso tem cérebro)? */
    public static boolean lutador(NpcVivo npc) {
        return npc.tem(Papel.MERCENARIO) || npc.tem(Papel.DUELISTA) || npc.tem(Papel.BOSS) || npc.tem(Papel.INVASOR);
    }

    public Cerebro cerebro(NpcVivo npc) {
        if (!lutador(npc)) {
            return null;
        }
        return cerebros.computeIfAbsent(npc.id(), id -> new Cerebro(id, atributosDe(npc), npc.local()));
    }

    /** Dá cérebro a todo NPC que luta (ao ligar o servidor e em cada /npcv reload). */
    public void prepararTodos() {
        for (NpcVivo npc : plugin.npcs().todos()) {
            if (lutador(npc)) {
                Cerebro c = cerebro(npc);
                if (c != null && c.posto() == null) {
                    c.setPosto(npc.local());
                }
            } else {
                cerebros.remove(npc.id());
            }
        }
        cerebros.keySet().removeIf(id -> plugin.npcs().obter(id) == null);
    }

    public Cerebro cerebroExistente(String npcId) {
        return cerebros.get(npcId);
    }

    public void esquecer(String npcId) {
        cerebros.remove(npcId);
    }

    public void esquecerTodos() {
        cerebros.clear();
    }

    /** Marca que o NPC está num menu/conversa, para ele parar de andar. */
    public void ocupar(NpcVivo npc, Player jogador, EstadoNpc estado) {
        Cerebro c = cerebro(npc);
        if (c != null && !c.estado().lutando() && !c.morto()) {
            c.setJogadorDaConversa(jogador == null ? null : jogador.getUniqueId());
            c.setEstado(estado);
        }
    }

    // ------------------------------------------------------------------ TAREFAS

    public void iniciar() {
        parar();
        if (!ativo()) {
            return;
        }
        tarefaDecidir = Bukkit.getScheduler().runTaskTimer(plugin, this::decidir, 60L, 20L);
        tarefaAgir = Bukkit.getScheduler().runTaskTimer(plugin, this::agir, 60L, 2L);
    }

    public void parar() {
        if (tarefaDecidir != null) {
            tarefaDecidir.cancel();
            tarefaDecidir = null;
        }
        if (tarefaAgir != null) {
            tarefaAgir.cancel();
            tarefaAgir = null;
        }
    }

    // ------------------------------------------------------------------ DECIDIR (1x por segundo)

    private void decidir() {
        double visao = num("comportamento.raio-de-visao", 16);
        double longeDoPosto = num("comportamento.distancia-do-posto", 24);
        double fracaoFuga = num("comportamento.vida-para-fugir", 0.2);
        for (NpcVivo npc : plugin.npcs().todos()) {
            Cerebro c = cerebros.get(npc.id());
            if (c == null || mundoProibido(npc.mundo())) {
                continue;
            }
            if (c.morto()) {
                plugin.combate().tentarReaparecer(npc, c);
                continue;
            }
            Mannequin corpo = corpo(npc);
            if (corpo == null) {
                continue;
            }
            Player alvo = alvoValido(c, corpo, visao * 1.5);
            if (alvo == null && npc.tem(Papel.BOSS)) {
                alvo = maisPertoHostil(corpo, visao, null);
                c.setAlvo(alvo == null ? null : alvo.getUniqueId());
            }
            if (alvo == null && npc.tem(Papel.INVASOR)) {
                // soldado de invasão: ataca quem defende a vila (menos os da própria facção) e senão marcha até o centro
                alvo = maisPertoHostil(corpo, visao, npc.faccao());
                c.setAlvo(alvo == null ? null : alvo.getUniqueId());
            }
            if (c.estado().ocupadoComJogador()) {
                Player conversa = c.jogadorDaConversa() == null ? null : Bukkit.getPlayer(c.jogadorDaConversa());
                boolean perto = conversa != null && conversa.getWorld().equals(corpo.getWorld())
                        && conversa.getLocation().distanceSquared(corpo.getLocation()) < visao * visao;
                if (perto && c.segundosNoEstado() < 120 && alvo == null) {
                    continue;
                }
                c.setJogadorDaConversa(null);
            }
            if (alvo != null) {
                boolean fraco = c.vida() <= c.vidaMaxima() * fracaoFuga && !npc.tem(Papel.BOSS) && !npc.tem(Papel.INVASOR)
                        && !plugin.duelos().emDuelo(npc.id());
                c.setEstado(fraco ? EstadoNpc.FUGINDO : EstadoNpc.COMBATE);
                continue;
            }
            if (c.dono() != null) {
                Player dono = Bukkit.getPlayer(c.dono());
                c.setEstado(dono == null ? EstadoNpc.VOLTANDO_AO_POSTO : EstadoNpc.SEGUINDO_DONO);
                continue;
            }
            Location posto = c.posto();
            boolean longe = posto != null && corpo.getWorld().equals(posto.getWorld())
                    && corpo.getLocation().distanceSquared(posto) > 2.25;
            if (longe) {
                c.setEstado(EstadoNpc.VOLTANDO_AO_POSTO);
                if (!npc.tem(Papel.INVASOR) && posto.distanceSquared(corpo.getLocation()) > longeDoPosto * longeDoPosto) {
                    corpo.teleport(posto); // ficou longe demais (caiu, empurrão): volta na hora
                }
                continue;
            }
            if (c.estado() != EstadoNpc.OCIOSO) {
                c.setEstado(EstadoNpc.OCIOSO);
                c.encherVida();
                plugin.combate().atualizarNome(npc, c);
            }
        }
    }

    /** Jogador que o NPC pode atacar: online, no mesmo mundo, vivo e jogando de verdade. */
    private Player alvoValido(Cerebro c, Mannequin corpo, double raio) {
        if (c.alvo() == null) {
            return null;
        }
        Player p = Bukkit.getPlayer(c.alvo());
        if (p == null || !atacavel(p) || !p.getWorld().equals(corpo.getWorld())
                || p.getLocation().distanceSquared(corpo.getLocation()) > raio * raio) {
            c.setAlvo(null);
            return null;
        }
        return p;
    }

    public static boolean atacavel(Player p) {
        return !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    /**
     * O mundo deixa jogador bater em jogador? (a regra do jogo, não o server.properties).
     * Usamos GameRules (a lista nova); as constantes do enum GameRule antigo estão marcadas para remoção.
     */
    public static boolean pvpLigado(World mundo) {
        return Boolean.TRUE.equals(mundo.getGameRuleValue(GameRules.PVP));
    }

    /** Jogador mais perto que dá para atacar (aliados = membros dessa facção ficam de fora; null = ninguém). */
    private Player maisPertoHostil(Mannequin corpo, double raio, String aliados) {
        Player melhor = null;
        double menor = raio * raio;
        for (Player p : corpo.getWorld().getPlayers()) {
            if (!atacavel(p) || (aliados != null && aliados.equals(plugin.repositorio().membro(p.getUniqueId())))) {
                continue;
            }
            double d = p.getLocation().distanceSquared(corpo.getLocation());
            if (d < menor) {
                menor = d;
                melhor = p;
            }
        }
        return melhor;
    }

    // ------------------------------------------------------------------ AGIR (a cada 2 ticks)

    private void agir() {
        int quedaMaxima = inteiro("comportamento.queda-maxima", 3);
        double distanciaDono = num("mercenarios.distancia-maxima-do-dono", 24);
        for (Map.Entry<String, Cerebro> e : new ArrayList<>(cerebros.entrySet())) {
            Cerebro c = e.getValue();
            NpcVivo npc = plugin.npcs().obter(e.getKey());
            if (npc == null) {
                cerebros.remove(e.getKey());
                continue;
            }
            if (c.morto() || mundoProibido(npc.mundo())) {
                continue;
            }
            Mannequin corpo = corpo(npc);
            if (corpo == null) {
                continue;
            }
            double velocidade = c.atributos().velocidade() * 2; // a tarefa roda a cada 2 ticks
            switch (c.estado()) {
                case COMBATE -> perseguirEBater(npc, c, corpo, velocidade, quedaMaxima);
                case FUGINDO -> fugir(npc, c, corpo, velocidade, quedaMaxima);
                case SEGUINDO_DONO -> seguirDono(c, corpo, velocidade, quedaMaxima, distanciaDono);
                case VOLTANDO_AO_POSTO -> voltarAoPosto(npc, c, corpo, velocidade, quedaMaxima);
                default -> {
                }
            }
        }
    }

    private void perseguirEBater(NpcVivo npc, Cerebro c, Mannequin corpo, double velocidade, int quedaMaxima) {
        Player alvo = c.alvo() == null ? null : Bukkit.getPlayer(c.alvo());
        if (alvo == null || !alvo.getWorld().equals(corpo.getWorld())) {
            return;
        }
        Movimento.olhar(corpo, alvo.getEyeLocation());
        double dist = corpo.getLocation().distance(alvo.getLocation());
        double alcance = c.atributos().alcance();
        if (c.atributos().arqueiro()) {
            if (dist > 4) {
                Movimento.passo(corpo, alvo.getLocation(), velocidade, quedaMaxima);
            }
            if (dist <= alcance && corpo.hasLineOfSight(alvo)
                    && c.golpeNoPonto((long) (num("comportamento.intervalo-flecha-segundos", 2.0) * 1000))) {
                atirar(corpo, alvo, c, dist);
            }
            return;
        }
        if (dist > alcance) {
            Movimento.passo(corpo, alvo.getLocation(), velocidade, quedaMaxima);
            return;
        }
        if (c.golpeNoPonto((long) (num("comportamento.intervalo-golpe-segundos", 1.2) * 1000))) {
            golpear(npc, corpo, alvo, c.atributos().dano());
        }
    }

    /** Dá um golpe corpo a corpo no jogador (quem protege área segura cancela o evento de dano). */
    public void golpear(NpcVivo npc, Mannequin corpo, Player alvo, double dano) {
        corpo.swingMainHand();
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1f);
        alvo.damage(dano, corpo);
        Vector empurrao = alvo.getLocation().toVector().subtract(corpo.getLocation().toVector());
        if (empurrao.lengthSquared() > 0.01) {
            alvo.setVelocity(empurrao.normalize().multiply(0.35).setY(0.28));
        }
    }

    private void atirar(Mannequin corpo, Player alvo, Cerebro c, double dist) {
        Location de = corpo.getEyeLocation();
        Vector dir = alvo.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(de.toVector());
        dir.setY(dir.getY() + dist * 0.06);
        Arrow flecha = corpo.launchProjectile(Arrow.class, dir.normalize().multiply(1.9));
        flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        flecha.setDamage(c.atributos().dano() / 2.5);
        flecha.getPersistentDataContainer().set(plugin.combate().chaveFlecha(), PersistentDataType.STRING, c.npcId());
        corpo.getWorld().playSound(de, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flecha.isValid()) {
                flecha.remove();
            }
        }, 100L);
    }

    private void fugir(NpcVivo npc, Cerebro c, Mannequin corpo, double velocidade, int quedaMaxima) {
        Player alvo = c.alvo() == null ? null : Bukkit.getPlayer(c.alvo());
        Location destino = c.posto();
        if (alvo != null && alvo.getWorld().equals(corpo.getWorld())) {
            Movimento.olhar(corpo, alvo.getEyeLocation());
            Vector longe = corpo.getLocation().toVector().subtract(alvo.getLocation().toVector()).setY(0);
            if (longe.lengthSquared() > 0.01) {
                destino = corpo.getLocation().add(longe.normalize().multiply(6));
            }
        }
        if (destino != null) {
            Movimento.passo(corpo, destino, velocidade * 1.15, quedaMaxima);
        }
        if (c.segundosNoEstado() >= 3 && c.golpeNoPonto(8000)) {
            plugin.combate().chamarAliados(npc, c);
        }
    }

    private void seguirDono(Cerebro c, Mannequin corpo, double velocidade, int quedaMaxima, double distanciaMaxima) {
        Player dono = Bukkit.getPlayer(c.dono());
        if (dono == null) {
            return;
        }
        if (!dono.getWorld().equals(corpo.getWorld())
                || dono.getLocation().distanceSquared(corpo.getLocation()) > distanciaMaxima * distanciaMaxima) {
            corpo.teleport(dono.getLocation());
            return;
        }
        double dist = dono.getLocation().distance(corpo.getLocation());
        if (dist > 3.5) {
            Movimento.passo(corpo, dono.getLocation(), velocidade, quedaMaxima);
        } else {
            Movimento.olhar(corpo, dono.getEyeLocation());
        }
    }

    private void voltarAoPosto(NpcVivo npc, Cerebro c, Mannequin corpo, double velocidade, int quedaMaxima) {
        Location posto = c.posto();
        if (posto == null || !posto.getWorld().equals(corpo.getWorld())) {
            return;
        }
        if (posto.distanceSquared(corpo.getLocation()) <= 2.25) {
            corpo.teleport(posto);
            c.setEstado(EstadoNpc.OCIOSO);
            c.encherVida();
            plugin.combate().atualizarNome(npc, c);
            return;
        }
        Movimento.passo(corpo, posto, velocidade, quedaMaxima);
        // Recupera vida devagar no caminho de volta
        if (c.vida() < c.vidaMaxima() && c.golpeNoPonto(2000)) {
            c.setVida(c.vida() + c.vidaMaxima() * 0.05);
            plugin.combate().atualizarNome(npc, c);
        }
    }

    // ------------------------------------------------------------------ AJUDA

    public Mannequin corpo(NpcVivo npc) {
        if (npc.entidade() == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(npc.entidade());
        return e instanceof Mannequin m && m.isValid() ? m : null;
    }

    /** Guarda o posto quando o NPC é criado ou movido, para ele saber para onde voltar. */
    public void marcarPosto(NpcVivo npc) {
        Cerebro c = cerebros.get(npc.id());
        if (c != null) {
            c.setPosto(npc.local());
        }
    }

    public String estadoDe(NpcVivo npc) {
        Cerebro c = cerebros.get(npc.id());
        if (c == null) {
            return "—";
        }
        String vida = Texto.dinheiro(Math.ceil(c.vida())) + "/" + Texto.dinheiro(c.vidaMaxima());
        return c.estado().nome() + " (" + (c.morto() ? "morto" : vida + " de vida") + ")";
    }

    public List<UUID> donos() {
        List<UUID> r = new ArrayList<>();
        for (Cerebro c : cerebros.values()) {
            if (c.dono() != null) {
                r.add(c.dono());
            }
        }
        return r;
    }

    public Map<String, Cerebro> todos() {
        return cerebros;
    }
}
