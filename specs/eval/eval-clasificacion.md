# Spec de Evaluación · Componente de Clasificación

| Campo | Valor |
|-------|-------|
| Componente | Clasificación (documento → serie/subserie de la TRD del cliente) |
| Contexto | Clasificación |
| Tipo | Probabilístico — gobernado por EDD |
| Estado | Borrador — Etapa 0 |
| Marco | Ver `edd-harness.md` |

---

## 1. Componente y frontera

El componente de clasificación recibe un documento (ya con texto extraído) y propone
su ubicación en la TRD del cliente: serie y subserie. Emite además una **confianza**.
Su salida es una `Sugerencia` (RF-RC-003): no decide; propone.

No es un clasificador genérico. Es **por cliente**: cada entidad tiene su propia TRD.
La evaluación, por tanto, siempre es relativa a una versión específica de una TRD real.

---

## 2. Por qué esta evaluación define el éxito del producto

La exactitud de clasificación es la métrica que decide si el producto funciona. Pero
el número que importa no es la exactitud cruda: es **cuánto trabajo de archivista se
elimina a un nivel de error aceptable**. La promesa comercial —reducir
drásticamente el tiempo de organización del archivo— es, literalmente, un punto de
la curva cobertura–error (sección 4.5). Esta spec existe para medir esa curva, no
solo una exactitud.

---

## 3. Unidad de evaluación y verdad de referencia

- **Unidad** — un documento de la capa de clasificación del set patrón.
- **Verdad de referencia** — la serie/subserie asignada por el archivista del design
  partner contra una versión específica de la TRD. Para documentos con ambigüedad
  irreducible, un **conjunto aceptable** de subseries (ver `edd-harness.md` 3.7).
- **Predicción** — la lista ordenada de subseries propuestas con sus confianzas.

---

## 4. Métricas

### 4.1 Exactitud top-1 a nivel subserie
Métrica principal. La subserie es el destino real de archivación. La predicción de
mayor confianza debe coincidir con la subserie de referencia (o caer en el conjunto
aceptable si el documento es ambiguo).

### 4.2 Exactitud top-1 a nivel serie
Crédito parcial: acertar la serie pero errar la subserie es un error menor que errar
ambas. Reportada aparte de 4.1.

### 4.3 Exactitud top-3
La interfaz de validación muestra las primeras N sugerencias. Si la subserie correcta
está entre las tres primeras, la tarea del archivista se reduce a una selección
rápida. Mide la utilidad real para el humano-en-el-loop.

### 4.4 Macro frente a micro
- **Micro** — ponderada por la distribución real de series; es la cifra de negocio.
- **Macro** — promediada entre series; evita que las series frecuentes oculten el
  fallo en las del cabo largo.
Ambas se reportan siempre. Una brecha grande entre macro y micro es señal de que las
series raras fallan.

### 4.5 Curva cobertura–error
La métrica norte. Para cada umbral de confianza, dos cantidades:
- **Cobertura** — fracción de documentos con confianza por encima del umbral, que se
  vuelven **candidatos a aprobación masiva**: el archivista los aprueba en bloque
  mediante una acción explícita (P-09), registrada con actor, fecha y sugerencias
  referenciadas (RF-RC-004).
- **Error a esa cobertura** — fracción de esos candidatos cuya sugerencia principal es
  incorrecta.
La curva responde la pregunta de negocio: *a un nivel de error tolerable, ¿qué
porcentaje del fondo puede aprobarse en bloque con revisión mínima?* El resto va a la
cola de validación documento a documento. El caso de negocio se construye sobre esta
curva.

### 4.6 Calibración de la confianza
La confianza ordena la cola de validación humana (P-09): el archivista revisa primero
lo de menor confianza. Si la confianza no está calibrada, la cola está mal ordenada y
el producto pierde su ventaja aunque la exactitud sea alta. Se mide la fidelidad de
la confianza: cuando el componente declara una confianza dada, su tasa de acierto
real debe corresponder a ese valor.

---

## 5. Protocolo de etiquetado

- El set patrón de clasificación lo etiqueta un **archivista**, contra la TRD real y
  versionada del design partner.
- Una porción se etiqueta por duplicado para medir la **concordancia entre
  anotadores** (ver `edd-harness.md` 3.7).
- Las series con concordancia baja se documentan: indican ambigüedad en la propia
  TRD, un hallazgo que se devuelve al design partner.
- Los documentos ambiguos reciben un **conjunto aceptable** de subseries.

---

## 6. Gates propuestos

> Estructura definida en la Etapa 0. Valores absolutos calibrados tras la línea base
> de la Etapa 1 (ver `edd-harness.md` 5).

- **Gate de liberación** — pisos absolutos sobre exactitud top-1 subserie (macro y
  micro) y sobre un punto acordado de la curva cobertura–error. *Valores: pendientes
  de calibración.*
- **Gate de no-regresión** — ninguna de las métricas de las secciones 4.1–4.6
  degrada más allá del margen de política sin aprobación explícita.
- **Calibración como gate blando** — una confianza notablemente descalibrada bloquea
  la liberación salvo aprobación registrada, por su efecto sobre la cola de validación.

---

## 7. Estratificación específica

La evaluación de clasificación se desglosa, como mínimo, por:
- **Serie de la TRD** — con atención explícita a las series del cabo largo.
- **Calidad de escaneo** — la clasificación depende del texto extraído; un OCR pobre
  arrastra el error. El desglose separa el fallo de clasificación del fallo heredado
  de OCR.
- **Soporte** — born-digital frente a escaneo.

---

## 8. Anti-patrones específicos de clasificación

- **Reportar solo la exactitud micro** — oculta el colapso en series raras, que son
  numerosas en una TRD real.
- **Optimizar top-1 e ignorar la calibración** — produce un modelo "exacto" con una
  cola de validación mal ordenada.
- **Evaluar con la TRD del proveedor y no la del cliente** — la clasificación es por
  cliente; una TRD genérica no predice nada.
- **Fuga**: usar documentos de la partición de prueba como ejemplos few-shot del
  prompt de clasificación.
- **Ignorar la curva cobertura–error** — lleva a perseguir una exactitud que no se
  traduce en trabajo de archivista ahorrado (Goodhart).

---

## 9. Trazabilidad

| Elemento | Traza a |
|----------|---------|
| La salida es una `Sugerencia`, no estado | RF-RC-003; constitución P-01 |
| El cambio de clasificación exige decisión humana | RF-RC-004; constitución P-09 |
| La confianza ordena la cola de validación | RF-RC-003; constitución P-09 |
| La curva cobertura–error | Promesa de producto: reducción del tiempo de organización; RF-RC-004 |
| Clasificación contra TRD versionada | RF-RC-006; Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019) |

---

## 10. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Punto exacto de la curva cobertura–error que define el caso de
  negocio y, por tanto, el gate de liberación.
- **[CLARIFICAR]** Cómo se cuenta el acierto cuando la TRD tiene jerarquía profunda
  (más de dos niveles): ¿solo serie/subserie o niveles adicionales?
- **[CLARIFICAR]** Tratamiento de un documento que legítimamente podría pertenecer a
  dos expedientes de series distintas.
- **[CLARIFICAR]** Umbral mínimo de concordancia entre anotadores por debajo del cual
  una serie se considera demasiado ambigua para evaluar de forma estricta.
