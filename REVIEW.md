OK: corrección de P-03 conforme; sin VETO.

Commit revisado: `9f490e03767499bfa12a53fe497a03801c9964f6` — contexto `specs/005-indexacion-busqueda/spec.md` (RF-IB-003, RF-IB-004, RNF-IB-002; P-01/P-03/P-08).

Dictamen

- P-01: conforme. El cambio no incorpora inferencia, embeddings ni recuperación probabilística reales. Conserva el embedding ya entregado por el llamador y no escribe estado de Records/Custodia ni materializa sugerencias.
- P-03: conforme. `AlmacenDeEntradas` e `IndiceLexicoAutoalojado` ya no conocen `VectorDeEntradaEntity`; el único ensamblaje de una entrada con su embedding está en `_con_embedding`, capa de orquestación que depende de `IndiceVectorial`. `IndiceVectorialAutoalojado` es ahora el único adaptador que accede a su propia tabla, y `IndiceVectorialGestionado` satisface el mismo puerto HTTP. No queda consumo directo del detalle de la variante autoalojada fuera de su adaptador.
- P-08: conforme. La actualización sigue produciendo `ENTRADA_ACTUALIZADA`; `guardar_con_evento` persiste transición y evento en una transacción con rollback. Las consultas modificadas mantienen su evento de acceso append-only antes de responder; no se añadió una transición sin auditoría.
- Tests: honestos respecto del cambio. La prueba de dominio verifica las invocaciones reales a ambos puertos al rectificar texto/embedding; la de API realiza una búsqueda posterior e independiente para comprobar que el embedding actualizado se recupera desde el puerto, y las pruebas de persistencia usan SQLAlchemy/SQLite real. No se limitan a inspeccionar el objeto de retorno de la actualización. La cobertura de la variante gestionada aún prueba su contrato HTTP de forma aislada, no el flujo API completo con esa variante inyectada; es una mejora de cobertura recomendable, pero no encubre ni contradice el criterio implementado.
- `specs/` no fue modificado; no aplica el control adicional de referencias normativas ni umbrales.

Verificación ejecutada

- `UV_CACHE_DIR=<temporal> uv run --directory contexts/indexacion-busqueda pytest`: 49 passed.
- `bash ./test.sh`: pasaron ambos checks de scripts; la suite no alcanzó Gradle porque el wrapper intentó descargar Gradle 9.7.0 y el sandbox bloqueó la conexión (`Permission denied: getsockopt`). No hubo fallo atribuible al cambio revisado.
