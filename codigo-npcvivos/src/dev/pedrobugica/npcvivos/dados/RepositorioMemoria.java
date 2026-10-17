package dev.pedrobugica.npcvivos.dados;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Base dos repositórios: tudo fica em memória (cache) e é gravado de tempos em tempos fora da thread principal.
 * As subclasses só decidem ONDE gravar (YAML ou SQLite) a "fotografia" dos dados: categoria -> chave -> valor.
 */
public abstract class RepositorioMemoria implements Repositorio {

    protected final JavaPlugin plugin;

    private final Map<String, Double> estoque = new ConcurrentHashMap<>();
    private final Map<String, Integer> vendido = new ConcurrentHashMap<>();
    private final Map<String, Integer> visitas = new ConcurrentHashMap<>();
    private final Map<String, Integer> falas = new ConcurrentHashMap<>();
    private final Map<String, Integer> missoesAtivas = new ConcurrentHashMap<>();
    private final Map<String, Double> concluidaQuando = new ConcurrentHashMap<>();
    private final Map<String, Integer> concluidaVezes = new ConcurrentHashMap<>();
    private final Map<String, Integer> reputacao = new ConcurrentHashMap<>();
    private final Map<String, String> membro = new ConcurrentHashMap<>();
    private final Map<String, Double> saidaFaccao = new ConcurrentHashMap<>();
    private final Map<String, String> relacoes = new ConcurrentHashMap<>();
    private final Map<String, Integer> repVenda = new ConcurrentHashMap<>();
    /** jogador -> títulos separados por "|" (um valor por jogador: título com ponto não vira seção no YAML). */
    private final Map<String, String> titulos = new ConcurrentHashMap<>();
    /** Mercado, pontuação entre facções, guerras e bônus da fase 7 (chave -> texto). */
    private final Map<String, String> mundoVivo = new ConcurrentHashMap<>();
    private final Set<String> marcas = ConcurrentHashMap.newKeySet();
    private volatile String dia = LocalDate.now().toString();

