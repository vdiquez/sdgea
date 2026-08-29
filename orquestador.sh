#!/usr/bin/env bash
# =============================================================================
# orquestador.sh — Loop agéntico Claude Code ↔ Codex CLI
# Proyecto: Capa AI-native de clasificación e indexación documental (SGDEA)
#
# Principio del proceso (espejo de P-01): los agentes proponen, Victor dispone.
# Frontera física: hook de git bloquea commits a .specify/memory/constitution.md
# (la constitución; specs/00-constitution.md es un stub que apunta ahí) sin
# HUMAN=1. El resto de specs/ es agente-escribible; Codex es el árbitro
# automático (VETO detiene el loop) — la aprobación humana es por excepción,
# no por defecto.
#
# Uso:
#   ./orquestador.sh bootstrap    # prepara CLAUDE.md, AGENTS.md, coordinación, hook
#   ./orquestador.sh preflight    # F0: propone correcciones de specs y SE DETIENE
#   ./orquestador.sh seed core    # siembra TODO.md con el corte vertical (F2+F3)
#   ./orquestador.sh loop         # loop autónomo (correr DENTRO del contenedor)
#   ./orquestador.sh digest       # lectura diaria de 10 minutos
#   ./orquestador.sh status       # estado rápido
#
# Variables (exportar antes de correr):
#   TEST_CMD="./test.sh"     árbitro objetivo — un solo binario (ver TEST_BIN
#                            abajo); ./test.sh corre gradlew test + pytest del
#                            arnés. Cambialo si el árbitro real debe ser otro.
#   MAX_ITER=25              iteraciones máximas del loop por corrida
#   MAX_TASK_ATTEMPTS=3      intentos de tests por tarea antes de bloquear
#   BACKOFF_START=600        backoff inicial ante rate limit (s)
#   BACKOFF_MAX=5400         backoff máximo (s)
#   RATE_LIMIT_HANDOFF_ATTEMPTS=2  ciclos de backoff que se toleran a Claude
#                            antes de entregarle la iteración a Codex en modo
#                            autorrevisión (implementa + se revisa a sí mismo,
#                            marca STATE.md "PENDIENTE_AUDITORIA_CLAUDE").
#                            Claude audita esas entradas en su próxima corrida
#                            exitosa, antes de tomar tarea nueva de TODO.md.
#   STEP_TIMEOUT=3600        timeout por paso de agente (s)
#   NTFY_TOPIC=""            topic de ntfy.sh para notificaciones push (opcional)
#   CONTAINED=0              =1 SOLO dentro de un contenedor sin volúmenes del host:
#                            usa --dangerously-skip-permissions
#   EXTRA_ALLOWED=""         reglas extra de --allowedTools, p. ej. "Bash(npm *)"
# =============================================================================
set -euo pipefail

TEST_CMD="${TEST_CMD:-./test.sh}"
MAX_ITER="${MAX_ITER:-25}"
MAX_TASK_ATTEMPTS="${MAX_TASK_ATTEMPTS:-3}"
BACKOFF_START="${BACKOFF_START:-600}"
BACKOFF_MAX="${BACKOFF_MAX:-5400}"
RATE_LIMIT_HANDOFF_ATTEMPTS="${RATE_LIMIT_HANDOFF_ATTEMPTS:-2}"
STEP_TIMEOUT="${STEP_TIMEOUT:-3600}"
NTFY_TOPIC="${NTFY_TOPIC:-}"
CONTAINED="${CONTAINED:-0}"
EXTRA_ALLOWED="${EXTRA_ALLOWED:-}"

LOOP_DIR=".loop"
LOG_DIR="$LOOP_DIR/logs"
TEST_BIN="${TEST_CMD%% *}"

# ----------------------------------------------------------------- utilidades
ts()     { date '+%Y-%m-%d %H:%M:%S'; }
log()    { echo "[$(ts)] $*" | tee -a "$LOOP_DIR/loop.log" >&2; }
notify() {
  log "NOTIFY: $*"
  [ -n "$NTFY_TOPIC" ] && curl -fsS -m 10 -d "$*" "https://ntfy.sh/$NTFY_TOPIC" >/dev/null 2>&1 || true
}
die() { log "FATAL: $*"; exit 1; }

