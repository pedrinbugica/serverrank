package dev.pedrobugica.npcvivos.reino;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.mundo.Guerra;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Os exércitos das guerras entre reinos. Enquanto ninguém está olhando, o exército "marcha no mapa" (só a posição anda,
 * sem pesar nada). Quando um jogador chega perto, os soldados aparecem de verdade e seguem marchando pelo terreno.
 * Chegando no alvo começa o CERCO: com jogadores perto é luta de verdade (os atacantes precisam segurar o pátio sem
 * defensores por perto); sem ninguém, a batalha é decidida pela força de cada lado. Vencer toma o castelo/vila.
 */
public final class Campanhas {

    private enum Fase {
        MARCHANDO, CERCO
    }

    private final class Exercito {
        final String id;
        final String reino;
        final String alvo;
        final String mundo;
        final double origemX;
        final double origemZ;
        final double destinoX;
        final double destinoZ;
        final double distanciaTotal;
        double x;
        double z;
        int forca;
        boolean capitao = true;
        boolean materializado;
        final List<UUID> soldados = new ArrayList<>();
        final List<UUID> defensores = new ArrayList<>();
        Fase fase = Fase.MARCHANDO;
        long inicio = System.currentTimeMillis();
        long cercoInicio;
        int captura;
        boolean cercoFisico;
        double melhorDistancia = Double.MAX_VALUE;
        long melhorEm = System.currentTimeMillis();
        BossBar barra;
        final Set<UUID> vendoBarra = new HashSet<>();

        Exercito(String id, String reino, Territorio alvo, Location origem, int forca) {
            this.id = id;
            this.reino = reino;
            this.alvo = alvo.id();
            this.mundo = alvo.mundo();
            this.origemX = origem.getX();
            this.origemZ = origem.getZ();
            this.destinoX = alvo.x() + 0.5;
            this.destinoZ = alvo.z() + 0.5;
            this.x = origemX;
            this.z = origemZ;
            this.forca = forca;
            this.distanciaTotal = Math.max(1, Math.hypot(destinoX - origemX, destinoZ - origemZ));
        }

        World w() {
            return Bukkit.getWorld(mundo);
        }

        double distancia() {
            return Math.hypot(destinoX - x, destinoZ - z);
        }
    }

    private final NpcVivosPlugin plugin;
    private final ReinoService reinos;
    private final Map<String, Exercito> exercitos = new LinkedHashMap<>();
    private final Map<String, Long> proximaCampanha = new HashMap<>();
    private int contador;
    private BukkitTask tarefa;

    Campanhas(NpcVivosPlugin plugin, ReinoService reinos) {
        this.plugin = plugin;
        this.reinos = reinos;
    }

    private double cfg(String k, double padrao) {
        return reinos.cfg().getDouble("guerra." + k, padrao);
    }

    void iniciar() {
        parar();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (Exercito e : List.copyOf(exercitos.values())) {
            sumir(e.soldados);
            sumir(e.defensores);
            esconderBarra(e);
        }
        exercitos.clear();
    }

    // ------------------------------------------------------------------ CONSULTAS

    public boolean emCerco(Territorio t) {
        for (Exercito e : exercitos.values()) {
            if (e.alvo.equals(t.id()) && e.fase == Fase.CERCO && e.cercoFisico) {
                return true;
            }
        }
        return false;
    }

    public List<String> noticias(Location onde) {
        List<String> l = new ArrayList<>();
        for (Exercito e : exercitos.values()) {
            Reino r = reinos.obter(e.reino);
            Territorio t = reinos.territorio(e.alvo);
            if (r == null || t == null) {
                continue;
            }
            String perto = onde != null && onde.getWorld().getName().equals(e.mundo)
                    ? " (a uns " + Math.round(Math.hypot(onde.getX() - e.x, onde.getZ() - e.z) / 10) * 10 + " blocos daqui)" : "";
            l.add(e.fase == Fase.CERCO ? "O exército de " + r.curto() + " está cercando " + Texto.semCor(t.nome()) + "!"
                    : "O exército de " + r.curto() + " marcha contra " + Texto.semCor(t.nome()) + perto + ".");
        }
        return l;
    }

