# Spec · Bounded Context: Enriquecimiento

| Campo | Valor |
|-------|-------|
| Código de contexto | `EN` |
| Tipo | Probabilístico — gobernado por EDD (ver `specs/eval/edd-harness.md` §2 y §4) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-03, P-05, P-09 |

---

## 1. Propósito y frontera del contexto

Enriquecimiento recibe el texto extraído de un documento y propone los **valores de
sus metadatos obligatorios** (p. ej. fecha, remitente, destinatario, asunto, tipo
documental — el esquema exacto está pendiente, ver §8). Nunca decide por sí solo:
emite una sugerencia con modelo, evidencia y confianza por cada valor propuesto; una
decisión humana es lo único que la materializa (P-01).

Es, junto con Clasificación, uno de los dos consumidores probabilísticos paralelos
del texto extraído que produce Extracción (`spec-records-custodia.md` §4 los trata
como entradas independientes hacia el núcleo de records).

**Dentro de la frontera:** recepción de texto extraído, extracción probabilística de
valores por campo de metadato, normalización de cada valor (forma normalizada +
forma original), asignación de confianza y evidencia por valor, envío de la
sugerencia de metadatos a Records/Custodia a través de la capa anticorrupción.

**Fuera de la frontera (de este contexto):** extracción de texto (contexto
Extracción); clasificación y agrupamiento en expedientes (contexto Clasificación);
definición y gobierno del esquema de metadatos obligatorios en sí — ese esquema es
un dato del design partner y de los requisitos del AGN, no algo que este contexto
decida (ver `spec-records-custodia.md` §8, ya marcado `[CLARIFICAR]` ahí);
materialización de los metadatos de un documento, que exige una decisión humana
(contexto Records/Custodia; interfaz en Validación Humana).

---

## 2. Lenguaje ubicuo

- **Texto extraído** — la entrada de este contexto, tal como la entrega Extracción
  (ver `specs/002-extraccion/spec.md` §2).
- **Campo de metadato** — un campo definido del esquema de metadatos obligatorios
  (nombre, formato esperado). El esquema exacto está `[CLARIFICAR]` (ver §8).
- **Valor propuesto** — un candidato de valor para un campo de metadato, con su
  forma tal como aparece en el documento, su forma normalizada, confianza y
  evidencia.
- **Sugerencia de metadatos** — el conjunto de valores propuestos para un documento,
  vinculada a él (mismo término que usa `spec-records-custodia.md` §4).
- **Coincidencia normalizada** — dos valores de un mismo campo se consideran
  equivalentes si su forma normalizada coincide, aunque su forma original difiera
  (p. ej. una fecha en dos formatos distintos). Ver `specs/eval/edd-harness.md` §4.
- **Campo no encontrado** — un campo de metadato para el que el texto extraído no
  ofrece evidencia suficiente; se marca explícitamente, nunca se omite en silencio.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Sugerencia de metadatos** (agregado raíz) — documento de origen, lista de
  valores propuestos (uno por campo: campo, valor original, valor normalizado,
  confianza, evidencia), modelo, fecha.

Igual que Clasificación, este contexto **no mantiene estado propio** de sus
sugerencias después de entregarlas: su ciclo de vida posterior es responsabilidad de
Records/Custodia y de la interfaz de Validación Humana.

### Invariantes (no negociables)

1. Ninguna sugerencia de metadatos materializa por sí sola los metadatos de un
   documento — eso exige una decisión humana explícita (P-01).
2. Todo valor propuesto porta modelo, evidencia y confianza; nunca se emite un
   valor sin evidencia verificable.
3. Un valor propuesto conserva tanto su forma normalizada como la forma en que
   aparece en el documento, para que la comparación (evaluación o revisión humana)
   no penalice diferencias de formato sin diferencia de significado.
4. Un campo de metadato sin evidencia suficiente se marca explícitamente como
   "no encontrado"; nunca se omite en silencio de la sugerencia.
5. Todo texto extraído recibido produce una sugerencia de metadatos (aunque tenga
   campos marcados "no encontrado") o una marca explícita de "no enriquecible" — no
   hay pérdida silenciosa.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Extracción | Texto extraído del documento (RF-EX-010) |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Records/Custodia | Sugerencia de metadatos, vía capa anticorrupción |
