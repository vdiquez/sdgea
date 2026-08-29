#!/usr/bin/env bash
# =============================================================================
# test-run-id.sh — prueba reproducible de nuevo_run_id() (orquestador.sh)
#
# Responde al VETO de Codex sobre el commit `f2e557c`: un `run_id` calculado
# solo con `date +%Y%m%d-%H%M%S` (precisión de segundo) no es único si dos
# invocaciones de cmd_loop/cmd_preflight arrancan en el mismo segundo, y no
# había ninguna prueba que lo demostrara.
#
# Esta prueba fuerza justo ese escenario: reemplaza `date` en PATH por un
# doble que SIEMPRE devuelve la misma marca de tiempo (simula dos
# invocaciones en el mismo segundo exacto), corre nuevo_run_id() en dos
# procesos `bash -c` separados (dos invocaciones reales, cada una con su
# propio $$) y verifica que los dos run_id resultantes son distintos.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

DISPATCH_LINE="$(grep -n '^# -\+ dispatcher' orquestador.sh | head -1 | cut -d: -f1)"
ORQ_FUNCS="$(mktemp)"
head -n "$((DISPATCH_LINE - 1))" orquestador.sh > "$ORQ_FUNCS"

FAKE_DATE_DIR="$(mktemp -d)"
cleanup() { rm -rf "$ORQ_FUNCS" "$FAKE_DATE_DIR"; }
trap cleanup EXIT

# Doble de `date`: ignora cualquier formato pedido y siempre devuelve la
# misma marca de tiempo fija -- simula que dos invocaciones caen en el mismo
# segundo exacto de reloj, el escenario que el VETO señaló como no cubierto.
cat > "$FAKE_DATE_DIR/date" <<'EOF'
#!/usr/bin/env bash
echo "20260101-000000"
EOF
chmod +x "$FAKE_DATE_DIR/date"

fail=0
assert_ne() {
  local etiqueta="$1" a="$2" b="$3"
  if [ "$a" = "$b" ]; then
    echo "FAIL: $etiqueta -- ambos run_id son iguales ($a)"
    fail=1
  else
    echo "PASS: $etiqueta ($a != $b)"
  fi
}
assert_true() {
  local etiqueta="$1" cond="$2"
  if [ "$cond" = "1" ]; then echo "PASS: $etiqueta"; else echo "FAIL: $etiqueta"; fail=1; fi
}

echo "== Caso 1: dos invocaciones con el reloj fijo en el mismo segundo producen run_id distintos =="
run_id_1="$(PATH="$FAKE_DATE_DIR:$PATH" bash -c "source '$ORQ_FUNCS'; nuevo_run_id")"
run_id_2="$(PATH="$FAKE_DATE_DIR:$PATH" bash -c "source '$ORQ_FUNCS'; nuevo_run_id")"
assert_ne "reloj fijo -> run_id únicos por proceso" "$run_id_1" "$run_id_2"

echo "== Caso 2: el run_id sigue trayendo la marca de tiempo legible (no solo un PID pelado) =="
case "$run_id_1" in
  20260101-000000-*) marca_presente=1 ;;
  *) marca_presente=0 ;;
esac
assert_true "run_id conserva el prefijo de fecha/hora" "$marca_presente"

if [ "$fail" -ne 0 ]; then
  echo "test-run-id.sh: FALLÓ" >&2
  exit 1
fi
echo "test-run-id.sh: todos los casos en verde."
