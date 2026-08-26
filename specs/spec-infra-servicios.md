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
  `records-custodia`; `seguridad-acceso` es el tercero (2026-08-25); el resto
  de los contextos adoptan el mismo patrón cuando les toque turno.
- **Framework de bootstrap: Spring Boot** — decisión activa (F1.D1,
  confirmada como no-aspiracional el 2026-08-21), no una spec nueva.
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
a `POST /autorizacion` antes de responder) — sigue sin implementarse; ver §8
[CLARIFICAR] actualizado.

## 6. Formato de error y serialización

`[CLARIFICAR]` — esta spec no fija un formato de error HTTP (RFC 7807 u otro)
ni convenciones de serialización (fechas ISO-8601, `snake_case` vs
`camelCase` en JSON, etc.). Es una decisión de implementación transversal a
los tres contextos; la tarea que ejecute esta spec debe fijarla y aplicarla
consistente en todos los servicios, documentándola en el código — no queda
bloqueada por esto porque no depende de una decisión de negocio, es
convención técnica interna.

## 7. Trazabilidad

| Elemento | Traza a |
|---|---|
| Un servicio HTTP por contexto | Decisión de Victor, 2026-08-21; P-07 (cortes verticales) |
| Spring Boot | F1.D1; confirmado activo 2026-08-21 |
| Postgres por contexto, sin esquema compartido | Decisión de Victor, 2026-08-21; P-02 (mismo código base, ambos modos de despliegue) |
| Cada endpoint traduce un método de dominio ya probado | P-06 (spec antes de código); TDD ya aplicado en T-01..T-11, T-23 |

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Formato de error HTTP y convenciones de serialización
  (§6) — decisión técnica, no bloqueante.
- **[CLARIFICAR]** Integración real de autenticación/autorización: el
  contexto Seguridad y Acceso ya existe como servicio (T-23, 2026-08-25,
  §5), pero `captura-ingesta` y `records-custodia` todavía no lo llaman —
  sus endpoints siguen sin exigir una decisión de `POST /autorizacion` antes
  de responder. Hasta que esa integración se implemente, ambos servicios no
  deben exponerse fuera de una red de confianza (docker-compose interno),
  igual que antes de que Seguridad y Acceso existiera.
