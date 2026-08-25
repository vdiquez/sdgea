# Spec · Bounded Context: Normalización

| Campo | Valor |
|-------|-------|
| Código de contexto | `NO` |
| Tipo | Híbrido — núcleo determinístico (SDD) con un componente probabilístico bajo EDD (detección de límites de documento, ver `specs/eval/edd-harness.md` §2) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-05, P-06, P-07, P-08 |

---

## 1. Propósito y frontera del contexto

Normalización recibe los ítems de ingesta ya validados por Captura/Ingesta y los
convierte en **unidades documentales candidatas**: separa un artefacto de origen que
puede contener varios documentos (una caja escaneada como un único PDF, típico de
fondos acumulados), normaliza cada unidad a un formato de preservación y elimina
duplicados de contenido antes de entregarlas a Extracción.

No interpreta el contenido más allá de encontrar dónde empieza y termina cada
documento: no hace OCR, no clasifica, no extrae metadatos.

**Dentro de la frontera:** recepción de ítems validados desde Captura/Ingesta,
detección de límites de documento (con su componente probabilístico y la
confirmación humana que lo cierra), normalización a formato de preservación,
deduplicación de contenido a nivel de documento, propagación de procedencia,
entrega a Extracción.

**Fuera de la frontera (de este contexto):** OCR y extracción de texto (contexto
Extracción); clasificación automática (contexto Clasificación); custodia del
original inmutable (contexto Records/Custodia) — Normalización opera sobre una copia
de trabajo del artefacto, nunca sobre el original bajo custodia.

> Nota de alcance (heredada de `spec-captura-ingesta.md` §1): en fondos acumulados,
> un único artefacto de origen suele contener una caja entera con muchos documentos.
> Este es exactamente el problema que resuelve la detección de límites de este
> contexto. En flujo activo (día a día), lo habitual es que un artefacto ya
> corresponda a un único documento — el caso trivial de RF-NO-003 — y la detección
> probabilística no entra en juego.

**Por qué es un contexto híbrido:** `specs/eval/edd-harness.md` §2 clasifica la
"detección de límites de documento" como un componente **probabilístico** que vive en
Normalización, gobernado por EDD (P-05), igual que la clasificación vive en el
contexto Clasificación. El resto del contexto (recepción, normalización de formato,
deduplicación, propagación de procedencia, entrega) es determinístico y se especifica
bajo SDD (P-06), igual que Captura/Ingesta. La frontera física de P-01 entre lo
probabilístico y lo determinístico no es exclusiva de Records/Custodia: aplica aquí
también, entre la sugerencia de límites y la unidad documental candidata confirmada.

---

## 2. Lenguaje ubicuo

- **Ítem de ingesta** — la unidad que entrega Captura/Ingesta (ver
  `spec-captura-ingesta.md` §2); la entrada de este contexto.
- **Unidad documental candidata** — el resultado de aplicar límites de documento
  sobre un ítem de ingesta. Un ítem de ingesta produce una o varias.
- **Límites de documento** — los puntos de separación que dividen un artefacto de
  origen en unidades documentales candidatas.
- **Sugerencia de límites** — propuesta probabilística de dónde están los límites de
  documento, con confianza; no separa documentos por sí sola (P-01).
- **Confirmación humana de límites** — acto explícito de un usuario que acepta o
  corrige una sugerencia de límites, o que traza los límites manualmente; es lo único
  que materializa unidades documentales candidatas definitivas cuando el caso no es
  trivial.
- **Caso trivial** — un artefacto de origen que la fuente declara, o que el sistema
  puede establecer sin ambigüedad, como un único documento; no requiere sugerencia ni
  confirmación.
- **Formato de preservación** — el formato canónico de destino al que se normaliza
  cada unidad documental candidata.
- **Duplicado de contenido** — dos unidades documentales candidatas cuyo contenido
  normalizado es equivalente; la segunda se vincula a la primera en vez de entregarse
  por separado.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Unidad documental candidata** (agregado raíz) — referencia al ítem de ingesta de
  origen, posición dentro del artefacto, sugerencia de límites (si aplica),
  confirmación humana (si aplica), formato original, forma normalizada, estado,
  procedencia heredada.
- **Sugerencia de límites** — modelo, evidencia (p. ej. los puntos de corte
  propuestos), confianza, fecha; vinculada al ítem de ingesta de origen.
- **Confirmación humana de límites** — actor, fecha, límites confirmados (idénticos,
  ajustados o re-trazados respecto a la sugerencia).

### Estados de la unidad documental candidata

`Pendiente de límites` → `Límites confirmados` → `Normalizada` →
`Entregada a Extracción`
Ramas terminales alternativas: `Rechazada` (formato no soportado tras normalizar),
`En cuarentena` (corrupta o ilegible tras la separación) y `Vinculada a duplicado`
(su contenido normalizado ya fue entregado por otra unidad). Toda unidad alcanza uno
de estos cuatro estados terminales.

### Invariantes (no negociables)

