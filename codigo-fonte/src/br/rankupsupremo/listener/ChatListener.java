package br.rankupsupremo.listener;

import br.rankupsupremo.RankupSupremo;
import br.rankupsupremo.dados.DadosJogador;
import br.rankupsupremo.util.Texto;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Formato do chat: [Prestígio] [Rank] [VIP/Staff] Nome » mensagem
 */
public final class ChatListener implements Listener {

    private final RankupSupremo plugin;

    public ChatListener(RankupSupremo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFalar(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.ativado", true)) {
            return;
        }
        Player p = e.getPlayer();
        DadosJogador d = plugin.jogadores().obterSeExistir(p.getUniqueId());
        String tag = d == null ? plugin.ranks().primeiro().tag() : plugin.ranks().obter(d.rank()).tag();
        int prestigio = d == null ? 0 : d.prestigio();
        String prestigioTxt = prestigio > 0
                ? Texto.substituir(plugin.getConfig().getString("chat.formato-prestigio", ""), "prestigio", String.valueOf(prestigio))
                : "";
        String prefixo = Texto.converterLegado(plugin.economia().prefixo(p));
        String formato = plugin.getConfig().getString("chat.formato", "{rank} <white>{jogador}<gray>: {mensagem}");
        String tagMundo = plugin.sobrevivencia().ehSobrevivencia(p.getWorld())
                ? plugin.getConfig().getString("chat.tag-mundo.sobrevivencia", "")
                : plugin.getConfig().getString("chat.tag-mundo.rankup", "");
        // Etiqueta da facção do plugin NpcVivos (ex.: [Aurora]); vai onde estiver {faccao} ou no começo da linha
        String faccao = "";
        if (plugin.temPlaceholderAPI() && plugin.getConfig().getBoolean("chat.mostrar-faccao", true)) {
            String t = PlaceholderAPI.setPlaceholders(p, "%npcvivos_tag%");
            if (!t.contains("%npcvivos_")) {
                faccao = Texto.converterLegado(t);
            }
        }
        if (!formato.contains("{faccao}")) {
            formato = "{faccao}" + formato;
        }
        String modelo = Texto.substituir(formato, "mundo_tag", tagMundo, "prestigio", prestigioTxt, "rank", tag,
                "prefixo", prefixo, "faccao", faccao, "jogador", p.getName()).replace("{mensagem}", "<mensagem>");

        Component mensagem = e.message();
        if (p.hasPermission("rankupsupremo.chat.cores")) {
            mensagem = LegacyComponentSerializer.legacyAmpersand().deserialize(Texto.plano(mensagem));
        }
        Component formatado = Texto.cor(modelo, Placeholder.component("mensagem", mensagem));
        e.renderer(ChatRenderer.viewerUnaware((fonte, nome, msg) -> formatado));
    }
}