    public List<String> resumo() {
        List<String> l = new ArrayList<>();
        for (Exercito e : exercitos.values()) {
            Reino r = reinos.obter(e.reino);
            Territorio t = reinos.territorio(e.alvo);
            if (r == null || t == null) {
                continue;
            }
            l.add(r.cor() + "⚔ Exército de " + r.curto() + " <gray>(" + e.forca + " soldados) " + (e.fase == Fase.CERCO ? "<red>CERCANDO " : "<gray>→ ")
                    + t.nome() + " <dark_gray>[" + Math.round(e.x) + ", " + Math.round(e.z) + "]"
                    + (e.fase == Fase.MARCHANDO ? " <gray>~" + Math.round(e.distancia()) + " blocos do alvo" : ""));
        }
        return l;
    }

    public int quantidade() {
        return exercitos.size();
    }

    // ------------------------------------------------------------------ LANÇAR

    /** Manda um exército do reino contra um castelo/vila. Devolve null se deu certo, ou o motivo. */
    public String lancar(Reino r, Territorio alvo, int tamanho) {
        if (alvo.dono() == null || alvo.dono().equals(r.id())) {
            return "Esse lugar já é de " + r.curto() + " (ou não tem dono).";
        }
        for (Exercito e : exercitos.values()) {
            if (e.reino.equals(r.id())) {
                return r.curto() + " já tem um exército em campanha.";
            }
        }
        World w = Bukkit.getWorld(alvo.mundo());
        if (w == null) {
            return "Mundo " + alvo.mundo() + " não está carregado.";
        }
        Location origem = null;
        double melhor = Double.MAX_VALUE;
        for (Territorio c : reinos.castelosDe(r.id())) {
            Location cc = c.centro();
            if (cc != null && cc.getWorld().equals(w)) {
                double d = alvo.distancia(cc);
                if (d < melhor) {
                    melhor = d;
                    origem = cc;
                }
            }
        }
        if (origem == null) {
            // sem castelo: exército rebelde que sai das terras selvagens
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            origem = new Location(w, alvo.x() + Math.cos(ang) * 400, alvo.y(), alvo.z() + Math.sin(ang) * 400);
        }
        Exercito e = new Exercito("ex" + (++contador), r.id(), alvo, origem, Math.max(2, tamanho));
        exercitos.put(e.id, e);
        e.barra = BossBar.bossBar(Texto.cor(""), 0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        Reino defensor = reinos.obter(alvo.dono());
        long minutos = Math.max(1, Math.round(e.distanciaTotal / cfg("velocidade-marcha", 1.6) / 60));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Texto.cor("<dark_red>⚔ " + r.nome() + " <gray>mandou um exército de <white>" + e.forca + " soldados <gray>contra "
                    + alvo.nome() + (defensor != null ? " <gray>(" + defensor.nome() + "<gray>)" : "") + "<gray>! Chegada em ~<white>"
                    + minutos + " min<gray>. <dark_gray>[" + alvo.x() + ", " + alvo.z() + "]"));
            Faccao m = plugin.faccoes().membro(p);
            if (m != null && defensor != null && m.id().equals(defensor.id())) {
                p.showTitle(Title.title(Texto.cor("<red><bold>⚔ INVASÃO A CAMINHO"), Texto.cor(r.nome() + " <gray>vai atacar " + alvo.nome()),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1f);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ A CADA SEGUNDO

    private void tick() {
        if (!reinos.ativo()) {
            return;
        }
        lancarSozinho();
        for (Exercito e : List.copyOf(exercitos.values())) {
            try {
                atualizar(e);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Exército " + e.id + " deu erro e foi desfeito: " + ex);
                encerrar(e);
            }
        }
    }

    /** Em cada guerra entre reinos, cada lado manda um exército de tempos em tempos. */
    private void lancarSozinho() {
        long agora = System.currentTimeMillis();
        long intervalo = (long) (cfg("campanha-a-cada-minutos", 12) * 60_000L);
        for (Guerra g : plugin.guerras().ativas()) {
            for (String lado : new String[]{g.a(), g.b()}) {
                Reino r = reinos.obter(lado);
                Reino inimigo = reinos.obter(g.inimiga(lado));
                if (r == null || inimigo == null) {
                    continue;
                }
                long quando = proximaCampanha.computeIfAbsent(lado, k -> agora + (long) (intervalo * ThreadLocalRandom.current().nextDouble(0.3, 1.0)));
                if (agora < quando) {
                    continue;
                }
                proximaCampanha.put(lado, agora + (long) (intervalo * ThreadLocalRandom.current().nextDouble(0.7, 1.3)));
                List<Territorio> alvos = new ArrayList<>();
                for (Territorio t : reinos.territorios()) {
                    if (inimigo.id().equals(t.dono())) {
                        alvos.add(t);
                        if (t.tipo() == Territorio.Tipo.CASTELO) {
                            alvos.add(t); // castelos têm mais chance
                        }
                    }
                }
                if (!alvos.isEmpty()) {
                    lancar(r, alvos.get(ThreadLocalRandom.current().nextInt(alvos.size())), (int) cfg("exercito-tamanho", 10));
                }
            }
        }
    }

    private void atualizar(Exercito e) {
        World w = e.w();
        Territorio alvo = reinos.territorio(e.alvo);
        Reino r = reinos.obter(e.reino);
        if (w == null || alvo == null || r == null) {
            encerrar(e);
            return;
        }
        if (e.reino.equals(alvo.dono()) || !reinos.emGuerra(e.reino, alvo.dono())) {
            // o alvo já é nosso, ou a guerra acabou: volta para casa
            avisarPerto(e, "<gray>O exército de " + r.curto() + " voltou para casa.");
            encerrar(e);
            return;
        }
        if (e.materializado) {
            e.soldados.removeIf(u -> {
                Entity en = Bukkit.getEntity(u);
                return en == null || !en.isValid() || en.isDead();
            });
            e.forca = e.soldados.size();
            if (e.forca <= 0) {
                derrota(e, alvo, true);
                return;
            }
            Location c = centro(e);
            e.x = c.getX();
            e.z = c.getZ();
        }
        Location destino = new Location(w, e.destinoX, alvo.y(), e.destinoZ);
        double raioMat = cfg("raio-materializar", 96);
        if (e.fase == Fase.MARCHANDO) {
            if (e.distancia() <= 40) {
                e.fase = Fase.CERCO;
                e.cercoInicio = System.currentTimeMillis();
                avisarPerto(e, "<red>⚔ O exército de " + r.curto() + " chegou a " + alvo.nome() + "<red>! O cerco começou!");
            } else if (!e.materializado) {
                double passo = cfg("velocidade-marcha", 1.6);
                double d = e.distancia();
                e.x += (e.destinoX - e.x) / d * passo;
                e.z += (e.destinoZ - e.z) / d * passo;
                Location aqui = new Location(w, e.x, alvo.y(), e.z);
                if (w.isChunkLoaded((int) Math.floor(e.x) >> 4, (int) Math.floor(e.z) >> 4) && jogadorPerto(aqui, raioMat)) {
                    materializar(e, aqui, r);
                }
            } else {
                Location c = new Location(w, e.x, alvo.y(), e.z);
                if (!jogadorPerto(c, raioMat + 64)) {
                    desmaterializar(e);
                } else {
                    marchar(e, c, destino);
                }
            }
        }
        if (e.fase == Fase.CERCO) {
            cerco(e, alvo, r, destino);
        }
        atualizarBarra(e, alvo, r);
    }

    private void marchar(Exercito e, Location centro, Location destino) {
        Vector dir = destino.toVector().subtract(centro.toVector()).setY(0);
        if (dir.lengthSquared() < 1) {
            return;
        }
        dir.normalize();
        Location ponto = centro.clone().add(dir.clone().multiply(18));
        World w = centro.getWorld();
        Location chao = w.isChunkLoaded(ponto.getBlockX() >> 4, ponto.getBlockZ() >> 4)
                ? ReinoService.chao(w, ponto.getBlockX(), ponto.getBlockZ(), w.getHighestBlockYAt(ponto.getBlockX(), ponto.getBlockZ()) + 1)
                : null;
        double dist = e.distancia();
        long agora = System.currentTimeMillis();
        if (dist < e.melhorDistancia - 2) {
            e.melhorDistancia = dist;
            e.melhorEm = agora;
        } else if (agora - e.melhorEm > 30_000 && chao != null) {
            // empacou (rio, montanha): o exército "dá a volta" e aparece mais à frente
            for (UUID u : e.soldados) {
                Entity en = Bukkit.getEntity(u);
                if (en != null) {
                    en.teleport(chao.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2)));
                }
            }
            e.melhorEm = agora;
            return;
        }
        for (UUID u : e.soldados) {
            if (!(Bukkit.getEntity(u) instanceof AbstractSkeleton s) || s.getTarget() != null) {
                continue;
            }
            if (s.getLocation().distanceSquared(centro) > 30 * 30) {
                s.teleport(centro);
            } else if (chao != null) {
                s.getPathfinder().moveTo(chao, 1.0);
            }
        }
    }

    private void cerco(Exercito e, Territorio alvo, Reino r, Location destino) {
        World w = destino.getWorld();
        boolean fisico = alvo.carregado() && jogadorPerto(destino, 128);
        long agora = System.currentTimeMillis();
        if (!fisico) {
            if (e.materializado) {
                desmaterializar(e);
            }
            e.cercoFisico = false;
            if (agora - e.cercoInicio > 45_000) {
                // ninguém olhando: decide pela força
                double def = alvo.tipo() == Territorio.Tipo.CASTELO ? reinos.cfg().getInt("guarnicao.soldados", 8) * 1.5
                        : reinos.cfg().getInt("guarnicao.soldados-por-vila", 3) + 2.0;
                double chance = e.forca / (e.forca + def);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    vitoria(e, alvo, r);
                } else {
                    derrota(e, alvo, false);
                }
            }
            return;
        }
        Location centroAlvo = alvo.centro();
        if (!e.materializado) {
            Vector dir = new Vector(e.origemX - e.destinoX, 0, e.origemZ - e.destinoZ);
            if (dir.lengthSquared() < 1) {
                dir = new Vector(1, 0, 0);
            }
            dir.normalize().multiply(alvo.raio() + 16);
            Location chegada = centroAlvo.clone().add(dir);
            materializar(e, chegada, r);
            if (!e.materializado) {
                return;
            }
        }
        if (!e.cercoFisico) {
            e.cercoFisico = true;
            e.cercoInicio = agora;
            reinos.completarGuarnicao(alvo);
            Reino dono = reinos.obter(alvo.dono());
            if (dono != null) {
                int extra = reinos.cfg().getInt("guerra.defensores-extra", 4);
                for (int i = 0; i < extra; i++) {
                    double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location l = ReinoService.chao(w, (int) (centroAlvo.getX() + Math.cos(ang) * 5), (int) (centroAlvo.getZ() + Math.sin(ang) * 5),
                            centroAlvo.getBlockY());
                    if (l != null) {
                        LivingEntity d = reinos.criarSoldado(l, dono, ThreadLocalRandom.current().nextBoolean() ? "arco" : "espada", false,
                                pdc -> pdc.set(reinos.kExercito, PersistentDataType.STRING, "defesa-" + e.id));
                        e.defensores.add(d.getUniqueId());
                    }
                }
            }
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(destino) < 200 * 200) {
                    p.showTitle(Title.title(Texto.cor("<red><bold>⚔ CERCO!"), Texto.cor(r.nome() + " <gray>ataca " + alvo.nome()),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
                    p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.9f);
                }
            }
        }
        // atacantes sem alvo marcham para o pátio
        int noPatio = 0;
        for (UUID u : e.soldados) {
            if (!(Bukkit.getEntity(u) instanceof AbstractSkeleton s)) {
                continue;
            }
            double d2 = s.getLocation().distanceSquared(centroAlvo);
            if (d2 <= 8 * 8) {
                noPatio++;
            }
            if (s.getTarget() == null) {
                s.getPathfinder().moveTo(centroAlvo, 1.0);
            }
        }
        int defensores = 0;
        for (Entity en : w.getNearbyEntities(centroAlvo, 14, 10, 14)) {
            if (reinos.ehSoldado(en) && alvo.dono().equals(reinos.reinoDoSoldado(en)) && !en.isDead()) {
                defensores++;
            } else if (en instanceof Player p) {
                Faccao m = plugin.faccoes().membro(p);
                if (m != null && m.id().equals(alvo.dono()) && !p.isDead()) {
                    defensores++;
                }
            }
        }
        if (noPatio > 0 && defensores == 0) {
            e.captura++;
            w.spawnParticle(Particle.DUST, centroAlvo.clone().add(0, 1.5, 0), 12, 2, 1, 2, 0,
                    new Particle.DustOptions(r.corCouro(), 1.6f));
        } else if (defensores > 0) {
            e.captura = Math.max(0, e.captura - 2);
        }
        if (e.captura >= (int) cfg("captura-segundos", 45)) {
            vitoria(e, alvo, r);
        } else if (agora - e.cercoInicio > cfg("tempo-cerco-minutos", 12) * 60_000L) {
            derrota(e, alvo, false);
        }
    }

    // ------------------------------------------------------------------ FIM

    private void vitoria(Exercito e, Territorio alvo, Reino r) {
        String antigo = alvo.dono();
        Reino perdedor = reinos.obter(antigo);
        reinos.definirDono(alvo, r.id());
        int pontos = reinos.cfg().getInt("guerra.pontos." + (alvo.tipo() == Territorio.Tipo.CASTELO ? "castelo" : "vila"), 200);
        // quem sobreviveu vira a nova guarnição
        for (UUID u : e.soldados) {
            Entity en = Bukkit.getEntity(u);
            if (en instanceof LivingEntity s && s.isValid()) {
                s.getPersistentDataContainer().remove(reinos.kExercito);
                s.getPersistentDataContainer().set(reinos.kTerritorio, PersistentDataType.STRING, alvo.id());
                Location l = s.getLocation();
                s.getPersistentDataContainer().set(reinos.kPosto, PersistentDataType.STRING, l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                s.setPersistent(true);
            }
        }
        e.soldados.clear();
        Title titulo = Title.title(Texto.cor(r.cor() + "<bold>🏰 " + Texto.semCor(alvo.nome()).toUpperCase() + " CAIU"),
                Texto.cor("<gray>Agora pertence ao " + r.nome()), Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(800)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Texto.cor("<gold>🏰 " + r.nome() + " <gold>conquistou " + alvo.nome() + "<gold>"
                    + (perdedor != null ? " de " + perdedor.nome() : "") + "<gold>!"));
            if (p.getWorld().getName().equals(alvo.mundo())) {
                p.showTitle(titulo);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.8f);
            }
        }
        if (antigo != null) {
            plugin.guerras().pontuar(r.id(), antigo, pontos, null);
            // sem nenhum castelo, o reino derrotado se rende
            Guerra g = plugin.guerras().entre(r.id(), antigo);
            if (g != null && alvo.tipo() == Territorio.Tipo.CASTELO && reinos.castelosDe(antigo).isEmpty()) {
                plugin.guerras().terminar(g);
            }
        }
        encerrar(e);
    }

    private void derrota(Exercito e, Territorio alvo, boolean destruido) {
        Reino r = reinos.obter(e.reino);
        Reino dono = reinos.obter(alvo.dono());
        String nomeR = r == null ? e.reino : r.nome();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Texto.cor(destruido
                    ? "<green>🛡 O exército de " + nomeR + " <green>foi destruído antes de tomar " + alvo.nome() + "<green>!"
                    : "<green>🛡 " + alvo.nome() + " <green>resistiu ao ataque de " + nomeR + "<green>!"));
        }
        if (dono != null) {
            plugin.guerras().pontuar(dono.id(), e.reino, reinos.cfg().getInt("guerra.pontos.defesa", 80), null);
        }
        encerrar(e);
    }

    private void encerrar(Exercito e) {
        exercitos.remove(e.id);
        sumir(e.soldados);
        sumir(e.defensores);
        esconderBarra(e);
    }

    void aoMorrerSoldado(LivingEntity s) {
        String ex = s.getPersistentDataContainer().get(reinos.kExercito, PersistentDataType.STRING);
        if (ex == null) {
            return;
        }
        Exercito e = exercitos.get(ex);
        if (e != null && e.soldados.remove(s.getUniqueId())) {
            e.forca = Math.max(0, e.forca - 1);
            if ("capitao".equals(s.getPersistentDataContainer().get(reinos.kTipo, PersistentDataType.STRING))) {
                e.capitao = false;
            }
        }
    }

    // ------------------------------------------------------------------ SOLDADOS DE VERDADE

    private void materializar(Exercito e, Location onde, Reino r) {
        World w = onde.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int criados = 0;
        for (int i = 0; i < e.forca; i++) {
            double ang = rnd.nextDouble(Math.PI * 2);
            double dist = rnd.nextDouble(0, 5);
            int bx = (int) Math.floor(onde.getX() + Math.cos(ang) * dist);
            int bz = (int) Math.floor(onde.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue; // nunca carrega chunk na marra
            }
            Location l = ReinoService.chao(w, bx, bz, w.getHighestBlockYAt(bx, bz) + 1);
            if (l == null) {
                continue;
            }
            String tipo = i == 0 && e.capitao ? "capitao" : rnd.nextDouble() < 0.3 ? "arco" : "espada";
            LivingEntity s = reinos.criarSoldado(l, r, tipo, false, pdc -> pdc.set(reinos.kExercito, PersistentDataType.STRING, e.id));
            e.soldados.add(s.getUniqueId());
            criados++;
        }
        if (criados > 0) {
            e.materializado = true;
            e.forca = criados;
            e.melhorDistancia = e.distancia();
            e.melhorEm = System.currentTimeMillis();
            w.playSound(onde, Sound.EVENT_RAID_HORN, 2f, 1.1f);
        }
    }

    private void desmaterializar(Exercito e) {
        Location c = centro(e);
        e.x = c.getX();
        e.z = c.getZ();
        e.forca = e.soldados.size();
        sumir(e.soldados);
        e.materializado = false;
    }

    private Location centro(Exercito e) {
        double sx = 0;
        double sz = 0;
        int n = 0;
        for (UUID u : e.soldados) {
            Entity en = Bukkit.getEntity(u);
            if (en != null) {
                sx += en.getLocation().getX();
                sz += en.getLocation().getZ();
                n++;
            }
        }
        World w = e.w();
        if (n == 0) {
            return new Location(w, e.x, 64, e.z);
        }
        int bx = (int) Math.floor(sx / n);
        int bz = (int) Math.floor(sz / n);
        int y = w.isChunkLoaded(bx >> 4, bz >> 4) ? w.getHighestBlockYAt(bx, bz) + 1 : 64;
        return new Location(w, sx / n, y, sz / n);
    }

    private void sumir(List<UUID> l) {
        for (UUID u : l) {
            Entity en = Bukkit.getEntity(u);
            if (en != null && en.isValid()) {
                en.getWorld().spawnParticle(Particle.POOF, en.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.02);
                en.remove();
            }
        }
        l.clear();
    }

    // ------------------------------------------------------------------ BARRA E AVISOS

    private boolean jogadorPerto(Location l, double raio) {
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= raio * raio) {
                return true;
            }
        }
        return false;
    }

    private void avisarPerto(Exercito e, String msg) {
        World w = e.w();
        if (w == null) {
            return;
        }
        for (Player p : w.getPlayers()) {
            if (Math.hypot(p.getLocation().getX() - e.destinoX, p.getLocation().getZ() - e.destinoZ) < 250
                    || Math.hypot(p.getLocation().getX() - e.x, p.getLocation().getZ() - e.z) < 250) {
                p.sendMessage(Texto.cor(msg));
            }
        }
    }

    private void atualizarBarra(Exercito e, Territorio alvo, Reino r) {
        if (e.barra == null) {
            return;
        }
        if (e.fase == Fase.CERCO && e.cercoFisico) {
            int max = (int) cfg("captura-segundos", 45);
            e.barra.name(Texto.cor("<red>🏰 Cerco a " + Texto.semCor(alvo.nome()) + " <gray>| " + r.cor() + r.curto()
                    + " <gray>tomando o pátio: <white>" + e.captura + "/" + max + " <gray>| atacantes: <white>" + e.forca));
            e.barra.progress(Math.max(0f, Math.min(1f, e.captura / (float) max)));
        } else {
            e.barra.name(Texto.cor(r.cor() + "⚔ Exército de " + r.curto() + " <gray>→ " + Texto.semCor(alvo.nome()) + " <gray>| <white>"
                    + e.forca + " soldados <gray>| <white>" + Math.round(e.distancia()) + " <gray>blocos"));
            e.barra.progress((float) Math.max(0, Math.min(1, 1 - e.distancia() / e.distanciaTotal)));
        }
        World w = e.w();
        Set<UUID> agora = new HashSet<>();
        for (Player p : w.getPlayers()) {
            double a = Math.hypot(p.getLocation().getX() - e.x, p.getLocation().getZ() - e.z);
            double b = Math.hypot(p.getLocation().getX() - e.destinoX, p.getLocation().getZ() - e.destinoZ);
            if (a < 160 || b < 160) {
                agora.add(p.getUniqueId());
                if (e.vendoBarra.add(p.getUniqueId())) {
                    p.showBossBar(e.barra);
                }
            }
        }
        for (UUID u : List.copyOf(e.vendoBarra)) {
            if (!agora.contains(u)) {
                e.vendoBarra.remove(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.hideBossBar(e.barra);
                }
            }
        }
    }

    private void esconderBarra(Exercito e) {
        if (e.barra == null) {
            return;
        }
        for (UUID u : e.vendoBarra) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.hideBossBar(e.barra);
            }
        }
        e.vendoBarra.clear();
    }
}
