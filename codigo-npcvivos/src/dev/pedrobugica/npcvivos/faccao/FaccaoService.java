package dev.pedrobugica.npcvivos.faccao;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Facções, relações entre elas, reputação dos jogadores e quem jurou lealdade a quem.
 * Faccao só guarda dados; aqui é onde se decide o que acontece.
 */
public final class FaccaoService {

    public static final List<String> PADRAO = List.of("vila_aurora", "irmandade_do_sul", "mercadores_livres");

    private final NpcVivosPlugin plugin;
    private final File pasta;
    private final Map<String, Faccao> faccoes = new LinkedHashMap<>();
    /** Jogadores procurados pelos guardas de uma facção (atacaram alguém dela): jogador -> facção -> até quando. */
    private final Map<UUID, Map<String, Long>> procurados = new ConcurrentHashMap<>();

    public FaccaoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "faccoes");
    }

    // ------------------------------------------------------------------ ARQUIVOS

    public void copiarExemplos() {
        for (String id : PADRAO) {
            if (!new File(pasta, id + ".yml").exists()) {
                plugin.saveResource("faccoes/" + id + ".yml", false);
            }
        }
    }

    /** Copia um arquivo de exemplo só se ele ainda não existe (atualizações trazem facções novas). */
    public void copiarExemplo(String id) {
        if (!new File(pasta, id + ".yml").exists()) {
            plugin.saveResource("faccoes/" + id + ".yml", false);
        }
    }

    public void carregar() {
        faccoes.clear();
        if (!pasta.exists() && !pasta.mkdirs()) {
            plugin.getLogger().warning("Não foi possível criar a pasta faccoes/.");
        }
        File[] arquivos = pasta.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (arquivos != null) {
            for (File f : arquivos) {
                try {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    String id = y.getString("id", f.getName().substring(0, f.getName().length() - 4)).toLowerCase(Locale.ROOT);
                    Map<String, Relacao> rel = new HashMap<>();
                    ConfigurationSection rs = y.getConfigurationSection("relacoes");
                    if (rs != null) {
                        for (String outra : rs.getKeys(false)) {
                            Relacao r = Relacao.buscar(rs.getString(outra));
                            if (r == null) {
                                plugin.getLogger().warning("Facção '" + id + "': relação inválida com '" + outra + "'.");
                            } else {
                                rel.put(outra.toLowerCase(Locale.ROOT), r);
                            }
                        }
                    }
                    List<String> desc = y.isList("descricao") ? y.getStringList("descricao")
                            : (y.isString("descricao") ? List.of(y.getString("descricao")) : List.of());
                    faccoes.put(id, new Faccao(id, y.getString("nome", id), y.getString("tag", ""), desc, y.getString("lider"), rel,
                            Math.max(0, Math.min(0.9, y.getDouble("beneficios_membro.desconto", 0.10)))));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Facção inválida em faccoes/" + f.getName(), e);
                }
            }
        }
        plugin.getLogger().info(faccoes.size() + " facção(ões) carregada(s).");
    }

    // ------------------------------------------------------------------ CONSULTAS

    public Faccao obter(String id) {
        return id == null ? null : faccoes.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Faccao> todas() {
        return new ArrayList<>(faccoes.values());
    }

    public Faccao daNpc(NpcVivo npc) {
        return obter(npc.faccao());
    }

    public Relacao relacao(String a, String b) {
        if (a == null || b == null) {
            return Relacao.NEUTRA;
        }
        if (a.equals(b)) {
            return Relacao.ALIADA;
        }
        Relacao mudada = Relacao.buscar(plugin.repositorio().relacao(a, b));
        if (mudada != null) {
            return mudada;
        }
        Faccao fa = obter(a);
        Faccao fb = obter(b);
        if (fa != null && fa.relacoes().containsKey(b)) {
            return fa.relacoes().get(b);
        }
        if (fb != null && fb.relacoes().containsKey(a)) {
            return fb.relacoes().get(a);
        }
        return Relacao.NEUTRA;
    }

    /** Relação escrita nos arquivos faccoes/*.yml (sem as mudanças do jogo): para onde a diplomacia volta com o tempo. */
    public Relacao relacaoPadrao(String a, String b) {
        if (a == null || b == null) {
            return Relacao.NEUTRA;
        }
        if (a.equals(b)) {
            return Relacao.ALIADA;
        }
        Faccao fa = obter(a);
        Faccao fb = obter(b);
        if (fa != null && fa.relacoes().containsKey(b)) {
            return fa.relacoes().get(b);
        }
        if (fb != null && fb.relacoes().containsKey(a)) {
            return fb.relacoes().get(a);
        }
        return Relacao.NEUTRA;
    }

    /** Muda a relação entre duas facções (comando da staff) e anuncia para o servidor. */
    public void mudarRelacao(Faccao a, Faccao b, Relacao nova) {
        Relacao antes = relacao(a.id(), b.id());
        plugin.repositorio().setRelacao(a.id(), b.id(), nova.name());
        plugin.repositorio().salvarAssincrono();
        if (antes == nova) {
            return;
        }
        String chave = switch (nova) {
            case GUERRA -> "anuncio-guerra";
            case ALIADA -> "anuncio-alianca";
            case RIVAL -> "anuncio-rivalidade";
            case NEUTRA -> "anuncio-paz";
        };
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(p, chave, "a", a.nome(), "b", b.nome());
            p.playSound(p.getLocation(), nova == Relacao.GUERRA ? Sound.EVENT_RAID_HORN : Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1f);
        }
    }

    public int reputacao(UUID jogador, String faccao) {
        return plugin.repositorio().reputacao(jogador, faccao);
    }

    public NivelReputacao nivel(UUID jogador, String faccao) {
        return NivelReputacao.de(reputacao(jogador, faccao));
    }

    /** Soma (ou tira) reputação e avisa o jogador; avisa também quando o nível muda. */
    public void alterarReputacao(Player p, Faccao f, int delta) {
        if (f == null || delta == 0) {
            return;
        }
        int antes = reputacao(p.getUniqueId(), f.id());
        int depois = Math.max(NivelReputacao.MINIMO, Math.min(NivelReputacao.MAXIMO, antes + delta));
        if (depois == antes) {
            return;
        }
        plugin.repositorio().setReputacao(p.getUniqueId(), f.id(), depois);
        p.sendActionBar(Texto.cor(plugin.mensagens().bruta(delta > 0 ? "reputacao-ganhou" : "reputacao-perdeu"),
                "pontos", String.valueOf(Math.abs(depois - antes)), "faccao", f.nome(), "total", String.valueOf(depois)));
        NivelReputacao na = NivelReputacao.de(antes);
        NivelReputacao nd = NivelReputacao.de(depois);
        if (na != nd) {
            plugin.mensagens().enviar(p, "reputacao-nivel", "faccao", f.nome(), "nivel", nd.formatado());
            p.playSound(p.getLocation(), nd.ordinal() > na.ordinal() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
    }

    public Faccao membro(Player p) {
        return obter(plugin.repositorio().membro(p.getUniqueId()));
    }

    /** Staff: define a reputação direto (o jogador pode estar offline). Devolve o valor gravado. */
    public int definirReputacao(UUID jogador, Faccao f, int valor) {
        int v = Math.max(NivelReputacao.MINIMO, Math.min(NivelReputacao.MAXIMO, valor));
        Player p = Bukkit.getPlayer(jogador);
        if (p != null) {
            alterarReputacao(p, f, v - reputacao(jogador, f.id()));
        } else {
            plugin.repositorio().setReputacao(jogador, f.id(), v);
        }
        plugin.repositorio().salvarAssincrono();
        return v;
    }

    /** Staff: coloca ou tira o jogador de uma facção, sem espera nem custo de reputação. */
    public void definirMembro(UUID jogador, Faccao f) {
        plugin.repositorio().setMembro(jogador, f == null ? null : f.id());
        plugin.repositorio().setSaiuDeFaccaoEm(jogador, 0);
        plugin.repositorio().salvarAssincrono();
    }

    // ------------------------------------------------------------------ ENTRAR / SAIR

    public void entrar(Player p, Faccao f) {
        Faccao atual = membro(p);
        if (atual != null) {
            plugin.mensagens().enviar(p, atual.id().equals(f.id()) ? "faccao-ja-membro" : "faccao-sair-antes", "faccao", atual.nome());
            return;
        }
        long horas = plugin.getConfig().getLong("faccoes.espera-trocar-horas", 24);
        long falta = plugin.repositorio().saiuDeFaccaoEm(p.getUniqueId()) + horas * 3_600_000L - System.currentTimeMillis();
        if (falta > 0 && !p.hasPermission("npcvivos.admin.faccao")) {
            plugin.mensagens().enviar(p, "faccao-espera", "horas", String.valueOf((falta + 3_599_999L) / 3_600_000L));
            return;
        }
        if (nivel(p.getUniqueId(), f.id()).ordinal() < NivelReputacao.NEUTRO.ordinal()) {
            plugin.mensagens().enviar(p, "faccao-sem-reputacao", "faccao", f.nome());
            return;
        }
        plugin.repositorio().setMembro(p.getUniqueId(), f.id());
        plugin.repositorio().salvarAssincrono();
        plugin.guerras().atualizarJogador(p);
        plugin.mensagens().enviar(p, "faccao-entrou", "faccao", f.nome(),
                "desconto", String.valueOf(Math.round(f.descontoMembro() * 100)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
    }

    public void sair(Player p) {
        Faccao atual = membro(p);
        if (atual == null) {
            plugin.mensagens().enviar(p, "faccao-sem-faccao");
            return;
        }
        plugin.repositorio().setMembro(p.getUniqueId(), null);
        plugin.repositorio().setSaiuDeFaccaoEm(p.getUniqueId(), System.currentTimeMillis());
        int custo = plugin.getConfig().getInt("faccoes.reputacao-perdida-ao-sair", 100);
        alterarReputacao(p, atual, -custo);
        plugin.repositorio().salvarAssincrono();
        plugin.guerras().atualizarJogador(p);
        plugin.mensagens().enviar(p, "faccao-saiu", "faccao", atual.nome(),
                "horas", String.valueOf(plugin.getConfig().getLong("faccoes.espera-trocar-horas", 24)));
    }

    // ------------------------------------------------------------------ EFEITOS NO COMÉRCIO E NAS MISSÕES

    /** Motivo de o NPC se recusar a negociar (chave de mensagem) ou null se pode. */
    public String recusaNegociar(Player p, NpcVivo npc) {
        Faccao f = daNpc(npc);
        if (f == null) {
            return null;
        }
        if (nivel(p.getUniqueId(), f.id()) == NivelReputacao.INIMIGO) {
            return "loja-fechada-inimigo";
        }
        Faccao m = membro(p);
        if (m != null) {
            Relacao r = relacao(m.id(), f.id());
            if (r == Relacao.RIVAL || r == Relacao.GUERRA) {
                return "loja-fechada-rival";
            }
        }
        return null;
    }

    /** Multiplicador do preço de compra (reputação + desconto de membro). */
    public double multiplicadorCompra(Player p, NpcVivo npc) {
        Faccao f = daNpc(npc);
        if (f == null) {
            return 1.0;
        }
        double m = nivel(p.getUniqueId(), f.id()).precoCompra();
        Faccao membro = membro(p);
        if (membro != null && membro.id().equals(f.id())) {
            m *= 1.0 - f.descontoMembro();
        }
        return m;
    }

    /** Multiplicador do que o NPC paga ao jogador. */
    public double multiplicadorVenda(Player p, NpcVivo npc) {
        Faccao f = daNpc(npc);
        return f == null ? 1.0 : nivel(p.getUniqueId(), f.id()).precoVenda();
    }

    /** O NPC dá missões para este jogador? (Hostil e Inimigo não recebem missões) */
    public boolean daMissoes(Player p, NpcVivo npc) {
        Faccao f = daNpc(npc);
        return f == null || nivel(p.getUniqueId(), f.id()).ordinal() >= NivelReputacao.NEUTRO.ordinal();
    }

    /** Reputação por vender para a facção (conta no limite diário de ajuda). */
    public void reputacaoPorVenda(Player p, NpcVivo npc) {
        reputacaoPorAjuda(p, daNpc(npc), plugin.getConfig().getInt("reputacao.por-venda", 1));
    }

    /** Ajuda à facção (vender, matar monstros perto da vila), com limite de pontos por dia. */
    public void reputacaoPorAjuda(Player p, Faccao f, int pontos) {
        int limite = plugin.getConfig().getInt("reputacao.limite-diario-ajuda", 30);
        if (f == null || pontos <= 0 || plugin.repositorio().reputacaoVendaHoje(p.getUniqueId(), f.id()) >= limite) {
            return;
        }
        plugin.repositorio().adicionarReputacaoVenda(p.getUniqueId(), f.id(), pontos);
        alterarReputacao(p, f, pontos);
    }

    // ------------------------------------------------------------------ PROCURADOS (guardas)

    public void marcarProcurado(Player p, Faccao f) {
        long segundos = plugin.getConfig().getLong("guardas.tempo-procurado-segundos", 60);
        procurados.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(f.id(), System.currentTimeMillis() + segundos * 1000);
    }

    public boolean procurado(Player p, Faccao f) {
        Map<String, Long> m = procurados.get(p.getUniqueId());
        Long ate = m == null ? null : m.get(f.id());
        return ate != null && ate > System.currentTimeMillis();
    }

    /** Os guardas da facção devem atacar este jogador? */
    public boolean inimigoDosGuardas(Player p, Faccao f) {
        if (f == null) {
            return false;
        }
        Faccao m = membro(p);
        if (m != null && m.id().equals(f.id())) {
            return procurado(p, f); // membro só apanha se atacar a própria vila
        }
        return procurado(p, f) || nivel(p.getUniqueId(), f.id()) == NivelReputacao.INIMIGO
                || (m != null && relacao(m.id(), f.id()) == Relacao.GUERRA);
    }

    public void esquecer(UUID jogador) {
        Map<String, Long> m = procurados.get(jogador);
        if (m != null) {
            m.values().removeIf(t -> t < System.currentTimeMillis());
            if (m.isEmpty()) {
                procurados.remove(jogador);
            }
        }
    }
}
