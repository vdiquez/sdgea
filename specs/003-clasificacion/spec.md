# Spec · Bounded Context: Clasificación

| Campo | Valor |
|-------|-------|
| Código de contexto | `CL` |
| Tipo | Probabilístico — gobernado por EDD (ver `specs/eval/edd-harness.md` §2 y `specs/eval/eval-clasificacion.md`) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-03, P-05, P-09 |

---

## 1. Propósito y frontera del contexto

Clasificación recibe el texto extraído de un documento y propone, para el núcleo de
records, dos tipos de sugerencia: **dónde ubicarlo en la TRD del cliente** (serie y
subserie) y **a qué expediente pertenece** (agrupamiento). Nunca decide por sí solo:
emite sugerencias con modelo, evidencia y confianza; una decisión humana es lo único
que las materializa (P-01, RF-RC-004).

Este contexto es enteramente probabilístico — no tiene una contraparte determinística
como Normalización o Extracción. Su spec de evaluación (`specs/eval/eval-clasificacion.md`)
ya existe y es más detallada que esta spec de dominio en lo referente a métricas,
gates y protocolo de etiquetado; esta spec cubre el contrato y el comportamiento, no
las métricas.

**Dentro de la frontera:** recepción de texto extraído, clasificación contra la TRD
vigente del cliente (serie/subserie, con confianza), agrupamiento probabilístico en
expedientes (con confianza), envío de ambos tipos de sugerencia a Records/Custodia a
través de la capa anticorrupción, exposición de evidencia trazable por sugerencia.

**Fuera de la frontera (de este contexto):** extracción de texto (contexto
Extracción); extracción de metadatos estructurados (contexto Enriquecimiento);
materialización de la clasificación o del expediente, que exige una decisión humana
(contexto Records/Custodia, RF-RC-004; interfaz en Validación Humana); indexación y
búsqueda (contexto Indexación y Búsqueda).

**Dos componentes probabilísticos, un solo contexto:** `specs/eval/edd-harness.md`
§2 lista "Clasificación" (documento → serie/subserie) y "Agrupamiento en
expedientes" (qué documentos conforman un expediente) como dos componentes
probabilísticos distintos que viven en este mismo bounded context. Cada uno produce
su propio tipo de Sugerencia; ambos comparten el mismo contrato de entrada (texto
extraído) y el mismo tratamiento arquitectónico (P-01, P-05, P-09).

---

## 2. Lenguaje ubicuo

- **Texto extraído** — la entrada de este contexto, tal como la entrega Extracción
  (ver `specs/002-extraccion/spec.md` §2).
- **TRD vigente** — la versión activa de la Tabla de Retención Documental del
  cliente contra la que se clasifica (RF-RC-006); toda sugerencia de clasificación
  referencia la versión usada.
- **Sugerencia de clasificación** — propuesta de serie/subserie de la TRD para un
  documento, con confianza y evidencia (RF-RC-003).
- **Sugerencia de agrupamiento** — propuesta de a qué expediente existente
  pertenece un documento, o de que debe originar uno nuevo, con confianza y
  evidencia.
- **Confianza** — medida de certeza de una sugerencia; ordena la cola de validación
  humana (P-09) y determina si un documento es candidato a aprobación masiva (ver
  `specs/eval/eval-clasificacion.md` §4.5).
- **Evidencia** — los fragmentos del texto extraído (o referencias a ellos) que
  sustentan una sugerencia, para que un humano pueda verificarla sin releer todo el
  documento.
- **No clasificable** — un texto extraído sin señal suficiente (p. ej. vacío o
  degradado) para producir una sugerencia razonable.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Sugerencia de clasificación** — documento de origen, TRD y versión referenciada,
  serie/subserie propuesta, confianza, evidencia, modelo, fecha.
- **Sugerencia de agrupamiento** — documento de origen, expediente propuesto (o
  marca de "expediente nuevo"), confianza, evidencia, modelo, fecha.

Este contexto **no mantiene estado propio** de sus sugerencias después de
entregarlas: su ciclo de vida posterior (pendiente, aprobada en bloque, corregida)
es responsabilidad de Records/Custodia (RF-RC-003, RF-RC-004, ya implementado en
`CapaAnticorrupcionSugerencias`) y de la interfaz de Validación Humana. Clasificación
es, por diseño, un productor que no rastrea el destino de lo que produce.

### Invariantes (no negociables)

