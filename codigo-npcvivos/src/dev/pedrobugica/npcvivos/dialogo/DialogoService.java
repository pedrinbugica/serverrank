package dev.pedrobugica.npcvivos.dialogo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.EstadoNpc;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.dialogo.Dialogo.No;
import dev.pedrobugica.npcvivos.dialogo.Dialogo.Opcao;
import dev.pedrobugica.npcvivos.missao.Missao;
import dev.pedrobugica.npcvivos.util.Texto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Conversas com os NPCs.
 * - Sem árvore e sem missões: o NPC fala (com memória) e abre a loja, ou dá dicas (guia).
 * - Com árvore (dialogos/<id>.yml) ou missões: a fala aparece no chat com opções clicáveis.
 *   As missões do NPC entram sozinhas como opções ("Missão: ..." e "Entregar: ...").
 */
public final class DialogoService {

    public static final List<String> PADRAO = List.of("borin", "marta");

    private record Escolha(String texto, String dica, Runnable acao) {
    }

    private record Clique(UUID jogador, int conversa, NpcVivo npc, Runnable acao, long expira) {
    }

    /** Códigos das opções clicáveis que ainda valem. */
    private final Map<String, Clique> cliques = new ConcurrentHashMap<>();

    private final NpcVivosPlugin plugin;
    private final File pasta;
    private final Map<String, Dialogo> dialogos = new HashMap<>();
    private final Map<String, Long> ultimoClique = new ConcurrentHashMap<>();
    /** Número da conversa atual de cada jogador: opções de conversas antigas param de funcionar. */
    private final Map<UUID, Integer> conversa = new ConcurrentHashMap<>();
    private final Set<String> avisados = ConcurrentHashMap.newKeySet();

