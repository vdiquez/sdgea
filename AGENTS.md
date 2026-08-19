# Contexto permanente — Codex (revisor)
Fuente de verdad: specs/. Gobierna .specify/memory/constitution.md
(specs/00-constitution.md es un stub que apunta ahí).
Constitución de ejecución (violarla invalida la sesión):
- SOLO .specify/memory/constitution.md es de solo lectura y solo se comitea con
  HUMAN=1 — es el único archivo sellado del proyecto (su stub en
  specs/00-constitution.md recibe el mismo trato).
- El resto de specs/ (contextos nuevos, plan-*.md, tasks-*.md, correcciones a
  specs existentes) lo puedes crear, editar y COMITEAR directamente, sin pedir
  aprobación humana por archivo. Codex revisa cada commit y VETA si viola la
  constitución o si detecta una referencia normativa/umbral inventado — ese es
  el árbitro, no una aprobación manual previa.
- Nunca inventar referencias normativas ni valores de umbral: quedan PENDIENTE.
- Nunca implementar componentes probabilísticos reales (clasificación, OCR, etc.);
  el clasificador del corte vertical es el componente FICTICIO del arnés.
- Nunca cambiar el stack decidido.
- Todo desarrollo de código es TDD contra los criterios Dado/Cuando/Entonces del RF.
  Todo desarrollo de spec sigue el rigor y formato de las specs ya existentes.
- Ante [CLARIFICAR] o ambigüedad real de negocio/legal (no procedimental — esta
  constitución ya resuelve lo procedimental): pregunta en QUESTIONS.md, marca
  "- [?]" y detente.
Coordinación: STATE.md (estado), TODO.md (cola), REVIEW.md (revisión), QUESTIONS.md.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
