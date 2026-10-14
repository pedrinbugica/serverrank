package br.rankupsupremo.listener;

import br.rankupsupremo.Acoes;
import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.mina.GerenciadorMinas;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.picareta.GerenciadorPicareta.Tipo;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mineração nas minas: coleta automática, Fortuna, Explosão, Laser, Fundição, tokens e venda automática.
 * Fora das minas, quebrar blocos no mundo Rankup é bloqueado (a não ser no modo construção).
 */
public final class MineracaoListener implements Listener {

    private final RankupSupremo plugin;
    private final Map<UUID, Long> avisoCheio = new HashMap<>();
    private final Map<UUID, Long> avisoArea = new HashMap<>();

    public MineracaoListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!plugin.ehMundoRankup(b.getWorld()) || plugin.emModoConstrucao(p)) {
            return; // fora do Rankup (ex.: Sobrevivência) a mineração é normal
        }
        Mina mina = plugin.minas().minaEm(b);
        if (mina == null) {
            e.setCancelled(true);
            avisar(p, avisoArea, "area-protegida");
            return;
        }
        if (mina.resetando()) {
            e.setCancelled(true);
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        GerenciadorMinas.Bloqueio bloqueio = plugin.minas().verificarAcesso(p, d, mina);
        if (bloqueio != null) {
            e.setCancelled(true);
            avisar(p, avisoArea, bloqueio.chave(), bloqueio.pares());
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);

        GerenciadorPicareta gp = plugin.picareta();
        ItemStack ferramenta = p.getInventory().getItemInMainHand();
        int fortuna = gp.nivel(ferramenta, Tipo.FORTUNA);
        boolean fundicao = gp.nivel(ferramenta, Tipo.FUNDICAO) > 0;
        Map<Material, Integer> ganhos = new EnumMap<>(Material.class);
        adicionar(ganhos, mina, b.getType(), d, fortuna, fundicao);
        int quebrados = 1;
        World w = b.getWorld();

        int laser = gp.nivel(ferramenta, Tipo.LASER);
        boolean laserAtivou = gp.sortearLaser(laser);
        if (laserAtivou) {
            quebrados += camada(mina, b, d, fortuna, fundicao, ganhos);
            w.spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 0.5, 0.5), 60, mina.tamanho() / 4.0, 0.2,
                    mina.tamanho() / 4.0, 0.02);
            Acoes.som(p, "entity.guardian.attack", 0.6f, 1.6f);
        } else {
            int explosao = gp.nivel(ferramenta, Tipo.EXPLOSAO);
            if (gp.sortearExplosao(explosao)) {
                quebrados += explodir(mina, b, gp.raioExplosao(), d, fortuna, fundicao, ganhos);
                w.spawnParticle(Particle.EXPLOSION, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.6, 0.6, 0.6, 0);
                Acoes.som(p, "entity.generic.explode", 0.5f, 1.3f);
            }
        }
        mina.adicionarQuebrados(quebrados);
        d.adicionarBlocos(quebrados);
        long tokens = sortearTokens(quebrados, mina, gp.nivel(ferramenta, Tipo.SORTE_TOKEN), d);
        if (tokens > 0) {
            d.adicionarTokens(tokens);
        }
        entregar(p, d, ganhos, tokens, laserAtivou);
    }

    private void adicionar(Map<Material, Integer> ganhos, Mina mina, Material bloco, DadosJogador d, int fortuna, boolean fundicao) {
        Material item = plugin.minas().recompensa(mina, bloco, d);
        if (fundicao) {
            item = GerenciadorPicareta.fundir(item);
        }
        ganhos.merge(item, plugin.picareta().quantidade(fortuna), Integer::sum);
    }

    private int explodir(Mina mina, Block centro, int raio, DadosJogador d, int fortuna, boolean fundicao,
                         Map<Material, Integer> ganhos) {
        World w = centro.getWorld();
        int quebrados = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int y = -raio; y <= raio; y++) {
                for (int z = -raio; z <= raio; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    int bx = centro.getX() + x;
                    int by = centro.getY() + y;
                    int bz = centro.getZ() + z;
                    if (!mina.contem(w, bx, by, bz)) {
                        continue;
                    }
                    Block alvo = w.getBlockAt(bx, by, bz);
                    Material tipo = alvo.getType();
                    if (tipo.isAir()) {
                        continue;
                    }
                    alvo.setType(Material.AIR, false);
                    adicionar(ganhos, mina, tipo, d, fortuna, fundicao);
                    quebrados++;
                }
            }
        }
        return quebrados;
    }

    /** Laser: quebra a camada inteira da mina na altura do bloco. */
    private int camada(Mina mina, Block centro, DadosJogador d, int fortuna, boolean fundicao, Map<Material, Integer> ganhos) {
        World w = centro.getWorld();
        int y = centro.getY();
        int quebrados = 0;
        for (int x = mina.x0(); x <= mina.x1(); x++) {
            for (int z = mina.z0(); z <= mina.z1(); z++) {
                if (x == centro.getX() && z == centro.getZ()) {
                    continue;
                }
                Block alvo = w.getBlockAt(x, y, z);
                Material tipo = alvo.getType();
                if (tipo.isAir()) {
                    continue;
                }
                alvo.setType(Material.AIR, false);
                adicionar(ganhos, mina, tipo, d, fortuna, fundicao);
                quebrados++;
            }
        }
        return quebrados;
    }

    private long sortearTokens(int blocos, Mina mina, int sorteToken, DadosJogador d) {
        FileConfiguration c = plugin.getConfig();
        double chance = c.getDouble("tokens.chance-por-bloco", 2.0) * mina.multiplicadorTokens()
                * plugin.picareta().multiplicadorSorteToken(sorteToken) * (1.0 + d.boosterTokensAtivo());
        if (chance <= 0) {
            return 0;
        }
        int min = Math.max(1, c.getInt("tokens.minimo", 1));
        int max = Math.max(min, c.getInt("tokens.maximo", 3));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long total = 0;
        for (int i = 0; i < blocos; i++) {
            if (r.nextDouble(100.0) < chance) {
                total += min + r.nextInt(max - min + 1);
            }
        }
        return total;
    }

    private void entregar(Player p, DadosJogador d, Map<Material, Integer> ganhos, long tokens, boolean laser) {
        List<String> barra = new ArrayList<>();
        if (laser) {
            barra.add(plugin.mensagens().bruta("laser-barra"));
        }
        boolean auto = d.autovender() && plugin.acoes().podeAutovender(p, d) && plugin.economia().ativa();
        if (auto) {
            double mult = plugin.vendas().multiplicador(p, d);
            double total = 0;
            Map<Material, Integer> semPreco = new EnumMap<>(Material.class);
            for (Map.Entry<Material, Integer> g : ganhos.entrySet()) {
                double preco = plugin.vendas().preco(g.getKey());
                if (preco > 0) {
                    total += preco * g.getValue() * mult;
                } else {
                    semPreco.put(g.getKey(), g.getValue());
                }
            }
            if (total > 0 && plugin.economia().depositar(p, total)) {
                barra.add(Texto.substituir(plugin.mensagens().bruta("autovender-barra"),
                        "valor", Numeros.abreviar(total), "multiplicador", Numeros.multiplicador(mult)));
            }
            ganhos = semPreco;
        }
        if (tokens > 0) {
            barra.add(Texto.substituir(plugin.mensagens().bruta("tokens-barra"), "tokens", Numeros.inteiro(tokens),
                    "total", Numeros.abreviar(d.tokens())));
        }
        if (!barra.isEmpty()) {
            p.sendActionBar(Texto.cor(String.join(" <dark_gray>• ", barra)));
        }
        boolean cheio = false;
        for (Map.Entry<Material, Integer> g : ganhos.entrySet()) {
            int restante = g.getValue();
            int max = Math.max(1, g.getKey().getMaxStackSize());
            while (restante > 0) {
                int qtd = Math.min(max, restante);
                restante -= qtd;
                if (!p.getInventory().addItem(new ItemStack(g.getKey(), qtd)).isEmpty()) {
                    cheio = true;
                    restante = 0;
                }
            }
        }
        if (cheio) {
            long agora = System.currentTimeMillis();
            Long ultimo = avisoCheio.get(p.getUniqueId());
            if (ultimo == null || agora - ultimo > 3000) {
                avisoCheio.put(p.getUniqueId(), agora);
                Acoes.titulo(p, plugin.mensagens().bruta("inventario-cheio-titulo"),
                        plugin.mensagens().bruta("inventario-cheio-subtitulo"));
                Acoes.som(p, "block.note_block.bass", 1f, 0.6f);
            }
        }
    }

    private void avisar(Player p, Map<UUID, Long> mapa, String chave, String... pares) {
        long agora = System.currentTimeMillis();
        Long ultimo = mapa.get(p.getUniqueId());
        if (ultimo == null || agora - ultimo > 2500) {
            mapa.put(p.getUniqueId(), agora);
            plugin.mensagens().enviar(p, chave, pares);
        }
    }

    public void limpar(UUID uuid) {
        avisoCheio.remove(uuid);
        avisoArea.remove(uuid);
    }
}
