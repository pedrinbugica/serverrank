package dev.pedrobugica.npcvivos.reino;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.core.NpcManager;
import dev.pedrobugica.npcvivos.core.NpcVivo;
import dev.pedrobugica.npcvivos.core.Papel;
import dev.pedrobugica.npcvivos.core.Rotina;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.faccao.Relacao;
import dev.pedrobugica.npcvivos.mundo.Guerra;
import dev.pedrobugica.npcvivos.povo.Corpos;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Os reinos (estilo Villager Recruits / Recruits: Living Villages, sem mod): cada reino tem castelo, rei, soldados e
 * vilas. Os soldados são esqueletos escondidos (a IA de luta do jogo) com corpo de gente, que defendem o castelo,
 * caçam monstros perto, prendem quem ataca o reino e lutam contra os soldados dos reinos em guerra.
 * A diplomacia é automática: a tensão sobe e os reis declaram guerra; os exércitos ficam nas {@link Campanhas}.
 */
public final class ReinoService implements Corpos.Molde, Listener {

    private final NpcVivosPlugin plugin;
    private final Corpos corpos;
    private final File arquivo;
    private final File arquivoDados;
    private YamlConfiguration cfg;
    private final Map<String, Reino> reinos = new LinkedHashMap<>();
    private final Map<String, Territorio> territorios = new LinkedHashMap<>();
    private final Campanhas campanhas;

    final NamespacedKey kReino;
    final NamespacedKey kTipo;
    final NamespacedKey kTerritorio;
    final NamespacedKey kExercito;
    final NamespacedKey kPosto;

    private BukkitTask tarefaIa;
    private BukkitTask tarefaGuarnicao;
    private BukkitTask tarefaDiplomacia;

    public ReinoService(NpcVivosPlugin plugin, Corpos corpos) {
        this.plugin = plugin;
        this.corpos = corpos;
        this.arquivo = new File(plugin.getDataFolder(), "reinos.yml");
        this.arquivoDados = new File(plugin.getDataFolder(), "reinos-dados.yml");
        this.kReino = new NamespacedKey(plugin, "soldado-reino");
        this.kTipo = new NamespacedKey(plugin, "soldado-tipo");
        this.kTerritorio = new NamespacedKey(plugin, "soldado-territorio");
        this.kExercito = new NamespacedKey(plugin, "soldado-exercito");
        this.kPosto = new NamespacedKey(plugin, "soldado-posto");
        this.campanhas = new Campanhas(plugin, this);
    }

    NpcVivosPlugin plugin() {
        return plugin;
    }

    Corpos corpos() {
        return corpos;
    }

    public Campanhas campanhas() {
        return campanhas;
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public boolean ativo() {
        return cfg != null && cfg.getBoolean("ativo", true);
    }

    // ------------------------------------------------------------------ CARREGAR

    public void carregar() {
        if (!arquivo.exists()) {
            plugin.saveResource("reinos.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(arquivo);
        reinos.clear();
        ConfigurationSection s = cfg.getConfigurationSection("reinos");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                String rid = id.toLowerCase(Locale.ROOT);
                Color couro;
                try {
                    couro = Color.fromRGB(Integer.parseInt(c.getString("cor-couro", "#888888").replace("#", ""), 16));
                } catch (NumberFormatException e) {
                    couro = Color.GRAY;
                }
                DyeColor bandeira;
                try {
                    bandeira = DyeColor.valueOf(c.getString("bandeira", "WHITE").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    bandeira = DyeColor.WHITE;
                }
                reinos.put(rid, new Reino(rid, c.getString("nome", rid), c.getString("curto", rid), c.getString("cor", "<white>"),
                        couro, bandeira, c.getString("lema", ""), c.getString("rei", "O Rei"), c.getString("rei-skin", "steve"),
                        c.getStringList("skins-soldados").isEmpty() ? List.of("steve") : c.getStringList("skins-soldados"),
                        c.getStringList("descricao")));
            }
        }
        criarFaccoes();
        carregarDados();
        plugin.getLogger().info(reinos.size() + " reino(s), " + castelos().size() + " castelo(s) e " + vilas().size() + " vila(s).");
    }

    /** Cada reino vira uma facção (se ainda não existir o arquivo): rivais entre si. */
    private void criarFaccoes() {
        File pasta = new File(plugin.getDataFolder(), "faccoes");
        boolean criou = false;
        for (Reino r : reinos.values()) {
            File f = new File(pasta, r.id() + ".yml");
            if (f.exists()) {
                continue;
            }
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Facção do " + Texto.semCor(r.nome()) + " (criada pelo reinos.yml).",
                    "Depois de editar: /npcv reload"));
            y.set("id", r.id());
            y.set("nome", r.nome());
            y.set("tag", "<dark_gray>[" + r.cor() + r.curto() + "<dark_gray>] ");
            y.set("lider", "rei_" + r.id());
            y.set("descricao", r.descricao());
            Map<String, String> rel = new LinkedHashMap<>();
            for (Reino o : reinos.values()) {
                if (!o.id().equals(r.id())) {
                    rel.put(o.id(), "RIVAL");
                }
            }
            y.set("relacoes", rel);
            y.set("beneficios_membro.desconto", 0.10);
            try {
                pasta.mkdirs();
                y.save(f);
                criou = true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível criar faccoes/" + r.id() + ".yml", e);
            }
        }
        if (criou) {
            plugin.faccoes().carregar();
        }
        criarDialogosDosReis();
    }

