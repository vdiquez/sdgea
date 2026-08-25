# Spec · Bounded Context: Indexación y Búsqueda

| Campo | Valor |
|-------|-------|
| Código de contexto | `IB` |
| Tipo | Híbrido — núcleo determinístico (SDD: construcción y mantenimiento del índice) con dos componentes probabilísticos bajo EDD (recuperación por relevancia y Q&A conversacional, ver `specs/eval/edd-harness.md` §2 y §4) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-03, P-05, P-06, P-08 |

---

## 1. Propósito y frontera del contexto

Indexación y Búsqueda indexa los documentos ya **materializados** por Records/Custodia
(tras una decisión humana) y responde consultas de dos formas: **búsqueda** (léxica
y semántica, con resultados ordenados por relevancia) y **Q&A conversacional**
(respuesta en lenguaje natural con citas verificables). Es el contexto donde la
propiedad de **disponibilidad** del documento de archivo se hace operable para el
usuario final.

No decide qué es un documento ni cómo se clasifica: solo indexa y recupera lo que
Records/Custodia ya confirmó como estado, y el texto que Extracción ya extrajo.

**Dentro de la frontera:** indexación léxica y semántica de documentos
materializados, actualización del índice ante cambios, búsqueda por palabra clave y
por relevancia semántica, filtrado por permisos, respuesta conversacional con citas,
auditoría de acceso por consulta.

**Fuera de la frontera (de este contexto):** clasificación y extracción de
metadatos (contextos Clasificación y Enriquecimiento); decisión de qué permisos
tiene un usuario (contexto Seguridad y Acceso — este contexto los **consume**, no
los define); materialización de documentos (contexto Records/Custodia).

**Por qué es un contexto híbrido:** `specs/eval/edd-harness.md` §2 lista
"Recuperación" y "Q&A conversacional" como componentes probabilísticos bajo este
contexto, con sus propias métricas (§4: precisión@k/recall@k/nDCG/MRR para
recuperación; correctitud, citas, tasa de alucinación y respeto de permisos para
Q&A). La construcción y el mantenimiento del índice en sí —tomar un documento
materializado y escribirlo en un índice léxico y uno vectorial— es una operación
determinística, especificada bajo SDD (P-06), igual que "indexación" aparece
explícitamente en la lista de componentes determinísticos de la constitución.

**Por qué P-03 pesa más aquí que en cualquier otro contexto:** de las seis
capacidades externas que la constitución obliga a abstraer (P-03), **tres** viven en
este contexto: embeddings, índice vectorial e índice léxico — y el Q&A añade una
cuarta, inferencia LLM. Ninguna se consume directo; cada una tiene su interfaz con
implementación autoalojada para on-premise (P-03, P-10).

---

## 2. Lenguaje ubicuo

- **Documento materializado** — el documento tal como lo expone Records/Custodia
  tras una decisión humana: clasificación, metadatos, procedencia (ver
  `spec-records-custodia.md` §2).
- **Índice léxico** — estructura de búsqueda por palabra clave / texto completo.
- **Índice vectorial** — estructura de búsqueda por similitud semántica (embeddings).
- **Consulta** — la pregunta o los términos de búsqueda que un usuario autorizado
  envía.
- **Recuperación** — el componente probabilístico que ordena los resultados de una
  consulta por relevancia semántica (P-05).
- **Respuesta Q&A** — la respuesta en lenguaje natural a una consulta
  conversacional, con sus citas.
- **Cita** — referencia a un documento (y fragmento) concreto que sustenta una
  afirmación de una respuesta Q&A.
- **Alucinación** — una afirmación de una respuesta Q&A sin ninguna cita real que la
  sustente; se trata como fallo (`specs/eval/edd-harness.md` §4).
- **Negativa apropiada** — cuando la evidencia disponible no sustenta una respuesta,
  el sistema declara que no puede responder en vez de inventar una.
- **Permisos del usuario** — el conjunto de documentos o expedientes que un usuario
  autenticado puede ver, definido por Seguridad y Acceso.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Entrada de índice** (agregado raíz) — referencia al documento materializado de
  origen, su texto extraído, sus campos indexados (léxicos y vectoriales), estado,
  fecha de indexación.
- **Consulta** — texto de la consulta, usuario, fecha, permisos aplicados,
  resultados devueltos o respuesta Q&A generada.

