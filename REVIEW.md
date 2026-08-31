OK: sin VETO.

Commit revisado: `8565402777873596c478b3bb4f41efd1e7296939` — corrección de T-58, contrastada con la constitución, `specs/spec-infra-servicios.md` §2, `specs/001-normalizacion/spec.md`, `specs/002-extraccion/spec.md` y `STATE.md`.

Resultado

- P-01: conforme. El cambio no añade inferencia ni modifica una ruta de materialización; únicamente lee eventos históricos. Las salidas probabilísticas siguen fuera de su alcance y no aparece una escritura de estado sin decisión humana.
- P-03: conforme. No se incorpora ni se consume capacidad externa; tampoco se evita una interfaz existente.
- P-08: conforme. La lectura de compatibilidad de `eventos_auditoria` ahora recupera también `VALIDACION_APLICADA`, que es ambiguo entre Normalización y Extracción, y lo expone con `origen_verificado=false`. Así conserva actor, fecha, tipo y estados anterior/posterior, sin omitir la transición ni atribuirla falsamente a un único contexto. Los tipos exclusivos se mantienen marcados como verificados y los de otro contexto continúan excluidos.

Honestidad de los tests

Las pruebas nuevas son honestas respecto del criterio corregido: siembran una fila heredada propia, una ajena y una ambigua; exigen recuperar la propia y la ambigua, exigir `origen_verificado=false` en la ambigua, y no recuperar la ajena. No maquillan la pérdida anterior mediante una aserción de ausencia. Los endpoints `GET /eventos-auditoria` devuelven directamente el repositorio revisado; además, las suites HTTP existentes verifican que las transiciones incluyen actor y estados anterior/posterior.

Control adicional de `specs/`

`specs/spec-infra-servicios.md` cambia únicamente la política de recuperación del historial ambiguo. El diff no añade referencias a Acuerdo, Ley, Decreto o ISO, ni introduce un umbral numérico nuevo; no hay cita ni valor inventado.

Verificaciones realizadas

- `git show HEAD`, `git diff HEAD^ HEAD --check` y trazado de los endpoints/repositores de ambos contextos.
- Pruebas focalizadas: Normalización `22 passed`; Extracción `28 passed`.
- `bash ./test.sh` pasó sus dos controles de scripts iniciales, pero no pudo continuar con Gradle: el wrapper intenta crear su lock bajo `C:\.gradle`, ruta sin permiso en este entorno. No es un fallo de pruebas del commit.

No añadí tareas a `TODO.md`.
