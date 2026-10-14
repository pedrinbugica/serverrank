package br.rankupsupremo;

import br.rankupsupremo.comando.ComandoAdmin;
import br.rankupsupremo.comando.ComandoTokens;
import br.rankupsupremo.comando.ComandosJogador;
import br.rankupsupremo.config.Estado;
import br.rankupsupremo.config.Mensagens;
import br.rankupsupremo.dados.GerenciadorJogadores;
import br.rankupsupremo.economia.Economia;
import br.rankupsupremo.economia.GerenciadorVendas;
import br.rankupsupremo.hook.ExpansaoPAPI;
import br.rankupsupremo.listener.ChatListener;
import br.rankupsupremo.listener.CombateListener;
import br.rankupsupremo.listener.JogadorListener;
import br.rankupsupremo.listener.MineracaoListener;
import br.rankupsupremo.listener.PagamentoListener;
import br.rankupsupremo.loja.GerenciadorLoja;
import br.rankupsupremo.listener.ProtecaoListener;
import br.rankupsupremo.menu.MenuListener;
import br.rankupsupremo.menu.Menus;
import br.rankupsupremo.mina.GerenciadorMinas;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.mundo.ConstrutorMundo;
import br.rankupsupremo.mundo.GeradorVazio;
import br.rankupsupremo.listener.SobrevivenciaListener;
import br.rankupsupremo.menu.FerramentasOp;
import br.rankupsupremo.menu.MenuOp;
import br.rankupsupremo.picareta.EfeitosPicareta;
import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.sobrevivencia.GerenciadorSobrevivencia;
import org.bukkit.configuration.ConfigurationSection;
import br.rankupsupremo.rank.GerenciadorRanks;
import br.rankupsupremo.visual.GerenciadorHologramas;
import br.rankupsupremo.visual.GerenciadorVisual;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RankUP Supremo - plugin principal do servidor de rankup.
 */
public final class RankupSupremo extends JavaPlugin {

    private Estado estado;
    private Mensagens mensagens;
    private GerenciadorRanks ranks;
    private GerenciadorMinas minas;
    private GerenciadorJogadores jogadores;
    private GerenciadorVendas vendas;
    private GerenciadorPicareta picareta;
    private GerenciadorVisual visual;
    private GerenciadorHologramas hologramas;
    private GerenciadorSobrevivencia sobrevivencia;
    private ConstrutorMundo construtor;
    private Economia economia;
    private Acoes acoes;
    private Menus menus;
    private GerenciadorLoja loja;
    private EfeitosPicareta efeitos;
    private CombateListener combate;

    private final Set<UUID> construindo = ConcurrentHashMap.newKeySet();
    private World mundo;
    private volatile Location spawn;
    private boolean placeholderApi;
    private boolean pronto;