### Estados de una entrada de índice

`Pendiente de indexación` → `Indexada`. Una entrada ya indexada se **actualiza**
cuando el documento materializado subyacente cambia (p. ej. una rectificación de
clasificación); la eliminación definitiva de una entrada por disposición final
queda fuera de alcance de la Etapa 0 (Etapa 5, ver "Disciplina de alcance" de la
constitución).

### Invariantes (no negociables)

1. El índice solo contiene documentos **materializados** (tras decisión humana);
   ninguna sugerencia pendiente aparece en resultados de búsqueda ni en respuestas
   Q&A como si fuera estado confirmado (P-01).
2. Ningún resultado de búsqueda ni respuesta Q&A expone contenido que el usuario
   consultante no tiene permiso de ver — tolerancia cero, sin excepción
   (`specs/eval/edd-harness.md` §5.3).
3. Toda respuesta Q&A que afirma algo sobre el acervo lo sustenta con al menos una
   cita verificable a un documento real; una afirmación sin cita que la sustente es
   una alucinación y se trata como fallo.
4. Toda consulta que accede a contenido de un documento genera un evento de
   auditoría de acceso, igual que RF-RC-010 lo exige para la recuperación del
   original.
5. La construcción y actualización del índice es determinística y reproducible a
   partir del mismo documento materializado; el ranking de relevancia
   (recuperación) y la generación de respuestas (Q&A) son los únicos componentes
   probabilísticos de este contexto, gobernados por EDD (P-05).

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Records/Custodia | Documento materializado (clasificación, metadatos, procedencia) tras decisión humana |
| Extracción | Texto extraído del documento, para indexar su contenido (RF-EX-010) |
| Seguridad y Acceso | Permisos del usuario consultante |
| Usuario autorizado | Consulta de búsqueda o pregunta conversacional |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Usuario autorizado | Resultados de búsqueda ordenados por relevancia, o respuesta Q&A con citas |
| Seguridad y Acceso | Eventos de auditoría de acceso a contenido |
| Operador | Reporte de documentos materializados pendientes de indexar |

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-IB-001 · Indexación de documentos materializados**
Todo documento materializado por Records/Custodia genera o actualiza una entrada de
índice.
- Dado un documento recién materializado, Cuando Indexación y Búsqueda lo recibe,
  Entonces existe una entrada de índice vinculada a él en estado `Indexada`.

**RF-IB-002 · Indexación léxica**
El contenido textual de un documento materializado se indexa para búsqueda por
palabra clave / texto completo.
- Dado un documento materializado con su texto extraído, Cuando se indexa,
  Entonces su contenido es recuperable por búsqueda de palabra clave.

**RF-IB-003 · Indexación vectorial**
El contenido textual de un documento materializado se indexa también para búsqueda
por similitud semántica.
- Dado un documento materializado con su texto extraído, Cuando se indexa,
  Entonces su contenido es recuperable por similitud semántica.

**RF-IB-004 · Actualización del índice ante cambios materializados**
Cuando un documento materializado cambia (p. ej. una rectificación de
clasificación), su entrada de índice se actualiza para reflejar el cambio.
- Dado un documento ya indexado cuyo estado materializado cambia, Cuando se
  detecta el cambio, Entonces su entrada de índice se actualiza en consecuencia.

**RF-IB-005 · Búsqueda léxica y por metadatos**
Un usuario autorizado puede buscar por palabra clave y filtrar por metadatos o
clasificación (serie/subserie, fecha, campos de Enriquecimiento).
- Dada una consulta por palabra clave con filtros, Cuando se ejecuta, Entonces
  devuelve solo documentos materializados que cumplen los filtros y contienen el
  término.

**RF-IB-006 · Recuperación por relevancia semántica**
Para una consulta, el componente probabilístico de recuperación ordena los
resultados por relevancia semántica, no solo por coincidencia léxica.
- Dada una consulta, Cuando se ejecuta la recuperación semántica, Entonces los
  resultados se devuelven ordenados por relevancia estimada.

**RF-IB-007 · Respuesta conversacional (Q&A) con citas**
Para una pregunta conversacional, el contexto genera una respuesta en lenguaje
natural sustentada por al menos una cita verificable a un documento real.
- Dada una pregunta conversacional con evidencia suficiente en el acervo, Cuando se
  responde, Entonces la respuesta incluye al menos una cita verificable a un
  documento real que la sustenta.

