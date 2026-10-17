package dev.pedrobugica.npcvivos.hook;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.NivelReputacao;
import dev.pedrobugica.npcvivos.util.Texto;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Placeholders (PlaceholderAPI) para chat, TAB e scoreboard:
 * %npcvivos_faccao% (nome da facção do jogador), %npcvivos_faccao_id%, %npcvivos_tag% (etiqueta para o chat),
 * %npcvivos_reputacao_<facção>%, %npcvivos_nivel_<facção>%, %npcvivos_missoes% (missões ativas) e
 * %npcvivos_item_nivel% (nível de aprimoramento do item na mão) e %npcvivos_guerra% (placar da guerra da facção).
 */
public final class ExpansaoPAPI extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final NpcVivosPlugin plugin;

    public ExpansaoPAPI(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "npcvivos";
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

    @Override
    public String onRequest(OfflinePlayer jogador, String parametro) {
        if (jogador == null) {
            return "";
        }
        String par = parametro.toLowerCase(Locale.ROOT);
        Faccao membro = plugin.faccoes().obter(plugin.repositorio().membro(jogador.getUniqueId()));
        switch (par) {
            case "faccao" -> {
                return membro == null ? "" : legado(membro.nome());
            }
            case "faccao_id" -> {
                return membro == null ? "" : membro.id();
            }
            case "tag" -> {
                return membro == null ? "" : legado(membro.tag());
            }
            case "item_nivel" -> {
                return jogador instanceof Player p ? String.valueOf(plugin.upgrades().itens().nivel(p.getInventory().getItemInMainHand())) : "0";
            }
            case "guerra" -> {
                var g = membro == null ? null : plugin.guerras().daFaccao(membro.id());
                if (g == null) {
                    return "";
                }
                Faccao fa = plugin.faccoes().obter(g.a());
                Faccao fb = plugin.faccoes().obter(g.b());
                return legado((fa == null ? g.a() : fa.nome()) + " <white>" + g.pontosA() + " <gray>x <white>" + g.pontosB() + " "
                        + (fb == null ? g.b() : fb.nome()));
            }
            case "missoes" -> {
                return jogador instanceof Player p ? String.valueOf(plugin.missoes().ativas(p).size()) : "0";
            }
            default -> {
            }
        }
        if (par.startsWith("reputacao_") || par.startsWith("nivel_")) {
            Faccao f = plugin.faccoes().obter(par.substring(par.indexOf('_') + 1));
            if (f == null) {
                return "";
            }
            int pontos = plugin.faccoes().reputacao(jogador.getUniqueId(), f.id());
            return par.startsWith("reputacao_") ? String.valueOf(pontos) : legado(NivelReputacao.de(pontos).formatado());
        }
        return null;
    }

    private static String legado(String miniMessage) {
        return LEGADO.serialize(Texto.cor(miniMessage));
    }
}
