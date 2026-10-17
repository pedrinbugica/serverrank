package dev.pedrobugica.npcvivos.upgrade;

/**
 * Regras para subir um item PARA este nível (upgrades.yml, seção "niveis").
 *
 * @param chance     % de sucesso (antes do bônus de reputação)
 * @param dinheiro   custo em dinheiro (antes do desconto da loja)
 * @param pedra      pedra gasta (null = nenhuma)
 * @param quantidade quantas pedras
 * @param seFalhar   quantos níveis o item perde se falhar (0 = perde só o custo)
 * @param bonus      quanto este nível soma de dano (armas) ou defesa (armaduras), em %
 */
public record NivelUpgrade(int nivel, double chance, double dinheiro, Pedra pedra, int quantidade, int seFalhar,
                           double bonus) {
}
