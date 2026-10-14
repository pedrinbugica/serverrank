package br.rankupsupremo.sobrevivencia;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mundo Sobrevivência (survival normal): configuração dos mundos, teleporte, /rtp e proteção do spawn.
 * Os mundos são criados pelo Multiverse (comandos-unicos no config.yml) e o inventário é separado
 * do Rankup pelo Multiverse-Inventories.
 */
public final class GerenciadorSobrevivencia {

    private static final Set<Material> PERIGOSOS = Set.of(Material.LAVA, Material.WATER, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.POWDER_SNOW, Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.POINTED_DRIPSTONE, Material.BUBBLE_COLUMN);

    private final RankupSupremo plugin;
    private final Map<UUID, Long> cooldownRtp = new HashMap<>();
    private final Set<UUID> procurando = new HashSet<>();

    public GerenciadorSobrevivencia(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public boolean ativado() {
        return cfg().getBoolean("sobrevivencia.ativado", true);
    }

    public String nomeBase() {
        return cfg().getString("sobrevivencia.mundo", "sobrevivencia");
    }

    public List<String> nomesMundos() {
        String b = nomeBase();
        return List.of(b, b + "_nether", b + "_the_end");
    }

    public boolean ehSobrevivencia(World w) {
        return w != null && ativado() && nomesMundos().contains(w.getName());
    }

    /** Mundo principal (overworld) da Sobrevivência, ou null se ainda não existir. */
    public World mundo() {
        return ativado() ? Bukkit.getWorld(nomeBase()) : null;
    }

    public Location spawn() {
        World w = mundo();
        if (w == null) {
            return null;
        }
        Location s = w.getSpawnLocation();
        return new Location(w, s.getBlockX() + 0.5, s.getY(), s.getBlockZ() + 0.5, s.getYaw(), 0f);
    }

    // ------------------------------------------------------------------ CONFIGURAÇÃO DOS MUNDOS

    public void configurarTodos() {
        for (String nome : nomesMundos()) {
            World w = Bukkit.getWorld(nome);
            if (w != null) {
                configurar(w);
            }
        }
    }

    public void configurar(World w) {
        if (!ehSobrevivencia(w)) {
            return;
        }
        Difficulty dificuldade;
        try {
            dificuldade = Difficulty.valueOf(cfg().getString("sobrevivencia.dificuldade", "NORMAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            dificuldade = Difficulty.NORMAL;
        }
        if (w.getEnvironment() == World.Environment.NORMAL && !plugin.estado().spawnSobrevivenciaConstruido()) {
            construirSpawn(w);
            plugin.estado().setSpawnSobrevivenciaConstruido(true);
        }
        w.setDifficulty(dificuldade);
        w.setGameRule(GameRules.PVP, cfg().getBoolean("sobrevivencia.pvp", true));
        if (w.getEnvironment() == World.Environment.NORMAL) {
            w.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE,
                    Math.max(0, Math.min(100, cfg().getInt("sobrevivencia.porcentagem-para-dormir", 30))));
        }
        int raio = cfg().getInt("sobrevivencia.borda-raio", 10000);
        if (raio > 0) {
            WorldBorder borda = w.getWorldBorder();
            World principal = mundo();
            Location centro = principal != null ? principal.getSpawnLocation() : w.getSpawnLocation();
            if (w.getEnvironment() == World.Environment.NETHER) {
                borda.setCenter(centro.getX() / 8.0, centro.getZ() / 8.0);
                borda.setSize(Math.max(64, raio * 2 / 8.0));
            } else if (w.getEnvironment() == World.Environment.THE_END) {
                borda.setCenter(0, 0);
                borda.setSize(raio * 2.0);
            } else {
                borda.setCenter(centro.getX(), centro.getZ());
                borda.setSize(raio * 2.0);
            }
        }
        if (w.getEnvironment() == World.Environment.NORMAL) {
            plugin.hologramas().adicionarSobrevivencia(w);
        }
        plugin.getLogger().info("Mundo de sobrevivência '" + w.getName() + "' configurado (dificuldade "
                + dificuldade + ", borda " + raio + ").");
    }

    /**
     * Constrói (uma única vez) uma praça de spawn no chão firme, limpa árvores em cima
     * e move o spawn do mundo para o centro dela.
     */
    public void construirSpawn(World w) {
        Location s = w.getSpawnLocation();
        int cx = s.getBlockX();
        int cz = s.getBlockZ();
        int y = w.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y < w.getMinHeight() + 10 || y > w.getMaxHeight() - 30) {
            y = Math.max(w.getSeaLevel(), 63);
        }
        int raio = 5;
        for (int dx = -raio - 1; dx <= raio + 1; dx++) {
            for (int dz = -raio - 1; dz <= raio + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > raio + 0.5) {
                    continue;
                }
                Material piso;
                if (d < 1.5) {
                    piso = Material.CHISELED_STONE_BRICKS;
                } else if (d >= raio - 0.5) {
                    piso = Material.POLISHED_ANDESITE;
                } else {
                    piso = (dx + dz) % 2 == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                }
                w.getBlockAt(cx + dx, y, cz + dz).setType(piso, false);
                // Base: preenche buracos/água logo abaixo para a praça não ficar flutuando
                for (int yy = y - 1; yy >= y - 8; yy--) {
                    Block b = w.getBlockAt(cx + dx, yy, cz + dz);
                    if (b.getType().isSolid() && !Tag.LEAVES.isTagged(b.getType())) {
                        break;
                    }
                    b.setType(yy == y - 1 ? Material.STONE_BRICKS : Material.STONE, false);
                }
                // Limpa árvores e plantas em cima
                for (int yy = y + 1; yy <= y + 16; yy++) {
                    Block b = w.getBlockAt(cx + dx, yy, cz + dz);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        int[][] postes = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
        for (int[] p : postes) {
            w.getBlockAt(cx + p[0], y + 1, cz + p[1]).setType(Material.STONE_BRICK_WALL, false);
            w.getBlockAt(cx + p[0], y + 2, cz + p[1]).setType(Material.LANTERN, false);
        }
        w.setSpawnLocation(cx, y + 1, cz);
        if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            // O Multiverse guarda o spawn na configuração dele; sem isso ele voltaria ao antigo ao reiniciar.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT, "mvsetspawn %s:%.1f,%d,%.1f,0,0",
                    w.getName(), cx + 0.5, y + 1, cz + 0.5));
        }
        plugin.getLogger().info("Praça de spawn da Sobrevivência construída em " + cx + ", " + (y + 1) + ", " + cz + ".");
    }

