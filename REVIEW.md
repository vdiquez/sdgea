OK: T-61 conforme con RF-UI-004; sin VETO.

# Revisión de `d32e1cf` — T-61 · RF-UI-004

## Alcance contra la spec

La nueva pantalla `/clasificacion` cumple el primer criterio de RF-UI-004: el operador entrega una candidata FICTICIA a `POST /api/clasificacion/clasificaciones`, y la UI muestra el contenido propuesto, su confianza y el componente reutilizable `MarcaDeSimulacion`.

La pantalla no materializa una clasificación ni ofrece una acción que lo haga. La respuesta de Clasificación sólo se conserva como estado efímero de React. El segundo criterio se satisface por el contrato real existente: `POST /clasificaciones` ordena y reenvía cada salida a `POST /sugerencias`; Records/Custodia la recibe mediante `CapaAnticorrupcionSugerencias` antes de que Clasificación responda 201.

## Prerrequisito de Records/Custodia

Conforme. `cliente.ts` limita los contextos que el navegador puede invocar y excluye `records-custodia`; `nginx.conf` no contiene ubicación para ese contexto y el overlay de demo tampoco lo declara como dependencia de la UI. `Clasificacion.tsx` invoca exclusivamente el contexto `clasificacion` a través de la ruta relativa del proxy curado.

La llamada Clasificación → Records/Custodia es legítima y no sortea el bloqueo de §1: procede desde el adaptador servidor-a-servidor `EnviadorDeSugerenciasHttp`, apunta al DNS interno del servicio y constituye precisamente la entrega de una propuesta a la capa anticorrupción exigida por RF-CL-004/RF-RC-003. No crea una ruta de navegador ni expone un puerto de Custodia.

## Constitución

- **P-01: conforme.** No se implementa clasificación probabilística real: la candidata, su evidencia y el identificador del modelo FICTICIO son declarados por el operador. El flujo sólo escribe una `Sugerencia`; `CapaAnticorrupcionSugerencias.recibir` consulta el documento, guarda la sugerencia y no modifica `DocumentoDeArchivo.clasificacion`. La única mutación de clasificación continúa en `materializar(DecisionHumana)`.
- **P-03: conforme.** La integración externa de Clasificación con Records/Custodia está detrás del puerto `dominio.EnviadorDeSugerencias`; `api.py` depende de esa interfaz y la implementación HTTP queda confinada al adaptador. La UI no incorpora ni consume una capacidad externa crítica sin interfaz.
- **P-08: conforme.** La recepción de la sugerencia anexa `SUGERENCIA_RECIBIDA` con actor de sistema (`modeloId`), fecha y estados anterior/posterior. El controlador usa `RecepcionDeSugerenciasTransaccional`, que hace atómicos el guardado de la sugerencia y el evento. La UI no introduce estado documental propio.

## Honestidad de los tests

El e2e nuevo no intercepta `fetch`, no simula la respuesta de Clasificación ni llama Records/Custodia desde el navegador. Crea, como setup explícito, un documento real mediante `apiRequest` al puerto local `localhost:8082`; después rellena el formulario y verifica en la UI la respuesta real, su confianza y la marca de simulación. Como Clasificación sólo responde tras `enviador.enviar`, el caso detectaría que la entrega interna a Custodia falle.

El setup directo por puerto local es coherente con el prerrequisito: es una utilidad de prueba que requiere el overlay explícito `docker-compose.local-ports.yml`, no una ruta de la aplicación web. Por tanto no es una simulación ni una evasión de la frontera de producción.

## Specs, normativa y umbrales

El commit no modifica archivos bajo `specs/`. Los textos añadidos a `STATE.md` y `TODO.md` describen contratos y comportamiento ya especificados; no introducen referencias normativas, cláusulas ni umbrales regulatorios o de negocio inventados. Los valores de formulario y del e2e son datos de demostración, no umbrales.

## Verificación ejecutada

- `git show --check HEAD`: sin errores de espacios.
- `npm.cmd --prefix contexts/ui-demo run build`: correcto.
- `npm.cmd --prefix contexts/ui-demo test`: 1 prueba, correcta.
- Se intentó el e2e específico con `PLAYWRIGHT_BASE_URL=http://localhost:8090`. No pudo ejecutar el flujo porque el entorno no permite acceder al daemon Docker y no había servicio escuchando en `localhost:8082` (`ECONNREFUSED`); es una limitación de infraestructura local previa a la aplicación, no un fallo atribuible al commit.
