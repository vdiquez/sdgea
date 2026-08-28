# Spec de Infraestructura · Servicios HTTP por Bounded Context

| Campo | Valor |
|-------|-------|
| Tipo | Infraestructura — determinístico, gobernado por SDD |
| Estado | Borrador — Etapa F2 |
| Principios rectores | P-02, P-03, P-06, P-07 |
| Decidido por | Victor, 2026-08-21 (ver QUESTIONS.md, resolución de T-14) |

---

## 1. Propósito y frontera

Esta spec define **cómo se empaqueta y expone** el código de dominio que ya
existe (T-01 a T-12), no nuevas reglas de negocio. No reemplaza ni reinterpreta
`spec-captura-ingesta.md` ni `spec-records-custodia.md`: cada endpoint aquí
descrito traduce uno-a-uno una operación de dominio ya implementada y probada
por TDD. Si un endpoint no tiene una función/método de dominio existente que lo
respalde, no se implementa en la tarea que ejecute esta spec — se deja
`[CLARIFICAR]` o fuera de alcance, igual que exige la constitución para
cualquier otro código.

## 2. Decisión de arquitectura

- **Cada bounded context es su propio proceso/servicio HTTP** (Victor,
  2026-08-21) — no un monolito modular. Arrancó con `captura-ingesta` y
  `records-custodia`; `seguridad-acceso` es el tercero (2026-08-25);
  `validacion-humana` es el cuarto (2026-08-26) y el primero que no tiene
  persistencia propia — es un orquestador HTTP real sobre los otros dos (§6);
  `normalizacion` es el quinto (2026-08-26) y el primero en Python/FastAPI en
  vez de Kotlin/Spring (§7) — decisión de stack ya tomada antes de T-33 para
  los cinco contextos probabilísticos restantes. El resto de los contextos
  adoptan el mismo patrón (Python/FastAPI) cuando les toque turno.
- **Framework de bootstrap: Spring Boot para los contextos deterministas o
  híbridos ya construidos (captura-ingesta, records-custodia,
  seguridad-acceso, validacion-humana); Python/FastAPI para los
  probabilísticos** (F1.D1 fijó Spring Boot; la elección de Python/FastAPI
  para los probabilísticos ya estaba en `docker-compose.saas.yml` y en el
  workspace `uv` de la raíz antes de que se implementara ninguno).
- **Persistencia: Postgres por contexto, sin esquema compartido** — cada
  contexto mapea sus propios agregados a sus propias tablas, en el mismo
  `postgres` que ya declaran `deploy/docker-compose.{saas,onprem}.yml`. Un
  contexto nunca consulta ni escribe las tablas de otro directamente; si
  necesita datos de otro contexto, los recibe por su contrato de entrada (ver
  spec de dominio correspondiente), igual que hoy.

## 3. Contrato mínimo — `captura-ingesta`

Traduce las funciones de
`contexts/captura-ingesta/.../IngestaPorLote.kt`. Hoy son funciones puras sin
estado; exponerlas por HTTP exige que el `LoteIngesta` que produce
`cargarLote` se persista para que `contarPorEstado`/`conciliar` puedan
operar sobre el mismo lote en una petición posterior — eso es trabajo de
implementación (repositorio + mapeo a tabla), no una regla de negocio nueva.

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /lotes` | `cargarLote(loteId, artefactos, inventario, fuente, fecha)` | RF-CI-001, RF-CI-007 |
| `GET /lotes/{loteId}/conteo` | `contarPorEstado(lote)` | RF-CI-008 |
| `GET /lotes/{loteId}/conciliacion` | `conciliar(lote)` | RF-CI-002 |
| `POST /lotes/{loteId}/items/{itemId}/validacion` | `validar(item, condicion)` | RF-CI-006 |

Mapeo de persistencia (estructura, no DDL — el DDL exacto es decisión de
implementación de la tarea que ejecute esta spec):
- `LoteIngesta` (id, inventario) → tabla `lotes_ingesta`.
- `ItemIngesta` (id, loteId, artefacto, estado, procedencia, razonValidacion)
  → tabla `items_ingesta`, con `lote_id` como llave foránea a
  `lotes_ingesta`.

Fuera de alcance de esta spec (no implementado en el dominio todavía, por
tanto tampoco aquí): RF-CI-003 (flujo de eventos), RF-CI-004 (alta de
Fuente), RF-CI-005 (idempotencia), RF-CI-009 (reanudabilidad), RF-CI-010
(entrega a Normalización).

## 4. Contrato mínimo — `records-custodia`

Traduce los métodos públicos de
`contexts/records-custodia/.../CustodiaOriginales.kt`,
`CapaAnticorrupcionSugerencias` y `RegistroTrd`. A diferencia de
`captura-ingesta`, `CustodiaOriginales` ya mantiene estado propio (mapas en
memoria); persistirlo en Postgres es reemplazar ese estado en memoria por
tablas, sin cambiar el contrato de los métodos.

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /documentos` | `custodiar(id, bytes, actor, fecha, procedencia)` | RF-RC-001, RF-RC-002 |
| `GET /documentos/{id}/original` | `consultar(id)` | RF-RC-001 |
| `GET /documentos/{id}` | `consultarDocumento(id)` | RF-RC-001 |
| `GET /documentos/{id}/procedencia` | `consultarProcedencia(id)` | RF-RC-002 |
| `POST /documentos/{id}/decisiones` | `materializar(decision)` | RF-RC-004, RF-VH-009 (T-39) |
| `GET /documentos/correcciones` | `correccionesPendientesDeRerevision()` | RF-VH-009 (T-39) |
| `POST /documentos/{id}/verificacion-integridad` | `verificarIntegridad(id, actor, fecha)` | RF-RC-009 |
| `POST /verificacion-integridad` | `verificarTodos(actor, fecha)` | RF-RC-009 |
| `POST /sugerencias` | `CapaAnticorrupcionSugerencias.recibir(entrada, fecha)` | RF-RC-003 |
| `GET /documentos/{id}/sugerencias` | `sugerenciasDe(documentoId)` | RF-RC-003 |
| `GET /sugerencias/pendientes` | `CapaAnticorrupcionSugerencias.sugerenciasPendientes()` | RF-VH-001 (T-28) |
| `POST /trd` | `RegistroTrd.publicar(trd)` | RF-RC-006 |
| `GET /trd/{version}` | `RegistroTrd.version(numero)` | RF-RC-006 |

