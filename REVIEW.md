OK: commit `82f866b` conforme a la constitución y al contexto de Extracción.

## Alcance revisado

- Commit: `82f866b` — marca T-42 completada en `TODO.md` y registra su evidencia en `STATE.md`.
- Diff efectivo: solo `STATE.md` y `TODO.md`; no modifica código, pruebas, Docker/Compose ni archivos bajo `specs/`.
- Contexto contrastado: `specs/002-extraccion/spec.md` (RF-EX-004 y RF-EX-011), `specs/spec-infra-servicios.md` §11 y la implementación de T-42 que este commit cierra (`959d07e`).

## Constitución

- **P-01 — conforme.** `HEAD` no introduce una vía probabilística de escritura. La implementación que T-42 registra conserva la salida OCR como `SugerenciaOcr`: `recibir_sugerencia_ocr` deja el agregado en `PENDIENTE_DE_EXTRACCION`; solo `confirmar_extraccion`, tras decisión humana explícita, lo materializa.
- **P-03 — conforme.** El dominio depende del puerto `VerificadorDeAutorizacion`; `VerificadorDeAutorizacionHttp` es el adaptador hacia Seguridad y Acceso. El Compose solo configura su URL interna. No hay consumo directo de OCR real: sigue siendo ficticio.
- **P-08 — conforme.** El commit no añade transiciones. Las transiciones ya implementadas devuelven `EventoAuditoria` y `guardar_con_evento` persiste agregado y evento en una única transacción; la prueba de persistencia fuerza una violación `NOT NULL` y verifica rollback de ambos, por lo que no es un doble amañado.

## Specs, referencias y umbrales

- No hay cambios bajo `specs/`, por lo que el chequeo adicional de referencias normativas y umbrales nuevos no aplica.
- El diff tampoco añade Acuerdos, Leyes, Decretos, ISO ni valores de umbral. No hay cita ni número inventado.

## Honestidad y verificación

- El commit no cambia pruebas. T-42 es infraestructura de empaquetado, no un RF nuevo con criterio Dado/Cuando/Entonces; no presenta tests como prueba de un comportamiento que no cubren.
- Las pruebas relevantes sí ejercen los criterios de aceptación: la sugerencia OCR no materializa sola (RF-EX-004/P-01); un actor sin permiso no confirma ni muta el agregado (RF-EX-011/P-03); y todo evento de transición comprueba actor, fecha y estados antes/después (P-08).
- Ejecutado: `uv run --directory contexts/extraccion pytest` con caché temporal — **55 passed**. `docker compose ... config --quiet` validó las composiciones SaaS y on-prem con el overlay local (solo emitió advertencias por no poder leer la configuración Docker del usuario).
- Límite de esta revisión: la suite completa `bash ./test.sh` no pudo completarse porque el sandbox no permite descargar Gradle 9.7.0 tras aislar su caché temporal. No es un fallo del proyecto; la evidencia histórica de `./test.sh` en `STATE.md` no fue reproducible íntegramente en este entorno. Tampoco se construyó ni levantó la imagen/stack; esa comprobación de punta a punta sigue prevista en T-43.

No se añadieron tareas a `TODO.md`: T-43 ya cubre la verificación pendiente con Docker real y Newman.
