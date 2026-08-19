# QUESTIONS — solo anexado; las responde el humano

## 2026-08-18 · F0 preflight bloqueado — [?]

Tarea recibida (Apéndice A del plan, vía `orquestador.sh preflight`): aplicar tres
correcciones en el árbol de trabajo, sin comitear, sobre `specs/eval/eval-clasificacion.md`
§4.5/§9, `specs/contexts/spec-records-custodia.md` §7, `CLAUDE-CODE-KICKOFF.md` y
`specs/README.md`.

No pude ejecutarla porque encontré dos problemas que no puedo resolver sin inventar
una decisión:

**A. No existe el directorio `specs/` en este repo.** Ninguno de los archivos vive en
las rutas que indica la tarea. En su lugar hay archivos planos en la raíz cuyo
contenido corresponde: `00-constitution.md`, `README.md` (el contenido de este último
es literalmente la descripción de `/specs`, incluyendo el árbol `specs/README.md`,
`specs/00-constitution.md`, `specs/contexts/...`), `spec-records-custodia.md`,
`spec-captura-ingesta.md`, `edd-harness.md`, `eval-clasificacion.md`. Los logs de
`.loop/logs/claude-preflight-1.json` y `-2.json` muestran que los intentos previos
fallaron por "Not logged in", no por este motivo — es decir, `specs/` nunca se colocó
en este repo, contra lo que pide `CLAUDE-CODE-KICKOFF.md` ("Coloca el directorio
`specs/` completo en la raíz del repo... antes de empezar").

**B. Conflicto directo de instrucciones.** El sistema me añade la regla dura "no
modificar nada bajo specs/" en la misma invocación que me pide editar contenido de
spec (`eval-clasificacion.md`, `spec-records-custodia.md`). `CLAUDE.md`/`AGENTS.md`
dicen además "specs/** es SOLO LECTURA para agentes". El hook de pre-commit instalado
por `orquestador.sh bootstrap` sugiere que la intención real es: los agentes SÍ
pueden editar el árbol de trabajo bajo `specs/` en F0, pero NUNCA comitear ese cambio
sin `HUMAN=1` — pero eso no es lo que dice la regla dura tal como está escrita, y no
quiero asumir cuál de las dos prevalece.

**Qué necesito para desbloquear:**
1. ¿Debo crear el directorio `specs/` y mover/reorganizar los archivos planos de la
   raíz dentro de él (según el árbol que ya describe `README.md`), o los archivos se
   quedan donde están y las rutas de la tarea son solo nombres lógicos?
2. Confirmar que, para la fase F0 específicamente, "no modificar nada bajo specs/" no
   aplica a las tres correcciones pedidas (el bloqueo real es solo sobre el commit,
   protegido por el hook `HUMAN=1`), o indicarme lo contrario.

**Las tres correcciones quedan listas para aplicar en cuanto se resuelva lo anterior**
(ya verifiqué que el contenido actual encaja con lo que pide el Apéndice A):
- `eval-clasificacion.md` §4.5: hoy dice "podrían auto-aceptarse sin revisión" /
  "auto-aceptaciones". Cambiar Cobertura → fracción de documentos sobre el umbral que
  se vuelven CANDIDATOS A APROBACIÓN MASIVA, aprobados en bloque por el archivista
  mediante acción explícita (P-09) registrada con actor y fecha (RF-RC-004); Error a
  esa cobertura → fracción de esos candidatos cuya sugerencia principal es incorrecta.
  Ajustar la pregunta de negocio de la sección y añadir traza a RF-RC-004 en §9 (hoy
  §9 solo traza RF-RC-004 a P-09, sin mencionar la acción P-09/RF-RC-004 de aprobación
  masiva explícitamente).
- `CLAUDE-CODE-KICKOFF.md` (línea ~76) y `specs/README.md`/`README.md`: "siete bounded
  contexts" → nueve, añadiendo Normalización y Extracción (ya nombrados como contextos
  aparte en `spec-captura-ingesta.md` §1 y en la tabla de `edd-harness.md` §2).
- `spec-records-custodia.md` §7 y `eval-clasificacion.md` §9: reemplazar "Acuerdo 003
  de 2015" (filas RF-RC-001, RF-RC-008) por "Acuerdo AGN 001 de 2024 (compila el
  antiguo Acuerdo 003 de 2015)"; reemplazar "Acuerdo 002 de 2014 (TRD)" (filas
  RF-RC-006, RF-RC-007 en spec-records-custodia.md; fila de trazabilidad en
  eval-clasificacion.md §9) por "Acuerdo AGN 001 de 2024 — procedimiento TRD (antes
  Acuerdos 002 de 2014 y 004 de 2019)". Conservar Ley 594, Decreto 1080 de 2015, ISO
  15489/16175 tal cual. No tocar ninguna celda `PENDIENTE`.

No se modificó ningún archivo. `STATE.md` queda marcado como bloqueado en F0 hasta
respuesta humana.

## 2026-08-18 · Resuelto

Causa raíz corregida en run_claude() de orquestador.sh (regla dura
contradecía la tarea de preflight). Política vigente: solo
specs/00-constitution.md requiere HUMAN=1. Reintentar preflight.