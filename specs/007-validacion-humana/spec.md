# Spec · Bounded Context: Validación Humana

| Campo | Valor |
|-------|-------|
| Código de contexto | `VH` |
| Tipo | Determinístico — gobernado por SDD (la constitución lista "validación" explícitamente entre los componentes determinísticos, P-06); orquesta alrededor de salidas probabilísticas sin serlo él mismo |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-05, P-06, P-08, P-09 |

---

## 1. Propósito y frontera del contexto

Validación Humana es donde el principio fundacional del producto se hace operable:
**la IA propone, el motor de records dispone** (P-01), y quien dispone es siempre un
humano actuando aquí. Este contexto agrega las sugerencias que producen todos los
contextos probabilísticos (Clasificación, Enriquecimiento, Extracción,
Normalización) en colas de revisión ordenadas por confianza, permite decidir sobre
ellas —una por una o en bloque— y produce la `Decisión humana` que Records/Custodia
materializa (RF-RC-004) o que Normalización usa para confirmar límites de documento
(RF-NO-004).

No es un componente probabilístico: decidir, registrar quién decidió y cuándo, y
ordenar una cola por un valor de confianza ya calculado son operaciones
determinísticas. Lo que orquesta —las sugerencias— sí es probabilístico, pero eso
ya ocurrió en otro contexto antes de llegar aquí.

**Dentro de la frontera:** agregación de sugerencias en colas de revisión por
confianza, revisión y decisión individual, aprobación masiva de candidatos de alta
confianza, confirmación o corrección de límites de documento, registro atribuible de
toda decisión, control de acceso por recurso, captura de correcciones para
retroalimentar el arnés de evaluación.

**Fuera de la frontera (de este contexto):** la generación de sugerencias en sí
(Clasificación, Enriquecimiento, Normalización, Extracción); el almacenamiento
definitivo de la decisión materializada (Records/Custodia, ya implementado —
`CapaAnticorrupcionSugerencias`, `CustodiaOriginales.materializar`); la definición de
permisos (Seguridad y Acceso — este contexto los consume, no los define).

---

## 2. Lenguaje ubicuo

- **Sugerencia** — propuesta de un contexto probabilístico, con modelo, evidencia y
  confianza (ver `spec-records-custodia.md` §2); la unidad que este contexto revisa.
- **Cola de revisión por confianza** — el conjunto de sugerencias pendientes de
  decisión, ordenadas de menor a mayor confianza (P-09), típicamente una por tipo
  (clasificación, agrupamiento, metadatos, límites de documento).
- **Candidato a aprobación masiva** — una sugerencia cuya confianza supera el
  umbral de la curva cobertura-error; elegible para decidirse en bloque sin
  revisión individual (`specs/eval/eval-clasificacion.md` §4.5).
- **Aprobación masiva** — acción explícita de un actor que decide sobre varios
  candidatos a la vez en una sola operación, registrando actor, fecha y las
  sugerencias referenciadas.
- **Decisión humana** — el acto explícito que materializa (o confirma) lo que una
  sugerencia propuso; el objeto que este contexto produce (ver
  `spec-records-custodia.md` §2).
- **Corrección** — una decisión humana cuyo resultado difiere de la sugerencia que
  la originó.
- **Flywheel de datos** — el ciclo por el cual las correcciones humanas
  retroalimentan el set patrón del arnés de evaluación, tras re-revisión porque
  están sesgadas hacia los casos difíciles (`specs/eval/edd-harness.md` §6, paso 5).
- **Actor** — el archivista u otro usuario humano autorizado que toma la decisión.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Cola de revisión** — tipo de sugerencia que agrupa, criterio de orden
  (confianza ascendente), sugerencias pendientes.
- **Decisión humana** — actor, fecha, sugerencia(s) referenciada(s), resultado
  (aceptación o corrección), destino (Records/Custodia o Normalización).
- **Corrección capturada** — una decisión humana marcada como distinta de su
  sugerencia de origen, candidata a re-revisión antes de convertirse en verdad de
  referencia del set patrón (`specs/eval/edd-harness.md` §6.4, `[CLARIFICAR]` ahí).

Este contexto no almacena el estado final de lo decidido: eso vive en
Records/Custodia (documento, expediente) o en Normalización (unidad documental
candidata). Validación Humana es la interfaz y el punto de origen de la decisión,
no su custodio final.

