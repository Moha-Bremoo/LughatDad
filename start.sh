#!/bin/bash
# ─────────────────────────────────────────────────────────────────
#  start.sh  —  لغة ضاد (LughatDad) Startup Script
#  Compiles LughatDad.jj via JavaCC and starts the Java HTTP server.
#  Then opens http://localhost:5050 in your browser.
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
LOG_FILE="/tmp/lughatdad-server.log"

echo ""
echo "  ┌────────────────────────────────────────────────┐"
echo "  │   لغة ضاد — LughatDad                          │"
echo "  │   Backend: LughatDad.jj (JavaCC 7.0.13)        │"
echo "  │   URL:     http://localhost:5050                │"
echo "  └────────────────────────────────────────────────┘"
echo ""

# ── Check Java ───────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo "❌  Java not found. Please install Java 11+ from https://adoptium.net"
  exit 1
fi
echo "✅  Java: $(java -version 2>&1 | head -1)"

# ── Kill any existing server on port 5050 ────────────────────────
EXISTING=$(lsof -ti:5050 2>/dev/null)
if [ -n "$EXISTING" ]; then
  echo "⚠️   Stopping existing server (PID $EXISTING)..."
  kill -9 $EXISTING 2>/dev/null
  sleep 0.5
fi

# ── Compile if .class files are missing or .jj is newer ─────────
if [ ! -f "$BACKEND_DIR/LughatDadServer.class" ] || \
   [ "$BACKEND_DIR/LughatDad.jj" -nt "$BACKEND_DIR/LughatDadServer.class" ]; then
  echo "⚙️   Compiling Java backend from LughatDad.jj..."
  cd "$BACKEND_DIR" || exit 1
  # Generate Java from .jj using bundled JavaCC
  java -cp javacc.jar org.javacc.parser.Main LughatDad.jj 2>&1
  if [ $? -ne 0 ]; then echo "❌  JavaCC generation failed."; exit 1; fi
  # Compile all generated Java + server wrapper
  javac -encoding UTF-8 *.java 2>&1
  if [ $? -ne 0 ]; then echo "❌  javac compilation failed."; exit 1; fi
  echo "✅  Compilation successful."
fi

# ── Start server with nohup so it stays alive ───────────────────
echo "🚀  Starting server..."
cd "$BACKEND_DIR" || exit 1
nohup java -cp . LughatDadServer > "$LOG_FILE" 2>&1 &
SERVER_PID=$!
echo "    PID: $SERVER_PID  |  Log: $LOG_FILE"

# ── Wait for server to be ready ─────────────────────────────────
echo -n "    Waiting for server"
for i in $(seq 1 20); do
  sleep 0.4
  echo -n "."
  if curl -sf http://localhost:5050/api/health >/dev/null 2>&1; then
    echo " ready!"
    break
  fi
done
echo ""

# ── Open browser ────────────────────────────────────────────────
echo "🌐  Opening http://localhost:5050 in your browser..."
open "http://localhost:5050" 2>/dev/null || \
  xdg-open "http://localhost:5050" 2>/dev/null || \
  echo "    Please open http://localhost:5050 manually."

echo ""
echo "  Server is running in the background."
echo "  To view logs:  tail -f $LOG_FILE"
echo "  To stop:       kill $SERVER_PID"
echo ""