Intentar modificar un original (`intentarModificar`) y los eventos de
auditoría de solo-anexado (`BitacoraAuditoria.anexar` /
`intentarModificar`/`intentarBorrar`) **no se exponen como endpoints**: son
invariantes que el dominio ya hace cumplir internamente (RF-RC-001, RF-RC-005),
no operaciones que un cliente HTTP deba invocar. La bitácora se expone
solo para lectura, si una tarea de implementación la necesita para auditoría
externa — no está en este contrato mínimo porque ningún RF lo pide todavía.

Mapeo de persistencia (estructura, no DDL):
- `OriginalInmutable` → tabla `originales_inmutables`, escritura de una sola
  vez (RF-RC-001, invariante 1 de `spec-records-custodia.md` §3) — la tarea
  de implementación debe garantizar a nivel de acceso a datos, no solo de
  código de dominio, que un `UPDATE` sobre esta tabla nunca ocurre.
- `DocumentoDeArchivo` → tabla `documentos_archivo`, con `original_id` como
  llave foránea a `originales_inmutables`.
- `EventoAuditoria` (vía `BitacoraAuditoria`) → tabla `eventos_auditoria`, de
  solo inserción (RF-RC-005) — mismo tratamiento que `originales_inmutables`.
- `Sugerencia` → tabla `sugerencias`, con `documento_id` como llave foránea.
- `Trd` / `RegistroTrd` → tabla `trd_versiones`, con `version` como parte de
  la llave (RF-RC-006: nunca se sobrescribe una versión publicada).

Fuera de alcance de esta spec: RF-RC-007 (cálculo de retención — no
implementado en el dominio todavía), RF-RC-008 (expediente electrónico — no
implementado), RF-RC-010 (recuperación con evento de acceso — `consultar`
existe pero sin el evento de auditoría de acceso que pide el RF).

**RF-VH-009 (T-39)**: `DecisionHumana`/`EventoAuditoria` ganan un campo
`esCorreccion: Boolean` (default `false`, así que ningún otro sitio que
construye `EventoAuditoria` en este contexto necesitó cambiar). Lo asigna el
llamador (Validación Humana ya sabe si la decisión coincidió con la
sugerencia que la originó o la corrigió, `GestionDeDecisiones.
construirDecision`) — records-custodia no recalcula esa comparación, solo la
persiste y la expone en `GET /documentos/correcciones`, cada entrada marcada
con `estadoDeRevision: "PENDIENTE_DE_REREVISION"`. El mecanismo real de
re-revisión sigue `[CLARIFICAR]` (`specs/eval/edd-harness.md` §9); este
endpoint solo declara honestamente que la corrección todavía no se promovió
a verdad de referencia, no implementa el flujo de revisión en sí ni decide
cuándo se promueve.

**Bug real encontrado y corregido en T-39, en la verificación contra Docker
real (no en los tests de Gradle, que usan H2 con `ddl-auto: create-drop` y
por eso nunca lo habrían expuesto)**: agregar `es_correccion` como columna
`NOT NULL` sin `columnDefinition` generó `ALTER TABLE ... ADD COLUMN
es_correccion boolean not null` — Postgres lo rechaza sobre una tabla
`eventos_auditoria` que ya tiene filas ("contains null values"), porque
`ddl-auto: update` no es una herramienta de migración real (ya advertido en
`application.yml`). Corregido con `@Column(columnDefinition = "boolean not
null default false")`, que le da a la columna un valor por defecto real en
la base de datos, no solo en el objeto Kotlin.

## 5. Contrato mínimo — `seguridad-acceso`