### Invariantes (no negociables)

1. Toda decisión humana, individual o en bloque, es atribuible a un actor y
   fechada; no existe materialización sin esos dos datos (P-08; RF-RC-004).
2. Una aprobación masiva referencia explícitamente cada sugerencia que aprueba; no
   es una acción genérica sin rastro de cuáles decidió.
3. Toda cola de revisión se ordena por confianza ascendente — la sugerencia de
   menor confianza siempre se presenta primero, nunca se oculta detrás de las de
   mayor confianza (P-09; `specs/eval/eval-clasificacion.md` §4.6).
4. Solo un actor con permiso sobre el recurso correspondiente —incluyendo su nivel
   de clasificación de la información— puede ver o decidir sobre él (RF-SA-004).
5. Toda corrección queda marcada como tal, distinguible de una simple aceptación,
   para poder alimentar el flywheel de datos sin mezclar ambos casos.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Records/Custodia | Documento + sugerencias pendientes de decisión (clasificación, agrupamiento, metadatos) |
| Normalización | Sugerencia de límites de documento pendiente de confirmación |
| Extracción | Extracciones de baja confianza pendientes de revisión |
| Seguridad y Acceso | Permisos del actor sobre el recurso solicitado |
| Actor autorizado | Decisión: aceptar, corregir, rechazar, o aprobar en bloque |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Records/Custodia | Decisión humana — materializa clasificación, metadatos o expediente (RF-RC-004) |
| Normalización | Confirmación o corrección de una sugerencia de límites (RF-NO-004) |
| Seguridad y Acceso | Solicitud de autorización previa a mostrar o decidir sobre un recurso |
| Arnés de evaluación | Correcciones capturadas, tras re-revisión, como candidatas a nueva verdad de referencia |
| Operador | Reporte de volumen y antigüedad de cada cola de revisión |

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-VH-001 · Agregación de sugerencias en colas de revisión**
El contexto reúne las sugerencias de todos los contextos probabilísticos en colas
de revisión organizadas por tipo.
- Dadas sugerencias pendientes de Clasificación, Enriquecimiento, Normalización y
  Extracción, Cuando se consultan las colas, Entonces cada sugerencia aparece en la
  cola correspondiente a su tipo.

**RF-VH-002 · Orden por confianza**
Cada cola de revisión se ordena de menor a mayor confianza.
- Dada una cola con varias sugerencias, Cuando se consulta, Entonces aparecen
  ordenadas de menor a mayor confianza.

**RF-VH-003 · Revisión y decisión individual**
Un actor autorizado puede aceptar, corregir o rechazar una sugerencia puntual; el
resultado se convierte en una decisión humana.
- Dada una sugerencia pendiente, Cuando un actor autorizado la acepta o la corrige,
  Entonces se produce una decisión humana con el actor, la fecha y el resultado.

**RF-VH-004 · Aprobación masiva de candidatos de alta confianza**
Un actor autorizado puede aprobar en bloque los candidatos que superan el umbral
de la curva cobertura-error, en una sola acción explícita.
- Dado un conjunto de candidatos a aprobación masiva, Cuando un actor autorizado
  los aprueba en bloque, Entonces se produce una decisión humana por cada uno,
  todas con el mismo actor y fecha, referenciando explícitamente cada sugerencia
  incluida.

**RF-VH-005 · Confirmación o corrección de límites de documento**
Un actor autorizado puede confirmar o corregir una sugerencia de límites de
documento pendiente de Normalización.
- Dada una sugerencia de límites pendiente, Cuando un actor autorizado la confirma
  o la corrige, Entonces Normalización recibe la confirmación con el actor y la
  fecha (RF-NO-004).

**RF-VH-006 · Registro atribuible de toda decisión**
Toda decisión, individual o masiva, queda registrada con actor y fecha, sin
excepción.
- Dada cualquier decisión humana producida por este contexto, Cuando se consulta,
  Entonces incluye el actor y la fecha que la produjeron.

**RF-VH-007 · Control de acceso por recurso**
Antes de mostrar una sugerencia o permitir decidir sobre ella, el contexto verifica
el permiso del actor sobre el recurso correspondiente.
- Dado un actor sin permiso sobre el recurso de una sugerencia, Cuando intenta
  verla o decidir sobre ella, Entonces el contexto lo deniega.

