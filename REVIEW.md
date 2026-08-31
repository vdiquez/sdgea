OK: sin VETO.

Commit revisado: `6dcad2b836b40355d007a0accdcdfdfb9a60f487` — `fix: corrige segundo VETO de Codex sobre specs/008-ui-demo -- RF-UI-004 cita RF-RC-003 explicitamente`.

Alcance: únicamente `specs/008-ui-demo/spec.md`; no hay cambios de código, infraestructura ni pruebas.

- P-01: conforme. RF-UI-004 ahora traza expresamente `FICTICIO → Sugerencia (RF-RC-003) → cola de Validación Humana → decisión humana → materialización`. Su segundo criterio declara que Clasificación cruza la capa anticorrupción, que Records/Custodia almacena la entrada como `Sugerencia` y que no cambia ningún estado documental. Esto coincide con RF-CL-004 y con el Dado/Cuando/Entonces de RF-RC-003. La UI no reenvía ni materializa por sí sola; la decisión sigue en RF-UI-005.
- P-03: conforme. El commit no incorpora ninguna capacidad externa ni evita una interfaz. Mantiene la restricción de no exponer Records/Custodia directamente mediante el navegador mientras esté pendiente el prerrequisito de autorización.
- P-08: conforme para el alcance del commit. No agrega una transición de estado. La recepción que referencia permanece sometida al contrato existente de Records/Custodia, cuya bitácora incluye `SUGERENCIA_RECIBIDA`; el cambio no elimina ni debilita esa auditabilidad.
- Control de `specs/`: el diff no añade referencias a Acuerdo, Ley, Decreto o ISO, ni umbrales numéricos. Las referencias nuevas son internas y ya existían antes del commit: RF-RC-003, RF-CL-004 y RF-VH-001. No hay cita ni valor inventado que deba quedar PENDIENTE/[CLARIFICAR].
- Honestidad de pruebas: no se modifican ni se añaden pruebas. Por tanto, el commit no pretende demostrar por tests un RF-UI aún en Borrador ni hay aserciones nuevas que puedan estar amañadas. La corrección es de trazabilidad de spec; sus futuras pruebas de UI deberán comprobar este criterio contra el flujo real, no solo la presencia textual de la referencia.

Verificaciones: `git show HEAD`, diff contra `HEAD^`, `git diff --check`, trazabilidad contra RF-CL-004, RF-RC-003, RF-VH-001 y el contrato de auditoría de `spec-infra-servicios.md`. No añadí tareas a `TODO.md`.