    protected RepositorioMemoria(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ FOTOGRAFIA (para gravar e ler)

    /** Todos os dados como texto: categoria -> chave -> valor. */
    protected Map<String, Map<String, String>> exportar() {
        virarDia();
        Map<String, Map<String, String>> d = new LinkedHashMap<>();
        d.put("geral", Map.of("dia", dia));
        d.put("estoque", textos(estoque, v -> String.valueOf(Math.round(v * 1000) / 1000.0)));
        d.put("vendido-hoje", textos(vendido, String::valueOf));
        d.put("visitas", textos(visitas, String::valueOf));
        d.put("falas", textos(falas, String::valueOf));
        d.put("missoes-ativas", textos(missoesAtivas, String::valueOf));
        d.put("missoes-concluidas-em", textos(concluidaQuando, v -> String.valueOf(v.longValue())));
        d.put("missoes-concluidas-vezes", textos(concluidaVezes, String::valueOf));
        d.put("reputacao", textos(reputacao, String::valueOf));
        d.put("membro", textos(membro, v -> v));
        d.put("saida-faccao", textos(saidaFaccao, v -> String.valueOf(v.longValue())));
        d.put("relacoes", textos(relacoes, v -> v));
        d.put("reputacao-venda-hoje", textos(repVenda, String::valueOf));
        d.put("titulos", textos(titulos, v -> v));
        d.put("mundo-vivo", textos(mundoVivo, v -> v));
        Map<String, String> m = new TreeMap<>();
        marcas.forEach(x -> m.put(x, "1"));
        d.put("marcas", m);
        return d;
    }

    private static <T> Map<String, String> textos(Map<String, T> origem, Function<T, String> conv) {
        Map<String, String> r = new TreeMap<>();
        origem.forEach((k, v) -> r.put(k, conv.apply(v)));
        return r;
    }

    /** Carrega uma fotografia (substitui o que estiver na memória). */
    protected void importar(Map<String, Map<String, String>> d) {
        String diaSalvo = d.getOrDefault("geral", Map.of()).getOrDefault("dia", dia);
        boolean hoje = diaSalvo.equals(dia);
        ler(d, "estoque", estoque, Double::parseDouble);
        if (hoje) {
            ler(d, "vendido-hoje", vendido, Integer::parseInt);
            ler(d, "reputacao-venda-hoje", repVenda, Integer::parseInt);
        }
        ler(d, "visitas", visitas, Integer::parseInt);
        ler(d, "falas", falas, Integer::parseInt);
        ler(d, "missoes-ativas", missoesAtivas, Integer::parseInt);
        ler(d, "missoes-concluidas-em", concluidaQuando, Double::parseDouble);
        ler(d, "missoes-concluidas-vezes", concluidaVezes, Integer::parseInt);
        ler(d, "reputacao", reputacao, Integer::parseInt);
        ler(d, "membro", membro, s -> s);
        ler(d, "saida-faccao", saidaFaccao, Double::parseDouble);
        ler(d, "relacoes", relacoes, s -> s);
        ler(d, "titulos", titulos, s -> s);
        ler(d, "mundo-vivo", mundoVivo, s -> s);
        marcas.addAll(d.getOrDefault("marcas", Map.of()).keySet());
    }

    private <T> void ler(Map<String, Map<String, String>> d, String cat, Map<String, T> destino, Function<String, T> conv) {
        destino.clear();
        for (Map.Entry<String, String> e : d.getOrDefault(cat, Map.of()).entrySet()) {
            try {
                destino.put(e.getKey(), conv.apply(e.getValue()));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Dado inválido em " + cat + "." + e.getKey() + ": " + e.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ AUXILIARES

    private static String k(String... partes) {
        return String.join(".", partes);
    }

    private void virarDia() {
        String hoje = LocalDate.now().toString();
        if (!hoje.equals(dia)) {
            dia = hoje;
            vendido.clear();
            repVenda.clear();
        }
    }

    // ------------------------------------------------------------------ LOJA E MEMÓRIA

    @Override
    public Double estoque(String npc, String chave) {
        return estoque.get(k(npc, chave));
    }

    @Override
    public void setEstoque(String npc, String chave, double quantidade) {
        estoque.put(k(npc, chave), quantidade);
    }

    @Override
    public int vendidoHoje(UUID jogador, String npc, String chave) {
        virarDia();
        return vendido.getOrDefault(k(jogador.toString(), npc, chave), 0);
    }

    @Override
    public void adicionarVendido(UUID jogador, String npc, String chave, int quantidade) {
        virarDia();
        vendido.merge(k(jogador.toString(), npc, chave), quantidade, Integer::sum);
    }

    @Override
    public int visitas(UUID jogador, String npc) {
        return visitas.getOrDefault(k(jogador.toString(), npc), 0);
    }

    @Override
    public void registrarVisita(UUID jogador, String npc) {
        visitas.merge(k(jogador.toString(), npc), 1, Integer::sum);
    }

    @Override
    public int proximaFala(UUID jogador, String npc, int total) {
        if (total <= 0) {
            return 0;
        }
        String chave = k(jogador.toString(), npc);
        int i = falas.getOrDefault(chave, 0) % total;
        falas.put(chave, (i + 1) % total);
        return i;
    }

    @Override
    public void esquecerNpc(String npc) {
        estoque.keySet().removeIf(c -> c.startsWith(npc + "."));
    }

    // ------------------------------------------------------------------ MISSÕES

    @Override
    public Integer progressoMissao(UUID jogador, String missao) {
        return missoesAtivas.get(k(jogador.toString(), missao));
    }

    @Override
    public void setProgressoMissao(UUID jogador, String missao, int progresso) {
        missoesAtivas.put(k(jogador.toString(), missao), progresso);
    }

    @Override
    public void removerMissaoAtiva(UUID jogador, String missao) {
        missoesAtivas.remove(k(jogador.toString(), missao));
    }

    @Override
    public Map<String, Integer> missoesAtivas(UUID jogador) {
        return comPrefixo(missoesAtivas, jogador + ".");
    }

    private static <T> Map<String, T> comPrefixo(Map<String, T> mapa, String prefixo) {
        Map<String, T> r = new LinkedHashMap<>();
        mapa.forEach((c, v) -> {
            if (c.startsWith(prefixo)) {
                r.put(c.substring(prefixo.length()), v);
            }
        });
        return r;
    }

    @Override
    public long concluidaEm(UUID jogador, String missao) {
        Double v = concluidaQuando.get(k(jogador.toString(), missao));
        return v == null ? 0 : v.longValue();
    }

    @Override
    public int vezesConcluida(UUID jogador, String missao) {
        return concluidaVezes.getOrDefault(k(jogador.toString(), missao), 0);
    }

    @Override
    public void marcarConcluida(UUID jogador, String missao, long quando) {
        concluidaQuando.put(k(jogador.toString(), missao), (double) quando);
        concluidaVezes.merge(k(jogador.toString(), missao), 1, Integer::sum);
    }

    // ------------------------------------------------------------------ FACÇÕES

    @Override
    public int reputacao(UUID jogador, String faccao) {
        return reputacao.getOrDefault(k(jogador.toString(), faccao), 0);
    }

    @Override
    public void setReputacao(UUID jogador, String faccao, int valor) {
        reputacao.put(k(jogador.toString(), faccao), valor);
    }

    @Override
    public Map<String, Integer> reputacoes(UUID jogador) {
        return comPrefixo(reputacao, jogador + ".");
    }

    @Override
    public String membro(UUID jogador) {
        return membro.get(jogador.toString());
    }

    @Override
    public void setMembro(UUID jogador, String faccao) {
        if (faccao == null) {
            membro.remove(jogador.toString());
        } else {
            membro.put(jogador.toString(), faccao);
        }
    }

    @Override
    public long saiuDeFaccaoEm(UUID jogador) {
        Double v = saidaFaccao.get(jogador.toString());
        return v == null ? 0 : v.longValue();
    }

    @Override
    public void setSaiuDeFaccaoEm(UUID jogador, long quando) {
        saidaFaccao.put(jogador.toString(), (double) quando);
    }

    private static String par(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "." + b : b + "." + a;
    }

    @Override
    public String relacao(String a, String b) {
        return relacoes.get(par(a, b));
    }

    @Override
    public void setRelacao(String a, String b, String tipo) {
        relacoes.put(par(a, b), tipo);
    }

    @Override
    public int reputacaoVendaHoje(UUID jogador, String faccao) {
        virarDia();
        return repVenda.getOrDefault(k(jogador.toString(), faccao), 0);
    }

    @Override
    public void adicionarReputacaoVenda(UUID jogador, String faccao, int pontos) {
        virarDia();
        repVenda.merge(k(jogador.toString(), faccao), pontos, Integer::sum);
    }

    // ------------------------------------------------------------------ TÍTULOS

    @Override
    public java.util.List<String> titulos(UUID jogador) {
        String bruto = titulos.get(jogador.toString());
        if (bruto == null || bruto.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(bruto.split("\\|"));
    }

    @Override
    public String mundo(String chave) {
        return mundoVivo.get(chave);
    }

    @Override
    public void setMundo(String chave, String valor) {
        if (valor == null) {
            mundoVivo.remove(chave);
        } else {
            mundoVivo.put(chave, valor);
        }
    }

    @Override
    public Map<String, String> mundoComPrefixo(String prefixo) {
        Map<String, String> r = new TreeMap<>();
        mundoVivo.forEach((k, v) -> {
            if (k.startsWith(prefixo)) {
                r.put(k, v);
            }
        });
        return r;
    }

    @Override
    public void guardarTitulo(UUID jogador, String titulo) {
        if (titulo == null || titulo.isBlank() || titulo.indexOf('|') >= 0) {
            return;
        }
        java.util.List<String> atuais = titulos(jogador);
        if (atuais.contains(titulo)) {
            return;
        }
        java.util.List<String> novos = new java.util.ArrayList<>(atuais);
        novos.add(titulo);
        titulos.put(jogador.toString(), String.join("|", novos));
    }

    // ------------------------------------------------------------------ MARCAS

    @Override
    public boolean executado(String marca) {
        return marcas.contains(marca);
    }

    @Override
    public void marcarExecutado(String marca) {
        marcas.add(marca);
    }

    // ------------------------------------------------------------------ GRAVAÇÃO

    /** Lê os dados do disco (ou banco). */
    public abstract void carregar();

    /** Grava a fotografia (chamado fora da thread principal no salvamento automático). */
    protected abstract void gravar(Map<String, Map<String, String>> fotografia);

    @Override
    public void salvarAssincrono() {
        Map<String, Map<String, String>> foto = exportar();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> gravar(foto));
    }

    @Override
    public void salvarSincrono() {
        gravar(exportar());
    }
}
