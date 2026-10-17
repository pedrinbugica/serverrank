package dev.pedrobugica.npcvivos.povo;

import dev.pedrobugica.npcvivos.NpcVivosPlugin;
import dev.pedrobugica.npcvivos.comportamento.Movimento;
import dev.pedrobugica.npcvivos.core.NpcManager;
import dev.pedrobugica.npcvivos.faccao.Faccao;
import dev.pedrobugica.npcvivos.menu.Menu;
import dev.pedrobugica.npcvivos.mundo.Guerra;
import dev.pedrobugica.npcvivos.util.Texto;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/**
 * Os moradores humanos: cada aldeão (e mercador ambulante) dos mundos do RPG ganha nome, sobrenome, skin, roupa da
 * profissão, personalidade, humor e a vida em cima da cabeça. Com o botão direito abre a conversa (estilo MCA):
 * conversar, piada, elogio, abraço, flerte, presente, casamento, trabalhos, notícias da guerra, seguir e ficar.
 * Tudo que o morador lembra fica guardado nele mesmo (PersistentDataContainer), então não pesa no banco de dados.
 */
public final class MoradorService implements Corpos.Molde, Listener {

    /** Quem é o morador. */
    public record Identidade(String nome, String sobrenome, boolean feminina, String skin, Personalidade personalidade, long semente) {
        public String completo() {
            return sobrenome == null || sobrenome.isBlank() ? nome : nome + " " + sobrenome;
        }
    }

    private static final String[] PROFISSOES = {"farmer", "fisherman", "shepherd", "fletcher", "librarian", "cartographer", "cleric",
            "armorer", "weaponsmith", "toolsmith", "butcher", "leatherworker", "mason"};

    private final NpcVivosPlugin plugin;
    private final Corpos corpos;
    private final File arquivo;
    private final File arquivoDados;
    private YamlConfiguration cfg;

    private final NamespacedKey kNome;
    private final NamespacedKey kSobrenome;
    private final NamespacedKey kFeminina;
    private final NamespacedKey kSkin;
    private final NamespacedKey kPersonalidade;
    private final NamespacedKey kSemente;
    private final NamespacedKey kHumor;
    private final NamespacedKey kAmizade;
    private final NamespacedKey kConjuge;
    private final NamespacedKey kConjugeNome;
    private final NamespacedKey kSeguindo;
    private final NamespacedKey kParado;
    private final NamespacedKey kCrianca;
    private final NamespacedKey kProfissao;
    private final NamespacedKey jConjuge;
    private final NamespacedKey jConjugeNome;
    private final NamespacedKey jTrabalho;
    private final NamespacedKey jPresenteDia;

    private final Map<String, Long> esperas = new HashMap<>();
    private final Map<String, Integer> presentesRecentes = new HashMap<>();
    private final Map<String, Long> presentesRecentesEm = new HashMap<>();
    private final Map<UUID, Long> proximaFala = new HashMap<>();
    private final Map<UUID, Long> proximoPerigo = new HashMap<>();
    private final Map<String, Long> cumprimentos = new HashMap<>();
    private final Map<UUID, TextDisplay> baloes = new HashMap<>();
    private final Map<UUID, Long> balaoAte = new HashMap<>();
    private final Map<UUID, Long> avisoReputacao = new HashMap<>();
    private final Set<String> falecidos = new LinkedHashSet<>();
    private BukkitTask tarefaVida;
    private BukkitTask tarefaSeguir;
    /** Preenchido pelo SurvivalRPG: lugares conhecidos perto (para as fofocas). */
    private BiFunction<Player, Location, List<String>> localizador;

    public MoradorService(NpcVivosPlugin plugin, Corpos corpos) {
        this.plugin = plugin;
        this.corpos = corpos;
        this.arquivo = new File(plugin.getDataFolder(), "povo.yml");
        this.arquivoDados = new File(plugin.getDataFolder(), "povo-dados.yml");
        kNome = chave("m-nome");
        kSobrenome = chave("m-sobrenome");
        kFeminina = chave("m-feminina");
        kSkin = chave("m-skin");
        kPersonalidade = chave("m-personalidade");
        kSemente = chave("m-semente");
        kHumor = chave("m-humor");
        kAmizade = chave("m-amizade");
        kConjuge = chave("m-conjuge");
        kConjugeNome = chave("m-conjuge-nome");
        kSeguindo = chave("m-seguindo");
        kParado = chave("m-parado");
        kCrianca = chave("m-crianca");
        kProfissao = chave("m-profissao");
        jConjuge = chave("conjuge");
        jConjugeNome = chave("conjuge-nome");
        jTrabalho = chave("trabalho");
        jPresenteDia = chave("presente-conjuge-dia");
    }

    private NamespacedKey chave(String k) {
        return new NamespacedKey(plugin, k);
    }

    // ------------------------------------------------------------------ CONFIG

