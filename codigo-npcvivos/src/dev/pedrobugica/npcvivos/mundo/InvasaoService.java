package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.comportamento.Cerebro;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invasões (fase 7): uma facção em guerra manda ondas de soldados contra a vila da outra e os jogadores defendem.
 * Os soldados são NPCs temporários (não vão para o disco) com o "posto" no centro da vila: o mesmo cérebro dos NPCs
 * de combate faz eles marcharem até lá e atacarem quem estiver perto (menos os membros da própria facção).
 * Soldado que chega no centro saqueia a vila; saques demais e a invasão vence.
 */
public final class InvasaoService {

    private static final class Invasao {
        String atacante;
        String defensor;
        Location centro;
        int onda;
        int ondas;
        int saques;
        int totalDaOnda;
        long inicio;
        long proximaOnda;
        final List<NpcVivo> soldados = new ArrayList<>();
        final Set<UUID> defensores = new LinkedHashSet<>();
        final List<long[]> chunks = new ArrayList<>();
        /** soldado -> {menor distância até o centro, quando chegou nela}: para destravar quem empacou no terreno */
        final Map<String, double[]> progresso = new HashMap<>();
        BossBar barra;
    }

    private final NpcVivosPlugin plugin;
    private Invasao atual;
    private int contador;

    public InvasaoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private MundoVivoService cfg() {
        return plugin.mundo();
    }

    public boolean acontecendo() {
        return atual != null;
    }

    /** "Irmandade do Sul ataca Vila Aurora: onda 2/3, 4 soldados, 1 saque" (para comandos). */
    public String descricao() {
        Invasao i = atual;
        if (i == null) {
            return null;
        }
        return nome(i.atacante) + " <gray>ataca " + nome(i.defensor) + "<gray>: onda <white>" + i.onda + "/" + i.ondas
                + "<gray>, <white>" + i.soldados.size() + " <gray>soldado(s), <white>" + i.saques + " <gray>saque(s)";
    }

    public boolean ehSoldado(NpcVivo npc) {
        return atual != null && atual.soldados.contains(npc);
    }

    // ------------------------------------------------------------------ COMEÇO

    /** Centro da vila de uma facção: a média dos NPCs dela no mundo onde ela tem mais NPCs. */
    public Location centroVila(String faccao) {
        Map<String, List<NpcVivo>> porMundo = new HashMap<>();
        for (NpcVivo n : plugin.npcs().todos()) {
            if (!n.temporario() && faccao.equals(n.faccao()) && n.local() != null) {
                porMundo.computeIfAbsent(n.mundo(), k -> new ArrayList<>()).add(n);
            }
        }
        List<NpcVivo> melhor = null;
        for (List<NpcVivo> l : porMundo.values()) {
            if (melhor == null || l.size() > melhor.size()) {
                melhor = l;
            }
        }
        if (melhor == null) {
            return null;
        }
        double x = 0;
        double z = 0;
        for (NpcVivo n : melhor) {
            x += n.x();
            z += n.z();
        }
        World w = melhor.get(0).local().getWorld();
        x /= melhor.size();
        z /= melhor.size();
        int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(w, x, y, z);
    }

