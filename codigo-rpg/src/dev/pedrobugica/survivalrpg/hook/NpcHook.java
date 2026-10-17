package dev.pedrobugica.survivalrpg.hook;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.core.Rotina;
import dev.pedrobugica.survivalrpg.SurvivalRpgPlugin;
import dev.pedrobugica.survivalrpg.mundo.Lugar;
import dev.pedrobugica.survivalrpg.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Ponte com o NpcVivos: arruma os NPCs da praça no chão novo da capital, cria os guardas dos portões, o
 * guardião das masmorras, o instrutor, o taverneiro e um morador em cada vila/taverna que aparece no mapa.
 * Se o NpcVivos não estiver instalado, nada disso acontece (e o resto do RPG funciona igual).
 */
public final class NpcHook {

    private static final List<String> DA_CAPITAL = List.of("sentinela_sul", "sentinela_leste", "sentinela_oeste",
            "guardiao_masmorras", "instrutor_rurik", "taverneiro_bento");

    private final SurvivalRpgPlugin plugin;

    public NpcHook(SurvivalRpgPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ativo() {
        Plugin p = Bukkit.getPluginManager().getPlugin("NpcVivos");
        return p != null && p.isEnabled();
    }

    private void tentar(String oque, Runnable r) {
        if (!ativo()) {
            return;
        }
        try {
            r.run();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "NpcVivos: não deu para " + oque, e);
        }
    }

    public void arrumarCapital(World w, int cx, int h, int cz) {
        tentar("arrumar os NPCs da capital", () -> Ponte.arrumarCapital(w, cx, h, cz));
    }

    public void moverBorin(Location l) {
        tentar("mover o Borin", () -> Ponte.mover("ferreiro_borin", l, 0));
    }

    public void taverneiro(Location l) {
        tentar("criar o taverneiro", () -> Ponte.criar("taverneiro_bento", Papel.GUIA, "<gold><bold>Bento, o Taverneiro", "kai", l, 180,
                Material.HONEY_BOTTLE, null, Rotina.Tipo.PASSEAR, 3,
                List.of("Bem-vindo à Taverna do Javali Dourado, {jogador}! Senta, descansa.",
                        "Dizem que nas Catacumbas Esquecidas tem um rei que não morre..."),
                List.of("Hidromel fresquinho! Quem vai?", "Ouvi dizer que tem uma torre de mago lá pro leste...",
                        "Aventureiros que voltam das masmorras sempre pagam a rodada!")));
    }

    public void removerDaCapital() {
        tentar("remover os NPCs da capital", () -> {
            for (String id : DA_CAPITAL) {
                Ponte.remover(id);
            }
        });
    }

    /** Um morador para vilas e tavernas novas. */
    public void povoar(Lugar l) {
        if (l.tipo() != Lugar.Tipo.VILA && l.tipo() != Lugar.Tipo.TAVERNA) {
            return;
        }
        tentar("criar o morador de " + l.id(), () -> {
            World w = Bukkit.getWorld(l.mundo());
            if (w == null) {
                return;
            }
            Block topo = w.getHighestBlockAt(l.x(), l.z());
            Location loc = new Location(w, l.x() + 0.5, topo.getY() + 1, l.z() + 0.5);
            String nome = Texto.semCor(l.nome());
            if (l.tipo() == Lugar.Tipo.VILA) {
                Ponte.criar("anciao_" + l.id(), Papel.GUIA, "<green><bold>Ancião de " + nome, "noor", loc, 0, null, null,
                        Rotina.Tipo.PASSEAR, 6,
                        List.of("Seja bem-vindo a " + nome + ", {jogador}.", "Viajante! Aqui você está seguro, os monstros não entram na vila."),
                        List.of("Os mais velhos contam histórias da capital, Vila Aurora...", "Cuidado na estrada à noite!",
                                "Se tiver mapa, use o /lugares para não se perder."));
            } else {
                Ponte.criar("taverneiro_" + l.id(), Papel.GUIA, "<yellow><bold>Taverneiro de " + nome, "kai", loc, 0,
                        Material.HONEY_BOTTLE, null, Rotina.Tipo.PASSEAR, 4,
                        List.of("Bem-vindo à " + nome + ", {jogador}! A casa é sua.", "Mais um aventureiro! Senta aí."),
                        List.of("Dizem que tem um castelo cheio de saqueadores lá longe...", "Bebida boa e cama quente, só aqui!",
                                "Daqui dá pra viajar rápido pra capital: /lugares"));
            }
        });
    }

    /** Tudo que encosta nas classes do NpcVivos fica aqui (só carrega se o plugin existir). */
    private static final class Ponte {

        private Ponte() {
        }

        static NpcVivosPlugin np() {
            return (NpcVivosPlugin) Bukkit.getPluginManager().getPlugin("NpcVivos");
        }

