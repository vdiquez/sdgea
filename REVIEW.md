VETO: P-01 incumplido: el resultado probabilístico de OCR se persiste directamente en `TextoExtraido`, sin cruzar la capa anticorrupción como `Sugerencia`.

# Revisión de `e623ad6c` — T-40 Extracción

Revisados `AGENTS.md`, `.specify/memory/constitution.md`, `STATE.md`,
`QUESTIONS.md`, la spec del contexto `specs/002-extraccion/spec.md` y el diff
completo de `HEAD` contra su padre. La decisión explícita de Victor del
2026-08-27 exige confirmación humana para todo OCR; se tuvo en cuenta.

## Veto

La corrección sí elimina la mutación terminal directa que motivó el veto de
`dd97fb4`: `recibir_resultado_ocr` deja `estado=PENDIENTE_DE_EXTRACCION` y
`confirmar_extraccion(..., actor, fecha)` es quien pasa a `EXTRAIDO`.

Pero P-01 no exige solamente aplazar esa transición: exige que toda salida
probabilística cruce la capa anticorrupción **como Sugerencia**. Aquí
`recibir_resultado_ocr` recibe un `ResultadoOcr` y lo instala directamente en
el agregado mediante `replace(texto, resultado_ocr=resultado)`. No existe una
`Sugerencia`/propuesta separada ni una capa anticorrupción que traduzca la
salida del OCR antes de almacenarla. Por tanto el dato probabilístico aún entra
directamente al estado persistible del núcleo; la posterior confirmación humana
no repara esa frontera física ausente. RF-EX-004 y RF-EX-011 repiten la misma
excepción al decir “queda adjunto al texto extraído”.

La corrección requerida es modelar la recepción como una sugerencia de OCR
separada, con modelo, evidencia, confianza y fecha, recibida por una capa
anticorrupción; la decisión humana debe referenciar esa sugerencia y ser la
única operación que materialice contenido/calidad en `TextoExtraido`.

## P-08

Hay además un defecto en el nuevo evento de recepción. La función no cambia el
campo `estado` (permanece `PENDIENTE_DE_EXTRACCION`), pero emite
`estado_anterior=None` y `estado_posterior="RESULTADO_OCR_RECIBIDO"`. Este
último no es un estado del agregado. P-08 exige evento atribuible, fechado y
con estado anterior y posterior de la transición; el test nuevo fija esos
valores ficticios y por ello no detecta la discrepancia. Al incorporar la
sugerencia debe auditarse su recepción con una representación verdadera y
coherente de antes/después, y la futura persistencia T-41 debe conservarla en
la bitácora append-only y en la misma transacción.

## P-03

Pasa para este commit: el OCR sigue siendo FICTICIO y `ResultadoOcr` es un dato
ya calculado por el llamador; no hay invocación directa de motor OCR ni otra
capacidad externa. La spec mantiene la exigencia de interfaz propia y dos
implementaciones para cuando se integre un motor real.

## Honestidad de los tests

Los cuatro tests añadidos ejercitan funciones reales y no usan dobles que
predeterminen el resultado. Sin embargo, el test de RF-EX-004 está amañado en
el sentido relevante para esta revisión: sólo comprueba que no cambie el enum
`estado` y que el `ResultadoOcr` quede directamente adjunto; no comprueba el
criterio constitucional de que la salida probabilística sea una `Sugerencia`
tras la capa anticorrupción. El test de confirmación tampoco puede demostrar
que el actor sea autorizado: la producción acepta cualquier `str` y no tiene
puerto/verificación de autorización. No acredita íntegramente el “actor
autorizado” de RF-EX-011.

## Specs, referencias y umbrales

El commit modifica `specs/002-extraccion/spec.md`. No introduce Acuerdo, Ley,
Decreto, ISO ni umbral numérico nuevos. RF-EX-011 sólo referencia P-01 y los
RF internos RF-RC-004/RF-NO-004; la tabla la deja correctamente como `N/A`.
No hay veto normativo/por umbral independiente.

## Verificación ejecutable

`git diff --check HEAD^ HEAD` no informó errores de espacios. Intenté
`bash ./test.sh`, pero Gradle no pudo crear su lock bajo `C:\.gradle` por las
restricciones del entorno, antes de ejecutar la suite; por ello no se afirma
un resultado local verde.

## Resultado

El commit permanece vetado hasta corregir P-01 y P-08 indicados arriba. No se
añadieron tareas a `TODO.md`: la corrección se deriva del propio T-40/RF-EX-004
y RF-EX-011 ya existentes y debe hacerse como corrección del commit vetado.
