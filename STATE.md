# STATE
Fase: F2/F3 — T-13/T-14 resueltas y desglosadas en T-15..T-18, las cuatro
hechas. T-02 sigue bloqueada; es la única tarea `- [?]` que queda en TODO.md.
Ver plan-ejecucion-agentica.md.

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
- F2: T-05 (RF-CI-008, cero pérdida silenciosa) implementado en
  `contexts/captura-ingesta`: se amplió `EstadoItemIngesta` con los tres
  estados terminales de spec §3 (`ENTREGADO`, `RECHAZADO`, `EN_CUARENTENA`,
  además del `RECIBIDO` ya existente) y se añadió `contarPorEstado(lote)`,
  que devuelve un `ConteoPorEstado` con `total`, `terminales` (suma de
  Entregado+Rechazado+En cuarentena) y `sinPerdidaSilenciosa` (el invariante
  del RF). Alcance deliberadamente angosto: esta tarea NO implementa las
  transiciones que llevan a esos estados terminales (eso es RF-CI-006 —
  bloqueada por [CLARIFICAR], ver T-02 — y RF-CI-010, ninguna de las dos es
  tarea abierta en TODO.md); los tests construyen `ItemIngesta` directamente
  en cada estado terminal para probar solo el invariante de conteo, sin
  inventar la política de negocio de cuarentena/rechazo que sigue pendiente.
  TDD: 2 tests nuevos (`CeroPerdidaSilenciosaTest`) escritos contra el
  Dado/Cuando/Entonces de RF-CI-008 antes de la implementación (confirmado
  el fallo de compilación por referencias no definidas antes de implementar);
  `./test.sh` en verde.
- F2: T-06 (RF-CI-002, conciliación contra inventario) implementado en
  `contexts/captura-ingesta`: `conciliar(lote)` compara los ids de
  `InventarioOrigen.registros` contra los `ArtefactoOrigen.id` de los ítems
  recibidos y devuelve un `ReporteConciliacion` con `faltantes` (registros del
  inventario sin ítem recibido) y `sobrantes` (ítems recibidos sin registro en
  el inventario), tal como exige el Dado/Cuando/Entonces de RF-CI-002 y el
  invariante 4 de spec §3 ("cada registro del inventario y cada ítem recibido
  quedan explicados"). TDD: 3 tests nuevos (`ConciliacionContraInventarioTest`
  — cuadre exacto, faltante, sobrante) escritos contra el criterio antes de
  implementar (confirmado el fallo de compilación por `conciliar` no
  definido); `./test.sh` en verde (Gradle BUILD SUCCESSFUL; pytest del arnés:
  3 passed). No se tocó ningún `[CLARIFICAR]` de la spec — RF-CI-002 no tiene
  ninguno pendiente.