    /** Começa uma invasão. Devolve null se deu certo, ou a chave da mensagem de erro. */
    public String iniciar(Faccao atacante, Faccao defensor) {
        if (atual != null) {
            return "invasao-ja-acontecendo";
        }
        if (atacante == null || defensor == null || atacante.id().equals(defensor.id())) {
            return "invasao-faccoes-invalidas";
        }
        Location centro = centroVila(defensor.id());
        if (centro == null) {
            return "invasao-sem-vila";
        }
        Invasao i = new Invasao();
        i.atacante = atacante.id();
        i.defensor = defensor.id();
        i.centro = centro;
        i.ondas = Math.max(1, cfg().inteiro("invasoes.ondas", 3));
        i.inicio = System.currentTimeMillis();
        atual = i;
        segurarChunks(i);
        World w = centro.getWorld();
        Title titulo = Title.title(Texto.cor(plugin.mensagens().bruta("invasao-titulo")),
                Texto.cor(Texto.substituir(plugin.mensagens().bruta("invasao-subtitulo"), "a", atacante.nome(), "b", defensor.nome())),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(p, "invasao-inicio", "a", atacante.nome(), "b", defensor.nome(), "mundo", w.getName(),
                    "x", String.valueOf(centro.getBlockX()), "z", String.valueOf(centro.getBlockZ()));
            if (p.getWorld().equals(w)) {
                p.showTitle(titulo);
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
        }
        i.barra = BossBar.bossBar(Texto.cor(""), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        novaOnda(i);
        return null;
    }

    private void segurarChunks(Invasao i) {
        World w = i.centro.getWorld();
        int raio = (int) Math.ceil((cfg().num("invasoes.distancia", 20) + 16) / 16.0);
        int cx = i.centro.getBlockX() >> 4;
        int cz = i.centro.getBlockZ() >> 4;
        for (int x = cx - raio; x <= cx + raio; x++) {
            for (int z = cz - raio; z <= cz + raio; z++) {
                w.getChunkAt(x, z);
                w.addPluginChunkTicket(x, z, plugin);
                i.chunks.add(new long[]{x, z});
            }
        }
    }

    private void soltarChunks(Invasao i) {
        World w = i.centro.getWorld();
        for (long[] c : i.chunks) {
            w.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
        }
        i.chunks.clear();
    }

    private int jogadoresPerto(Location centro, double raio) {
        int n = 0;
        for (Player p : centro.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(centro) <= raio * raio) {
                n++;
            }
        }
        return n;
    }

    private void novaOnda(Invasao i) {
        i.onda++;
        int quantos = cfg().inteiro("invasoes.soldados-por-onda", 3)
                + cfg().inteiro("invasoes.soldados-por-jogador", 1) * jogadoresPerto(i.centro, 64);
        quantos = Math.max(1, Math.min(cfg().inteiro("invasoes.maximo-por-onda", 8), quantos));
        i.totalDaOnda = quantos;
        Faccao atacante = plugin.faccoes().obter(i.atacante);
        double distancia = cfg().num("invasoes.distancia", 20);
        List<String> skins = cfg().cfg().getStringList("invasoes.skins");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < quantos; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            World w = i.centro.getWorld();
            double x = i.centro.getX() + Math.cos(ang) * distancia;
            double z = i.centro.getZ() + Math.sin(ang) * distancia;
            int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            Location l = new Location(w, x, y, z);
            l.setDirection(i.centro.toVector().subtract(l.toVector()));
            boolean arqueiro = r.nextDouble() < cfg().num("invasoes.soldado.arqueiros", 0.3);
            NpcVivo s = new NpcVivo("invasor_" + (++contador));
            s.setNome(Texto.substituir(plugin.mensagens().bruta("invasao-nome-soldado"), "faccao",
                    atacante == null ? i.atacante : atacante.nome()));
            s.setSkin(skins.isEmpty() ? "noor" : skins.get(r.nextInt(skins.size())));
            s.setLocal(w.getName(), l.getX(), l.getY(), l.getZ(), l.getYaw());
            s.papeis().add(Papel.INVASOR);
            s.setFaccao(i.atacante);
            s.setItemNaMao(arqueiro ? Material.BOW : Material.IRON_SWORD);
            MemoryConfiguration c = new MemoryConfiguration();
            c.set("vida", cfg().num("invasoes.soldado.vida", 30));
            c.set("dano", cfg().num("invasoes.soldado.dano", 4));
            c.set("armadura", cfg().num("invasoes.soldado.armadura", 2));
            c.set("velocidade", cfg().num("invasoes.soldado.velocidade", 0.24));
            c.set("alcance", arqueiro ? 14.0 : cfg().num("invasoes.soldado.alcance", 3.0));
            c.set("arqueiro", arqueiro);
            c.set("respawn-segundos", 0);
            c.set("drops.dinheiro", cfg().num("invasoes.recompensa-por-soldado", 15));
            s.setCombate(c);
            plugin.npcs().registrarTemporario(s);
            Cerebro cb = plugin.comportamento().cerebro(s);
            if (cb != null) {
                cb.setPosto(i.centro.clone());
                cb.setEstado(EstadoNpc.VOLTANDO_AO_POSTO);
            }
            i.soldados.add(s);
        }
        for (Player p : i.centro.getWorld().getPlayers()) {
            plugin.mensagens().enviar(p, "invasao-onda", "onda", String.valueOf(i.onda), "ondas", String.valueOf(i.ondas),
                    "soldados", String.valueOf(quantos));
        }
    }

    // ------------------------------------------------------------------ A CADA SEGUNDO

