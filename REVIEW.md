VETO: P-03 incumplido: `AlmacenDeEntradas` lee directamente `VectorDeEntradaEntity`/`indices_vectoriales`, el detalle de la variante autoalojada, por lo que no es intercambiable con `IndiceVectorialGestionado`.

Commit revisado: `53ce657bf62c34b87813882838ad6ca76b5cfb1e` — contexto `specs/005-indexacion-busqueda/spec.md` (RF-IB-003, RNF-IB-002; P-01/P-03/P-08).

Hallazgo bloqueante

- `persistencia.py` incorpora `_embedding_de(session, entrada_id)`, que consulta directamente `VectorDeEntradaEntity`, y `AlmacenDeEntradas.obtener()`/`todas_indexadas()` lo invocan. Esa tabla solo existe para `IndiceVectorialAutoalojado`; al sustituir el puerto por `IndiceVectorialGestionado`, el almacén continúa leyendo la tabla local en vez del puerto y pierde el embedding. La orquestación conoce por tanto la implementación activa, exactamente lo que P-03 prohíbe. Debe eliminarse ese acoplamiento: el consumidor debe depender de la interfaz `IndiceVectorial` (o el agregado de índice debe separar el dato externo sin que el almacén concrete la variante) y una prueba debe ejercitar ambas variantes bajo el mismo contrato.

Verificaciones

- P-01: conforme en el cambio. No se incorpora componente probabilístico real; el adaptador vectorial únicamente almacena/recupera el embedding que recibe y no materializa decisiones de records.
- P-08: conforme para la transición de indexación autoalojada. `IndiceVectorialAutoalojado.indexar()` deja su escritura en la misma `Session` que `guardar_con_evento()`, cuyo `commit` incluye entrada y evento; el rollback cubre ambos. El cambio no añade nuevas transiciones sin evento.
- Tests: los dos tests nuevos son honestos respecto de la variante autoalojada: usan SQLAlchemy/SQLite real, persisten y leen el vector, y comprueban que el almacén lo reconstruye. No prueban el criterio de intercambiabilidad de P-03 ni pueden detectar el acoplamiento anterior; falta una prueba que cambie la implementación del puerto y verifique el mismo comportamiento sin acceso a la tabla local.
- `specs/` no fue modificado en este commit; no aplica el chequeo adicional de referencias normativas ni umbrales.
- Verificación ejecutada: `bash ./test.sh` llegó a los checks de scripts, pero no completó la suite por un fallo de entorno de Gradle al crear `C:\\.gradle\\wrapper\\...zip.lck` (no por un fallo de tests del cambio).
