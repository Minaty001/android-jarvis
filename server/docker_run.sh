#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

IMAGE_NAME="jarvis-backend"
PORT="${PORT:-8000}"

echo "=== Building J.A.R.V.I.S. Docker Image ==="
docker build -t "$IMAGE_NAME" -f "$SCRIPT_DIR/Dockerfile" "$SCRIPT_DIR"

echo ""
echo "=== Running J.A.R.V.I.S. Backend in Docker ==="
echo "Access at: http://localhost:$PORT"
echo "Health:    http://localhost:$PORT/api/health"
echo "WebSocket: ws://localhost:$PORT/ws/jarvis"
echo ""

# Stop existing container if running
docker rm -f "$IMAGE_NAME" 2>/dev/null || true

exec docker run --rm -it \
  --name "$IMAGE_NAME" \
  -p "$PORT:$PORT" \
  -e PORT="$PORT" \
  -e GROQ_API_KEY="${GROQ_API_KEY:-}" \
  -e GEMINI_API_KEY="${GEMINI_API_KEY:-}" \
  -e OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-}" \
  -e JARVIS_AUTH_TOKEN="${JARVIS_AUTH_TOKEN:-}" \
  "$IMAGE_NAME"
