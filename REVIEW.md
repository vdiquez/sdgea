OK: sin violaciones P-01/P-03/P-08 ni referencias o umbrales inventados en `79d11862567d5c8aa1e3558e872459394b9800f6`.

Commit revisado: `79d1186` — T-57, colección Postman del ciclo de Indexación y Búsqueda, contra `specs/005-indexacion-busqueda/spec.md`, con el flujo de Records/Custodia y Seguridad y Acceso que exige su contrato.

Hallazgos

- P-01: conforme. La colección custodia y luego materializa el documento mediante `POST /documentos/{id}/decisiones` con actor humano antes de enviarlo a Indexación y Búsqueda. No introduce un clasificador, embedding ni Q&A reales: embedding, ranking y respuesta llegan declarados como FICTICIOS; Indexación solo persiste el estado ya materializado. No hay una escritura de Records/Custodia desde salida probabilística ni un bypass de la decisión humana en el flujo ejercitado.
- P-03: conforme. El rol e identidad se crean en Seguridad y Acceso y las tres rutas de consulta ejercitan el filtro de permisos por su interfaz HTTP. La implementación usada por la colección mantiene los puertos `IndiceLexico`, `IndiceVectorial` y `VerificadorDePermisos`; las pruebas del contexto verifican que indexación invoca ambos índices y que las consultas invocan el verificador por candidato. El commit no añade ningún consumo externo directo.
- P-08: conforme. La colección verifica las dos transiciones del contexto (`DOCUMENTO_MATERIALIZADO_RECIBIDO` y `ENTRADA_INDEXADA`) y los accesos permitidos y denegados en la bitácora. La suite del contexto cubre además los campos anterior/posterior de cada transición y la atomicidad real: si falla el anexo, no se confirma la entrada; también comprueba que los eventos de acceso persisten y son leíbles después de la consulta. No hay transición nueva sin evento.
- RF-IB-001/002/003/005/006/007/008/009/010: la carpeta 10 cubre el documento materializado, sus índices léxico/vectorial, búsqueda filtrada, preservación del ranking ficticio ya calculado, Q&A con cita, negativa apropiada, filtrado de cita no autorizada y auditoría. RF-IB-004 no es parte de T-57 y permanece fuera de este cambio, de acuerdo con el alcance explícito de la tarea.

Honestidad de las pruebas

- No están amañadas para pasar: la primera corrida detectó una contaminación real del índice persistente; el test ahora incluye un identificador único tanto en el texto como en el término y exige exactamente un resultado de la corrida actual. Una búsqueda sin permiso exige una lista vacía y el Q&A sin permiso exige que la cita no aparezca; no aceptan meramente un código HTTP exitoso.
- La aserción E2E final de auditoría comprueba existencia, actor, fecha, tipo y documentos accedidos de los eventos relevantes. Los detalles de estado anterior/posterior y rollback no se vuelven a duplicar allí, pero sí están cubiertos por pruebas unitarias/de persistencia que ejercitan SQLite/SQLAlchemy real, no dobles que oculten la escritura.
- `POST /recuperaciones` recibe el orden de relevancia ya calculado, lo cual es correcto para el componente FICTICIO del corte vertical: la aserción invertida comprueba que el contexto no lo reordena. No representa una implementación probabilística real.

Control de specs, referencias y umbrales

- El commit no modifica archivos bajo `specs/`; no aplica el control adicional de referencias normativas ni umbrales nuevos. No se detectaron citas normativas ni valores numéricos nuevos inventados en los archivos modificados.

Verificaciones ejecutadas

- `UV_CACHE_DIR=<temporal> uv run --directory contexts/indexacion-busqueda pytest`: 50 passed.
- Parseo de `postman/SGDEA-coleccion.postman_collection.json` y `postman/SGDEA-local.postman_environment.json`: válidos.
- `git diff HEAD^ HEAD --check`: sin errores de whitespace.

No añadí tareas a `TODO.md`.