Traduce las funciones de
`contexts/seguridad-acceso/.../SeguridadAcceso.kt` (T-23):
`GestionDeAccesos` (identidades, autenticación, autorización) y
`GestionDeRoles`.

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /identidades` | `GestionDeAccesos.crearIdentidad(id, actor, credencial, roles)` | RF-SA-001, RF-SA-002 |
| `POST /identidades/autenticacion` | `GestionDeAccesos.autenticar(actor, credencial, fecha)` | RF-SA-001 |
| `POST /identidades/{id}/roles` | `GestionDeAccesos.asignarRol(id, rol)` | RF-SA-002 |
| `DELETE /identidades/{id}/roles/{rol}` | `GestionDeAccesos.revocarRol(id, rol)` | RF-SA-002, RF-SA-006 |
| `POST /roles` | `GestionDeRoles.crear(nombre, permisos)` | RF-SA-002 |
| `POST /autorizacion` | `GestionDeAccesos.autorizar(identidadId, accion, tipoRecurso, nivelClasificacion, recurso, fecha)` | RF-SA-003, RF-SA-004, RF-SA-008 |
| `GET /eventos-seguridad` | `GestionDeAccesos.eventosDeSeguridad` | RF-SA-005, RF-SA-010 |

Mapeo de persistencia (estructura, no DDL):
- `Identidad` (id, actor, credencialHash, estado, roles) → tabla `identidades`,
  con `roles` como lista de nombres de rol (columna de texto/JSON, mismo
  tratamiento que `inventario` en captura-ingesta) — cada nombre se resuelve
  contra `roles` al reconstruir el dominio.
- `Rol` (nombre, permisos) → tabla `roles`, con `permisos` serializado a JSON
  (mismo tratamiento que `evidencia`/`series` en records-custodia).
- `EventoSeguridad` (actor, fecha, tipo, recurso) → tabla `eventos_seguridad`,
  de solo inserción (RF-SA-005/RNF-SA-003) — mismo tratamiento que
  `eventos_auditoria` en records-custodia: `EntityManager.persist`, nunca
  `merge`/`update`.

Fuera de alcance de esta spec: la integración real de `captura-ingesta` y
`records-custodia` con este servicio (que sus endpoints efectivamente llamen
a `POST /autorizacion` antes de responder) — sigue sin implementarse; ver §9
[CLARIFICAR] actualizado.

## 6. Contrato mínimo — `validacion-humana`

Traduce las funciones de
`contexts/validacion-humana/.../ValidacionHumana.kt` (T-29, ampliado en
T-38/T-39): `ColaDeRevision`, `GestionDeDecisiones`, `GestionDeLimites` y
`ColaDeLimites`. A diferencia de los otros tres contextos, este **no tiene
persistencia propia** (`specs/007-validacion-humana/spec.md` §3): sus cinco
puertos de dominio (`FuenteDeSugerencias`, `RegistradorDeDecisiones`,
`VerificadorDePermisos`, `ConfirmadorDeLimites`,
`FuenteDeSugerenciasDeLimites`) los implementan adaptadores HTTP reales
(`integracion/IntegracionHttp.kt`, T-30/T-38/T-39) contra `records-custodia`,
`seguridad-acceso` y `normalizacion` — la **primera integración HTTP real
entre servicios** de este proyecto; hasta T-29/T-30 cada contexto solo se
había probado de forma aislada (Postman contra uno a la vez).

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `GET /colas/clasificacion?identidadId=` | `ColaDeRevision.ordenadasPorConfianza()` (tras verificar permiso) | RF-VH-001, RF-VH-002, RF-VH-007 |
| `GET /colas/clasificacion/masivo?identidadId=&umbral=` | `ColaDeRevision.candidatasAAprobacionMasiva(umbral)` | RF-VH-004, RF-VH-007 |
| `GET /colas/clasificacion/estado` | `ColaDeRevision.volumenYAntiguedadDeLaCola()` | RF-VH-010 |
| `POST /decisiones` | `GestionDeDecisiones.decidir(...)` | RF-VH-003, RF-VH-006, RF-VH-007, RF-VH-008 |
| `POST /decisiones/masivo` | `GestionDeDecisiones.aprobarEnBloque(...)` | RF-VH-004, RF-VH-006, RF-VH-007 |
| `POST /unidades/{unidadId}/confirmacion-limites` | `GestionDeLimites.confirmar(...)` | RF-VH-005, RF-VH-007 |
| `GET /colas/limites?identidadId=` | `ColaDeLimites.ordenadasPorConfianza()` (tras verificar permiso) | RF-VH-001, RF-VH-002, RF-VH-007 (T-39) |
| `GET /colas/limites/estado` | `ColaDeLimites.volumenYAntiguedadDeLaCola()` | RF-VH-010 (T-39) |

Variables de entorno: `RECORDS_CUSTODIA_BASE_URL`, `SEGURIDAD_ACCESO_BASE_URL`,
`NORMALIZACION_BASE_URL` (T-38; mismo patrón que `DB_HOST`/`DB_PORT` en los
otros contextos — parametrizar por entorno para que el mismo código base
sirva a SaaS y on-premise, P-02).

Sin mapeo de persistencia: no hay tablas propias de este contexto.

**RF-VH-005 (T-38, cierra el hallazgo homónimo de la revisión acumulada de
Codex, `65c3c43..HEAD`, `TODO.md`)**: confirmación/corrección de límites de
documento. No se construyó en T-29/T-30 porque Normalización no existía
todavía como servicio (§7). `GestionDeLimites.confirmar(identidadId,
unidadId, actor, fecha)` verifica permiso (`accion="confirmar",
tipoRecurso="documento"`, mismo criterio uniforme que el resto de este
contexto) y reenvía a `POST /unidades/{id}/confirmacion-limites` en
Normalización vía `ConfirmadorDeLimitesHttp` — primer consumidor real de ese
endpoint (T-33/T-34). Normalización no distingue "confirmar" de "corregir"
como operaciones separadas (`confirmar_limites` admite límites "idénticos,
ajustados o re-trazados" bajo una única llamada, RF-NO-004), así que este
puerto no inventa una operación de corrección aparte que Normalización no
tiene. El endpoint HTTP de este contexto usa el mismo path que Normalización
(`POST /unidades/{id}/confirmacion-limites`) para que ambos contratos sean
simétricos.

**Bug real encontrado y corregido en T-38, no cubierto por los tests con
`MockRestServiceServer`** (que interceptan antes de abrir un socket real, así
que nunca lo habrían detectado): el `RestTemplate` compartido de este
contexto (`ClienteHttpConfig`) no fijaba una fábrica de peticiones HTTP
explícita, así que Spring Boot 3.5 elegía por defecto
`JdkClientHttpRequestFactory` (basado en `java.net.http.HttpClient`, sin
Apache HttpComponents/Jetty en el classpath). Ese cliente intenta, salvo que
se fije la versión explícitamente, un *upgrade* h2c en texto plano en su
primera petición HTTP/1.1. Tomcat (`records-custodia`, `seguridad-acceso`) lo
ignora sin problema; `uvicorn` (`normalizacion`, el primer backend no-Java de
este proyecto) lo rechaza como petición HTTP inválida y responde `400` sin
siquiera enrutarla a FastAPI — reproducido de punta a punta contra el stack
Docker real (`LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG` confirmó
`Response 400 BAD_REQUEST` seguido de `ServicioNoDisponibleException`, y los
logs de `normalizacion` mostraban `WARNING: Unsupported upgrade request.` /
`WARNING: Invalid HTTP request received.` sin ninguna línea de acceso — la
petición nunca llegó a la capa de aplicación). Corregido fijando
`HttpClient.Version.HTTP_1_1` explícito en el `HttpClient` que respalda al
`JdkClientHttpRequestFactory`; los timeouts se configuran directamente sobre
ese `HttpClient`/factory (no con `RestTemplateBuilder.connectTimeout/
readTimeout`, que dependen de reflexión contra una lista fija de fábricas
conocidas y no reconocen `JdkClientHttpRequestFactory`). Esto es relevante
para cualquier futuro cliente HTTP Kotlin→Python de este proyecto (los
cuatro contextos probabilísticos restantes seguirán el mismo patrón
Python/FastAPI/uvicorn que Normalización).

**RF-VH-001/002/010 (T-39)**: la spec (`specs/007-validacion-humana/spec.md`
§5, RF-VH-001) exige agregar sugerencias de los cuatro contextos
probabilísticos (Clasificación, Enriquecimiento, Normalización, Extracción)
"organizadas por tipo". `ColaDeLimites` + `FuenteDeSugerenciasDeLimitesHttp`
cierran la segunda cola real: agrega las unidades con sugerencia de límites
pendiente de Normalización (`GET /unidades/pendientes-de-limites`, §7),
ordenadas por confianza — una cola separada de `/colas/clasificacion`, no
una variante: revisar una sugerencia de límites no produce una
`DecisionDeClasificacion`, produce una confirmación (T-38). Sin ruta
`/masivo`: el `[CLARIFICAR]` de la spec (§8) sobre aprobación masiva para
sugerencias distintas de clasificación sigue abierto, no se inventa aquí.
**Extracción y Enriquecimiento quedan fuera de esta tarea, no por decisión
de diseño sino porque ninguno de los dos existe todavía como servicio real**
(`contexts/extraccion` y `contexts/enriquecimiento` son solo un
`main.py`/`pyproject.toml` de andamiaje, sin dominio ni HTTP) — mismo
criterio que RF-VH-005 esperó a que Normalización existiera (T-33) antes de
cerrarse en T-38; RF-VH-001 quedará completo cuando esos dos contextos
existan y expongan su propio `GET .../pendientes` análogo.

Consumidor de `GET /unidades/pendientes-de-limites` (Normalización, Python):
Jackson necesitó mapeo explícito de campos snake_case
(`integracion/Dtos.kt`, `@JsonProperty`) por primera vez en este contexto —
hasta T-38, las respuestas de Normalización que Validación Humana consumía
se descartaban sin parsear (`ConfirmadorDeLimitesHttp` usa `Map::class.java`
y no lee el cuerpo). `@JsonIgnoreProperties(ignoreUnknown = true)` porque
`UnidadPendienteDeLimites` solo necesita un subconjunto de lo que
Normalización expone (id, lote_id y la sugerencia de límites), no el
objeto completo.

**RF-VH-009 (T-39)**: ver §4 (records-custodia) para el contrato completo de
`GET /documentos/correcciones` y el bug real de DDL encontrado y corregido
en la verificación contra Docker. Del lado de Validación Humana, el único
cambio es que `RegistradorDeDecisionesHttp.materializar` ahora envía
`esCorreccion = decision.tipo == TipoDeDecision.CORRECCION` — un dato que
`GestionDeDecisiones.construirDecision` ya calculaba y descartaba antes de
T-39 (la respuesta HTTP inmediata al llamador sí incluía `tipo`, pero
records-custodia nunca lo recibía, así que no había ningún registro
durable de qué decisiones habían sido correcciones).

## 7. Contrato mínimo — `normalizacion`

Traduce las funciones de `contexts/normalizacion/dominio.py` (T-33):
`recibir_item`, `recibir_sugerencia_de_limites`, `confirmar_limites`,
`normalizar`, `marcar_cuarentena_o_rechazo`, `entregar`,
`contar_por_estado`. **Primer contexto en Python/FastAPI** de este proyecto
— decisión de stack ya tomada antes de esta tarea (`docker-compose.saas.yml`
ya declaraba "Python/FastAPI" para este contexto y los otros cuatro
probabilísticos; `contexts/normalizacion` ya era miembro del workspace `uv`
de la raíz con su propio `pyproject.toml`). Sigue la misma convención que
`eval-harness` (único proyecto Python que ya corría aquí): capa de dominio
sin dependencias de framework, `pytest`, dataclasses `frozen=True`.

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /unidades` | `recibir_item(...)` | RF-NO-001, RF-NO-003 |
| `GET /unidades/{id}` | consulta directa del almacén | — |
| `GET /unidades/pendientes-de-limites` | `pendientes_de_limites(...)` | RF-VH-001 (T-39) |
| `POST /unidades/{id}/sugerencia-limites` | `recibir_sugerencia_de_limites(...)` | RF-NO-002 |
| `POST /unidades/{id}/confirmacion-limites` | `confirmar_limites(...)` | RF-NO-004 |
| `POST /unidades/{id}/normalizacion` | `normalizar(...)` | RF-NO-005 |
| `POST /unidades/{id}/validacion` | `marcar_cuarentena_o_rechazo(...)` | RF-NO-009 |
| `POST /unidades/{id}/entrega` | `entregar(...)` (huellas ya entregadas calculadas server-side) | RF-NO-006, RF-NO-010 |
| `GET /lotes/{lote_id}/conteo` | `contar_por_estado(...)` | RF-NO-008 |
| `GET /eventos-auditoria` | `AlmacenDeUnidades.eventos_de_auditoria()` | RF-NO-008, P-08 |

