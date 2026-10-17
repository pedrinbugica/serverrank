package dev.pedrobugica.npcvivos;

import dev.pedrobugica.npcvivos.comandos.AdminCommand;
import dev.pedrobugica.npcvivos.comandos.FaccaoCommand;
import dev.pedrobugica.npcvivos.comandos.MercenarioCommand;
import dev.pedrobugica.npcvivos.comandos.MissoesCommand;
import dev.pedrobugica.npcvivos.comandos.PovoCommand;
import dev.pedrobugica.npcvivos.comandos.ReinoCommand;
import dev.pedrobugica.npcvivos.combate.BossService;
import dev.pedrobugica.npcvivos.combate.CombateService;
import dev.pedrobugica.npcvivos.combate.DueloService;
import dev.pedrobugica.npcvivos.combate.MercenarioService;
import dev.pedrobugica.npcvivos.comercio.ComercioService;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.comportamento.ComportamentoService;
import dev.pedrobugica.npcvivos.comportamento.VidaService;
import dev.pedrobugica.npcvivos.core.NpcManager;
import dev.pedrobugica.npcvivos.core.Rotina;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.dados.Repositorio;
import dev.pedrobugica.npcvivos.dados.RepositorioMemoria;
import dev.pedrobugica.npcvivos.dados.SqliteRepositorio;
import dev.pedrobugica.npcvivos.dados.YamlRepositorio;
import dev.pedrobugica.npcvivos.dialogo.DialogoService;
import dev.pedrobugica.npcvivos.faccao.FaccaoService;
import dev.pedrobugica.npcvivos.faccao.GuardaService;
import dev.pedrobugica.npcvivos.listeners.CombateListener;
import dev.pedrobugica.npcvivos.listeners.NpcListener;
import dev.pedrobugica.npcvivos.menu.MenuListener;
import dev.pedrobugica.npcvivos.missao.MissaoService;
import dev.pedrobugica.npcvivos.mundo.DiplomaciaService;
import dev.pedrobugica.npcvivos.mundo.GuerraService;
import dev.pedrobugica.npcvivos.mundo.InvasaoService;
import dev.pedrobugica.npcvivos.mundo.MercadoService;
import dev.pedrobugica.npcvivos.mundo.MundoListener;
import dev.pedrobugica.npcvivos.mundo.MundoVivoService;
import dev.pedrobugica.npcvivos.povo.Corpos;
import dev.pedrobugica.npcvivos.povo.MoradorService;
import dev.pedrobugica.npcvivos.reino.ReinoService;
import dev.pedrobugica.npcvivos.upgrade.Pedra;
import dev.pedrobugica.npcvivos.upgrade.UpgradeListener;
import dev.pedrobugica.npcvivos.upgrade.UpgradeService;
import dev.pedrobugica.npcvivos.util.Economia;
import dev.pedrobugica.npcvivos.util.Mensagens;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * NpcVivos - NPCs que conversam, vendem, compram, lutam e lembram dos jogadores.
 * Fases 1 a 6 do plano: base, comércio, diálogos e missões, facções e reputação (com guardas e SQLite),
 * aprimoramento de itens e combate (mercenários, duelos e chefes).
 */
public final class NpcVivosPlugin extends JavaPlugin {