json_field() { # json_field <archivo> <campo>  (usa jq si existe; si no, grep)
  if command -v jq >/dev/null 2>&1; then
    jq -r ".$2 // empty" "$1" 2>/dev/null || true
  else
    grep -o "\"$2\":\"[^\"]*\"" "$1" | head -1 | cut -d'"' -f4 || true
  fi
}

is_rate_limited() { grep -qiE 'rate.?limit|overloaded|429|usage limit|quota' "$1"; }

commit_count() { git rev-list --count HEAD 2>/dev/null || echo 0; }

# ------------------------------------------------------- llamadas a los agentes
# Claude Code headless. Captura session_id; ante rate limit hace backoff y
# reanuda LA MISMA sesión con --resume, hasta RATE_LIMIT_HANDOFF_ATTEMPTS
# ciclos — a partir de ahí devuelve 2 (distinto de 1=fallo real) para que
# cmd_loop entregue esa iteración a Codex en vez de seguir esperando: un
# rate limit de cuenta (ventana de varias horas) no se resuelve reintentando
# unos minutos más, a diferencia de un "overloaded"/429 transitorio, que sí
# suele resolverse en el primer o segundo reintento.
# Prueba reproducible de los tres códigos de retorno (0/2/1) y del punto
# exacto de handoff: ./test-run-claude.sh (no está en test.sh a propósito —
# toma ~90s por los backoffs reales de la rama de fallo genérico; correr
# a mano tras tocar run_claude()/cmd_loop()).
run_claude() { # run_claude <prompt> <etiqueta>
  local prompt="$1" tag="$2" out sid="" wait="$BACKOFF_START" attempt=0 rl_attempts=0
  local -a flags=(--output-format json
                  --append-system-prompt "Reglas duras: .specify/memory/constitution.md es SOLO LECTURA, nunca la edites ni la comitees (specs/00-constitution.md es un stub que apunta ahí, mismo trato). El resto de specs/ (contextos, plan-*.md, tasks-*.md, correcciones) SÍ puedes crearlo y editarlo, y comitearlo tras pasar revisión de Codex — no pidas aprobación humana por archivo. No inventar referencias normativas ni umbrales: quedan PENDIENTE/[CLARIFICAR]. No implementar componentes probabilísticos reales. Ante ambigüedad real (no procedimental) escribir en QUESTIONS.md y detener la tarea.")
  if [ "$CONTAINED" = "1" ]; then
    flags+=(--dangerously-skip-permissions)
  else
    # Hallazgo operativo real (2026-08-27, T-41): sin el segundo patrón,
    # Claude a veces invoca "bash ./test.sh" en vez de "./test.sh" (p. ej. en
    # Windows/Git Bash) — eso NO calza con "Bash(./test.sh *)", queda
    # denegado sin aviso claro, y la sesión headless puede terminar su turno
    # sin comitear nada, preguntando algo que nadie puede responder.
    flags+=(--permission-mode acceptEdits
            --allowedTools "Bash(git *),Bash($TEST_BIN *),Bash(bash $TEST_BIN*)${EXTRA_ALLOWED:+,$EXTRA_ALLOWED}")
  fi
  while :; do
    attempt=$((attempt+1)); out="$LOG_DIR/claude-$tag-$attempt.json"
    if [ -n "$sid" ]; then
      timeout "$STEP_TIMEOUT" claude -p "Continúa exactamente donde quedaste con la misma tarea." \
        --resume "$sid" "${flags[@]}" >"$out" 2>&1 && rc=0 || rc=$?
    else
      timeout "$STEP_TIMEOUT" claude -p "$prompt" "${flags[@]}" >"$out" 2>&1 && rc=0 || rc=$?
    fi
    [ -z "$sid" ] && sid="$(json_field "$out" session_id)"
    if [ "$rc" -eq 0 ]; then return 0; fi
    if is_rate_limited "$out"; then
      rl_attempts=$((rl_attempts+1))
      if [ "$rl_attempts" -ge "$RATE_LIMIT_HANDOFF_ATTEMPTS" ]; then
        log "Claude rate-limited $rl_attempts veces seguidas (~${wait}s de espera ya agotados). Se entrega esta iteración a Codex en modo autorrevisión en vez de seguir esperando."
        return 2
      fi
      log "Claude rate-limited (intento $rl_attempts/$RATE_LIMIT_HANDOFF_ATTEMPTS). Backoff ${wait}s; luego --resume ${sid:-nuevo}."
      sleep "$wait"; wait=$(( wait*2 > BACKOFF_MAX ? BACKOFF_MAX : wait*2 ))
    elif [ "$attempt" -lt 3 ]; then
      log "Claude falló (rc=$rc). Reintento $attempt/3. Log: $out"; sleep 30
    else
      log "Claude falló definitivamente. Log: $out"; return 1
    fi
  done
}

