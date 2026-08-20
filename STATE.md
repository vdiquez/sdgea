# STATE
Fase: F1 CERRADO → F2 arrancando. Ver plan-ejecucion-agentica.md.

Hecho:
- F0: correcciones de corpus aplicadas y comiteadas (A.1-A.3); constitución
  ubicada en specs/00-constitution.md (commit HUMAN=1 de Victor).
- F1.D1: decisión de stack — Kotlin/Spring Boot (núcleo determinístico) +
  Python 3.12/FastAPI (capa probabilística + arnés EDD), Gradle + uv,
  Docker/docker-compose, GitHub Actions.
- F1: andamiaje raíz (nueve contextos, Gradle multi-módulo, workspace uv,
  CI, empaquetado dual) — dos incrementos comiteados.
- F1: interfaces P-03 (platform-kotlin, platform-python) + esqueleto
  ejecutable del arnés EDD con componente FICTICIO — comiteado.
- F1: `specify init` (spec-kit) corrido con integraciones Claude Code + Codex;
  constitución migrada a .specify/memory/constitution.md (commit HUMAN=1 de
  Victor, `37a6125`).
- F1: TEST_CMD fijado (`./test.sh` — gradlew test + pytest del arnés en un
  solo binario, para que --allowedTools lo autorice completo).
- F1: cierre — `codex exec` revisó af24e22/a890e83/37a6125 contra la
  constitución. Resultado: "OK: cierre de F1 sin objeciones" (ver REVIEW.md).
  Verificó P-01/P-02/P-03/P-05/P-06/P-10, corrió él mismo la suite del arnés
  (3 passed) y confirmó que las pruebas no están amañadas. Sin VETO.
- F2: `TODO.md` sembrado con las 14 tareas T-01..T-14 del corte vertical.
- F2: sandbox Docker (`agent-sandbox/`) construido y verificado de punta a
  punta — Ubuntu 24.04, usuario no-root `agent` (obligatorio: Claude Code
  rechaza --dangerously-skip-permissions bajo root), autenticación por
  montaje de solo lectura de la sesión del host (no API keys separadas).
  `codex login status` y una llamada real de `claude -p` confirmaron que
  autentica; `orquestador.sh` y `test.sh` corren bien dentro del contenedor.
- F2: T-01 (RF-CI-001, ingesta por lote) implementado en
  `contexts/captura-ingesta` (paquete `sgdea.contexts.capturaingesta`):
  `cargarLote(loteId, artefactos, inventario)` produce un `ItemIngesta` en
  estado `RECIBIDO` por cada `ArtefactoOrigen`, conservando referencia al
  artefacto y al lote. TDD: 3 tests (`IngestaPorLoteTest`) escritos contra el
  Dado/Cuando/Entonces del RF antes de la implementación; `./test.sh` en
  verde (Gradle: BUILD SUCCESSFUL, 3/3 en el nuevo test; pytest del arnés:
  3 passed). Alcance deliberadamente angosto: solo el estado `Recibido` de
  RF-CI-001 — validación/cuarentena (T-02), conciliación (T-06) y demás
  estados del ítem quedan para sus propias tareas.
  Siguiente paso: T-02 (RF-CI-006, validación y cuarentena) — primera tarea
  abierta en TODO.md.

## Camino a F2 (checklist, 2026-08-20)

- [ ] 1. Enviar el one-pager a 3–5 entidades calificadas (F4, hilo comercial —
      sin dependencia técnica; seguimos con los pasos técnicos primero).
- [x] 2. Cerrar F1: `codex exec` revisó el esqueleto completo → REVIEW.md sin
      VETO.
- [x] 3. TEST_CMD fijado a `./test.sh`.
- [x] 4. TODO.md sembrado (14 tareas T-01..T-14).
- [x] 5. Sandbox Docker construido y verificado (`agent-sandbox/`).
- [ ] 6. Arrancar F2: `./agent-sandbox/run.sh` y dentro, `./orquestador.sh loop`.
- [ ] 7. Skill_Seekers sobre el PDF del Acuerdo AGN 001 de 2024 → skill
      normativa (F4, automatizable en paralelo).
- [ ] 8. Completar F3: gates AgentShield + security-review en CI; Dockerfiles
      reales por contexto a medida que el loop les da código.
- [ ] 9. Empezar el ritual `./orquestador.sh digest`; decidir si configurar
      NTFY_TOPIC.
- [ ] 10. En la reunión/convenio de F4: proteger 9.2, negociar el Anexo 1 como
      la negociación real del set patrón, decidir herramienta de anotación
      con el archivista.

Después de estos 10: TODO.md (sembrado en el paso 4) es el tracker de lo que
sigue — el loop autónomo lo consume y lo marca `- [x]` / `- [?]` solo.

## Sobre spec-kit — cuándo entran sus comandos

`/speckit-plan`, `/speckit-tasks` y `/speckit-implement` operan sobre una
"feature" registrada por `/speckit-specify` (crea specs/NNN-slug/spec.md +
.specify/feature.json) — no se pueden apuntar directo a
specs/contexts/*.md. El corte vertical de F2 (paso 6 de arriba) sigue el
mecanismo YA construido en orquestador.sh (TODO.md + loop con backoff, VETO
de Codex, watchdog) — spec-kit se instaló pero F2 no pasa por él, y así lo
describe el plan tal cual está escrito. El punto natural para usar
/speckit-specify por primera vez es al especificar un bounded context que
TODAVÍA no tiene spec escrita a mano (Normalización, Extracción,
Enriquecimiento, Indexación y Búsqueda, Seguridad y Acceso, Validación
Humana) — no antes.
