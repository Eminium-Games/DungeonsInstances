#!/bin/bash
# ============================================================
#  Serveur de test local Paper 1.20.1 + plugin DungeonInstances
#  Usage : ./run-test-server.sh
# ============================================================
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# --- 1. Java 17 ---
if [ -n "$JAVA17_HOME" ]; then
  export JAVA_HOME="$JAVA17_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! java -version 2>&1 | grep -q '17\.'; then
  echo "[WARN] Java 17 non detecte (requis pour 1.20.1). Version actuelle :"
  java -version
fi

# --- 2. Maven ---
if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERREUR] 'mvn' introuvable. Installez Maven 3.9+."
  exit 1
fi

# --- 3. Paper 1.20.1 build 196 ---
if [ ! -f "test-server/paper.jar" ]; then
  echo "[INFO] Telechargement de Paper 1.20.1 build 196..."
  curl -L -o test-server/paper.jar \
    "https://fill-data.papermc.io/v1/objects/234a9b32098100c6fc116664d64e36ccdb58b5b649af0f80bcccb08b0255eaea/paper-1.20.1-196.jar"
fi

# --- 4. Build plugin ---
echo "[INFO] Build du plugin..."
mvn clean package -DskipTests

# --- 5. Copie vers test-server/plugins ---
mkdir -p test-server/plugins
rm -f test-server/plugins/smp-position-saver-*.jar test-server/plugins/DungeonInstances*.jar
cp target/*.jar test-server/plugins/ 2>/dev/null || true
echo "[INFO] Plugin copie :"
ls -1 test-server/plugins/*.jar

# --- 6. Lancement ---
echo ""
echo "============================================================"
echo " Serveur de test : localhost:25565 (version 1.20.1)"
echo " Client : Minecraft 1.20.1 -> Multijoueur -> localhost:25565"
echo " Comptes de test deja OP : Fourty3000 (client 1), ItsRealNocturne (client 2)"
echo " Arret propre : 'stop'"
echo "============================================================"
cd "$ROOT/test-server"
exec java -Xms1G -Xmx2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -jar paper.jar nogui
