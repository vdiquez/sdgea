OK: 6d364a1 siembra T-49..T-52 para Enriquecimiento sin violaciones detectadas de la constitución.

Alcance revisado: `git show HEAD` modifica únicamente `TODO.md` (122 líneas); no hay
código, pruebas ni archivos bajo `specs/` modificados. El contexto aplicable es
`specs/004-enriquecimiento/spec.md` y el contrato de infraestructura existente.

- P-01: conforme en el plan. T-49 declara valores y sugerencias FICTICIOS,
  recibidos ya calculados; prohíbe materialización y conserva esta última en
  Records/Custodia tras decisión humana. T-50 solo reenvía la sugerencia a
  `POST /sugerencias` por la capa anticorrupción.
- P-03: conforme en el plan. La dependencia externa hacia Records/Custodia queda
  explícitamente detrás del puerto/`Protocol` `EnviadorDeSugerencias`; el
  orquestador no se tipa contra el cliente HTTP concreto. No se introduce una
  nueva capacidad externa directa.
- P-08: conforme con el límite del contexto definido por la spec: Enriquecimiento
  no mantiene agregado ni transición persistida propia; la recepción de cada
  sugerencia en Records/Custodia emite el evento de auditoría. T-52 exige además
  comprobar el ciclo real contra ese receptor.
- Ambigüedades: los dos `[CLARIFICAR]` de la spec (esquema de metadatos y su
  relación con clasificación) se preservan. El plan recibe campo/valor/razón del
  llamador y no fija taxonomías ni umbrales.
- Referencias/umbrales: el chequeo reforzado no aplica porque el commit no toca
  `specs/`. Aun así, el diff no añade Acuerdos, Leyes, Decretos, ISO ni umbrales
  regulatorios; los números que aparecen son identificadores de RF/tareas,
  puertos o cobertura propuesta de Postman, no requisitos normativos nuevos.
- Honestidad de tests: no hay pruebas añadidas o alteradas en este commit, por lo
  que no existe una prueba que pueda estar amañada ni una ejecución que atribuirle.
  Las tareas futuras sí exigen TDD contra Dado/Cuando/Entonces, MockTransport que
  compruebe método/URL/cuerpo y validación Docker/Newman real; su honestidad debe
  revisarse cuando el código y las pruebas existan.

No se añadieron tareas a TODO.md: el commit ya contiene la cola derivada de la
spec existente y esta revisión no descubrió trabajo adicional.