        static void mover(String id, Location l, float yaw) {
            NpcVivo n = np().npcs().obter(id);
            if (n == null) {
                return;
            }
            n.setLocal(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), yaw);
            np().npcs().salvar(n);
            np().npcs().spawnar(n);
        }

        static void remover(String id) {
            NpcVivo n = np().npcs().obter(id);
            if (n != null) {
                np().npcs().remover(n);
            }
        }

        static void criar(String id, Papel papel, String nome, String skin, Location l, float yaw, Material mao, String faccao,
                          Rotina.Tipo tipo, double raio, List<String> saudacoes, List<String> falas) {
            NpcVivo n = np().npcs().obter(id);
            if (n == null) {
                n = np().npcs().criar(id, papel, nome, l);
            }
            n.setNome(nome);
            n.setSkin(skin);
            n.setLocal(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), yaw);
            if (mao != null) {
                n.setItemNaMao(mao);
            }
            if (faccao != null) {
                n.setFaccao(faccao);
            }
            n.saudacao().clear();
            n.saudacao().addAll(saudacoes);
            n.retorno().clear();
            n.retorno().add("De volta, {jogador}? Bom te ver!");
            n.setRotina(new Rotina(tipo, raio, new ArrayList<>(), new ArrayList<>(falas), tipo != Rotina.Tipo.PARADO));
            np().npcs().salvar(n);
            np().npcs().spawnar(n);
        }

        static void arrumarCapital(World w, int cx, int h, int cz) {
            // NPCs que já estavam perto do spawn: mesmo x/z, no chão novo
            for (NpcVivo n : new ArrayList<>(np().npcs().todos())) {
                if (n.temporario() || !w.getName().equals(n.mundo())) {
                    continue;
                }
                if (Math.hypot(n.x() - cx, n.z() - cz) <= 12 && Math.abs(n.y() - (h + 1)) > 0.01) {
                    n.setLocal(n.mundo(), n.x(), h + 1, n.z(), n.yaw());
                    np().npcs().salvar(n);
                    np().npcs().spawnar(n);
                }
            }
            List<String> falasGuarda = List.of("Tudo calmo por aqui.", "Nenhum monstro passa por este portão!",
                    "Se for pra estrada, vá preparado.", "Às ordens do Conselho de Aurora.");
            criar("sentinela_sul", Papel.GUARDA, "<aqua><bold>Sentinela do Portão Sul", "steve", new Location(w, cx + 3.5, h + 1, cz + 37.5),
                    0, Material.IRON_SWORD, "vila_aurora", Rotina.Tipo.PARADO, 2,
                    List.of("Alto lá, {jogador}! Ah, é você. Pode passar."), falasGuarda);
            criar("sentinela_leste", Papel.GUARDA, "<aqua><bold>Sentinela do Portão Leste", "steve", new Location(w, cx + 37.5, h + 1, cz - 2.5),
                    -90, Material.IRON_SWORD, "vila_aurora", Rotina.Tipo.PARADO, 2,
                    List.of("Bom dia, {jogador}. A estrada leste leva às terras selvagens."), falasGuarda);
            criar("sentinela_oeste", Papel.GUARDA, "<aqua><bold>Sentinela do Portão Oeste", "steve", new Location(w, cx - 36.5, h + 1, cz + 3.5),
                    90, Material.IRON_SWORD, "vila_aurora", Rotina.Tipo.PARADO, 2,
                    List.of("Saudações, {jogador}. Volte antes de anoitecer."), falasGuarda);
            criar("guardiao_masmorras", Papel.GUIA, "<dark_purple><bold>Morvan, o Guardião", "efe", new Location(w, cx - 20.5, h + 1, cz + 19.5),
                    150, Material.SOUL_LANTERN, null, Rotina.Tipo.PARADO, 2,
                    List.of("Procura glória, {jogador}? Junte seu grupo e clique na pedra-guia.",
                            "As masmorras não perdoam. Mas lá dentro você não perde seus itens se cair."),
                    List.of("Três masmorras, três chefes... quem será o próximo a cair?", "O portal sussurra à noite...",
                            "Leve um clérigo. Confie em mim."));
            criar("instrutor_rurik", Papel.GUIA, "<red><bold>Rurik, o Instrutor", "alex", new Location(w, cx - 22.5, h + 1, cz - 19.5),
                    180, Material.WOODEN_SWORD, null, Rotina.Tipo.PASSEAR, 3,
                    List.of("Novato, {jogador}? Escolha sua classe com /classe e treine aqui!",
                            "Com a arma da classe na mão, aperte F e depois 1, 2 ou 3 para usar habilidades."),
                    List.of("Guerreiro aguenta, mago explode, clérigo cura... e você?", "Mais força nesse golpe!",
                            "Distribua seus pontos em /atributos a cada nível!"));
        }
    }
}