`POST /unidades` (actor obligatorio en el cuerpo) y `POST
/unidades/{id}/normalizacion`, `POST /unidades/{id}/validacion`, `POST
/unidades/{id}/entrega` (actor y fecha obligatorios en el cuerpo) — añadidos
en el hallazgo V-01 (ver más abajo) para que cada transición sea atribuible.

**RF-VH-001 (T-39)**: `GET /unidades/pendientes-de-limites` filtra
`estado == PENDIENTE_DE_LIMITES and sugerencia_de_limites is not None` —
mismo criterio que `documentosSinClasificar`/`sugerenciasPendientes` en
records-custodia (T-28): una unidad sin sugerencia todavía no tiene nada que
un humano pueda revisar. Declarado ANTES de `GET /unidades/{id}` en
`api.py`: a diferencia de Spring MVC (que resuelve por especificidad de
patrón, ver §4 y §6), FastAPI/Starlette resuelve rutas por orden de
declaración — si `GET /unidades/{id}` fuera primero, "pendientes-de-limites"
se interpretaría como un `{id}` literal y esta ruta nunca se alcanzaría.

Mapeo de persistencia (estructura, no DDL): `UnidadDocumentalCandidata` →
tabla `unidades_documentales`, con procedencia/sugerencia/confirmación
aplanadas en columnas propias (mismo criterio que `Procedencia` en
captura-ingesta) y `evidencia` serializada a JSON en una columna de texto
(mismo criterio que `evidencia_json` en records-custodia). Variables de
entorno idénticas a los contextos Kotlin: `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER`, `DB_PASSWORD`.

