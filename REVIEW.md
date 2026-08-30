# Revisión de `7fc611ad54f8744edfe6a06fd3e1563df0065926`

**Veredicto: OK.** T-53 corrige la trazabilidad de `formaOriginal` requerida por
RF-EN-003 sin introducir una transición de estado adicional ni ampliar el alcance
del componente FICTICIO.

## Contra la spec correspondiente

- **RF-EN-003:** `ValorPropuesto.valor_original` se copia a
  `SugerenciaSaliente.forma_original`, viaja como `formaOriginal` en el contrato
  HTTP y se persiste/recupera desde `Sugerencia`. La forma normalizada continúa
  en `contenidoPropuesto`. Un `CampoNoEncontrado` conserva correctamente
  `null`: no se inventa una forma original.
- **RF-EN-004 y RF-EN-010:** evidencia y confianza no cambian; el endpoint de
  consulta conserva ahora además la forma original. La prueba HTTP de
  Records/Custodia cubre POST -> persistencia -> GET en peticiones separadas.

## Principios revisados

- **P-01 — conforme.** Enriquecimiento sigue emitiendo solamente
  `SugerenciaSaliente`; no contiene agregado ni operación que materialice
  metadatos. Records/Custodia recibe la entrada mediante
  `CapaAnticorrupcionSugerencias`, guarda una `Sugerencia` y no modifica el
  documento. La materialización permanece en la decisión humana existente.
- **P-03 — conforme.** La llamada externa sigue detrás del puerto
  `EnviadorDeSugerencias`; `api.py` depende de él y la implementación HTTP es
  un adaptador intercambiable. No se añadió ningún consumo externo directo.
- **P-08 — conforme.** T-53 no crea una transición: conserva un atributo de la
  misma recepción de sugerencia. Esa recepción sigue pasando por
  `RecepcionDeSugerenciasTransaccional` y
  `CapaAnticorrupcionSugerencias.recibir`, que anexa
  `SUGERENCIA_RECIBIDA` con actor, fecha y estados anterior/posterior en la
  misma operación transaccional. La prueba HTTP preexistente comprueba ese
  evento para `POST /sugerencias`; el nuevo test prueba el nuevo atributo
  durante el mismo recorrido de recepción.

## Honestidad de las pruebas

- Las pruebas de dominio ejercen la traducción real de valor original a
  sugerencia, no un DTO aislado.
- La prueba de integración Python usa `httpx.MockTransport` sobre el cliente
  real y verifica método, URL y cuerpo JSON exacto, incluido `formaOriginal`.
- La prueba HTTP Kotlin no usa un doble: custodia un documento, hace POST de
  la sugerencia y verifica en un GET posterior el valor persistido. El cambio
  de contrato habría fallado antes de la implementación.
- Ejecutado en esta revisión: `python -m pytest contexts/enriquecimiento/tests`
  con el entorno local: **29 passed**. La suite Gradle/Kotlin no pudo iniciarse
  en el sandbox: el wrapper no puede escribir en `C:\\.gradle` y, con
  `GRADLE_USER_HOME` temporal, no puede descargar Gradle por restricción de
  red. No se interpreta como una prueba ejecutada en verde.

## Chequeo adicional de specs

Los únicos cambios bajo `specs/` documentan el contrato opcional y la brecha
cerrada. No añaden Acuerdo, Ley, Decreto, ISO ni otro referente normativo, ni
fijan umbrales numéricos nuevos. Los pendientes y `[CLARIFICAR]` existentes se
mantienen. Sin motivo de VETO.
