OK CON OBSERVACIÓN: `cf6916f313acc98e5e7867baa9878e64900ff092` no introduce una violación de la constitución ni una referencia normativa o un umbral inventado.

Revisión de `cf6916f` — corrección de las observaciones sobre T-52 (Enriquecimiento).

Alcance del commit: modifica `STATE.md`, `TODO.md`, la documentación Postman, la colección Postman y `specs/004-enriquecimiento/spec.md`. No modifica código de dominio, adaptadores, interfaces ni persistencia.

- P-01: conforme en el alcance revisado. La petición 77 sigue enviando salidas hacia Records/Custodia, que las recibe como `Sugerencia`; el commit no incorpora una escritura de metadatos o estado desde Enriquecimiento ni una materialización sin `DecisionHumana`.
- P-03: conforme. No se agregó consumo directo de una capacidad externa. La ruta ya existente conserva el puerto `EnviadorDeSugerencias` y su adaptador HTTP; el diff solo añade evidencia E2E y documentación.
- P-08: conforme en el código inspeccionado. `CapaAnticorrupcionSugerencias.recibir` anexa `SUGERENCIA_RECIBIDA` con actor, fecha, estado anterior y posterior; el test de dominio `CustodiaOriginalesTest` verifica esos cuatro datos. La nueva petición 81 sí comprueba E2E que se recibió al menos un evento atribuible a `enriquecedor-ficticio-v1`. Observación: esa petición no correlaciona el evento con `documento_id_en` (el evento expuesto no porta id de documento) ni afirma `estadoAnterior`/`estadoPosterior`; por tanto, no sustituye la prueba de dominio de P-08 ni demuestra esos atributos en la respuesta HTTP.
- Honestidad de pruebas: la petición 81 no está amañada para un simple 200: filtra por tipo y actor que el flujo 77 produce y exige al menos un resultado. La aserción es útil pero parcial por la falta de correlación y de estados anterior/posterior anteriores. Las peticiones 77--80 conservan comprobaciones materiales de las dos sugerencias y de que la ruta no-enriquecible no añade otra. La brecha de forma original no se oculta: queda declarada y T-53 la recoge; por ello no se afirma que RF-EN-003 esté cerrado E2E.

Control reforzado de `specs/`: el único archivo bajo `specs/` tocado es `specs/004-enriquecimiento/spec.md`. Su adición documenta una brecha ya observable en el contrato compartido. No incorpora Acuerdo, Ley, Decreto, ISO, artículo ni umbral numérico nuevos; las referencias y números de tarea/RF son internos y preexistentes. Los umbrales ya pendientes se conservan como `[CLARIFICAR]`/`PENDIENTE`.

Verificación: `git diff --check HEAD^ HEAD` no informa errores y la colección Postman parsea como JSON válido. No fue posible ejecutar los tests Kotlin focalizados: el wrapper de Gradle intenta crear su lock bajo `C:\\.gradle`, ruta no escribible en este sandbox, y falla antes de iniciar Gradle.