| Validación Humana | Sugerencias de metadatos de baja confianza, por campo, ordenadas (P-09) |
| Operador | Reporte de documentos marcados "no enriquecible" |

### Capa anticorrupción

Toda sugerencia de metadatos cruza la capa anticorrupción ya descrita en
`spec-records-custodia.md` §4 (`CapaAnticorrupcionSugerencias`, **ya implementada**:
T-08/T-20/T-21). Enriquecimiento nunca escribe directamente sobre los metadatos de
un documento; el evento de auditoría de la recepción ya lo emite Records/Custodia al
recibir la sugerencia.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-EN-001 · Recepción de texto extraído**
El contexto recibe cada texto extraído que Extracción entrega (RF-EX-010) como
disparador del enriquecimiento.
- Dado un texto extraído en `Extraído`, Cuando Enriquecimiento lo recibe, Entonces
  queda disponible para producir una sugerencia de metadatos.

**RF-EN-002 · Extracción probabilística de valores por campo**
Para cada campo del esquema de metadatos obligatorios, el contexto propone un valor
a partir del texto extraído, con una confianza asociada.
- Dado un texto extraído y un campo de metadato, Cuando se evalúa, Entonces se
  genera un valor propuesto para ese campo con una confianza asociada, o el campo
  queda marcado "no encontrado".

**RF-EN-003 · Normalización del valor extraído**
Todo valor propuesto conserva su forma tal como aparece en el documento y su forma
normalizada.
- Dado un valor propuesto, Cuando se consulta, Entonces expone su forma original y
  su forma normalizada.

**RF-EN-004 · Confianza y evidencia por valor propuesto**
Todo valor propuesto porta la evidencia textual que lo sustenta y su confianza.
- Dado un valor propuesto, Cuando se consulta, Entonces incluye su evidencia y su
  confianza.

**RF-EN-005 · Marca explícita de campo no encontrado**
Un campo de metadato sin evidencia suficiente en el texto extraído se marca
explícitamente, sin omitirse de la sugerencia.
- Dado un campo de metadato sin evidencia suficiente, Cuando se evalúa, Entonces la
  sugerencia incluye ese campo marcado "no encontrado" en vez de omitirlo.

**RF-EN-006 · Envío de sugerencias de metadatos a Records/Custodia**
Toda sugerencia de metadatos se entrega a Records/Custodia a través de la capa
anticorrupción, sin alterar los metadatos del documento.
- Dada una sugerencia de metadatos generada, Cuando se entrega, Entonces
  Records/Custodia la almacena como `Sugerencia` y los metadatos del documento
  permanecen sin cambio hasta una decisión humana.

**RF-EN-007 · Nunca materializa directamente**
El contexto no expone ninguna operación que cambie los metadatos de un documento;
solo emite sugerencias.
- Dado cualquier intento de cambiar los metadatos de un documento sin pasar por
  Records/Custodia y una decisión humana, Entonces el contexto no lo permite: no
  existe tal operación en su contrato.

**RF-EN-008 · Granularidad por campo**
Cada valor propuesto es revisable y aprobable de forma independiente por campo, no
solo como un bloque único por documento.
- Dada una sugerencia de metadatos con varios valores propuestos, Cuando se
  consulta, Entonces cada valor se distingue individualmente por su campo.

**RF-EN-009 · Cero pérdida silenciosa**
Todo texto extraído recibido produce una sugerencia de metadatos o queda marcado
explícitamente como "no enriquecible"; nunca se descarta en silencio.
- Dado un texto extraído sin señal suficiente para ningún campo, Cuando se evalúa,
  Entonces queda marcado como "no enriquecible" con razón registrada.

**RF-EN-010 · Consulta de sugerencias de metadatos por documento**
Las sugerencias de metadatos de un documento son consultables con su campo, valor
normalizado, confianza y evidencia.
- Dado un documento con una sugerencia de metadatos, Cuando se consulta, Entonces
  se listan sus valores propuestos con campo, valor normalizado, confianza y
  evidencia.

---

## 6. Requisitos no funcionales

**RNF-EN-001 · Rendimiento a volumen** — el enriquecimiento sostiene fondos de
millones de documentos sin degradación inaceptable.

