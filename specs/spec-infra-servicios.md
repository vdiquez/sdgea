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
| `POST /documentos/{id}/decisiones` | `materializar(decision)` | RF-RC-004 |
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
`contexts/validacion-humana/.../ValidacionHumana.kt` (T-29): `ColaDeRevision`
y `GestionDeDecisiones`. A diferencia de los otros tres contextos, este
**no tiene persistencia propia** (`specs/007-validacion-humana/spec.md` §3):
sus tres puertos de dominio (`FuenteDeSugerencias`, `RegistradorDeDecisiones`,
`VerificadorDePermisos`) los implementan adaptadores HTTP reales
(`integracion/IntegracionHttp.kt`, T-30) contra `records-custodia` y
`seguridad-acceso` — la **primera integración HTTP real entre servicios** de
este proyecto; hasta T-29/T-30 cada contexto solo se había probado de forma
aislada (Postman contra uno a la vez).

| Método y ruta | Dominio que invoca | RF |
|---|---|---|
| `GET /colas/clasificacion?identidadId=` | `ColaDeRevision.ordenadasPorConfianza()` (tras verificar permiso) | RF-VH-001, RF-VH-002, RF-VH-007 |
| `GET /colas/clasificacion/masivo?identidadId=&umbral=` | `ColaDeRevision.candidatasAAprobacionMasiva(umbral)` | RF-VH-004, RF-VH-007 |
| `GET /colas/clasificacion/estado` | `ColaDeRevision.volumenYAntiguedadDeLaCola()` | RF-VH-010 |
| `POST /decisiones` | `GestionDeDecisiones.decidir(...)` | RF-VH-003, RF-VH-006, RF-VH-007, RF-VH-008 |
| `POST /decisiones/masivo` | `GestionDeDecisiones.aprobarEnBloque(...)` | RF-VH-004, RF-VH-006, RF-VH-007 |

Variables de entorno: `RECORDS_CUSTODIA_BASE_URL`, `SEGURIDAD_ACCESO_BASE_URL`
(mismo patrón que `DB_HOST`/`DB_PORT` en los otros contextos — parametrizar
por entorno para que el mismo código base sirva a SaaS y on-premise, P-02).

Sin mapeo de persistencia: no hay tablas propias de este contexto.

RF-VH-005 (confirmación/corrección de límites de documento) no se construyó
en T-29/T-30 porque Normalización no existía todavía como servicio — ver §7
(nueva) para su contrato ahora que sí existe. Cerrarlo en Validación Humana
(nuevo puerto `ConfirmadorDeLimites` + adaptador HTTP real) queda como tarea
explícita, no incluida en el alcance original de T-29/T-30.

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
| `POST /unidades/{id}/sugerencia-limites` | `recibir_sugerencia_de_limites(...)` | RF-NO-002 |
| `POST /unidades/{id}/confirmacion-limites` | `confirmar_limites(...)` | RF-NO-004 |
| `POST /unidades/{id}/normalizacion` | `normalizar(...)` | RF-NO-005 |
| `POST /unidades/{id}/validacion` | `marcar_cuarentena_o_rechazo(...)` | RF-NO-009 |
| `POST /unidades/{id}/entrega` | `entregar(...)` (huellas ya entregadas calculadas server-side) | RF-NO-006, RF-NO-010 |
| `GET /lotes/{lote_id}/conteo` | `contar_por_estado(...)` | RF-NO-008 |

Mapeo de persistencia (estructura, no DDL): `UnidadDocumentalCandidata` →
tabla `unidades_documentales`, con procedencia/sugerencia/confirmación
aplanadas en columnas propias (mismo criterio que `Procedencia` en
captura-ingesta) y `evidencia` serializada a JSON en una columna de texto
(mismo criterio que `evidencia_json` en records-custodia). Variables de
entorno idénticas a los contextos Kotlin: `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER`, `DB_PASSWORD`.

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

## 10. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Formato de error HTTP y convenciones de serialización
  (§8) — decisión técnica, no bloqueante.
- **[CLARIFICAR]** Integración real de autenticación/autorización: el
  contexto Seguridad y Acceso ya existe como servicio (T-23, 2026-08-25,
  §5), pero `captura-ingesta` y `records-custodia` todavía no lo llaman —
  sus endpoints siguen sin exigir una decisión de `POST /autorizacion` antes
  de responder. `validacion-humana` (T-30) sí lo llama de verdad, siendo el
  primer consumidor real de `/autorizacion`. Hasta que captura-ingesta y
  records-custodia hagan lo mismo, ambos siguen sin deber exponerse fuera de
  una red de confianza (docker-compose interno).
- **[CLARIFICAR]** RF-VH-005 (confirmación/corrección de límites de
  documento): Normalización ya existe como servicio (T-33/T-34, §7) y ya
  expone `POST /unidades/{id}/confirmacion-limites`, pero Validación Humana
  todavía no lo llama — mismo tipo de brecha que autenticación/autorización
  arriba, no cerrada en esta tarea.
- **[CLARIFICAR]** Recepción real de ítems desde Captura/Ingesta en
  Normalización (RF-NO-001): Captura/Ingesta no expone todavía un mecanismo
  para que Normalización descubra qué ítems están listos para recibir, ni
  una forma de marcarlos `Entregado` (RF-CI-010 sigue sin implementar, ver
  §3). `POST /unidades` de Normalización funciona hoy con los datos que le
  pase el llamador directamente.
