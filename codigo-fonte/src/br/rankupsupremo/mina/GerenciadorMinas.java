package br.rankupsupremo.mina;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carrega as minas de minas.yml, controla os resets automáticos e o acesso (rank, cargo VIP e prestígio).
 */
public final class GerenciadorMinas {

    /** Motivo de um jogador não poder usar uma mina: chave da mensagem + variáveis. */
    public record Bloqueio(String chave, String... pares) {
    }

    private final RankupSupremo plugin;
    private final Map<String, Mina> minas = new LinkedHashMap<>();
    private List<Mina> normaisPorRank = List.of();
    private BukkitTask tarefa;

    private int xInicial;
    private int espacamento;
    private int posZ;
    private int alturaTopo;
    private int blocosPorTick;
    private int avisoSegundos;

    public GerenciadorMinas(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        FileConfiguration c = plugin.getConfig();
        xInicial = c.getInt("minas.posicao-x-inicial", 1000);
        espacamento = Math.max(150, c.getInt("minas.espacamento", 200));
        posZ = c.getInt("minas.posicao-z", 0);
        alturaTopo = Math.max(100, Math.min(250, c.getInt("minas.altura-topo", 120)));
        blocosPorTick = Math.max(1000, c.getInt("minas.blocos-por-tick", 20000));
        avisoSegundos = Math.max(0, c.getInt("minas.aviso-reset-segundos", 10));

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "minas.yml"));
        ConfigurationSection sec = yml.getConfigurationSection("minas");
        Map<String, Mina> antigas = new HashMap<>(minas);
        minas.clear();
        if (sec == null) {
            plugin.getLogger().severe("Nenhuma mina encontrada em minas.yml!");
            normaisPorRank = List.of();
            return;
        }
        for (String idOriginal : sec.getKeys(false)) {
            ConfigurationSection m = sec.getConfigurationSection(idOriginal);
            if (m == null) {
                continue;
            }
            String id = idOriginal.toLowerCase(Locale.ROOT);
            Rank rank = plugin.ranks().buscar(m.getString("rank", ""));
            if (rank == null) {
                plugin.getLogger().warning("Mina '" + id + "': rank '" + m.getString("rank") + "' não existe. Usando o primeiro rank.");
                rank = plugin.ranks().primeiro();
            }
            List<Material> blocos = new ArrayList<>();
            List<Double> pesos = new ArrayList<>();
            ConfigurationSection b = m.getConfigurationSection("blocos");
            if (b != null) {
                for (String nomeBloco : b.getKeys(false)) {
                    Material mat = Material.matchMaterial(nomeBloco);
                    double peso = b.getDouble(nomeBloco, 0);
                    if (mat == null || !mat.isBlock() || !mat.isItem() || mat.isAir()) {
                        plugin.getLogger().warning("Mina '" + id + "': bloco inválido '" + nomeBloco + "', ignorado.");
                        continue;
                    }
                    if (peso <= 0) {
                        continue;
                    }
                    blocos.add(mat);
                    pesos.add(peso);
                }
            }
            if (blocos.isEmpty()) {
                plugin.getLogger().warning("Mina '" + id + "' não tem blocos válidos. Usando STONE.");
                blocos.add(Material.STONE);
                pesos.add(1.0);
            }
            Mina mina = antigas.getOrDefault(id, new Mina(id));
            mina.configurar(
                    m.getString("nome", id),
                    rank.id(),
                    material(m.getString("icone"), Material.STONE, false),
                    material(m.getString("parede"), Material.STONE_BRICKS, true),
                    material(m.getString("detalhe"), Material.CHISELED_STONE_BRICKS, true),
                    impar(Math.max(7, Math.min(61, m.getInt("tamanho", 25)))),
                    Math.max(3, Math.min(alturaTopo - 40, m.getInt("profundidade", 25))),
                    Math.max(1, m.getInt("reset-minutos", 10)),
                    Math.max(0, Math.min(95, m.getDouble("reset-porcentagem", 25))),
                    blocos, pesos);
            mina.configurarEspecial(
                    enumOu(TipoMina.class, m.getString("tipo"), TipoMina.NORMAL),
                    enumOu(TemaMina.class, m.getString("tema"), TemaMina.PADRAO),
                    m.getString("permissao"),
                    m.getString("cargo", "VIP"),
                    m.getInt("prestigio-minimo", 0),
                    m.getInt("drops-rank", 0),
                    m.getDouble("multiplicador-tokens", 1.0),
                    m.contains("largura-plataforma") ? Math.max(3, Math.min(12, m.getInt("largura-plataforma"))) : -1);
            minas.put(id, mina);
        }
        List<Mina> normais = new ArrayList<>();
        for (Mina m : minas.values()) {
            if (!m.especial()) {
                normais.add(m);
            }
        }
        normais.sort(Comparator.comparingInt(m -> plugin.ranks().obter(m.rankId()).indice()));
        normaisPorRank = List.copyOf(normais);
        plugin.getLogger().info(minas.size() + " minas carregadas (" + (minas.size() - normais.size()) + " especiais).");
    }

    private static <E extends Enum<E>> E enumOu(Class<E> tipo, String valor, E padrao) {
        if (valor == null) {
            return padrao;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    private static int impar(int n) {
        return n % 2 == 0 ? n + 1 : n;
    }

    private Material material(String nome, Material padrao, boolean bloco) {
        Material m = nome == null ? null : Material.matchMaterial(nome);
        if (m == null || m.isAir() || (bloco && !m.isBlock()) || (!bloco && !m.isItem())) {
            return padrao;
        }
        return m;
    }

    /** Define a posição de cada mina no mundo (índices fixos salvos em estado.yml). */
    public void posicionar(World mundo) {
        for (Mina m : minas.values()) {
            int indice = plugin.estado().indiceMina(m.id());
            m.posicionar(mundo, indice, xInicial, espacamento, posZ, alturaTopo);
        }
    }

    public List<Mina> todas() {
        return new ArrayList<>(minas.values());
    }

    public List<Mina> normais() {
        return normaisPorRank;
    }

    public List<Mina> especiais() {
        List<Mina> l = new ArrayList<>();
        for (Mina m : minas.values()) {
            if (m.especial()) {
                l.add(m);
            }
        }
        return l;
    }

    public Mina obter(String id) {
        return id == null ? null : minas.get(id.toLowerCase(Locale.ROOT));
    }

    public Mina minaEm(Block b) {
        World w = b.getWorld();
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();
        for (Mina m : minas.values()) {
            if (m.contem(w, x, y, z)) {
                return m;
            }
        }
        return null;
    }

    public Mina minaNaRegiao(Location l) {
        for (Mina m : minas.values()) {
            if (m.naRegiao(l)) {
                return m;
            }
        }
        return null;
    }

    /** O local fica numa mina PvP (buraco ou plataforma)? */
    public boolean zonaPvp(Location l) {
        Mina m = minaNaRegiao(l);
        return m != null && m.pvp();
    }

    /** null = pode usar a mina; senão, o motivo. */
    public Bloqueio verificarAcesso(Player p, DadosJogador d, Mina m) {
        if (p.hasPermission("rankupsupremo.mina.todas")) {
            return null;
        }
        Rank atual = plugin.ranks().obter(d.rank());
        Rank necessario = plugin.ranks().obter(m.rankId());
        if (atual.indice() < necessario.indice()) {
            return new Bloqueio("mina-sem-acesso", "rank", necessario.tag());
        }
        if (m.permissao() != null && !p.hasPermission(m.permissao())) {
            return new Bloqueio("mina-sem-acesso-cargo", "cargo", m.nomeCargo());
        }
        if (d.prestigio() < m.prestigioMinimo()) {
            return new Bloqueio("mina-sem-acesso-prestigio", "prestigio", String.valueOf(m.prestigioMinimo()));
        }
        return null;
    }

    public boolean temAcesso(Player p, DadosJogador d, Mina m) {
        return verificarAcesso(p, d, m) == null;
    }

    /** Texto curto do requisito da mina (hologramas e menus). */
    public String requisito(Mina m) {
        String rank = plugin.ranks().obter(m.rankId()).tag();
        return switch (m.tipo()) {
            case VIP -> "<green>[" + m.nomeCargo() + "]";
            case PRESTIGIO -> "<light_purple>Prestígio " + m.prestigioMinimo() + "+";
            case PVP -> rank + " <red>(PvP)";
            default -> rank;
        };
    }

    /** Melhor mina NORMAL que o jogador pode acessar. */
    public Mina melhorMina(Player p, DadosJogador d) {
        Mina melhor = null;
        for (Mina m : normaisPorRank) {
            if (temAcesso(p, d, m)) {
                melhor = m;
            }
        }
        return melhor;
    }

    /**
     * Item que o jogador recebe ao quebrar um bloco na mina. Minas com "drops-rank" sorteiam
     * os minérios da mina do rank do jogador + N (assim a mina VIP é sempre melhor que a do rank).
     */
    public Material recompensa(Mina mina, Material bloco, DadosJogador d) {
        if (mina.dropsRank() <= 0 || normaisPorRank.isEmpty()) {
            return bloco;
        }
        return fonteDeDrops(mina, d).sortear();
    }

    /** Mina normal cujos minérios a mina especial entrega para este jogador (rank dele + "drops-rank"). */
    public Mina fonteDeDrops(Mina mina, DadosJogador d) {
        if (mina.dropsRank() <= 0 || normaisPorRank.isEmpty()) {
            return mina;
        }
        int alvo = plugin.ranks().obter(d.rank()).indice() + mina.dropsRank();
        Mina fonte = normaisPorRank.get(0);
        for (Mina m : normaisPorRank) {
            if (plugin.ranks().obter(m.rankId()).indice() <= alvo) {
                fonte = m;
            }
        }
        return fonte;
    }

    public void iniciarTarefa() {
        pararTarefa();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::verificar, 20L, 20L);
    }

    public void pararTarefa() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    private void verificar() {
        long agora = System.currentTimeMillis();
        long aviso = avisoSegundos * 1000L;
        for (Mina m : minas.values()) {
            if (m.mundo() == null || m.resetando()) {
                continue;
            }
            boolean porPorcentagem = m.resetPorcentagem() > 0 && m.restante() * 100.0 <= m.resetPorcentagem();
            if (porPorcentagem && m.proximoReset() - agora > aviso) {
                m.setProximoReset(agora + aviso); // avisa e reseta daqui a alguns segundos
            }
            long falta = m.proximoReset() - agora;
            if (falta <= 0) {
                resetar(m, true);
            } else if (aviso > 0 && falta <= aviso && !m.avisado()) {
                m.setAvisado(true);
                long segundos = (falta + 999) / 1000;
                for (Player p : m.mundo().getPlayers()) {
                    if (m.naRegiao(p.getLocation())) {
                        plugin.mensagens().enviar(p, "mina-aviso-reset", "mina", m.nome(), "segundos", String.valueOf(segundos));
                        Acoes.som(p, "block.note_block.pling", 0.8f, 0.7f);
                    }
                }
            }
        }
    }

    public void resetarTodas(boolean avisar) {
        for (Mina m : minas.values()) {
            if (m.mundo() != null && !m.resetando()) {
                resetar(m, avisar);
            }
        }
    }

    /** Reseta a mina em lotes (sem travar o servidor). */
    public void resetar(Mina m, boolean avisar) {
        if (m.mundo() == null || m.resetando()) {
            return;
        }
        m.setResetando(true);
        m.setAvisado(false);
        m.setQuebrados(0);
        m.setProximoReset(System.currentTimeMillis() + m.resetMinutos() * 60_000L);

        for (Player p : m.mundo().getPlayers()) {
            if (m.dentro(p.getLocation())) {
                p.teleport(m.spawn());
            }
        }

        final World w = m.mundo();
        final int tam = m.tamanho();
        final int area = tam * tam;
        final int total = m.volume();
        new BukkitRunnable() {
            int cursor = 0;

            @Override
            public void run() {
                int limite = Math.min(total, cursor + blocosPorTick);
                while (cursor < limite) {
                    int dy = cursor / area;
                    int resto = cursor % area;
                    int dx = resto % tam;
                    int dz = resto / tam;
                    w.getBlockAt(m.x0() + dx, m.yBase() + dy, m.z0() + dz).setType(m.sortear(), false);
                    cursor++;
                }
                if (cursor >= total) {
                    cancel();
                    m.setResetando(false);
                    if (avisar) {
                        for (Player p : w.getPlayers()) {
                            if (m.naRegiao(p.getLocation())) {
                                plugin.mensagens().enviar(p, "mina-resetada", "mina", m.nome());
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
