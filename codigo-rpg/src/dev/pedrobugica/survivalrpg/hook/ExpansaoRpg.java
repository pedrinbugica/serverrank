package dev.pedrobugica.survivalrpg.hook;

import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.jogador.PerfilRpg;
import dev.pedrobugica.survivalrpg.mundo.Regiao;
import dev.pedrobugica.survivalrpg.util.Texto;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Placeholders para scoreboard, TAB e chat: %rpg_classe%, %rpg_classe_cor%, %rpg_nivel%, %rpg_xp%, %rpg_xp_max%,
 * %rpg_xp_porcento%, %rpg_mana%, %rpg_mana_max%, %rpg_regiao%, %rpg_regiao_niveis%, %rpg_pontos%, %rpg_grupo% e
 * %rpg_lugares%.
 */
public final class ExpansaoRpg extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final SurvivalRpgPlugin plugin;

    public ExpansaoRpg(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rpg";
    }

    @Override
    public String getAuthor() {
        return "pedrobugica";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static String legado(String mini) {
        return LEGADO.serialize(Texto.cor(mini));
    }

    @Override
    public String onRequest(OfflinePlayer jogador, String parametro) {
        if (!(jogador instanceof Player p) || !p.isOnline()) {
            return "";
        }
        PerfilRpg r = plugin.perfis().perfil(p);
        return switch (parametro.toLowerCase(Locale.ROOT)) {
            case "classe" -> r.classe() == null ? "Nenhuma" : r.classe().nome();
            case "classe_cor" -> r.classe() == null ? legado("<gray>Nenhuma") : legado(r.classe().formatado());
            case "nivel" -> String.valueOf(r.nivel());
            case "xp" -> Texto.numero(r.xp());
            case "xp_max" -> Texto.numero(plugin.perfis().xpPara(r.nivel()));
            case "xp_porcento" -> String.valueOf((int) (100 * r.xp() / Math.max(1, plugin.perfis().xpPara(r.nivel()))));
            case "mana" -> String.valueOf((int) r.mana());
            case "mana_max" -> String.valueOf((int) plugin.perfis().manaMaxima(p));
            case "pontos" -> String.valueOf(r.pontosLivres());
            case "regiao" -> {
                var z = plugin.regioes().zona(p.getLocation());
                Regiao reg = plugin.regioes().regiao(p.getLocation());
                yield z != null ? legado(z.nome()) : reg == null ? "" : legado(reg.nome());
            }
            case "regiao_niveis" -> {
                Regiao reg = plugin.regioes().regiao(p.getLocation());
                yield reg == null ? "" : reg.faixa();
            }
            case "grupo" -> {
                var g = plugin.grupos().grupo(p);
                yield g == null ? "0" : String.valueOf(g.membros().size());
            }
            case "lugares" -> String.valueOf(r.regioesDescobertas().size());
            default -> null;
        };
    }
}
