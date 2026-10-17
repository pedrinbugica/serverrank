package dev.pedrobugica.survivalrpg.jogador;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Carrega o personagem quando o jogador entra, grava quando sai (e de 5 em 5 minutos, fora da thread principal),
 * dá XP, sobe de nível e aplica a vida extra da Vitalidade.
 */
public final class PerfilService implements Listener {

    private final SurvivalRpgPlugin plugin;
    private final File pasta;
    private final Map<UUID, PerfilRpg> perfis = new ConcurrentHashMap<>();
    private final NamespacedKey chaveVida;

    public PerfilService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "jogadores");
        this.chaveVida = new NamespacedKey(plugin, "vida-vitalidade");
    }

    // ------------------------------------------------------------------ CARREGAR / GRAVAR

    public PerfilRpg perfil(Player p) {
        return perfis.computeIfAbsent(p.getUniqueId(), u -> carregar(u, p.getName()));
    }

    public PerfilRpg perfilSeExistir(UUID u) {
        return perfis.get(u);
    }

    private PerfilRpg carregar(UUID u, String nome) {
        PerfilRpg r = new PerfilRpg(u);
        r.setNome(nome);
        File f = new File(pasta, u + ".yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            r.setClasse(Classe.buscar(y.getString("classe")));
            r.setNivel(Math.max(1, Math.min(nivelMaximo(), y.getInt("nivel", 1))));
            r.setXp(Math.max(0, y.getDouble("xp", 0)));
            r.setPontosLivres(Math.max(0, y.getInt("pontos-livres", 0)));
            for (Atributo a : Atributo.values()) {
                r.setAtributo(a, y.getInt("atributos." + a.name(), 0));
            }
            r.setMana(y.getDouble("mana", 100));
            r.setTrocouClasseEm(y.getLong("trocou-classe-em", 0));
            r.regioesDescobertas().addAll(y.getStringList("regioes-descobertas"));
            r.setNumerosDeDano(y.getBoolean("numeros-de-dano", true));
        }
        r.limpar();
        return r;
    }

    private YamlConfiguration foto(PerfilRpg r) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nome", r.nome());
        y.set("classe", r.classe() == null ? null : r.classe().name());
        y.set("nivel", r.nivel());
        y.set("xp", Math.round(r.xp() * 100) / 100.0);
        y.set("pontos-livres", r.pontosLivres());
        for (Atributo a : Atributo.values()) {
            y.set("atributos." + a.name(), r.atributo(a));
        }
        y.set("mana", Math.round(r.mana()));
        y.set("trocou-classe-em", r.trocouClasseEm());
        y.set("regioes-descobertas", new ArrayList<>(r.regioesDescobertas()));
        y.set("numeros-de-dano", r.numerosDeDano());
        return y;
    }

    private void gravar(PerfilRpg r, boolean agora) {
        String conteudo = foto(r).saveToString();
        File f = new File(pasta, r.uuid() + ".yml");
        r.limpar();
        Runnable escrever = () -> {
            try {
                if (!pasta.exists() && !pasta.mkdirs()) {
                    return;
                }
                java.nio.file.Files.writeString(f.toPath(), conteudo, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o perfil " + r.uuid(), e);
            }
        };
        if (agora) {
            escrever.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, escrever);
        }
    }

    public void gravarTodos(boolean agora) {
        for (PerfilRpg r : perfis.values()) {
            if (r.sujo() || agora) {
                gravar(r, agora);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEntrar(PlayerJoinEvent e) {
        PerfilRpg r = perfil(e.getPlayer());
        r.setNome(e.getPlayer().getName());
        Bukkit.getScheduler().runTask(plugin, () -> aplicarVida(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoSair(PlayerQuitEvent e) {
        PerfilRpg r = perfis.remove(e.getPlayer().getUniqueId());
        if (r != null) {
            gravar(r, false);
        }
    }

    // ------------------------------------------------------------------ NÍVEL E XP

    public int nivelMaximo() {
        return plugin.getConfig().getInt("nivel-maximo", 50);
    }

    /** XP para passar do nível n para o n+1: base x n^expoente (100 x n^1,5). */
    public double xpPara(int n) {
        return Math.round(plugin.getConfig().getDouble("xp.base", 100) * Math.pow(n, plugin.getConfig().getDouble("xp.expoente", 1.5)));
    }

    /** Soma XP e sobe de nível quantas vezes precisar. */
    public void darXp(Player p, double quanto, String motivo) {
        if (quanto <= 0) {
            return;
        }
        PerfilRpg r = perfil(p);
        if (r.nivel() >= nivelMaximo()) {
            return;
        }
        r.setXp(r.xp() + quanto);
        boolean subiu = false;
        while (r.nivel() < nivelMaximo() && r.xp() >= xpPara(r.nivel())) {
            r.setXp(r.xp() - xpPara(r.nivel()));
            r.setNivel(r.nivel() + 1);
            r.setPontosLivres(r.pontosLivres() + plugin.getConfig().getInt("pontos-por-nivel", 3));
            if (r.classe() != null) {
                r.setAtributo(r.classe().principal(), r.atributo(r.classe().principal()) + 1);
            }
            subiu = true;
        }
        if (r.nivel() >= nivelMaximo()) {
            r.setXp(0);
        }
        if (motivo != null) {
            p.sendActionBar(Texto.cor(plugin.mensagens().bruta("xp-ganho"), "xp", Texto.numero(quanto), "motivo", motivo));
            plugin.hud().pausar(p, 1500);
        }
        if (subiu) {
            subirNivel(p, r);
        }
        plugin.hud().atualizarXp(p);
    }

    private void subirNivel(Player p, PerfilRpg r) {
        p.showTitle(Title.title(Texto.cor(plugin.mensagens().bruta("nivel-titulo"), "nivel", String.valueOf(r.nivel())),
                Texto.cor(plugin.mensagens().bruta("nivel-subtitulo"), "pontos", String.valueOf(r.pontosLivres())),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        plugin.mensagens().enviar(p, "nivel-chat", "nivel", String.valueOf(r.nivel()), "pontos", String.valueOf(r.pontosLivres()));
        aplicarVida(p);
        if (r.nivel() % 10 == 0) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                plugin.mensagens().enviar(o, "nivel-anuncio", "jogador", p.getName(), "nivel", String.valueOf(r.nivel()),
                        "classe", r.classe() == null ? "" : r.classe().formatado());
            }
        }
    }

    /** Vitalidade: +1 de vida máxima a cada 2 pontos (vale em todos os mundos do servidor RPG). */
    public void aplicarVida(Player p) {
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida == null) {
            return;
        }
        vida.removeModifier(chaveVida);
        if (!plugin.mundoRpg(p.getWorld())) {
            return;
        }
        PerfilRpg r = perfil(p);
        double extra = Math.floor(r.atributo(Atributo.VITALIDADE) / 2.0) + plugin.itens().somaAtributo(p, Atributo.VITALIDADE) / 2.0;
        if (extra > 0) {
            vida.addModifier(new AttributeModifier(chaveVida, Math.min(80, extra), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        if (p.getHealth() > vida.getValue()) {
            p.setHealth(vida.getValue());
        }
    }

    /** Valor total do atributo: pontos do personagem + itens RPG equipados. */
    public int atributoTotal(Player p, Atributo a) {
        return perfil(p).atributo(a) + plugin.itens().somaAtributo(p, a);
    }

    public double manaMaxima(Player p) {
        return plugin.getConfig().getDouble("mana.base", 100) + 2 * atributoTotal(p, Atributo.INTELIGENCIA);
    }

    public List<PerfilRpg> online() {
        return new ArrayList<>(perfis.values());
    }
}