    private Mensagens mensagens;
    private Economia economia;
    private RepositorioMemoria repositorio;
    private NpcManager npcs;
    private ComercioService comercio;
    private DialogoService dialogo;
    private MissaoService missoes;
    private FaccaoService faccoes;
    private GuardaService guardas;
    private UpgradeService upgrades;
    private ComportamentoService comportamento;
    private CombateService combate;
    private MercenarioService mercenarios;
    private DueloService duelos;
    private BossService bosses;
    private MundoVivoService mundo;
    private VidaService vida;
    private MercadoService mercado;
    private DiplomaciaService diplomacia;
    private GuerraService guerras;
    private InvasaoService invasoes;
    private Corpos corpos;
    private MoradorService moradores;
    private ReinoService reinos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        mensagens = new Mensagens(this);
        mensagens.carregar();
        economia = new Economia();
        // Dados: SQLite (padrão, migra o dados.yml antigo sozinho) ou YAML
        repositorio = "yaml".equalsIgnoreCase(getConfig().getString("dados.tipo", "sqlite"))
                ? new YamlRepositorio(this) : new SqliteRepositorio(this);
        repositorio.carregar();
        upgrades = new UpgradeService(this);
        upgrades.carregar(); // antes das missões: recompensas podem ter pedras e itens aprimorados
        npcs = new NpcManager(this);
        comercio = new ComercioService(this);
        dialogo = new DialogoService(this);
        missoes = new MissaoService(this);
        faccoes = new FaccaoService(this);
        guardas = new GuardaService(this);
        comportamento = new ComportamentoService(this);
        combate = new CombateService(this);
        mercenarios = new MercenarioService(this);
        duelos = new DueloService(this);
        bosses = new BossService(this);
        mundo = new MundoVivoService(this);
        vida = new VidaService(this);
        mercado = new MercadoService(this);
        diplomacia = new DiplomaciaService(this);
        guerras = new GuerraService(this);
        invasoes = new InvasaoService(this);
        mundo.carregar();
        comportamento.carregar();
        if (!repositorio.executado("conteudo-fase4")) {
            faccoes.copiarExemplos();
            missoes.copiarExemplo("borin_encomenda_especial");
        }
        if (!repositorio.executado("topico-borin-vila")) {
            // quem já tinha o borin.yml da 0.3.0 ganha o tópico da Vila Aurora (uma vez só: se a staff apagar, não volta)
            dialogo.adicionarTopico("borin", "vila", "O que é a Vila Aurora?");
            repositorio.marcarExecutado("topico-borin-vila");
        }
        if (!repositorio.executado("conteudo-fase7")) {
            faccoes.copiarExemplo("mercadores_livres");
        }
        faccoes.carregar();
        if (!repositorio.executado("conteudo-fase3")) {
            dialogo.copiarExemplos();
            missoes.copiarExemplos();
        }
        dialogo.carregar();
        missoes.carregar();
        npcs.carregar();
        // Moradores humanos e reinos (corpo de gente para aldeões e soldados)
        corpos = new Corpos(this);
        moradores = new MoradorService(this, corpos);
        moradores.carregar();
        reinos = new ReinoService(this, corpos);
        reinos.carregar();

