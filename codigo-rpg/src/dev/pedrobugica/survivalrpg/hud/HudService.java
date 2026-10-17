package dev.pedrobugica.survivalrpg.hud;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.classe.Atributo;
import dev.pedrobugica.survivalrpg.habilidade.Habilidade;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que aparece na tela: vida e mana na barra de ação (e as habilidades no modo habilidade), o nível de RPG
 * na barra de XP e a regeneração da mana.
 */
public final class HudService {

    private final SurvivalRpgPlugin plugin;
    private final Map<UUID, Long> pausado = new HashMap<>();
    private BukkitTask tarefa;
    private int tique;

    public HudService(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    public void iniciar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    /** Outra mensagem acabou de ir para a barra de ação: espera um pouco antes de redesenhar. */
    public void pausar(Player p, long ms) {
        pausado.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    private void tick() {
        tique++;
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.mundoRpg(p.getWorld())) {
                continue;
            }
            PerfilRpg r = plugin.perfis().perfil(p);
            double max = plugin.perfis().manaMaxima(p);
            double regen = (plugin.getConfig().getDouble("mana.regeneracao-por-segundo", 2)
                    + 0.1 * plugin.perfis().atributoTotal(p, Atributo.SABEDORIA)) / 2;
            r.setMana(Math.min(max, r.mana() + regen));
            if (tique % 4 == 0) {
                atualizarXp(p);
            }
            if (pausado.getOrDefault(p.getUniqueId(), 0L) > agora) {
                continue;
            }
            p.sendActionBar(Texto.cor(linha(p, r, max)));
        }
    }

    private String linha(Player p, PerfilRpg r, double manaMax) {
        AttributeInstance vidaMax = p.getAttribute(Attribute.MAX_HEALTH);
        StringBuilder sb = new StringBuilder();
        sb.append("<red>❤ ").append((int) Math.ceil(p.getHealth())).append("<dark_red>/").append((int) Math.round(vidaMax == null ? 20 : vidaMax.getValue()))
                .append("   <aqua>✦ ").append((int) r.mana()).append("<dark_aqua>/").append((int) manaMax);
        if (r.modoHabilidade() && r.classe() != null) {
            sb.append("  <dark_gray>│");
            List<Habilidade> l = Habilidade.daClasse(r.classe());
            for (int i = 0; i < l.size(); i++) {
                Habilidade h = l.get(i);
                long falta = r.recarga(h.name());
                String estado = r.nivel() < h.nivelMinimo() ? "<dark_gray>Nv " + h.nivelMinimo()
                        : falta > 0 ? "<red>" + (falta / 1000 + 1) + "s" : r.mana() < h.mana() ? "<blue>" + h.mana() + "✦" : "<green>✔";
                sb.append(" <yellow>[").append(i + 1).append("] <white>").append(h.nome()).append(" ").append(estado);
            }
        } else if (r.classe() == null) {
            sb.append("  <dark_gray>│ <yellow>Escolha sua classe: <white>/classe");
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (plugin.itens().ehItemRpg(mao) && !plugin.itens().podeUsar(p, mao)) {
            sb.append("  <red>⚠ Item de nível ").append(plugin.itens().nivelExigido(mao));
        }
        String rumo = r.modoHabilidade() ? null : plugin.lugares().textoRumo(p);
        if (rumo != null) {
            sb.append("  <dark_gray>│ ").append(rumo);
        }
        return sb.toString();
    }

    /** Barra de XP do jogo mostrando o nível de RPG (a XP de verdade, de encantar, continua igual). */
    public void atualizarXp(Player p) {
        if (!plugin.getConfig().getBoolean("hud.barra-xp-rpg", true) || !plugin.mundoRpg(p.getWorld())) {
            return;
        }
        PerfilRpg r = plugin.perfis().perfil(p);
        double precisa = plugin.perfis().xpPara(r.nivel());
        float progresso = r.nivel() >= plugin.perfis().nivelMaximo() ? 1f : (float) Math.max(0, Math.min(0.999, r.xp() / Math.max(1, precisa)));
        p.sendExperienceChange(progresso, r.nivel());
    }

    public void esquecer(UUID u) {
        pausado.remove(u);
    }
}
