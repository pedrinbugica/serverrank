package dev.pedrobugica.npcvivos.mundo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comercio.ItemLoja;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import dev.pedrobugica.npcvivos.upgrade.ItemAprimoravel;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preço dinâmico e comércio entre NPCs (fase 7).
 *
 * <p>Cada loja lembra quantos lotes de cada item os jogadores compraram (procura, D) e venderam (oferta, O)
 * nas últimas horas. O preço segue a fórmula do documento: preço = base x (1 + (D - O) / (O + D + k)),
 * arredondado em degraus (5%) e com piso e teto. Os números "esquecem" devagar, então o preço volta sozinho
 * para o normal. Em cima disso entram a guerra (itens de combate mais caros), a rota cortada (item importado
 * de uma facção inimiga), o bônus de quem venceu a última guerra e as promoções da staff.</p>
 */
public final class MercadoService {

    private static final Set<Material> COMBATE = EnumSet.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW,
            Material.SHIELD, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.TOTEM_OF_UNDYING,
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TNT, Material.ENDER_PEARL);

    private final NpcVivosPlugin plugin;
    /** Promoções da staff: facção (ou "*") -> {desconto 0..1, termina em ms}. Somem ao reiniciar. */
    private final Map<String, double[]> promocoes = new ConcurrentHashMap<>();

    public MercadoService(NpcVivosPlugin plugin) {
        this.plugin = plugin;
    }

    private MundoVivoService cfg() {
        return plugin.mundo();
    }

    public boolean ativo() {
        return cfg().sim("precos.ativo", true);
    }

    // ------------------------------------------------------------------ PROCURA E OFERTA

    /** Identidade do item no mercado: o material, ou o material + uma marca para itens com nome/dados. */
    static String chaveItem(ItemLoja it) {
        String m = it.material().name();
        return it.simples() ? m : m + "#" + Integer.toHexString(Arrays.hashCode(it.item().serializeAsBytes()));
    }

    private static String chave(NpcVivo npc, ItemLoja it, char tipo) {
        return "mercado|" + npc.id() + "|" + chaveItem(it) + "|" + tipo;
    }

    private double ler(String chave) {
        String v = plugin.repositorio().mundo(chave);
        try {
            return v == null ? 0 : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void somar(String chave, double quanto) {
        double novo = ler(chave) + quanto;
        plugin.repositorio().setMundo(chave, novo <= 0.0001 ? null : String.valueOf(Math.round(novo * 1000) / 1000.0));
    }

    public double procura(NpcVivo npc, ItemLoja it) {
        return ler(chave(npc, it, 'd'));
    }

    public double oferta(NpcVivo npc, ItemLoja it) {
        return ler(chave(npc, it, 'o'));
    }

    /** O jogador comprou lotes deste item: a procura sobe. */
    public void registrarCompra(NpcVivo npc, ItemLoja it, double lotes) {
        somar(chave(npc, it, 'd'), lotes);
    }

    /** O jogador vendeu lotes deste item: a oferta sobe. */
    public void registrarVenda(NpcVivo npc, ItemLoja it, double lotes) {
        somar(chave(npc, it, 'o'), lotes);
    }

    /** Fator da procura/oferta (1 = preço normal). */
    public double fatorMercado(NpcVivo npc, ItemLoja it) {
        if (!ativo()) {
            return 1;
        }
        double d = procura(npc, it);
        double o = oferta(npc, it);
        double k = Math.max(1, cfg().num("precos.suavizar", 24));
        double f = 1 + (d - o) / (o + d + k);
        f = Math.max(cfg().num("precos.minimo", 0.6), Math.min(cfg().num("precos.maximo", 1.6), f));
        double degrau = cfg().num("precos.degrau", 0.05);
        if (degrau > 0) {
            f = Math.round(f / degrau) * degrau;
        }
        return Math.round(f * 1000) / 1000.0;
    }

    /** Esquecer um pouco a cada X minutos (meia-vida configurável): o preço volta ao normal sozinho. */
    public void decair(int minutos) {
        double meiaVida = Math.max(1, cfg().num("precos.meia-vida-minutos", 120));
        double fator = Math.pow(0.5, minutos / meiaVida);
        for (Map.Entry<String, String> e : plugin.repositorio().mundoComPrefixo("mercado|").entrySet()) {
            try {
                double novo = Double.parseDouble(e.getValue()) * fator;
                plugin.repositorio().setMundo(e.getKey(), novo < 0.05 ? null : String.valueOf(Math.round(novo * 1000) / 1000.0));
            } catch (NumberFormatException ex) {
                plugin.repositorio().setMundo(e.getKey(), null);
            }
        }
    }

    // ------------------------------------------------------------------ MODIFICADORES

    public static boolean itemDeCombate(ItemLoja it) {
        return ItemAprimoravel.tipo(it.item()) != null || COMBATE.contains(it.material());
    }

    /** O item vem de uma facção com quem a do NPC está em guerra? Então não chega mais (sem reposição). */
    public boolean rotaCortada(NpcVivo npc, ItemLoja it) {
        return it.importadoDe() != null && npc.faccao() != null
                && plugin.faccoes().relacao(npc.faccao(), it.importadoDe()) == Relacao.GUERRA;
    }

    private boolean emGuerra(NpcVivo npc) {
        return npc.faccao() != null && plugin.guerras().emGuerra(npc.faccao());
    }

    /** A facção do NPC venceu a última guerra e ainda está no período de bônus? */
    public boolean temBonusVitoria(String faccao) {
        String v = faccao == null ? null : plugin.repositorio().mundo("vitoria|" + faccao);
        try {
            return v != null && Long.parseLong(v) > System.currentTimeMillis();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void darBonusVitoria(String faccao, int dias) {
        plugin.repositorio().setMundo("vitoria|" + faccao, String.valueOf(System.currentTimeMillis() + dias * 86_400_000L));
    }

    /** Desconto de promoção (0 a 0,9) que vale para a loja deste NPC agora. */
    public double promocao(NpcVivo npc) {
        long agora = System.currentTimeMillis();
        promocoes.values().removeIf(v -> v[1] < agora);
        double[] geral = promocoes.get("*");
        double[] daFaccao = npc.faccao() == null ? null : promocoes.get(npc.faccao());
        return Math.max(geral == null ? 0 : geral[0], daFaccao == null ? 0 : daFaccao[0]);
    }

    /** Começa uma promoção (alvo = id de facção ou "*" para todas as lojas). */
    public void iniciarPromocao(String alvo, double desconto, int minutos) {
        promocoes.put(alvo, new double[]{Math.max(0, Math.min(0.9, desconto)), System.currentTimeMillis() + minutos * 60_000L});
    }

    /** Multiplicador final do que o jogador PAGA (sem reputação, que fica no FaccaoService). */
    public double fatorCompra(NpcVivo npc, ItemLoja it) {
        double f = fatorMercado(npc, it);
        if (emGuerra(npc) && itemDeCombate(it)) {
            f *= 1 + cfg().num("precos.guerra-itens-de-combate", 0.25);
        }
        if (rotaCortada(npc, it)) {
            f *= 1 + cfg().num("precos.rota-cortada", 0.5);
        }
        if (temBonusVitoria(npc.faccao())) {
            f *= 1 - cfg().num("precos.vitoria-desconto", 0.10);
        }
        f *= 1 - promocao(npc);
        return f;
    }

    /** Multiplicador final do que o NPC PAGA ao jogador. */
    public double fatorVenda(NpcVivo npc, ItemLoja it) {
        double f = fatorMercado(npc, it);
        if (emGuerra(npc) && itemDeCombate(it)) {
            f *= 1 + cfg().num("precos.guerra-itens-de-combate", 0.25);
        }
        if (temBonusVitoria(npc.faccao())) {
            f *= 1 + cfg().num("precos.vitoria-desconto", 0.10);
        }
        return f;
    }

    /** Linhas para a lore do item na loja explicando por que o preço mudou. */
    public List<String> motivos(NpcVivo npc, ItemLoja it, boolean compra) {
        List<String> l = new ArrayList<>();
        double f = fatorMercado(npc, it);
        if (f > 1.001) {
            l.add((compra ? "<red>" : "<green>") + "▲ Muita procura: +" + Texto.dinheiro(Math.round((f - 1) * 100)) + "%");
        } else if (f < 0.999) {
            l.add((compra ? "<green>" : "<red>") + "▼ Muita oferta: -" + Texto.dinheiro(Math.round((1 - f) * 100)) + "%");
        }
        if (emGuerra(npc) && itemDeCombate(it)) {
            l.add("<red>⚔ Guerra: itens de combate +" + Texto.dinheiro(Math.round(cfg().num("precos.guerra-itens-de-combate", 0.25) * 100)) + "%");
        }
        if (compra && rotaCortada(npc, it)) {
            Faccao f2 = plugin.faccoes().obter(it.importadoDe());
            l.add("<red>⚔ Rota de " + Texto.semCor(f2 == null ? it.importadoDe() : f2.nome()) + " cortada pela guerra: sem reposição, +"
                    + Texto.dinheiro(Math.round(cfg().num("precos.rota-cortada", 0.5) * 100)) + "%");
        }
        if (temBonusVitoria(npc.faccao())) {
            l.add("<gold>✦ A facção venceu a última guerra: " + (compra ? "-" : "+")
                    + Texto.dinheiro(Math.round(cfg().num("precos.vitoria-desconto", 0.10) * 100)) + "%");
        }
        if (compra && promocao(npc) > 0) {
            l.add("<light_purple>✦ Promoção: -" + Texto.dinheiro(Math.round(promocao(npc) * 100)) + "%");
        }
        return l;
    }

    // ------------------------------------------------------------------ COMÉRCIO ENTRE NPCs

    /** Facções que podem trocar estoque: a mesma ou aliadas. */
    private boolean parceiras(NpcVivo a, NpcVivo b) {
        if (a.faccao() == null || b.faccao() == null) {
            return false;
        }
        return a.faccao().equals(b.faccao()) || plugin.faccoes().relacao(a.faccao(), b.faccao()) == Relacao.ALIADA;
    }

    /**
     * Comerciantes de facções aliadas (ou da mesma) trocam estoque: quem está quase sem um item recebe de quem tem
     * de sobra. Devolve quantas trocas aconteceram.
     */
    public int trocarEntreNpcs() {
        if (!cfg().sim("comercio-entre-npcs.ativo", true)) {
            return 0;
        }
        double falta = cfg().num("comercio-entre-npcs.falta", 0.3);
        double sobra = cfg().num("comercio-entre-npcs.sobra", 0.6);
        int trocas = 0;
        List<NpcVivo> lojas = new ArrayList<>();
        for (NpcVivo n : plugin.npcs().todos()) {
            if (!n.temporario() && !n.compra().isEmpty()) {
                lojas.add(n);
            }
        }
        for (NpcVivo precisa : lojas) {
            for (ItemLoja it : precisa.compra()) {
                if (it.estoqueMax() <= 0) {
                    continue;
                }
                int atual = plugin.comercio().estoque(precisa, it);
                if (atual >= it.estoqueMax() * falta) {
                    continue;
                }
                for (NpcVivo tem : lojas) {
                    if (tem == precisa || !parceiras(precisa, tem)) {
                        continue;
                    }
                    ItemLoja igual = mesmoItem(tem, it);
                    if (igual == null || igual.estoqueMax() <= 0) {
                        continue;
                    }
                    int dele = plugin.comercio().estoque(tem, igual);
                    if (dele <= igual.estoqueMax() * sobra) {
                        continue;
                    }
                    // Passa lotes até o que precisa chegar na metade, sem deixar o outro abaixo da metade
                    int passa = Math.min(dele - (int) Math.ceil(igual.estoqueMax() * 0.5),
                            (int) Math.ceil(it.estoqueMax() * 0.5) - atual);
                    if (passa <= 0) {
                        continue;
                    }
                    plugin.repositorio().setEstoque(tem.id(), igual.chave(), dele - passa);
                    plugin.repositorio().setEstoque(precisa.id(), it.chave(), atual + passa);
                    atual += passa;
                    trocas++;
                    plugin.getLogger().info("Comércio entre NPCs: " + tem.id() + " mandou " + passa + " lote(s) de "
                            + it.material().name() + " para " + precisa.id() + ".");
                    if (!precisa.faccao().equals(tem.faccao())) {
                        plugin.diplomacia().alterar(precisa.faccao(), tem.faccao(),
                                cfg().num("comercio-entre-npcs.pontos-relacao-por-troca", 5), "troca entre comerciantes");
                    }
                    if (atual >= it.estoqueMax() * falta) {
                        break;
                    }
                }
            }
        }
        if (trocas > 0) {
            plugin.repositorio().salvarAssincrono();
        }
        return trocas;
    }

    private static ItemLoja mesmoItem(NpcVivo npc, ItemLoja modelo) {
        String chave = chaveItem(modelo);
        for (ItemLoja i : npc.compra()) {
            if (chaveItem(i).equals(chave) && i.quantidade() == modelo.quantidade()) {
                return i;
            }
        }
        return null;
    }

    /** Para o /npcv mercado ver <npc>. */
    public List<String> resumo(NpcVivo npc) {
        List<String> l = new ArrayList<>();
        for (ItemLoja it : npc.compra()) {
            l.add(linhaResumo("vende", npc, it, fatorCompra(npc, it)));
        }
        for (ItemLoja it : npc.venda()) {
            l.add(linhaResumo("compra", npc, it, fatorVenda(npc, it)));
        }
        return l;
    }

    private String linhaResumo(String tipo, NpcVivo npc, ItemLoja it, double fator) {
        return "<gray> " + tipo + " <white>" + it.material().name().toLowerCase() + " <dark_gray>| <gray>procura <white>"
                + Texto.dinheiro(Math.round(procura(npc, it) * 10) / 10.0) + " <gray>oferta <white>"
                + Texto.dinheiro(Math.round(oferta(npc, it) * 10) / 10.0) + " <dark_gray>| <yellow>x"
                + Texto.dinheiro(Math.round(fator * 100) / 100.0) + (it.importadoDe() != null ? " <dark_gray>(importado de "
                + it.importadoDe() + ")" : "");
    }

    /** Avisa os jogadores online (anúncio de promoção). */
    void anunciar(String chave, String... pares) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.mensagens().enviar(p, chave, pares);
        }
    }
}
