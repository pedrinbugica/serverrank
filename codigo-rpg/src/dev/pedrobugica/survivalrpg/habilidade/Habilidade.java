package dev.pedrobugica.survivalrpg.habilidade;

import dev.pedrobugica.survivalrpg.classe.Classe;

import java.util.ArrayList;
import java.util.List;

/** As habilidades de cada classe (3 por classe, liberadas nos níveis 1, 10 e 20). */
public enum Habilidade {
    GRITO_DE_GUERRA(Classe.GUERREIRO, "Grito de Guerra", 1, 15, 12, "Os monstros perto vêm atrás de você e você ganha defesa por 5s"),
    INVESTIDA(Classe.GUERREIRO, "Investida", 10, 20, 10, "Avança e derruba os monstros no caminho"),
    FURIA(Classe.GUERREIRO, "Fúria", 20, 35, 40, "Mais força e velocidade por 10s e recupera vida"),

    TIRO_TRIPLO(Classe.ARQUEIRO, "Tiro Triplo", 1, 15, 4, "Dispara 3 flechas em leque"),
    CHUVA_DE_FLECHAS(Classe.ARQUEIRO, "Chuva de Flechas", 10, 30, 15, "Faz chover flechas onde você mira"),
    SALTO_EVASIVO(Classe.ARQUEIRO, "Salto Evasivo", 20, 20, 8, "Pula para trás e fica rápido por 3s"),

    BOLA_DE_FOGO(Classe.MAGO, "Bola de Fogo", 1, 20, 4, "Explode em área pequena e queima"),
    ESCUDO_DE_GELO(Classe.MAGO, "Escudo de Gelo", 10, 30, 15, "Absorve dano e deixa lento quem bate em você"),
    TELEPORTE_CURTO(Classe.MAGO, "Teleporte Curto", 20, 25, 10, "Teleporta até 8 blocos para onde você olha"),

    LUZ_CURATIVA(Classe.CLERIGO, "Luz Curativa", 1, 20, 6, "Cura você e o grupo em volta"),
    BENCAO(Classe.CLERIGO, "Bênção", 10, 30, 20, "Regeneração e defesa para o grupo por 6s"),
    MARTELO_SAGRADO(Classe.CLERIGO, "Martelo Sagrado", 20, 35, 12, "Dano em área (dobrado em mortos-vivos)"),

    PASSO_SOMBRIO(Classe.LADINO, "Passo Sombrio", 1, 20, 10, "Fica invisível por 3s e o próximo golpe é crítico"),
    LAMINAS_VENENOSAS(Classe.LADINO, "Lâminas Venenosas", 10, 25, 14, "Os próximos 4 golpes envenenam"),
    BOMBA_DE_FUMACA(Classe.LADINO, "Bomba de Fumaça", 20, 30, 18, "Cega os monstros em volta e você fica rápido");

    private final Classe classe;
    private final String nome;
    private final int nivelMinimo;
    private final int mana;
    private final double recarga;
    private final String descricao;

    Habilidade(Classe classe, String nome, int nivelMinimo, int mana, double recarga, String descricao) {
        this.classe = classe;
        this.nome = nome;
        this.nivelMinimo = nivelMinimo;
        this.mana = mana;
        this.recarga = recarga;
        this.descricao = descricao;
    }

    public Classe classe() {
        return classe;
    }

    public String nome() {
        return nome;
    }

    public int nivelMinimo() {
        return nivelMinimo;
    }

    public int mana() {
        return mana;
    }

    public double recarga() {
        return recarga;
    }

    public String descricao() {
        return descricao;
    }

    public static List<Habilidade> daClasse(Classe c) {
        List<Habilidade> l = new ArrayList<>();
        for (Habilidade h : values()) {
            if (h.classe == c) {
                l.add(h);
            }
        }
        return l;
    }
}