- F2: T-07 (RF-RC-006, TRD como objeto versionado) implementado en
  `contexts/records-custodia`: se añadieron `ReglaRetencion`, `Serie`,
  `Subserie` y `Trd` (versión, vigencia, árbol de series/subseries con sus
  reglas de retención) según el modelo de spec §3; `Clasificacion` referencia
  un documento, una serie/subserie y el número de versión de TRD usado; y
  `RegistroTrd.publicar(trd)` solo añade versiones — nunca sobrescribe ni
  retira una ya publicada, así que `registro.version(n)` sigue devolviendo la
  misma versión después de publicar una posterior. Alcance deliberadamente
  angosto ("estructura mínima" en TODO.md): no implementa la política de
  importación/publicación de una nueva versión de TRD (spec §8
  `[CLARIFICAR]`, "cómo se importa y se publica... sin afectar clasificaciones
  vigentes") — ese clarificar es sobre el mecanismo de publicación, no sobre
  el invariante estructural que exige RF-RC-006 (que las clasificaciones
  previas conserven su referencia de versión), que es lo único que esta tarea
  prueba. `Clasificacion` no está vinculada todavía a `DocumentoDeArchivo`
  (ese campo de clasificación es alcance de RF-RC-003 en adelante, ya
  señalado como fuera de alcance en T-04). TDD: 2 tests nuevos
  (`TrdComoObjetoVersionadoTest`) escritos contra el Dado/Cuando/Entonces de
  RF-RC-006 antes de la implementación; `./test.sh` en verde (Gradle BUILD
  SUCCESSFUL — 2 nuevos tests pasando en records-custodia; pytest del arnés:
  3 passed).
- F2: T-08 (RF-RC-003, recepción de sugerencias como propuestas) implementado
  en `contexts/records-custodia`: `Sugerencia` (spec §3: modelo, evidencia,
  confianza, tipo, contenido propuesto, fecha, vinculada a un documento) más
  `SugerenciaEntrante` (la entrada cruda que llega a la capa anticorrupción
  de spec §4) y `CapaAnticorrupcionSugerencias.recibir(...)`, que exige que
  el documento ya esté custodiado, traduce la entrada a `Sugerencia` y la
  guarda vinculada al documento — sin tocar `DocumentoDeArchivo` ni ningún
  estado. El "EMISOR FICTICIO" de TODO.md es `SugerenciaEntrante`: representa
  la salida de Clasificación/Enriquecimiento sin implementar ningún
  clasificador real, en línea con la constitución. Se añadió
  `CustodiaOriginales.consultarDocumento(id)` (mínimo necesario para probar
  que el documento no cambia). TDD: 2 tests nuevos
  (`RecepcionDeSugerenciasTest`) escritos contra el Dado/Cuando/Entonces de
  RF-RC-003 antes de la implementación (confirmado el fallo de compilación
  por símbolos no definidos); `./test.sh` en verde (Gradle BUILD
  SUCCESSFUL — records-custodia ahora con 2+1+2+2 tests; pytest del arnés:
  3 passed). No se tocó ningún `[CLARIFICAR]` de la spec — RF-RC-003 no
  tiene ninguno pendiente.

- F2: T-09 (RF-RC-004, materialización solo por decisión humana) implementado
  en `contexts/records-custodia`: se añadió `Clasificacion?` (nulable) a
  `DocumentoDeArchivo` y `DecisionHumana` (documentoId, actor, fecha,
  sugerenciasReferenciadas, clasificacionResultante) según spec §3.
  `CustodiaOriginales.materializar(decision)` es la única operación que
  puede fijar esa clasificación: actualiza el documento y añade un
  `EventoAuditoria` (`DECISION_HUMANA_MATERIALIZADA`) con el actor y la
  fecha de la decisión, satisfaciendo el primer Dado/Cuando/Entonces. El
  segundo ("sin decisión humana, el sistema lo impide") se prueba de forma
  estructural, igual que el patrón ya usado en T-03/T-08: no existe ningún
  otro método público que mute `clasificacion` — recibir una `Sugerencia`
  vía `CapaAnticorrupcionSugerencias.recibir` no la toca (ya lo garantizaba
  T-08 vía RF-RC-003, invariante 2 de spec §3), así que el test reconfirma
  que `clasificacion` sigue `null` tras recibir una sugerencia sin decisión
  aplicada. No se implementó el modelo completo de estados de ciclo de vida
  (spec §8 `[CLARIFICAR]`, "enumerar las transiciones válidas") — esta
  tarea solo materializa la clasificación, que es lo único que TODO.md pide
  y lo único que RF-RC-004 ejercita sin inventar esa taxonomía. TDD: 2 tests
  nuevos (`MaterializacionPorDecisionHumanaTest`) escritos contra el
  Dado/Cuando/Entonces de RF-RC-004 antes de la implementación; `./test.sh`
  en verde (Gradle BUILD SUCCESSFUL; pytest del arnés: 3 passed).

- F2: T-10 (RF-RC-005, bitácora inmutable de solo anexado) implementado en
  `contexts/records-custodia`: se extrajo `BitacoraAuditoria` (spec §3/§5),
  que reemplaza la lista privada de eventos que usaba `CustodiaOriginales`
  directamente. `anexar(evento)` es la única operación que añade contenido;
  `intentarModificar(indice, eventoNuevo)` e `intentarBorrar(indice)` son
  operaciones explícitas que siempre rechazan con
  `ModificacionDeEventoAuditoriaRechazadaException` sobre un evento ya
  anexado, satisfaciendo el segundo Dado/Cuando/Entonces del RF. El primer
  criterio ("existe un evento con actor, fecha, tipo, estado anterior y
  posterior") ya lo cumplían `custodiar`/`materializar`/`intentarModificar`
  de `CustodiaOriginales` desde T-03/T-09; el test nuevo lo verifica
  explícitamente sobre el evento `ORIGINAL_CUSTODIADO`. No se implementó
  encadenamiento de huellas entre eventos (spec §8 `[CLARIFICAR]`, "si la
  bitácora de auditoría usa además una cadena de huellas encadenadas para
  prueba de manipulación") — es una decisión de diseño adicional distinta
  del invariante "solo anexado, sin modificar ni borrar" que exige
  RF-RC-005 y que esta tarea sí prueba. TDD: 3 tests nuevos
  (`BitacoraDeAuditoriaInmutableTest`) escritos contra el Dado/Cuando/Entonces
  antes de la implementación (confirmado el fallo de compilación por
  símbolos no definidos); `./test.sh` en verde (Gradle BUILD SUCCESSFUL;
  pytest del arnés: 3 passed).
  Siguiente paso: T-11 (RF-RC-009, verificación de integridad por demanda)
  es la próxima tarea abierta en TODO.md.

- F2: T-11 (RF-RC-009, verificación de integridad por demanda) implementado
  en `contexts/records-custodia`: `CustodiaOriginales.verificarIntegridad(id,
  actor, fecha)` recalcula la huella del original y la compara contra la
  huella registrada; si no coincide, anexa un evento de auditoría
  (`DISCREPANCIA_DE_INTEGRIDAD`) y el `ResultadoVerificacionIntegridad`
  devuelto marca `coincide = false`, satisfaciendo el único
  Dado/Cuando/Entonces de RF-RC-009. `verificarTodos(actor, fecha)` corre esa
  verificación sobre todos los originales custodiados y agrega el resultado
  en un `ReporteVerificacionIntegridad` (con `discrepancias` derivado), para
  cubrir el "reportar discrepancias" (plural) del enunciado del RF sin
  inventar alcance adicional. La ejecución "de forma programada" que
  menciona el RF es responsabilidad de un disparador externo (cron/scheduler)
  que invoque este mismo método — no es lógica de dominio y queda fuera de
  esta tarea, documentado como tal en el código.
  Decisión de diseño: como el original inmutable nunca se puede mutar por API
  pública (invariante 1, ya garantizado desde T-03), no hay forma de producir
  una discrepancia real sin un seam. Se añadió un parámetro de constructor
  opcional `lectorDeAlmacenamiento: ((id) -> ByteArray)?` a
  `CustodiaOriginales`, con default `null` que hace que la verificación lea
  del propio registro en memoria (por lo que en operación normal siempre
  coincide); las pruebas lo sustituyen para simular divergencia del medio de
  almacenamiento (bit-rot / corrupción), sin exponer ninguna API de mutación
  del original. No es una capacidad externa real (P-03 ya cubre eso con
  `ObjectStorage` en `platform-kotlin`, sin usar todavía aquí) — es solo el
  seam mínimo para poder probar la rama de discrepancia del RF.
  TDD: 3 tests nuevos (`VerificacionDeIntegridadTest` — coincide, no
  coincide, reporte agregado con `verificarTodos`) escritos contra el
  Dado/Cuando/Entonces de RF-RC-009 antes de la implementación (confirmado
  el fallo de compilación por símbolos no definidos); `./test.sh` en verde
  (Gradle BUILD SUCCESSFUL — records-custodia con 3 tests nuevos pasando;
  pytest del arnés: 3 passed). No se tocó ningún `[CLARIFICAR]` de la spec —
  RF-RC-009 no tiene ninguno pendiente.
  Siguiente paso: T-12 (arnés — cargar set de juguete, correr componente
  ficticio, emitir boleta versionada) es la próxima tarea abierta en TODO.md.

- F2: T-12 (arnés — boleta versionada) implementado en `eval-harness/`. Cargar
  el set de juguete y correr el componente ficticio ya existían desde F1
  (`a890e83`); lo que faltaba, per `edd-harness.md` §3.6 ("el set patrón se
  versiona... una métrica solo es comparable entre corridas sobre la misma
  versión del set patrón") y §5.4 ("cada corrida produce una boleta de
  resultados versionada y comparable entre corridas"), era que la boleta
  cargara esa versión. Se añadió `SetPatron` (version + registros) como tipo
  de retorno de `cargar_set_patron`, se cambió el formato de la fixture JSON
  de un array plano a `{"version": ..., "registros": [...]}`, y `Boleta` ganó
  el campo `version`, tomado del `SetPatron` usado en `correr_arnes`. No se
  construyó una función de comparación entre boletas — "comparable" en la
  spec es la propiedad que da compartir el mismo campo `version`, no una
  operación nueva; añadir un comparador sería alcance no pedido. T-12 no
  referencia ningún RF de una spec de contexto (es infraestructura del arnés
  EDD, gobernada por `edd-harness.md`, no por un RF con Dado/Cuando/Entonces
  propio), así que las pruebas se derivaron directamente de los dos párrafos
  normativos del arnés citados arriba. TDD: 1 test nuevo
  (`test_boletas_de_la_misma_version_de_set_patron_son_comparables`) y los 3
  tests existentes actualizados para el nuevo formato, escritos antes de
  tocar `harness.py` (confirmado el fallo por `ImportError: cannot import
  name 'SetPatron'` antes de implementar); `./test.sh` en verde (Gradle BUILD
  SUCCESSFUL; pytest del arnés: 4 passed, antes 3). No se tocó ningún
  `[CLARIFICAR]` de `edd-harness.md` ni de `eval-clasificacion.md`.
  Siguiente paso: T-13 (CI: build + tests + arnés; gates AgentShield +
  security-review) es la próxima tarea abierta en TODO.md.

- F2/F3: T-13 (CI: build + tests + arnés; gates AgentShield + security-review)
  BLOQUEADA — marcada `- [?]` en TODO.md. La parte "build + tests + arnés" ya
  existe en `.github/workflows/ci.yml` desde F1 (`build-kotlin` +
  `build-python` corriendo `pytest` sobre `eval-harness/`). Lo que falta son
  los dos gates de seguridad nombrados en `plan-ejecucion-agentica.md`
  (líneas 59/135): "AgentShield" no tiene ninguna referencia técnica en todo
  el repo (ni paquete, ni GitHub Action, ni config) — es una decisión que el
  humano dice tener ya tomada pero nunca volcó al repo. Cablearlo exigiría
  inventar una referencia de acción de terceros para un job que se supone
  debe *prevenir* riesgo de cadena de suministro, no introducirlo. El gate de
  security-review sí es identificable (plugin de Anthropic, disponible como
  skill en este entorno) pero se dejó también sin implementar: cablear solo
  la mitad del gate dejaría el CI en verde con apariencia de estar protegido
  sin estarlo. Pregunta registrada en QUESTIONS.md (2026-08-21). No se tocó
  `.github/workflows/ci.yml`.
  Siguiente paso: un humano responde la referencia concreta de AgentShield y
  confirma la acción de security-review en QUESTIONS.md; T-14 (empaquetado
  dual P-02) es la próxima tarea abierta en TODO.md que no depende de esa
  respuesta.

- F2/F3: T-14 (empaquetado dual P-02: mismos contenedores como SaaS y como
  instalador appliance) BLOQUEADA — marcada `- [?]` en TODO.md. Los compose
  de F1 (`deploy/docker-compose.{saas,onprem}.yml`) difieren "Dockerfiles
  reales" hasta que `captura-ingesta`/`records-custodia` tuvieran código, y
  ya lo tienen (T-01..T-11). Pero verificado con grep sobre todo el repo: no
  existe ningún punto de entrada de aplicación (`fun main`,
  `@SpringBootApplication`, `FastAPI(`) en ningún contexto, y la dependencia
  de Spring Boot que STATE.md registra como decidida en F1.D1 nunca se
  añadió a ningún `build.gradle.kts`. Los tres módulos Kotlin con código
  (`platform-kotlin`, `captura-ingesta`, `records-custodia`) son bibliotecas
  puras probadas por tests unitarios, sin servidor ni proceso de larga
  duración. Escribir un Dockerfile "real" exigiría inventar un límite de
  servicio (¿REST por contexto? ¿monolito modular?) que ninguna spec de
  contexto define — violaría P-06 (SDD) hacerlo sin spec previa. Pregunta
  registrada en QUESTIONS.md (2026-08-21).
  Siguiente paso: un humano define el límite de servicio (HTTP vs monolito
  modular, framework de bootstrap, mapeo de persistencia) en QUESTIONS.md.
  Con T-02, T-13 y T-14 bloqueadas, no queda ninguna tarea `- [ ]` abierta en
  TODO.md — las tres tareas restantes de la lista original dependen de
  respuesta humana.
- F2/F3: T-13 y T-14 resueltas por Victor (QUESTIONS.md, 2026-08-21).
  T-13: `.github/workflows/ci.yml` gana el job `security-review`
  (`anthropics/claude-code-security-review`, dispara en `pull_request`,
  usa `secrets.ANTHROPIC_API_KEY`) y el job `agentshield-pendiente`
  (placeholder explícito y no bloqueante — AgentShield sigue sin decisión de
  herramienta). T-14: servicio HTTP por contexto (no monolito modular),
  Spring Boot confirmado como decisión activa, Postgres por contexto sin
  esquema compartido. T-15 (escribir `specs/spec-infra-servicios.md`) hecha:
  contrato HTTP mínimo para `captura-ingesta` y `records-custodia`, cada
  endpoint trazado a un método de dominio ya implementado — ninguno inventa
  regla de negocio nueva. Desglosada en T-16 (captura-ingesta servicio),
  T-17 (records-custodia servicio) y T-18 (Dockerfiles + compose), todas
  abiertas `- [ ]` en TODO.md. T-02 sigue siendo la única bloqueada.

- F3: T-16 (captura-ingesta como servicio HTTP + persistencia Postgres)
  implementado contra `specs/spec-infra-servicios.md` §3, sin tocar ninguna
  regla de dominio ya probada (T-01/T-05/T-06). Añadido en
  `contexts/captura-ingesta`:
  - `CapturaIngestaApplication.kt`: punto de entrada Spring Boot (primer
    `fun main`/`@SpringBootApplication` del repo — resuelve la mitad de lo
    que dejó bloqueado T-14 sobre falta de capa de aplicación).
  - `http/LotesController.kt`: los tres endpoints exactos de la tabla de la
    spec (`POST /lotes`, `GET /lotes/{id}/conteo`, `GET
    /lotes/{id}/conciliacion`), cada uno invocando directo `cargarLote` /
    `contarPorEstado` / `conciliar` ya existentes — ningún endpoint nuevo sin
    función de dominio detrás, tal como exige la spec §1.
  - `persistencia/Entidades.kt` + `LoteIngestaRepositorio.kt`: `LoteEntity`
    (tabla `lotes_ingesta`) y `ItemIngestaEntity` (tabla `items_ingesta`, FK
    `lote_id`) tal como mapea la spec §3; el dominio (`LoteIngesta` etc.)
    sigue sin anotaciones JPA — el repositorio es el único punto que traduce
    entre agregado inmutable y entidades persistentes. `inventario` (lista) se
    guarda serializada a JSON en una columna de texto — decisión de
    implementación no exigida literalmente por la spec (que solo fija
    "id, inventario -> tabla lotes_ingesta"), documentada en el código.
  Decisiones técnicas tomadas (no normativas, no de negocio):
  - Spring Boot **3.5.16** (línea estable más reciente a la fecha, verificada
    contra el índice real de Maven Central — no inventada) + `kotlin("plugin.spring")`/
    `kotlin("plugin.jpa")` en la misma versión que `kotlin("jvm")` (2.4.10) ya
    fijada en F1.D1, añadidos en `build.gradle.kts` raíz.
  - `ddl-auto: update` (Hibernate) en vez de una herramienta de migración: no
    hay ninguna decisión de Flyway/Liquibase en ninguna spec y decidir una
    aquí habría sido la misma clase de invención de arquitectura que bloqueó
    T-14 — documentado en `application.yml` como revisable.
  - Formato de error: default de Spring Boot vía `ResponseStatusException`
    (404 con cuerpo estándar), sin fijar RFC 7807 — la spec §5/§7 deja esto
    en `[CLARIFICAR]` explícitamente como "no bloqueante"; T-16 no lo resuelve,
    solo no lo necesita para sus tres endpoints.
  - Serialización: defaults de Jackson que trae Spring Boot (fechas ISO-8601
    vía `jackson-datatype-jsr310` autoconfigurado, `camelCase`); se añadió
    `jackson-module-kotlin` (estándar en cualquier proyecto Kotlin+Boot) para
    (de)serializar `List<String>` sin boilerplate de tipo genérico.
  TDD: 4 tests nuevos (`LotesControllerTest`, `@SpringBootTest` con
  `TestRestTemplate` sobre puerto aleatorio) escritos contra la tabla de
  endpoints de la spec §3 antes de escribir controlador/persistencia/entidad
  alguna — no hay Dado/Cuando/Entonces propio porque T-16 es infraestructura,
  no un RF (mismo tratamiento que T-12). Los tests cubren: `POST /lotes`
  traduce `cargarLote` correctamente (RF-CI-001/RF-CI-007), `GET .../conteo`
  lee un lote persistido en una petición POST anterior — prueba la
  persistencia entre peticiones que pide la spec, no solo el cálculo puro ya
  cubierto por T-05 (RF-CI-008), `GET .../conciliacion` igual para RF-CI-002,
  y un 404 sobre lote inexistente.
  Nota de entorno (no bloqueante, no es parte de esta tarea): en esta sesión
  `/repo/.gradle` y `/repo/.venv` son puntos de montaje con propietario
  `root`, no escribibles por el usuario `agent` (`Cannot create directory
  '/repo/.gradle/9.7.0/fileHashes'` / `Permission denied` en
  `.venv/CACHEDIR.TAG`) — no viene de este cambio (los directorios ya
  existían con ese dueño antes de tocar código, timestamps `Aug 21 00:42`).
  Verificado con overrides fuera del árbol del repo (`--project-cache-dir` /
  `-g` de Gradle, `UV_PROJECT_ENVIRONMENT` de uv) que no tocan ningún archivo
  versionado: `./gradlew test` completo (todos los módulos, incluidos los 4
  tests nuevos) y `pytest` del arnés (4 passed) en verde. No se modificó
  `test.sh` para no alterar el árbitro del loop sin que un humano decida cómo
  quiere resolver la propiedad de esos volúmenes/mounts.
  Siguiente paso: T-17 (records-custodia como servicio HTTP + persistencia
  Postgres, specs/spec-infra-servicios.md §4) es la próxima tarea abierta en
  TODO.md.

- F3: T-17 (records-custodia como servicio HTTP + persistencia Postgres)
  implementado contra `specs/spec-infra-servicios.md` §4, sin tocar ninguna
  regla de dominio ya probada (T-03/T-08/T-09/T-11). A diferencia de
  captura-ingesta (T-16, funciones puras + repositorio externo),
  `CustodiaOriginales`/`BitacoraAuditoria`/`CapaAnticorrupcionSugerencias`/
  `RegistroTrd` ya mantenían su propio estado en mapas/listas en memoria; la
  spec §4 pide explícitamente "reemplazar ese estado en memoria por tablas,
  sin cambiar el contrato de los métodos". Se resolvió así: cada una de esas
  cuatro clases gana un parámetro de constructor `Almacen...` (puerto) con
  una implementación en memoria por defecto idéntica a la que ya tenían —
  ningún test de dominio de T-03..T-11 cambió una sola línea, todos siguen en
  verde con `CustodiaOriginales()` sin argumentos — y una implementación
  respaldada por Postgres (`persistencia/Almacenes.kt`) que el servicio HTTP
  inyecta vía `configuracion/RecordsCustodiaConfig.kt`. Los once endpoints de
  la tabla de la spec §4 están en `http/` (`DocumentosController`,
  `SugerenciasController`, `VerificacionIntegridadController`,
  `TrdController`), cada uno invocando directo el método de dominio
  correspondiente; `intentarModificar` y las mutaciones de la bitácora
  siguen sin exponerse, tal como exige la spec.
  Decisión técnica (no normativa): `originales_inmutables` y
  `eventos_auditoria` (las dos tablas que la spec marca como "de solo
  inserción" / WORM) se escriben con `EntityManager.persist` en vez de
  `JpaRepository.save`, porque `save` sobre una entidad con `@Id` ya asignado
  hace `merge` (INSERT o UPDATE según exista la fila) — `persist` solo emite
  INSERT y falla en vez de sobrescribir, que es la garantía "a nivel de
  acceso a datos" que la spec pide literalmente para esas dos tablas.
  `documentos_archivo`, `sugerencias` y `trd_versiones` sí se actualizan con
  el `save()` normal de Spring Data porque no tienen esa exigencia (el
  documento cambia de clasificación en RF-RC-004). `evidencia` (lista) y el
  árbol de series/subseries de la TRD se serializan a JSON en una columna de
  texto, misma decisión que `inventario` en captura-ingesta (T-16).
  El formato de error HTTP sigue en `[CLARIFICAR]` (spec §5, no bloqueante):
  se añadió un `@RestControllerAdvice` mínimo que traduce
  `NoSuchElementException` (que ahora lanzan `consultar`/`consultarDocumento`/
  `consultarProcedencia`/`materializar`/`version` sobre un id inexistente,
  reemplazando el `getValue` de `Map` que lanzaba lo mismo antes del
  refactor) a 404, sin fijar RFC 7807.
  TDD: 9 tests nuevos (`RecordsCustodiaHttpTest`, `@SpringBootTest` con
  `TestRestTemplate` sobre puerto aleatorio) escritos contra la tabla de
  endpoints de la spec §4 antes de escribir controlador/persistencia/config
  alguna — no hay Dado/Cuando/Entonces propio porque T-17 es infraestructura,
  no un RF (mismo tratamiento que T-12/T-16). Cubren: custodiar + consultar
  original/documento/procedencia persistidos entre peticiones separadas
  (RF-RC-001/002), 404 sobre id inexistente, materializar decisión humana con
  persistencia entre peticiones (RF-RC-004), recibir sugerencia + consultarla
  (RF-RC-003), verificar integridad por documento y agregada (RF-RC-009), y
  publicar/consultar versión de TRD incluyendo 404 sobre versión inexistente
  (RF-RC-006). `./gradlew test` (todos los módulos, incluidos los 2 tests
  existentes de records-custodia sin cambios y los 4 de captura-ingesta) y
  `pytest` del arnés (4 passed) en verde.
  Nota de entorno (no bloqueante, misma causa que T-16): `/repo/.gradle` y
  `/repo/.venv` siguen siendo puntos de montaje de propietario `root` en esta
  sesión: verificado con overrides fuera del árbol del repo
  (`--project-cache-dir`/`-g` de Gradle, `UV_PROJECT_ENVIRONMENT` de uv), sin
  tocar `test.sh`. Nota adicional propia de esta tarea: el `application.yml`
  de test usa H2 **sin** `MODE=PostgreSQL` (a diferencia de captura-ingesta)
  porque ese modo de compatibilidad rechaza el tipo `BLOB` que Hibernate
  genera para la columna `bytes` de `originales_inmutables`; documentado en
  el propio archivo.
  Siguiente paso: T-18 (Dockerfiles reales + wiring en
  deploy/docker-compose.{saas,onprem}.yml) es la próxima tarea abierta en
  TODO.md — y la última de T-15..T-18.

- F3: T-18 (Dockerfiles reales + wiring en
  deploy/docker-compose.{saas,onprem}.yml) implementado, cerrando T-15..T-18.
  `contexts/captura-ingesta/Dockerfile` y `contexts/records-custodia/Dockerfile`:
  build multi-etapa `eclipse-temurin:21-jdk` (coincide con `jvmToolchain(21)`
  de ambos `build.gradle.kts`) → `./gradlew :contexts:<x>:bootJar -x test` →
  runtime `eclipse-temurin:21-jre` copiando el jar ejecutable
  (`<contexto>.jar`, ya observado en `build/libs/` desde T-16/T-17, no el
  `-plain.jar`). Tests omitidos en el build de imagen (`-x test`): decisión de
  implementación, no recorte de cobertura — `./test.sh` ya los corre en CI
  antes de que exista una imagen que construir. Como el build es multi-módulo
  (cada contexto depende de `platform-kotlin`), el contexto de build de Docker
  es la raíz del repo, no el directorio del contexto: los `Dockerfile` viven
  en `contexts/<x>/Dockerfile` pero se invocan con `context: ..` desde
  `deploy/`, documentado en el propio archivo. Se añadió `.dockerignore` en la
  raíz (excluye `.git/`, `.gradle/`, `.venv/`, `build/`, `.specify/`,
  `agent-sandbox/`) para no inflar ese contexto de build compartido.
  `deploy/docker-compose.{saas,onprem}.yml` ganan los servicios
  `captura-ingesta` y `records-custodia`, ambos apuntando al mismo `postgres`
  ya declarado (specs/spec-infra-servicios.md §2: Postgres por contexto sin
  esquema compartido, pero un solo servidor) vía las variables de entorno que
  ya leía `application.yml` desde T-16/T-17 (`DB_HOST=postgres`, etc.).
  Decisión deliberada: **sin `ports:`** (no se publica ningún puerto al host)
  en ninguno de los dos servicios, en ninguno de los dos modos — cita directa
  de `specs/spec-infra-servicios.md` §7 ("sin el contexto Seguridad y Acceso,
  estos servicios no deben exponerse fuera de una red de confianza"); otros
  servicios del mismo compose los alcanzan por nombre de servicio
  (`captura-ingesta:8081`, `records-custodia:8082`), no hace falta publicar
  puerto para eso. SaaS y on-premise quedan con el mismo wiring (P-02: un solo
  código base) porque ninguna de las seis capacidades de P-03 está en juego
  todavía para estos dos contextos.
  T-18 es infraestructura de empaquetado (como T-12/T-16/T-17), no un RF con
  Dado/Cuando/Entonces propio, así que no aplica TDD sobre criterios de
  negocio. Verificación de honestidad en su lugar: `./gradlew test` completo
  (16 tareas, BUILD SUCCESSFUL, sin tocar ningún test ni código de dominio) y
  `pytest` del arnés (4 passed) en verde tras el cambio — confirma que
  Dockerfiles/compose no rompieron nada existente. Los dos `docker-compose*.yml`
  se parsearon con PyYAML (`yaml.safe_load`) confirmando sintaxis válida y la
  ausencia de `ports:` en los dos servicios nuevos. **Límite de esta
  verificación, documentado explícitamente**: `docker` no está instalado en
  este entorno (mismo hallazgo que T-16/T-17 registraron para Testcontainers),
  así que ni la imagen ni el `docker compose up` real se construyeron ni se
  corrieron aquí — falta esa verificación de punta a punta cuando alguien la
  corra en un entorno con Docker disponible.
  Siguiente paso: con T-15..T-18 cerradas, T-02 (RF-CI-006, bloqueada por
  `[CLARIFICAR]`, ver QUESTIONS.md 2026-08-20) es la única tarea que queda
  `- [?]` en TODO.md — no queda ninguna tarea `- [ ]` abierta. Un humano debe
  responder la taxonomía de condiciones de cuarentena/rechazo para
  desbloquearla; hasta entonces no hay más trabajo autónomo de F2/F3 que
  tomar de TODO.md.

## Camino a F2 (checklist, 2026-08-20)

- [ ] 1. Enviar el one-pager a 3–5 entidades calificadas (F4, hilo comercial —
      sin dependencia técnica; seguimos con los pasos técnicos primero).
- [x] 2. Cerrar F1: `codex exec` revisó el esqueleto completo → REVIEW.md sin
      VETO.
- [x] 3. TEST_CMD fijado a `./test.sh`.
- [x] 4. TODO.md sembrado (14 tareas T-01..T-14).
- [x] 5. Sandbox Docker construido y verificado (`agent-sandbox/`).
- [x] 6. F2 corrido — 11/14 tareas hechas (T-01, T-03..T-12), 3 bloqueadas
      (T-02, T-13, T-14). Dos bugs reales encontrados y corregidos en el
      camino: `.venv` compartido entre host/contenedor se corrompía
      (aislado en volúmenes Docker nombrados), y `codex exec` corría en
      sandbox de solo lectura — podía revisar pero nunca escribir
      REVIEW.md, así que el VETO nunca se materializaba aunque el loop se
      veía en verde (corregido con `--sandbox workspace-write`).
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