**RNF-EN-002 · Paridad de despliegue** — el modelo de extracción de metadatos se
consume detrás de una interfaz propia con implementación autoalojada (P-03),
operando idéntico en SaaS y on-premise (P-02, P-10).

**RNF-EN-003 · Reporte por campo** — la calidad del enriquecimiento se reporta por
campo obligatorio, nunca como un promedio único que oculte el fallo en un campo
concreto (`specs/eval/edd-harness.md` §4).

**RNF-EN-004 · Comparación por coincidencia normalizada** — la evaluación y la
revisión humana comparan valores por su forma normalizada, no por coincidencia
exacta de cadena de texto.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-EN-001 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-EN-002 | Requisitos funcionales de SGDEA (AGN); ISO 16175 (metadatos) | PENDIENTE | ☐ |
| RF-EN-003 | Requisitos funcionales de SGDEA (AGN); ISO 16175 (metadatos) | PENDIENTE | ☐ |
| RF-EN-004 | Constitución del proyecto P-05, P-09 | N/A | ☐ |
| RF-EN-005 | Constitución del proyecto P-08 (no descartar en silencio) | N/A | ☐ |
| RF-EN-006 | Constitución del proyecto P-01 | N/A | ☐ |
| RF-EN-007 | Constitución del proyecto P-01 | N/A | ☐ |
| RF-EN-008 | Constitución del proyecto P-09 | N/A | ☐ |
| RF-EN-009 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |
| RF-EN-010 | Constitución del proyecto P-01 (patrón de consulta de RF-RC-003) | N/A | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Esquema exacto de metadatos obligatorios (campos, formatos) —
  ya marcado `[CLARIFICAR]` en `spec-records-custodia.md` §8; esta spec hereda esa
  misma pendiente y no inventa campos concretos.
- **[CLARIFICAR]** Si Enriquecimiento depende de una clasificación (serie)
  confirmada o sugerida para saber qué campos son obligatorios, o si extrae un
  conjunto común de campos independientemente de la serie. Hay una tensión real
  entre "Clasificación y Enriquecimiento son consumidores paralelos del mismo texto
  extraído" (`spec-records-custodia.md` §4) y "los metadatos obligatorios dependen
  de la TRD/serie" (`spec-records-custodia.md` §8) que ninguna spec existente
  resuelve todavía.
- **Brecha de implementación detectada (no de spec):** `DecisionHumana` y
  `DocumentoDeArchivo` (`CustodiaOriginales.kt`, ya implementados) solo modelan
  `clasificacionResultante`; no existe todavía un campo de metadatos en
  `DocumentoDeArchivo` ni una decisión humana que los materialice. Una futura tarea
  de Records/Custodia debe extender ese modelo antes de que la materialización de
  metadatos (RF-EN-006 en adelante) tenga dónde aterrizar.
- **[CLARIFICAR]** Si existe, para metadatos, un análogo a la "aprobación masiva"
  de clasificación (`specs/eval/eval-clasificacion.md` §4.5) — un umbral de
  confianza que permita aceptar en bloque los valores de alta confianza, o si todo
  valor de metadato exige revisión campo por campo sin importar la confianza.
- **Brecha de implementación detectada (no de spec, encontrada en la revisión de
  Codex sobre T-52):** la forma genérica de `Sugerencia`/`SugerenciaEntrante` en
  records-custodia (`contenidoPropuesto`, `evidencia`, `confianza` — T-08, mismo
  contrato que usa Clasificación) no tiene un campo dedicado para la "forma
  original" que exige RF-EN-003. El dominio de Enriquecimiento sí conserva y
  expone ambas formas (`ValorPropuesto.valor_original`/`valor_normalizado`,
  probado a nivel de dominio), pero `a_sugerencia_saliente()` solo traduce la
  forma normalizada dentro de `contenido_propuesto` — la forma original nunca
  llega a records-custodia ni es consultable desde `GET /documentos/{id}/
  sugerencias`. No es una decisión de negocio: es una limitación real del
  contrato compartido de `Sugerencia`, fuera de alcance de T-49..T-52. Si la
  revisión humana llega a necesitar ver ambas formas desde records-custodia (no
  solo desde el propio Enriquecimiento), una futura tarea debe extender ese
  contrato — no inventarlo aquí.
