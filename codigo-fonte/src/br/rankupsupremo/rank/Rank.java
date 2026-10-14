package br.rankupsupremo.rank;

import org.bukkit.Material;

import java.util.List;

/**
 * Um rank configurado em ranks.yml. "blocosNecessarios" é o requisito extra de blocos minerados (0 = nenhum).
 */
public record Rank(String id, int indice, String nome, String tag, double custo, Material icone, List<String> comandos,
                   long blocosNecessarios) {
}