**P-08 (hallazgo V-01 de la revisión acumulada de Codex, `65c3c43..HEAD`,
2026-08-27, ver `REVIEW.md`)**: la versión original de esta spec/T-33 no
persistía ningún evento de auditoría — cada dominio de transición
(`recibir_item`, `recibir_sugerencia_de_limites`, `confirmar_limites`,
`normalizar`, `marcar_cuarentena_o_rechazo`, `entregar`) ahora devuelve una
tupla `(UnidadDocumentalCandidata, EventoAuditoria)`, y
`AlmacenDeUnidades.guardar_con_evento(unidad, evento)` persiste ambos en una
única transacción SQLAlchemy (`merge` + `add` + `commit`, con
`rollback()` explícito si falla el `commit`) sobre una nueva tabla
`eventos_auditoria` (columnas: `actor`, `fecha`, `tipo`, `estado_anterior`,
`estado_posterior`) — mismo criterio que `eventos_auditoria` en
records-custodia y `eventos_seguridad` en seguridad-acceso. Verificado con un
test de atomicidad real (violación de restricción NOT NULL, no un doble
simulado) que confirma que si el evento no se puede anexar, la unidad
tampoco queda persistida (`tests/test_persistencia.py`).

Fuera de alcance de esta spec: la recepción real de ítems desde
Captura/Ingesta (RF-NO-001 asume que `lote_id`/`item_ingesta_id`/`procedencia`
ya llegan en la petición; Captura/Ingesta todavía no expone un mecanismo para
que Normalización los descubra — mismo tipo de brecha que
`GET /sugerencias/pendientes`, T-28, resolvió para Records/Custodia).

## 8. Formato de error y serialización

