#!/usr/bin/env bash
# =============================================================================
# test-run-claude.sh — prueba reproducible de run_claude() (orquestador.sh)
#
# Responde al VETO de Codex sobre el commit `4eb7497` ("loop: entrega la
# iteracion a Codex (autorrevision) cuando Claude agota su ventana de uso"):
# el commit modificaba run_claude()/cmd_loop() para devolver 0 (éxito), 2
# (rate limit sostenido -> handoff a Codex) o 1 (fallo genérico), y STATE.md
# afirmaba que los tres casos se habían verificado "con un claude simulado",
# pero esa verificación nunca quedó como una prueba reproducible en el
# commit. Este script es esa prueba: sustituye el binario `claude` real por
# un doble controlado (no amañado — obedece las mismas señales que
# is_rate_limited() ya usa contra la salida real de Claude Code: texto con
# "rate limit"/"429"/etc.) y verifica los tres códigos de retorno más el
# punto exacto del handoff (después de exactamente
# RATE_LIMIT_HANDOFF_ATTEMPTS intentos, ni antes ni después).
#
# No requiere red ni credenciales — corre en cualquier entorno, incluido CI.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# Solo definiciones de función + variables: cargamos orquestador.sh hasta
# antes de su dispatcher (case "$1" in ...), para no disparar cmd_* ni el
# "exit 1" del branch por defecto al hacer source.
DISPATCH_LINE="$(grep -n '^# -\+ dispatcher' orquestador.sh | head -1 | cut -d: -f1)"
ORQ_FUNCS="$(mktemp)"
head -n "$((DISPATCH_LINE - 1))" orquestador.sh > "$ORQ_FUNCS"

FAKE_CLAUDE_DIR="$(mktemp -d)"
CALL_LOG="$(mktemp)"

cleanup() { rm -rf "$ORQ_FUNCS" "$FAKE_CLAUDE_DIR" "$CALL_LOG"; }
trap cleanup EXIT

cat > "$FAKE_CLAUDE_DIR/claude" <<'EOF'
#!/usr/bin/env bash
# Doble de prueba de `claude`: NO amaña el resultado que run_claude() evalúa
# -- produce exactamente las señales que is_rate_limited() ya busca en la
# salida real de Claude Code (texto con "rate limit"/"429"/etc.), o un JSON
# de éxito con los campos que json_field()/run_claude() ya leen
# (session_id), o un fallo genérico sin esas señales.
echo "invoked" >> "${FAKE_CLAUDE_CALL_LOG:?}"
case "${FAKE_CLAUDE_MODE:-success}" in
  success)
    printf '{"session_id":"fake-session","subtype":"success","is_error":false,"result":"ok","usage":{"input_tokens":1,"output_tokens":1},"total_cost_usd":0}\n'
    exit 0 ;;
  ratelimit)
    echo "Error: rate limit exceeded (429), please retry later" >&2
    exit 1 ;;
  failure)
    echo "Error: unexpected internal crash, unrelated to throttling" >&2
    exit 1 ;;
  *)
    echo "test-run-claude.sh: FAKE_CLAUDE_MODE desconocido: ${FAKE_CLAUDE_MODE:-}" >&2
    exit 2 ;;
esac
EOF
chmod +x "$FAKE_CLAUDE_DIR/claude"

# run_claude() duerme de verdad entre reintentos (backoff ante rate limit,
# 30s fijos ante fallo genérico). No mockeamos sleep para no falsear el
# comportamiento real -- en su lugar fijamos BACKOFF_START al mínimo posible
# para que la corrida completa (los tres casos) tome segundos, no minutos.
export BACKOFF_START=1
export BACKOFF_MAX=1
export STEP_TIMEOUT=30
export RATE_LIMIT_HANDOFF_ATTEMPTS=2

fail=0
assert_eq() {
  local etiqueta="$1" esperado="$2" real="$3"
  if [ "$esperado" != "$real" ]; then
    echo "FAIL: $etiqueta -- esperado=$esperado real=$real"
    fail=1
  else
    echo "PASS: $etiqueta"
  fi
}

run_caso() {
  local nombre="$1" modo="$2"
  : > "$CALL_LOG"
  (
    source "$ORQ_FUNCS"
    PATH="$FAKE_CLAUDE_DIR:$PATH"
    FAKE_CLAUDE_MODE="$modo"
    FAKE_CLAUDE_CALL_LOG="$CALL_LOG"
    export PATH FAKE_CLAUDE_MODE FAKE_CLAUDE_CALL_LOG
    mkdir -p "$LOOP_DIR" "$LOG_DIR"
    set +e
    run_claude "prueba de $nombre" "test-run-claude-$nombre"
    echo "$?" > "$CALL_LOG.rc"
  )
  echo "$(cat "$CALL_LOG.rc")" "$(wc -l < "$CALL_LOG" | tr -d ' ')"
}

echo "== Caso 1: éxito =="
read -r rc n_llamadas <<< "$(run_caso exito success)"
assert_eq "éxito -> código de retorno 0" "0" "$rc"
assert_eq "éxito -> exactamente 1 invocación de claude" "1" "$n_llamadas"

echo "== Caso 2: rate limit sostenido (handoff a Codex) =="
read -r rc n_llamadas <<< "$(run_caso ratelimit ratelimit)"
assert_eq "rate limit sostenido -> código de retorno 2" "2" "$rc"
assert_eq "rate limit sostenido -> handoff justo tras RATE_LIMIT_HANDOFF_ATTEMPTS=2 invocaciones (ni antes ni después)" "2" "$n_llamadas"

echo "== Caso 3: fallo genérico (no es rate limit) =="
read -r rc n_llamadas <<< "$(run_caso fallo failure)"
assert_eq "fallo genérico -> código de retorno 1" "1" "$rc"
assert_eq "fallo genérico -> agota los 3 reintentos antes de rendirse" "3" "$n_llamadas"

# Los logs que dejan los tres casos son artefactos de esta prueba (claude
# simulado), no corridas reales del loop -- se limpian para no ensuciar
# tokens/CONTEO-TOKENS.md con datos ficticios.
rm -f "$REPO_ROOT"/.loop/logs/claude-test-run-claude-*.json

if [ "$fail" -ne 0 ]; then
  echo "test-run-claude.sh: FALLÓ" >&2
  exit 1
fi
echo "test-run-claude.sh: todos los casos en verde."
