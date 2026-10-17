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
import java.util.Locale;
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

    /** Um reino do NpcVivos (reinos.yml), do jeito que o RPG precisa para construir castelos e o lobby. */
    public record InfoReino(String id, String nome, String curto, String cor, String bandeira, String lema, String rei) {
    }

    /** Os reinos do NpcVivos (vazio se o plugin não estiver ligado). */
    public List<InfoReino> reinos() {
        if (!ativo()) {
            return List.of();
        }
        try {
            return Ponte.reinos();
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    public void registrarCastelo(String reino, String nome, Location centro, int raio, Location trono) {
        tentar("registrar o castelo de " + reino, () -> Ponte.registrarCastelo(reino, nome, centro, raio, trono));
    }

    /**
     * Avisa o NpcVivos de todas as vilas e castelos do mapa (as vilas ficam com o reino do castelo mais perto)
     * e ensina os moradores a contar fofocas sobre os lugares em volta.
     */
    public void sincronizarReinos() {
        tentar("sincronizar os reinos", () -> {
            for (Lugar l : plugin.lugares().todos()) {
                Location c = l.local();
                if (c == null) {
                    continue;
                }
                if (l.tipo() == Lugar.Tipo.REINO && l.id().startsWith("reino_")) {
                    Ponte.registrarCastelo(l.id().substring(6), l.nome(), c.clone().add(0, 0, 6), 25, c.clone().add(0, 1, -19));
                }
            }
            for (Lugar l : plugin.lugares().todos()) {
                if (l.tipo() == Lugar.Tipo.VILA && l.local() != null) {
                    Ponte.registrarVila(l.id(), l.nome(), l.local(), Math.max(32, l.raioZona()));
                }
            }
            Ponte.localizador(plugin);
        });
    }

    /** O arauto do lobby do RPG: dá dicas para quem acabou de chegar. */
    public void guiaDoLobby(Location l) {
        tentar("criar o guia do lobby", () -> Ponte.criar("arauto_lobby_rpg", Papel.GUIA, "<gold><bold>Aldo, o Arauto", "sunny", l, l.getYaw(),
                Material.GOAT_HORN, null, Rotina.Tipo.PASSEAR, 3,
                List.of("Bem-vindo à Cidadela dos Heróis, {jogador}! Escolha sua classe nas estátuas a oeste.",
                        "Os portais a leste levam à capital, às terras selvagens e às masmorras.",
                        "Três reinos disputam estas terras. Jure lealdade a um deles com /reino!"),
                List.of("Os reinos estão em guerra... escolha bem o seu lado!", "Leia o mural ao norte: tem todos os comandos.",
                        "Os moradores das vilas adoram presentes. Flores, bolo, esmeraldas...",
                        "Dizem que quem tirar a espada da pedra será rei... hahaha!", "Novato? A primeira classe é de graça!")));
    }

    /** Resumo dos reinos para o painel do lobby (castelos, vilas e guerras). */
    public List<String> linhasReinos() {
        if (!ativo()) {
            return List.of();
        }
        try {
            return Ponte.linhasReinos();
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
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

    /** Um morador para vilas e tavernas novas (e a vila entra no reino mais perto). */
    public void povoar(Lugar l) {
        if (l.tipo() == Lugar.Tipo.VILA && l.local() != null) {
            tentar("registrar a vila " + l.id(), () -> Ponte.registrarVila(l.id(), l.nome(), l.local(), Math.max(32, l.raioZona())));
        }
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

        static List<InfoReino> reinos() {
            List<InfoReino> l = new ArrayList<>();
            for (dev.pedrobugica.npcvivos.reino.Reino r : np().reinos().todos()) {
                l.add(new InfoReino(r.id(), r.nome(), r.curto(), r.cor(), r.bandeira().name(), r.lema(), r.rei()));
            }
            return l;
        }

        static List<String> linhasReinos() {
            List<String> l = new ArrayList<>();
            var rs = np().reinos();
            for (dev.pedrobugica.npcvivos.reino.Reino r : rs.todos()) {
                long castelos = rs.castelosDe(r.id()).size();
                long vilas = rs.territorios().stream().filter(t -> r.id().equals(t.dono())
                        && t.tipo() == dev.pedrobugica.npcvivos.reino.Territorio.Tipo.VILA).count();
                List<String> inimigos = new ArrayList<>();
                for (var g : np().guerras().ativas()) {
                    if (g.envolve(r.id())) {
                        var o = rs.obter(g.inimiga(r.id()));
                        inimigos.add(o == null ? g.inimiga(r.id()) : o.curto());
                    }
                }
                l.add(r.cor() + r.curto() + " <dark_gray>| <gray>🏰 " + castelos + " <gray>🏘 " + vilas + " "
                        + (inimigos.isEmpty() ? "<green>em paz" : "<red>⚔ " + String.join(", ", inimigos)));
            }
            for (String x : rs.campanhas().resumo()) {
                l.add(x);
            }
            return l;
        }

        static void registrarCastelo(String reino, String nome, Location centro, int raio, Location trono) {
            np().reinos().registrarCastelo(reino, nome, centro, raio, trono);
        }

        static void registrarVila(String id, String nome, Location centro, int raio) {
            np().reinos().registrarVila(id, nome, centro, raio);
        }

        /** Fofoca dos moradores: lugares que o jogador ainda não descobriu, com distância e direção. */
        static void localizador(SurvivalRpgPlugin rpg) {
            np().moradores().setLocalizador((p, onde) -> {
                List<String> r = new ArrayList<>();
                var perfil = rpg.perfis().perfil(p);
                List<Lugar> candidatos = new ArrayList<>();
                for (Lugar l : rpg.lugares().todos()) {
                    if (l.mundo().equals(onde.getWorld().getName()) && !rpg.lugares().descoberto(perfil, l)
                            && l.tipo() != Lugar.Tipo.CAPITAL && l.distancia(onde) < 3000 && l.distancia(onde) > 60) {
                        candidatos.add(l);
                    }
                }
                candidatos.sort(java.util.Comparator.comparingDouble(l -> l.distancia(onde)));
                for (int i = 0; i < Math.min(2, candidatos.size()); i++) {
                    Lugar l = candidatos.get(i);
                    double dx = l.x() - onde.getX();
                    double dz = l.z() - onde.getZ();
                    String tipo = l.tipo().nome().toLowerCase(Locale.ROOT);
                    r.add("Ouvi falar de " + Texto.semCor(l.nome()) + " (" + tipo + "), a uns " + Math.round(l.distancia(onde) / 50) * 50
                            + " blocos ao " + direcao(dx, dz) + ".");
                }
                return r;
            });
        }

        static String direcao(double dx, double dz) {
            double ang = Math.toDegrees(Math.atan2(dx, -dz));
            if (ang < 0) {
                ang += 360;
            }
            String[] nomes = {"norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste"};
            return nomes[(int) Math.round(ang / 45) % 8];
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
