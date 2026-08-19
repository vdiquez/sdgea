# Diseño del Arnés de Evaluación (EDD)

| Campo | Valor |
|-------|-------|
| Tipo | Documento de diseño — metodología EDD |
| Estado | Borrador — Etapa 0 |
| Principio rector | P-05 (evaluación antes que código, para lo probabilístico) |
| Relación | El arnés es infraestructura; se construye bajo SDD. Los componentes que evalúa se desarrollan bajo EDD. |

---

## 1. Propósito

Los componentes probabilísticos del producto no se pueden especificar como los
determinísticos: no se puede "espec-ear" que un clasificador clasifique bien. Se
desarrollan **contra evidencia**. El arnés de evaluación es la infraestructura que
mide cada componente probabilístico contra un conjunto de referencia de dato real,
con métricas y umbrales definidos **antes** de construir el componente.

Regla operativa derivada de P-05: **ningún componente probabilístico se libera sin
pasar sus gates de evaluación.** El arnés no es una herramienta de QA posterior; es
parte del lazo de desarrollo desde la primera línea de código del componente.

---

## 2. Componentes probabilísticos bajo EDD

| Componente | Contexto | Decisión que produce |
|------------|----------|----------------------|
| OCR / extracción de texto | Extracción | Texto a partir de un escaneo |
| Detección de límites de documento | Normalización | Dónde termina un documento y empieza el siguiente |
| Clasificación | Clasificación | Documento → serie/subserie de la TRD del cliente |
| Agrupamiento en expedientes | Clasificación | Qué documentos conforman un expediente |
| Extracción de metadatos | Enriquecimiento | Valores de los metadatos obligatorios |
| Recuperación | Indexación y Búsqueda | Documentos relevantes para una consulta |
| Q&A conversacional | Indexación y Búsqueda | Respuesta con citas sobre el acervo |

Cada componente tiene su propia **spec de evaluación** (ver
`eval-clasificacion.md` como ejemplar). Este documento define el marco común.

---

## 3. El set patrón (conjunto de referencia)

El set patrón es el activo más importante del arnés. Sin él, EDD no existe.

### 3.1 Regla de oro

**El set patrón se construye con dato real de cliente, nunca sintético.** Un set
sintético produce métricas que mienten: no contiene la suciedad real de un fondo
acumulado —escaneos torcidos, manuscritos, sellos, calidad mixta, series del cabo
largo—. El dato sintético se admite **solo** para pruebas de estrés y aumento de
casos borde; jamás para *medir* las métricas de referencia.

### 3.2 Estructura: anotación por capas

El set patrón se construye una sola vez como una **anotación por capas sobre una
muestra de un acervo real**. Cada registro del set patrón contiene:

- Identificador y referencia al artefacto de origen real (con su procedencia).
- Versión de la TRD asociada.
- Estrato (ver 3.3).
- **Capa OCR** — texto de referencia (o una muestra representativa de él).
- **Capa de límites** — los puntos de separación de documentos dentro del artefacto.
- **Capa de clasificación** — por cada documento: serie/subserie de referencia; marca
  de ambigüedad; conjunto aceptable de etiquetas si es ambiguo (ver 3.7).
- **Capa de metadatos** — por cada documento: valores de referencia de cada campo
  obligatorio.
- **Capa de expediente** — la agrupación de referencia de los documentos.
- **Capa de recuperación / Q&A** — consultas asociadas con sus documentos relevantes
  y respuestas de referencia.
- Metadatos de anotación — anotador(es), fecha, concordancia medida.

Construir las capas sobre el mismo corpus permite evaluar todo el pipeline sobre los
mismos documentos.

### 3.3 Estratificación y representatividad

Un muestreo puramente aleatorio sub-representa lo difícil. El set patrón se
estratifica deliberadamente por las dimensiones que provocan fallos:

- **Calidad de escaneo** — alta, media, baja, manuscrito.
- **Soporte** — born-digital frente a escaneo.
- **Serie de la TRD** — incluyendo las series del cabo largo, no solo las frecuentes.
- **Época** — documentos antiguos frente a recientes.

Las métricas se reportan en dos formas: **micro** (agregado, ponderado por la
distribución real) para la cifra de negocio, y **macro** (promediada entre series)
para que las series raras no queden ocultas por las frecuentes.