**RF-VH-008 · Distinción entre aceptación y corrección**
Una decisión que coincide exactamente con la sugerencia original se distingue de
una que la modifica.
- Dada una decisión humana, Cuando se consulta, Entonces se identifica como
  aceptación o como corrección respecto a la sugerencia que la originó.

**RF-VH-009 · Captura de correcciones para el flywheel de datos**
Las correcciones quedan disponibles para re-revisión y eventual incorporación como
verdad de referencia del set patrón del arnés, sin incorporarse en crudo.
- Dada una corrección registrada, Cuando se consulta para alimentar el set patrón,
  Entonces queda marcada como pendiente de re-revisión antes de aceptarse como
  verdad de referencia (`specs/eval/edd-harness.md` §6, paso 5).

**RF-VH-010 · Observabilidad de las colas**
El volumen y la antigüedad de cada cola de revisión son consultables en tiempo
real.
- Dada una cola de revisión, Cuando se consulta su estado, Entonces expone su
  volumen y la antigüedad de su sugerencia más antigua.

---

## 6. Requisitos no funcionales

**RNF-VH-001 · Usabilidad de la evidencia** — la evidencia de cada sugerencia se
presenta de forma que el actor pueda decidir sin releer el documento completo
(depende de RF-CL-008 y RF-EN-004: toda sugerencia ya porta evidencia trazable).

**RNF-VH-002 · Rendimiento de la cola a volumen** — las colas responden con fondos
de millones de sugerencias pendientes sin degradación inaceptable.

**RNF-VH-003 · Paridad de despliegue** — toda la funcionalidad opera idéntica en
SaaS y on-premise, incluyendo entornos sin conectividad saliente (P-02, P-10).

**RNF-VH-004 · Trazabilidad de la aprobación masiva** — cada sugerencia incluida en
una aprobación masiva sigue siendo identificable individualmente después del
hecho; el bloque nunca oculta cuáles la componían.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-VH-001 | Constitución del proyecto P-09 | N/A | ☐ |
| RF-VH-002 | Constitución del proyecto P-09 (`eval-clasificacion.md` §4.6) | N/A | ☐ |
| RF-VH-003 | Requisitos funcionales de SGDEA (AGN); Constitución del proyecto P-01, P-09; RF-RC-004 | PENDIENTE | ☐ |
| RF-VH-004 | Requisitos funcionales de SGDEA (AGN); Constitución del proyecto P-09 (`eval-clasificacion.md` §4.5) | PENDIENTE | ☐ |
| RF-VH-005 | Constitución del proyecto P-01; RF-NO-004 | N/A | ☐ |
| RF-VH-006 | Constitución del proyecto P-08 | N/A | ☐ |
| RF-VH-007 | Constitución del proyecto — integridad (El objeto a proteger); RF-SA-004 | N/A | ☐ |
| RF-VH-008 | Constitución del proyecto P-09 | N/A | ☐ |
| RF-VH-009 | Constitución del proyecto P-05, P-09 (`edd-harness.md` §6) | N/A | ☐ |
| RF-VH-010 | Constitución del proyecto P-09 | N/A | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Mecanismo exacto de re-revisión de correcciones antes de
  incorporarlas al set patrón — ya marcado `[CLARIFICAR]` en
  `specs/eval/edd-harness.md` §6.4/§9; esta spec hereda esa misma pendiente desde
  el lado de captura, no la resuelve.
- **[CLARIFICAR]** Umbral de la curva cobertura-error que habilita la aprobación
  masiva (RF-VH-004) — depende de la calibración de la Etapa 1
  (`specs/eval/eval-clasificacion.md` §6, ya `[CLARIFICAR]` ahí).
- **[CLARIFICAR]** Si existe un mecanismo de aprobación masiva análogo para
  sugerencias de metadatos, o si esas siempre exigen revisión campo por campo
  (misma pregunta que dejó abierta `specs/004-enriquecimiento/spec.md` §8 desde el
  lado de Enriquecimiento).
- **[CLARIFICAR]** Interfaz o herramienta concreta de revisión (web, aplicación de
  escritorio, etc.) — decisión de implementación de una etapa posterior; no
  bloqueante para esta spec de dominio.
