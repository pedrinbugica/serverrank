package dev.pedrobugica.npcvivos.comportamento;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Rotina;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A vida "de fundo" dos NPCs: passear pela vila, fazer a ronda, falar sozinho num balão em cima da cabeça,
 * cumprimentar quem chega perto e conversar com outro NPC quando os dois param lado a lado.
 * NPC longe de qualquer jogador fica parado (não gasta nada). Quem luta (cérebro de combate) não entra aqui.
 */
public final class VidaService {

    private static final class Estado {
        Location destino;
        long pausaAte;
        int ponto;
        int travado;
        long proximaFala;
        long ocupadoAte;
        TextDisplay balao;
        long balaoAte;
        final Map<UUID, Long> cumprimentou = new HashMap<>();
    }

    private final NpcVivosPlugin plugin;
    private final Map<String, Estado> estados = new HashMap<>();
    private BukkitTask tarefaMover;
    private BukkitTask tarefaDecidir;

    public VidaService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private double cfg(String k, double padrao) {
        return plugin.getConfig().getDouble("vida." + k, padrao);
    }

    public void iniciar() {
        parar();
        if (!plugin.getConfig().getBoolean("vida.ativo", true)) {
            return;
        }
        tarefaMover = Bukkit.getScheduler().runTaskTimer(plugin, this::mover, 40L, 2L);
        tarefaDecidir = Bukkit.getScheduler().runTaskTimer(plugin, this::decidir, 40L, 20L);
    }

    public void parar() {
        if (tarefaMover != null) {
            tarefaMover.cancel();
            tarefaMover = null;
        }
        if (tarefaDecidir != null) {
            tarefaDecidir.cancel();
            tarefaDecidir = null;
        }
        for (Estado e : estados.values()) {
            tirarBalao(e);
        }
        estados.clear();
    }

    private Estado estado(NpcVivo npc) {
        return estados.computeIfAbsent(npc.id(), k -> {
            Estado e = new Estado();
            e.proximaFala = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(5000, 20000);
            return e;
        });
    }

    /** O NPC está andando agora? (aí ele olha para onde vai, e não para o jogador) */
    public boolean andando(NpcVivo npc) {
        Estado e = estados.get(npc.id());
        return e != null && e.destino != null;
    }

    /** Um jogador começou a conversar: o NPC para de andar por um tempo. */
    public void ocupar(NpcVivo npc, int segundos) {
        Estado e = estado(npc);
        e.ocupadoAte = System.currentTimeMillis() + segundos * 1000L;
        e.destino = null;
    }

    public void esquecer(String npcId) {
        Estado e = estados.remove(npcId);
        if (e != null) {
            tirarBalao(e);
        }
    }

