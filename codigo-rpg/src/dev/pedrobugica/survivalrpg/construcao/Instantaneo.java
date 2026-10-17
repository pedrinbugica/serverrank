package dev.pedrobugica.survivalrpg.construcao;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * "Foto" de uma caixa do mundo tirada antes de construir, gravada em disco: /rpgadmin desfazer põe tudo de volta.
 * Guarda só os blocos (o conteúdo de baús e placas não entra; por isso as obras evitam lugares com coisas de jogador).
 */
public final class Instantaneo {

    private final String mundo;
    private final int x1;
    private final int y1;
    private final int z1;
    private final int sx;
    private final int sy;
    private final int sz;
    private final List<String> paleta;
    private final int[] blocos;

    private Instantaneo(String mundo, int x1, int y1, int z1, int sx, int sy, int sz, List<String> paleta, int[] blocos) {
        this.mundo = mundo;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.paleta = paleta;
        this.blocos = blocos;
    }

    /** Tira a foto a partir das fotos de chunk já carregadas (thread principal ou não, tanto faz). */
    public static Instantaneo tirar(Terreno t, int xa, int ya, int za, int xb, int yb, int zb) {
        World w = t.mundo();
        int x1 = Math.min(xa, xb);
        int z1 = Math.min(za, zb);
        int y1 = Math.max(w.getMinHeight(), Math.min(ya, yb));
        int y2 = Math.min(w.getMaxHeight() - 1, Math.max(ya, yb));
        int sx = Math.abs(xb - xa) + 1;
        int sz = Math.abs(zb - za) + 1;
        int sy = y2 - y1 + 1;
        List<String> paleta = new ArrayList<>();
        Map<String, Integer> idx = new HashMap<>();
        int[] blocos = new int[sx * sy * sz];
        int i = 0;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    String s = t.dados(x1 + x, y1 + y, z1 + z).getAsString();
                    Integer n = idx.get(s);
                    if (n == null) {
                        n = paleta.size();
                        paleta.add(s);
                        idx.put(s, n);
                    }
                    blocos[i++] = n;
                }
            }
        }
        return new Instantaneo(w.getName(), x1, y1, z1, sx, sy, sz, paleta, blocos);
    }

    public void gravar(File f) throws IOException {
        File pasta = f.getParentFile();
        if (pasta != null && !pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível criar " + pasta);
        }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f), 1 << 16))) {
            out.writeInt(1);
            out.writeUTF(mundo);
            out.writeInt(x1);
            out.writeInt(y1);
            out.writeInt(z1);
            out.writeInt(sx);
            out.writeInt(sy);
            out.writeInt(sz);
            out.writeInt(paleta.size());
            for (String s : paleta) {
                out.writeUTF(s);
            }
            for (int b : blocos) {
                // varint: quase sempre 1 byte
                int v = b;
                while ((v & ~0x7F) != 0) {
                    out.writeByte((v & 0x7F) | 0x80);
                    v >>>= 7;
                }
                out.writeByte(v);
            }
        }
    }

    public static Instantaneo ler(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(f), 1 << 16))) {
            in.readInt();
            String mundo = in.readUTF();
            int x1 = in.readInt();
            int y1 = in.readInt();
            int z1 = in.readInt();
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            int n = in.readInt();
            List<String> paleta = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                paleta.add(in.readUTF());
            }
            int[] blocos = new int[sx * sy * sz];
            for (int i = 0; i < blocos.length; i++) {
                int v = 0;
                int shift = 0;
                int b;
                do {
                    b = in.readUnsignedByte();
                    v |= (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0);
                blocos[i] = v;
            }
            return new Instantaneo(mundo, x1, y1, z1, sx, sy, sz, paleta, blocos);
        }
    }

    public String mundo() {
        return mundo;
    }

    /** Obra que devolve a caixa ao que era (só os blocos que mudaram). */
    public Obra restauracao(Terreno atual) {
        World w = Bukkit.getWorld(mundo);
        if (w == null) {
            throw new IllegalStateException("Mundo " + mundo + " não carregado");
        }
        Obra o = new Obra(w);
        BlockData[] cache = new BlockData[paleta.size()];
        int i = 0;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    int b = blocos[i++];
                    String antes = paleta.get(b);
                    if (!atual.dados(x1 + x, y1 + y, z1 + z).getAsString().equals(antes)) {
                        if (cache[b] == null) {
                            cache[b] = Bukkit.createBlockData(antes);
                        }
                        o.set(x1 + x, y1 + y, z1 + z, cache[b]);
                    }
                }
            }
        }
        return o;
    }

    public int x1() {
        return x1;
    }

    public int z1() {
        return z1;
    }

    public int x2() {
        return x1 + sx - 1;
    }

    public int z2() {
        return z1 + sz - 1;
    }
}
