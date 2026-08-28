OK: `383c6443a8f9206ac0d79a7617e9ac68702d002c` resuelve los VETOs P-01 y P-08 de `dd97fb4` y `e623ad6`; sin violaciones constitucionales ni referencias/umbrales inventados detectados.

# Revisión de `383c6443` — T-40 Extracción, tercera vuelta OCR

Revisados `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`,
`QUESTIONS.md`, la spec activa `specs/002-extraccion/spec.md`, el diff completo
de `HEAD` contra `HEAD^` y los tests del contexto.

## Veredicto de los VETOs anteriores

- **P-01 — resuelto.** `SugerenciaOcr` sustituye a `ResultadoOcr` y porta
  `modelo_id`, `contenido`, `calidad`, `evidencia` y `fecha`, consistente con
  la forma de las sugerencias de Normalización y Records/Custodia. La frontera
  del dominio recibe esa propuesta ya calculada mediante
  `recibir_sugerencia_ocr`; esta sólo la adjunta y conserva
  `PENDIENTE_DE_EXTRACCION`. `confirmar_extraccion(texto, actor, fecha)` es la
  única operación que materializa `contenido`/`calidad` y transiciona a
  `EXTRAIDO`. No se ejecuta OCR real: sigue siendo el emisor FICTICIO del
  arnés, como exige la constitución.
- **P-08 — resuelto.** La recepción de sugerencia y la determinación de
  soporte ahora emiten `EventoAuditoria` con el estado real antes y después
  (`PENDIENTE_DE_EXTRACCION` en ambos casos), no sentinels. Las transiciones de
  estado implementadas (`recibir_unidad`, extracción born-digital,
  confirmación humana y cuarentena/rechazo) devuelven igualmente eventos con
  actor, fecha y estados anterior/posterior. La bitácora append-only y la
  persistencia atómica pertenecen al T-41 pendiente; este corte de funciones
  puras no pretende suplirlas.
- **P-03 — pasa.** El commit no invoca ni conecta una capacidad externa. Al
  integrarse un motor OCR real seguirá siendo obligatorio el puerto propio y
  las implementaciones intercambiables exigidas por RNF-EX-002/P-03.

## Spec, referencias y umbrales

El único archivo bajo `specs/` modificado es
`specs/002-extraccion/spec.md`. El cambio alinea §1, el lenguaje ubicuo y
RF-EX-004/RF-EX-011 con `SugerenciaOcr`. No añade Acuerdo, Ley, Decreto, ISO ni
una referencia normativa nueva; tampoco introduce un umbral numérico. Las
referencias internas nuevas son P-01, RF-RC-004 y RF-NO-004, ya existentes, y
la trazabilidad de RF-EX-011 queda correctamente en `N/A`.

## Tests y honestidad

Los tests ejercitan las funciones de producción, sin mocks ni resultados
preconfigurados. El caso nuevo de recepción verifica que una sugerencia con
evidencia queda adjunta, no cambia el estado ni el contenido, y emite estados
reales; los de confirmación verifican que sólo el actor humano materializa la
sugerencia y que no puede confirmarse sin propuesta ni dos veces. Por tanto
prueban el criterio relevante y no están amañados para conservar los VETOs.

Como mejora de cobertura no bloqueante, el test nombrado “actor y fecha” de
RF-EX-011 verifica el actor pero no afirma explícitamente `evento.fecha`; la
implementación sí propaga el parámetro y P-08 queda satisfecho por inspección,
pero conviene añadir esa aserción al siguiente cambio de la misma prueba.

## Verificación

- `git diff --check HEAD^ HEAD`: sin errores.
- `UV_CACHE_DIR=<temporal> uv run --directory contexts/extraccion pytest`:
  **29 passed**. Pytest emitió sólo un warning no funcional porque no puede
  escribir `.pytest_cache`.
- `bash ./test.sh`: no se completó localmente. Tras configurar un
  `GRADLE_USER_HOME` temporal para evitar el lock no accesible de `C:\\.gradle`,
  Gradle necesitó descargar 9.7.0 y el sandbox bloqueó la conexión de red
  (`Permission denied: getsockopt`) antes de ejecutar pruebas Gradle. No se
  declara un resultado verde para la suite completa.

No se añadieron tareas a `TODO.md`.