    private void criarDialogosDosReis() {
        File pasta = new File(plugin.getDataFolder(), "dialogos");
        boolean criou = false;
        for (Reino r : reinos.values()) {
            File f = new File(pasta, "rei_" + r.id() + ".yml");
            if (f.exists()) {
                continue;
            }
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Conversa do rei/rainha do " + Texto.semCor(r.nome()) + ". Veja dialogos/borin.yml para entender."));
            y.set("inicio", "entrada");
            y.set("nos.entrada.fala", List.of("Saudações, {jogador}. Eu sou " + r.rei() + ", soberano de " + r.curto() + ". " + r.lema(),
                    "Ajoelhe-se diante do trono de " + r.curto() + ", {jogador}."));
            y.set("nos.entrada.fala_retorno", List.of("Bem-vindo de volta, {jogador}.", "{jogador}! O reino precisa de você."));
            List<Map<String, Object>> op = new ArrayList<>();
            op.add(Map.of("texto", "Quero jurar lealdade a " + r.curto(), "acao", "entrar_faccao"));
            op.add(Map.of("texto", "Como anda a guerra?", "comandos", List.of("reino noticias {jogador}"), "ir", "entrada"));
            op.add(Map.of("texto", "Fale-me do seu reino", "ir", "historia"));
            y.set("nos.entrada.opcoes", op);
            List<String> historia = new ArrayList<>(r.descricao());
            historia.add(r.lema());
            y.set("nos.historia.fala", List.of(String.join(" ", historia)));
            y.set("nos.historia.opcoes", List.of(Map.of("texto", "Voltar", "ir", "entrada")));
            try {
                pasta.mkdirs();
                y.save(f);
                criou = true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível criar dialogos/rei_" + r.id() + ".yml", e);
            }
        }
        if (criou) {
            plugin.dialogo().carregar();
        }
    }

