#!/usr/bin/env bash
# =============================================================================
# test-marca-autorrevision.sh — prueba reproducible de
# garantizar_marca_autorrevision() (orquestador.sh)
#
# Responde a la pregunta real de Victor (2026-08-30): cómo evitar que un
# VETO detenga el loop cuando Codex actúa como implementador Y validador de
# la misma iteración (CODEX_LEAD_PROMPT + CODEX_SELFREVIEW_PROMPT), sin
# apagar el control de calidad en sí.
#
# El VETO real que motivó esto (commit `2795ff3`, ver STATE.md 2026-08-29)
# no fue un defecto de contenido: Codex simplemente olvidó escribir la línea
# "AUTORREVISION: Codex (Claude no disponible)" en el commit y la marca
# "PENDIENTE_AUDITORIA_CLAUDE" en STATE.md que su propio prompt le pedía, su
# autorrevisión lo detectó y VETÓ, y el loop entero se detuvo por un olvido
# de formato. garantizar_marca_autorrevision() elimina esa clase de VETO por
# completo: el propio orquestador (bash determinista, no un LLM) aplica
# ambos requisitos sobre el commit de Codex ANTES de que se autorrevise, así
# que ya nunca pueden faltar. Esta prueba verifica esa garantía directamente,
# sin invocar ningún agente real.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

DISPATCH_LINE="$(grep -n '^# -\+ dispatcher' orquestador.sh | head -1 | cut -d: -f1)"
ORQ_FUNCS="$(mktemp)"
head -n "$((DISPATCH_LINE - 1))" orquestador.sh > "$ORQ_FUNCS"

TMP_REPO="$(mktemp -d)"
OUT="$(mktemp)"
cleanup() { rm -rf "$ORQ_FUNCS" "$TMP_REPO"; rm -f "$OUT"; }
trap cleanup EXIT

fail=0
assert_eq() {
  local etiqueta="$1" esperado="$2" real="$3"
  if [ "$esperado" != "$real" ]; then
    echo "FAIL: $etiqueta -- esperado=[$esperado] real=[$real]"
    fail=1
  else
    echo "PASS: $etiqueta"
  fi
}
assert_ne() {
  local etiqueta="$1" a="$2" b="$3"
  if [ "$a" = "$b" ]; then
    echo "FAIL: $etiqueta -- ambos son [$a], deberían ser distintos"
    fail=1
  else
    echo "PASS: $etiqueta"
  fi
}
assert_contains() {
  local etiqueta="$1" texto="$2" buscado="$3"
  if printf '%s' "$texto" | grep -qF "$buscado"; then
    echo "PASS: $etiqueta"
  else
    echo "FAIL: $etiqueta -- no se encontró [$buscado]"
    fail=1
  fi
}

# Repo git aislado que simula el árbol de trabajo real: un commit ya existe
# (ajeno, de otra iteración anterior), luego uno nuevo que representa el
# commit de Codex en modo autorrevisión -- sin la línea/marca, tal como
# ocurrió de verdad en 2795ff3 -- y por último la llamada real a la función
# bajo prueba.
(
  cd "$TMP_REPO"
  git init --quiet
  git config user.email "test@example.com"
  git config user.name "Test"

  printf '# STATE\n(anterior)\n' > STATE.md
  git add STATE.md
  git commit --quiet -m "chore: commit previo, ajeno a esta prueba"
  commit_previo="$(git rev-parse HEAD)"

  printf 'contenido nuevo\n' > archivo.txt
  git add archivo.txt
  git commit --quiet -m "chore: registra el REVIEW.md de X (OK)"
  commit_lead_original="$(git rev-parse HEAD)"
  commits_antes="$(git rev-list --count HEAD)"

  source "$ORQ_FUNCS"
  garantizar_marca_autorrevision "7"

  {
    echo "commits_antes=$commits_antes"
    echo "commits_despues=$(git rev-list --count HEAD)"
    echo "commit_previo=$commit_previo"
    echo "commit_lead_original=$commit_lead_original"
    echo "head_final=$(git rev-parse HEAD)"
    echo "padre_de_head=$(git rev-parse HEAD^)"
  } > "$OUT.datos"
  git log -1 --format=%B > "$OUT.mensaje"
  cat STATE.md > "$OUT.estado"
  git show --stat --format='' HEAD > "$OUT.archivos"
)

source "$OUT.datos"
mensaje="$(cat "$OUT.mensaje")"
estado="$(cat "$OUT.estado")"
archivos_del_commit="$(cat "$OUT.archivos")"
rm -f "$OUT.datos" "$OUT.mensaje" "$OUT.estado" "$OUT.archivos"

echo "== Caso 1: amend, no un commit nuevo aparte =="
assert_eq "número de commits no cambia" "$commits_antes" "$commits_despues"

echo "== Caso 2: se amendó el commit correcto (el de Codex), no el anterior =="
assert_ne "el hash del commit de Codex cambió (el amend sí ocurrió)" "$commit_lead_original" "$head_final"
assert_eq "el padre de HEAD sigue siendo el commit ajeno anterior, intacto" "$commit_previo" "$padre_de_head"

echo "== Caso 3: el mensaje conserva el original y añade la línea obligatoria =="
assert_contains "mensaje conserva el resumen original" "$mensaje" "chore: registra el REVIEW.md de X (OK)"
assert_contains "mensaje incluye AUTORREVISION" "$mensaje" "AUTORREVISION: Codex (Claude no disponible)"

echo "== Caso 4: STATE.md queda marcado PENDIENTE_AUDITORIA_CLAUDE, referenciando la iteración =="
assert_contains "STATE.md conserva el contenido previo" "$estado" "(anterior)"
assert_contains "STATE.md incluye la marca" "$estado" "PENDIENTE_AUDITORIA_CLAUDE"
assert_contains "STATE.md referencia la iteración correcta" "$estado" "iteración 7"

echo "== Caso 5: el commit final (el que revisa la autorrevisión) incluye el cambio de STATE.md =="
assert_contains "STATE.md aparece en el commit amendado" "$archivos_del_commit" "STATE.md"

if [ "$fail" -ne 0 ]; then
  echo "test-marca-autorrevision.sh: FALLÓ" >&2
  exit 1
fi
echo "test-marca-autorrevision.sh: todos los casos en verde."