    public void tick() {
        Invasao i = atual;
        if (i == null) {
            return;
        }
        double raioSaque = cfg().num("invasoes.raio-do-saque", 3.5);
        for (NpcVivo s : List.copyOf(i.soldados)) {
            Cerebro c = plugin.comportamento().cerebroExistente(s.id());
            if (c == null || c.morto()) {
                continue; // a morte é tratada em aoMorrer
            }
            Mannequin corpo = plugin.comportamento().corpo(s);
            if (corpo == null || !corpo.getWorld().equals(i.centro.getWorld())) {
                continue;
            }
            if (corpo.getLocation().distanceSquared(i.centro) <= raioSaque * raioSaque) {
                saquear(i, s);
            } else if (c.alvo() == null) {
                destravar(i, s, corpo);
            }
        }
        if (atual == null) {
            return;
        }
        long agora = System.currentTimeMillis();
        if (i.saques >= Math.max(1, cfg().inteiro("invasoes.saques-para-perder", 3))) {
            fim(i, false);
            return;
        }
        if (agora - i.inicio > cfg().num("invasoes.tempo-maximo-minutos", 10) * 60_000L) {
            fim(i, true); // o tempo acabou sem saques suficientes: os invasores desistem e a vila resiste
            return;
        }
        if (i.soldados.isEmpty()) {
            if (i.onda >= i.ondas) {
                fim(i, true);
                return;
            }
            if (i.proximaOnda == 0) {
                i.proximaOnda = agora + 5000;
            } else if (agora >= i.proximaOnda) {
                i.proximaOnda = 0;
                novaOnda(i);
            }
        }
        atualizarBarra(i);
    }