    @Override
    public void onEnable() {
        salvarArquivosPadrao();

        estado = new Estado(this);
        mensagens = new Mensagens(this);
        ranks = new GerenciadorRanks(this);
        minas = new GerenciadorMinas(this);
        jogadores = new GerenciadorJogadores(this);
        vendas = new GerenciadorVendas(this);
        picareta = new GerenciadorPicareta(this);
        visual = new GerenciadorVisual(this);
        hologramas = new GerenciadorHologramas(this);
        sobrevivencia = new GerenciadorSobrevivencia(this);
        construtor = new ConstrutorMundo(this);
        economia = new Economia();
        acoes = new Acoes(this);
        menus = new Menus(this);
        loja = new GerenciadorLoja(this);
        efeitos = new EfeitosPicareta(this);
        combate = new CombateListener(this);

        carregarConfiguracoes();
        jogadores.carregar();

        MineracaoListener mineracao = new MineracaoListener(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(mineracao, this);
        pm.registerEvents(new ProtecaoListener(this), this);
        pm.registerEvents(new JogadorListener(this, mineracao), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new SobrevivenciaListener(this), this);
        pm.registerEvents(combate, this);
        pm.registerEvents(new PagamentoListener(this), this);

        ComandosJogador cj = new ComandosJogador(this);
        for (String c : List.of("rankup", "ranks", "prestigio", "minas", "vender", "autovender", "picareta", "spawn",
                "toprank", "sobrevivencia", "mundos", "rtp", "loja")) {
            registrarComando(c, cj);
        }
        ComandoAdmin admin = new ComandoAdmin(this);
        registrarComando("rankupadmin", admin);
        FerramentasOp ferramentasOp = new FerramentasOp(this);
        pm.registerEvents(ferramentasOp, this);
        registrarComando("menuop", new MenuOp(this, ferramentasOp));
        registrarComando("tokens", new ComandoTokens(this));

        // O plugin carrega antes dos mundos (load: STARTUP); o restante roda quando o servidor terminar de iniciar.
        getServer().getScheduler().runTask(this, this::aposInicializacao);
    }

    private <T extends CommandExecutor & TabCompleter> void registrarComando(String nome, T executor) {
        PluginCommand cmd = getCommand(nome);
        if (cmd == null) {
            getLogger().severe("Comando /" + nome + " não está no plugin.yml!");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    private void salvarArquivosPadrao() {
        saveDefaultConfig();
        // Adiciona ao config.yml as opções novas de atualizações do plugin (mantendo o que já foi editado).
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (String arq : List.of("ranks.yml", "minas.yml", "vendas.yml", "mensagens.yml", "loja.yml")) {
            if (!new File(getDataFolder(), arq).exists()) {
                saveResource(arq, false);
            }
        }
    }

    private void carregarConfiguracoes() {
        reloadConfig();
        mensagens.carregar();
        ranks.carregar();
        minas.carregar();
        vendas.carregar();
        picareta.carregar();
        loja.carregar();
    }

    private void aposInicializacao() {
        mundo = Bukkit.getWorld(getConfig().getString("mundo", "world"));
        if (mundo == null) {
            mundo = Bukkit.getWorlds().get(0);
            getLogger().warning("Mundo '" + getConfig().getString("mundo") + "' não encontrado. Usando '" + mundo.getName() + "'.");
        }
        configurarMundo(mundo);
        spawn = lerSpawn();

        if (!economia.conectar()) {
            getLogger().severe("Economia não encontrada! Instale o Vault + EssentialsX. Rankup e vendas ficarão desativados.");
        } else {
            getLogger().info("Economia conectada via Vault.");
        }
        placeholderApi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (placeholderApi) {
            new ExpansaoPAPI(this).register();
            getLogger().info("PlaceholderAPI detectado: placeholders %rankup_...% registrados.");
        }

        if (!estado.spawnConstruido()) {
            getLogger().info("Construindo o spawn pela primeira vez...");
            construtor.construirSpawn(mundo);
            estado.setSpawnConstruido(true);
        }
        prepararMinas(true);

        hologramas.iniciar(mundo);
        sobrevivencia.configurarTodos();
        visual.iniciar();
        minas.iniciarTarefa();
        efeitos.iniciar();
        combate.iniciar();

        getServer().getScheduler().runTaskTimer(this, () -> {
            jogadores.atualizarTop();
            jogadores.salvarAssincrono();
        }, 20L * 60 * 5, 20L * 60 * 5);
        getServer().getScheduler().runTaskTimer(this, jogadores::atualizarTop, 20L * 30, 20L * 30);

        executarComandosIniciais();
        pronto = true;
        getLogger().info("RankUP Supremo pronto! " + ranks.todos().size() + " ranks e " + minas.todas().size() + " minas.");
    }

    /** Posiciona, constrói (se necessário) e enche todas as minas. */
    private void prepararMinas(boolean resetar) {
        minas.posicionar(mundo);
        for (Mina m : minas.todas()) {
            if (!estado.minaConstruida(m.id())) {
                construtor.construirMina(m);
                estado.setMinaConstruida(m.id(), true);
            }
        }
        if (resetar) {
            minas.resetarTodas(false);
        }
    }

    private void configurarMundo(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.RESPAWN_RADIUS, 0);
        w.setGameRule(GameRules.PVP, true); // o dano só passa na mina PvP (ProtecaoListener)
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
    }

    private Location lerSpawn() {
        var c = getConfig();
        return new Location(mundo, c.getDouble("spawn.x", 0.5), c.getDouble("spawn.y", 101), c.getDouble("spawn.z", 8.5),
                (float) c.getDouble("spawn.yaw", 180), (float) c.getDouble("spawn.pitch", 0));
    }

    public void definirSpawn(Location l) {
        getConfig().set("spawn.x", Math.round(l.getX() * 10) / 10.0);
        getConfig().set("spawn.y", Math.round(l.getY() * 10) / 10.0);
        getConfig().set("spawn.z", Math.round(l.getZ() * 10) / 10.0);
        getConfig().set("spawn.yaw", (double) Math.round(l.getYaw()));
        getConfig().set("spawn.pitch", (double) Math.round(l.getPitch()));
        saveConfig();
        spawn = lerSpawn();
        mundo.setSpawnLocation(spawn);
    }

    /**
     * Executa (uma única vez) os comandos de "primeira-inicializacao" e cada bloco de "comandos-unicos".
     * Assim, atualizações do servidor (ex.: criação do mundo Sobrevivência) também chegam em servidores já existentes.
     */
    private void executarComandosIniciais() {
        long atraso = 40L;
        if (!estado.comandosIniciaisExecutados()) {
            List<String> comandos = getConfig().getStringList("primeira-inicializacao.comandos");
            getLogger().info("Primeira inicialização: configurando grupos e permissões (" + comandos.size() + " comandos)...");
            atraso = agendarComandos(comandos, atraso, () -> {
                estado.setComandosIniciaisExecutados(true);
                getLogger().info("Grupos e permissões configurados com sucesso!");
            });
        }
        ConfigurationSection blocos = getConfig().getConfigurationSection("comandos-unicos");
        if (blocos != null) {
            for (String id : blocos.getKeys(false)) {
                if (estado.blocoExecutado(id)) {
                    continue;
                }
                List<String> comandos = blocos.getStringList(id);
                getLogger().info("Executando a configuração '" + id + "' (" + comandos.size() + " comandos)...");
                atraso = agendarComandos(comandos, atraso, () -> {
                    estado.marcarBlocoExecutado(id);
                    getLogger().info("Configuração '" + id + "' concluída!");
                });
            }
        }
    }

    private long agendarComandos(List<String> comandos, long inicio, Runnable aoTerminar) {
        long atraso = inicio;
        for (String c : comandos) {
            getServer().getScheduler().runTaskLater(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c), atraso);
            atraso += 2L;
        }
        getServer().getScheduler().runTaskLater(this, aoTerminar, atraso + 20L);
        return atraso + 40L;
    }

    /** /rankupadmin recarregar */
    public void recarregar() {
        carregarConfiguracoes();
        if (mundo != null) {
            spawn = lerSpawn();
            prepararMinas(false);
            hologramas.iniciar(mundo);
            visual.iniciar();
        }
        sobrevivencia.configurarTodos();
        jogadores.atualizarTop();
    }

    public boolean ehMundoRankup(World w) {
        return w != null && w == mundo;
    }

    @Override
    public void onDisable() {
        if (minas != null) {
            minas.pararTarefa();
        }
        if (visual != null) {
            visual.parar();
        }
        if (efeitos != null) {
            efeitos.parar();
        }
        if (hologramas != null) {
            hologramas.removerTodos();
        }
        if (jogadores != null && pronto) {
            jogadores.salvarSincrono();
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new GeradorVazio();
    }

    // ------------------------------------------------------------------ Modo construção (admins)

    public boolean emModoConstrucao(Player p) {
        return construindo.contains(p.getUniqueId());
    }

    public boolean alternarModoConstrucao(Player p) {
        if (!construindo.remove(p.getUniqueId())) {
            construindo.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public void desativarModoConstrucao(Player p) {
        construindo.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Acessores

    public Estado estado() {
        return estado;
    }

    public Mensagens mensagens() {
        return mensagens;
    }

    public GerenciadorRanks ranks() {
        return ranks;
    }

    public GerenciadorMinas minas() {
        return minas;
    }

    public GerenciadorJogadores jogadores() {
        return jogadores;
    }

    public GerenciadorVendas vendas() {
        return vendas;
    }

    public GerenciadorPicareta picareta() {
        return picareta;
    }

    public GerenciadorVisual visual() {
        return visual;
    }

    public ConstrutorMundo construtor() {
        return construtor;
    }

    public Economia economia() {
        return economia;
    }

    public Acoes acoes() {
        return acoes;
    }

    public Menus menus() {
        return menus;
    }

    public GerenciadorLoja loja() {
        return loja;
    }

    public EfeitosPicareta efeitos() {
        return efeitos;
    }

    public CombateListener combate() {
        return combate;
    }

    public World mundo() {
        return mundo;
    }

    public Location spawn() {
        Location s = spawn;
        if (s == null) {
            World w = mundo != null ? mundo : Bukkit.getWorlds().get(0);
            return w.getSpawnLocation();
        }
        return s.clone();
    }

    public GerenciadorSobrevivencia sobrevivencia() {
        return sobrevivencia;
    }

    public GerenciadorHologramas hologramas() {
        return hologramas;
    }

    public boolean temPlaceholderAPI() {
        return placeholderApi;
    }
}
