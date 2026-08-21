VETO: P-06 / specs/spec-infra-servicios.md §4 — `POST /trd` puede sobrescribir una versión de TRD ya publicada.

# Revisión de T-16, T-17 y T-18

Commits revisados: `e1473aa` (T-16), `760875f` y `6282640` (T-17), y
`82a7bc3` (T-18). Se revisaron contra `AGENTS.md`,
`.specify/memory/constitution.md`, `STATE.md` y
`specs/spec-infra-servicios.md`, en ese orden, y contra el contenido real de
los commits con `git show`.

## Motivos del veto

1. **RF-RC-006 queda violado por la persistencia de TRD.**
   `AlmacenDeTrdJpa.guardar` en
   `contexts/records-custodia/src/main/kotlin/sgdea/contexts/recordscustodia/persistencia/Almacenes.kt`
   llama `TrdVersionJpaRepository.save(...)` con una entidad cuyo `@Id` es la
   versión. Para una versión que ya existe, Spring Data JPA usa `merge`, por lo
   que el segundo `POST /trd` para la misma versión actualiza `vigente_desde` y
   `series_json`. Esto contradice directamente §4: una versión publicada
   «nunca se sobrescribe», y contradice el contrato que el dominio declara para
   `RegistroTrd.publicar`. La prueba HTTP solo publica una vez; no cubre el
   caso que debe rechazar la segunda publicación.

2. **Faltan dos llaves foráneas exigidas de forma expresa por §4.**
   `DocumentoEntity.originalId` y `SugerenciaEntity.documentoId` son columnas
   escalares: no tienen `@ManyToOne`/`@JoinColumn` ni una FK equivalente. Por
   tanto, el DDL de Hibernate no impide un `original_id` inexistente en
   `documentos_archivo`, ni un `documento_id` inexistente en `sugerencias`.
   La spec exige respectivamente esas dos llaves foráneas. El mapeo de
   `items_ingesta.lote_id` sí usa `@ManyToOne` y `@JoinColumn`; los dos mapeos
   de Records/Custodia deben alcanzar el mismo nivel de garantía.

3. **El formato de errores no se aplicó de forma consistente entre ambos
   servicios.** §5 lo deja como decisión técnica no bloqueante, pero requiere
   fijarla, documentarla y aplicarla consistentemente. Captura/Ingesta delega
   el 404 a `ResponseStatusException` y a la respuesta por defecto de Spring;
   Records/Custodia añade `ManejoDeErrores` y devuelve `{"error": ...}` para
   solo una familia de errores. Esto sí fija dos convenciones distintas, pese a
   que el comentario dice no fijar un formato. Debe definirse una única
   convención y aplicarse en los dos contextos (incluidos los 404).

## Comprobaciones que pasan

- **P-01 / RF-RC-004:** no hay endpoint que aplique una sugerencia al estado
  del documento. `POST /sugerencias` solo construye `SugerenciaEntrante` y la
  entrega a `CapaAnticorrupcionSugerencias.recibir`; esa capa continúa siendo
  la única puerta de sugerencias. La única ruta que materializa clasificación
  es `POST /documentos/{id}/decisiones`, que crea una `DecisionHumana` con
  actor y fecha y llama a `CustodiaOriginales.materializar`.
- **P-03 / aislamiento del dominio:** los puertos `AlmacenDeOriginales`,
  `AlmacenDeDocumentos`, `AlmacenDeEventos`, `AlmacenDeSugerencias` y
  `AlmacenDeTrd` están en el paquete de dominio; sus adaptadores JPA están en
  `persistencia/`, y el cableado de clases concretas queda en la configuración
  Spring. El dominio no importa JPA, Spring ni las implementaciones JPA.
  Captura/Ingesta tampoco anota sus tipos de dominio con JPA: el mapeo está en
  su adaptador de persistencia.
- **RF-RC-001 / RF-RC-005 a nivel de acceso a datos:** inspeccionado el código,
  `AlmacenDeOriginalesJpa.guardar` y `AlmacenDeEventosJpa.anexar` usan
  exclusivamente `EntityManager.persist`. No llaman `merge`, `update` ni
  `save`; sus lecturas son `find`/consultas. Esto satisface el tratamiento
  solicitado de INSERT solamente en esos dos adaptadores.
- **H2 de pruebas:** las justificaciones de ambos `src/test/resources/application.yml`
  son razonables para un sandbox sin Docker. Captura/Ingesta no tiene una
  garantía específica de Postgres que se esté eludiendo. En Records/Custodia,
  la garantía WORM evaluada aquí depende de que esos adaptadores usen `persist`
  y no de una característica propietaria de Postgres; H2 no la oculta. Sería
  conveniente añadir una prueba de integración que intente una segunda
  escritura del mismo original y compruebe su rechazo, pero no sustituye el
  hallazgo de TRD ni requiere Testcontainers para ser válida.
- **T-18 / P-02 y frontera de red:** los Dockerfiles usan el mismo código
  multi-módulo y Java 21 que el proyecto. Ambos compose conectan los servicios
  al `postgres` declarado y no publican `ports`, coherente con §7 mientras no
  exista Seguridad y Acceso. No se introdujo una capacidad probabilística,
  referencia normativa ni umbral nuevo.

## Verificación ejecutable

En este entorno de revisión, `./gradlew test --no-daemon` no pudo iniciarse:
el wrapper intenta crear su lock bajo `C:\\.gradle\\wrapper` y recibe permiso
denegado. Es una limitación de este sandbox, no un resultado rojo atribuible a
estos commits. La conclusión anterior se basa en inspección del código real;
la ejecución verde en el host y el smoke test con Postgres reportados para la
revisión siguen siendo evidencia complementaria, pero no corrigen los tres
incumplimientos señalados.