    public void carregar() {
        if (!arquivo.exists()) {
            plugin.saveResource("povo.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(arquivo);
        falecidos.clear();
        falecidos.addAll(YamlConfiguration.loadConfiguration(arquivoDados).getStringList("falecidos"));
    }

    private void gravarDados() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>(falecidos);
        if (l.size() > 500) {
            l = l.subList(l.size() - 500, l.size());
        }
        y.set("falecidos", l);
        try {
            y.save(arquivoDados);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível gravar povo-dados.yml: " + e.getMessage());
        }
    }

    public boolean ativo() {
        return cfg != null && cfg.getBoolean("ativo", true);
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public void setLocalizador(BiFunction<Player, Location, List<String>> localizador) {
        this.localizador = localizador;
    }

    private List<String> lista(String caminho) {
        return cfg.getStringList(caminho);
    }

    private String sortear(String caminho) {
        List<String> l = lista(caminho);
        return l.isEmpty() ? "" : l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    private static <T> T sortear(List<T> l, Random r) {
        return l.get(r.nextInt(l.size()));
    }

    public void iniciar() {
        parar();
        corpos.registrar(this);
        tarefaVida = Bukkit.getScheduler().runTaskTimer(plugin, this::viver, 40L, 20L);
        tarefaSeguir = Bukkit.getScheduler().runTaskTimer(plugin, this::seguirTick, 40L, 2L);
    }

    public void parar() {
        if (tarefaVida != null) {
            tarefaVida.cancel();
            tarefaVida = null;
        }
        if (tarefaSeguir != null) {
            tarefaSeguir.cancel();
            tarefaSeguir = null;
        }
        for (TextDisplay t : baloes.values()) {
            t.remove();
        }
        baloes.clear();
        balaoAte.clear();
        gravarDados();
    }

    // ------------------------------------------------------------------ IDENTIDADE

    @Override
    public boolean quer(LivingEntity e) {
        if (!ativo() || !(e instanceof AbstractVillager)) {
            return false;
        }
        if (e instanceof WanderingTrader && !cfg.getBoolean("mercador-ambulante", true)) {
            return false;
        }
        return cfg.getStringList("mundos").contains(e.getWorld().getName());
    }

    public boolean ehMorador(Entity e) {
        return e instanceof AbstractVillager v && corpos.temCorpo(v);
    }

    public Identidade identidade(AbstractVillager v) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        String nome = pdc.get(kNome, PersistentDataType.STRING);
        if (nome != null) {
            return new Identidade(nome, pdc.getOrDefault(kSobrenome, PersistentDataType.STRING, ""),
                    pdc.getOrDefault(kFeminina, PersistentDataType.BYTE, (byte) 0) == 1,
                    pdc.getOrDefault(kSkin, PersistentDataType.STRING, "steve"),
                    Personalidade.buscar(pdc.get(kPersonalidade, PersistentDataType.STRING)),
                    pdc.getOrDefault(kSemente, PersistentDataType.LONG, 0L));
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean fem = r.nextBoolean();
        List<String> nomes = lista(fem ? "nomes.femininos" : "nomes.masculinos");
        nome = nomes.isEmpty() ? (fem ? "Maria" : "João") : nomes.get(r.nextInt(nomes.size()));
        String sobrenome = pdc.get(kSobrenome, PersistentDataType.STRING); // filho já nasce com o sobrenome dos pais
        if (sobrenome == null) {
            List<String> s = lista("nomes.sobrenomes");
            sobrenome = s.isEmpty() ? "" : s.get(r.nextInt(s.size()));
        }
        List<String> skins = new ArrayList<>(lista(fem ? "skins.femininas" : "skins.masculinas"));
        skins.addAll(lista(fem ? "skins.extras-femininas" : "skins.extras-masculinas"));
        String skin = skins.isEmpty() ? (fem ? "alex-fino" : "steve") : skins.get(r.nextInt(skins.size()));
        Personalidade per = Personalidade.values()[r.nextInt(Personalidade.values().length)];
        long semente = r.nextLong();
        pdc.set(kNome, PersistentDataType.STRING, nome);
        pdc.set(kSobrenome, PersistentDataType.STRING, sobrenome);
        pdc.set(kFeminina, PersistentDataType.BYTE, (byte) (fem ? 1 : 0));
        pdc.set(kSkin, PersistentDataType.STRING, skin);
        pdc.set(kPersonalidade, PersistentDataType.STRING, per.name());
        pdc.set(kSemente, PersistentDataType.LONG, semente);
        pdc.set(kHumor, PersistentDataType.INTEGER, 45 + r.nextInt(30));
        return new Identidade(nome, sobrenome, fem, skin, per, semente);
    }

    /** Renomeia (comando da staff). */
    public void renomear(AbstractVillager v, String nome, String sobrenome) {
        identidade(v);
        v.getPersistentDataContainer().set(kNome, PersistentDataType.STRING, nome);
        if (sobrenome != null) {
            v.getPersistentDataContainer().set(kSobrenome, PersistentDataType.STRING, sobrenome);
        }
        corpos.revestir(v);
    }

    private static boolean crianca(AbstractVillager v) {
        return v instanceof Villager vil && !vil.isAdult();
    }

    public String chaveProfissao(AbstractVillager v) {
        if (v instanceof WanderingTrader) {
            return "ambulante";
        }
        if (crianca(v)) {
            return "crianca";
        }
        Villager vil = (Villager) v;
        return vil.getProfession().getKey().getKey().toLowerCase(Locale.ROOT);
    }

    public String nomeProfissao(AbstractVillager v, boolean fem) {
        String s = cfg.getString("profissoes." + chaveProfissao(v), "Morador|Moradora");
        String[] p = s.split("\\|");
        return fem && p.length > 1 ? p[1] : p[0];
    }

    public String nomeCompleto(AbstractVillager v) {
        return identidade(v).completo();
    }

    // ------------------------------------------------------------------ CORPO

    @Override
    public void vestir(LivingEntity cerebro, Mannequin corpo) {
        AbstractVillager v = (AbstractVillager) cerebro;
        Identidade id = identidade(v);
        String prof = chaveProfissao(v);
        v.getPersistentDataContainer().set(kCrianca, PersistentDataType.BYTE, (byte) (crianca(v) ? 1 : 0));
        v.getPersistentDataContainer().set(kProfissao, PersistentDataType.STRING, prof);
        corpo.setProfile(NpcManager.perfil(id.skin()));
        corpo.customName(Texto.cor((crianca(v) ? "<yellow>" : "<white>") + id.completo()));
        corpo.setCustomNameVisible(true);
        Random r = new Random(id.semente());
        corpo.setMainHand(r.nextInt(10) == 0 ? MainHand.LEFT : MainHand.RIGHT);
        AttributeInstance escala = corpo.getAttribute(Attribute.SCALE);
        if (escala != null) {
            escala.setBaseValue(crianca(v) ? 0.55 : 0.94 + r.nextDouble() * 0.1);
        }
        roupa(corpo.getEquipment(), prof, r);
        atualizar(cerebro, corpo);
        if (v.getPersistentDataContainer().has(kParado) || v.getPersistentDataContainer().has(kSeguindo)) {
            v.setAware(false);
        }
    }

    @Override
    public void atualizar(LivingEntity cerebro, Mannequin corpo) {
        AbstractVillager v = (AbstractVillager) cerebro;
        Identidade id = identidade(v);
        double max = cerebro.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : cerebro.getAttribute(Attribute.MAX_HEALTH).getValue();
        double vida = Math.max(0, cerebro.getHealth());
        String cor = vida > max * 0.6 ? "<green>" : vida > max * 0.3 ? "<yellow>" : "<red>";
        StringBuilder d = new StringBuilder("<gray>").append(nomeProfissao(v, id.feminina())).append(" <dark_gray>• ")
                .append(cor).append("❤ ").append((int) Math.ceil(vida)).append("<dark_gray>/").append((int) Math.round(max));
        var reino = plugin.reinos() == null ? null : plugin.reinos().reinoEm(v.getLocation());
        if (reino != null) {
            d.append(" <dark_gray>• ").append(reino.cor()).append(reino.curto());
        }
        corpo.setDescription(Texto.cor(d.toString()));
    }

    private static Color cor(int rgb, Random r, int variacao) {
        int red = Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) + r.nextInt(variacao * 2 + 1) - variacao));
        int green = Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) + r.nextInt(variacao * 2 + 1) - variacao));
        int blue = Math.max(0, Math.min(255, (rgb & 0xFF) + r.nextInt(variacao * 2 + 1) - variacao));
        return Color.fromRGB(red, green, blue);
    }

    private static ItemStack couro(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        i.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(c));
        return i;
    }

    /** Roupa pela profissão: couro tingido (cores um pouco diferentes para cada um) e a ferramenta na mão. */
    private static void roupa(EntityEquipment eq, String prof, Random r) {
        eq.clear();
        int[] casual = {0x8B5A2B, 0x3B5E2B, 0x6B4226, 0x2F4F6F, 0x7A2E2E, 0x556B2F, 0x4B3B6B, 0x8C7853, 0x5F6A6A};
        int tunica = casual[r.nextInt(casual.length)];
        int calca = r.nextBoolean() ? 0x4A3520 : 0x2E2E3A;
        Material mao = null;
        Material chapeu = null;
        int corChapeu = 0;
        ItemStack peito = null;
        switch (prof) {
            case "farmer" -> {
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0xD9B76E;
                tunica = r.nextBoolean() ? 0x5C7A2E : 0x8B6B3D;
                mao = r.nextBoolean() ? Material.IRON_HOE : Material.WHEAT;
            }
            case "fisherman" -> {
                chapeu = r.nextBoolean() ? Material.LEATHER_HELMET : null;
                corChapeu = 0xC9A227;
                tunica = 0x2F5F8F;
                mao = Material.FISHING_ROD;
            }
            case "shepherd" -> {
                tunica = 0xEAE6DA;
                calca = 0x6B4E2E;
                mao = Material.SHEARS;
            }
            case "fletcher" -> {
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0x2E5E2E;
                tunica = 0x3E6B2E;
                mao = Material.BOW;
            }
            case "librarian" -> {
                tunica = r.nextBoolean() ? 0x6B2E5E : 0x7A1F1F;
                calca = tunica;
                mao = Material.BOOK;
            }
            case "cartographer" -> {
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0x5A3A1A;
                tunica = 0x7A5A3A;
                mao = Material.MAP;
            }
            case "cleric" -> {
                tunica = 0x5E2E7A;
                calca = 0x5E2E7A;
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0xD4AF37;
                mao = Material.SPLASH_POTION;
            }
            case "armorer" -> {
                peito = new ItemStack(Material.IRON_CHESTPLATE);
                mao = Material.IRON_INGOT;
            }
            case "weaponsmith" -> {
                peito = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                mao = Material.IRON_SWORD;
            }
            case "toolsmith" -> {
                tunica = 0x4A3A2A;
                mao = Material.IRON_PICKAXE;
            }
            case "butcher" -> {
                tunica = 0xEDE8E0;
                mao = r.nextBoolean() ? Material.IRON_AXE : Material.COOKED_BEEF;
            }
            case "leatherworker" -> {
                tunica = 0x7B4B2A;
                calca = 0x5B3B1A;
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0x6B3B1A;
                mao = Material.LEATHER;
            }
            case "mason" -> {
                tunica = 0x7A7A7A;
                calca = 0x4A4A4A;
                mao = Material.BRICK;
            }
            case "ambulante" -> {
                chapeu = Material.LEATHER_HELMET;
                corChapeu = 0x1F3F7A;
                tunica = 0x2A4A8A;
                calca = 0x1F3F7A;
                mao = Material.EMERALD;
            }
            case "crianca" -> {
                int[] alegres = {0xE05A47, 0x47A0E0, 0xE0C847, 0x5AC85A, 0xC85AC8, 0xE08A47};
                tunica = alegres[r.nextInt(alegres.length)];
                calca = 0x3A4A6A;
            }
            case "nitwit" -> {
                tunica = 0x6E8B3D;
                mao = r.nextInt(3) == 0 ? Material.APPLE : null;
            }
            default -> {
                // "none": roupa do dia a dia
            }
        }
        if (chapeu != null) {
            eq.setHelmet(couro(chapeu, cor(corChapeu, r, 12)));
        }
        eq.setChestplate(peito != null ? peito : couro(Material.LEATHER_CHESTPLATE, cor(tunica, r, 18)));
        eq.setLeggings(couro(Material.LEATHER_LEGGINGS, cor(calca, r, 12)));
        eq.setBoots(couro(Material.LEATHER_BOOTS, cor(0x3B2A1A, r, 10)));
        if (mao != null) {
            eq.setItemInMainHand(new ItemStack(mao));
        }
    }

    // ------------------------------------------------------------------ AMIZADE E HUMOR

    public int amizade(AbstractVillager v, UUID jogador) {
        Map<String, Integer> m = amizades(v);
        return m.getOrDefault(jogador.toString(), 0);
    }

    private Map<String, Integer> amizades(AbstractVillager v) {
        Map<String, Integer> m = new LinkedHashMap<>();
        String s = v.getPersistentDataContainer().get(kAmizade, PersistentDataType.STRING);
        if (s != null && !s.isBlank()) {
            for (String par : s.split(";")) {
                int i = par.indexOf('=');
                if (i > 0) {
                    try {
                        m.put(par.substring(0, i), Integer.parseInt(par.substring(i + 1)));
                    } catch (NumberFormatException ignorado) {
                        // valor estragado
                    }
                }
            }
        }
        return m;
    }

    public int mudarAmizade(AbstractVillager v, Player p, int delta) {
        Map<String, Integer> m = amizades(v);
        int antes = m.getOrDefault(p.getUniqueId().toString(), 0);
        int depois = Math.max(-100, Math.min(100, antes + delta));
        m.put(p.getUniqueId().toString(), depois);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() != 0) {
                sb.append(sb.isEmpty() ? "" : ";").append(e.getKey()).append('=').append(e.getValue());
            }
        }
        v.getPersistentDataContainer().set(kAmizade, PersistentDataType.STRING, sb.toString());
        if (depois != antes) {
            p.sendActionBar(Texto.cor((depois > antes ? "<green>+" : "<red>") + (depois - antes) + " amizade <gray>com <white>"
                    + nomeCompleto(v) + " <dark_gray>(" + coracoes(depois) + "<dark_gray>)"));
        }
        return depois;
    }

    public static String coracoes(int amizade) {
        int n = (int) Math.round(Math.abs(amizade) / 10.0);
        if (amizade < 0) {
            return "<dark_red>" + "💔".repeat(Math.max(1, n));
        }
        return "<red>" + "❤".repeat(n) + "<dark_gray>" + "❤".repeat(10 - n);
    }

    private int humor(AbstractVillager v) {
        return v.getPersistentDataContainer().getOrDefault(kHumor, PersistentDataType.INTEGER, 60);
    }

    private void mudarHumor(AbstractVillager v, int delta) {
        v.getPersistentDataContainer().set(kHumor, PersistentDataType.INTEGER, Math.max(0, Math.min(100, humor(v) + delta)));
    }

    private static String textoHumor(int h) {
        if (h >= 80) {
            return "<green>Muito feliz ☺";
        }
        if (h >= 60) {
            return "<green>Feliz";
        }
        if (h >= 40) {
            return "<yellow>Normal";
        }
        if (h >= 20) {
            return "<gold>Chateado(a)";
        }
        return "<red>Furioso(a) ☹";
    }

    // ------------------------------------------------------------------ CASAMENTO

    public UUID conjugeDoJogador(Player p) {
        String s = p.getPersistentDataContainer().get(jConjuge, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean casadoCom(AbstractVillager v, Player p) {
        return p.getUniqueId().toString().equals(v.getPersistentDataContainer().get(kConjuge, PersistentDataType.STRING));
    }

    private boolean casado(AbstractVillager v) {
        return v.getPersistentDataContainer().has(kConjuge);
    }

    // ------------------------------------------------------------------ MENU DE CONVERSA

    @Override
    public void clicar(Player p, LivingEntity cerebro, Mannequin corpo) {
        AbstractVillager v = (AbstractVillager) cerebro;
        if (!p.hasPermission("npcvivos.usar")) {
            return;
        }
        if (v instanceof Villager vil && vil.isSleeping()) {
            p.sendMessage(Texto.cor("<gray><italic>" + nomeCompleto(v) + " está dormindo... zzz"));
            return;
        }
        if (amizade(v, p.getUniqueId()) <= -60) {
            falar(p, v, sortear("respostas.odeia"));
            return;
        }
        v.lookAt(p);
        if (v.getPathfinder().hasPath()) {
            v.getPathfinder().stopPathfinding();
        }
        presenteDoConjuge(p, v);
        abrirMenu(p, v);
    }

    private ItemStack botao(Material m, String nome, String... lore) {
        return Menu.criar(m, nome, List.of(lore));
    }

    public void abrirMenu(Player p, AbstractVillager v) {
        Identidade id = identidade(v);
        boolean crianca = crianca(v);
        int am = amizade(v, p.getUniqueId());
        String prof = nomeProfissao(v, id.feminina());
        Menu m = new Menu(4, Texto.cor("<dark_gray>" + id.completo()));
        List<String> info = new ArrayList<>();
        info.add("<gray>Profissão: <white>" + prof);
        info.add("<gray>Personalidade: <white>" + id.personalidade().nome(id.feminina()));
        info.add("<gray>Humor: " + textoHumor(humor(v)));
        info.add("<gray>Idade: <white>" + (crianca ? "criança" : "adulto(a)"));
        info.add("<gray>Vida: <red>❤ " + (int) Math.ceil(v.getHealth()));
        var reino = plugin.reinos() == null ? null : plugin.reinos().reinoEm(v.getLocation());
        String vila = plugin.reinos() == null ? null : plugin.reinos().vilaEm(v.getLocation());
        if (vila != null) {
            info.add("<gray>Mora em: <white>" + vila);
        }
        if (reino != null) {
            info.add("<gray>Reino: " + reino.nome());
        }
        String conj = v.getPersistentDataContainer().get(kConjugeNome, PersistentDataType.STRING);
        info.add("<gray>Estado civil: " + (casadoCom(v, p) ? "<light_purple>casado(a) com você ❤" : conj != null ? "<white>casado(a) com " + conj : "<white>solteiro(a)"));
        info.add("");
        info.add("<gray>Amizade com você: " + coracoes(am) + " <dark_gray>(" + am + ")");
        m.item(4, botao(Material.NAME_TAG, "<gold><bold>" + id.completo(), info.toArray(String[]::new)), null);

        m.item(10, botao(Material.BOOK, "<green><bold>Conversar", "<gray>Bater um papo.", "<gray>Sobe a amizade (se ele(a) estiver a fim)."),
                (pl, c) -> interagir(pl, v, "conversar"));
        if (crianca) {
            m.item(11, botao(Material.SNOWBALL, "<yellow><bold>Brincar", "<gray>Crianças adoram brincar!"),
                    (pl, c) -> interagir(pl, v, "brincar"));
        } else {
            m.item(11, botao(Material.SLIME_BALL, "<yellow><bold>Contar uma piada", "<gray>Funciona melhor com quem é engraçado."),
                    (pl, c) -> interagir(pl, v, "piada"));
        }
        m.item(12, botao(Material.POPPY, "<aqua><bold>Elogiar", "<gray>Todo mundo gosta de um elogio... quase."),
                (pl, c) -> interagir(pl, v, "elogio"));
        m.item(13, botao(Material.PINK_WOOL, "<light_purple><bold>Abraçar", "<gray>Precisa de amizade " + cfg.getInt("amizade-minima.abracar", 20) + "+."),
                (pl, c) -> interagir(pl, v, "abraco"));
        if (!crianca) {
            m.item(14, botao(Material.RED_TULIP, "<red><bold>Flertar", "<gray>Precisa de amizade " + cfg.getInt("amizade-minima.flertar", 40) + "+.",
                    "<gray>Românticos(as) gostam mais."), (pl, c) -> interagir(pl, v, "flerte"));
        }
        m.item(15, botao(Material.CHEST, "<gold><bold>Dar presente", "<gray>Dá o item da sua mão (1 unidade).",
                "<gray>Flores, comida, joias... cada profissão", "<gray>tem seus favoritos."), (pl, c) -> {
            pl.closeInventory();
            presente(pl, v);
        });
        boolean temLoja = v instanceof WanderingTrader || (v instanceof Villager vil && !crianca
                && !vil.getRecipes().isEmpty());
        m.item(16, botao(temLoja ? Material.EMERALD : Material.GRAY_DYE, temLoja ? "<dark_green><bold>Negociar" : "<gray><bold>Negociar",
                temLoja ? "<gray>Abre as trocas." : "<gray>Não tem nada para trocar."), (pl, c) -> {
            if (!temLoja) {
                falar(pl, v, "Não tenho nada pra vender agora, desculpa.");
                return;
            }
            pl.closeInventory();
            pl.openMerchant(v, true);
        });

        m.item(20, botao(Material.WRITABLE_BOOK, "<yellow><bold>Pedir trabalho", "<gray>Ele(a) pede uma entrega ou caçada.",
                "<gray>Paga em dinheiro, amizade e reputação.", "<gray>Já tem um? Clique para entregar."), (pl, c) -> {
            pl.closeInventory();
            trabalho(pl, v);
        });
        m.item(21, botao(Material.FILLED_MAP, "<aqua><bold>Notícias e boatos", "<gray>Guerras, exércitos, lugares e fofocas."),
                (pl, c) -> {
                    pl.closeInventory();
                    noticias(pl, v);
                });
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        if (pdc.has(kSeguindo) || pdc.has(kParado)) {
            m.item(22, botao(Material.LEAD, "<white><bold>Liberar", "<gray>Volta à vida normal."), (pl, c) -> {
                pl.closeInventory();
                liberar(pl, v);
            });
            if (pdc.has(kSeguindo)) {
                m.item(23, botao(Material.OAK_SIGN, "<white><bold>Ficar aqui", "<gray>Espera parado(a) neste lugar."), (pl, c) -> {
                    pl.closeInventory();
                    ficar(pl, v);
                });
            }
        } else {
            m.item(22, botao(Material.LEAD, "<white><bold>Me siga", "<gray>Precisa de amizade " + cfg.getInt("amizade-minima.seguir", 30) + "+."),
                    (pl, c) -> {
                        pl.closeInventory();
                        seguir(pl, v);
                    });
            m.item(23, botao(Material.OAK_SIGN, "<white><bold>Fique aqui", "<gray>Precisa de amizade " + cfg.getInt("amizade-minima.seguir", 30) + "+."),
                    (pl, c) -> {
                        pl.closeInventory();
                        ficar(pl, v);
                    });
        }
        if (!crianca) {
            if (casadoCom(v, p)) {
                m.item(24, botao(itemDivorcio(), "<dark_red><bold>Divorciar", "<gray>Shift + clique para confirmar.",
                        "<red>A amizade despenca."), (pl, c) -> {
                    if (c.isShiftClick()) {
                        pl.closeInventory();
                        divorciar(pl, v);
                    }
                });
            } else {
                m.item(24, botao(Material.DIAMOND, "<light_purple><bold>Pedir em casamento", "<gray>Precisa de amizade " + cfg.getInt("amizade-minima.casar", 90) + "+",
                        "<gray>e de um <aqua>diamante<gray> na mão (a aliança).", "<gray>Você só pode ter um cônjuge."), (pl, c) -> {
                    pl.closeInventory();
                    casar(pl, v);
                });
            }
        }
        m.item(31, botao(Material.BARRIER, "<red>Tchau!"), (pl, c) -> {
            pl.closeInventory();
            falar(pl, v, crianca ? "Tchau! Volta pra brincar!" : "Até mais, " + pl.getName() + "!");
        });
        m.preencher(Material.GRAY_STAINED_GLASS_PANE);
        m.abrir(p);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.3f, 1.4f);
    }

    /** O jogo não tem um item de "coração partido" simples em todas as versões: usa o caco de cerâmica se existir. */
    private static Material itemDivorcio() {
        Material m = Material.matchMaterial("HEARTBREAK_POTTERY_SHERD");
        return m != null ? m : Material.PAPER;
    }

    // ------------------------------------------------------------------ INTERAÇÕES

    private boolean perto(Player p, AbstractVillager v) {
        if (!v.isValid() || !v.getWorld().equals(p.getWorld()) || v.getLocation().distanceSquared(p.getLocation()) > 64) {
            p.sendMessage(Texto.cor("<red>Chegue mais perto de " + nomeCompleto(v) + "."));
            return false;
        }
        return true;
    }

    private boolean esperando(Player p, AbstractVillager v, String acao) {
        String k = v.getUniqueId() + "|" + p.getUniqueId() + "|" + acao;
        long agora = System.currentTimeMillis();
        Long ate = esperas.get(k);
        if (ate != null && ate > agora) {
            p.sendMessage(Texto.cor("<gray>" + nomeCompleto(v) + " ainda está pensando na última vez... <dark_gray>(espere "
                    + ((ate - agora) / 1000 + 1) + "s)"));
            return true;
        }
        esperas.put(k, agora + cfg.getLong("espera-interacao-segundos", 45) * 1000L);
        if (esperas.size() > 5000) {
            esperas.values().removeIf(t -> t < agora);
        }
        return false;
    }

    private void interagir(Player p, AbstractVillager v, String acao) {
        p.closeInventory();
        if (!perto(p, v) || esperando(p, v, acao)) {
            return;
        }
        Identidade id = identidade(v);
        Personalidade per = id.personalidade();
        int am = amizade(v, p.getUniqueId());
        boolean conjuge = casadoCom(v, p);
        double base;
        int ganho;
        int perda;
        String dito = null;
        String minimo = null;
        switch (acao) {
            case "piada" -> {
                base = 0.50 + per.piada;
                ganho = 4;
                perda = 3;
                dito = sortear("piadas");
            }
            case "elogio" -> {
                base = 0.60 + per.elogio;
                ganho = 3;
                perda = 2;
                dito = sortear("elogios");
            }
            case "abraco" -> {
                base = 0.65 + per.abraco;
                ganho = 4;
                perda = 5;
                minimo = "abracar";
            }
            case "flerte" -> {
                base = 0.45 + per.flerte;
                ganho = 6;
                perda = 6;
                minimo = "flertar";
                dito = sortear("cantadas");
                UUID meu = conjugeDoJogador(p);
                if (casado(v) && !conjuge) {
                    falar(p, v, "Sou casado(a)! Me respeite!");
                    mudarAmizade(v, p, -10);
                    efeito(v, false);
                    return;
                }
                if (meu != null && !conjuge) {
                    p.sendMessage(Texto.cor("<gray><italic>(Você é casado(a)... melhor não.)"));
                }
            }
            case "brincar" -> {
                base = 0.85;
                ganho = 4;
                perda = 1;
            }
            default -> {
                base = 0.72 + per.conversa;
                ganho = 3;
                perda = 1;
            }
        }
        if (dito != null && !dito.isBlank()) {
            p.sendMessage(Texto.cor("<gray>Você: <white><italic>\"" + dito + "\""));
        }
        boolean ok;
        if (conjuge) {
            ok = true;
        } else if (minimo != null && am < cfg.getInt("amizade-minima." + minimo, 20)) {
            ok = false;
            perda *= 2;
        } else {
            double chance = base + (humor(v) - 50) / 250.0 + am / 400.0;
            ok = ThreadLocalRandom.current().nextDouble() < Math.max(0.05, Math.min(0.97, chance));
        }
        String resposta = sortear("respostas." + acao + (ok ? "-ok" : "-falha"));
        if (acao.equals("conversar")) {
            resposta = ok ? conversaDeVerdade(p, v) : sortear("respostas.conversar-falha");
        }
        falar(p, v, resposta);
        mudarAmizade(v, p, ok ? ganho : -perda);
        mudarHumor(v, ok ? 3 : -4);
        efeito(v, ok);
    }

    /** Conversa boa: às vezes ele conta algo da profissão, da personalidade ou de onde mora. */
    private String conversaDeVerdade(Player p, AbstractVillager v) {
        int r = ThreadLocalRandom.current().nextInt(4);
        String l = switch (r) {
            case 0 -> sortear("falas.profissao." + chaveProfissao(v));
            case 1 -> sortear("falas.personalidade." + identidade(v).personalidade().chave());
            default -> sortear("respostas.conversar-ok");
        };
        return l.isBlank() ? sortear("respostas.conversar-ok") : l;
    }

    private void efeito(AbstractVillager v, boolean bom) {
        Location l = v.getLocation().add(0, v.getHeight() + 0.4, 0);
        v.getWorld().spawnParticle(bom ? Particle.HEART : Particle.ANGRY_VILLAGER, l, bom ? 4 : 3, 0.3, 0.2, 0.3, 0);
        v.getWorld().playSound(l, bom ? Sound.ENTITY_VILLAGER_CELEBRATE : Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
    }

    /** O que o morador diz para o jogador: no chat dele e num balão em cima da cabeça. */
    public void falar(Player p, AbstractVillager v, String fala) {
        if (fala == null || fala.isBlank()) {
            return;
        }
        String f = Texto.substituir(fala, "jogador", p.getName(), "nome", identidade(v).nome());
        p.sendMessage(Texto.cor("<gold>" + nomeCompleto(v) + "<dark_gray> » <white>" + f));
        balao(v, f);
    }

    private void presente(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (mao.getType().isAir()) {
            p.sendMessage(Texto.cor("<yellow>Segure na mão o item que quer dar de presente."));
            return;
        }
        Material mat = mao.getType();
        String prof = chaveProfissao(v);
        int valor;
        ConfigurationSection amados = cfg.getConfigurationSection("presentes.amados");
        ConfigurationSection odiados = cfg.getConfigurationSection("presentes.odiados");
        if (amados != null && amados.contains(mat.name())) {
            valor = amados.getInt(mat.name());
        } else if (odiados != null && odiados.contains(mat.name())) {
            valor = odiados.getInt(mat.name());
        } else {
            valor = cfg.getInt("presentes.outros", 1);
        }
        if (valor > 0 && cfg.getStringList("presentes.favoritos." + prof).contains(mat.name())) {
            valor *= 2;
        }
        Personalidade per = identidade(v).personalidade();
        if (valor > 0 && (mat == Material.EMERALD || mat == Material.DIAMOND || mat == Material.GOLD_INGOT)) {
            valor = (int) Math.round(valor * per.ganancia);
        }
        String k = v.getUniqueId() + "|" + p.getUniqueId() + "|" + mat;
        long agora = System.currentTimeMillis();
        if (presentesRecentesEm.getOrDefault(k, 0L) < agora - 20 * 60_000L) {
            presentesRecentes.remove(k);
        }
        int repetidos = presentesRecentes.merge(k, 1, Integer::sum) - 1;
        presentesRecentesEm.put(k, agora);
        if (valor > 0 && repetidos > 0) {
            valor = Math.max(0, valor / (1 + repetidos));
        }
        mao.setAmount(mao.getAmount() - 1);
        String chave = repetidos >= 2 && valor >= 0 ? "presente-repetido" : valor >= 8 ? "presente-amou" : valor >= 2 ? "presente-gostou"
                : valor >= 0 ? "presente-tanto-faz" : "presente-odiou";
        falar(p, v, sortear("respostas." + chave));
        mudarAmizade(v, p, valor);
        mudarHumor(v, valor > 0 ? 5 : -10);
        efeito(v, valor >= 0);
    }

    private void casar(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        if (crianca(v)) {
            return;
        }
        if (casadoCom(v, p)) {
            falar(p, v, "Já somos casados, bobo(a)! ❤");
            return;
        }
        if (conjugeDoJogador(p) != null) {
            p.sendMessage(Texto.cor("<red>Você já é casado(a) com " + p.getPersistentDataContainer().getOrDefault(jConjugeNome,
                    PersistentDataType.STRING, "alguém") + ". Divorcie antes (no menu do seu cônjuge)."));
            return;
        }
        if (casado(v)) {
            falar(p, v, "Desculpa... eu já sou casado(a).");
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (mao.getType() != Material.DIAMOND) {
            p.sendMessage(Texto.cor("<yellow>Segure um <aqua>diamante <yellow>na mão (a aliança) para pedir em casamento."));
            return;
        }
        if (amizade(v, p.getUniqueId()) < cfg.getInt("amizade-minima.casar", 90)) {
            falar(p, v, sortear("respostas.casar-nao"));
            mudarAmizade(v, p, -5);
            efeito(v, false);
            return;
        }
        mao.setAmount(mao.getAmount() - 1);
        String nome = nomeCompleto(v);
        v.getPersistentDataContainer().set(kConjuge, PersistentDataType.STRING, p.getUniqueId().toString());
        v.getPersistentDataContainer().set(kConjugeNome, PersistentDataType.STRING, p.getName());
        p.getPersistentDataContainer().set(jConjuge, PersistentDataType.STRING, v.getUniqueId().toString());
        p.getPersistentDataContainer().set(jConjugeNome, PersistentDataType.STRING, nome);
        falar(p, v, sortear("respostas.casar-sim"));
        mudarAmizade(v, p, 10);
        mudarHumor(v, 40);
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.sendMessage(Texto.cor("<light_purple>💍 <white>" + p.getName() + " <light_purple>se casou com <white>" + nome + "<light_purple>! Felicidades ao casal!"));
        }
        Location l = v.getLocation().add(0, 1, 0);
        v.getWorld().spawnParticle(Particle.HEART, l, 30, 1, 1, 1, 0);
        Firework fw = v.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            FireworkMeta fm = f.getFireworkMeta();
            fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.FUCHSIA, Color.WHITE)
                    .withFade(Color.RED).flicker(true).build());
            fm.setPower(0);
            f.setFireworkMeta(fm);
        });
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private void divorciar(Player p, AbstractVillager v) {
        v.getPersistentDataContainer().remove(kConjuge);
        v.getPersistentDataContainer().remove(kConjugeNome);
        p.getPersistentDataContainer().remove(jConjuge);
        p.getPersistentDataContainer().remove(jConjugeNome);
        falar(p, v, "Então é isso... adeus, " + p.getName() + ".");
        mudarAmizade(v, p, -60);
        mudarHumor(v, -50);
        efeito(v, false);
    }

    /** Uma vez por dia (do jogo real), o cônjuge dá um presentinho quando você vem falar com ele. */
    private void presenteDoConjuge(Player p, AbstractVillager v) {
        if (!casadoCom(v, p)) {
            return;
        }
        long dia = System.currentTimeMillis() / 86_400_000L;
        if (p.getPersistentDataContainer().getOrDefault(jPresenteDia, PersistentDataType.LONG, 0L) == dia) {
            return;
        }
        p.getPersistentDataContainer().set(jPresenteDia, PersistentDataType.LONG, dia);
        Material[] presentes = {Material.BREAD, Material.COOKED_BEEF, Material.CAKE, Material.GOLDEN_APPLE, Material.EMERALD,
                Material.POPPY, Material.COOKIE, Material.IRON_INGOT, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material m = presentes[r.nextInt(presentes.length)];
        int qtd = m == Material.CAKE || m == Material.GOLDEN_APPLE ? 1 : 1 + r.nextInt(4);
        for (ItemStack sobra : p.getInventory().addItem(new ItemStack(m, qtd)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
        falar(p, v, "Amor! Guardei isso pra você hoje. ❤");
        p.sendMessage(Texto.cor("<light_purple>Você ganhou <white>" + qtd + "x <lang:" + m.translationKey() + "> <light_purple>do seu cônjuge."));
    }

    // ------------------------------------------------------------------ SEGUIR / FICAR

    private void seguir(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        if (!casadoCom(v, p) && amizade(v, p.getUniqueId()) < cfg.getInt("amizade-minima.seguir", 30)) {
            falar(p, v, sortear("respostas.seguir-nao"));
            return;
        }
        v.getPersistentDataContainer().remove(kParado);
        v.getPersistentDataContainer().set(kSeguindo, PersistentDataType.STRING, p.getUniqueId().toString());
        v.setAware(false);
        falar(p, v, sortear("respostas.seguir-ok"));
    }

    private void ficar(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        if (!v.getPersistentDataContainer().has(kSeguindo) && !casadoCom(v, p)
                && amizade(v, p.getUniqueId()) < cfg.getInt("amizade-minima.seguir", 30)) {
            falar(p, v, sortear("respostas.seguir-nao"));
            return;
        }
        v.getPersistentDataContainer().remove(kSeguindo);
        v.getPersistentDataContainer().set(kParado, PersistentDataType.BYTE, (byte) 1);
        v.setAware(false);
        falar(p, v, sortear("respostas.ficar-ok"));
    }

    private void liberar(Player p, AbstractVillager v) {
        v.getPersistentDataContainer().remove(kSeguindo);
        v.getPersistentDataContainer().remove(kParado);
        v.setAware(true);
        falar(p, v, sortear("respostas.liberar-ok"));
    }

    /** Quem está seguindo um jogador anda atrás dele (passinhos do plugin) e teleporta se ficar muito para trás. */
    private void seguirTick() {
        for (LivingEntity c : corpos.cerebros(this)) {
            String s = c.getPersistentDataContainer().get(kSeguindo, PersistentDataType.STRING);
            if (s == null) {
                continue;
            }
            Player p;
            try {
                p = Bukkit.getPlayer(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                p = null;
            }
            if (p == null || !p.getWorld().equals(c.getWorld()) || !quer(c)) {
                continue; // espera o dono voltar
            }
            double d = p.getLocation().distanceSquared(c.getLocation());
            if (d > 24 * 24) {
                Location atras = p.getLocation().clone().subtract(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                c.teleport(NpcManager.lugarSeguro(atras, 3));
            } else if (d > 2.5 * 2.5) {
                Movimento.passo(c, p.getLocation(), d > 64 ? 0.42 : 0.24, 3);
            } else if (d < 9) {
                Movimento.olhar(c, p.getEyeLocation());
            }
        }
    }

    // ------------------------------------------------------------------ TRABALHOS

    private record Trabalho(String tipo, String alvo, int quantidade, int progresso, String morador, String nomeMorador, long expira) {
        String gravar() {
            return tipo + ";" + alvo + ";" + quantidade + ";" + progresso + ";" + morador + ";" + nomeMorador + ";" + expira;
        }

        static Trabalho ler(String s) {
            if (s == null) {
                return null;
            }
            String[] p = s.split(";", 7);
            if (p.length < 7) {
                return null;
            }
            try {
                return new Trabalho(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], p[5], Long.parseLong(p[6]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String descricao() {
            if (tipo.equals("entrega")) {
                Material m = Material.matchMaterial(alvo);
                return "Entregar <white>" + quantidade + "x <lang:" + (m == null ? "item.minecraft.paper" : m.translationKey()) + "><gray> para <white>" + nomeMorador;
            }
            EntityType t = tipoEntidade(alvo);
            return "Caçar <white>" + quantidade + "x <lang:" + (t == null ? "entity.minecraft.zombie" : t.translationKey()) + "><gray> ("
                    + progresso + "/" + quantidade + ") para <white>" + nomeMorador;
        }
    }

    private static EntityType tipoEntidade(String s) {
        try {
            return EntityType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Trabalho trabalhoDe(Player p) {
        Trabalho t = Trabalho.ler(p.getPersistentDataContainer().get(jTrabalho, PersistentDataType.STRING));
        if (t != null && t.expira() < System.currentTimeMillis()) {
            p.getPersistentDataContainer().remove(jTrabalho);
            p.sendMessage(Texto.cor("<gray>Seu trabalho para " + t.nomeMorador() + " expirou."));
            return null;
        }
        return t;
    }

    /** /trabalho: mostra o trabalho atual. */
    public void mostrarTrabalho(Player p) {
        Trabalho t = trabalhoDe(p);
        if (t == null) {
            p.sendMessage(Texto.cor("<gray>Você não tem nenhum trabalho. Fale com um morador e escolha <yellow>Pedir trabalho<gray>."));
            return;
        }
        p.sendMessage(Texto.cor("<gold><bold>Trabalho atual: <gray>" + t.descricao()));
        p.sendMessage(Texto.cor("<gray>Expira em " + Math.max(1, (t.expira() - System.currentTimeMillis()) / 3_600_000L)
                + "h. <dark_gray>(/trabalho abandonar para desistir)"));
    }

    public void abandonarTrabalho(Player p) {
        if (trabalhoDe(p) == null) {
            p.sendMessage(Texto.cor("<gray>Você não tem trabalho para abandonar."));
            return;
        }
        p.getPersistentDataContainer().remove(jTrabalho);
        p.sendMessage(Texto.cor("<yellow>Trabalho abandonado."));
    }

    private void trabalho(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        Trabalho t = trabalhoDe(p);
        String nome = nomeCompleto(v);
        if (t != null) {
            if (!t.morador().equals(v.getUniqueId().toString())) {
                falar(p, v, "Você já está trabalhando para " + t.nomeMorador() + ". Termine aquilo primeiro!");
                return;
            }
            if (t.tipo().equals("entrega")) {
                Material m = Material.matchMaterial(t.alvo());
                if (m == null || !p.getInventory().containsAtLeast(new ItemStack(m), t.quantidade())) {
                    falar(p, v, "Ainda estou esperando " + t.quantidade() + " daquilo que pedi...");
                    p.sendMessage(Texto.cor("<gray>" + t.descricao()));
                    return;
                }
                p.getInventory().removeItem(new ItemStack(m, t.quantidade()));
            } else if (t.progresso() < t.quantidade()) {
                falar(p, v, "Ainda faltam " + (t.quantidade() - t.progresso()) + "! Volte quando terminar.");
                return;
            }
            pagar(p, v, t);
            return;
        }
        if (crianca(v)) {
            falar(p, v, "Eu sou criança, não tenho trabalho pra dar! Hihi.");
            return;
        }
        if (amizade(v, p.getUniqueId()) < cfg.getInt("amizade-minima.trabalho", -20)) {
            falar(p, v, "Não confio em você pra isso.");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Trabalho novo;
        if (r.nextInt(100) < 60) {
            List<?> lista = cfg.getList("trabalhos.entregas." + chaveProfissao(v));
            if (lista == null || lista.isEmpty()) {
                lista = cfg.getList("trabalhos.entregas.padrao", List.of());
            }
            if (lista.isEmpty()) {
                falar(p, v, "Agora não preciso de nada, obrigado.");
                return;
            }
            List<?> par = (List<?>) lista.get(r.nextInt(lista.size()));
            novo = new Trabalho("entrega", String.valueOf(par.get(0)).toUpperCase(Locale.ROOT), Integer.parseInt(String.valueOf(par.get(1))), 0,
                    v.getUniqueId().toString(), nome, System.currentTimeMillis() + 48 * 3_600_000L);
        } else {
            List<?> lista = cfg.getList("trabalhos.cacadas", List.of());
            if (lista.isEmpty()) {
                falar(p, v, "Agora não preciso de nada, obrigado.");
                return;
            }
            List<?> par = (List<?>) lista.get(r.nextInt(lista.size()));
            novo = new Trabalho("caca", String.valueOf(par.get(0)).toUpperCase(Locale.ROOT), Integer.parseInt(String.valueOf(par.get(1))), 0,
                    v.getUniqueId().toString(), nome, System.currentTimeMillis() + 48 * 3_600_000L);
        }
        p.getPersistentDataContainer().set(jTrabalho, PersistentDataType.STRING, novo.gravar());
        double pagamento = valorTrabalho(novo);
        falar(p, v, novo.tipo().equals("entrega") ? "Você pode me ajudar? Preciso que me traga umas coisas." : "Os monstros andam atacando por aqui... pode dar um jeito?");
        p.sendMessage(Texto.cor("<gold><bold>Novo trabalho: <gray>" + novo.descricao() + " <dark_gray>| <green>$" + Texto.dinheiro(pagamento)));
        p.sendMessage(Texto.cor("<gray>Volte e clique em <yellow>Pedir trabalho <gray>para entregar. <dark_gray>(/trabalho)"));
    }

    private double valorTrabalho(Trabalho t) {
        double base = cfg.getDouble("trabalhos.recompensa-base", 60);
        return base + t.quantidade() * (t.tipo().equals("entrega") ? cfg.getDouble("trabalhos.recompensa-por-item", 6)
                : cfg.getDouble("trabalhos.recompensa-por-monstro", 18));
    }

    private void pagar(Player p, AbstractVillager v, Trabalho t) {
        p.getPersistentDataContainer().remove(jTrabalho);
        double valor = valorTrabalho(t);
        if (plugin.economia().ativa()) {
            plugin.economia().depositar(p, valor);
        }
        p.giveExp(cfg.getInt("trabalhos.xp", 25));
        falar(p, v, "Muito obrigado, " + p.getName() + "! Aqui está o combinado.");
        p.sendMessage(Texto.cor("<green><bold>Trabalho concluído! <gray>+<green>$" + Texto.dinheiro(valor)));
        mudarAmizade(v, p, cfg.getInt("trabalhos.amizade", 12));
        mudarHumor(v, 10);
        var reino = plugin.reinos() == null ? null : plugin.reinos().reinoEm(v.getLocation());
        Faccao f = reino == null ? null : plugin.faccoes().obter(reino.id());
        if (f != null) {
            plugin.faccoes().alterarReputacao(p, f, cfg.getInt("trabalhos.reputacao", 10));
        }
        efeito(v, true);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMatarParaTrabalho(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        Trabalho t = Trabalho.ler(p.getPersistentDataContainer().get(jTrabalho, PersistentDataType.STRING));
        if (t == null || !t.tipo().equals("caca") || t.progresso() >= t.quantidade() || !e.getEntityType().name().equals(t.alvo())) {
            return;
        }
        Trabalho n = new Trabalho(t.tipo(), t.alvo(), t.quantidade(), t.progresso() + 1, t.morador(), t.nomeMorador(), t.expira());
        p.getPersistentDataContainer().set(jTrabalho, PersistentDataType.STRING, n.gravar());
        p.sendActionBar(Texto.cor("<gold>Trabalho: <white>" + n.progresso() + "/" + n.quantidade()
                + (n.progresso() >= n.quantidade() ? " <green>- volte para " + n.nomeMorador() + "!" : "")));
    }

    // ------------------------------------------------------------------ NOTÍCIAS

    private void noticias(Player p, AbstractVillager v) {
        if (!perto(p, v)) {
            return;
        }
        List<String> l = new ArrayList<>();
        var reinos = plugin.reinos();
        var reino = reinos == null ? null : reinos.reinoEm(v.getLocation());
        String vila = reinos == null ? null : reinos.vilaEm(v.getLocation());
        if (vila != null && reino != null) {
            l.add("Aqui é " + Texto.semCor(vila) + ", terra do " + Texto.semCor(reino.nome()) + ".");
        } else if (reino != null) {
            l.add("Estamos nas terras do " + Texto.semCor(reino.nome()) + ".");
        }
        for (Guerra g : plugin.guerras().ativas()) {
            Faccao a = plugin.faccoes().obter(g.a());
            Faccao b = plugin.faccoes().obter(g.b());
            String na = a == null ? g.a() : Texto.semCor(a.nome());
            String nb = b == null ? g.b() : Texto.semCor(b.nome());
            if (reino != null && g.envolve(reino.id())) {
                String inimigo = g.a().equals(reino.id()) ? nb : na;
                l.add("Estamos em guerra contra " + inimigo + "! Placar: " + g.pontosA() + " x " + g.pontosB() + ".");
            } else {
                l.add("Dizem que " + na + " e " + nb + " estão em guerra.");
            }
        }
        if (reinos != null) {
            l.addAll(reinos.noticias(v.getLocation()));
        }
        if (localizador != null) {
            try {
                l.addAll(localizador.apply(p, v.getLocation()));
            } catch (RuntimeException ignorado) {
                // o SurvivalRPG pode ter desligado
            }
        }
        String boato = sortear("boatos");
        if (!boato.isBlank()) {
            l.add(boato);
        }
        if (l.isEmpty()) {
            l.add("Nada de novo por aqui... ainda bem!");
        }
        p.sendMessage(Texto.cor("<gold>" + nomeCompleto(v) + " <gray>conta as novidades:"));
        int n = 0;
        for (String s : l) {
            if (n++ >= 6) {
                break;
            }
            p.sendMessage(Texto.cor("<dark_gray> • <white>" + s));
        }
        balao(v, l.get(0));
        mudarAmizade(v, p, 1);
    }

    // ------------------------------------------------------------------ VIDA DE FUNDO

    private void balao(AbstractVillager v, String texto) {
        Mannequin corpo = corpos.corpoDe(v);
        if (corpo == null || texto == null || texto.isBlank()) {
            return;
        }
        TextDisplay antigo = baloes.remove(v.getUniqueId());
        if (antigo != null) {
            antigo.remove();
        }
        String curto = texto.length() > 90 ? texto.substring(0, 87) + "..." : texto;
        TextDisplay t = corpo.getWorld().spawn(corpo.getLocation().add(0, 2.4, 0), TextDisplay.class, d -> {
            d.text(Texto.cor("<white>" + curto));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.setLineWidth(150);
            d.setBackgroundColor(Color.fromARGB(150, 20, 20, 20));
            d.setTransformation(new Transformation(new Vector3f(0, 0.75f, 0), new AxisAngle4f(), new Vector3f(0.85f, 0.85f, 0.85f), new AxisAngle4f()));
        });
        if (!corpo.addPassenger(t)) {
            t.teleport(corpo.getLocation().add(0, 2.6, 0));
        }
        baloes.put(v.getUniqueId(), t);
        balaoAte.put(v.getUniqueId(), System.currentTimeMillis() + (long) (cfg.getDouble("balao-segundos", 5) * 1000));
    }

    private Player jogadorMaisPerto(Entity e, double raio) {
        Player melhor = null;
        double d = raio * raio;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double x = p.getLocation().distanceSquared(e.getLocation());
            if (x < d) {
                d = x;
                melhor = p;
            }
        }
        return melhor;
    }

    private void viver() {
        long agora = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : List.copyOf(balaoAte.entrySet())) {
            if (e.getValue() < agora) {
                balaoAte.remove(e.getKey());
                TextDisplay t = baloes.remove(e.getKey());
                if (t != null) {
                    t.remove();
                }
            }
        }
        if (!ativo()) {
            return;
        }
        double raio = cfg.getDouble("distancia-ativa", 40);
        double cumprimento = cfg.getDouble("distancia-cumprimento", 4);
        long esperaCumprimento = cfg.getLong("cumprimentar-a-cada-minutos", 6) * 60_000L;
        long intervalo = cfg.getLong("falar-a-cada-segundos", 45) * 1000L;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (LivingEntity c : corpos.cerebros(this)) {
            if (!(c instanceof AbstractVillager v) || !v.isValid()) {
                continue;
            }
            Player perto = jogadorMaisPerto(v, raio);
            if (perto == null) {
                continue;
            }
            // cresceu ou mudou de profissão: troca de roupa
            boolean eraCrianca = v.getPersistentDataContainer().getOrDefault(kCrianca, PersistentDataType.BYTE, (byte) 0) == 1;
            String prof = v.getPersistentDataContainer().get(kProfissao, PersistentDataType.STRING);
            if (eraCrianca != crianca(v) || !chaveProfissao(v).equals(prof)) {
                corpos.revestir(v);
                continue;
            }
            Mannequin corpo = corpos.corpoDe(v);
            if (corpo == null) {
                continue;
            }
            boolean dormindo = v instanceof Villager vil && vil.isSleeping();
            if (dormindo) {
                continue;
            }
            // perigo: monstro perto
            if (agora >= proximoPerigo.getOrDefault(v.getUniqueId(), 0L)) {
                for (Entity en : v.getNearbyEntities(8, 4, 8)) {
                    if (en instanceof Monster && !corpos.temCorpo(en)) {
                        balao(v, sortear("falas.perigo"));
                        proximoPerigo.put(v.getUniqueId(), agora + 8000);
                        proximaFala.put(v.getUniqueId(), agora + 6000);
                        break;
                    }
                }
            }
            // cumprimentar quem passa
            for (Player p : v.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.getLocation().distanceSquared(v.getLocation()) > cumprimento * cumprimento) {
                    continue;
                }
                String k = v.getUniqueId() + "|" + p.getUniqueId();
                if (cumprimentos.getOrDefault(k, 0L) > agora) {
                    continue;
                }
                cumprimentos.put(k, agora + esperaCumprimento);
                int am = amizade(v, p.getUniqueId());
                String chave = casadoCom(v, p) ? "cumprimento-conjuge" : am <= -40 ? "cumprimento-inimigo" : am >= 15 ? "cumprimento-amigo"
                        : "cumprimento-desconhecido";
                String vila = plugin.reinos() == null ? null : plugin.reinos().vilaEm(v.getLocation());
                falar(p, v, Texto.substituir(sortear("falas." + chave), "vila", vila == null ? "nossa vila" : Texto.semCor(vila)));
                Movimento.olhar(v, p.getEyeLocation());
                proximaFala.put(v.getUniqueId(), agora + 8000);
            }
            if (r.nextInt(100) < 12 && !chaveProfissao(v).equals("none") && !crianca(v)) {
                corpo.swingMainHand(); // trabalhando
            }
            if (v.getWorld().hasStorm() && r.nextInt(30) == 0) {
                mudarHumor(v, -1);
            } else if (r.nextInt(60) == 0) {
                mudarHumor(v, humor(v) < 60 ? 1 : -1);
            }
            long prox = proximaFala.computeIfAbsent(v.getUniqueId(), k -> agora + r.nextLong(5000, intervalo + 1));
            if (agora < prox) {
                continue;
            }
            proximaFala.put(v.getUniqueId(), agora + (long) (intervalo * (0.6 + r.nextDouble() * 0.8)));
            // conversa com outro morador ao lado
            if (r.nextInt(100) < 35) {
                AbstractVillager outro = null;
                for (Entity en : v.getNearbyEntities(4, 2, 4)) {
                    if (en instanceof AbstractVillager ov && ehMorador(ov) && !(ov instanceof Villager ovl && ovl.isSleeping())) {
                        outro = ov;
                        break;
                    }
                }
                List<?> conversas = cfg.getList("falas.conversas", List.of());
                if (outro != null && !conversas.isEmpty() && conversas.get(r.nextInt(conversas.size())) instanceof List<?> par && par.size() >= 2) {
                    AbstractVillager b = outro;
                    Movimento.olhar(v, b.getEyeLocation());
                    Movimento.olhar(b, v.getEyeLocation());
                    balao(v, String.valueOf(par.get(0)));
                    proximaFala.put(b.getUniqueId(), agora + intervalo);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (b.isValid()) {
                            balao(b, String.valueOf(par.get(1)));
                        }
                    }, 50L);
                    continue;
                }
            }
            balao(v, falaSolta(v));
        }
    }

    private String falaSolta(AbstractVillager v) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var reinos = plugin.reinos();
        var reino = reinos == null ? null : reinos.reinoEm(v.getLocation());
        if (reino != null && r.nextInt(100) < 30) {
            for (Guerra g : plugin.guerras().ativas()) {
                if (g.envolve(reino.id())) {
                    Faccao inimiga = plugin.faccoes().obter(g.inimiga(reino.id()));
                    return Texto.substituir(sortear("falas.guerra"), "reino", Texto.semCor(reino.nome()),
                            "inimigo", inimiga == null ? g.inimiga(reino.id()) : Texto.semCor(inimiga.nome()));
                }
            }
        }
        int escolha = r.nextInt(100);
        if (v.getWorld().hasStorm() && escolha < 30) {
            return sortear("falas.chuva");
        }
        if (escolha < 40) {
            String l = sortear("falas.profissao." + chaveProfissao(v));
            if (!l.isBlank()) {
                return l;
            }
        }
        if (escolha < 65) {
            String l = sortear("falas.personalidade." + identidade(v).personalidade().chave());
            if (!l.isBlank()) {
                return l;
            }
        }
        long t = v.getWorld().getTime();
        return sortear(t < 6000 ? "falas.manha" : t < 12000 ? "falas.tarde" : "falas.noite");
    }

    // ------------------------------------------------------------------ EVENTOS

    private static Player jogadorAtacante(Entity d) {
        if (d instanceof Player p) {
            return p;
        }
        if (d instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            if (s instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoApanhar(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof AbstractVillager v) || !ehMorador(v)) {
            return;
        }
        Player p = jogadorAtacante(e.getDamager());
        balao(v, sortear("falas.ferido"));
        mudarHumor(v, -20);
        if (p == null) {
            return;
        }
        mudarAmizade(v, p, -10);
        var reino = plugin.reinos() == null ? null : plugin.reinos().reinoEm(v.getLocation());
        Faccao f = reino == null ? null : plugin.faccoes().obter(reino.id());
        if (f != null) {
            long agora = System.currentTimeMillis();
            if (avisoReputacao.getOrDefault(p.getUniqueId(), 0L) < agora) {
                avisoReputacao.put(p.getUniqueId(), agora + 3000);
                plugin.faccoes().alterarReputacao(p, f, -cfg.getInt("reputacao-ao-bater", 5));
            }
            plugin.faccoes().marcarProcurado(p, f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof AbstractVillager v) || !quer(v) || !v.getPersistentDataContainer().has(kNome)) {
            return;
        }
        Identidade id = identidade(v);
        String prof = nomeProfissao(v, id.feminina());
        Player matador = v.getKiller();
        String msg = "<dark_gray>☠ <gray>" + id.completo() + " <dark_gray>(" + prof + ") <gray>morreu"
                + (matador != null ? " pelas mãos de <red>" + matador.getName() : "") + ".";
        for (Player p : v.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(v.getLocation()) < 64 * 64) {
                p.sendMessage(Texto.cor(msg));
            }
        }
        String conj = v.getPersistentDataContainer().get(kConjuge, PersistentDataType.STRING);
        if (conj != null) {
            falecidos.add(v.getUniqueId().toString());
            gravarDados();
            try {
                Player p = Bukkit.getPlayer(UUID.fromString(conj));
                if (p != null) {
                    viuvo(p);
                }
            } catch (IllegalArgumentException ignorado) {
                // uuid estragado
            }
        }
        if (matador != null) {
            var reino = plugin.reinos() == null ? null : plugin.reinos().reinoEm(v.getLocation());
            Faccao f = reino == null ? null : plugin.faccoes().obter(reino.id());
            if (f != null) {
                plugin.faccoes().alterarReputacao(matador, f, -40);
                plugin.faccoes().marcarProcurado(matador, f);
            }
        }
        TextDisplay t = baloes.remove(v.getUniqueId());
        if (t != null) {
            t.remove();
        }
    }

    private void viuvo(Player p) {
        String nome = p.getPersistentDataContainer().getOrDefault(jConjugeNome, PersistentDataType.STRING, "Seu cônjuge");
        p.getPersistentDataContainer().remove(jConjuge);
        p.getPersistentDataContainer().remove(jConjugeNome);
        p.sendMessage(Texto.cor("<dark_red>💔 <red>" + nome + ", seu cônjuge, morreu. Descanse em paz."));
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.3f, 1.6f);
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent e) {
        UUID c = conjugeDoJogador(e.getPlayer());
        if (c != null && falecidos.contains(c.toString())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> viuvo(e.getPlayer()), 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoNascer(EntityBreedEvent e) {
        if (!(e.getEntity() instanceof Villager filho) || !(e.getMother() instanceof Villager mae) || !(e.getFather() instanceof Villager pai)) {
            return;
        }
        String sobrenome = pai.getPersistentDataContainer().get(kSobrenome, PersistentDataType.STRING);
        if (sobrenome == null) {
            sobrenome = mae.getPersistentDataContainer().get(kSobrenome, PersistentDataType.STRING);
        }
        if (sobrenome != null) {
            filho.getPersistentDataContainer().set(kSobrenome, PersistentDataType.STRING, sobrenome);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!filho.isValid() || !quer(filho)) {
                return;
            }
            String msg = "<yellow>👶 Nasceu <white>" + nomeCompleto(filho) + "<yellow>, filho(a) de <white>" + nomeCompleto(mae)
                    + " <yellow>e <white>" + nomeCompleto(pai) + "<yellow>!";
            for (Player p : filho.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(filho.getLocation()) < 48 * 48) {
                    p.sendMessage(Texto.cor(msg));
                }
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMudarProfissao(VillagerCareerChangeEvent e) {
        Villager v = e.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (v.isValid() && corpos.temCorpo(v)) {
                corpos.revestir(v);
            }
        }, 2L);
    }

    // ------------------------------------------------------------------ GERADOR (staff)

    /** Cria moradores em volta de um ponto (profissão aleatória ou a pedida). Devolve quantos nasceram. */
    public int gerar(Location centro, int quantidade, String profissao) {
        World w = centro.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = 0;
        for (int i = 0; i < quantidade; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 1.5 + r.nextDouble() * Math.min(8, 1 + quantidade);
            Location l = NpcManager.lugarSeguro(centro.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist), 4);
            String prof = profissao != null ? profissao : PROFISSOES[r.nextInt(PROFISSOES.length)];
            w.spawn(l, Villager.class, v -> {
                Villager.Profession pr = org.bukkit.Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof.toLowerCase(Locale.ROOT)));
                if (pr != null) {
                    v.setProfession(pr);
                    v.setVillagerExperience(1); // não perde a profissão sem bancada
                }
                v.setPersistent(true);
                v.setRemoveWhenFarAway(false);
            });
            n++;
        }
        return n;
    }

    public static List<String> profissoes() {
        return List.of(PROFISSOES);
    }

    /** O morador mais perto do jogador (para os comandos da staff). */
    public AbstractVillager maisPerto(Player p, double raio) {
        AbstractVillager melhor = null;
        double d = raio * raio;
        for (Entity e : p.getNearbyEntities(raio, raio, raio)) {
            Entity real = corpos.real(e);
            if (real instanceof AbstractVillager v && ehMorador(v)) {
                double x = v.getLocation().distanceSquared(p.getLocation());
                if (x < d) {
                    d = x;
                    melhor = v;
                }
            }
        }
        return melhor;
    }

    public void definirAmizade(AbstractVillager v, Player p, int valor) {
        mudarAmizade(v, p, valor - amizade(v, p.getUniqueId()));
    }
}
