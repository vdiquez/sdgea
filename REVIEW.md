VETO: P-08 — la recepción de una sugerencia persiste la sugerencia sin anexar el evento de auditoría obligatorio.

# Revisión de `HEAD` — `582dd67` (T-19)

Revisado contra `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md` y
`specs/spec-infra-servicios.md`, contrastando el diff de `git show HEAD` y el
código efectivo de `HEAD` (no sus comentarios).

## Resultado de los cuatro motivos del veto anterior

1. **RF-RC-006 — corregido.** `RegistroTrd.publicar` consulta el puerto y
   rechaza una versión existente antes de guardar; además,
   `AlmacenDeTrdJpa.guardar` usa `EntityManager.persist`, no
   `JpaRepository.save`. Una segunda publicación ya no puede convertir el
   adaptador JPA en un `merge` que sobrescriba `trd_versiones`. Las pruebas de
   dominio y HTTP añadidas intentan la segunda publicación, esperan rechazo y
   verifican que permanece la fecha original.

2. **FK de documento y sugerencia — corregido.** `DocumentoEntity.original`
   es `@ManyToOne` con `@JoinColumn(name = "original_id", nullable = false)`
   hacia `OriginalEntity`; `SugerenciaEntity.documento` aplica el mismo mapeo
   para `documento_id` hacia `DocumentoEntity`. Los adaptadores obtienen las
   referencias con `EntityManager.getReference` y las consultas derivadas usan
   `findByDocumento_Id`. Ya no son las dos columnas escalares huérfanas del
   veto anterior.

3. **Formato de error entre servicios — corregido para el caso común que
   motivó el veto.** Captura/Ingesta dejó de lanzar `ResponseStatusException`:
   ambos servicios manejan `NoSuchElementException` con
   `@RestControllerAdvice`, HTTP 404 y el cuerpo `{"error": mensaje}`. Las dos
   pruebas HTTP comprueban tanto el 404 como el campo `error`. El 409 añadido
   para publicación TRD usa esa misma forma de cuerpo. Esto es una convención
   técnica permitida por §5; no fija RFC 7807, que sigue marcado
   `[CLARIFICAR]`.

4. **P-08 — sigue presente y es una violación real.**
   `CapaAnticorrupcionSugerencias.recibir(entrada, fecha)` consulta el
   documento, crea una `Sugerencia` y ejecuta `almacen.guardar(sugerencia)`.
   No recibe una `BitacoraAuditoria`, no llama `anexar` y no hay otro camino
   que anexe un evento al recibirla. El cableado Spring también la construye
   solo con `CustodiaOriginales` y `AlmacenDeSugerenciasJpa`; por tanto la
   inserción de `sugerencias` queda sin una inserción correspondiente en
   `eventos_auditoria`.

   P-08 nombra expresamente la «recepción de sugerencia» como transición que
   debe producir un evento inmutable, atribuible, fechado y con estado anterior
   y posterior. Que el documento no cambie protege P-01, pero no elimina la
   obligación de auditoría. El commit no modifica esta ruta ni incorpora una
   prueba que compruebe dicho evento. Se mantiene por ello el VETO.

## Comprobaciones adicionales

- **P-01:** pasa en el código revisado. La sugerencia se conserva como
  propuesta; `recibir` no llama a `materializar` ni guarda un
  `DocumentoDeArchivo` modificado. La materialización continúa requiriendo
  `DecisionHumana`.
- **P-03:** el cambio no introduce consumo directo de una capacidad externa en
  el dominio. Los almacenes siguen tras sus puertos y los adaptadores JPA
  permanecen en `persistencia/`; no se detectó violación nueva de P-03.
- **P-08 restante:** custodia, decisión humana, intento rechazado de modificar
  el original y discrepancia de integridad sí anexan eventos. La omisión de la
  recepción de sugerencia basta por sí sola para vetar. El acceso sin evento es
  deuda ya declarada fuera del contrato mínimo (RF-RC-010), no una regresión
  introducida por este commit.
- **Referencias y umbrales:** el diff no introduce referencia normativa ni
  valor umbral inventado. La mención de RFC 7807 ya figura en la spec como
  decisión pendiente y el commit no la adopta.

## Verificación ejecutable

`./test.sh` terminó correctamente (exit code 0) con un `GRADLE_USER_HOME`
temporal dentro del entorno de revisión. La suite verde no cubre el motivo
P-08: no existe una prueba que reciba una sugerencia y exija el evento de
auditoría atribuible con estados anterior y posterior.