# Codex headless. Ante rate limit: backoff y reintento limpio (el estado vive
# en archivos, el prompt es idempotente). Reanudación manual: codex exec resume <ID>.
run_codex() { # run_codex <prompt> <etiqueta>
  local prompt="$1" tag="$2" out wait="$BACKOFF_START" attempt=0
  while :; do
    attempt=$((attempt+1)); out="$LOG_DIR/codex-$tag-$attempt.log"
    timeout "$STEP_TIMEOUT" codex exec --json --sandbox workspace-write "$prompt" >"$out" 2>&1 && rc=0 || rc=$?
    if [ "$rc" -eq 0 ]; then return 0; fi
    if is_rate_limited "$out"; then
      log "Codex rate-limited (intento $attempt). Backoff ${wait}s."
      sleep "$wait"; wait=$(( wait*2 > BACKOFF_MAX ? BACKOFF_MAX : wait*2 ))
    elif [ "$attempt" -lt 3 ]; then
      log "Codex falló (rc=$rc). Reintento $attempt/3."; sleep 30
    else
      log "Codex falló definitivamente. Log: $out"; return 1
    fi
  done
}

fitness() { # tests como árbitro objetivo
  log "Árbitro: $TEST_CMD"
  ( eval "$TEST_CMD" ) >"$LOG_DIR/tests-$(date +%s).log" 2>&1
}

# --------------------------------------------------------------------- prompts
CLAUDE_STEP_PROMPT='Lee, en este orden: CLAUDE.md, .specify/memory/constitution.md, STATE.md, REVIEW.md y TODO.md.

Si STATE.md tiene una o más entradas marcadas "PENDIENTE_AUDITORIA_CLAUDE" (commits que Codex
hizo solo y autorrevisó mientras tu cuenta estaba en su límite de uso): audítalas TODAS primero,
antes de tomar cualquier tarea nueva de TODO.md. Para cada una, revisa el commit correspondiente
(git show) con el mismo rigor de una revisión tuya normal: P-01, P-03, P-08, honestidad de los
tests, y (si tocó specs/) referencias/umbrales inventados. Si encuentras una violación real:
corrígela tú mismo si es una corrección de consistencia clara contra un patrón ya ratificado en
el proyecto, o escríbela en QUESTIONS.md si exige una decisión de Victor. Cuando termines de
auditar una entrada, quita su marca "PENDIENTE_AUDITORIA_CLAUDE" de STATE.md y deja una línea
diciendo qué encontraste (o que quedó conforme). Si no hay ninguna pendiente, sigue directo con
lo de abajo.