        var pm = getServer().getPluginManager();
        pm.registerEvents(corpos, this);
        pm.registerEvents(moradores, this);
        pm.registerEvents(reinos, this);
        pm.registerEvents(new NpcListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(missoes, this);
        pm.registerEvents(guardas, this);
        pm.registerEvents(new UpgradeListener(this), this);
        pm.registerEvents(new CombateListener(this), this);
        pm.registerEvents(new MundoListener(this), this);
        FaccaoCommand fc = new FaccaoCommand(this);
        for (String nome : List.of("faccao", "reputacao")) {
            PluginCommand c = getCommand(nome);
            if (c != null) {
                c.setExecutor(fc);
                c.setTabCompleter(fc);
            }
        }
        PluginCommand cmdConversa = getCommand("npcconversa");
        if (cmdConversa != null) {
            cmdConversa.setExecutor((s, c, l, a) -> {
                if (s instanceof org.bukkit.entity.Player p && a.length == 1) {
                    dialogo.clicar(p, a[0]);
                }
                return true;
            });
            cmdConversa.setTabCompleter((s, c, l, a) -> List.of());
        }
        PluginCommand cmdMercenario = getCommand("mercenario");
        if (cmdMercenario != null) {
            MercenarioCommand mc = new MercenarioCommand(this);
            cmdMercenario.setExecutor(mc);
            cmdMercenario.setTabCompleter(mc);
        }
        PluginCommand cmdMissoes = getCommand("missoes");
        if (cmdMissoes != null) {
            MissoesCommand mc = new MissoesCommand(this);
            cmdMissoes.setExecutor(mc);
            cmdMissoes.setTabCompleter(mc);
        }
        ReinoCommand rc = new ReinoCommand(this);
        PovoCommand pc = new PovoCommand(this);
        for (String nome : List.of("reino", "reinoadmin")) {
            PluginCommand c = getCommand(nome);
            if (c != null) {
                c.setExecutor(rc);
                c.setTabCompleter(rc);
            }
        }
        for (String nome : List.of("povo", "trabalho")) {
            PluginCommand c = getCommand(nome);
            if (c != null) {
                c.setExecutor(pc);
                c.setTabCompleter(pc);
            }
        }
        PluginCommand cmd = getCommand("npcv");
        if (cmd != null) {
            AdminCommand admin = new AdminCommand(this);
            cmd.setExecutor(admin);
            cmd.setTabCompleter(admin);
        }

        // Depois que o servidor terminar de ligar (mundos do Multiverse e economia do Essentials prontos)
        Bukkit.getScheduler().runTask(this, () -> {
            if (!economia.conectar()) {
                getLogger().severe("Economia não encontrada (Vault + EssentialsX). As lojas dos NPCs ficam desligadas.");
            }
            criarExemplos();
            atualizarExemplosFase3();
            atualizarExemplosFase4();
            atualizarExemplosFase5();
            npcs.spawnarTodos();
            npcs.iniciarTarefas();
            comercio.iniciarReposicao();
            guardas.iniciar();
            criarExemplosFase6();
            criarExemplosFase7();
            comportamento.prepararTodos();
            comportamento.iniciar();
            mercenarios.iniciar();
            duelos.iniciar();
            bosses.iniciar();
            guerras.carregar();
            mundo.iniciar();
            atualizarExemplosFase9();
            vida.iniciar();
            moradores.iniciar();
            reinos.iniciar();
            corpos.iniciar();
            configurarPermissoes();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new dev.pedrobugica.npcvivos.hook.ExpansaoPAPI(this).register();
                getLogger().info("PlaceholderAPI detectado: %npcvivos_tag%, %npcvivos_faccao%, %npcvivos_reputacao_<facção>%...");
            }
        });
        Bukkit.getScheduler().runTaskTimer(this, () -> repositorio.salvarAssincrono(), 20L * 60 * 5, 20L * 60 * 5);
        getLogger().info("NpcVivos ligado.");
    }

    @Override
    public void onDisable() {
        if (reinos != null) {
            reinos.parar(); // exércitos são temporários
        }
        if (moradores != null) {
            moradores.parar();
        }
        if (corpos != null) {
            corpos.parar(); // tira os corpos e mostra os mobs de novo
        }
        if (vida != null) {
            vida.parar(); // tira os balões de fala
        }
        if (invasoes != null) {
            invasoes.encerrar(); // soldados são temporários: somem antes de tudo
            guerras.parar();
            mundo.parar();
        }
        if (npcs != null) {
            npcs.pararTarefas();
            npcs.removerTodasEntidades();
        }
        if (comercio != null) {
            comercio.pararReposicao();
        }
        if (guardas != null) {
            guardas.parar();
        }
        if (comportamento != null) {
            comportamento.parar();
            mercenarios.parar();
            duelos.parar();
            bosses.parar();
            comportamento.esquecerTodos();
        }
        if (repositorio != null) {
            repositorio.salvarSincrono();
        }
    }

    public void recarregar() {
        invasoes.encerrar(); // o npcs.carregar() abaixo apaga os NPCs temporários
        reloadConfig();
        mundo.carregar();
        mensagens.carregar();
        upgrades.carregar();
        dialogo.carregar();
        missoes.carregar();
        faccoes.carregar();
        npcs.carregar();
        npcs.spawnarTodos();
        comportamento.carregar();
        comportamento.prepararTodos();
        comportamento.iniciar();
        bosses.iniciar();
        mundo.iniciar();
        vida.iniciar();
        moradores.carregar();
        reinos.carregar();
        reinos.iniciar();
        moradores.iniciar();
        corpos.iniciar();
    }

    /**
     * Vida nos NPCs de exemplo: a Marta e a Iara passeiam, o Tomás faz a ronda da praça e todo mundo
     * fala alguma coisa sozinho de vez em quando (balão em cima da cabeça).
     */
    private void atualizarExemplosFase9() {
        if (repositorio.executado("conteudo-vida")) {
            return;
        }
        int feitos = 0;
        for (NpcVivo npc : npcs.todos()) {
            if (npc.temporario() || npc.rotina() != null) {
                continue;
            }
            Rotina r = switch (npc.id()) {
                case "fazendeira_marta" -> new Rotina(Rotina.Tipo.PASSEAR, 5, new java.util.ArrayList<>(), new java.util.ArrayList<>(List.of(
                        "Essas cenouras não vão se colher sozinhas...", "Choveu bem esta semana, a plantação agradece!",
                        "Alguém viu meu ancinho?", "Pão fresquinho saindo do forno!")), true);
                case "mercadora_iara" -> new Rotina(Rotina.Tipo.PASSEAR, 3, new java.util.ArrayList<>(), new java.util.ArrayList<>(List.of(
                        "Mercadorias do Sul, do Norte e de além-mar!", "Preço bom é preço de quem chega cedo!",
                        "A caravana parte quando a lua cheia subir.")), true);
                case "ferreiro_borin" -> new Rotina(Rotina.Tipo.PARADO, 3, new java.util.ArrayList<>(), new java.util.ArrayList<>(List.of(
                        "*tim* *tim* *tim*", "Esse ferro está bom... mas podia estar melhor.",
                        "Uma lâmina +10? Só com muita sorte e muita pedra!")), false);
                case "guarda_tomas" -> {
                    Location c = npc.local();
                    java.util.List<org.bukkit.util.Vector> ronda = new java.util.ArrayList<>();
                    if (c != null) {
                        for (int[] d : new int[][]{{0, 0}, {6, -4}, {0, -9}, {-6, -4}}) {
                            Location p = noChao(c, d[0], d[1]);
                            ronda.add(new org.bukkit.util.Vector(p.getBlockX(), p.getBlockY(), p.getBlockZ()));
                        }
                    }
                    yield new Rotina(ronda.isEmpty() ? Rotina.Tipo.PARADO : Rotina.Tipo.PATRULHA, 6, ronda, new java.util.ArrayList<>(List.of(
                            "Tudo calmo na Vila Aurora.", "Olho vivo, cidadãos!", "Nada de confusão por aqui, hein?")), false);
                }
                case "guia_lucas" -> new Rotina(Rotina.Tipo.PARADO, 3, new java.util.ArrayList<>(), new java.util.ArrayList<>(List.of(
                        "Novo por aqui? Clique em mim!", "Use /minas para começar a minerar!", "Quer aventura? A Sobrevivência é RPG!")), false);
                default -> null;
            };
            if (r != null) {
                npc.setRotina(r);
                npcs.salvar(npc);
                feitos++;
            }
        }
        repositorio.marcarExecutado("conteudo-vida");
        repositorio.salvarAssincrono();
        if (feitos > 0) {
            getLogger().info("Vida: " + feitos + " NPC(s) de exemplo agora passeiam, patrulham e falam sozinhos.");
        }
    }

    /**
     * Fase 7: nasce a Iara, dos Mercadores Livres (aliados da Vila Aurora). Ela troca estoque com o Borin e a Marta
     * (comércio entre NPCs) e vende coisas que vêm do Sul: se a Vila entrar em guerra com a Irmandade, essas rotas
     * fecham (sem reposição e mais caras).
     */
    private void criarExemplosFase7() {
        if (repositorio.executado("conteudo-fase7")) {
            return;
        }
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupSupremo");
        String nome = rankup != null ? rankup.getConfig().getString("sobrevivencia.mundo", "sobrevivencia") : "sobrevivencia";
        World sobrevivencia = Bukkit.getWorld(nome);
        if (sobrevivencia == null || faccoes.obter("mercadores_livres") == null) {
            return; // tenta de novo quando o mundo carregar
        }
        Location centro = sobrevivencia.getSpawnLocation().add(0.5, 0, 0.5);
        if (npcs.obter("mercadora_iara") == null) {
            NpcVivo iara = npcs.criar("mercadora_iara", Papel.COMERCIANTE, "<aqua><bold>Iara, a Mercadora", olhandoPara(noChao(centro, -5, -5), centro));
            iara.setSkin("makena");
            iara.setFaccao("mercadores_livres");
            iara.saudacao().clear();
            iara.saudacao().add("Olá, {jogador}! Acabei de chegar com a caravana. Coisas do Sul, do Norte, de todo canto!");
            iara.retorno().clear();
            iara.retorno().add("Voltou, {jogador}! A caravana trouxe novidades.");
            iara.retorno().add("{jogador}! Os preços mudam toda hora, aproveite enquanto está barato.");
            compra(iara, Material.BREAD, 16, 30, 30, 10);
            compraImportada(iara, Material.ENDER_PEARL, 1, 60, 4, 1, "irmandade_do_sul");
            compraImportada(iara, Material.BLAZE_ROD, 1, 45, 6, 1, "irmandade_do_sul");
            compra(iara, Material.GLOWSTONE_DUST, 8, 40, 8, 2);
            compra(iara, Material.ARROW, 16, 20, 16, 4);
            compra(iara, Material.NAME_TAG, 1, 120, 2, 0.25);
            compra(iara, Material.SADDLE, 1, 150, 2, 0.25);
            iara.compra().add(new ItemLoja("compra-" + iara.compra().size(), upgrades.itens().criarPedra(dev.pedrobugica.npcvivos.upgrade.Pedra.COMUM, 1),
                    1, 45, 12, 3, 0));
            venda(iara, Material.LEATHER, 8, 16, 64);
            venda(iara, Material.WHITE_WOOL, 16, 12, 128);
            venda(iara, Material.HONEY_BOTTLE, 4, 20, 32);
            venda(iara, Material.GOLD_INGOT, 8, 40, 64);
            npcs.salvar(iara);
            npcs.spawnar(iara);
        }
        repositorio.marcarExecutado("conteudo-fase7");
        repositorio.salvarAssincrono();
        getLogger().info("Fase 7: Iara, dos Mercadores Livres, chegou à praça da Sobrevivência.");
    }

    private static void compraImportada(NpcVivo npc, Material m, int qtd, double preco, int estoque, double repoe, String de) {
        npc.compra().add(new ItemLoja("compra-" + npc.compra().size(), new ItemStack(m), qtd, preco, estoque, repoe, 0, de));
    }

    /**
     * Fase 6: nascem a duelista Kaela e o mercenário Dargo na praça da Sobrevivência.
     * Chefe não vem de exemplo: ele precisa de uma arena escolhida pela staff (/npcv criar ... BOSS).
     */
    private void criarExemplosFase6() {
        if (repositorio.executado("conteudo-fase6") || !comportamento.ativo()) {
            return;
        }
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupSupremo");
        String nome = rankup != null ? rankup.getConfig().getString("sobrevivencia.mundo", "sobrevivencia") : "sobrevivencia";
        World sobrevivencia = Bukkit.getWorld(nome);
        if (sobrevivencia == null) {
            return; // tenta de novo no próximo /npcv reload ou quando o mundo carregar
        }
        Location centro = sobrevivencia.getSpawnLocation().add(0.5, 0, 0.5);
        if (npcs.obter("duelista_kaela") == null) {
            NpcVivo kaela = npcs.criar("duelista_kaela", Papel.DUELISTA, "<red><bold>Kaela, a Duelista", olhandoPara(noChao(centro, 0, -5), centro));
            kaela.setSkin("zuri-fino");
            kaela.setItemNaMao(Material.IRON_SWORD);
            kaela.setFaccao("vila_aurora");
            kaela.saudacao().clear();
            kaela.saudacao().add("Então você é {jogador}? Dizem que sabe lutar. Quer provar?");
            kaela.retorno().clear();
            kaela.retorno().addAll(List.of("De volta para mais, {jogador}?", "Pegue a espada, {jogador}. A areia está esperando."));
            kaela.falas().addAll(List.of(
                    "No duelo não vale fugir: saia da arena e você perde a aposta.",
                    "Ganhe na dificuldade Lendária e você leva um título que ninguém mais tem.",
                    "Arma aprimorada ajuda, sim. Mas não salva quem não sabe desviar."));
            npcs.salvar(kaela);
            npcs.spawnar(kaela);
        }
        if (npcs.obter("mercenario_dargo") == null) {
            NpcVivo dargo = npcs.criar("mercenario_dargo", Papel.MERCENARIO, "<dark_red><bold>Dargo, o Mercenário", olhandoPara(noChao(centro, 5, -5), centro));
            dargo.setSkin("ari");
            dargo.setItemNaMao(Material.IRON_AXE);
            dargo.setFaccao("vila_aurora");
            dargo.saudacao().clear();
            dargo.saudacao().add("Eu luto por quem paga, {jogador}. E eu não sou barato.");
            dargo.retorno().clear();
            dargo.retorno().add("O machado está afiado, {jogador}. Quanto você tem aí?");
            dargo.falas().addAll(List.of(
                    "Me contrate e eu te sigo e bato em quem te bater.",
                    "Só trabalho em mundo onde o PvP está ligado. No spawn eu fico de bobeira.",
                    "Se eu cair em combate, preciso de um tempo para me recuperar antes do próximo contrato."));
            npcs.salvar(dargo);
            npcs.spawnar(dargo);
        }
        repositorio.marcarExecutado("conteudo-fase6");
        repositorio.salvarAssincrono();
        getLogger().info("Fase 6: duelista Kaela e mercenário Dargo criados na Sobrevivência.");
    }

    /** Fase 5: o Borin passa a vender pedras de aprimoramento comuns (a forja aparece sozinha para ferreiros). */
    private void atualizarExemplosFase5() {
        if (repositorio.executado("conteudo-fase5")) {
            return;
        }
        NpcVivo borin = npcs.obter("ferreiro_borin");
        if (borin == null) {
            return; // tenta de novo quando o Borin existir
        }
        boolean jaVende = borin.compra().stream().anyMatch(i -> upgrades.itens().pedra(i.item()) != null);
        if (!jaVende) {
            borin.compra().add(new ItemLoja("compra-" + borin.compra().size(), upgrades.itens().criarPedra(Pedra.COMUM, 1),
                    1, 40, 16, 4, 0));
            npcs.salvar(borin);
        }
        repositorio.marcarExecutado("conteudo-fase5");
        repositorio.salvarAssincrono();
        getLogger().info("Fase 5: o Borin agora vende Pedras de Aprimoramento Comuns e aprimora armas e armaduras.");
    }

    /** Fase 4: Borin e Marta entram na facção Vila Aurora e nasce o guarda Tomás na praça da Sobrevivência. */
    private void atualizarExemplosFase4() {
        if (repositorio.executado("conteudo-fase4") || faccoes.obter("vila_aurora") == null) {
            return;
        }
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupSupremo");
        String nome = rankup != null ? rankup.getConfig().getString("sobrevivencia.mundo", "sobrevivencia") : "sobrevivencia";
        World sobrevivencia = Bukkit.getWorld(nome);
        if (sobrevivencia == null) {
            return; // tenta de novo quando o mundo carregar
        }
        for (String id : List.of("ferreiro_borin", "fazendeira_marta")) {
            NpcVivo npc = npcs.obter(id);
            if (npc != null && npc.faccao() == null) {
                npc.setFaccao("vila_aurora");
                npcs.salvar(npc);
                npcs.spawnar(npc);
            }
        }
        if (npcs.obter("guarda_tomas") == null) {
            Location centro = sobrevivencia.getSpawnLocation().add(0.5, 0, 0.5);
            Location l = olhandoPara(noChao(centro, 0, 4), centro);
            NpcVivo tomas = npcs.criar("guarda_tomas", Papel.GUARDA, "<aqua><bold>Tomás, o Guarda", l);
            tomas.setSkin("efe");
            tomas.setItemNaMao(Material.IRON_SWORD);
            tomas.setFaccao("vila_aurora");
            tomas.saudacao().clear();
            tomas.saudacao().add("Alto lá! Ah, é você, {jogador}. Bem-vindo à Vila Aurora. Aqui a gente vive em paz.");
            tomas.retorno().clear();
            tomas.retorno().add("Tudo tranquilo por aqui, {jogador}.");
            tomas.falas().addAll(List.of(
                    "Quem ataca o povo da vila vira inimigo... e eu não deixo inimigo passar.",
                    "Quer fazer parte da vila? Use <yellow>/faccao entrar vila_aurora</yellow>. Os membros ganham desconto nas lojas!",
                    "Use <yellow>/reputacao</yellow> para ver como a vila te vê. Missões e vendas para o Borin e a Marta ajudam.",
                    "Os monstros que você mata perto da vila também contam a seu favor.",
                    "A Irmandade do Sul não gosta da gente. Se jurar lealdade a eles, nada de negócio por aqui!"));
            npcs.salvar(tomas);
            npcs.spawnar(tomas);
        }
        repositorio.marcarExecutado("conteudo-fase4");
        repositorio.salvarAssincrono();
        getLogger().info("Fase 4: facção Vila Aurora configurada e guarda Tomás criado.");
    }

    /** Fase 3: o Borin e a Marta criados pela versão 0.2.0 ganham conversa com opções e missões. */
    private void atualizarExemplosFase3() {
        if (repositorio.executado("conteudo-fase3")) {
            return;
        }
        if (!repositorio.executado("exemplos-v1")) {
            // servidor novo: os exemplos já nascem com a conversa (em criarExemplos)
            repositorio.marcarExecutado("conteudo-fase3");
            return;
        }
        for (String[] par : new String[][]{{"ferreiro_borin", "borin"}, {"fazendeira_marta", "marta"}}) {
            NpcVivo npc = npcs.obter(par[0]);
            if (npc != null && npc.arvore() == null && dialogo.dialogo(par[1]) != null) {
                npc.setArvore(par[1]);
                npc.papeis().add(Papel.MISSIONARIO);
                npcs.salvar(npc);
            }
        }
        repositorio.marcarExecutado("conteudo-fase3");
        repositorio.salvarAssincrono();
    }

    /** Uma vez só: a staff (grupo admin) ganha os comandos do /npcv. */
    private void configurarPermissoes() {
        if (repositorio.executado("permissoes-v1") || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        // Num servidor novo, o grupo admin só é criado pelo RankupSupremo segundos depois. O LuckPerms roda os
        // comandos em fila, então criar antes garante que a permissão entra (se já existir, ele só avisa).
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup admin");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group admin permission set npcvivos.admin.* true");
        repositorio.marcarExecutado("permissoes-v1");
        repositorio.salvarAssincrono();
    }

    // ------------------------------------------------------------------ NPCs DE EXEMPLO

    /**
     * Cria os NPCs de exemplo; cada parte espera o seu mundo existir (chamado ao ligar e quando um mundo carrega).
     * No fim sempre damos cérebro a quem luta: num servidor novo a Sobrevivência só carrega depois do plugin ligar,
     * então os NPCs da fase 6 nascem aqui, fora do prepararTodos() do onEnable.
     */
    public void criarExemplos() {
        if (repositorio.executado("exemplos-v1")) {
            if (faccoes != null) {
                atualizarExemplosFase4();
                atualizarExemplosFase5();
                criarExemplosFase6();
                criarExemplosFase7();
                comportamento.prepararTodos();
            }
            return;
        }
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupSupremo");
        FileConfiguration rc = rankup != null ? rankup.getConfig() : null;

        // Guia no spawn do Rankup
        World mundoRankup = Bukkit.getWorld(rc != null ? rc.getString("mundo", "world") : "world");
        if (mundoRankup != null && !repositorio.executado("exemplo-guia") && npcs.obter("guia_lucas") == null) {
            Location spawn = rc != null
                    ? new Location(mundoRankup, rc.getDouble("spawn.x", 0.5), rc.getDouble("spawn.y", 101), rc.getDouble("spawn.z", 8.5))
                    : mundoRankup.getSpawnLocation();
            Location l = olhandoPara(NpcManager.lugarSeguro(spawn.clone().add(3, 0, -2), 4), spawn);
            NpcVivo guia = npcs.criar("guia_lucas", Papel.GUIA, "<gold><bold>Lucas, o Guia", l);
            guia.setSkin("sunny");
            guia.saudacao().clear();
            guia.saudacao().add("Olá, {jogador}! Eu sou o Lucas, o guia do RankUP Supremo. Clique em mim de novo para ouvir uma dica!");
            guia.retorno().clear();
            guia.retorno().add("Olá de novo, {jogador}! Quer mais uma dica?");
            guia.falas().addAll(List.of(
                    "Use <yellow>/minas</yellow> para ir até a mina do seu rank e minere tudo!",
                    "Venda os minérios com <yellow>/vender</yellow> e depois use <yellow>/rankup</yellow> para subir de rank.",
                    "Os <light_purple>tokens</light_purple> que caem minerando compram encantamentos especiais em <yellow>/picareta</yellow>.",
                    "No último rank, use <yellow>/prestigio</yellow> para renascer mais forte e vender mais caro.",
                    "Na <yellow>/loja</yellow> tem boosters que aumentam o dinheiro das vendas por um tempo.",
                    "Cuidado na <red>Mina PvP</red>: lá dentro os outros jogadores podem te atacar!",
                    "Quer um survival normal? Use <yellow>/sobrevivencia</yellow>. O inventário e o dinheiro de lá são separados.",
                    "Na Sobrevivência, o Ferreiro Borin e a Fazendeira Marta ficam no spawn. Fale com eles!"));
            npcs.salvar(guia);
            npcs.spawnar(guia);
            repositorio.marcarExecutado("exemplo-guia");
        }

        // Comerciantes no spawn da Sobrevivência
        String nomeSobrevivencia = rc != null ? rc.getString("sobrevivencia.mundo", "sobrevivencia") : "sobrevivencia";
        World sobrevivencia = Bukkit.getWorld(nomeSobrevivencia);
        if (sobrevivencia != null && !repositorio.executado("exemplo-sobrevivencia")) {
            Location centro = sobrevivencia.getSpawnLocation().add(0.5, 0, 0.5);
            if (npcs.obter("ferreiro_borin") == null) {
                Location l = olhandoPara(noChao(centro, 4, 0), centro);
                NpcVivo borin = npcs.criar("ferreiro_borin", Papel.COMERCIANTE, "<gold><bold>Borin, o Ferreiro", l);
                borin.papeis().add(Papel.FERREIRO);
                borin.setSkin("steve");
                borin.setItemNaMao(Material.IRON_PICKAXE);
                borin.setArvore("borin");
                borin.papeis().add(Papel.MISSIONARIO);
                borin.saudacao().clear();
                borin.saudacao().add("Bem-vindo à minha forja, {jogador}! Vendo ferramentas, compro minérios e conserto o que estiver gasto.");
                borin.retorno().clear();
                borin.retorno().addAll(List.of("Olá de novo, {jogador}! Trouxe o ferro?", "Ah, {jogador}! A forja está quente, o que vai ser?"));
                compra(borin, Material.IRON_SWORD, 1, 70, 10, 2);
                compra(borin, Material.IRON_PICKAXE, 1, 80, 10, 2);
                compra(borin, Material.IRON_AXE, 1, 80, 10, 2);
                compra(borin, Material.IRON_SHOVEL, 1, 35, 10, 2);
                compra(borin, Material.SHIELD, 1, 60, 6, 1);
                compra(borin, Material.IRON_HELMET, 1, 90, 5, 1);
                compra(borin, Material.IRON_CHESTPLATE, 1, 140, 5, 1);
                compra(borin, Material.IRON_LEGGINGS, 1, 125, 5, 1);
                compra(borin, Material.IRON_BOOTS, 1, 70, 5, 1);
                compra(borin, Material.BUCKET, 1, 50, 8, 2);
                compra(borin, Material.ANVIL, 1, 350, 2, 0.5);
                venda(borin, Material.IRON_INGOT, 16, 48, 128);
                venda(borin, Material.GOLD_INGOT, 16, 64, 64);
                venda(borin, Material.COPPER_INGOT, 64, 80, 256);
                venda(borin, Material.COAL, 64, 64, 256);
                venda(borin, Material.DIAMOND, 1, 50, 16);
                npcs.salvar(borin);
                npcs.spawnar(borin);
            }
            if (npcs.obter("fazendeira_marta") == null) {
                Location l = olhandoPara(noChao(centro, -4, 0), centro);
                NpcVivo marta = npcs.criar("fazendeira_marta", Papel.COMERCIANTE, "<green><bold>Marta, a Fazendeira", l);
                marta.setSkin("alex-fino");
                marta.setItemNaMao(Material.IRON_HOE);
                marta.setArvore("marta");
                marta.papeis().add(Papel.MISSIONARIO);
                marta.saudacao().clear();
                marta.saudacao().add("Olá, {jogador}! Tenho sementes fresquinhas e compro tudo o que você colher.");
                marta.retorno().clear();
                marta.retorno().addAll(List.of("Olá de novo, {jogador}! Como está a plantação?", "{jogador}! Trouxe trigo hoje?"));
                compra(marta, Material.WHEAT_SEEDS, 16, 12, 0, 0);
                compra(marta, Material.CARROT, 16, 20, 0, 0);
                compra(marta, Material.POTATO, 16, 20, 0, 0);
                compra(marta, Material.MELON_SEEDS, 8, 20, 0, 0);
                compra(marta, Material.PUMPKIN_SEEDS, 8, 20, 0, 0);
                compra(marta, Material.BREAD, 16, 28, 20, 6);
                compra(marta, Material.BONE_MEAL, 32, 28, 0, 0);
                compra(marta, Material.OAK_SAPLING, 8, 20, 0, 0);
                venda(marta, Material.WHEAT, 64, 40, 640);
                venda(marta, Material.CARROT, 64, 20, 640);
                venda(marta, Material.POTATO, 64, 20, 640);
                venda(marta, Material.BEETROOT, 64, 30, 640);
                venda(marta, Material.MELON_SLICE, 64, 30, 640);
                venda(marta, Material.PUMPKIN, 16, 30, 160);
                venda(marta, Material.SUGAR_CANE, 64, 50, 640);
                venda(marta, Material.EGG, 16, 8, 160);
                venda(marta, Material.LEATHER, 16, 40, 64);
                npcs.salvar(marta);
                npcs.spawnar(marta);
            }
            repositorio.marcarExecutado("exemplo-sobrevivencia");
            getLogger().info("NPCs de exemplo criados na Sobrevivência (ferreiro e fazendeira).");
        }
        if (repositorio.executado("exemplo-guia") && repositorio.executado("exemplo-sobrevivencia")) {
            repositorio.marcarExecutado("exemplos-v1");
        }
        repositorio.salvarAssincrono();
        if (faccoes != null) {
            atualizarExemplosFase4();
            atualizarExemplosFase5();
            criarExemplosFase6();
            criarExemplosFase7();
            comportamento.prepararTodos();
        }
    }

    private static void compra(NpcVivo npc, Material m, int qtd, double preco, int estoque, double repoe) {
        npc.compra().add(new ItemLoja("compra-" + npc.compra().size(), new ItemStack(m), qtd, preco, estoque, repoe, 0));
    }

    private static void venda(NpcVivo npc, Material m, int qtd, double preco, int limite) {
        npc.venda().add(new ItemLoja("venda-" + npc.venda().size(), new ItemStack(m), qtd, preco, 0, 0, limite));
    }

    /** Ponto no chão firme a dx/dz do centro (ignora copas de árvore, para o NPC não ficar flutuando). */
    private static Location noChao(Location centro, int dx, int dz) {
        World w = centro.getWorld();
        int x = centro.getBlockX() + dx;
        int z = centro.getBlockZ() + dz;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return NpcManager.lugarSeguro(new Location(w, x + 0.5, y, z + 0.5), 4);
    }

    private static Location olhandoPara(Location de, Location para) {
        Vector dir = para.toVector().subtract(de.toVector());
        if (dir.lengthSquared() > 0.01) {
            de.setDirection(dir);
        }
        de.setPitch(0);
        return de;
    }

    // ------------------------------------------------------------------ ACESSORES

    public Mensagens mensagens() {
        return mensagens;
    }

    public Economia economia() {
        return economia;
    }

    public Repositorio repositorio() {
        return repositorio;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public ComercioService comercio() {
        return comercio;
    }

    public MissaoService missoes() {
        return missoes;
    }

    public FaccaoService faccoes() {
        return faccoes;
    }

    public VidaService vida() {
        return vida;
    }

    public MundoVivoService mundo() {
        return mundo;
    }

    public MercadoService mercado() {
        return mercado;
    }

    public DiplomaciaService diplomacia() {
        return diplomacia;
    }

    public GuerraService guerras() {
        return guerras;
    }

    public InvasaoService invasoes() {
        return invasoes;
    }

    public UpgradeService upgrades() {
        return upgrades;
    }

    public GuardaService guardas() {
        return guardas;
    }

    public DialogoService dialogo() {
        return dialogo;
    }

    public ComportamentoService comportamento() {
        return comportamento;
    }

    public CombateService combate() {
        return combate;
    }

    public MercenarioService mercenarios() {
        return mercenarios;
    }

    public DueloService duelos() {
        return duelos;
    }

    public Corpos corpos() {
        return corpos;
    }

    public MoradorService moradores() {
        return moradores;
    }

    public ReinoService reinos() {
        return reinos;
    }

    public BossService bosses() {
        return bosses;
    }
}
