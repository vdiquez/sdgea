OK: sin violaciones P-01/P-03/P-08 ni referencias o umbrales inventados en `c7d225b2a0949b8be89430d2412acfd2ef972c97`.

Commit revisado: `c7d225b` — corrección del aislamiento de persistencia de Indexación y Búsqueda, contra `specs/005-indexacion-busqueda/spec.md` y `specs/spec-infra-servicios.md` §2.

Hallazgo y conformidad

- La corrección es pertinente y suficiente para este contexto: las cuatro entidades pasan a `ib_entradas_de_indice`, `ib_indices_vectoriales`, `ib_eventos_auditoria` e `ib_eventos_de_acceso`. La búsqueda global de `__tablename__` confirma que ninguno de esos nombres es usado por otro contexto. Así, este servicio ya no lee ni escribe las tablas genéricas compartidas que originaron el veto anterior.
- P-01: conforme. El cambio solo renombra tablas; no introduce inferencia ni permite que una salida probabilística escriba Records/Custodia. El contrato sigue recibiendo únicamente documentos materializados tras decisión humana.
- P-03: conforme. No hay consumo directo nuevo de capacidades externas ni cambio de cableado: `IndiceLexico`, `IndiceVectorial` y `VerificadorDePermisos` continúan siendo puertos; las variantes autoalojadas siguen detrás de ellos.
- P-08: conforme. `guardar_con_evento` conserva la escritura atómica de cada transición y `guardar_evento_de_acceso` conserva el anexo del evento de consulta. Al usar tablas exclusivas de este contexto, `GET /eventos-auditoria` ya no puede mezclar la bitácora de otros bounded contexts por esa colisión de nombres.

Honestidad de pruebas

- La prueba nueva no está amañada: importa las cuatro entidades realmente usadas por SQLAlchemy y falla si se restituye cualquiera de los nombres genéricos. Es una guarda de regresión directa para la modificación efectuada.
- Su alcance es estructural: prueba el mapeo ORM, no levanta los otros servicios ni siembra una tabla ajena para demostrar el aislamiento completo del endpoint. Eso no invalida el criterio de este commit; la verificación integrada queda documentada en `STATE.md`/`TODO.md`. No pude repetirla porque el daemon Docker no es accesible en este entorno (`docker_engine: Access is denied`).
- Las pruebas preexistentes relevantes sí ejercitan los criterios: transiciones con evento en la misma transacción, rollback si falla el anexo, persistencia posterior del evento de acceso y lectura por API. No hay dobles que oculten esas escrituras; usan SQLite/SQLAlchemy real para el adaptador autoalojado.

Verificaciones ejecutadas

- `UV_CACHE_DIR=<temporal> uv run --directory contexts/indexacion-busqueda pytest`: 50 passed.
- `git diff HEAD^ HEAD --check`: sin errores de whitespace.
- El commit no modifica archivos bajo `specs/`; por tanto no aplica el control adicional de referencias normativas o umbrales nuevos. No añadí tareas a `TODO.md`.