Toma la PRIMERA tarea abierta "- [ ]" de TODO.md y trabaja SOLO en ella.
Si la tarea es de CÓDIGO (implementación de un RF):
1) Localiza en la spec del contexto los criterios Dado/Cuando/Entonces del RF de la tarea.
2) Escribe PRIMERO los tests que expresan esos criterios.
3) Implementa hasta que los tests pasen ejecutando: __TEST_CMD__
Si la tarea es de SPEC (nuevo contexto, plan-*.md o tasks-*.md, corrección a una
spec existente): redáctala siguiendo el formato y el nivel de rigor de las specs
ya existentes (spec-records-custodia.md como referencia). No inventes referencias
normativas ni umbrales.
En ambos casos, al terminar:
4) Haz un commit atómico con mensaje "RF-XXX: <resumen>" / "spec: <resumen>" /
   "chore: ...". Puedes comitear specs/ directamente — SOLO
   .specify/memory/constitution.md (y su stub specs/00-constitution.md) está
   bloqueado por el hook y requiere HUMAN=1.
5) Marca la tarea "- [x]" en TODO.md y actualiza STATE.md (tarea, decisiones tomadas, siguiente paso).
Si encuentras un [CLARIFICAR], una celda PENDIENTE o una ambigüedad real: NO la resuelvas inventando.
Escribe la pregunta en QUESTIONS.md, marca la tarea "- [?]" y termina limpiamente.'

CODEX_REVIEW_PROMPT='Lee AGENTS.md, .specify/memory/constitution.md y STATE.md.
Revisa el último commit (git show HEAD) contra la spec del contexto correspondiente.
Verifica en especial: P-01 (nada probabilístico escribe estado: todo cruza la capa
anticorrupción como Sugerencia y solo una decisión humana materializa), P-03 (toda
capacidad externa detrás de su interfaz), P-08 (toda transición emite evento de
auditoría), y la HONESTIDAD de los tests: ¿prueban el criterio de aceptación o están
amañados para pasar?
Si el commit toca cualquier archivo bajo specs/ (contexto nuevo, plan-*.md,
tasks-*.md, o corrección a una spec existente): verifica ADEMÁS que ninguna
referencia normativa (Acuerdo, Ley, Decreto, ISO) ni ningún umbral numérico nuevo
aparece sin existir ya en una spec previa, y sin quedar marcado
PENDIENTE/[CLARIFICAR] si no hay fuente. Una cita o un número inventado es motivo
de VETO igual que una violación de P-01/P-03/P-08 — este chequeo es lo que
reemplaza la revisión humana archivo por archivo.
Sobrescribe REVIEW.md con tu revisión. Si detectas una violación de la
constitución o una referencia/umbral inventado, la PRIMERA línea de REVIEW.md debe
ser exactamente "VETO: <motivo>".
Puedes añadir tareas "- [ ]" al final de TODO.md solo si derivan de una spec existente.'

# Modo autorrevisión: Claude está en su límite de uso de cuenta (ver
# RATE_LIMIT_HANDOFF_ATTEMPTS) y Codex toma AMBOS roles de esta iteración —
# implementa y, aparte, se revisa a sí mismo con CODEX_SELFREVIEW_PROMPT.
# Marca STATE.md para que Claude audite el commit en su próxima corrida
# exitosa (ver el párrafo nuevo al inicio de CLAUDE_STEP_PROMPT).
CODEX_LEAD_PROMPT='Claude Code está en el límite de uso de su cuenta (rate limit sostenido, no un
error transitorio) y no puede trabajar por ahora. Vas a implementar tú la siguiente tarea del
loop, con las mismas reglas que sigue Claude Code aquí — y, porque no hay un segundo agente
disponible para revisarte en este momento, además de implementar vas a autorrevisarte con el
mismo rigor de una revisión de Codex normal antes de comitear.

