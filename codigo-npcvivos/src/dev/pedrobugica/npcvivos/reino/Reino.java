package dev.pedrobugica.npcvivos.reino;

import org.bukkit.Color;
import org.bukkit.DyeColor;

import java.util.List;

/**
 * Um reino do reinos.yml. O id é também o id da facção (faccoes/&lt;id&gt;.yml).
 *
 * @param cor      tag de cor do MiniMessage (ex.: "&lt;gold&gt;")
 * @param bandeira cor das bandeiras e da lã do castelo
 */
public record Reino(String id, String nome, String curto, String cor, Color corCouro, DyeColor bandeira, String lema,
                    String rei, String reiSkin, List<String> skins, List<String> descricao) {
}
