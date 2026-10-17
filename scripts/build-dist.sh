#!/usr/bin/env bash
# Builds the runnable JARs into dist/<platform>/.
#
# JavaFX contains native code, so a JAR only runs on the operating system it was built for.
# This script builds one pair of JARs per platform, so you can hand the right ones to anyone.
#
#   ./scripts/build-dist.sh                 # macOS (Apple silicon), Windows, Linux
#   ./scripts/build-dist.sh win mac         # only the platforms you name
#
# Platform names: mac-aarch64 (Apple silicon), mac (Intel Mac), win, linux

set -euo pipefail
cd "$(dirname "$0")/.."

# The project targets Java 21 (virtual threads). Use it when it is installed (macOS helper).
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    [ -n "$JAVA_HOME" ] && export JAVA_HOME
fi

PLATFORMS=("$@")
if [ ${#PLATFORMS[@]} -eq 0 ]; then
    PLATFORMS=(mac-aarch64 win linux)
fi

folder_for() {
    case "$1" in
        mac-aarch64) echo "macos-apple-silicon" ;;
        mac)         echo "macos-intel" ;;
        win)         echo "windows" ;;
        linux)       echo "linux" ;;
        *) echo "Unknown platform '$1' (use mac-aarch64, mac, win or linux)" >&2; exit 1 ;;
    esac
}

echo "==> Running all tests once"
mvn -q clean verify

rm -rf dist
for platform in "${PLATFORMS[@]}"; do
    folder="dist/$(folder_for "$platform")"
    echo "==> Building the $platform JARs into $folder"
    mvn -q clean package -DskipTests -Djavafx.platform="$platform"
    mkdir -p "$folder"
    cp TCPServer/target/TCPServer.jar TCPClient/target/TCPClient.jar "$folder/"
    cp TCPServer/src/main/resources/server.properties TCPClient/src/main/resources/client.properties "$folder/"
done

cat > dist/HOW-TO-RUN.txt <<'EOF'
Multi-Client Chat - how to run
==============================
Needs Java 21 or newer (check with: java -version).
Open the folder that matches your computer, then:

1. Start the server (one computer):
       java -jar TCPServer.jar
   Its port is set in server.properties (default 3000).

2. Start a client (as many as you like, on any computer that can reach the server):
       java -jar TCPClient.jar <ServerIPAddress> <PortNumber>
   for example:
       java -jar TCPClient.jar localhost 3000
   With no arguments the client reads the address from client.properties.

3. In the client, type a username and press "Join chat".
   Leave the username empty to join in READ-ONLY MODE.

Commands in the message box:  allUsers (who is online)   bye / end (leave)

You may see a JavaFX warning about an "unnamed module" in the terminal. It is harmless.
EOF

echo "==> Done:"
find dist -type f | sort