1. Toda unidad documental candidata se rastrea hasta su ítem de ingesta de origen y,
   transitivamente, hasta su procedencia completa (RF-CI-007).
2. Ninguna **sugerencia de límites** se convierte en unidades documentales candidatas
   definitivas sin una **confirmación humana** explícita, salvo el caso trivial de
   RF-NO-003, donde no existe ambigüedad de límites que resolver.
3. Toda unidad documental candidata alcanza un estado terminal contabilizado: no hay
   pérdida silenciosa a nivel de documento (P-08), igual que RF-CI-008 la garantiza a
   nivel de artefacto.
4. La normalización de formato preserva el contenido informativo del artefacto
   original; el artefacto de origen sin normalizar permanece disponible a través de
   Records/Custodia — Normalización nunca sustituye ni descarta el original.
5. Dos unidades documentales candidatas reconocidas como duplicado de contenido no
   generan documentos independientes aguas abajo.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Captura/Ingesta | Ítem de ingesta validado, con su artefacto de origen y procedencia (RF-CI-010) |
| Validación Humana | Confirmación o corrección de una sugerencia de límites pendiente |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Validación Humana | Sugerencia de límites de documento pendiente de confirmación (caso no trivial) |
| Extracción | Unidad documental candidata normalizada, con su procedencia |
| Records/Custodia | Procedencia extendida de cada unidad documental candidata |
| Seguridad y Acceso | Eventos de auditoría de normalización |
| Operador | Reporte de unidades en cuarentena o rechazadas |

### Capa anticorrupción

Toda **sugerencia de límites** cruza una capa de traducción antes de convertirse en
una unidad documental candidata con `Límites confirmados`; ninguna sugerencia por sí
sola materializa una separación definitiva de documentos (P-01). Esta es una segunda
instancia de la misma frontera física que `spec-records-custodia.md` §4 describe
entre Clasificación/Enriquecimiento y el núcleo de records — aquí se aplica un paso
antes en el pipeline, entre la detección de límites y la existencia misma de un
documento candidato.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-NO-001 · Recepción de ítems validados desde Captura/Ingesta**
Todo ítem de ingesta que Captura/Ingesta entrega (RF-CI-010) genera al menos una
unidad documental candidata, conservando su artefacto de origen y procedencia.
- Dado un ítem de ingesta `Entregado`, Cuando Normalización lo recibe, Entonces
  existe al menos una unidad documental candidata vinculada a ese ítem, en estado
  `Pendiente de límites`.

**RF-NO-002 · Detección probabilística de límites de documento**
Cuando un artefacto de origen no es un caso trivial (RF-NO-003), el contexto invoca
el componente probabilístico de detección de límites (gobernado por EDD, ver
`specs/eval/edd-harness.md` §2), que produce una sugerencia de límites con confianza
sin decidir por sí sola la separación definitiva.
- Dado un artefacto de origen no declarado como de un único documento, Cuando se
  detectan sus límites, Entonces se genera una sugerencia de límites con los puntos
  de corte propuestos y una confianza, y las unidades resultantes permanecen en
  `Pendiente de límites` hasta su confirmación.

**RF-NO-003 · Caso trivial de un único documento**
Cuando la fuente declara, o el sistema puede establecer sin ambigüedad, que un
artefacto de origen corresponde a un único documento, la unidad documental candidata
pasa directamente a `Límites confirmados` sin pasar por el componente probabilístico
ni por confirmación humana.
- Dado un artefacto declarado de un único documento, Cuando se procesa, Entonces su
  única unidad documental candidata queda en `Límites confirmados` sin generar una
  sugerencia de límites.

**RF-NO-004 · Confirmación humana de límites de documento**
Una sugerencia de límites nunca separa documentos de forma definitiva por sí sola;
solo una confirmación humana explícita —idéntica, ajustada o re-trazada respecto a la
sugerencia— materializa las unidades documentales candidatas en `Límites confirmados`.
- Dada una sugerencia de límites pendiente, Cuando un humano la confirma o la
  corrige, Entonces las unidades documentales candidatas resultantes quedan en
  `Límites confirmados` con el actor y la fecha registrados.
- Dada una sugerencia de límites sin confirmación humana, Entonces ninguna unidad
  documental candidata alcanza `Límites confirmados`.

**RF-NO-005 · Normalización a formato de preservación**
Toda unidad documental candidata con límites confirmados se convierte a su formato de
preservación de destino, conservando intacto el artefacto original.
- Dada una unidad documental candidata en `Límites confirmados`, Cuando se
  normaliza, Entonces queda en estado `Normalizada` con una referencia a su forma
  normalizada y a su artefacto original sin modificar.

**RF-NO-006 · Deduplicación de contenido a nivel de documento**
Dos unidades documentales candidatas cuyo contenido normalizado es equivalente se
reconocen como duplicado; la segunda se vincula a la primera en vez de entregarse por
separado.
- Dada una unidad documental candidata normalizada cuyo contenido coincide con el de
  una ya entregada, Cuando se detecta, Entonces queda `Vinculada a duplicado` en vez
  de `Entregada a Extracción`.