    private Mannequin corpo(NpcVivo npc) {
        if (npc.entidade() == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(npc.entidade());
        return e instanceof Mannequin m && m.isValid() ? m : null;
    }

    /** NPC que tem cérebro de combate (mercenário, duelista, chefe, soldado) anda pelo ComportamentoService. */
    private boolean lutador(NpcVivo npc) {
        return plugin.comportamento() != null && ComportamentoService.lutador(npc);
    }

    private static boolean noite(World w) {
        long t = w.getTime() % 24000;
        return t >= 13000 && t <= 23000;
    }

    // ------------------------------------------------------------------ DECIDIR (1x por segundo)

    private void decidir() {
        long agora = System.currentTimeMillis();
        double acordado = cfg("distancia-acordado", 40);
        List<NpcVivo> parados = new ArrayList<>();
        for (NpcVivo npc : plugin.npcs().todos()) {
            Rotina r = npc.rotina();
            if (npc.temporario() || lutador(npc)) {
                continue;
            }
            Mannequin m = corpo(npc);
            if (m == null) {
                continue;
            }
            Player perto = maisPerto(m, acordado);
            if (perto == null) {
                continue; // ninguém por perto: o NPC "dorme"
            }
            Estado e = estado(npc);
            boolean ocupado = e.ocupadoAte > agora;
            // Andar
            if (!ocupado && r != null && r.anda() && e.destino == null && agora >= e.pausaAte) {
                Location casa = npc.local();
                if (casa != null && casa.getWorld().equals(m.getWorld())) {
                    if (r.dormir() && noite(m.getWorld())) {
                        if (m.getLocation().distanceSquared(casa) > 2.25) {
                            e.destino = casa;
                        } else {
                            e.pausaAte = agora + 15_000;
                        }
                    } else if (r.tipo() == Rotina.Tipo.PATRULHA) {
                        Vector v = r.pontos().get(e.ponto % r.pontos().size());
                        e.ponto = (e.ponto + 1) % r.pontos().size();
                        e.destino = new Location(m.getWorld(), v.getX() + 0.5, v.getY(), v.getZ() + 0.5);
                    } else {
                        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                        double dist = ThreadLocalRandom.current().nextDouble(1.5, Math.max(2, r.raio()));
                        e.destino = casa.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                    }
                    e.travado = 0;
                }
            }
            if (e.destino == null) {
                parados.add(npc);
            }
            // Cumprimentar quem chega perto
            Player muitoPerto = maisPerto(m, cfg("distancia-cumprimento", 4));
            if (muitoPerto != null && !ocupado) {
                Long ultimo = e.cumprimentou.get(muitoPerto.getUniqueId());
                if (ultimo == null || agora - ultimo > cfg("cumprimentar-a-cada-minutos", 5) * 60_000L) {
                    e.cumprimentou.put(muitoPerto.getUniqueId(), agora);
                    List<String> l = plugin.mensagens().lista("vida-cumprimentos");
                    if (!l.isEmpty()) {
                        falar(npc, m, sortear(l), muitoPerto);
                        e.proximaFala = agora + 12_000;
                        continue;
                    }
                }
            }
            // Falar sozinho de vez em quando
            if (agora >= e.proximaFala && !ocupado && perto.getLocation().distanceSquared(m.getLocation()) <= 16 * 16) {
                List<String> falas = r != null && !r.falas().isEmpty() ? r.falas() : plugin.mensagens().lista("vida-falas-comuns");
                if (!falas.isEmpty()) {
                    falar(npc, m, sortear(falas), perto);
                }
                e.proximaFala = agora + (long) (cfg("falar-a-cada-segundos", 35) * 1000 * ThreadLocalRandom.current().nextDouble(0.7, 1.4));
            }
        }
        conversarEntreSi(parados, agora);
    }

    /** Dois NPCs parados lado a lado batem um papo (um fala, o outro responde). */
    private void conversarEntreSi(List<NpcVivo> parados, long agora) {
        if (parados.size() < 2 || ThreadLocalRandom.current().nextDouble() > 0.15) {
            return;
        }
        List<String> conversas = plugin.mensagens().lista("vida-conversas");
        if (conversas.isEmpty()) {
            return;
        }
        for (int i = 0; i < parados.size(); i++) {
            for (int j = i + 1; j < parados.size(); j++) {
                NpcVivo a = parados.get(i);
                NpcVivo b = parados.get(j);
                Mannequin ma = corpo(a);
                Mannequin mb = corpo(b);
                if (ma == null || mb == null || !ma.getWorld().equals(mb.getWorld())
                        || ma.getLocation().distanceSquared(mb.getLocation()) > 25) {
                    continue;
                }
                Estado ea = estado(a);
                Estado eb = estado(b);
                if (ea.proximaFala - agora > 20_000 || eb.proximaFala - agora > 20_000) {
                    continue;
                }
                String[] par = sortear(conversas).split("\\|", 2);
                Movimento.olhar(ma, mb.getEyeLocation());
                Movimento.olhar(mb, ma.getEyeLocation());
                falar(a, ma, par[0].trim(), null);
                if (par.length > 1) {
                    String resposta = par[1].trim();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Mannequin mb2 = corpo(b);
                        if (mb2 != null) {
                            falar(b, mb2, resposta, null);
                        }
                    }, 60L);
                }
                ea.proximaFala = agora + 40_000;
                eb.proximaFala = agora + 40_000;
                ea.pausaAte = Math.max(ea.pausaAte, agora + 8_000);
                eb.pausaAte = Math.max(eb.pausaAte, agora + 8_000);
                return;
            }
        }
    }

    private static Player maisPerto(Mannequin m, double raio) {
        Player melhor = null;
        double menor = raio * raio;
        for (Player p : m.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double d = p.getLocation().distanceSquared(m.getLocation());
            if (d < menor) {
                menor = d;
                melhor = p;
            }
        }
        return melhor;
    }

    private static String sortear(List<String> l) {
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    // ------------------------------------------------------------------ ANDAR (a cada 2 ticks)

    private void mover() {
        long agora = System.currentTimeMillis();
        double velocidade = cfg("velocidade", 0.11);
        for (Map.Entry<String, Estado> en : estados.entrySet()) {
            Estado e = en.getValue();
            NpcVivo npc = plugin.npcs().obter(en.getKey());
            Mannequin m = npc == null ? null : corpo(npc);
            if (m == null) {
                tirarBalao(e);
                continue;
            }
            if (e.balao != null) {
                if (agora > e.balaoAte || !e.balao.isValid()) {
                    tirarBalao(e);
                } else {
                    e.balao.teleport(m.getLocation().add(0, m.getHeight() + 0.45, 0));
                }
            }
            if (e.destino == null) {
                continue;
            }
            if (e.ocupadoAte > agora || !e.destino.getWorld().equals(m.getWorld())) {
                e.destino = null;
                continue;
            }
            Vector falta = e.destino.toVector().subtract(m.getLocation().toVector()).setY(0);
            if (falta.lengthSquared() < 0.36) {
                chegou(npc, e, agora);
                continue;
            }
            if (!Movimento.passo(m, e.destino, velocidade, 3)) {
                if (++e.travado > 40) {
                    chegou(npc, e, agora); // não dá para chegar lá (muro, buraco): desiste e escolhe outro lugar
                }
            } else {
                e.travado = 0;
            }
        }
    }

    private void chegou(NpcVivo npc, Estado e, long agora) {
        e.destino = null;
        Rotina r = npc.rotina();
        boolean patrulha = r != null && r.tipo() == Rotina.Tipo.PATRULHA;
        e.pausaAte = agora + (long) (patrulha ? ThreadLocalRandom.current().nextDouble(1.5, 4) * 1000
                : ThreadLocalRandom.current().nextDouble(cfg("pausa-minima-segundos", 4), cfg("pausa-maxima-segundos", 12)) * 1000);
    }

    // ------------------------------------------------------------------ BALÃO DE FALA

    /** Mostra uma fala num balão em cima da cabeça do NPC por alguns segundos. */
    public void falar(NpcVivo npc, Mannequin m, String texto, Player jogador) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        Estado e = estado(npc);
        tirarBalao(e);
        String t = Texto.substituir(texto, "jogador", jogador == null ? "viajante" : jogador.getName(), "npc", Texto.semCor(npc.nome()));
        e.balao = m.getWorld().spawn(m.getLocation().add(0, m.getHeight() + 0.45, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Texto.cor("<white>" + t));
            d.setBillboard(Display.Billboard.CENTER);
            d.setLineWidth(160);
            d.setBackgroundColor(Color.fromARGB(150, 20, 20, 20));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setViewRange(0.35f); // ~ 20 blocos
            d.setTeleportDuration(2);
        });
        e.balaoAte = System.currentTimeMillis() + (long) (cfg("balao-segundos", 4.5) * 1000);
    }

    private static void tirarBalao(Estado e) {
        if (e.balao != null) {
            e.balao.remove();
            e.balao = null;
        }
    }
}
