# Revisión de `d1471fca79ec2c40ed7405dc8860ca772a29f363` — T-41b

Revisados `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`, el plan
vigente, `specs/002-extraccion/spec.md`, `specs/spec-infra-servicios.md`, el
diff completo de `HEAD` y las pruebas de Extracción.

## Resultado: OK

El commit corrige el VETO anterior de RF-EX-011. No se detectó violación de la
constitución, referencia normativa inventada, ni umbral numérico nuevo sin
fuente o sin marcar como pendiente.

## Principios constitucionales

- **P-01 — pasa.** `SugerenciaOcr` conserva modelo, contenido, calidad,
  evidencia y fecha; `recibir_sugerencia_ocr` deja el agregado en
  `PENDIENTE_DE_EXTRACCION`. La única materialización hacia `EXTRAIDO` desde
  esa salida probabilística es `confirmar_extraccion`, después de una decisión
  humana autorizada. El OCR sigue siendo ficticio: no se implementa ni invoca
  un componente probabilístico real.
- **P-03 — pasa.** La verificación contra seguridad-acceso está detrás del
  puerto propio `VerificadorDeAutorizacion`. El dominio y el endpoint dependen
  del puerto; `VerificadorDeAutorizacionHttp` es el adaptador de producción
  para `POST /autorizacion`, y los dobles de prueba son intercambiables sin
  acoplar el dominio a HTTP.
- **P-08 — pasa.** La confirmación autorizada devuelve `EventoAuditoria` con
  actor, fecha y transición real `PENDIENTE_DE_EXTRACCION` → `EXTRAIDO`; el
  endpoint la persiste junto con el agregado mediante `guardar_con_evento`.
  La denegación ocurre antes de cualquier transición, por lo que no hay evento
  de transición que emitir. Las demás transiciones ya existentes del contexto
  siguen devolviendo evento de auditoría.

## Honestidad de las pruebas

Las pruebas cubren el criterio Dado/Cuando/Entonces de RF-EX-011, no solo su
camino feliz: una sugerencia confirmada por el verificador permitido queda
`EXTRAIDO`, conserva contenido y calidad, y registra actor, fecha y ambos
estados; el verificador que deniega provoca `AccesoDenegadoError` antes de
materializar. El test HTTP además comprueba el 403 del endpoint. Los dobles son
inyecciones explícitas del puerto y representan las dos respuestas de la
dependencia; no ocultan la regla de autorización ni una transición.

## Specs, referencias y umbrales

El commit modifica `specs/002-extraccion/spec.md` y
`specs/spec-infra-servicios.md`. El chequeo de diff no encontró nuevas citas a
Acuerdo, Ley, Decreto o ISO, ni nuevos umbrales de negocio. `POST
/autorizacion`, el permiso `confirmar`/`documento`, el puerto 8083 y su URL
base ya estaban definidos en `specs/spec-infra-servicios.md`; las adiciones
solo documentan el consumidor de Extracción y la corrección de RF-EX-011.

## Verificación

- `git diff --check HEAD^ HEAD`: sin errores.
- `uv run --directory contexts/extraccion pytest -q`: **55 passed**.
  Solo hubo advertencias no bloqueantes del entorno: deprecación de
  `starlette.testclient` y falta de permiso para escribir `.pytest_cache`.

No se añadieron tareas a `TODO.md`: no queda trabajo derivado de una spec
existente a partir de esta revisión.
