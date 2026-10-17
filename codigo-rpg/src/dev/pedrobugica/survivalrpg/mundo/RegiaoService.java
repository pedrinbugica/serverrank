package dev.pedrobugica.survivalrpg.mundo;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Regiões do mapa (quanto mais longe do spawn, mais fortes os mobs) e zonas seguras (cidades, onde não nascem
 * monstros). Avisa na tela quando o jogador entra numa região ou cidade e dá XP na primeira descoberta.
 */
public final class RegiaoService {

    /** Uma zona segura (cidade, vila, castelo): sem monstros do RPG, sem PvP. */
    public record ZonaSegura(String id, String nome, String mundo, double x, double z, double raio) {
        public boolean contem(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) {
                return false;
            }
            double dx = l.getX() - x;
            double dz = l.getZ() - z;
            return dx * dx + dz * dz <= raio * raio;
        }
    }

    private final SurvivalRpgPlugin plugin;
    private final List<Regiao> regioes = new ArrayList<>();
    private final List<ZonaSegura> zonas = new CopyOnWriteArrayList<>();
    private BukkitTask tarefa;

    public RegiaoService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        regioes.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("regioes");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection r = s.getConfigurationSection(id);
                if (r == null) {
                    continue;
                }
                List<Integer> niveis = r.getIntegerList("niveis");
                regioes.add(new Regiao(id, r.getString("nome", id), r.getString("mundo", mundoPrincipal()),
                        r.getDouble("de", -1), r.getDouble("ate", -1),
                        niveis.size() > 0 ? niveis.get(0) : 1, niveis.size() > 1 ? niveis.get(1) : 10, r.getBoolean("pvp", false)));
            }
        }
        plugin.getLogger().info(regioes.size() + " região(ões) do mapa de RPG.");
    }

    public String mundoPrincipal() {
        return plugin.getConfig().getString("mundo-principal", "sobrevivencia");
    }

    public List<Regiao> todas() {
        return List.copyOf(regioes);
    }

    /** Centro dos anéis: o spawn do mundo principal. */
    public Location centro(World w) {
        World principal = Bukkit.getWorld(mundoPrincipal());
        return (principal != null && principal.equals(w) ? principal : w).getSpawnLocation();
    }

    public double distanciaDoSpawn(Location l) {
        Location c = centro(l.getWorld());
        double dx = l.getX() - c.getX();
        double dz = l.getZ() - c.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A região deste lugar (null = fora do RPG). */
    public Regiao regiao(Location l) {
        if (l.getWorld() == null || !plugin.mundoRpg(l.getWorld())) {
            return null;
        }
        String mundo = l.getWorld().getName();
        double d = distanciaDoSpawn(l);
        Regiao mundoTodo = null;
        for (Regiao r : regioes) {
            if (!r.mundo().equalsIgnoreCase(mundo)) {
                continue;
            }
            if (r.de() < 0) {
                mundoTodo = r;
            } else if (d >= r.de() && (r.ate() < 0 || d < r.ate())) {
                return r;
            }
        }
        if (mundoTodo != null) {
            return mundoTodo;
        }
        // Passou do último anel: vale o mais longe
        Regiao longe = null;
        for (Regiao r : regioes) {
            if (r.mundo().equalsIgnoreCase(mundo) && r.de() >= 0 && (longe == null || r.de() > longe.de())) {
                longe = r;
            }
        }
        return longe;
    }

    // ------------------------------------------------------------------ ZONAS SEGURAS

    public void registrarZona(ZonaSegura z) {
        zonas.removeIf(x -> x.id().equals(z.id()));
        zonas.add(z);
    }

    public void removerZona(String id) {
        zonas.removeIf(x -> x.id().equals(id));
    }

    public List<ZonaSegura> zonas() {
        return List.copyOf(zonas);
    }

    public ZonaSegura zona(Location l) {
        for (ZonaSegura z : zonas) {
            if (z.contem(l)) {
                return z;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ AVISOS NA TELA

    public void iniciar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::verificar, 40L, 20L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    private void verificar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PerfilRpg r = plugin.perfis().perfil(p);
            Regiao reg = regiao(p.getLocation());
            ZonaSegura z = reg == null ? null : zona(p.getLocation());
            String agora = reg == null ? null : (z != null ? "zona:" + z.id() : reg.id());
            String antes = r.regiaoAtual();
            if (agora == null ? antes == null : agora.equals(antes)) {
                continue;
            }
            r.setRegiaoAtual(agora);
            if (agora == null) {
                continue;
            }
            if (z != null) {
                p.showTitle(Title.title(Texto.cor(z.nome()), Texto.cor(plugin.mensagens().bruta("zona-segura-subtitulo")),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(600))));
                descobrir(p, r, "zona:" + z.id(), z.nome());
                continue;
            }
            // Saindo de uma zona segura para a mesma região não precisa de outro título grande
            if (antes != null && antes.startsWith("zona:") && reg.id().equals(regiaoSemZona(p))) {
                p.sendActionBar(Texto.cor(plugin.mensagens().bruta("saiu-zona-segura"), "regiao", reg.nome(), "niveis", reg.faixa()));
                plugin.hud().pausar(p, 2500);
                continue;
            }
            p.showTitle(Title.title(Texto.cor(reg.nome()), Texto.cor(plugin.mensagens().bruta("regiao-subtitulo"), "niveis", reg.faixa(),
                            "pvp", reg.pvp() ? plugin.mensagens().bruta("regiao-pvp") : ""),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2600), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.5f, 1.2f);
            descobrir(p, r, reg.id(), reg.nome());
        }
    }

    private String regiaoSemZona(Player p) {
        Regiao reg = regiao(p.getLocation());
        return reg == null ? "" : reg.id();
    }

    private void descobrir(Player p, PerfilRpg r, String id, String nome) {
        if (r.regioesDescobertas().add(id.toLowerCase(Locale.ROOT))) {
            r.marcarSujo();
            double xp = plugin.getConfig().getDouble("xp.descobrir-lugar", 150) * Math.max(1, r.nivel() / 5.0);
            plugin.perfis().darXp(p, xp, Texto.semCor(nome) + " descoberta");
            plugin.mensagens().enviar(p, "descobriu", "lugar", nome, "xp", Texto.numero(xp));
        }
    }
}