`[CLARIFICAR]` — esta spec no fija un formato de error HTTP (RFC 7807 u otro)
ni convenciones de serialización (fechas ISO-8601, `snake_case` vs
`camelCase` en JSON, etc.). Es una decisión de implementación transversal a
los cinco contextos; la tarea que ejecute esta spec debe fijarla y
aplicarla consistente en todos los servicios, documentándola en el código —
no queda bloqueada por esto porque no depende de una decisión de negocio, es
convención técnica interna. Nota real de esta tarea: FastAPI/Pydantic no
serializa las `@property` de un dataclass Python (a diferencia de
Kotlin/Jackson, que sí serializa `val ... get()`) — cada endpoint que
necesite exponer un valor derivado lo arma explícito en la capa HTTP
(`api.py`), no asumas que devolver el dataclass del dominio alcanza.

## 9. Trazabilidad

| Elemento | Traza a |
|---|---|
| Un servicio HTTP por contexto | Decisión de Victor, 2026-08-21; P-07 (cortes verticales) |
| Spring Boot (contextos deterministas/híbridos ya construidos) | F1.D1; confirmado activo 2026-08-21 |
| Python/FastAPI (contextos probabilísticos) | Decisión ya tomada antes de T-33 — `docker-compose.saas.yml` y el workspace `uv` de la raíz ya la reflejaban |
| Postgres por contexto, sin esquema compartido | Decisión de Victor, 2026-08-21; P-02 (mismo código base, ambos modos de despliegue) |
| Cada endpoint traduce un método de dominio ya probado | P-06 (spec antes de código); TDD ya aplicado en T-01..T-11, T-23, T-29, T-33 |
| validacion-humana como orquestador HTTP real, sin persistencia propia | Decisión de Victor, 2026-08-26; specs/007-validacion-humana/spec.md §3 |
| RF-VH-005 cerrado (Validación Humana confirma límites vía Normalización) | Decisión de Victor, 2026-08-27 ("Cierra el ciclo RF-VH-005"), TODO.md T-38 |
| RF-VH-001/002/010/009 ampliados (cola de límites; correcciones pendientes de re-revisión) | Decisión de Victor, 2026-08-27 ("Si, sigamos con T-39"), TODO.md T-39 |
| RF-EX-011 exige verificar autorización real (`VerificadorDeAutorizacion` + `POST /autorizacion`) | VETO real de Codex sobre commit `cf93d84`, ver `REVIEW.md` y `QUESTIONS.md` 2026-08-27; P-01, P-03 |
| `clasificacion` como orquestador HTTP sin persistencia propia, Python/FastAPI | Decisión de Victor, 2026-08-28 ("Sigamos con Clasificación"); `specs/003-clasificacion/spec.md` §3, TODO.md T-45 |

## 10. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Formato de error HTTP y convenciones de serialización
  (§8) — decisión técnica, no bloqueante.
- **[CLARIFICAR]** Integración real de autenticación/autorización: el
  contexto Seguridad y Acceso ya existe como servicio (T-23, 2026-08-25,
  §5), pero `captura-ingesta` y `records-custodia` todavía no lo llaman —
  sus endpoints siguen sin exigir una decisión de `POST /autorizacion` antes
  de responder. `validacion-humana` (T-30) sí lo llama de verdad, siendo el
  primer consumidor real de `/autorizacion`; `extraccion` (2026-08-27, VETO
  de Codex sobre commit `cf93d84`, ver §11) es el segundo, pero solo para
  `POST /textos/{id}/confirmacion` — el único endpoint cuyo RF (RF-EX-011)
  exige literalmente un "actor autorizado". Hasta que captura-ingesta y
  records-custodia hagan lo mismo, ambos siguen sin deber exponerse fuera de
  una red de confianza (docker-compose interno).
- ~~RF-VH-005 (confirmación/corrección de límites de documento)~~ — **cerrado
  en T-38**: `GestionDeLimites` + `ConfirmadorDeLimitesHttp` en Validación
  Humana ya llaman a `POST /unidades/{id}/confirmacion-limites` en
  Normalización (§6).
- **[CLARIFICAR]** Recepción real de ítems desde Captura/Ingesta en
  Normalización (RF-NO-001): Captura/Ingesta no expone todavía un mecanismo
  para que Normalización descubra qué ítems están listos para recibir, ni
  una forma de marcarlos `Entregado` (RF-CI-010 sigue sin implementar, ver
  §3). `POST /unidades` de Normalización funciona hoy con los datos que le
  pase el llamador directamente.
- RF-VH-001 (T-39, parcial): las colas de clasificación (records-custodia) y
  de límites (Normalización) ya agregan sugerencias reales; Extracción y
  Enriquecimiento quedan pendientes **porque ninguno de los dos existe
  todavía como servicio** (`contexts/extraccion`/`contexts/enriquecimiento`
  son solo andamiaje) — no es una decisión de negocio ni un `[CLARIFICAR]`,
  es una dependencia real que no existe. Se completará cuando esos contextos
  tengan dominio y HTTP.
- **[CLARIFICAR]** Mecanismo de re-revisión de correcciones (RF-VH-009):
  `GET /documentos/correcciones` en records-custodia (T-39) expone las
  correcciones marcadas `PENDIENTE_DE_REREVISION`, pero cómo se re-revisan y
  se promueven a verdad de referencia del set patrón sigue
  `[CLARIFICAR]` — ya lo estaba en `specs/eval/edd-harness.md` §9 antes de
  esta tarea; T-39 no lo resuelve, solo expone las candidatas.

