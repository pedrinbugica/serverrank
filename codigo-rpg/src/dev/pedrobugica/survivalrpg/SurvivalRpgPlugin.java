package dev.pedrobugica.survivalrpg;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.classe.Classe;
import dev.pedrobugica.survivalrpg.combate.CombateListener;
import dev.pedrobugica.survivalrpg.comandos.AdminCommand;
import dev.pedrobugica.survivalrpg.comandos.JogadorCommand;
import dev.pedrobugica.survivalrpg.construcao.Capital;
import dev.pedrobugica.survivalrpg.grupo.GrupoService;
import dev.pedrobugica.survivalrpg.habilidade.HabilidadeService;
import dev.pedrobugica.survivalrpg.hook.NpcHook;
import dev.pedrobugica.survivalrpg.hud.HudService;
import dev.pedrobugica.survivalrpg.item.ItemRpgService;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.jogador.PerfilService;
import dev.pedrobugica.survivalrpg.masmorra.MasmorraService;
import dev.pedrobugica.survivalrpg.menu.Menus;
import dev.pedrobugica.survivalrpg.mob.MobService;
import dev.pedrobugica.survivalrpg.mundo.LugarService;
import dev.pedrobugica.survivalrpg.mundo.RegiaoService;
import dev.pedrobugica.survivalrpg.util.Economia;
import dev.pedrobugica.survivalrpg.util.Mensagens;
import dev.pedrobugica.survivalrpg.util.MenuListener;
import dev.pedrobugica.survivalrpg.util.Texto;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * SurvivalRPG: transforma o mundo de sobrevivência num RPG — classes, níveis, atributos, habilidades, mobs com
 * nível por região, itens com raridade, grupos, lugares para descobrir, a capital Vila Aurora e masmorras.
 */
public final class SurvivalRpgPlugin extends JavaPlugin implements Listener {

    private Mensagens mensagens;
    private Economia economia;
    private PerfilService perfis;
    private ItemRpgService itens;
    private MobService mobs;
    private RegiaoService regioes;
    private GrupoService grupos;
    private CombateListener combate;
    private HudService hud;
    private HabilidadeService habilidades;
    private Menus menus;
    private LugarService lugares;
    private Capital capital;
    private MasmorraService masmorras;
    private NpcHook npcHook;
    private File arquivoEstado;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        completarConfig();
        mensagens = new Mensagens(this);
        mensagens.carregar();
        economia = new Economia();
        if (!economia.conectar()) {
            getLogger().warning("Vault sem economia: viagens, troca de classe e prêmios em dinheiro ficam grátis/desligados.");
        }
        perfis = new PerfilService(this);
        itens = new ItemRpgService(this);
        mobs = new MobService(this);
        regioes = new RegiaoService(this);
        grupos = new GrupoService(this);
        combate = new CombateListener(this);
        hud = new HudService(this);
        habilidades = new HabilidadeService(this);
        menus = new Menus(this);
        lugares = new LugarService(this);
        capital = new Capital(this);
        masmorras = new MasmorraService(this);
        npcHook = new NpcHook(this);
        arquivoEstado = new File(getDataFolder(), "estado.yml");

        regioes.carregar();
        lugares.carregar();
        masmorras.carregar();

        var pm = getServer().getPluginManager();
        pm.registerEvents(perfis, this);
        pm.registerEvents(mobs, this);
        pm.registerEvents(grupos, this);
        pm.registerEvents(combate, this);
        pm.registerEvents(habilidades, this);
        pm.registerEvents(lugares, this);
        pm.registerEvents(masmorras, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(this, this);
        lugares.ligarGriefPrevention();

        JogadorCommand jogador = new JogadorCommand(this);
        for (String c : List.of("rpg", "classe", "atributos", "poderes", "grupo", "g", "lugares", "masmorra")) {
            registrar(c, jogador, jogador);
        }
        AdminCommand admin = new AdminCommand(this);
        registrar("rpgadmin", admin, admin);

        if (pm.isPluginEnabled("PlaceholderAPI")) {
            try {
                new dev.pedrobugica.survivalrpg.hook.ExpansaoRpg(this).register();
                getLogger().info("PlaceholderAPI: %rpg_classe%, %rpg_nivel%, %rpg_xp_porcento%, %rpg_regiao%...");
            } catch (LinkageError e) {
                getLogger().warning("PlaceholderAPI incompatível: placeholders desligados.");
            }
        }

        hud.iniciar();
        regioes.iniciar();
        lugares.iniciar();
        masmorras.iniciar();
        Bukkit.getScheduler().runTaskTimer(this, () -> perfis.gravarTodos(false), 20L * 300, 20L * 300);
        for (Player p : Bukkit.getOnlinePlayers()) {
            perfis.perfil(p);
            perfis.aplicarVida(p);
        }
        Bukkit.getScheduler().runTaskLater(this, this::construcaoAutomatica, 20L * getConfig().getInt("construcao.espera-segundos", 15));
        getLogger().info("SurvivalRPG ligado nos mundos " + getConfig().getStringList("mundos") + ".");
    }

