OK: `591cc50` corrige íntegramente la segunda observación sobre T-62 y cierra correctamente T-65; sin VETO constitucional.

# Revisión de `591cc50` — refuerzo e2e de T-62 y cierre de T-65

## Dictamen

El e2e exige ahora explícitamente `HTTP 2xx` para ambas lecturas de `GET /documentos/correcciones`, antes de consumir sus cuerpos JSON. Esto elimina el falso positivo señalado en la revisión de `a7af4b8`: dos respuestas de error sin propiedad `length` ya no pueden satisfacer por accidente `undefined === undefined`.

El cambio mantiene la comprobación servidor-a-servidor del efecto materializado de la decisión: una aceptación exacta de `serie/subserie` no incrementa las correcciones pendientes de re-revisión. No añade mocks ni interceptores y no abre una ruta de navegador hacia Records/Custodia.

T-65 queda cerrada de forma consistente. La tarea describía precisamente el defecto ya resuelto en `a7af4b8`: `GestionDeDecisiones.construirDecision` reconstruye el valor esperado como `serieId/subserieId` cuando corresponde, conserva el formato sin subserie y tiene pruebas para aceptación y corrección. Marcarla `[x]` no encubre trabajo de dominio pendiente ni introduce un requisito nuevo.

## Constitución y specs

- **P-01 y P-09: conforme.** La clasificación continúa como sugerencia FICTICIA y la materialización depende de la acción explícita del operador; la prueba refuerza ese resultado, sin automatizar decisiones.
- **P-03: conforme.** No hay nueva integración ni consumo directo de capacidad externa. La consulta directa a Records/Custodia es únicamente setup/observación del e2e mediante el puerto local documentado, no una ruta de la UI.
- **P-08: conforme.** Se conserva la comprobación de que la aceptación no se registra incorrectamente como corrección; el commit no altera el modelo ni la bitácora de auditoría.
- **RF-UI-005 y RF-VH-008/009: conforme.** El refuerzo prueba honestamente la distinción aceptación/corrección y la observabilidad de correcciones requerida por las specs aplicables.

El commit no modifica `specs/`, no introduce referencias normativas ni umbrales, y no modifica el stack ni incorpora componentes probabilísticos reales.

## Verificación ejecutada

- `git diff HEAD^ HEAD --check`: correcto.
- `npm.cmd --prefix contexts/ui-demo run build`: correcto.
- `npm.cmd --prefix contexts/ui-demo test`: 1 prueba, correcta.

El e2e requiere el stack Docker real con los overlays `saas`, `demo` y `local-ports`; no se ejecutó en este sandbox.
