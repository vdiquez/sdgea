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

# MSYS_NO_PATHCONV: Git Bash en Windows reescribe rutas tipo /repo como si
# fueran rutas de host — esto evita que mangle las rutas internas del contenedor.
#
# .venv y .gradle van en volúmenes nombrados, NO en el bind mount del repo:
# son artefactos específicos de plataforma (venv de Linux, caché de Gradle) y
# compartirlos con el host los corrompe — ya pasó una vez (uv no podía borrar
# un symlink de Linux estando en Windows). Persisten entre corridas del loop,
# solo no se mezclan con lo que ve el host.
MSYS_NO_PATHCONV=1 docker run --rm -it \
    --name sgdea-agent-loop \
    -v "$REPO_ROOT:/repo" \
    -v sgdea-agent-venv:/repo/.venv \
    -v sgdea-agent-gradle:/repo/.gradle \
    -v "$CLAUDE_CREDS:/home/agent/.claude/.credentials.json:ro" \
    -v "$CLAUDE_CONFIG:/home/agent/.claude.json:ro" \
    -v "$CODEX_AUTH:/home/agent/.codex/auth.json:ro" \
    -w /repo \
    -e CONTAINED=1 \
    -e TEST_CMD \
    -e MAX_ITER \
    -e NTFY_TOPIC \
    sgdea-agent-sandbox:latest
