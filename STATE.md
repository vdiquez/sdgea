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
- F2: T-02 (RF-CI-006, validación y cuarentena) BLOQUEADA — marcada `- [?]`
  en TODO.md. El Dado/Cuando/Entonces del RF ("el ítem queda `En cuarentena`
  o `Rechazado`") no define qué condición (corrupto / ilegible / formato no
  soportado) lleva a cada rama; `spec-captura-ingesta.md` §8 ya lo señala
  como `[CLARIFICAR]` sin resolver. Implementarlo exige inventar una política
  de negocio no autorizada, así que se detuvo sin tocar código ni comitear.
  Pregunta registrada en QUESTIONS.md (2026-08-20).
  Siguiente paso: un humano responde la taxonomía de condiciones en
  QUESTIONS.md; T-03 (RF-RC-001, custodia del original) es la próxima tarea
  abierta en TODO.md que no depende de esa respuesta.
- F2: T-03 (RF-RC-001, custodia del original inmutable) implementado en
  `contexts/records-custodia` (paquete `sgdea.contexts.recordscustodia`):
  `CustodiaOriginales` guarda cada original en modo de una sola escritura,
  calcula su huella SHA-256 y expone `consultar`/`custodiar`; todo intento de
  `intentarModificar` un original ya custodiado se rechaza con
  `ModificacionDeOriginalRechazadaException` y deja un `EventoAuditoria` sin
  tocar los bytes ni la huella almacenados. TDD: 2 tests
  (`CustodiaOriginalesTest`) escritos contra el Dado/Cuando/Entonces de
  RF-RC-001 antes de la implementación; `./test.sh` en verde. El algoritmo de
  huella (SHA-256) es una decisión de implementación, no una referencia
  normativa inventada: satisface RNF-RC-001 ("robusto y verificable de forma
  independiente") sin citar ninguna norma ni umbral. El `[CLARIFICAR]` de
  spec §8 sobre encadenamiento de huellas en la bitácora queda fuera de
  alcance — es diseño de RF-RC-005/T-10, no de RF-RC-001. El evento de
  auditoría emitido aquí es un boceto mínimo (actor, fecha, tipo, estado
  anterior/posterior); la garantía de solo-anexado e inmutabilidad de la
  bitácora completa es T-10.
- F2: T-04 (RF-RC-002 + RF-CI-007, procedencia completa de punta a punta)
  implementado. Los dos contextos siguen desacoplados en compilación (cada
  módulo Gradle solo depende de `platform-kotlin`, sin dependencia cruzada
  captura-ingesta ↔ records-custodia), así que cada RF se probó contra su
  propio Dado/Cuando/Entonces:
  - `contexts/captura-ingesta`: `cargarLote` ahora recibe `fuente` y `fecha`
    y produce, por cada `ItemIngesta`, una `Procedencia` (fuente, fecha,
    disparador, loteOFlujoId). `disparador = "carga_por_lote"` nombra el
    mensaje de entrada "Carga de un lote" del §4 de la spec — no es una
    política de negocio inventada, es el nombre del evento de entrada ya
    declarado. Test nuevo: `RegistroDeProcedenciaTest`.
  - `contexts/records-custodia`: se añadió el agregado mínimo
    `DocumentoDeArchivo` (id, originalId, procedencia) y el value object
    `Procedencia` (fuente, fecha, loteOFlujoId) de spec §3. `custodiar` ahora
    exige una `Procedencia` y `consultarProcedencia(id)` la expone. El resto
    del agregado documento (metadatos, clasificación, estado de ciclo de
    vida) queda fuera de alcance — es RF-RC-003 en adelante. Test nuevo:
    `RegistroDeProcedenciaTest`.
  TDD: 4 tests nuevos/actualizados escritos antes de la implementación;
  `./test.sh` en verde (Gradle BUILD SUCCESSFUL — 3+1 tests en
  captura-ingesta, 2+1 en records-custodia, todos passing; pytest del
  arnés: 3 passed). No se tocó ningún `[CLARIFICAR]` de las specs.

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