## 11. Contrato mínimo — `extraccion`

Traduce las funciones de `contexts/extraccion/dominio.py` (T-40, corregido en
tres rondas de revisión de Codex — ver `QUESTIONS.md` 2026-08-27):
`recibir_unidad`, `determinar_soporte`, `extraer_texto_born_digital`,
`recibir_sugerencia_ocr`, `confirmar_extraccion`,
`marcar_cuarentena_o_rechazo`, `entregar`, `candidatas_a_revision_por_baja_confianza`,
`contar_por_estado`. Sigue el mismo patrón Python/FastAPI/SQLAlchemy que
Normalización (T-33..T-37): capa de dominio sin dependencias de framework,
`guardar_con_evento` persiste el agregado y su `EventoAuditoria` en una única
transacción (P-08 desde el primer commit, no como fix posterior — lección de
V-01/T-37).

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /textos` | `recibir_unidad(...)` | RF-EX-001 |
| `GET /textos/pendientes-de-revision?umbral=` | `candidatas_a_revision_por_baja_confianza(...)` | RF-EX-006 |
| `GET /textos/{id}` | consulta directa del almacén | — |
| `POST /textos/{id}/soporte` | `determinar_soporte(...)` | RF-EX-002 |
| `POST /textos/{id}/extraccion-born-digital` | `extraer_texto_born_digital(...)` | RF-EX-003 |
| `POST /textos/{id}/sugerencia-ocr` | `recibir_sugerencia_ocr(...)` | RF-EX-004 |
| `POST /textos/{id}/confirmacion` | `confirmar_extraccion(...)` | RF-EX-011 |
| `POST /textos/{id}/validacion` | `marcar_cuarentena_o_rechazo(...)` | RF-EX-009 |
| `GET /textos/{id}/entrega` | `entregar(...)` | RF-EX-010 |
| `GET /lotes/{lote_o_flujo_id}/conteo` | `contar_por_estado(...)` | RF-EX-008 |
| `GET /eventos-auditoria` | `AlmacenDeTextos.eventos_de_auditoria()` | RF-EX-008, P-08 |

`GET /textos/pendientes-de-revision` declarado ANTES de `GET /textos/{id}` en
`api.py` — mismo motivo real que en normalizacion (T-39): FastAPI/Starlette
resuelve rutas por orden de declaración, a diferencia de Spring MVC.
`umbral` es obligatorio, sin valor por defecto: el umbral de calidad sigue
`[CLARIFICAR]` en `specs/002-extraccion/spec.md` §8, así que nunca se
inventa uno en el código — lo declara el llamador en cada consulta.

`GET /textos/{id}/entrega` es de solo lectura (no transiciona estado —
`Extraído` ya es terminal de éxito): valida y devuelve, sin anexar evento ni
recibir actor/fecha; un texto no `Extraído` responde 409 vía el mismo
manejador de `ErrorDeDominio` que el resto del módulo.

Mapeo de persistencia (estructura, no DDL): `TextoExtraido` → tabla
`textos_extraidos`, con procedencia y sugerencia de OCR aplanadas en columnas
propias (mismo criterio que `UnidadDocumentalEntity` en normalizacion) y
`evidencia` serializada a JSON en una columna de texto. Variables de entorno
idénticas a los otros contextos: `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`.

Autorización (corregido 2026-08-27, VETO real de Codex sobre commit `cf93d84`,
ver `REVIEW.md`): a diferencia de `confirmar_limites` en Normalización y
`materializar` en records-custodia (cuyo Dado/Cuando/Entonces solo exige "una
decisión humana"/"un humano"), el de RF-EX-011 dice literalmente "un actor
**autorizado** la confirma" — Codex sostuvo que aceptar cualquier `str` como
actor no cumplía ese criterio. `confirmar_extraccion(texto, actor, fecha,
verificador)` ahora exige un puerto `VerificadorDeAutorizacion`
(`dominio.py`, P-03) y rechaza con `AccesoDenegadoError` (HTTP 403) si el
actor no tiene el permiso `confirmar`/`documento`. `POST
/textos/{id}/confirmacion` lo inyecta vía `Depends(obtener_verificador)`
(`api.py`), implementado por `VerificadorDeAutorizacionHttp`
(`integracion.py`) — primer consumidor **Python** de `POST /autorizacion` en
seguridad-acceso (Kotlin ya lo consumía desde `validacion-humana`, T-30).
Variable de entorno nueva: `SEGURIDAD_ACCESO_BASE_URL`
(default `http://localhost:8083`, mismo patrón que `RECORDS_CUSTODIA_BASE_URL`
en validacion-humana). El resto de las funciones de dominio de Extracción
(`recibir_unidad`, `determinar_soporte`, etc.) sigue sin verificar
autorización — solo RF-EX-011 lo exige literalmente en su criterio; extenderlo
a los demás endpoints sería alcance no pedido. La brecha que sigue abierta
(§10) es que `captura-ingesta`/`records-custodia`/`confirmar_limites` en
Normalización todavía no llaman a `/autorizacion` — ninguno de esos RFs usa la
palabra "autorizado" en su criterio, así que no están cubiertos por este
mismo VETO.

