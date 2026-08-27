VETO: P-01 incumplido: el resultado probabilístico de OCR materializa directamente el estado `EXTRAIDO`, sin pasar por una Sugerencia ni una decisión humana.

# Revisión de `dd97fb4` — T-40, Extracción

Revisados `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`, el diff de
`HEAD` y `specs/002-extraccion/spec.md`.

## V-01 · P-01

`ResultadoOcr` es explícitamente el resultado de un componente probabilístico
(OCR). Sin embargo, `recibir_resultado_ocr` en
`contexts/extraccion/dominio.py:164` recibe ese resultado y en la línea 172
sobrescribe directamente el agregado `TextoExtraido` con estado `EXTRAIDO`, su
contenido y su calidad. No hay objeto `Sugerencia`, capa anticorrupción ni
decisión humana que materialice ese resultado.

El test `TestExtraccionViaOcr` (`tests/test_dominio.py:115`) codifica y exige
esa misma materialización automática. Por ello no es una prueba de que la
frontera de P-01 se conserve, sino una prueba que afianza la violación. El
comentario de la spec que exceptúa el texto extraído no prevalece sobre la
constitución ni sobre el criterio expreso de esta revisión: nada probabilístico
escribe estado; debe cruzar la capa anticorrupción como Sugerencia y sólo una
decisión humana puede materializarlo.

## Hallazgo de especificación y tests

- `marcar_cuarentena_o_rechazo` (`dominio.py:188`) admite cualquier estado de
  origen. Puede cambiar un texto ya `EXTRAIDO`, `RECHAZADO` o
  `EN_CUARENTENA`, pese a que §3 de la spec declara esas tres ramas como
  terminales y sólo permite `Pendiente de extracción` → terminal. No hay test
  que intente esas transiciones inválidas; los de RF-EX-009 sólo usan el estado
  pendiente. Debe rechazarlas y probarse el rechazo.

## Chequeos requeridos

- **P-03:** sin infracción en este commit. No se invoca un OCR ni otra
  capacidad externa real; `ResultadoOcr` es sólo un transporte ficticio. Al
  conectar un motor real en T-41 deberá existir el puerto propio exigido por
  RNF-EX-002, con implementaciones gestionada y autoalojada.
- **P-08:** en el dominio, las transiciones de estado implementadas
  (`recibir_unidad`, extracción born-digital, recepción OCR y cuarentena/rechazo)
  devuelven un `EventoAuditoria` con actor, fecha y estados anterior/posterior.
  La bitácora append-only y la persistencia atómica no existen todavía, pero
  están correctamente fuera del corte T-40 y asignadas a T-41; no se marca un
  segundo veto por ello. `determinar_soporte` cambia un atributo, no el estado,
  aunque su evento usa el centinela `SOPORTE_DETERMINADO` en vez de estados.
- **Honestidad de tests:** los 25 tests llaman al dominio de producción y sí
  cubren las ramas Dado/Cuando/Entonces principales; no hay dobles que
  preconfiguren resultados. Son insuficientes para detectar V-01 porque esperan
  el comportamiento prohibido, y omiten el rechazo de transiciones desde
  estados terminales señalado arriba.
- **Referencias y umbrales en specs:** el commit no modifica ningún archivo
  bajo `specs/`; por tanto no aplica el chequeo adicional de referencias
  normativas ni umbrales nuevos en specs. No se detectó una nueva cita normativa
  en los archivos modificados.

## Verificación

`uv run --directory contexts/extraccion pytest`: **25 passed**. Se usó una
caché temporal dentro del workspace porque la caché global de `uv` no es
accesible en este entorno; pytest emitió un warning no funcional por no poder
escribir `.pytest_cache`.