1. Ninguna sugerencia de clasificación ni de agrupamiento materializa por sí sola la
   clasificación de un documento ni la composición de un expediente — eso exige una
   decisión humana explícita (P-01; RF-RC-004).
2. Toda sugerencia de clasificación referencia la versión específica de la TRD
   vigente al momento de generarse (RF-RC-006).
3. Toda sugerencia —de clasificación o de agrupamiento— porta modelo, evidencia y
   confianza; nunca se emite sin los tres (RF-RC-003).
4. La confianza declarada es la que ordena la cola de validación humana (P-09); una
   confianza descalibrada compromete esa cola, no solo la exactitud cruda (ver
   `specs/eval/eval-clasificacion.md` §4.6).
5. Todo texto extraído recibido produce al menos una sugerencia de clasificación o
   una marca explícita de "no clasificable" — no hay pérdida silenciosa.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Extracción | Texto extraído del documento (RF-EX-010) |
| Records/Custodia | TRD vigente — versión activa consultable (RF-RC-006) |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Records/Custodia | Sugerencia de clasificación (serie/subserie), vía capa anticorrupción |
| Records/Custodia | Sugerencia de agrupamiento en expediente, vía capa anticorrupción |
| Validación Humana | Sugerencias ordenadas por confianza, con su evidencia (P-09) |
| Operador | Reporte de documentos marcados "no clasificable" |

### Capa anticorrupción

Toda sugerencia que este contexto emite cruza la capa anticorrupción ya descrita en
`spec-records-custodia.md` §4 (`CapaAnticorrupcionSugerencias`, **ya implementada**:
T-08 la construyó, T-20 le añadió su evento de auditoría atribuible y T-21 lo hizo
atómico junto con la escritura de la sugerencia). Clasificación nunca escribe
directamente sobre el estado de un documento o expediente; el evento de auditoría
de la recepción de la sugerencia ya lo emite Records/Custodia al recibirla —
Clasificación no duplica ese evento por su lado.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-CL-001 · Recepción de texto extraído**
El contexto recibe cada texto extraído que Extracción entrega (RF-EX-010) como
disparador de la clasificación y el agrupamiento.
- Dado un texto extraído en `Extraído`, Cuando Clasificación lo recibe, Entonces
  queda disponible para producir sugerencias de clasificación y de agrupamiento.

**RF-CL-002 · Clasificación contra la TRD vigente**
El contexto propone la serie y subserie de la TRD vigente del cliente para un
documento, con una confianza asociada.
- Dado un texto extraído y una TRD vigente, Cuando se clasifica, Entonces se genera
  una sugerencia de clasificación con serie, subserie, confianza y la versión de
  TRD usada.

**RF-CL-003 · Ranking de sugerencias por confianza**
Cuando existe más de una serie/subserie candidata razonable, el contexto expone las
sugerencias ordenadas por confianza descendente.
- Dadas varias series/subseries candidatas para un documento, Cuando se consultan
  sus sugerencias, Entonces aparecen ordenadas de mayor a menor confianza.

**RF-CL-004 · Envío de sugerencias de clasificación a Records/Custodia**
Toda sugerencia de clasificación se entrega a Records/Custodia a través de la capa
anticorrupción, sin alterar el estado del documento.
- Dada una sugerencia de clasificación generada, Cuando se entrega, Entonces
  Records/Custodia la almacena como `Sugerencia` (RF-RC-003) y la clasificación del
  documento permanece sin cambio hasta una decisión humana.

**RF-CL-005 · Agrupamiento probabilístico en expedientes**
El contexto propone a qué expediente existente pertenece un documento, o que debe
originar uno nuevo, con una confianza asociada.
- Dado un texto extraído, Cuando se evalúa su agrupamiento, Entonces se genera una
  sugerencia de agrupamiento con el expediente propuesto (o la marca de expediente
  nuevo) y una confianza.

**RF-CL-006 · Envío de sugerencias de agrupamiento a Records/Custodia**
Toda sugerencia de agrupamiento se entrega a Records/Custodia a través de la capa
anticorrupción, sin alterar la composición de ningún expediente.
- Dada una sugerencia de agrupamiento generada, Cuando se entrega, Entonces
  Records/Custodia la almacena y la composición de expedientes permanece sin cambio
  hasta una decisión humana.

**RF-CL-007 · Nunca materializa directamente**
El contexto no expone ninguna operación que cambie la clasificación de un documento
ni la composición de un expediente; solo emite sugerencias.
- Dado cualquier intento de cambiar la clasificación o un expediente sin pasar por
  Records/Custodia y una decisión humana, Entonces el contexto no lo permite: no
  existe tal operación en su contrato.

