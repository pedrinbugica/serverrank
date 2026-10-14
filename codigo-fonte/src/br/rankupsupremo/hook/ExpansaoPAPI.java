package br.rankupsupremo.hook;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.util.Texto;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Placeholders para outros plugins: %rankup_rank%, %rankup_proximo%, %rankup_progresso%,
 * %rankup_prestigio%, %rankup_multiplicador%, %rankup_blocos%, %rankup_custo%...
 */
public final class ExpansaoPAPI extends PlaceholderExpansion {

    private final RankupSupremo plugin;

    public ExpansaoPAPI(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rankup";
    }

    @Override
    public String getAuthor() {
        return "RankUP Supremo";
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
        if (!(jogador instanceof Player p) || !p.isOnline()) {
            return "";
        }
        Map<String, String> v = plugin.visual().variaveis().de(p);
        String valor = v.get(parametro.toLowerCase(Locale.ROOT));
        // Outros plugins esperam texto com códigos clássicos (§), então convertemos o MiniMessage.
        return valor == null ? null : LEGADO.serialize(Texto.cor(valor));
    }

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
}
