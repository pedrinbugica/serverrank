package br.rankupsupremo.picareta;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.picareta.GerenciadorPicareta.Tipo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Melhorias Velocidade e Visão Noturna: dão o efeito enquanto o jogador segura a picareta no mundo Rankup.
 * Só removemos efeitos que nós mesmos demos (poções de verdade não são tocadas).
 */
public final class EfeitosPicareta {

    private static final int DURACAO_VELOCIDADE = 60;
    private static final int DURACAO_VISAO = 400; // acima de 200 ticks a tela não pisca

    private final RankupSupremo plugin;
    private final Set<UUID> comVelocidade = ConcurrentHashMap.newKeySet();
    private final Set<UUID> comVisao = ConcurrentHashMap.newKeySet();
    private BukkitTask tarefa;

    public EfeitosPicareta(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    public void iniciar() {
        parar();
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                atualizar(p);
            }
        }, 40L, 20L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            limpar(p);
        }
    }

    public void atualizar(Player p) {
        int velocidade = 0;
        int visao = 0;
        if (plugin.ehMundoRankup(p.getWorld())) {
            ItemStack mao = p.getInventory().getItemInMainHand();
            if (GerenciadorPicareta.ehPicareta(mao)) {
                velocidade = plugin.picareta().nivel(mao, Tipo.VELOCIDADE);
                visao = plugin.picareta().nivel(mao, Tipo.VISAO_NOTURNA);
            }
        }
        if (velocidade > 0) {
            aplicar(p, PotionEffectType.SPEED, Math.min(velocidade, 10) - 1, DURACAO_VELOCIDADE, comVelocidade);
        } else {
            remover(p, PotionEffectType.SPEED, DURACAO_VELOCIDADE, comVelocidade);
        }
        if (visao > 0) {
            aplicar(p, PotionEffectType.NIGHT_VISION, 0, DURACAO_VISAO, comVisao);
        } else {
            remover(p, PotionEffectType.NIGHT_VISION, DURACAO_VISAO, comVisao);
        }
    }

    private static void aplicar(Player p, PotionEffectType tipo, int amplificador, int duracao, Set<UUID> marcados) {
        PotionEffect atual = p.getPotionEffect(tipo);
        if (atual != null && !marcados.contains(p.getUniqueId())
                && (atual.getAmplifier() > amplificador || atual.getDuration() > duracao)) {
            return; // o jogador já tem um efeito melhor (ex.: poção)
        }
        p.addPotionEffect(new PotionEffect(tipo, duracao, amplificador, true, false, true));
        marcados.add(p.getUniqueId());
    }

    private static void remover(Player p, PotionEffectType tipo, int duracao, Set<UUID> marcados) {
        if (!marcados.remove(p.getUniqueId())) {
            return;
        }
        PotionEffect atual = p.getPotionEffect(tipo);
        if (atual != null && atual.isAmbient() && atual.getDuration() <= duracao) {
            p.removePotionEffect(tipo);
        }
    }

    public void limpar(Player p) {
        remover(p, PotionEffectType.SPEED, DURACAO_VELOCIDADE, comVelocidade);
        remover(p, PotionEffectType.NIGHT_VISION, DURACAO_VISAO, comVisao);
    }
}
