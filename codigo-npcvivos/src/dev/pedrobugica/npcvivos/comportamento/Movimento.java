package dev.pedrobugica.npcvivos.comportamento;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * O manequim não tem a IA do jogo, então quem anda com ele é o plugin: a cada tick damos um
 * passinho na direção do alvo. Ele sobe um bloco, desce até três e não entra em parede nem em lava.
 */
public final class Movimento {

    private Movimento() {
    }

    /**
     * Dá um passo na direção do destino.
     *
     * @return true se conseguiu andar
     */
    public static boolean passo(Entity e, Location destino, double velocidade, int quedaMaxima) {
        Location atual = e.getLocation();
        if (destino.getWorld() == null || !destino.getWorld().equals(atual.getWorld())) {
            return false;
        }
        Vector dir = destino.toVector().subtract(atual.toVector()).setY(0);
        if (dir.lengthSquared() < 0.0004) {
            return false;
        }
        dir.normalize().multiply(velocidade);
        Location alvo = atual.clone().add(dir);
        Location pisavel = ajustarAoChao(alvo, quedaMaxima);
        if (pisavel == null) {
            // Travou de frente: tenta desviar para os lados (contorna o canto de uma parede)
            for (double giro : new double[]{Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2}) {
                Vector lado = dir.clone().rotateAroundY(giro);
                pisavel = ajustarAoChao(atual.clone().add(lado), quedaMaxima);
                if (pisavel != null) {
                    break;
                }
            }
            if (pisavel == null) {
                return false;
            }
        }
        pisavel.setDirection(destino.toVector().subtract(pisavel.toVector()).setY(0));
        pisavel.setPitch(0f);
        e.teleport(pisavel);
        return true;
    }

    /** Vira o NPC para olhar o alvo, sem andar. */
    public static void olhar(Entity e, Location alvo) {
        Location olho = e.getLocation().add(0, e.getHeight() * 0.9, 0);
        Vector dir = alvo.toVector().subtract(olho.toVector());
        if (dir.lengthSquared() > 0.01) {
            Location l = olho.clone().setDirection(dir);
            e.setRotation(l.getYaw(), Math.max(-40f, Math.min(40f, l.getPitch())));
        }
    }

    /**
     * Um lugar com chão firme em volta do centro, espalhando os pontos em círculo
     * (usado para invocar os ajudantes do chefe sem empilhar todos no mesmo bloco).
     * Se não achar chão, devolve o próprio centro.
     */
    public static Location aoLado(Location centro, double raio, int indice, int total) {
        double angulo = Math.PI * 2 * indice / Math.max(1, total);
        Location tentativa = centro.clone().add(Math.cos(angulo) * raio, 0, Math.sin(angulo) * raio);
        Location chao = ajustarAoChao(tentativa, 3);
        return chao == null ? centro.clone() : chao;
    }

    /**
     * Acha a altura em que o NPC pode ficar de pé nesse x/z: sobe até 1 bloco e desce até
     * quedaMaxima. Devolve null se não dá para andar para lá (parede, buraco fundo, lava).
     */
    private static Location ajustarAoChao(Location l, int quedaMaxima) {
        World w = l.getWorld();
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int base = l.getBlockY();
        for (int dy = 1; dy >= -quedaMaxima; dy--) {
            int y = base + dy;
            if (y < w.getMinHeight() || y + 1 >= w.getMaxHeight()) {
                continue;
            }
            Block chao = w.getBlockAt(x, y - 1, z);
            if (!chao.getType().isSolid() || perigoso(chao)) {
                continue;
            }
            if (!livre(w.getBlockAt(x, y, z)) || !livre(w.getBlockAt(x, y + 1, z))) {
                continue;
            }
            Location destino = l.clone();
            destino.setY(y);
            return destino;
        }
        return null;
    }

    private static boolean livre(Block b) {
        return b.isPassable() && !perigoso(b);
    }

    private static boolean perigoso(Block b) {
        return switch (b.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH,
                 CACTUS, POINTED_DRIPSTONE, POWDER_SNOW, WITHER_ROSE -> true;
            default -> false;
        };
    }
}
