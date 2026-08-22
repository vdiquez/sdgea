OK: sin objeciones al commit 65c3c43; corrige el VETO P-08 con una recepción transaccional y una prueba honesta de rollback.

# Revisión de `HEAD` — `65c3c43` (T-21)

Revisado contra `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`,
`specs/contexts/spec-records-custodia.md`, `specs/spec-infra-servicios.md` y el
diff completo de `git show HEAD`.

## Resultado

`RecepcionDeSugerenciasTransaccional.recibir` abre ahora una transacción Spring
que engloba la llamada completa a `CapaAnticorrupcionSugerencias.recibir`. Las
operaciones de `AlmacenDeSugerenciasJpa` y `AlmacenDeEventosJpa`, ambas con la
propagación `REQUIRED` predeterminada, participan en esa misma transacción. Si el
anexado del evento falla, la escritura anterior de la sugerencia se revierte. El
controlador HTTP usa este servicio transaccional, cerrando el defecto del commit
anterior en el camino de producción expuesto.

## Comprobaciones constitucionales

- **P-01:** pasa. La entrada procedente del componente probabilístico sigue
  cruzando `CapaAnticorrupcionSugerencias`, se traduce y persiste únicamente como
  `Sugerencia`, y no altera la clasificación ni otro estado de
  `DocumentoDeArchivo`. Este cambio no introduce una vía de materialización sin
  `DecisionHumana`.
- **P-03:** pasa. La capa de aplicación coordina el caso de uso sin consumir
  directamente JPA, H2/Postgres ni otra capacidad externa; el dominio continúa
  usando los puertos `AlmacenDeSugerencias` y `AlmacenDeEventos`, con los
  adaptadores concretos confinados al cableado Spring.
- **P-08:** pasa para el alcance corregido. La recepción de la sugerencia y el
  evento `SUGERENCIA_RECIBIDA` quedan en una sola unidad atómica: no puede
  confirmarse la transición si falla su auditoría. El evento nominal conserva
  actor, fecha, tipo y estados anterior/posterior conforme a RF-RC-005.
- **Honestidad de tests:** pasa. La prueba nueva no sustituye el almacén de
  sugerencias: ejecuta el servicio Spring y la persistencia JPA real sobre H2,
  sustituye solo el adaptador de eventos para provocar de manera controlada el
  fallo requerido, y después consulta el almacén real mediante
  `capa.sugerenciasDe`. Sin el `@Transactional` exterior, la sugerencia queda
  confirmada y la aserción falla; con la corrección, la consulta vacía demuestra
  el rollback. Esto prueba directamente la condición negativa que originó el
  VETO, no un doble en memoria predispuesto a pasar.
- **Specs, normativa y umbrales:** el commit no toca archivos bajo `specs/`. No
  introduce referencias normativas ni umbrales numéricos nuevos; los valores de
  fecha, confianza e identificadores del test son datos de ejemplo, no políticas
  ni gates normativos.

## Verificación ejecutable

Se intentó ejecutar `./test.sh` con un `GRADLE_USER_HOME` temporal. La suite no
llegó a iniciarse porque el wrapper intentó descargar Gradle 9.7.0 y la red del
sandbox está bloqueada (`java.net.SocketException: Operation not permitted`). La
conclusión se apoya por ello en la inspección estática del cableado, la semántica
transaccional y la prueba añadida; no se afirma una ejecución local verde.

El árbol de trabajo ya contenía modificaciones ajenas al commit en `.agents/`,
`.specify/`, `gradlew.bat` y `REVIEW.md`; solo se sobrescribió `REVIEW.md` como
solicitó la revisión. No se añadieron tareas a `TODO.md`.