    public DialogoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "dialogos");
    }

    // ------------------------------------------------------------------ ARQUIVOS

    /**
     * Atualização: põe num diálogo que já existe um tópico novo do arquivo padrão, mais a opção que leva até ele
     * na primeira tela. O resto do arquivo (o que a staff editou) fica como está.
     */
    public void adicionarTopico(String arquivo, String no, String textoOpcao) {
        File f = new File(pasta, arquivo + ".yml");
        if (!f.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource("dialogos/" + arquivo + ".yml")) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            if (in == null || y.contains("nos." + no)) {
                return;
            }
            YamlConfiguration padrao = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection novo = padrao.getConfigurationSection("nos." + no);
            if (novo == null) {
                return;
            }
            for (Map.Entry<String, Object> e : novo.getValues(true).entrySet()) {
                if (!(e.getValue() instanceof ConfigurationSection)) {
                    y.set("nos." + no + "." + e.getKey(), e.getValue());
                }
            }
            String inicio = y.getString("inicio", "entrada");
            List<Map<?, ?>> opcoes = new ArrayList<>(y.getMapList("nos." + inicio + ".opcoes"));
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("texto", textoOpcao);
            op.put("ir", no);
            opcoes.add(op);
            y.set("nos." + inicio + ".opcoes", opcoes);
            y.save(f);
            plugin.getLogger().info("Diálogo " + arquivo + ": tópico novo '" + no + "' adicionado.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível atualizar dialogos/" + arquivo + ".yml", e);
        }
    }

    public void copiarExemplos() {
        for (String id : PADRAO) {
            if (!new File(pasta, id + ".yml").exists()) {
                plugin.saveResource("dialogos/" + id + ".yml", false);
            }
        }
    }

    public void carregar() {
        dialogos.clear();
        if (!pasta.exists() && !pasta.mkdirs()) {
            plugin.getLogger().warning("Não foi possível criar a pasta dialogos/.");
        }
        File[] arquivos = pasta.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (arquivos != null) {
            for (File f : arquivos) {
                String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                try {
                    dialogos.put(id, ler(id, YamlConfiguration.loadConfiguration(f)));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Diálogo inválido em dialogos/" + f.getName(), e);
                }
            }
        }
        plugin.getLogger().info(dialogos.size() + " diálogo(s) carregado(s).");
    }

    private Dialogo ler(String id, YamlConfiguration y) {
        Map<String, No> nos = new LinkedHashMap<>();
        ConfigurationSection sec = y.getConfigurationSection("nos");
        if (sec != null) {
            for (String nid : sec.getKeys(false)) {
                ConfigurationSection n = sec.getConfigurationSection(nid);
                if (n == null) {
                    continue;
                }
                List<Opcao> opcoes = new ArrayList<>();
                for (Map<?, ?> o : n.getMapList("opcoes")) {
                    Map<String, Object> se = new LinkedHashMap<>();
                    if (o.get("se") instanceof Map<?, ?> cond) {
                        cond.forEach((k, v) -> se.put(String.valueOf(k), v));
                    }
                    List<String> cmds = new ArrayList<>();
                    if (o.get("comandos") instanceof List<?> l) {
                        l.forEach(x -> cmds.add(String.valueOf(x)));
                    }
                    opcoes.add(new Opcao(String.valueOf(o.get("texto")), se, str(o.get("ir")), str(o.get("acao")),
                            str(o.get("missao")), cmds, str(o.get("fala"))));
                }
                nos.put(nid, new No(nid, lista(n, "fala"), lista(n, "fala_retorno"), opcoes));
            }
        }
        String inicio = y.getString("inicio", nos.isEmpty() ? "inicio" : nos.keySet().iterator().next());
        return new Dialogo(id, inicio, nos);
    }

    private static List<String> lista(ConfigurationSection s, String chave) {
        return s.isList(chave) ? s.getStringList(chave) : (s.isString(chave) ? List.of(s.getString(chave)) : List.of());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public Dialogo dialogo(String id) {
        return id == null ? null : dialogos.get(id.toLowerCase(Locale.ROOT));
    }

    /** Ids das conversas carregadas, em ordem alfabética (para o menu de edição). */
    public List<String> ids() {
        List<String> l = new ArrayList<>(dialogos.keySet());
        l.sort(null);
        return l;
    }

    // ------------------------------------------------------------------ CONVERSA

    public void interagir(Player p, NpcVivo npc) {
        String chave = p.getUniqueId() + ":" + npc.id();
        long agora = System.currentTimeMillis();
        Long antes = ultimoClique.put(chave, agora);
        if (antes != null && agora - antes < 600) {
            return; // o jogo manda o clique duas vezes às vezes
        }
        if (plugin.vida() != null) {
            plugin.vida().ocupar(npc, 25); // para de passear enquanto conversa
        }
        UUID id = p.getUniqueId();
        int visitas = plugin.repositorio().visitas(id, npc.id());
        plugin.repositorio().registrarVisita(id, npc.id());
        p.playSound(npc.local() != null ? npc.local() : p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.1f);

        var faccaoNpc = plugin.faccoes().daNpc(npc);
        if (faccaoNpc != null && plugin.faccoes().nivel(p.getUniqueId(), faccaoNpc.id()) == dev.pedrobugica.npcvivos.faccao.NivelReputacao.INIMIGO) {
            falar(p, npc, sortear(plugin.mensagens().lista("npc-recusa-inimigo")));
            return;
        }
        plugin.comportamento().ocupar(npc, p, EstadoNpc.CONVERSANDO);
        Dialogo d = dialogo(npc.arvore());
        boolean temMissao = !plugin.missoes().oferecidas(p, npc).isEmpty() || !plugin.missoes().entregaveis(p, npc).isEmpty();
        if (d == null && !temMissao && !temCombate(npc)) {
            conversaSimples(p, npc, visitas);
            return;
        }
        No raiz = d != null ? d.nos().get(d.inicio()) : null;
        mostrar(p, npc, d, raiz, visitas == 0, true);
    }

    /** Fase 1: fala com memória + loja (comerciante) ou dicas (guia). */
    private void conversaSimples(Player p, NpcVivo npc, int visitas) {
        String fala;
        if (visitas == 0 && !npc.saudacao().isEmpty()) {
            fala = sortear(npc.saudacao());
        } else if (!npc.falas().isEmpty() && !npc.temLoja()) {
            fala = npc.falas().get(plugin.repositorio().proximaFala(p.getUniqueId(), npc.id(), npc.falas().size()));
        } else if (!npc.retorno().isEmpty()) {
            fala = sortear(npc.retorno());
        } else {
            fala = sortear(npc.saudacao());
        }
        falar(p, npc, fala);
        if (npc.temLoja() || npc.tem(Papel.FERREIRO)) {
            plugin.comercio().abrir(p, npc, true);
        } else if (visitas == 0 && !npc.falas().isEmpty()) {
            plugin.mensagens().enviar(p, "clique-de-novo", "npc", npc.nome());
        }
    }

    /** Mostra um nó: fala do NPC + opções clicáveis. raiz = primeira tela (entram missões, loja e tchau). */
    private void mostrar(Player p, NpcVivo npc, Dialogo d, No no, boolean primeiraVez, boolean raiz) {
        String fala;
        if (no == null) {
            fala = primeiraVez || npc.retorno().isEmpty() ? sortear(npc.saudacao()) : sortear(npc.retorno());
        } else {
            fala = !primeiraVez && !no.falasRetorno().isEmpty() ? sortear(no.falasRetorno()) : sortear(no.falas());
        }
        p.sendMessage(Component.empty());
        falar(p, npc, fala);

        List<Escolha> escolhas = new ArrayList<>();
        boolean temLoja = false;
        boolean temFechar = false;
        boolean temForja = false;
        if (raiz) {
            for (Missao m : plugin.missoes().entregaveis(p, npc)) {
                if (plugin.missoes().pronta(p, m)) {
                    escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("opcao-entregar"), "missao", m.nome()),
                            "Entregar a missão e pegar a recompensa", () -> {
                                plugin.missoes().entregar(p, npc, m);
                            }));
                } else {
                    escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("opcao-andamento"), "missao", m.nome(),
                            "progresso", plugin.missoes().progresso(p, m) + "/" + m.quantidade()), "Missão em andamento",
                            () -> plugin.mensagens().enviar(p, "missao-faltando", "missao", m.nome(),
                                    "progresso", plugin.missoes().progresso(p, m) + "/" + m.quantidade())));
                }
            }
            for (Missao m : plugin.missoes().oferecidas(p, npc)) {
                escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("opcao-missao"), "missao", m.nome()),
                        "Ver a missão", () -> mostrarMissao(p, npc, d, m)));
            }
        }
        if (no != null) {
            for (Opcao o : no.opcoes()) {
                if (!condicoes(p, npc, o.condicoes())) {
                    continue;
                }
                if ("loja".equals(o.acao()) || "vender".equals(o.acao())) {
                    temLoja = true;
                }
                if ("fechar".equals(o.acao())) {
                    temFechar = true;
                }
                if ("aprimorar".equals(o.acao())) {
                    temForja = true;
                }
                escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("opcao"), "texto", o.texto()), null,
                        () -> executar(p, npc, d, o)));
            }
        }
        if (raiz && !temLoja && (npc.temLoja() || npc.tem(Papel.FERREIRO))) {
            escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-loja"), "Abrir a loja", () -> plugin.comercio().abrir(p, npc, true)));
        }
        if (raiz && !temForja && npc.tem(Papel.FERREIRO) && plugin.upgrades().ativo()) {
            escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-aprimorar"), "Aprimorar a arma ou armadura da sua mão",
                    () -> plugin.upgrades().abrirForja(p, npc)));
        }
        if (raiz && temCombate(npc)) {
            adicionarOpcoesDeCombate(p, npc, d, escolhas);
        }
        if (!raiz && no != null && no.opcoes().isEmpty() && d != null) {
            escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-voltar"), null,
                    () -> mostrar(p, npc, d, d.nos().get(d.inicio()), false, true)));
        }
        if (raiz && !temFechar) {
            escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-tchau"), null, () -> despedir(p, npc)));
        }
        enviarEscolhas(p, npc, escolhas);
    }

    // ------------------------------------------------------------------ COMBATE (fase 6)

    /** O NPC tem o que oferecer de combate (contrato de mercenário ou duelo)? */
    private boolean temCombate(NpcVivo npc) {
        return plugin.comportamento().ativo() && (npc.tem(Papel.MERCENARIO) || npc.tem(Papel.DUELISTA));
    }

    private void adicionarOpcoesDeCombate(Player p, NpcVivo npc, Dialogo d, List<Escolha> escolhas) {
        if (npc.tem(Papel.MERCENARIO)) {
            if (npc.id().equals(plugin.mercenarios().idContratado(p))) {
                escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-dispensar"), "Encerrar o contrato agora",
                        () -> plugin.mercenarios().dispensar(p, "mercenario-dispensado")));
            } else {
                escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("opcao-contratar"),
                        "valor", Texto.dinheiro(plugin.mercenarios().preco(p, npc))),
                        "Ele luta por você durante " + plugin.mercenarios().minutos() + " minutos",
                        () -> plugin.mercenarios().contratar(p, npc)));
            }
        }
        if (npc.tem(Papel.DUELISTA)) {
            if (npc.id().equals(plugin.duelos().duelosDoJogador(p))) {
                escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-desistir-duelo"), "Desistir e perder a aposta",
                        () -> plugin.duelos().desistir(p)));
            } else {
                escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-duelo"), "Escolher a dificuldade do duelo",
                        () -> mostrarDuelos(p, npc, d)));
            }
        }
    }

    /** Submenu do duelista: uma linha por dificuldade do combate.yml. */
    private void mostrarDuelos(Player p, NpcVivo npc, Dialogo d) {
        p.sendMessage(Component.empty());
        plugin.mensagens().enviarSemPrefixo(p, "duelo-escolha", "npc", npc.nome());
        List<Escolha> escolhas = new ArrayList<>();
        for (String nome : plugin.duelos().dificuldades()) {
            ConfigurationSection s = plugin.duelos().dificuldade(nome);
            if (s == null) {
                continue;
            }
            escolhas.add(new Escolha(Texto.substituir(plugin.mensagens().bruta("duelo-opcao"),
                    "dificuldade", plugin.duelos().nomeBonito(nome),
                    "aposta", Texto.dinheiro(s.getDouble("aposta", 0)),
                    "premio", Texto.dinheiro(s.getDouble("premio", 0))),
                    "Vida " + (int) s.getDouble("vida", 0) + ", dano " + (int) s.getDouble("dano", 0),
                    () -> plugin.duelos().comecar(p, npc, nome)));
        }
        escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-voltar"), null,
                () -> mostrar(p, npc, d, d != null ? d.nos().get(d.inicio()) : null, false, true)));
        enviarEscolhas(p, npc, escolhas);
    }

    private void mostrarMissao(Player p, NpcVivo npc, Dialogo d, Missao m) {
        p.sendMessage(Component.empty());
        for (String linha : m.descricao()) {
            falar(p, npc, linha);
        }
        plugin.mensagens().enviarSemPrefixo(p, "missao-detalhe", "missao", m.nome(), "objetivo", plugin.missoes().objetivo(m),
                "recompensa", plugin.missoes().recompensa(m), "tipo", m.tipo().nome());
        List<Escolha> escolhas = new ArrayList<>();
        escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-aceitar"), "Aceitar a missão", () -> plugin.missoes().aceitar(p, npc, m)));
        escolhas.add(new Escolha(plugin.mensagens().bruta("opcao-recusar"), null, () -> {
            No raiz = d != null ? d.nos().get(d.inicio()) : null;
            mostrar(p, npc, d, raiz, false, true);
        }));
        enviarEscolhas(p, npc, escolhas);
    }

    private void executar(Player p, NpcVivo npc, Dialogo d, Opcao o) {
        if (o.fala() != null) {
            falar(p, npc, o.fala());
        }
        if (!o.comandos().isEmpty()) {
            for (String c : o.comandos()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{jogador}", p.getName()).replace("{npc}", npc.id()));
            }
        }
        String acao = o.acao() == null ? "" : o.acao().toLowerCase(Locale.ROOT);
        Missao m = plugin.missoes().obter(o.missao());
        switch (acao) {
            case "loja" -> plugin.comercio().abrir(p, npc, true);
            case "vender" -> plugin.comercio().abrir(p, npc, false);
            case "fechar" -> despedir(p, npc);
            case "aprimorar" -> plugin.upgrades().abrirForja(p, npc);
            case "contratar" -> plugin.mercenarios().contratar(p, npc);
            case "dispensar" -> plugin.mercenarios().dispensar(p, "mercenario-dispensado");
            case "duelo" -> mostrarDuelos(p, npc, d);
            case "dar_missao" -> {
                if (m != null) {
                    plugin.missoes().aceitar(p, npc, m);
                } else {
                    avisarUmaVez("missao '" + o.missao() + "' não existe (diálogo " + (d != null ? d.id() : "?") + ")");
                }
            }
            case "ver_missao" -> {
                if (m != null) {
                    mostrarMissao(p, npc, d, m);
                }
            }
            case "entregar_missao" -> {
                if (m != null) {
                    plugin.missoes().entregar(p, npc, m);
                }
            }
            case "entrar_faccao" -> {
                var f = plugin.faccoes().daNpc(npc);
                if (f != null) {
                    plugin.faccoes().entrar(p, f);
                }
            }
            default -> {
                if (o.ir() != null && d != null && d.nos().containsKey(o.ir())) {
                    mostrar(p, npc, d, d.nos().get(o.ir()), false, o.ir().equals(d.inicio()));
                } else if (o.ir() != null) {
                    avisarUmaVez("nó '" + o.ir() + "' não existe no diálogo " + (d != null ? d.id() : "?"));
                }
            }
        }
    }

    /**
     * Cada opção é uma linha clicável no chat. O clique roda "/npcconversa <código>", com um código aleatório
     * de uso único: funciona em qualquer versão do Minecraft (inclusive as antigas, via ViaVersion).
     */
    private void enviarEscolhas(Player p, NpcVivo npc, List<Escolha> escolhas) {
        int numero = conversa.merge(p.getUniqueId(), 1, Integer::sum);
        long expira = System.currentTimeMillis() + 5 * 60_000L;
        cliques.values().removeIf(c -> c.jogador().equals(p.getUniqueId()) || c.expira() < System.currentTimeMillis());
        for (Escolha e : escolhas) {
            String codigo = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            cliques.put(codigo, new Clique(p.getUniqueId(), numero, npc, e.acao(), expira));
            Component linha = Texto.cor(e.texto())
                    .hoverEvent(HoverEvent.showText(Texto.cor("<gray>" + (e.dica() != null ? e.dica() : "Clique para escolher"))))
                    .clickEvent(ClickEvent.runCommand("/npcconversa " + codigo));
            p.sendMessage(linha);
        }
    }

    /** Chamado pelo /npcconversa quando o jogador clica numa opção. */
    public void clicar(Player p, String codigo) {
        Clique c = cliques.get(codigo);
        if (c == null || !c.jogador().equals(p.getUniqueId()) || c.expira() < System.currentTimeMillis()) {
            plugin.mensagens().enviar(p, "conversa-antiga");
            return;
        }
        Integer atual = conversa.get(p.getUniqueId());
        if (atual == null || atual != c.conversa()) {
            plugin.mensagens().enviar(p, "conversa-antiga");
            return;
        }
        if (!perto(p, c.npc())) {
            plugin.mensagens().enviar(p, "longe-do-npc", "npc", c.npc().nome());
            return;
        }
        conversa.merge(p.getUniqueId(), 1, Integer::sum); // fecha as opções desta tela
        cliques.values().removeIf(x -> x.jogador().equals(p.getUniqueId()));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
        c.acao().run();
    }

    private boolean perto(Player p, NpcVivo npc) {
        Location l = npc.local();
        double max = plugin.getConfig().getDouble("npc.distancia-interacao", 8);
        return l != null && l.getWorld().equals(p.getWorld()) && l.distanceSquared(p.getLocation()) <= max * max;
    }

    private void despedir(Player p, NpcVivo npc) {
        falar(p, npc, sortear(plugin.mensagens().lista("despedidas")));
        plugin.comportamento().ocupar(npc, null, EstadoNpc.VOLTANDO_AO_POSTO);
    }

    private void falar(Player p, NpcVivo npc, String fala) {
        if (fala == null || fala.isEmpty()) {
            return;
        }
        p.sendMessage(Texto.cor(plugin.mensagens().bruta("fala"), "npc", npc.nome(),
                "fala", Texto.substituir(fala, "jogador", p.getName(), "npc", Texto.semCor(npc.nome()))));
    }

    // ------------------------------------------------------------------ CONDIÇÕES

    private boolean condicoes(Player p, NpcVivo npc, Map<String, Object> se) {
        for (Map.Entry<String, Object> c : se.entrySet()) {
            String v = String.valueOf(c.getValue());
            Missao m = plugin.missoes().obter(v);
            boolean ok = switch (c.getKey().toLowerCase(Locale.ROOT)) {
                case "missao_ativa" -> m != null && plugin.missoes().ativa(p, m);
                case "missao_pronta" -> m != null && plugin.missoes().ativa(p, m) && plugin.missoes().pronta(p, m);
                case "missao_concluida" -> m != null && plugin.missoes().concluida(p, m);
                case "missao_nao_concluida" -> m == null || !plugin.missoes().concluida(p, m);
                case "missao_disponivel" -> m != null && plugin.missoes().disponivel(p, m);
                case "visitas_min" -> plugin.repositorio().visitas(p.getUniqueId(), npc.id()) >= numero(c.getValue());
                case "permissao" -> p.hasPermission(v);
                case "tem_item" -> temItem(p, c.getValue());
                case "reputacao_min" -> {
                    var f = plugin.faccoes().daNpc(npc);
                    yield f != null && plugin.faccoes().reputacao(p.getUniqueId(), f.id()) >= numero(c.getValue());
                }
                case "nivel_min" -> {
                    var f = plugin.faccoes().daNpc(npc);
                    var n = dev.pedrobugica.npcvivos.faccao.NivelReputacao.buscar(v);
                    yield f != null && n != null && plugin.faccoes().nivel(p.getUniqueId(), f.id()).ordinal() >= n.ordinal();
                }
                case "membro", "nao_membro" -> {
                    var mem = plugin.faccoes().membro(p);
                    String alvo = v.equalsIgnoreCase("npc") ? npc.faccao() : v;
                    boolean eh = mem != null && mem.id().equalsIgnoreCase(alvo);
                    yield c.getKey().equalsIgnoreCase("membro") == eh;
                }
                default -> {
                    avisarUmaVez("condição desconhecida '" + c.getKey() + "' (NPC " + npc.id() + ")");
                    yield true;
                }
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static int numero(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** tem_item: "IRON_INGOT:32" ou {item: IRON_INGOT, quantidade: 32} */
    private static boolean temItem(Player p, Object valor) {
        String nome;
        int qtd;
        if (valor instanceof Map<?, ?> m) {
            nome = String.valueOf(m.get("item"));
            qtd = numero(m.get("quantidade") == null ? 1 : m.get("quantidade"));
        } else {
            String[] partes = String.valueOf(valor).split(":");
            nome = partes[0];
            qtd = partes.length > 1 ? numero(partes[1]) : 1;
        }
        Material mat = Material.matchMaterial(nome);
        if (mat == null) {
            return false;
        }
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == mat) {
                n += s.getAmount();
            }
        }
        return n >= Math.max(1, qtd);
    }

    private void avisarUmaVez(String aviso) {
        if (avisados.add(aviso)) {
            plugin.getLogger().warning("Diálogos: " + aviso);
        }
    }

    private static String sortear(List<String> l) {
        return l == null || l.isEmpty() ? null : l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    public void esquecer(UUID jogador) {
        ultimoClique.keySet().removeIf(k -> k.startsWith(jogador.toString()));
        conversa.remove(jogador);
        cliques.values().removeIf(c -> c.jogador().equals(jogador));
    }
}
