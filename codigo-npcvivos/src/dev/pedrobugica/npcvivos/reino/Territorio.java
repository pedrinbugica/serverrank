package dev.pedrobugica.npcvivos.reino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Um castelo ou uma vila no mapa, com o reino dono (que muda quando é conquistado).
 * Castelos têm o "reino original" (onde mora o rei) e o trono.
 */
public final class Territorio {

    public enum Tipo {
        CASTELO, VILA
    }

    private final String id;
    private final Tipo tipo;
    private String nome;
    private final String mundo;
    private final int x;
    private final int y;
    private final int z;
    private final int raio;
    private String dono;
    private String reinoOriginal;
    private double tronoX;
    private double tronoY;
    private double tronoZ;
    /** Cor antiga das bandeiras, quando o castelo mudou de dono com o chunk descarregado (pintar quando carregar). */
    private String pintarDe;

    public Territorio(String id, Tipo tipo, String nome, String mundo, int x, int y, int z, int raio) {
        this.id = id;
        this.tipo = tipo;
        this.nome = nome;
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.raio = raio;
    }

    public String id() {
        return id;
    }

    public Tipo tipo() {
        return tipo;
    }

    public String nome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String mundo() {
        return mundo;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int raio() {
        return raio;
    }

    public String dono() {
        return dono;
    }

    public void setDono(String dono) {
        this.dono = dono;
    }

    public String reinoOriginal() {
        return reinoOriginal;
    }

    public void setReinoOriginal(String reinoOriginal) {
        this.reinoOriginal = reinoOriginal;
    }

    public void setTrono(double x, double y, double z) {
        this.tronoX = x;
        this.tronoY = y;
        this.tronoZ = z;
    }

    public double tronoX() {
        return tronoX;
    }

    public double tronoY() {
        return tronoY;
    }

    public double tronoZ() {
        return tronoZ;
    }

    public Location trono() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, tronoX, tronoY, tronoZ);
    }

    public String pintarDe() {
        return pintarDe;
    }

    public void setPintarDe(String pintarDe) {
        this.pintarDe = pintarDe;
    }

    public Location centro() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public double distancia(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) {
            return Double.MAX_VALUE;
        }
        double dx = l.getX() - x;
        double dz = l.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean contem(Location l) {
        return distancia(l) <= raio;
    }

    public boolean carregado() {
        World w = Bukkit.getWorld(mundo);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }
}