Lee, en este orden: AGENTS.md, .specify/memory/constitution.md, STATE.md, REVIEW.md y TODO.md.
Toma la PRIMERA tarea abierta "- [ ]" de TODO.md y trabaja SOLO en ella.
Si la tarea es de CÓDIGO (implementación de un RF):
1) Localiza en la spec del contexto los criterios Dado/Cuando/Entonces del RF de la tarea.
2) Escribe PRIMERO los tests que expresan esos criterios.
3) Implementa hasta que los tests pasen ejecutando: __TEST_CMD__
Si la tarea es de SPEC (nuevo contexto, plan-*.md o tasks-*.md, corrección a una spec
existente): redáctala siguiendo el formato y el nivel de rigor de las specs ya existentes. No
inventes referencias normativas ni umbrales.
Antes de comitear, autorrevísate: P-01 (nada probabilístico escribe estado), P-03 (toda
capacidad externa detrás de su interfaz propia), P-08 (toda transición emite evento de
auditoría), honestidad de los tests, y (si tocaste specs/) que ninguna referencia normativa o
umbral es inventado. Si encuentras un problema, corrígelo antes de comitear — no comitees algo
que tú mismo vetarías si lo revisaras de otro.
Al terminar:
4) Haz un commit atómico con mensaje "RF-XXX: <resumen>" / "spec: <resumen>" / "chore: ...", y
   añade una línea "AUTORREVISION: Codex (Claude no disponible)". Puedes comitear specs/
   directamente — SOLO .specify/memory/constitution.md (y su stub specs/00-constitution.md)
   está bloqueado por el hook y requiere HUMAN=1.
5) Marca la tarea "- [x]" en TODO.md y actualiza STATE.md: agrega la entrada de esta tarea
   marcada explícitamente "PENDIENTE_AUDITORIA_CLAUDE" (para que Claude Code la audite cuando
   su cuenta vuelva a estar disponible), con la misma información de siempre (decisiones
   tomadas, siguiente paso).
Si encuentras un [CLARIFICAR], una celda PENDIENTE o una ambigüedad real: NO la resuelvas
inventando. Escribe la pregunta en QUESTIONS.md, marca la tarea "- [?]" y termina limpiamente.'

CODEX_SELFREVIEW_PROMPT="$CODEX_REVIEW_PROMPT"'

Nota: este commit lo hiciste TÚ (Claude Code está en el límite de uso de su cuenta y no puede
revisarte ahora) — no hay un segundo agente independiente revisándote en este momento, así que
sé más exigente contigo mismo de lo habitual. Confirma además que el mensaje del commit incluye
la línea "AUTORREVISION: Codex (Claude no disponible)" y que STATE.md deja la entrada de esta
tarea marcada "PENDIENTE_AUDITORIA_CLAUDE". Si falta cualquiera de las dos, trátalo como hallazgo
bloqueante y VETA hasta que se corrija.'

PREFLIGHT_PROMPT='Fase F0 (pre-vuelo). Lee .specify/memory/constitution.md y luego aplica EN EL ÁRBOL DE
TRABAJO, SIN COMITEAR, exactamente estas tres correcciones (Apéndice A del plan):
1) specs/eval/eval-clasificacion.md §4.5: elimina la noción de "auto-aceptarse sin
   revisión". Redefine: Cobertura = fracción de documentos sobre el umbral que se
   vuelven CANDIDATOS A APROBACIÓN MASIVA, aprobados en bloque por el archivista
   mediante acción explícita (P-09) registrada con actor y fecha (RF-RC-004); Error a
   esa cobertura = fracción de esos candidatos cuya sugerencia principal es incorrecta.
   Ajusta la frase de la pregunta de negocio y añade traza a RF-RC-004 en §9.
2) CLAUDE-CODE-KICKOFF.md y specs/README.md: los "siete" bounded contexts pasan a
   NUEVE, añadiendo Normalización y Extracción (ya nombrados en spec-captura-ingesta §1
   y en la tabla del edd-harness §2).
3) specs/contexts/spec-records-custodia.md §7 y specs/eval/eval-clasificacion.md §9:
   reemplaza "Acuerdo 003 de 2015" por "Acuerdo AGN 001 de 2024 (compila el antiguo
   Acuerdo 003 de 2015)" y "Acuerdo 002 de 2014 (TRD)" por "Acuerdo AGN 001 de 2024 —
   procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019)". Conserva Ley 594,
   Decreto 1080 de 2015, ISO 15489/16175. NO toques las celdas PENDIENTE.
NO hagas git commit. Al final imprime un resumen de los cambios por archivo.'