    private void carregarDados() {
        territorios.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivoDados);
        ConfigurationSection s = y.getConfigurationSection("territorios");
        if (s == null) {
            return;
        }
        for (String chave : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(chave);
            if (c == null) {
                continue;
            }
            // versão 1.0.0 gravava "vila_vila_1"
            String id = chave.startsWith("vila_vila_") ? chave.substring(5) : chave;
            Territorio.Tipo tipo;
            try {
                tipo = Territorio.Tipo.valueOf(c.getString("tipo", "VILA"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Territorio t = new Territorio(id, tipo, c.getString("nome", id), c.getString("mundo", "sobrevivencia"),
                    c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("raio", 40));
            t.setDono(c.getString("dono"));
            t.setReinoOriginal(c.getString("reino-original"));
            t.setTrono(c.getDouble("trono.x", t.x() + 0.5), c.getDouble("trono.y", t.y()), c.getDouble("trono.z", t.z() + 0.5));
            t.setPintarDe(c.getString("pintar-de"));
            territorios.put(id, t);
        }
    }

    public void gravar() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Castelos e vilas dos reinos (quem é dono de quê). Mexa pelo /reinoadmin."));
        for (Territorio t : territorios.values()) {
            String b = "territorios." + t.id() + ".";
            y.set(b + "tipo", t.tipo().name());
            y.set(b + "nome", t.nome());
            y.set(b + "mundo", t.mundo());
            y.set(b + "x", t.x());
            y.set(b + "y", t.y());
            y.set(b + "z", t.z());
            y.set(b + "raio", t.raio());
            y.set(b + "dono", t.dono());
            y.set(b + "reino-original", t.reinoOriginal());
            if (t.tipo() == Territorio.Tipo.CASTELO) {
                y.set(b + "trono.x", t.tronoX());
                y.set(b + "trono.y", t.tronoY());
                y.set(b + "trono.z", t.tronoZ());
            }
            y.set(b + "pintar-de", t.pintarDe());
        }
        try {
            y.save(arquivoDados);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar reinos-dados.yml", e);
        }
    }

    public void iniciar() {
        parar();
        corpos.registrar(this);
        if (!ativo()) {
            return;
        }
        tarefaIa = Bukkit.getScheduler().runTaskTimer(plugin, this::pensar, 60L, 20L);
        tarefaGuarnicao = Bukkit.getScheduler().runTaskTimer(plugin, this::guarnicoes, 200L, 600L);
        long ciclo = Math.max(1, cfg.getLong("guerra.diplomacia-a-cada-minutos", 20)) * 1200L;
        tarefaDiplomacia = Bukkit.getScheduler().runTaskTimer(plugin, this::diplomacia, ciclo, ciclo);
        campanhas.iniciar();
        for (Territorio t : castelos()) {
            garantirRei(t);
        }
    }

    public void parar() {
        for (BukkitTask t : new BukkitTask[]{tarefaIa, tarefaGuarnicao, tarefaDiplomacia}) {
            if (t != null) {
                t.cancel();
            }
        }
        tarefaIa = null;
        tarefaGuarnicao = null;
        tarefaDiplomacia = null;
        campanhas.parar();
    }

    // ------------------------------------------------------------------ CONSULTAS

    public Collection<Reino> todos() {
        return reinos.values();
    }

    public Reino obter(String id) {
        return id == null ? null : reinos.get(id.toLowerCase(Locale.ROOT));
    }

    public Faccao faccao(Reino r) {
        return r == null ? null : plugin.faccoes().obter(r.id());
    }

    public List<Territorio> castelos() {
        return territorios.values().stream().filter(t -> t.tipo() == Territorio.Tipo.CASTELO).toList();
    }

    public List<Territorio> vilas() {
        return territorios.values().stream().filter(t -> t.tipo() == Territorio.Tipo.VILA).toList();
    }

    public Territorio territorio(String id) {
        return id == null ? null : territorios.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Territorio> territorios() {
        return territorios.values();
    }

    public List<Territorio> castelosDe(String reino) {
        return castelos().stream().filter(t -> reino.equals(t.dono())).toList();
    }

    public Territorio territorioEm(Location l) {
        Territorio melhor = null;
        for (Territorio t : territorios.values()) {
            if (t.contem(l) && (melhor == null || t.raio() < melhor.raio())) {
                melhor = t;
            }
        }
        return melhor;
    }

    /** O reino dono do lugar (vila ou castelo em volta), ou null. */
    public Reino reinoEm(Location l) {
        if (l == null) {
            return null;
        }
        Territorio t = territorioEm(l);
        return t == null ? null : obter(t.dono());
    }

    /** Nome da vila/castelo em volta (com cor), ou null. */
    public String vilaEm(Location l) {
        Territorio t = l == null ? null : territorioEm(l);
        return t == null ? null : t.nome();
    }

    public boolean emGuerra(String a, String b) {
        return a != null && b != null && !a.equals(b) && plugin.guerras().entre(a, b) != null;
    }

    /** Frases de notícia para os moradores (exércitos em marcha, castelos tomados...). */
    public List<String> noticias(Location onde) {
        List<String> l = new ArrayList<>(campanhas.noticias(onde));
        for (Territorio c : castelos()) {
            if (c.dono() != null && c.reinoOriginal() != null && !c.dono().equals(c.reinoOriginal())) {
                Reino d = obter(c.dono());
                l.add(Texto.semCor(c.nome()) + " caiu e agora pertence a " + (d == null ? c.dono() : d.curto()) + "!");
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ REGISTRO (feito pelo SurvivalRPG)

    /** O SurvivalRPG construiu (ou achou) o castelo de um reino. */
    public Territorio registrarCastelo(String reino, String nome, Location centro, int raio, Location trono) {
        String rid = reino.toLowerCase(Locale.ROOT);
        String id = "castelo_" + rid;
        Territorio t = territorios.get(id);
        if (t == null) {
            t = new Territorio(id, Territorio.Tipo.CASTELO, nome, centro.getWorld().getName(), centro.getBlockX(), centro.getBlockY(),
                    centro.getBlockZ(), raio);
            t.setDono(rid);
            t.setReinoOriginal(rid);
            territorios.put(id, t);
        } else {
            t.setNome(nome);
        }
        if (trono != null) {
            t.setTrono(trono.getX(), trono.getY(), trono.getZ());
        }
        redistribuirVilas();
        gravar();
        garantirRei(t);
        return t;
    }

    /** Uma vila (ou taverna/cidade) do mapa: fica com o reino do castelo mais perto (se estiver no alcance). */
    public Territorio registrarVila(String id, String nome, Location centro, int raio) {
        String limpo = id.toLowerCase(Locale.ROOT);
        String vid = limpo.startsWith("vila_") ? limpo : "vila_" + limpo;
        Territorio t = territorios.get(vid);
        if (t == null) {
            t = new Territorio(vid, Territorio.Tipo.VILA, nome, centro.getWorld().getName(), centro.getBlockX(), centro.getBlockY(),
                    centro.getBlockZ(), Math.max(16, raio));
            territorios.put(vid, t);
            t.setDono(reinoMaisPerto(centro));
            gravar();
        } else if (!t.nome().equals(nome)) {
            t.setNome(nome);
            gravar();
        }
        return t;
    }

    public void removerTerritorio(String id) {
        if (territorios.remove(id) != null) {
            gravar();
        }
    }

    private String reinoMaisPerto(Location l) {
        double alcance = cfg.getDouble("guarnicao.raio-de-influencia", 1400);
        Territorio melhor = null;
        double d = alcance;
        for (Territorio c : castelos()) {
            double x = c.distancia(l);
            if (x < d) {
                d = x;
                melhor = c;
            }
        }
        return melhor == null ? null : melhor.dono();
    }

    /** Vilas sem dono ganham o reino do castelo mais perto (as conquistadas não mudam). */
    private void redistribuirVilas() {
        for (Territorio v : vilas()) {
            if (v.dono() == null) {
                v.setDono(reinoMaisPerto(v.centro() == null ? new Location(null, v.x(), v.y(), v.z()) : v.centro()));
            }
        }
    }

    /** Staff ou conquista: muda o dono (e pinta as bandeiras do castelo). */
    public void definirDono(Territorio t, String novo) {
        String antigo = t.dono();
        t.setDono(novo);
        if (t.tipo() == Territorio.Tipo.CASTELO) {
            Reino a = obter(antigo);
            Reino n = obter(novo);
            if (a != null && n != null && a.bandeira() != n.bandeira()) {
                if (t.carregado()) {
                    pintar(t, a.bandeira(), n.bandeira());
                } else {
                    t.setPintarDe(t.pintarDe() == null ? a.bandeira().name() : t.pintarDe());
                }
            }
            garantirRei(t);
            removerGuarnicao(t, antigo);
        } else {
            removerGuarnicao(t, antigo);
        }
        gravar();
    }

    // ------------------------------------------------------------------ REIS

    /** O rei/rainha fica no trono do castelo original enquanto o reino for dono dele. */
    public void garantirRei(Territorio c) {
        if (c.tipo() != Territorio.Tipo.CASTELO || c.reinoOriginal() == null) {
            return;
        }
        Reino r = obter(c.reinoOriginal());
        if (r == null) {
            return;
        }
        String id = "rei_" + r.id();
        NpcVivo rei = plugin.npcs().obter(id);
        boolean noTrono = r.id().equals(c.dono());
        if (!noTrono) {
            if (rei != null) {
                plugin.npcs().remover(rei);
            }
            return;
        }
        Location trono = c.trono();
        if (trono == null) {
            return;
        }
        if (rei == null) {
            rei = plugin.npcs().criar(id, Papel.GUIA, r.cor() + "<bold>" + r.rei(), trono);
        }
        rei.setNome(r.cor() + "<bold>" + r.rei());
        rei.setSkin(r.reiSkin());
        rei.setFaccao(r.id());
        rei.papeis().add(Papel.LIDER);
        rei.setItemNaMao(Material.GOLDEN_SWORD);
        rei.setArvore("rei_" + r.id());
        rei.setLocal(trono.getWorld().getName(), trono.getX(), trono.getY(), trono.getZ(), trono.getYaw());
        rei.setRotina(new Rotina(Rotina.Tipo.PARADO, 2, new ArrayList<>(),
                new ArrayList<>(List.of(r.lema(), "Guardas! Fiquem atentos.", "Meu reino não cairá!", "Quem ousa desafiar " + r.curto() + "?")), false));
        plugin.npcs().salvar(rei);
        plugin.npcs().spawnar(rei);
    }

    // ------------------------------------------------------------------ SOLDADOS

    public boolean ehSoldado(Entity e) {
        return e instanceof AbstractSkeleton && e.getPersistentDataContainer().has(kReino);
    }

    public String reinoDoSoldado(Entity e) {
        return e == null ? null : e.getPersistentDataContainer().get(kReino, PersistentDataType.STRING);
    }

    /**
     * Cria um soldado. tipo: espada, arco ou capitao. persistente = fica salvo no mapa (guarnição); exército não.
     */
    public Skeleton criarSoldado(Location l, Reino r, String tipo, boolean persistente, Consumer<PersistentDataContainer> extra) {
        double vida = tipo.equals("capitao") ? cfg.getDouble("soldado.capitao-vida", 60) : cfg.getDouble("soldado.vida", 30);
        double dano = tipo.equals("capitao") ? cfg.getDouble("soldado.capitao-dano", 8) : cfg.getDouble("soldado.dano", 5);
        return l.getWorld().spawn(l, Skeleton.class, s -> {
            s.setPersistent(persistente);
            s.setRemoveWhenFarAway(false);
            s.setShouldBurnInDay(false);
            s.setCanPickupItems(false);
            EntityEquipment eq = s.getEquipment();
            Material arma = switch (tipo) {
                case "arco" -> Material.BOW;
                case "capitao" -> Material.DIAMOND_SWORD;
                default -> Material.IRON_SWORD;
            };
            eq.setItemInMainHand(new ItemStack(arma));
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            base(s, Attribute.MAX_HEALTH, vida);
            s.setHealth(vida);
            // a espada já soma +5 (ferro) / +6 (diamante) no dano do mob
            base(s, Attribute.ATTACK_DAMAGE, Math.max(1, dano - (arma == Material.IRON_SWORD ? 5 : arma == Material.DIAMOND_SWORD ? 6 : 0)));
            base(s, Attribute.ARMOR, cfg.getDouble("soldado.armadura", 6) + (tipo.equals("capitao") ? 4 : 0));
            base(s, Attribute.MOVEMENT_SPEED, cfg.getDouble("soldado.velocidade", 0.27));
            base(s, Attribute.FOLLOW_RANGE, 32);
            base(s, Attribute.KNOCKBACK_RESISTANCE, tipo.equals("capitao") ? 0.6 : 0.2);
            PersistentDataContainer pdc = s.getPersistentDataContainer();
            pdc.set(kReino, PersistentDataType.STRING, r.id());
            pdc.set(kTipo, PersistentDataType.STRING, tipo);
            pdc.set(kPosto, PersistentDataType.STRING, l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
            if (extra != null) {
                extra.accept(pdc);
            }
        });
    }

    private static void base(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    private static ItemStack couro(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        i.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(c));
        return i;
    }

    @Override
    public boolean quer(LivingEntity e) {
        return ehSoldado(e);
    }

    @Override
    public void vestir(LivingEntity cerebro, Mannequin corpo) {
        AbstractSkeleton s = (AbstractSkeleton) cerebro;
        Reino r = obter(reinoDoSoldado(s));
        Bukkit.getMobGoals().removeAllGoals(s, GoalType.TARGET);
        s.setShouldBurnInDay(false);
        if (r == null) {
            Bukkit.getScheduler().runTask(plugin, s::remove); // reino apagado do reinos.yml
            return;
        }
        String tipo = s.getPersistentDataContainer().getOrDefault(kTipo, PersistentDataType.STRING, "espada");
        Random rnd = new Random(s.getUniqueId().getMostSignificantBits());
        corpo.setProfile(NpcManager.perfil(r.skins().get(rnd.nextInt(r.skins().size()))));
        String titulo = switch (tipo) {
            case "capitao" -> "<bold>Capitão de " + r.curto();
            case "arco" -> "Arqueiro de " + r.curto();
            default -> "Soldado de " + r.curto();
        };
        corpo.customName(Texto.cor(r.cor() + "⚔ " + titulo));
        corpo.setCustomNameVisible(true);
        EntityEquipment eq = corpo.getEquipment();
        eq.clear();
        switch (tipo) {
            case "capitao" -> {
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setLeggings(couro(Material.LEATHER_LEGGINGS, r.corCouro()));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            }
            case "arco" -> {
                eq.setHelmet(couro(Material.LEATHER_HELMET, r.corCouro()));
                eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, r.corCouro()));
                eq.setLeggings(couro(Material.LEATHER_LEGGINGS, Color.fromRGB(0x3A2A1A)));
                eq.setBoots(couro(Material.LEATHER_BOOTS, Color.fromRGB(0x3A2A1A)));
            }
            default -> {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, r.corCouro()));
                eq.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            }
        }
        eq.setItemInMainHand(s.getEquipment().getItemInMainHand().clone());
        if (!tipo.equals("arco")) {
            ItemStack escudo = new ItemStack(Material.SHIELD);
            try {
                DyeColor cor = r.bandeira();
                escudo.editMeta(ShieldMeta.class, m -> m.setBaseColor(cor));
            } catch (RuntimeException | LinkageError ignorado) {
                // versão sem cor de escudo
            }
            eq.setItemInOffHand(escudo);
        }
        atualizar(cerebro, corpo);
    }

    @Override
    public void atualizar(LivingEntity cerebro, Mannequin corpo) {
        Reino r = obter(reinoDoSoldado(cerebro));
        double max = cerebro.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : cerebro.getAttribute(Attribute.MAX_HEALTH).getValue();
        double vida = Math.max(0, cerebro.getHealth());
        String cor = vida > max * 0.6 ? "<green>" : vida > max * 0.3 ? "<yellow>" : "<red>";
        corpo.setDescription(Texto.cor(cor + "❤ " + (int) Math.ceil(vida) + "<dark_gray>/" + (int) Math.round(max)
                + (r == null ? "" : " <dark_gray>• " + r.cor() + r.curto())));
    }

    @Override
    public void clicar(Player p, LivingEntity cerebro, Mannequin corpo) {
        Reino r = obter(reinoDoSoldado(cerebro));
        if (r == null) {
            return;
        }
        Faccao membro = plugin.faccoes().membro(p);
        String fala;
        if (membro != null && membro.id().equals(r.id())) {
            fala = List.of("Às suas ordens!", "Pelo " + r.curto() + "!", r.lema(), "Nenhum inimigo passa por aqui.")
                    .get(ThreadLocalRandom.current().nextInt(4));
        } else if (plugin.faccoes().inimigoDosGuardas(p, faccao(r))) {
            fala = "Inimigo do reino! Saia daqui ou morra!";
        } else {
            fala = "Quer servir ao " + Texto.semCor(r.nome()) + "? Fale com o rei no castelo ou use /reino.";
        }
        p.sendMessage(Texto.cor(r.cor() + Texto.semCor(Texto.plano(corpo.customName() == null ? Texto.cor("Soldado") : corpo.customName()))
                + "<dark_gray> » <white>" + fala));
        p.playSound(corpo.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.6f, 1f);
    }

    // ------------------------------------------------------------------ IA (a cada segundo)

    /** O jogador é inimigo deste reino (os soldados atacam)? */
    public boolean jogadorInimigo(Player p, String reino) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        Faccao f = plugin.faccoes().obter(reino);
        return f != null && plugin.faccoes().inimigoDosGuardas(p, f);
    }

    private boolean inimigo(LivingEntity s, String reino, LivingEntity alvo) {
        if (alvo == null || !alvo.isValid() || alvo.isDead() || !alvo.getWorld().equals(s.getWorld())) {
            return false;
        }
        if (alvo instanceof Player p) {
            return jogadorInimigo(p, reino);
        }
        if (ehSoldado(alvo)) {
            return emGuerra(reino, reinoDoSoldado(alvo));
        }
        return alvo instanceof Monster && !(alvo instanceof Creeper) && !corpos.temCorpo(alvo);
    }

    private void pensar() {
        for (LivingEntity c : corpos.cerebros(this)) {
            if (!(c instanceof AbstractSkeleton s) || !s.isValid()) {
                continue;
            }
            String reino = reinoDoSoldado(s);
            LivingEntity alvo = s.getTarget();
            if (alvo != null && (!inimigo(s, reino, alvo) || alvo.getLocation().distanceSquared(s.getLocation()) > 28 * 28)) {
                s.setTarget(null);
                alvo = null;
            }
            if (alvo == null) {
                alvo = procurarAlvo(s, reino);
                if (alvo != null) {
                    s.setTarget(alvo);
                }
            }
            if (alvo != null) {
                continue;
            }
            if (s.getPersistentDataContainer().has(kExercito)) {
                continue; // quem anda com o exército é a Campanhas
            }
            Location posto = posto(s);
            if (posto != null && posto.distanceSquared(s.getLocation()) > 14 * 14) {
                s.getPathfinder().moveTo(posto, 1.0);
            }
        }
    }

    private LivingEntity procurarAlvo(AbstractSkeleton s, String reino) {
        LivingEntity melhor = null;
        double d = Double.MAX_VALUE;
        for (Entity e : s.getNearbyEntities(18, 8, 18)) {
            if (!(e instanceof LivingEntity le) || e instanceof Mannequin) {
                continue;
            }
            if (!inimigo(s, reino, le)) {
                continue;
            }
            double x = le.getLocation().distanceSquared(s.getLocation());
            // soldado inimigo e jogador procurado têm prioridade sobre monstro
            if (le instanceof Monster && !ehSoldado(le)) {
                x += 100;
            }
            if (x < d) {
                d = x;
                melhor = le;
            }
        }
        return melhor;
    }

    public Location posto(Entity s) {
        String p = s.getPersistentDataContainer().get(kPosto, PersistentDataType.STRING);
        if (p == null) {
            return null;
        }
        String[] v = p.split(",");
        try {
            return new Location(s.getWorld(), Integer.parseInt(v[0]) + 0.5, Integer.parseInt(v[1]), Integer.parseInt(v[2]) + 0.5);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ GUARNIÇÕES (a cada 30 s)

    private boolean jogadorPerto(Location l, double raio) {
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= raio * raio) {
                return true;
            }
        }
        return false;
    }

    private void guarnicoes() {
        double raioAtivo = cfg.getDouble("guarnicao.raio-ativo", 160);
        for (Territorio t : List.copyOf(territorios.values())) {
            Location c = t.centro();
            if (c == null || t.dono() == null || !t.carregado() || !jogadorPerto(c, raioAtivo)) {
                continue;
            }
            if (t.pintarDe() != null) {
                Reino n = obter(t.dono());
                try {
                    DyeColor de = DyeColor.valueOf(t.pintarDe());
                    if (n != null && de != n.bandeira()) {
                        pintar(t, de, n.bandeira());
                    }
                } catch (IllegalArgumentException ignorado) {
                    // cor inválida
                }
                t.setPintarDe(null);
                gravar();
            }
            if (campanhas.emCerco(t)) {
                continue; // durante o cerco quem manda são as Campanhas
            }
            completarGuarnicao(t);
        }
    }

    /** Soldados do dono que pertencem a este castelo/vila e estão por perto. */
    public List<AbstractSkeleton> guarnicao(Territorio t, String reino) {
        List<AbstractSkeleton> l = new ArrayList<>();
        Location c = t.centro();
        if (c == null) {
            return l;
        }
        for (Entity e : c.getWorld().getNearbyEntities(c, t.raio() + 32, 64, t.raio() + 32)) {
            if (e instanceof AbstractSkeleton s && s.isValid() && ehSoldado(s)
                    && t.id().equals(s.getPersistentDataContainer().get(kTerritorio, PersistentDataType.STRING))
                    && (reino == null || reino.equals(reinoDoSoldado(s)))) {
                l.add(s);
            }
        }
        return l;
    }

    public int completarGuarnicao(Territorio t) {
        Reino r = obter(t.dono());
        Location c = t.centro();
        if (r == null || c == null) {
            return 0;
        }
        int quer = t.tipo() == Territorio.Tipo.CASTELO ? cfg.getInt("guarnicao.soldados", 8) : cfg.getInt("guarnicao.soldados-por-vila", 3);
        List<AbstractSkeleton> atuais = guarnicao(t, r.id());
        boolean temCapitao = atuais.stream().anyMatch(s -> "capitao".equals(s.getPersistentDataContainer().get(kTipo, PersistentDataType.STRING)));
        int faltam = Math.min(4, quer - atuais.size());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int feitos = 0;
        for (int i = 0; i < faltam; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 3 + rnd.nextDouble() * Math.max(3, Math.min(t.raio() - 4, t.tipo() == Territorio.Tipo.CASTELO ? 16 : 12));
            int x = (int) Math.floor(c.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(c.getZ() + Math.sin(ang) * dist);
            Location l = chao(c.getWorld(), x, z, c.getBlockY());
            if (l == null) {
                continue;
            }
            String tipo = t.tipo() == Territorio.Tipo.CASTELO && !temCapitao && i == 0 ? "capitao"
                    : rnd.nextDouble() < cfg.getDouble("guarnicao.arqueiros", 0.35) ? "arco" : "espada";
            if (tipo.equals("capitao")) {
                temCapitao = true;
            }
            criarSoldado(l, r, tipo, true, pdc -> pdc.set(kTerritorio, PersistentDataType.STRING, t.id()));
            feitos++;
        }
        return feitos;
    }

    /** Chão firme com 2 de ar perto da altura indicada (procura de cima para baixo dentro do castelo). */
    static Location chao(World w, int x, int z, int alturaBase) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        for (int y = alturaBase + 6; y >= alturaBase - 6; y--) {
            Block b = w.getBlockAt(x, y - 1, z);
            if (b.getType().isSolid() && !b.isLiquid() && w.getBlockAt(x, y, z).isPassable() && w.getBlockAt(x, y + 1, z).isPassable()
                    && !w.getBlockAt(x, y, z).isLiquid()) {
                return new Location(w, x + 0.5, y, z + 0.5);
            }
        }
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        Block b = w.getBlockAt(x, y - 1, z);
        return b.isLiquid() ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    private void removerGuarnicao(Territorio t, String reino) {
        if (reino == null || !t.carregado()) {
            return;
        }
        for (AbstractSkeleton s : guarnicao(t, reino)) {
            s.getWorld().spawnParticle(Particle.POOF, s.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            s.remove();
        }
    }

    // ------------------------------------------------------------------ BANDEIRAS

    /** Troca a cor das bandeiras, lã e tapetes do castelo (aos poucos, para não travar). */
    public void pintar(Territorio t, DyeColor de, DyeColor para) {
        World w = Bukkit.getWorld(t.mundo());
        if (w == null) {
            return;
        }
        String a = "minecraft:" + de.name().toLowerCase(Locale.ROOT) + "_";
        String b = "minecraft:" + para.name().toLowerCase(Locale.ROOT) + "_";
        int r = t.raio() + 4;
        int y0 = t.y() - 8;
        int y1 = t.y() + 45;
        new BukkitRunnable() {
            int y = y0;

            @Override
            public void run() {
                for (int n = 0; n < 3 && y <= y1; n++, y++) {
                    for (int x = t.x() - r; x <= t.x() + r; x++) {
                        for (int z = t.z() - r; z <= t.z() + r; z++) {
                            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                                continue;
                            }
                            Block bl = w.getBlockAt(x, y, z);
                            String nome = bl.getType().name();
                            if (!nome.endsWith("_BANNER") && !nome.endsWith("_WOOL") && !nome.endsWith("_CARPET")) {
                                continue;
                            }
                            String dados = bl.getBlockData().getAsString();
                            if (dados.startsWith(a)) {
                                try {
                                    BlockData nova = Bukkit.createBlockData(b + dados.substring(a.length()));
                                    bl.setBlockData(nova, false);
                                } catch (IllegalArgumentException ignorado) {
                                    // bloco sem a versão nessa cor
                                }
                            }
                        }
                    }
                }
                if (y > y1) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ DIPLOMACIA AUTOMÁTICA

    private void diplomacia() {
        if (!cfg.getBoolean("guerra.automatica", true) || reinos.size() < 2) {
            return;
        }
        List<Reino> l = new ArrayList<>(reinos.values());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Reino a = l.get(r.nextInt(l.size()));
        Reino b = l.get(r.nextInt(l.size()));
        if (a == b) {
            return;
        }
        Faccao fa = faccao(a);
        Faccao fb = faccao(b);
        if (fa == null || fb == null || emGuerra(a.id(), b.id())) {
            return;
        }
        long guerrasDeReino = plugin.guerras().ativas().stream().filter(g -> obter(g.a()) != null && obter(g.b()) != null).count();
        String[] motivos = {"Disputa pelas terras da fronteira", "Um emissário foi insultado na corte", "Soldados cruzaram a fronteira à noite",
                "Uma caravana de ouro foi saqueada", "O casamento real foi recusado", "Espiões foram presos no castelo"};
        String motivo = motivos[r.nextInt(motivos.length)];
        if (guerrasDeReino < cfg.getInt("guerra.maximo-guerras", 2) && r.nextDouble() < cfg.getDouble("guerra.chance-guerra", 0.25)) {
            plugin.guerras().iniciar(fa, fb, cfg.getDouble("guerra.duracao-horas", 48), motivo);
            return;
        }
        // tensão sobe aos poucos (a DiplomaciaService declara a guerra sozinha abaixo de -60)
        if (plugin.faccoes().relacao(a.id(), b.id()) != Relacao.ALIADA) {
            plugin.diplomacia().alterar(a.id(), b.id(), -r.nextInt(4, 15), motivo);
        }
    }

    /** /reino noticias: o placar das guerras dos reinos e os exércitos. */
    public List<String> resumoGuerras() {
        List<String> l = new ArrayList<>();
        for (Guerra g : plugin.guerras().ativas()) {
            Reino a = obter(g.a());
            Reino b = obter(g.b());
            if (a == null || b == null) {
                continue;
            }
            l.add(a.nome() + " <gray>x " + b.nome() + " <dark_gray>| <white>" + g.pontosA() + " <gray>a <white>" + g.pontosB()
                    + " <dark_gray>(meta " + g.meta() + ")");
        }
        l.addAll(campanhas.resumo());
        return l;
    }

    // ------------------------------------------------------------------ EVENTOS

    private Entity atacanteReal(Entity d) {
        if (d instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            return s instanceof Entity e ? e : null;
        }
        return d;
    }

    /** Sem fogo amigo: soldado não fere aliado, morador nem quem não é inimigo. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFerir(EntityDamageByEntityEvent e) {
        Entity atacante = atacanteReal(e.getDamager());
        Entity vitima = corpos.real(e.getEntity());
        if (ehSoldado(atacante)) {
            String reino = reinoDoSoldado(atacante);
            if (vitima instanceof AbstractVillager || vitima instanceof IronGolem
                    || (ehSoldado(vitima) && !emGuerra(reino, reinoDoSoldado(vitima)))
                    || (vitima instanceof Player p && !jogadorInimigo(p, reino))) {
                e.setCancelled(true);
            }
            return;
        }
        if (atacante instanceof Player p && ehSoldado(vitima)) {
            String reino = reinoDoSoldado(vitima);
            Faccao membro = plugin.faccoes().membro(p);
            if (membro != null && membro.id().equals(reino)) {
                e.setCancelled(true); // não bate no próprio exército
                p.sendActionBar(Texto.cor("<red>Esse soldado é do seu reino!"));
                return;
            }
            Faccao f = plugin.faccoes().obter(reino);
            if (f != null && !jogadorInimigo(p, reino)) {
                plugin.faccoes().marcarProcurado(p, f); // agora os soldados revidam
            }
            if (vitima instanceof AbstractSkeleton s && s.getTarget() == null) {
                s.setTarget(p);
            }
        }
    }

    /** Golem de ferro e outros mobs não caçam os soldados (eles são "esqueletos" por dentro). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMirar(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && ehSoldado(e.getTarget()) && !ehSoldado(e.getEntity()) && !(e.getEntity() instanceof Monster)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTransformar(EntityTransformEvent e) {
        if (ehSoldado(e.getEntity())) {
            e.setCancelled(true); // esqueleto na neve vira "stray": soldado não
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPegarFogo(EntityCombustEvent e) {
        if (ehSoldado(e.getEntity()) && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)
                && !(e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent)) {
            e.setCancelled(true); // sol
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrer(EntityDeathEvent e) {
        if (!ehSoldado(e.getEntity())) {
            return;
        }
        LivingEntity s = e.getEntity();
        e.getDrops().clear();
        e.setDroppedExp(0);
        String reino = reinoDoSoldado(s);
        Reino r = obter(reino);
        boolean capitao = "capitao".equals(s.getPersistentDataContainer().get(kTipo, PersistentDataType.STRING));
        campanhas.aoMorrerSoldado(s);
        Player matador = s.getKiller();
        if (matador == null) {
            // morto por soldado inimigo: ponto de guerra para o outro lado
            if (s.getLastDamageCause() instanceof EntityDamageByEntityEvent ee) {
                Entity a = atacanteReal(ee.getDamager());
                if (ehSoldado(a)) {
                    plugin.guerras().pontuar(reinoDoSoldado(a), reino, cfg.getInt("guerra.pontos.soldado", 5), null);
                }
            }
            return;
        }
        e.setDroppedExp(capitao ? 30 : 8);
        Faccao membro = plugin.faccoes().membro(matador);
        if (membro != null && emGuerra(membro.id(), reino)) {
            double premio = cfg.getDouble("soldado.recompensa", 25) * (capitao ? 4 : 1);
            if (plugin.economia().ativa()) {
                plugin.economia().depositar(matador, premio);
            }
            matador.sendActionBar(Texto.cor("<gold>+$" + Texto.dinheiro(premio) + " <gray>por derrotar um soldado de " + (r == null ? reino : r.curto())));
            plugin.guerras().pontuar(membro.id(), reino, cfg.getInt("guerra.pontos.soldado", 5) * (capitao ? 3 : 1), matador);
            if (ThreadLocalRandom.current().nextInt(100) < (capitao ? 60 : 15)) {
                e.getDrops().add(new ItemStack(capitao ? Material.DIAMOND : Material.IRON_INGOT, 1 + ThreadLocalRandom.current().nextInt(capitao ? 2 : 3)));
            }
            e.getDrops().add(new ItemStack(Material.ARROW, ThreadLocalRandom.current().nextInt(4)));
        } else {
            Faccao f = plugin.faccoes().obter(reino);
            if (f != null) {
                plugin.faccoes().alterarReputacao(matador, f, -cfg.getInt("soldado.reputacao-ao-matar", 25));
                plugin.faccoes().marcarProcurado(matador, f);
            }
        }
        if (capitao && r != null) {
            for (Player p : s.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(s.getLocation()) < 128 * 128) {
                    p.sendMessage(Texto.cor("<gray>⚔ O <bold>Capitão de " + r.curto() + "</bold> caiu diante de <white>" + matador.getName() + "<gray>!"));
                }
            }
        }
    }
}
