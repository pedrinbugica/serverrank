#!/bin/sh
# Recompila os plugins próprios no macOS/Linux e coloca os .jar na pasta plugins.
# Uso:  ./compilar.sh            (os três)
#       ./compilar.sh npcvivos   (só um: rankup, npcvivos ou rpg)
# Requer o JDK 25 (javac). Pode rodar com o servidor ligado: o .jar novo só vale depois de reiniciar.
set -e
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "javac nao encontrado. Instale o JDK 25: https://adoptium.net/temurin/releases/?version=25"
    exit 1
fi
if [ ! -d libraries ]; then
    echo "A pasta libraries ainda nao existe. Inicie o servidor uma vez antes de compilar."
    exit 1
fi

BASE_CP="$(find libraries -name '*.jar' | sort | tr '\n' ':')"
BASE_CP="$BASE_CP$(ls plugins/VaultUnlocked-*.jar plugins/PlaceholderAPI-*.jar | tr '\n' ':')"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# compilar <pasta-do-codigo> <jar-de-saida> [classpath extra]
compilar() {
    origem="$1"; destino="$2"; extra="$3"
    saida="$TMP/$(basename "$origem")"
    mkdir -p "$saida"
    find "$origem/src" -name '*.java' > "$TMP/fontes.txt"
    echo "Compilando $origem..."
    javac -nowarn -encoding UTF-8 --release 21 -d "$saida" -cp "$BASE_CP$extra" @"$TMP/fontes.txt"
    cp -R "$origem/recursos/." "$saida/"
    # Gera num arquivo temporário e troca de uma vez: o servidor ligado continua usando o jar antigo até reiniciar
    jar --create --file "$destino.novo" -C "$saida" .
    mv -f "$destino.novo" "$destino"
    echo "  -> $destino"
}

alvo="${1:-todos}"
case "$alvo" in
    rankup|todos) compilar codigo-fonte plugins/RankupSupremo-1.0.0.jar "" ;;
esac
case "$alvo" in
    npcvivos|todos) compilar codigo-npcvivos plugins/NpcVivos.jar "" ;;
esac
case "$alvo" in
    rpg|todos) compilar codigo-rpg plugins/SurvivalRPG.jar "plugins/NpcVivos.jar:$(ls plugins/GriefPrevention*.jar plugins/EssentialsX-*.jar plugins/multiverse-core-*.jar 2>/dev/null | tr '\n' ':')" ;;
esac
echo "Pronto. Reinicie o servidor (digite stop e ligue de novo) para aplicar."