**RF-NO-007 · Propagación de procedencia**
Cada unidad documental candidata hereda y extiende la procedencia completa de su
ítem de ingesta de origen, añadiendo su propia posición dentro del artefacto.
- Dada una unidad documental candidata, Cuando se consulta su procedencia, Entonces
  incluye la procedencia completa del ítem de ingesta de origen y su posición dentro
  del artefacto.

**RF-NO-008 · Cero pérdida silenciosa**
Toda unidad documental candidata alcanza un estado terminal contabilizado.
- Dado un conjunto de unidades documentales candidatas procesadas, Cuando se suma
  por estado, Entonces la cuenta de `Entregada a Extracción` + `Rechazada` +
  `En cuarentena` + `Vinculada a duplicado` iguala el total de unidades creadas.

**RF-NO-009 · Validación y cuarentena de unidades candidatas**
Una unidad documental candidata corrupta, ilegible o de formato no soportado tras la
separación se pone en cuarentena o se rechaza con una razón explícita, con el mismo
criterio que RF-CI-006: recuperable dentro del sistema actual → `En cuarentena`; solo
recuperable con un artefacto nuevo o un cambio de sistema → `Rechazada`.
- Dada una unidad documental candidata corrupta o ilegible tras la separación,
  Cuando se detecta, Entonces queda `En cuarentena` con razón registrada.
- Dada una unidad documental candidata de formato no soportado tras la
  normalización, Cuando se detecta, Entonces queda `Rechazada` con razón registrada.

**RF-NO-010 · Entrega a Extracción**
El contexto entrega las unidades documentales candidatas normalizadas a Extracción
mediante un contrato estable; no realiza OCR ni clasificación.
- Dada una unidad documental candidata `Normalizada` y no duplicada, Cuando se
  entrega, Entonces Extracción la recibe con su forma normalizada y su procedencia, y
  la unidad pasa a `Entregada a Extracción`.

---

## 6. Requisitos no funcionales

**RNF-NO-001 · Rendimiento a volumen** — la detección de límites y la normalización
sostienen fondos acumulados de millones de artefactos, varios documentos por
artefacto, sin degradación inaceptable.

**RNF-NO-002 · Paridad de despliegue** — toda la funcionalidad opera idéntica en
SaaS y en appliance on-premise, incluyendo entornos sin conectividad saliente (P-02,
P-10) — el componente probabilístico de límites no depende de un servicio externo
que no tenga contraparte autoalojada (P-03).

**RNF-NO-003 · Fidelidad de la normalización** — la conversión a formato de
preservación no pierde contenido informativo verificable frente al artefacto
original.

**RNF-NO-004 · Observabilidad de la cola de confirmación** — el volumen y la
antigüedad de las sugerencias de límites pendientes de confirmación humana son
consultables en tiempo real (alimenta P-09).

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A` (no hay nada externo que fijar).

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-NO-001 | Requisitos funcionales de SGDEA (AGN); guía de organización de fondos acumulados | PENDIENTE | ☐ |
| RF-NO-002 | Constitución del proyecto P-01, P-05 (frontera probabilístico/determinístico) | N/A | ☐ |
| RF-NO-003 | Constitución del proyecto P-06 (alcance determinístico) | N/A | ☐ |
| RF-NO-004 | Constitución del proyecto P-01, P-09 (decisión humana explícita) | N/A | ☐ |
| RF-NO-005 | Requisitos funcionales de SGDEA (AGN); ISO 16175 | PENDIENTE | ☐ |
| RF-NO-006 | Requisitos funcionales de SGDEA (AGN); ISO 16175 | PENDIENTE | ☐ |
| RF-NO-007 | Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015); ISO 16175 (metadatos de procedencia) | PENDIENTE | ☐ |
| RF-NO-008 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |
| RF-NO-009 | Requisitos funcionales de SGDEA (AGN) — captura | PENDIENTE | ☐ |
| RF-NO-010 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Formato(s) de preservación exacto(s) de destino — depende de las
  herramientas de conversión y del estándar de preservación digital que se adopte;
  no se fija un estándar concreto sin decisión explícita.
- **[CLARIFICAR]** Relación temporal exacta con la custodia del original en
  Records/Custodia: si el original se custodia antes, después o en paralelo a la
  normalización, y qué copia de trabajo usa este contexto mientras tanto.
- **[CLARIFICAR]** Mecanismo o umbral exacto para decidir cuándo un artefacto es
  "caso trivial" (RF-NO-003) frente a cuándo requiere detección probabilística de
  límites — depende de metadatos de la fuente (un flujo activo puede declarar 1
  documento por evento; un fondo acumulado no).
- **[CLARIFICAR]** Definición exacta de "duplicado de contenido" a nivel de
  documento (RF-NO-006) frente a la idempotencia de artefacto ya resuelta en
  Captura/Ingesta (RF-CI-005): duplicado exacto tras normalizar, o duplicado
  casi-exacto (near-duplicate).
