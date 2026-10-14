package br.rankupsupremo;

import br.rankupsupremo.config.Mensagens;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.economia.Economia;
import br.rankupsupremo.economia.GerenciadorVendas;
import br.rankupsupremo.mina.GerenciadorMinas;
import br.rankupsupremo.mina.Mina;
import br.rankupsupremo.picareta.GerenciadorPicareta;
import br.rankupsupremo.picareta.GerenciadorPicareta.Melhoria;
import br.rankupsupremo.picareta.GerenciadorPicareta.Tipo;
import br.rankupsupremo.rank.Rank;
import br.rankupsupremo.util.Numeros;
import br.rankupsupremo.util.Texto;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

/**
 * Ações de jogo compartilhadas entre comandos e menus.
 */
public final class Acoes {

    private final RankupSupremo plugin;

    public Acoes(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    private Mensagens msg() {
        return plugin.mensagens();
    }

    public static void som(Player p, String chave, float volume, float tom) {
        p.playSound(Sound.sound(Key.key(chave), Sound.Source.MASTER, volume, tom));
    }

    public static void titulo(Player p, String titulo, String subtitulo, String... pares) {
        p.showTitle(Title.title(Texto.cor(titulo, pares), Texto.cor(subtitulo, pares),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
    }

    private boolean economiaOk(Player p) {
        if (!plugin.economia().ativa()) {
            msg().enviar(p, "economia-indisponivel");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ RANKUP

    /** Coisas que usam o dinheiro do Rankup só funcionam no mundo Rankup (o Sobrevivência tem dinheiro separado). */
    public boolean noRankup(Player p) {
        if (plugin.ehMundoRankup(p.getWorld())) {
            return true;
        }
        msg().enviar(p, "apenas-rankup");
        som(p, "entity.villager.no", 1f, 1f);
        return false;
    }

    public boolean rankup(Player p) {
        if (!noRankup(p) || !economiaOk(p)) {
            return false;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        Rank atual = plugin.ranks().obter(d.rank());
        Rank proximo = plugin.ranks().proximo(atual);
        if (proximo == null) {
            msg().enviar(p, "rankup-ultimo-rank");
            som(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        if (d.blocos() < proximo.blocosNecessarios()) {
            msg().enviar(p, "rankup-sem-blocos", "rank", proximo.tag(), "blocos", Numeros.inteiro(proximo.blocosNecessarios()),
                    "falta", Numeros.inteiro(proximo.blocosNecessarios() - d.blocos()));
            som(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        double custo = plugin.ranks().custo(proximo, d.prestigio());
        Economia eco = plugin.economia();
        double saldo = eco.saldo(p);
        if (saldo < custo) {
            msg().enviar(p, "rankup-sem-dinheiro", "custo", Numeros.abreviar(custo), "falta",
                    Numeros.abreviar(custo - saldo), "rank", proximo.tag());
            som(p, "entity.villager.no", 1f, 1f);
            return false;
        }
        if (!eco.sacar(p, custo)) {
            msg().enviar(p, "erro-transacao");
            return false;
        }
        d.setRank(proximo.id());
        for (String cmd : proximo.comandos()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    Texto.substituir(cmd, "jogador", p.getName(), "rank", proximo.id()));
        }
        titulo(p, msg().bruta("rankup-titulo"), msg().bruta("rankup-subtitulo"), "rank", proximo.tag());
        som(p, "ui.toast.challenge_complete", 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
        msg().enviar(p, "rankup-sucesso", "rank", proximo.tag(), "custo", Numeros.abreviar(custo));
        msg().transmitir("rankup-transmissao", "jogador", p.getName(), "rank", proximo.tag());
        if (plugin.ranks().proximo(proximo) == null) {
            msg().enviar(p, "rankup-chegou-ultimo");
        } else {
            Mina nova = minaDoRank(proximo);
            if (nova != null) {
                msg().enviar(p, "rankup-nova-mina", "mina", nova.nome());
            }
        }
        plugin.visual().atualizarJogador(p);
        plugin.jogadores().atualizarTop();
        return true;
    }

    private Mina minaDoRank(Rank rank) {
        for (Mina m : plugin.minas().todas()) {
            if (m.rankId().equals(rank.id())) {
                return m;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ PRESTÍGIO

    public void infoPrestigio(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        double custo = plugin.ranks().custoPrestigio(d.prestigio());
        double bonusAtual = d.prestigio() * plugin.ranks().bonusVendaPrestigio() * 100;
        double bonusNovo = (d.prestigio() + 1) * plugin.ranks().bonusVendaPrestigio() * 100;
        msg().enviarLista(p, "prestigio-info", "prestigio", String.valueOf(d.prestigio()),
                "proximo", String.valueOf(d.prestigio() + 1), "custo", Numeros.abreviar(custo),
                "rank", plugin.ranks().ultimo().tag(), "bonus-atual", Numeros.multiplicador(bonusAtual),
                "bonus-novo", Numeros.multiplicador(bonusNovo),
                "aumento", Numeros.multiplicador(plugin.getConfig().getDouble("prestigio.aumento-custo-ranks", 0.35) * 100));
    }

    public void prestigiar(Player p) {
        if (!noRankup(p) || !economiaOk(p)) {
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        if (d.prestigio() >= plugin.ranks().prestigioMaximo()) {
            msg().enviar(p, "prestigio-maximo");
            return;
        }
        Rank atual = plugin.ranks().obter(d.rank());
        if (atual.indice() != plugin.ranks().ultimo().indice()) {
            msg().enviar(p, "prestigio-nao-ultimo-rank", "rank", plugin.ranks().ultimo().tag());
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        double custo = plugin.ranks().custoPrestigio(d.prestigio());
        double saldo = plugin.economia().saldo(p);
        if (saldo < custo) {
            msg().enviar(p, "prestigio-sem-dinheiro", "custo", Numeros.abreviar(custo), "falta", Numeros.abreviar(custo - saldo));
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        if (!plugin.economia().sacar(p, custo)) {
            msg().enviar(p, "erro-transacao");
            return;
        }
        if (plugin.getConfig().getBoolean("prestigio.resetar-dinheiro", false)) {
            double resto = plugin.economia().saldo(p);
            if (resto > 0) {
                plugin.economia().sacar(p, resto);
            }
        }
        d.setPrestigio(d.prestigio() + 1);
        d.setRank(plugin.ranks().primeiro().id());
        for (String cmd : plugin.getConfig().getStringList("prestigio.comandos")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    Texto.substituir(cmd, "jogador", p.getName(), "prestigio", String.valueOf(d.prestigio())));
        }
        String ps = String.valueOf(d.prestigio());
        titulo(p, msg().bruta("prestigio-titulo"), msg().bruta("prestigio-subtitulo"), "prestigio", ps);
        som(p, "ui.toast.challenge_complete", 1f, 0.8f);
        som(p, "entity.ender_dragon.growl", 0.6f, 1.2f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 0.6, 1.2, 0.6, 0.15);
        msg().enviar(p, "prestigio-sucesso", "prestigio", ps);
        msg().transmitir("prestigio-transmissao", "jogador", p.getName(), "prestigio", ps);
        plugin.visual().atualizarJogador(p);
        plugin.jogadores().atualizarTop();
        teleportarSpawn(p, false);
    }

    // ------------------------------------------------------------------ MINAS

    public void teleportarMina(Player p, Mina m) {
        DadosJogador d = plugin.jogadores().obter(p);
        GerenciadorMinas.Bloqueio bloqueio = plugin.minas().verificarAcesso(p, d, m);
        if (bloqueio != null) {
            msg().enviar(p, bloqueio.chave(), bloqueio.pares());
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        if (m.mundo() == null) {
            msg().enviar(p, "mina-nao-encontrada");
            return;
        }
        p.teleport(m.spawn());
        som(p, "entity.enderman.teleport", 1f, 1.2f);
        msg().enviar(p, "mina-teleportado", "mina", m.nome());
        if (m.pvp()) {
            msg().enviar(p, "mina-pvp-aviso");
        }
    }

    public void teleportarSpawn(Player p, boolean avisar) {
        p.teleport(plugin.spawn());
        if (avisar) {
            som(p, "entity.enderman.teleport", 1f, 1f);
            msg().enviar(p, "spawn-teleportado");
        }
    }

    // ------------------------------------------------------------------ VENDAS

    public void vender(Player p) {
        if (!economiaOk(p)) {
            return;
        }
        GerenciadorVendas.Resultado r = plugin.vendas().venderInventario(p);
        if (r.itens() < 0) {
            msg().enviar(p, "erro-transacao");
            return;
        }
        if (r.itens() == 0) {
            msg().enviar(p, "vender-nada");
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        msg().enviar(p, "vender-sucesso", "quantidade", Numeros.inteiro(r.itens()), "valor", Numeros.abreviar(r.valor()),
                "multiplicador", Numeros.multiplicador(r.multiplicador()));
        som(p, "entity.experience_orb.pickup", 1f, 1.4f);
    }

    public boolean podeAutovender(Player p, DadosJogador d) {
        return p.hasPermission("rankupsupremo.autovender")
                || d.prestigio() >= plugin.getConfig().getInt("vendas.autovender.prestigio-minimo", 1);
    }

    public void alternarAutovender(Player p) {
        DadosJogador d = plugin.jogadores().obter(p);
        if (!podeAutovender(p, d)) {
            msg().enviar(p, "autovender-bloqueado", "prestigio",
                    String.valueOf(plugin.getConfig().getInt("vendas.autovender.prestigio-minimo", 1)));
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        d.setAutovender(!d.autovender());
        msg().enviar(p, d.autovender() ? "autovender-ativado" : "autovender-desativado");
        som(p, "ui.button.click", 1f, d.autovender() ? 1.5f : 0.8f);
    }

    // ------------------------------------------------------------------ PICARETA

    /** Compra até "quantidade" níveis da melhoria (para quando faltar dinheiro ou chegar ao máximo). */
    public void comprarMelhoria(Player p, Tipo tipo, int quantidade) {
        if (!noRankup(p)) {
            p.closeInventory();
            return;
        }
        GerenciadorPicareta gp = plugin.picareta();
        Melhoria m = gp.melhoria(tipo);
        boolean tokens = m.moeda() == GerenciadorPicareta.Moeda.TOKENS;
        if (!tokens && !economiaOk(p)) {
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!GerenciadorPicareta.ehPicareta(item)) {
            msg().enviar(p, "picareta-segure");
            p.closeInventory();
            return;
        }
        int nivel = gp.nivel(item, tipo);
        if (nivel >= m.maximo()) {
            msg().enviar(p, "picareta-nivel-maximo", "melhoria", m.nome());
            som(p, "entity.villager.no", 1f, 1f);
            return;
        }
        DadosJogador d = plugin.jogadores().obter(p);
        int comprados = 0;
        double gasto = 0;
        while (comprados < quantidade && nivel < m.maximo()) {
            double custo = m.custo(nivel);
            boolean pago;
            if (tokens) {
                pago = d.tokens() >= custo;
                if (pago) {
                    d.adicionarTokens(-(long) custo);
                }
            } else {
                pago = plugin.economia().tem(p, custo) && plugin.economia().sacar(p, custo);
            }
            if (!pago) {
                if (comprados == 0) {
                    if (tokens) {
                        msg().enviar(p, "picareta-sem-tokens", "custo", Numeros.inteiro((long) custo),
                                "falta", Numeros.inteiro((long) custo - d.tokens()));
                    } else {
                        msg().enviar(p, "picareta-sem-dinheiro", "custo", Numeros.abreviar(custo),
                                "falta", Numeros.abreviar(custo - plugin.economia().saldo(p)));
                    }
                    som(p, "entity.villager.no", 1f, 1f);
                    return;
                }
                break;
            }
            nivel++;
            comprados++;
            gasto += custo;
        }
        gp.definirNivel(item, tipo, nivel);
        p.getInventory().setItemInMainHand(item);
        msg().enviar(p, tokens ? "picareta-melhorada-tokens" : "picareta-melhorada", "melhoria", m.nome(),
                "nivel", String.valueOf(nivel), "niveis", String.valueOf(comprados),
                "custo", tokens ? Numeros.inteiro((long) gasto) : Numeros.abreviar(gasto));
        plugin.efeitos().atualizar(p);
        som(p, "block.anvil.use", 0.7f, 1.4f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 80, 0.5, 0.5, 0.5, 1);
    }

    public void darPicareta(Player p, boolean forcar) {
        if (!forcar && plugin.picareta().temPicareta(p)) {
            msg().enviar(p, "picareta-ja-tem");
            return;
        }
        var sobra = p.getInventory().addItem(plugin.picareta().criar());
        if (!sobra.isEmpty()) {
            for (ItemStack it : sobra.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
            }
        }
        msg().enviar(p, "picareta-recebida");
        som(p, "entity.item.pickup", 1f, 1f);
    }

    // ------------------------------------------------------------------ TOP

    public void mostrarTop(Player p) {
        List<DadosJogador> top = plugin.jogadores().top();
        msg().enviarSemPrefixo(p, "toprank-cabecalho");
        if (top.isEmpty()) {
            msg().enviarSemPrefixo(p, "toprank-vazio");
        }
        int pos = 1;
        for (DadosJogador d : top) {
            msg().enviarSemPrefixo(p, "toprank-linha", "posicao", String.valueOf(pos++), "jogador", d.nome(),
                    "rank", plugin.ranks().obter(d.rank()).tag(), "prestigio", String.valueOf(d.prestigio()),
                    "blocos", Numeros.abreviar(d.blocos()));
        }
        msg().enviarSemPrefixo(p, "toprank-rodape");
    }

    public Location spawn() {
        return plugin.spawn();
    }
}