Fuera de alcance de esta spec: recepción real de unidades desde Normalización
(RF-EX-001 asume que la unidad ya llega en la petición, mismo tipo de brecha
que Normalización tiene con Captura/Ingesta); entrega real a Clasificación,
Enriquecimiento e Indexación y Búsqueda (`entregar()` es una validación de
lectura, no una integración HTTP real — Clasificación ya existe como servicio
desde T-45 (§12), pero Extracción todavía no la llama; Enriquecimiento e
Indexación y Búsqueda siguen sin existir).

## 12. Contrato mínimo — `clasificacion`

Traduce las funciones puras de `contexts/clasificacion/dominio.py` (T-44):
`recibir_texto_extraido`, `clasificar`, `ordenar_por_confianza`, `agrupar`,
`marcar_no_clasificable`, `a_sugerencia_saliente_de_clasificacion`,
`a_sugerencia_saliente_de_agrupamiento`. A diferencia de `extraccion`/
`normalizacion` (persistencia propia con `guardar_con_evento`) y como
`validacion-humana` (Kotlin), este contexto **no tiene persistencia propia**
(`specs/003-clasificacion/spec.md` §3: "no mantiene estado propio de sus
sugerencias después de entregarlas") — es el primer orquestador HTTP sin
tablas propias en Python: cada endpoint compone funciones puras de dominio y
reenvía el resultado a `records-custodia` vía `POST /sugerencias`
(`integracion.py`, `EnviadorDeSugerenciasHttp`, cliente `httpx` real).

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `POST /clasificaciones` | `recibir_texto_extraido(...)` + `clasificar(...)` (una vez por candidata) + `ordenar_por_confianza(...)` + reenvío a `POST /sugerencias` (`tipo="clasificacion"`) por cada una, en orden | RF-CL-001, RF-CL-002, RF-CL-003, RF-CL-004, RF-CL-008, RF-CL-009 |
| `POST /agrupamientos` | `recibir_texto_extraido(...)` + `agrupar(...)` + reenvío a `POST /sugerencias` (`tipo="agrupamiento"`) | RF-CL-001, RF-CL-005, RF-CL-006, RF-CL-008 |
| `POST /no-clasificables` | `recibir_texto_extraido(...)` + `marcar_no_clasificable(...)` | RF-CL-010 |

`POST /clasificaciones` acepta una o más candidatas en un solo cuerpo (una
petición, un texto, N candidatas de serie/subserie ya calculadas por el
llamador FICTICIO) porque RF-CL-003 exige que, "cuando existe más de una
candidata razonable", las sugerencias se expongan ordenadas por confianza
descendente — ranking que solo tiene sentido sobre un conjunto conocido en el
momento de la petición, no acumulado entre peticiones (este contexto no
guarda estado). `POST /agrupamientos` acepta una sola candidata por petición:
ningún RF exige ranking de expedientes candidatos, a diferencia de
RF-CL-003 para series/subseries.

`POST /no-clasificables` no reenvía nada a `records-custodia`: la tabla de
salidas de la spec (§4) nombra su destino como "Operador" (reporte), no
Records/Custodia — sin persistencia propia, la respuesta HTTP síncrona con la
`MarcaNoClasificable` es ese reporte; no se inventa un almacén ni un canal de
notificación adicional que la spec no pide.

Cuerpo de `POST /sugerencias` que construye `EnviadorDeSugerenciasHttp`
(`documentoId`/`tipo`/`contenidoPropuesto`/`modeloId`/`evidencia`/
`confianza`/`fecha` en camelCase, mismo criterio que
`VerificadorDeAutorizacionHttp` en extraccion, T-41b, porque Spring/Jackson
serializa así del lado de records-custodia) — ver §4 y
`http/Dtos.kt::RecibirSugerenciaRequest` para el contrato exacto que expone
records-custodia.

Sin mapeo de persistencia: no hay tablas propias de este contexto (mismo
criterio que `validacion-humana`, §6).

Variables de entorno: `RECORDS_CUSTODIA_BASE_URL` (default
`http://localhost:8082`, mismo patrón que en `validacion-humana`/`extraccion`).

Manejo de errores: `dominio.ErrorDeDominio` (texto no recibido en `Extraído`)
→ 409; `ServicioNoDisponibleError` (`records-custodia` no responde o responde
error) → 502, mismo criterio que `ServicioNoDisponibleException` en
`validacion-humana` (§6, Kotlin) — aquí sin envolver una excepción Spring,
`EnviadorDeSugerenciasHttp.enviar` atrapa `httpx.HTTPError` (fallos de
transporte y respuestas no-2xx vía `raise_for_status()`) y la traduce.

Prueba de integración honesta (T-45, para no repetir la brecha real que dejó
`extraccion`/T-41b — ver nota en §11: `VerificadorDeAutorizacionHttp` nunca
tuvo un test que verificara la forma exacta de su petición saliente, solo un
doble en la capa de API): `tests/test_integracion.py` inyecta un
`httpx.Client(transport=httpx.MockTransport(...))` en
`EnviadorDeSugerenciasHttp` y verifica método, URL y cuerpo JSON exactos de la
petición que construye, mismo criterio de honestidad que `IntegracionHttpTest`
en `validacion-humana` (Kotlin, T-30) con `MockRestServiceServer`.
`tests/test_api.py` sigue el patrón de dependency-injection con un doble
(`_EnviadorDePrueba`) para probar la composición HTTP↔dominio sin red, mismo
criterio que el resto de los contextos Python.
