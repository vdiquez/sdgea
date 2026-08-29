OK: el commit `eab6e8b` cumple la spec aplicable y no presenta violaciones de P-01, P-03 ni P-08.

Revisión del commit `eab6e8b` — `fix: RF-EN-009 (segundo VETO de Codex) -- evaluar_texto() produce la marca real`.

Contexto contrastado: `specs/004-enriquecimiento/spec.md` (§§3--5, en especial invariante 5 y RF-EN-009), `.specify/memory/constitution.md`, `STATE.md` y `git show HEAD`.

Hallazgo y criterio de aceptación

- El flujo nuevo `evaluar_texto()` cubre la bifurcación que faltaba: con valores o campos marcados genera una `SugerenciaDeMetadatos`; sin señal y con razón declarada produce para el mismo `TextoDisponible` una `MarcaNoEnriquecible` que conserva documento, razón, actor y fecha. Esto satisface literalmente el Entonces de RF-EN-009 y elimina la pérdida silenciosa que motivó los dos vetos anteriores.
- El rechazo cuando no se suministran ni valores ni razón es una entrada malformada, no el caso de aceptación de RF-EN-009. No sustituye la marca en el caso especificado.

Principios solicitados

- P-01: conforme. El contexto continúa siendo puramente propositivo: no escribe ni materializa metadatos de `DocumentoDeArchivo`. `MarcaNoEnriquecible` tampoco muta el documento.
- P-03: conforme en el alcance del commit. No se incorpora ni consume una capacidad externa; la entrega a Records/Custodia sigue fuera de este cambio (T-50) y deberá usar su puerto propio.
- P-08: conforme. El cambio no introduce una transición persistida de estado de documento o expediente. La futura recepción de una sugerencia por Records/Custodia sigue siendo la frontera que emite auditoría; la marca se reporta al Operador y no altera el documento.

Honestidad de pruebas

- Las tres pruebas añadidas no están amañadas: invocan `evaluar_texto()`, no construyen directamente la marca para demostrar el caso de aceptación. Verifican la marca con razón, el camino alternativo de sugerencia y el rechazo de la entrada malformada. La primera prueba observa exactamente el resultado exigido por RF-EN-009.
- Los valores ficticios de confianza y evidencia son entradas declaradas por el llamador; no implementan ni simulan un componente probabilístico real, consistente con el alcance constitucional y el arnés ficticio.

Control de specs, referencias y umbrales

- El commit no modifica `specs/`; no aplica el control reforzado de referencias normativas y umbrales. Tampoco añade citas normativas ni umbrales nuevos en el código o pruebas.
- `git diff HEAD^ HEAD --check` no reporta errores de espacio.

Verificación ejecutada

- `uv run --directory contexts/enriquecimiento pytest -q`: 19 passed. Solo quedó una advertencia no bloqueante por permisos de escritura de `.pytest_cache`.
- `bash ./test.sh` no pudo completar Gradle en este entorno: la caché por defecto está denegada y, al redirigirla a una caché temporal, el wrapper no pudo descargar Gradle por la restricción de red (`Permission denied: getsockopt`). El script previo de `run_id` pasó. Es una limitación del entorno de revisión, no un fallo atribuido al commit.