**RF-CL-008 · Evidencia trazable por sugerencia**
Toda sugerencia expone la evidencia textual que la sustenta, para que un humano
pueda verificarla sin releer el documento completo.
- Dada una sugerencia, Cuando se consulta, Entonces incluye evidencia (fragmentos o
  referencias al texto extraído) que la sustenta.

**RF-CL-009 · Uso de la TRD vigente para clasificaciones nuevas**
Toda clasificación nueva usa la versión de la TRD vigente en el momento de generarse;
una publicación posterior de una nueva versión no reclasifica documentos ya
sugeridos o materializados (RF-RC-006).
- Dada una nueva versión de la TRD publicada, Cuando se clasifican documentos
  nuevos, Entonces usan la versión vigente; las sugerencias y clasificaciones ya
  generadas conservan la versión con la que se generaron.

**RF-CL-010 · Cero pérdida silenciosa**
Todo texto extraído recibido produce al menos una sugerencia de clasificación o
queda marcado explícitamente como "no clasificable"; nunca se descarta en silencio.
- Dado un texto extraído sin señal suficiente, Cuando se evalúa, Entonces queda
  marcado como "no clasificable" con razón registrada, en vez de omitirse sin dejar
  rastro.

---

## 6. Requisitos no funcionales

**RNF-CL-001 · Rendimiento a volumen** — la clasificación y el agrupamiento
sostienen fondos de millones de documentos sin degradación inaceptable.

**RNF-CL-002 · Paridad de despliegue** — el modelo de clasificación se consume
detrás de una interfaz propia con implementación autoalojada (P-03), operando
idéntico en SaaS y on-premise (P-02, P-10).

**RNF-CL-003 · Especificidad por cliente** — el componente se evalúa y opera contra
la TRD real de cada cliente; nunca contra una TRD genérica de otro cliente (ver
`specs/eval/eval-clasificacion.md` §1 y §8).

**RNF-CL-004 · Calibración de confianza sostenida** — la calibración se vigila en
producción, no solo en la línea base de evaluación (`specs/eval/eval-clasificacion.md`
§4.6).

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-CL-001 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-CL-002 | Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019) | PENDIENTE | ☐ |
| RF-CL-003 | Constitución del proyecto P-09 (validación humana como producto) | N/A | ☐ |
| RF-CL-004 | Constitución del proyecto P-01 (frontera probabilístico/determinístico) | N/A | ☐ |
| RF-CL-005 | Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015) (expediente electrónico) | PENDIENTE | ☐ |
| RF-CL-006 | Constitución del proyecto P-01 | N/A | ☐ |
| RF-CL-007 | Constitución del proyecto P-01 | N/A | ☐ |
| RF-CL-008 | Constitución del proyecto P-09 | N/A | ☐ |
| RF-CL-009 | Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019) | PENDIENTE | ☐ |
| RF-CL-010 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Si "Agrupamiento en expedientes" necesita su propia spec de
  evaluación (`eval-agrupamiento.md`) separada de `eval-clasificacion.md`, o si se
  anexa a esta última — `specs/eval/edd-harness.md` §2 y §4 ya las trata como dos
  filas distintas (métrica principal distinta: concordancia de agrupamiento por
  pares, frente a exactitud top-1 de clasificación) bajo el mismo contexto.
- **Inconsistencia detectada con `spec-records-custodia.md` §4:** su tabla de
  entradas solo nombra "Sugerencia de serie/subserie" desde Clasificación; no
  nombra explícitamente una "Sugerencia de agrupamiento en expediente", y RF-RC-008
  (conformación del expediente) no detalla si depende de este tipo de sugerencia o
  de otro mecanismo. Esta spec asume que sí through la misma capa anticorrupción;
  una revisión futura de `spec-records-custodia.md` debería cerrar esa brecha
  explícitamente en vez de dejarla implícita en dos specs distintas.
- **[CLARIFICAR]** Cuántas sugerencias de clasificación (top-N) se emiten como
  máximo por documento, y si N es fijo o configurable — `eval-clasificacion.md`
  §4.3 usa "top-3" como métrica de evaluación, no necesariamente como el N exacto
  que la interfaz de producción muestra.
- **[CLARIFICAR]** Qué constituye exactamente "no clasificable" (RF-CL-010): solo
  texto vacío o insuficiente, o también una confianza tan baja en todas las
  candidatas que ninguna sugerencia es razonable de presentar.