    /**
     * O manequim anda sozinho, mas não pula morro nem árvore. Soldado sem alvo que passou 10 segundos sem chegar
     * mais perto do centro avança uns blocos por cima do obstáculo (como quem escala), para a invasão não travar.
     */
    private void destravar(Invasao i, NpcVivo s, Mannequin corpo) {
        double dist = corpo.getLocation().distance(i.centro);
        long agora = System.currentTimeMillis();
        double[] pr = i.progresso.get(s.id());
        if (pr == null || dist < pr[0] - 0.5) {
            i.progresso.put(s.id(), new double[]{dist, agora});
            return;
        }
        if (agora - (long) pr[1] < 10_000L) {
            return;
        }
        org.bukkit.util.Vector dir = i.centro.toVector().subtract(corpo.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        Location frente = corpo.getLocation().add(dir.normalize().multiply(Math.min(4, dist)));
        World w = frente.getWorld();
        frente.setY(w.getHighestBlockYAt(frente.getBlockX(), frente.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        corpo.teleport(frente);
        i.progresso.put(s.id(), new double[]{frente.distance(i.centro), agora});
    }

    private void saquear(Invasao i, NpcVivo s) {
        i.soldados.remove(s);
        remover(s);
        i.saques++;
        int limite = Math.max(1, cfg().inteiro("invasoes.saques-para-perder", 3));
        for (Player p : i.centro.getWorld().getPlayers()) {
            plugin.mensagens().enviar(p, "invasao-saque", "b", nome(i.defensor), "saques", String.valueOf(i.saques),
                    "limite", String.valueOf(limite));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.8f, 0.8f);
        }
    }

    private void atualizarBarra(Invasao i) {
        if (i.barra == null) {
            return;
        }
        i.barra.name(Texto.cor(plugin.mensagens().bruta("invasao-barra"), "a", nome(i.atacante), "b", nome(i.defensor),
                "onda", String.valueOf(i.onda), "ondas", String.valueOf(i.ondas), "soldados", String.valueOf(i.soldados.size()),
                "saques", String.valueOf(i.saques)));
        i.barra.progress((float) Math.max(0, Math.min(1, i.soldados.size() / (double) Math.max(1, i.totalDaOnda))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(i.centro.getWorld()) && p.getLocation().distanceSquared(i.centro) <= 96 * 96) {
                p.showBossBar(i.barra);
            } else {
                p.hideBossBar(i.barra);
            }
        }
    }

    // ------------------------------------------------------------------ MORTE DOS SOLDADOS

    /** Chamado pelo CombateService quando um NPC de combate morre. */
    public void aoMorrer(NpcVivo npc, Player autor) {
        Invasao i = atual;
        if (i == null || !i.soldados.remove(npc)) {
            return;
        }
        // O corpo já sumiu; tira do mapa no tick seguinte (o CombateService ainda está usando o NPC)
        Bukkit.getScheduler().runTask(plugin, () -> remover(npc));
        if (autor == null) {
            return;
        }
        i.defensores.add(autor.getUniqueId());
        Faccao defensor = plugin.faccoes().obter(i.defensor);
        int rep = cfg().inteiro("invasoes.reputacao-por-soldado", 3);
        if (defensor != null && rep > 0) {
            plugin.faccoes().alterarReputacao(autor, defensor, rep);
        }
        plugin.guerras().pontuar(i.defensor, i.atacante, cfg().inteiro("guerras.pontos.invasor-morto", 5), autor);
    }

    private void remover(NpcVivo s) {
        plugin.npcs().removerTemporario(s);
        plugin.comportamento().esquecer(s.id());
    }

    // ------------------------------------------------------------------ FIM

    private void fim(Invasao i, boolean defendida) {
        atual = null;
        for (NpcVivo s : List.copyOf(i.soldados)) {
            remover(s);
        }
        i.soldados.clear();
        soltarChunks(i);
        if (i.barra != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(i.barra);
            }
        }
        if (defendida) {
            double premio = cfg().num("invasoes.recompensa-vitoria", 100);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.mensagens().enviar(p, "invasao-defendida", "a", nome(i.atacante), "b", nome(i.defensor));
            }
            for (UUID u : i.defensores) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && premio > 0 && plugin.economia().depositar(p, premio)) {
                    plugin.mensagens().enviar(p, "invasao-premio", "valor", Texto.dinheiro(premio), "b", nome(i.defensor));
                }
            }
            plugin.guerras().pontuar(i.defensor, i.atacante, cfg().inteiro("guerras.pontos.invasao-defendida", 50), null);
        } else {
            double perda = Math.max(0, Math.min(1, cfg().num("invasoes.estoque-perdido", 0.3)));
            for (NpcVivo n : plugin.npcs().todos()) {
                if (!i.defensor.equals(n.faccao()) || n.temporario()) {
                    continue;
                }
                for (ItemLoja it : n.compra()) {
                    int est = plugin.comercio().estoque(n, it);
                    if (est > 0) {
                        plugin.repositorio().setEstoque(n.id(), it.chave(), Math.floor(est * (1 - perda)));
                    }
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.mensagens().enviar(p, "invasao-perdida", "a", nome(i.atacante), "b", nome(i.defensor),
                        "perda", String.valueOf(Math.round(perda * 100)));
            }
            plugin.guerras().pontuar(i.atacante, i.defensor, cfg().inteiro("guerras.pontos.invasao-vitoriosa", 60), null);
        }
        plugin.repositorio().salvarAssincrono();
    }

    /** Staff parou a invasão (ou o plugin está desligando): tira os soldados sem vencedor. */
    public void encerrar() {
        Invasao i = atual;
        if (i == null) {
            return;
        }
        atual = null;
        for (NpcVivo s : List.copyOf(i.soldados)) {
            remover(s);
        }
        soltarChunks(i);
        if (i.barra != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(i.barra);
            }
        }
    }

    // ------------------------------------------------------------------ INVASÕES SOZINHAS

    /** De tempos em tempos, uma facção em guerra pode atacar a vila da inimiga (se houver jogadores por lá). */
    public void talvezIniciar() {
        if (atual != null || !cfg().sim("invasoes.ativo", true) || !cfg().sim("invasoes.automaticas", true)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Guerra g : plugin.guerras().ativas()) {
            List<String> comVila = new ArrayList<>();
            for (String f : List.of(g.a(), g.b())) {
                Location c = centroVila(f);
                if (c != null && jogadoresPerto(c, 10_000) >= cfg().inteiro("invasoes.jogadores-minimos-no-mundo", 1)) {
                    comVila.add(f);
                }
            }
            if (comVila.isEmpty() || r.nextDouble() * 100 >= cfg().num("invasoes.chance", 40)) {
                continue;
            }
            String defensor = comVila.get(r.nextInt(comVila.size()));
            iniciar(plugin.faccoes().obter(g.inimiga(defensor)), plugin.faccoes().obter(defensor));
            return;
        }
    }

    private String nome(String faccao) {
        Faccao f = plugin.faccoes().obter(faccao);
        return f == null ? faccao : f.nome();
    }
}