### 3.4 Tamaño y crecimiento

El factor limitante no es el total, sino la **cantidad de ejemplos por serie**: cada
serie necesita suficientes ejemplos para una estimación estable de su exactitud. Las
series del cabo largo fijan el esfuerzo de etiquetado. El set patrón **crece con el
tiempo** mediante el flywheel (ver 6.5).

### 3.5 Particiones: desarrollo y prueba

El set patrón se divide en dos particiones disjuntas:

- **Partición de desarrollo** — se inspecciona y se usa para iterar y depurar.
- **Partición de prueba (held-out)** — se reserva; solo se ejecuta para los gates;
  **no se inspecciona** durante la iteración.

Mantenerlas separadas evita el sobreajuste al set patrón. La partición de prueba
**nunca** puede aparecer como ejemplos en los prompts (few-shot), como dato de
ajuste fino, ni dentro de los índices de recuperación usados en la evaluación. La
fuga de datos es la forma número uno en que una evaluación miente.

### 3.6 Versionado

El set patrón se versiona, y su versión se ata a la **versión de la TRD** (ver
RF-RC-006). Cuando el design partner publica una nueva versión de su TRD, las
clasificaciones de referencia pueden cambiar; el set patrón se actualiza en
consecuencia. Una métrica solo es comparable entre corridas sobre la misma versión
del set patrón.

### 3.7 Subjetividad del etiquetado y concordancia

Clasificar contra una TRD tiene ambigüedad genuina: dos archivistas pueden discrepar
sobre la subserie de un mismo documento. Esto importa porque, si el set patrón tiene
ruido de etiquetado, no se puede distinguir un modelo bueno de uno perfecto: el
ruido fija el techo de la métrica.

Protocolo:
- Una porción del set patrón se etiqueta por duplicado y se mide la **concordancia
  entre anotadores**. Una concordancia baja en una serie es una señal de que esa
  serie de la TRD es ambigua —hallazgo valioso por sí mismo—.
- El archivista del design partner es la **autoridad de referencia** ante discrepancia.
- Los documentos con ambigüedad irreducible se marcan y se les asocia un **conjunto
  aceptable de etiquetas**: la predicción es correcta si cae dentro de ese conjunto.

---

## 4. Marco de métricas

Cada componente tiene su métrica principal, métricas de apoyo y el fallo que
detecta. El detalle de clasificación está en `eval-clasificacion.md`.

| Componente | Métrica principal | Apoyo | Fallo que detecta |
|------------|-------------------|-------|-------------------|
| OCR | Tasa de error de carácter (CER), estratificada por calidad | Tasa de error de palabra | Texto degradado que contamina todo lo posterior |
| Límites de documento | Precisión / recall de los puntos de separación | — | Documentos fusionados o partidos |
| Clasificación | Exactitud top-1 a nivel subserie | top-1 serie, top-3, calibración, curva cobertura–error | Asignación errónea a la TRD |
| Agrupamiento en expedientes | Concordancia de agrupamiento por pares | — | Expedientes mal conformados |
| Extracción de metadatos | F1 por campo obligatorio | Coincidencia exacta vs. normalizada | Metadatos faltantes o erróneos |
| Recuperación | Precisión@k y recall@k | Métrica de ordenamiento (nDCG / MRR) | Documentos relevantes no recuperados |
| Q&A conversacional | Correctitud de la respuesta | Correctitud de citas, tasa de alucinación, negativa apropiada, respeto de permisos | Respuestas inventadas o citas que no sustentan |

Notas:
- Las métricas agregadas ocultan fallos. La extracción de metadatos se reporta
  **por campo** (no un promedio único) y la clasificación **por serie**.
- La comparación de metadatos usa coincidencia **normalizada**: una fecha correcta
  con otro formato no es un error.
- En el Q&A, el **respeto de permisos** —no exponer contenido que el usuario no
  puede ver— se trata como gate duro (ver 5.3).

---

## 5. Política de gates

Un gate es un umbral que un componente debe superar para liberarse.

### 5.1 Gate de liberación (piso absoluto)
El componente debe alcanzar mínimos absolutos en sus métricas clave. **Los valores
absolutos no se fijan en la Etapa 0**: se calibran tras la línea base de la Etapa 1,
informados por lo alcanzable y por la economía del humano-en-el-loop. La Etapa 0
define la *estructura* del gate; la Etapa 1 calibra los *números*.