**RF-IB-008 · Cero exposición sin permiso**
Ningún resultado de búsqueda ni respuesta Q&A expone contenido, ni siquiera como
referencia o cita, de un documento que el usuario consultante no tiene permiso de
ver.
- Dada una consulta de un usuario sin permiso sobre un documento relevante, Cuando
  se responde, Entonces ese documento no aparece en los resultados ni se cita en la
  respuesta.

**RF-IB-009 · Auditoría de acceso por consulta**
Toda consulta que accede a contenido de al menos un documento genera un evento de
auditoría de acceso.
- Dada una consulta resuelta con al menos un documento accedido, Cuando se
  completa, Entonces existe un evento de auditoría de acceso con actor, fecha y los
  documentos accedidos.

**RF-IB-010 · Negativa apropiada**
Cuando la evidencia en el acervo no sustenta una respuesta a una pregunta
conversacional, el contexto lo declara en vez de generar una respuesta inventada.
- Dada una pregunta conversacional sin evidencia suficiente en el acervo, Cuando se
  responde, Entonces el contexto declara que no puede responder en vez de generar
  una afirmación sin cita que la sustente.

---

## 6. Requisitos no funcionales

**RNF-IB-001 · Rendimiento a volumen** — la búsqueda y el Q&A responden en un
tiempo aceptable sobre fondos de millones de documentos indexados.

**RNF-IB-002 · Paridad de despliegue** — el índice léxico, el índice vectorial y la
inferencia LLM se consumen cada uno detrás de su propia interfaz con
implementación autoalojada (P-03), operando idéntico en SaaS y on-premise (P-02,
P-10), incluyendo entornos sin conectividad saliente.

**RNF-IB-003 · Consistencia de permisos** — el filtrado por permisos se aplica de
forma idéntica sin importar el mecanismo de recuperación (léxico, vectorial o
Q&A) — no hay una ruta de consulta que se salte el filtro.

**RNF-IB-004 · Trazabilidad de citas** — toda cita de una respuesta Q&A es
verificable contra el documento real que referencia.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-IB-001 | Constitución del proyecto P-06; RF-RC-004 (materialización) | N/A | ☐ |
| RF-IB-002 | Constitución del proyecto — disponibilidad (El objeto a proteger); Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-IB-003 | Constitución del proyecto — disponibilidad (El objeto a proteger) | N/A | ☐ |
| RF-IB-004 | Constitución del proyecto P-06 | N/A | ☐ |
| RF-IB-005 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-IB-006 | Constitución del proyecto P-05 | N/A | ☐ |
| RF-IB-007 | Constitución del proyecto P-05 | N/A | ☐ |
| RF-IB-008 | Constitución del proyecto — autenticidad/integridad (El objeto a proteger); P-01 | N/A | ☐ |
| RF-IB-009 | Constitución del proyecto P-08; RF-RC-010 (patrón de auditoría de acceso) | N/A | ☐ |
| RF-IB-010 | Constitución del proyecto P-05 (`edd-harness.md` §4, negativa apropiada) | N/A | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Mecanismo exacto de correlación entre el texto extraído
  (Extracción) y el documento materializado (Records/Custodia) para construir una
  entrada de índice completa — ninguna spec existente detalla si Records/Custodia
  reenvía el texto junto con el estado materializado o si Indexación lo correlaciona
  por su cuenta (misma pregunta sin resolver que ya señaló
  `specs/004-enriquecimiento/spec.md` §8 desde otro ángulo).
- **[CLARIFICAR]** Motor(es) concretos de índice léxico, índice vectorial e
  inferencia LLM — la constitución exige abstracción (P-03) e implementación
  autoalojada viable (P-10), pero no fija cuáles; decisión de la Etapa 1, informada
  por el arnés.
- **[CLARIFICAR]** Umbral o mecanismo exacto de "negativa apropiada" en Q&A: cuánta
  evidencia debe faltar antes de que el sistema prefiera declarar que no puede
  responder en vez de intentarlo.
- **[CLARIFICAR]** Si una rectificación de clasificación o metadatos ya
  materializados debe reindexar automáticamente (RF-IB-004) de inmediato o en un
  ciclo asíncrono, y si eso es alcance de la Etapa 0 o de una etapa posterior.
