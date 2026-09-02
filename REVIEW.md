OK: `ae45b3c` resuelve por completo la observación de la revisión de `d789113`: la pantalla hace visible que este es el alcance inicial de Records/Custodia, que muestra todos los eventos expuestos sin filtrar por documento ni sesión, y que la consolidación de los demás contextos y de Seguridad y Acceso sigue pendiente. El e2e comprueba el aviso antes de verificar el evento de decisión.

# Revisión de `ae45b3c` — corrección de T-64 / RF-UI-011, alcance inicial

## Dictamen

`Bitacora.tsx` nombra explícitamente la pantalla «Bitácora de Records/Custodia (alcance inicial)» y muestra, antes del contenido cargado, un aviso que comunica el límite real del corte: lista todos los eventos que expone ese contexto, no está filtrada por documento ni por sesión porque el backend no distingue eventos por documento, y aún no consolida Normalización, Extracción, Indexación y Búsqueda ni la bitácora de Seguridad y Acceso.

Esto satisface íntegramente R-01 de la revisión anterior, sin fingir que existe un filtro o una consolidación que el backend no ofrece. La ampliación del e2e navega a `/bitacora` y exige que el texto «sin filtrar por documento» sea visible antes de localizar `DECISION_HUMANA_MATERIALIZADA` con su actor y fecha; por tanto, el aviso queda cubierto por una prueba de interfaz sobre el flujo real, además del comportamiento previo.

## Constitución y specs

- **RF-UI-011 / `specs/008-ui-demo/spec.md` §5: conforme.** El código no presenta este corte como la bitácora consolidada requerida por el RF; declara de forma exacta los contextos pendientes y conserva el alcance actual de Records/Custodia.
- **P-01, P-03 y P-08: conforme.** No hay cambios a decisiones humanas, integraciones o stack; la vista sigue siendo una lectura de eventos reales atribuibles y fechados.
- No se modificó la constitución, no se introdujeron referencias normativas ni umbrales inventados, ni componentes probabilísticos reales.

## Verificación ejecutada

- Inspección del diff de `ae45b3c`, la observación R-01 anterior y `specs/008-ui-demo/spec.md` §5.
- `git diff ae45b3c^ ae45b3c --check`: correcto.
- `npm.cmd --prefix contexts/ui-demo run build`: correcto.
- `npm.cmd --prefix contexts/ui-demo test`: 1 prueba, correcta.
- No ejecuté el e2e Docker en este entorno; la aserción añadida se inspeccionó y apunta a la pantalla real tras el flujo existente.