# ------------------------------------------------------------------- bootstrap
cmd_bootstrap() {
  mkdir -p "$LOOP_DIR" "$LOG_DIR"
  git rev-parse --git-dir >/dev/null 2>&1 || die "Esto no es un repo git."
  command -v claude >/dev/null || die "claude no está en PATH."
  command -v codex  >/dev/null || log "AVISO: codex no está en PATH (instálalo antes de 'loop')."

  [ -f CLAUDE.md ] || cat > CLAUDE.md <<'EOF'
# Contexto permanente — Claude Code (implementador)
Fuente de verdad: specs/. Gobierna .specify/memory/constitution.md
(specs/00-constitution.md es un stub que apunta ahí, mismo trato).
Constitución de ejecución (violarla invalida la sesión):
- specs/** es SOLO LECTURA para agentes, CON UNA ÚNICA EXCEPCIÓN: la tarea de
  preflight de F0 (invocada como `./orquestador.sh preflight`) puede editar el
  ÁRBOL DE TRABAJO de specs/ para aplicar las correcciones del Apéndice A. Fuera
  de esa tarea específica, specs/** es solo lectura, sin excepción.
- specs/** SOLO SE COMITEA CON HUMAN=1 — esto no tiene excepción, ni siquiera en
  la tarea de preflight. El agente nunca ejecuta ese commit, aunque se lo pidan.
- Nunca inventar referencias normativas ni valores de umbral: quedan PENDIENTE.
- Nunca implementar componentes probabilísticos reales (clasificación, OCR, etc.);
  el clasificador del corte vertical es el componente FICTICIO del arnés.
- Nunca cambiar el stack decidido.
- Todo desarrollo es TDD contra los criterios Dado/Cuando/Entonces del RF.
- Ante [CLARIFICAR] o ambigüedad: pregunta en QUESTIONS.md, marca "- [?]" y detente.
Coordinación: STATE.md (estado), TODO.md (cola), REVIEW.md (revisión), QUESTIONS.md.
EOF
  [ -f AGENTS.md ] || sed 's/Claude Code (implementador)/Codex (revisor)/' CLAUDE.md > AGENTS.md
  [ -f STATE.md ]     || printf '# STATE\nFase: F1 pendiente. Ver plan-ejecucion-agentica.md.\n' > STATE.md
  [ -f TODO.md ]      || printf '# TODO\n(siembra con: ./orquestador.sh seed core)\n' > TODO.md
  [ -f REVIEW.md ]    || printf '# REVIEW\n(sin revisiones aún)\n' > REVIEW.md
  [ -f QUESTIONS.md ] || printf '# QUESTIONS — solo anexado; las responde el humano\n' > QUESTIONS.md

  # Frontera física: SOLO la constitución exige HUMAN=1 (espejo del P-01 en el
  # proceso). El resto de specs/ — contextos nuevos, plan-*.md, tasks-*.md,
  # correcciones a specs existentes — se comitea normalmente si pasa la
  # revisión de Codex; no requiere aprobación humana por archivo.
  local hook=".git/hooks/pre-commit"
  if [ ! -f "$hook" ]; then
    cat > "$hook" <<'EOF'
#!/bin/sh
# Único archivo sellado: la constitución. Todo lo demás bajo specs/ es
# agente-escribible y se comitea automáticamente si pasa revisión de Codex.
if git diff --cached --name-only | grep -qE '^(specs/00-constitution\.md|\.specify/memory/constitution\.md)$'; then
  if [ "${HUMAN:-0}" != "1" ]; then
    echo "BLOQUEADO: la constitución solo se comitea con HUMAN=1 (decisión humana explícita)." >&2
    exit 1
  fi
fi
EOF
    chmod +x "$hook"
    log "Hook instalado: solo la constitución requiere HUMAN=1."
  fi
  log "Bootstrap listo. Siguiente: ./orquestador.sh preflight"
}

# ----------------------------------------------------------------------- seeds
cmd_seed() {
  case "${1:-}" in
    core) cat > TODO.md <<'EOF'
# TODO — F2/F3: corte vertical determinístico + arnés (clasificador ficticio)
- [ ] T-01 RF-CI-001 Ingesta por lote: artefactos + inventario -> ítems `Recibido`
- [ ] T-02 RF-CI-006 Validación y cuarentena con razón registrada
- [ ] T-03 RF-RC-001 Custodia del original inmutable (WORM + huella verificable)
- [ ] T-04 RF-RC-002 + RF-CI-007 Procedencia completa de punta a punta
- [ ] T-05 RF-CI-008 Cero pérdida silenciosa: suma de estados terminales cuadra
- [ ] T-06 RF-CI-002 Conciliación contra inventario (FUID): faltantes y sobrantes
- [ ] T-07 RF-RC-006 TRD como objeto versionado (estructura mínima)
- [ ] T-08 RF-RC-003 Sugerencia vía capa anticorrupción, con EMISOR FICTICIO; no toca estado
- [ ] T-09 RF-RC-004 Materialización solo por decisión humana (actor + fecha)
- [ ] T-10 RF-RC-005 Bitácora inmutable de solo anexado; modificar/borrar se rechaza
- [ ] T-11 RF-RC-009 Verificación de integridad por demanda con reporte de discrepancias
- [ ] T-12 Arnés: cargar set de juguete, correr componente ficticio, emitir boleta versionada
- [ ] T-13 CI: build + tests + arnés; gates AgentShield + security-review
- [ ] T-14 Empaquetado dual (P-02): mismos contenedores como SaaS y como instalador appliance
EOF
      log "TODO.md sembrado con el corte vertical (14 tareas)." ;;
    *) die "Uso: ./orquestador.sh seed core" ;;
  esac
}

# ------------------------------------------------------------------- preflight
cmd_preflight() {
  mkdir -p "$LOG_DIR"
  log "F0: Claude propone las correcciones de corpus (sin comitear)."
  run_claude "$PREFLIGHT_PROMPT" "preflight" || die "Pre-vuelo falló; revisa $LOG_DIR."
  echo; echo "================ REVISIÓN HUMANA REQUERIDA ================"
  git --no-pager diff --stat -- specs/ CLAUDE-CODE-KICKOFF.md 2>/dev/null || true
  echo "Revisa:   git diff specs/"
  echo "Comitea:  HUMAN=1 git commit -am 'F0: correcciones de corpus'"
  echo "Descarta: git checkout -- specs/"
  notify "F0 lista para tu revisión: git diff specs/"
}

# ------------------------------------------------------------------------ loop
cmd_loop() {
  mkdir -p "$LOG_DIR"
  [ -f TODO.md ] || die "Falta TODO.md; corre bootstrap y seed."
  rm -f BLOCKED.md
  local prompt="${CLAUDE_STEP_PROMPT//__TEST_CMD__/$TEST_CMD}"
  local codex_lead_prompt="${CODEX_LEAD_PROMPT//__TEST_CMD__/$TEST_CMD}"
  local stale=0 prev_count fail_streak=0 codex_led rc_claude
  prev_count=$(commit_count)
  notify "Loop iniciado ($(git branch --show-current 2>/dev/null || echo '?'))"

  for i in $(seq 1 "$MAX_ITER"); do
    log "===== Iteración $i/$MAX_ITER ====="

    if ! grep -q '^- \[ \]' TODO.md; then
      if grep -q '^- \[?\]' TODO.md; then
        printf 'Motivo: tarea(s) "- [?]" esperando respuesta humana en QUESTIONS.md\n' > BLOCKED.md
        notify "Loop detenido: hay preguntas para ti en QUESTIONS.md."
        return 0
      fi
      notify "TODO.md completado en la iteración $i. Loop terminado con éxito."
      return 0
    fi

    rc_claude=0
    run_claude "$prompt" "iter$i" || rc_claude=$?
    codex_led=0
    if [ "$rc_claude" -eq 1 ]; then
      echo "Motivo: Claude falló (iter $i)" > BLOCKED.md; notify "Loop bloqueado: Claude falló."; return 1
    elif [ "$rc_claude" -eq 2 ]; then
      notify "Claude en límite de uso sostenido; Codex toma la iteración $i (implementa + autorrevisión)."
      run_codex "$codex_lead_prompt" "iter$i-lead" || { echo "Motivo: Codex en modo autorrevisión falló (iter $i)" > BLOCKED.md; notify "Loop bloqueado: Codex en modo autorrevisión falló."; return 1; }
      codex_led=1
    fi

    if fitness; then
      fail_streak=0
    else
      fail_streak=$((fail_streak+1))
      log "Tests en rojo ($fail_streak/$MAX_TASK_ATTEMPTS)."
      if [ "$fail_streak" -ge "$MAX_TASK_ATTEMPTS" ]; then
        printf 'Motivo: %s intentos con tests en rojo sobre la misma tarea.\nÚltimo log: %s\n' \
          "$MAX_TASK_ATTEMPTS" "$(ls -t "$LOG_DIR"/tests-*.log | head -1)" > BLOCKED.md
        notify "Loop bloqueado: tests en rojo $MAX_TASK_ATTEMPTS veces. Se requiere tu criterio."
        return 1
      fi
      continue   # misma tarea, siguiente intento
    fi

    if [ "$codex_led" -eq 1 ]; then
      run_codex "$CODEX_SELFREVIEW_PROMPT" "iter$i-selfreview" || log "Autorrevisión de Codex falló; se continúa con precaución."
    else
      run_codex "$CODEX_REVIEW_PROMPT" "iter$i" || log "Revisión de Codex falló; se continúa con precaución."
    fi
    if head -1 REVIEW.md 2>/dev/null | grep -q '^VETO:'; then
      { echo "Motivo: VETO de Codex:"; head -5 REVIEW.md; } > BLOCKED.md
      notify "Loop detenido por VETO de Codex. Lee REVIEW.md; tú decides."
      return 1
    fi

    # Watchdog: dos iteraciones seguidas sin commits nuevos = girar en vacío
    local now_count; now_count=$(commit_count)
    if [ "$now_count" -le "$prev_count" ]; then
      stale=$((stale+1))
      [ "$stale" -ge 2 ] && { echo "Motivo: 2 iteraciones sin commits (watchdog)." > BLOCKED.md; notify "Loop detenido por watchdog."; return 1; }
    else
      stale=0
    fi
    prev_count=$now_count
    sleep 5
  done
  notify "Loop alcanzó MAX_ITER=$MAX_ITER. Corre 'digest' y relanza si procede."
}

# ------------------------------------------------------------- digest / status
cmd_digest() {
  echo "================= DIGEST · $(ts) ================="
  echo "--- STATE.md ---";        sed -n '1,20p' STATE.md 2>/dev/null
  echo; echo "--- Últimos commits ---"; git --no-pager log --oneline -15 2>/dev/null
  echo; echo "--- REVIEW.md (Codex) ---"; sed -n '1,40p' REVIEW.md 2>/dev/null
  echo; echo "--- QUESTIONS.md (te esperan) ---"; cat QUESTIONS.md 2>/dev/null
  [ -f BLOCKED.md ] && { echo; echo "--- BLOCKED.md ---"; cat BLOCKED.md; }
  echo; echo ">> Recordatorio del plan: primero una acción del hilo comercial hoy."
}

cmd_status() {
  echo "Pendientes : $(grep -c '^- \[ \]' TODO.md 2>/dev/null || echo 0)"
  echo "Hechas     : $(grep -c '^- \[x\]' TODO.md 2>/dev/null || echo 0)"
  echo "Bloqueadas : $(grep -c '^- \[?\]' TODO.md 2>/dev/null || echo 0)"
  [ -f BLOCKED.md ] && echo "LOOP BLOQUEADO — lee BLOCKED.md"
}

# ------------------------------------------------------------------ dispatcher
case "${1:-}" in
  bootstrap) cmd_bootstrap ;;
  preflight) cmd_preflight ;;
  seed)      shift; cmd_seed "${1:-}" ;;
  loop)      cmd_loop ;;
  digest)    cmd_digest ;;
  status)    cmd_status ;;
  *) sed -n '2,20p' "$0"; exit 1 ;;
esac
