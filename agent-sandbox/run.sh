#!/usr/bin/env bash
# Arranca el sandbox del loop montando SOLO el repo (bind mount de trabajo) y
# los archivos mínimos de autenticación de tu sesión actual — no los
# directorios completos de ~/.claude o ~/.codex, que traen caché, historial y
# hasta un perfil de Chrome ajenos a la autenticación. Solo lectura.
set -euo pipefail

REPO_ROOT="$(CDPATH="" cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST_HOME="${HOME:?HOME no está definido}"

CLAUDE_CREDS="$HOST_HOME/.claude/.credentials.json"
CLAUDE_CONFIG="$HOST_HOME/.claude.json"
CODEX_AUTH="$HOST_HOME/.codex/auth.json"

for f in "$CLAUDE_CREDS" "$CLAUDE_CONFIG" "$CODEX_AUTH"; do
    [ -f "$f" ] || { echo "Falta $f — inicia sesión en el host primero (claude /login, codex login)." >&2; exit 1; }
done

# .venv y .gradle van en carpetas del host FUERA del repo, NO en un volumen
# nombrado de Docker: los volúmenes nombrados se crean root:root la primera
# vez que se montan y el usuario 'agent' no puede escribir ahí (mismo bug que
# tuvo /home/agent/.codex — Gradle fallaba con "Cannot create directory" en
# CADA iteración del loop, y el watchdog de tests-en-rojo se disparó por eso,
# no por el código). Un bind mount a una carpeta del host sí funciona sin
# líos de permisos (igual que /repo), y tampoco se mezcla con el .venv/.gradle
# del host porque vive en una ruta aparte.
SANDBOX_STATE="$HOST_HOME/.sgdea-agent-sandbox"
mkdir -p "$SANDBOX_STATE/venv" "$SANDBOX_STATE/gradle"

# MSYS_NO_PATHCONV: Git Bash en Windows reescribe rutas tipo /repo como si
# fueran rutas de host — esto evita que mangle las rutas internas del contenedor.
MSYS_NO_PATHCONV=1 docker run --rm -it \
    --name sgdea-agent-loop \
    -v "$REPO_ROOT:/repo" \
    -v "$SANDBOX_STATE/venv:/repo/.venv" \
    -v "$SANDBOX_STATE/gradle:/repo/.gradle" \
    -v "$CLAUDE_CREDS:/home/agent/.claude/.credentials.json:ro" \
    -v "$CLAUDE_CONFIG:/home/agent/.claude.json:ro" \
    -v "$CODEX_AUTH:/home/agent/.codex/auth.json:ro" \
    -w /repo \
    -e CONTAINED=1 \
    -e TEST_CMD \
    -e MAX_ITER \
    -e NTFY_TOPIC \
    sgdea-agent-sandbox:latest
