OK: T-59 es consistente con su alcance de infraestructura; no reclama ningun RF-UI cerrado.

# Revision de `9dfa94e` — correccion del VETO de T-59

## Alcance y consistencia

El commit solo cambia comentarios y registros de coordinacion de
`MarcaDeSimulacion`; no cambia su comportamiento. `TODO.md`, `STATE.md` y los
dos archivos del componente ahora la describen coherentemente como andamiaje
compartido de T-59 para las pantallas T-61+, no como RF-UI-012 entregado. La
afirmacion de T-59 — infraestructura pura, sin RF-UI todavia — vuelve a ser
consistente con sus artefactos.

RF-UI-012 sigue pendiente: requiere que toda pantalla que exponga una salida
FICTICIA use la marca comun. Aun no existe una de esas pantallas y el commit
lo declara expresamente; no hay cierre prematuro del RF.

## Principios constitucionales

- **P-01: conforme.** No se incorpora componente probabilistico, sugerencia
  que escriba estado ni materializacion automatica. La marca solo comunica que
  una futura salida FICTICIA es simulada.
- **P-03: conforme.** El diff no altera interfaces, clientes externos ni el
  proxy curado. No introduce consumo directo de una capacidad externa.
- **P-08: conforme / no aplica.** No hay transicion de estado de documento o
  expediente ni cambio de comportamiento que pueda omitir un evento de
  auditoria.

## Honestidad de pruebas

`MarcaDeSimulacion.prueba.tsx` prueba exactamente el alcance acotado de la
pieza pura: que su estado visible contiene texto que identifica la simulacion.
No se presenta como prueba de aceptacion de RF-UI-012; el comentario lo niega
de forma explicita y reserva esas pruebas para T-61+. Por tanto no esta
amanada ni oculta que falta la cobertura e2e del RF completo.

Verificacion ejecutada: `npm.cmd --prefix contexts/ui-demo test -- --run
src/componentes/MarcaDeSimulacion.prueba.tsx` — 1 archivo, 1 prueba, verde.

## Control de specs, normativa y umbrales

El commit no modifica ningun archivo bajo `specs/`; el control diferencial de
referencias normativas y umbrales no aplica. Tampoco introduce referencias
normativas ni valores numericos nuevos en los cuatro archivos modificados.
