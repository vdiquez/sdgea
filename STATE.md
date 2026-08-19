# STATE
Fase: F1 en curso — andamiaje hecho, falta cierre (revisión de Codex → REVIEW.md).

Hecho:
- F0: correcciones de corpus aplicadas y comiteadas (A.1-A.3); constitución
  ubicada en specs/00-constitution.md (commit HUMAN=1 de Victor).
- F1.D1: decisión de stack — Kotlin/Spring Boot (núcleo determinístico) +
  Python 3.12/FastAPI (capa probabilística + arnés EDD), Gradle + uv,
  Docker/docker-compose, GitHub Actions.
- F1: andamiaje raíz (nueve contextos, Gradle multi-módulo, workspace uv,
  CI, empaquetado dual) — dos incrementos comiteados.
- F1: interfaces P-03 (platform-kotlin, platform-python) + esqueleto
  ejecutable del arnés EDD con componente FICTICIO — comiteado.
- F1: `specify init` (spec-kit) corrido con integraciones Claude Code + Codex.
  La constitución se migró de specs/00-constitution.md a la ruta que espera
  spec-kit, .specify/memory/constitution.md (contenido idéntico); el archivo
  viejo queda como stub. Pendiente el commit HUMAN=1 de Victor.

Pendiente para cerrar F1:
- Commit HUMAN=1 de la migración de la constitución.
- `codex exec` revisa el esqueleto completo contra la constitución → primera
  REVIEW.md (F1, paso 4 del plan).