### 5.2 Gate de no-regresión (relativo)
Una nueva versión no puede degradar ninguna métrica rastreada más allá de un margen
definido frente a la versión en producción. Atrapa el caso "mejoramos la
clasificación pero rompimos la extracción de metadatos". Este gate **sí** se define
como política desde la Etapa 0: ninguna métrica cae más de un margen pequeño sin
aprobación explícita y fechada.

### 5.3 Gates duros y blandos
- **Duros** — bloquean la liberación sin excepción. Ejemplo: cualquier fuga de
  permisos en el Q&A es tolerancia cero.
- **Blandos** — bloquean salvo aprobación explícita y registrada de un responsable.

### 5.4 Cuándo corre el arnés
El arnés corre sobre la partición de desarrollo ante cada cambio de un componente
probabilístico, y sobre la partición de prueba como gate de liberación, integrado en
la canalización de integración continua. Cada corrida produce una **boleta de
resultados** versionada y comparable entre corridas.

---

## 6. El bucle EDD

1. **Definir la evaluación** — métricas, gates y set patrón, *antes* de construir el
   componente. (Etapa 0, este documento y las specs de evaluación.)
2. **Línea base** — construir la versión más simple viable del componente y medirla.
   Es la primera señal de viabilidad. (Etapa 1.)
3. **Calibrar umbrales** — fijar los gates de liberación absolutos, informados por la
   línea base y por la necesidad de negocio.
4. **Iterar** — mejorar el componente; cada cambio se mide contra desarrollo y se
   verifica contra prueba.
5. **Capturar producción** — las correcciones humanas en producción alimentan el set
   patrón. Advertencia: las correcciones de producción están **sesgadas hacia los
   casos difíciles** que el modelo falló, y deben **re-revisarse** antes de
   convertirse en verdad de referencia; no se incorporan en crudo.

---

## 7. Anti-patrones

- **Fuga de datos** — la partición de prueba aparece en prompts, en ajuste fino o en
  los índices de recuperación. La evaluación deja de medir generalización.
- **Medir sobre dato sintético** — produce métricas que no predicen el comportamiento
  real.
- **Sobreajuste al set patrón** — iterar contra la partición de prueba hasta
  memorizarla. Se previene con la separación desarrollo/prueba y con no inspeccionar
  la de prueba.
- **Goodhart** — optimizar la exactitud top-1 mientras el resultado de producto
  (tiempo de archivista ahorrado) no mejora. La curva cobertura–error es el norte.
- **Set patrón obsoleto** — la TRD cambia y el set patrón no se actualiza: se mide
  contra una verdad de referencia equivocada.
- **Métrica agregada que oculta** — un promedio único esconde el fallo en series
  raras o en campos concretos. Siempre se reporta el desglose.
- **Ruido de etiquetado no medido** — sin concordancia entre anotadores no se conoce
  el techo real de la métrica.

---

## 8. Relación con SDD y con la constitución

- Los **criterios de evaluación** de un componente probabilístico son el análogo de
  los criterios de aceptación de un requisito determinístico: también trazan a los
  requisitos y a la promesa de producto.
- El **software del arnés** es determinístico: se especifica y construye bajo SDD, y
  tendrá su propia spec de contexto.
- La **calibración de la confianza** que valida el arnés sostiene RF-RC-003: la
  `Sugerencia` que el núcleo de records almacena porta una confianza, y esa confianza
  ordena la cola de validación humana (P-09). Una confianza no calibrada rompe el
  producto aunque la exactitud sea buena.

---

## 9. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Tamaño objetivo del set patrón por serie para una estimación
  estable — se fija con la TRD real del design partner y su distribución.
- **[CLARIFICAR]** Proporción de la muestra a etiquetar por duplicado para medir
  concordancia.
- **[CLARIFICAR]** Margen exacto del gate de no-regresión por métrica.
- **[CLARIFICAR]** Mecanismo de re-revisión de las correcciones de producción antes
  de incorporarlas al set patrón.
- **[CLARIFICAR]** Herramienta de anotación a usar para construir las capas del set
  patrón con el archivista.