    @Override
    public void onDisable() {
        if (habilidades != null) {
            habilidades.desligarTodos();
        }
        if (masmorras != null) {
            masmorras.parar();
        }
        if (lugares != null) {
            lugares.parar();
        }
        if (hud != null) {
            hud.parar();
        }
        if (regioes != null) {
            regioes.parar();
        }
        if (perfis != null) {
            perfis.gravarTodos(true);
        }
    }

    private void registrar(String nome, CommandExecutor ex, TabCompleter tab) {
        PluginCommand c = getCommand(nome);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(tab);
        }
    }

    /** Chaves novas do config.yml (de atualizações) entram sozinhas, sem apagar o que a staff mudou. */
    private void completarConfig() {
        try (var in = getResource("config.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration padrao = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean mudou = false;
            for (String k : padrao.getKeys(true)) {
                if (!padrao.isConfigurationSection(k) && !getConfig().contains(k, true)) {
                    getConfig().set(k, padrao.get(k));
                    mudou = true;
                }
            }
            if (mudou) {
                saveConfig();
            }
        } catch (IOException e) {
            getLogger().warning("Não foi possível completar o config.yml: " + e.getMessage());
        }
    }

    public void recarregar() {
        reloadConfig();
        completarConfig();
        mensagens.carregar();
        regioes.carregar();
        masmorras.parar();
        lugares.carregar();
        masmorras.carregar();
        masmorras.iniciar();
    }

    // ------------------------------------------------------------------ CONSTRUÇÃO AUTOMÁTICA

    private YamlConfiguration estado() {
        return YamlConfiguration.loadConfiguration(arquivoEstado);
    }

    private void marcarEstado(String chave) {
        YamlConfiguration y = estado();
        y.set(chave, System.currentTimeMillis());
        try {
            y.save(arquivoEstado);
        } catch (IOException e) {
            getLogger().warning("Não foi possível gravar estado.yml: " + e.getMessage());
        }
    }

    /** Na primeira vez: constrói a capital, as masmorras e espalha os lugares pelo mapa. Depois não mexe mais. */
    private void construcaoAutomatica() {
        if (!getConfig().getBoolean("construcao.automatica", true)) {
            return;
        }
        if (Bukkit.getWorld(regioes.mundoPrincipal()) == null) {
            getLogger().warning("Mundo " + regioes.mundoPrincipal() + " não carregado: construção automática adiada.");
            return;
        }
        Runnable depoisDaCapital = () -> {
            masmorras.prepararTodas();
            if (getConfig().getBoolean("lugares.automatico", true) && !estado().contains("lugares-populados")) {
                Bukkit.getScheduler().runTaskLater(this, () -> lugares.popular(Bukkit.getConsoleSender(),
                        () -> marcarEstado("lugares-populados")), 20L * 30);
            }
        };
        if (!capital.construida() && !estado().contains("capital-tentada")) {
            marcarEstado("capital-tentada");
            capital.construir(Bukkit.getConsoleSender(), false, depoisDaCapital);
        } else {
            depoisDaCapital.run();
        }
    }

    // ------------------------------------------------------------------ CLASSE E PONTOS

    /** Escolhe/troca a classe. A primeira é grátis; trocar custa dinheiro e tem espera. Os pontos voltam. */
    public void escolherClasse(Player p, Classe c, boolean admin) {
        PerfilRpg r = perfis.perfil(p);
        if (r.classe() == c) {
            mensagens.enviar(p, "classe-mesma");
            return;
        }
        boolean troca = r.classe() != null;
        if (troca && !admin) {
            long espera = (long) (getConfig().getDouble("classes.troca.espera-horas", 24) * 3_600_000L);
            long falta = r.trocouClasseEm() + espera - System.currentTimeMillis();
            if (falta > 0) {
                mensagens.enviar(p, "classe-espera", "horas", String.valueOf(falta / 3_600_000L + 1));
                return;
            }
            double custo = getConfig().getDouble("classes.troca.custo", 5000);
            if (economia.ativa() && !economia.sacar(p, custo)) {
                mensagens.enviar(p, "classe-sem-dinheiro", "custo", Texto.dinheiro(custo));
                return;
            }
        }
        habilidades.sairDoModo(p, r, false);
        r.setClasse(c);
        if (troca) {
            r.setTrocouClasseEm(System.currentTimeMillis());
        }
        devolverPontos(r);
        perfis.aplicarVida(p);
        p.showTitle(Title.title(Texto.cor(c.formatado()), Texto.cor(mensagens.bruta("classe-subtitulo")),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.3f);
        mensagens.enviar(p, troca ? "classe-trocada" : "classe-escolhida", "classe", c.formatado(), "pontos", String.valueOf(r.pontosLivres()));
    }

    private void devolverPontos(PerfilRpg r) {
        for (Atributo a : Atributo.values()) {
            r.setAtributo(a, 0);
        }
        r.setPontosLivres((r.nivel() - 1) * getConfig().getInt("pontos-por-nivel", 3));
        if (r.classe() != null) {
            r.setAtributo(r.classe().principal(), r.nivel() - 1);
        }
    }

    public void redistribuir(Player p, boolean cobrar) {
        PerfilRpg r = perfis.perfil(p);
        double custo = getConfig().getDouble("atributos.custo-resetar", 2000);
        if (cobrar && economia.ativa() && !economia.sacar(p, custo)) {
            mensagens.enviar(p, "atributos-sem-dinheiro", "custo", Texto.dinheiro(custo));
            return;
        }
        devolverPontos(r);
        perfis.aplicarVida(p);
        mensagens.enviar(p, "atributos-resetados", "pontos", String.valueOf(r.pontosLivres()));
    }

    // ------------------------------------------------------------------ VIDA EXTRA AO TROCAR EQUIPAMENTO/MUNDO

    @EventHandler
    public void aoMudarDeMundo(PlayerChangedWorldEvent e) {
        perfis.aplicarVida(e.getPlayer());
        hud.atualizarXp(e.getPlayer());
        if (!mundoRpg(e.getPlayer().getWorld())) {
            // fora do RPG a barra de XP volta a ser a do jogo
            e.getPlayer().sendExperienceChange(e.getPlayer().getExp(), e.getPlayer().getLevel());
        }
    }

    @EventHandler
    public void aoTrocarArmadura(PlayerArmorChangeEvent e) {
        Bukkit.getScheduler().runTask(this, () -> perfis.aplicarVida(e.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void aoTrocarItem(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(this, () -> perfis.aplicarVida(e.getPlayer()));
    }

    // ------------------------------------------------------------------ ACESSO

    public boolean mundoRpg(World w) {
        if (w == null) {
            return false;
        }
        String n = w.getName().toLowerCase(Locale.ROOT);
        for (String m : getConfig().getStringList("mundos")) {
            if (m.toLowerCase(Locale.ROOT).equals(n)) {
                return true;
            }
        }
        return false;
    }

    public Mensagens mensagens() {
        return mensagens;
    }

    public Economia economia() {
        return economia;
    }

    public PerfilService perfis() {
        return perfis;
    }

    public ItemRpgService itens() {
        return itens;
    }

    public MobService mobs() {
        return mobs;
    }

    public RegiaoService regioes() {
        return regioes;
    }

    public GrupoService grupos() {
        return grupos;
    }

    public CombateListener combate() {
        return combate;
    }

    public HudService hud() {
        return hud;
    }

    public HabilidadeService habilidades() {
        return habilidades;
    }

    public Menus menus() {
        return menus;
    }

    public LugarService lugares() {
        return lugares;
    }

    public Capital capital() {
        return capital;
    }

    public MasmorraService masmorras() {
        return masmorras;
    }

    public NpcHook npcHook() {
        return npcHook;
    }
}
