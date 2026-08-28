VETO: RF-EX-011 incumplido: `confirmar_extraccion` acepta cualquier `str` como actor, sin verificar autorización mediante una interfaz, y su test no cubre el criterio «actor autorizado».

# Revisión de `cf93d843fa8f34dbacd7277571e3abe695a307aa` — T-40 Extracción

Revisados `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`, el plan
vigente, `specs/002-extraccion/spec.md`, el diff completo de `HEAD` y el dominio
y las pruebas de Extracción en `HEAD`.

## Hallazgo bloqueante: RF-EX-011 y honestidad TDD

El único cambio funcional del commit añade `assert evento.fecha ==
PROCEDENCIA.fecha`. Es una aserción válida y mejora la cobertura de P-08, pero
no completa el criterio Dado/Cuando/Entonces de RF-EX-011: «Cuando un **actor
autorizado** la confirma». La implementación
`confirmar_extraccion(texto, actor: str, fecha)` acepta cualquier cadena y no
recibe ni consulta un verificador/autorizador. La prueba usa
`"archivista-1"`, pero no demuestra que esté autorizado ni que un actor sin
permiso sea rechazado.

Por ello la prueba no está manipulada con dobles preconfigurados, pero sí es
incompleta respecto del criterio de aceptación que dice probar. Mantenerla en
verde no acredita la autorización ni materializa una decisión humana
autorizada. Debe definirse e introducirse una interfaz propia de autorización
(P-03), con una prueba de actor permitido y otra de rechazo para actor no
autorizado; no se debe simular esa garantía con una cadena o una convención de
nombre.

## Principios constitucionales

- **P-01:** pasa en el dominio revisado. `SugerenciaOcr` porta modelo,
  contenido, calidad, evidencia y fecha; `recibir_sugerencia_ocr` conserva
  `PENDIENTE_DE_EXTRACCION`, y solamente `confirmar_extraccion` materializa
  contenido, calidad y `EXTRAIDO`.
- **P-03:** no hay consumo directo de OCR ni de otra capacidad externa en este
  commit; el OCR permanece ficticio. El hallazgo bloqueante exige que la futura
  comprobación de autorización se haga también tras una interfaz propia.
- **P-08:** pasa para la transición de confirmación: el evento incluye actor,
  fecha y los estados reales `PENDIENTE_DE_EXTRACCION` → `EXTRAIDO`; la nueva
  aserción verifica explícitamente la fecha. Las demás transiciones del dominio
  revisado emiten igualmente un `EventoAuditoria`.

## Specs, referencias y umbrales

`HEAD` modifica solamente `REVIEW.md`, `STATE.md` y una prueba; no toca ningún
archivo bajo `specs/`. Por tanto no aplica el chequeo adicional de referencias
normativas ni de umbrales en specs. No se introduce en el diff una cita
normativa ni un umbral numérico nuevo.

## Verificación

- `git diff --check HEAD^ HEAD`: sin errores.
- `uv run --directory contexts/extraccion pytest -q`: **53 passed**. Hay dos
  advertencias no bloqueantes del entorno (`StarletteDeprecationWarning` y falta
  de permiso para escribir `.pytest_cache`).

No se añadieron tareas a `TODO.md`: la corrección requerida deriva directamente
de RF-EX-011/T-40 ya existentes.
