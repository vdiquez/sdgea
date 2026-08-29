OK CON OBSERVACIÓN: el commit `3a534d4` cumple la spec aplicable y no presenta violaciones de P-01, P-03 ni P-08.

Revisión de `3a534d4` — T-51: Dockerfile real de Enriquecimiento y wiring en Docker Compose.

Contexto contrastado: `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`,
`specs/004-enriquecimiento/spec.md` (§§3–5), `specs/spec-infra-servicios.md`
(§13) y T-51 de `TODO.md`.

## Principios constitucionales

- **P-01 — Conforme.** El cambio sólo empaqueta el proceso HTTP existente y corrige
  su entrada en `main.py`. Enriquecimiento no materializa metadatos ni escribe el
  estado de documentos: continúa enviando `SugerenciaSaliente` al puerto
  `EnviadorDeSugerencias`, que cruza la capa anticorrupción de Records/Custodia.
  La decisión humana sigue siendo la única vía de materialización.
- **P-03 — Conforme.** El adaptador HTTP concreto sigue aislado detrás de
  `dominio.EnviadorDeSugerencias`; el dominio no conoce `httpx` ni la URL de
  Records/Custodia. Docker Compose aporta la URL como configuración. No se añadió
  capacidad externa crítica consumida directamente.
- **P-08 — Conforme.** T-51 no añade una transición de estado de documento o
  expediente. La recepción de la sugerencia permanece en Records/Custodia, donde
  se emite su evento de auditoría; el nuevo punto de entrada no elude esa ruta.

## Spec, referencias y umbrales

El commit modifica `specs/spec-infra-servicios.md`. La adición no introduce
referencias a Acuerdos, Leyes, Decretos ni ISO. El único número nuevo en el texto
añadido es el puerto técnico `8088`, que ya estaba definido antes del commit en el
§13 como valor por defecto de `SERVER_PORT`; no es un umbral ni una cita inventada.

## Pruebas y honestidad

Las pruebas existentes de Enriquecimiento no están amañadas: las API usan un doble
para aislar el puerto, y `test_integracion.py` comprueba con `httpx.MockTransport`
el método, URL y JSON exactos que el adaptador real construye. Cubren los criterios
RF-EN-001..009 relevantes para el comportamiento ya implementado, incluida la
emisión de sugerencias por campo y la ausencia de reenvío cuando no corresponde.

**Observación:** T-51 no añade una prueba que construya y arranque el Dockerfile ni
que ejerza `main.py` como proceso. Las 29 pruebas del contexto pasan, pero importan
`api.app` directamente, por lo que no prueban el cambio que reemplazó el stub de
`main.py`. No es una prueba falsificada ni una violación P-01/P-03/P-08, pero la
verificación de imagen/Compose de extremo a extremo queda pendiente de T-52 y de un
entorno con acceso al daemon Docker.

## Evidencia ejecutada

- `git diff --check HEAD^ HEAD`: sin errores de whitespace.
- `uv run --directory contexts/enriquecimiento pytest tests`: **29 passed**.
- Importación de `main` y de su aplicación FastAPI: correcta.
- Los tres archivos Compose se parsearon con PyYAML: correctos.
- `bash ./test.sh`: su prueba inicial de `run_id` pasó; Gradle no pudo descargar su
  distribución porque el sandbox bloquea red. Docker tampoco pudo conectarse al
  daemon por permisos del entorno. Estos límites no se atribuyen al commit.
