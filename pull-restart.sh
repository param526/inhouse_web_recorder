#!/bin/bash
# pull-restart.sh — Git pull and restart the server if changes were detected.
# Usage: ./pull-restart.sh [branch]
#   branch  optional branch name (default: current branch)

set -e
cd "$(dirname "$0")"

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
BEFORE=$(git rev-parse HEAD)

echo "[pull-restart] Pulling branch: $BRANCH ..."
git pull origin "$BRANCH"

AFTER=$(git rev-parse HEAD)

if [ "$BEFORE" = "$AFTER" ]; then
    echo "[pull-restart] No changes — server not restarted."
    exit 0
fi

echo "[pull-restart] Changes detected ($BEFORE -> $AFTER). Restarting server..."

# Kill existing server on port 4567 (cross-platform: works in Git Bash on Windows and Linux)
if command -v lsof &>/dev/null; then
    PID=$(lsof -ti :4567 2>/dev/null || true)
    if [ -n "$PID" ]; then
        echo "[pull-restart] Stopping old server (PID $PID)..."
        kill "$PID" 2>/dev/null || true
        sleep 2
    fi
elif command -v netstat &>/dev/null; then
    # Windows Git Bash / MSYS2
    PID=$(netstat -ano 2>/dev/null | grep ':4567 ' | grep 'LISTENING' | awk '{print $NF}' | head -1)
    if [ -n "$PID" ] && [ "$PID" != "0" ]; then
        echo "[pull-restart] Stopping old server (PID $PID)..."
        taskkill //PID "$PID" //F 2>/dev/null || true
        sleep 2
    fi
fi

echo "[pull-restart] Compiling and starting server..."
mvn compile exec:java &
SERVER_PID=$!
echo "[pull-restart] Server started (PID $SERVER_PID)."