    // ------------------------------------------------------------------ TELEPORTE

    public void teleportar(Player p) {
        World w = mundo();
        if (w == null) {
            plugin.mensagens().enviar(p, "sobrevivencia-indisponivel");
            return;
        }
        if (ehSobrevivencia(p.getWorld())) {
            plugin.mensagens().enviar(p, "sobrevivencia-ja-esta");
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        Location destino = ler(d.ultimaSobrevivencia());
        if (destino == null) {
            destino = spawn();
        }
        boolean primeiraVez = !d.visitouSobrevivencia();
        p.teleportAsync(destino).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ok || !p.isOnline()) {
                return;
            }
            Acoes.som(p, "entity.enderman.teleport", 1f, 1f);
            Acoes.titulo(p, plugin.mensagens().bruta("sobrevivencia-titulo"), plugin.mensagens().bruta("sobrevivencia-subtitulo"));
            if (primeiraVez) {
                d.setVisitouSobrevivencia(true);
                plugin.mensagens().enviarLista(p, "sobrevivencia-boas-vindas", "jogador", p.getName());
            }
        }));
    }

    public void salvarPosicao(Player p, Location l) {
        if (l != null && ehSobrevivencia(l.getWorld())) {
            plugin.jogadores().obter(p).setUltimaSobrevivencia(String.format(Locale.ROOT, "%s;%.2f;%.2f;%.2f;%.1f;%.1f",
                    l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        }
    }

    private Location ler(String s) {
        if (s == null) {
            return null;
        }
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null || !ehSobrevivencia(w)) {
                return null;
            }
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ /RTP

    public void rtp(Player p) {
        World w = mundo();
        if (w == null) {
            plugin.mensagens().enviar(p, "sobrevivencia-indisponivel");
            return;
        }
        if (procurando.contains(p.getUniqueId())) {
            plugin.mensagens().enviar(p, "rtp-procurando");
            return;
        }
        long agora = System.currentTimeMillis();
        long espera = cfg().getLong("sobrevivencia.rtp.cooldown-segundos", 60) * 1000L;
        Long ultimo = cooldownRtp.get(p.getUniqueId());
        if (ultimo != null && agora - ultimo < espera && !p.hasPermission("rankupsupremo.rtp.semcooldown")) {
            plugin.mensagens().enviar(p, "rtp-cooldown", "segundos", String.valueOf((espera - (agora - ultimo)) / 1000 + 1));
            return;
        }
        procurando.add(p.getUniqueId());
        plugin.mensagens().enviar(p, "rtp-procurando");
        tentar(p, w, 0);
    }

    private void tentar(Player p, World w, int tentativa) {
        int maxTentativas = 12;
        if (tentativa >= maxTentativas || !p.isOnline()) {
            procurando.remove(p.getUniqueId());
            if (p.isOnline()) {
                plugin.mensagens().enviar(p, "rtp-falhou");
            }
            return;
        }
        int min = Math.max(0, cfg().getInt("sobrevivencia.rtp.distancia-minima", 300));
        int max = Math.max(min + 50, cfg().getInt("sobrevivencia.rtp.distancia-maxima", 4000));
        int raioBorda = cfg().getInt("sobrevivencia.borda-raio", 10000);
        if (raioBorda > 0) {
            max = Math.min(max, raioBorda - 50);
            min = Math.min(min, Math.max(0, max - 50));
        }
        Location centro = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angulo = r.nextDouble(Math.PI * 2);
        double dist = min + r.nextDouble(Math.max(1, max - min));
        int x = (int) Math.round(centro.getX() + Math.cos(angulo) * dist);
        int z = (int) Math.round(centro.getZ() + Math.sin(angulo) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                procurando.remove(p.getUniqueId());
                return;
            }
            Location destino = localSeguro(w, x, z);
            if (destino == null) {
                tentar(p, w, tentativa + 1);
                return;
            }
            p.teleportAsync(destino).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                procurando.remove(p.getUniqueId());
                if (ok && p.isOnline()) {
                    cooldownRtp.put(p.getUniqueId(), System.currentTimeMillis());
                    Acoes.som(p, "entity.enderman.teleport", 1f, 0.8f);
                    plugin.mensagens().enviar(p, "rtp-sucesso", "x", String.valueOf(x), "z", String.valueOf(z));
                }
            }));
        }));
    }

    private Location localSeguro(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 3) {
            return null;
        }
        Block chao = w.getBlockAt(x, y, z);
        Material tipo = chao.getType();
        if (!tipo.isSolid() || PERIGOSOS.contains(tipo) || Tag.LEAVES.isTagged(tipo) || chao.isLiquid()) {
            return null;
        }
        Block pes = chao.getRelative(0, 1, 0);
        Block cabeca = chao.getRelative(0, 2, 0);
        if (!pes.isPassable() || !cabeca.isPassable() || pes.isLiquid() || cabeca.isLiquid()) {
            return null;
        }
        if (!w.getWorldBorder().isInside(pes.getLocation())) {
            return null;
        }
        return new Location(w, x + 0.5, y + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
    }

    public void limpar(UUID uuid) {
        procurando.remove(uuid);
    }

    // ------------------------------------------------------------------ PROTEÇÃO DO SPAWN

    /** O local está dentro da área protegida do spawn da Sobrevivência? */
    public boolean spawnProtegido(Location l) {
        int raio = cfg().getInt("sobrevivencia.protecao-spawn-raio", 24);
        World w = mundo();
        if (raio <= 0 || w == null || l.getWorld() != w) {
            return false;
        }
        Location s = w.getSpawnLocation();
        double dx = l.getX() - s.getX();
        double dz = l.getZ() - s.getZ();
        return dx * dx + dz * dz <= (double) raio * raio;
    }
}
