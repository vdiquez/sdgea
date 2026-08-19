# Spec · Bounded Context: Records / Custodia

| Campo | Valor |
|-------|-------|
| Código de contexto | `RC` |
| Tipo | Determinístico — gobernado por SDD |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-04, P-08, P-10 |

---

## 1. Propósito y frontera del contexto

Records/Custodia es el **núcleo determinístico** del producto: custodia el original
inmutable de cada documento, mantiene el estado del ciclo de vida, conforma el
expediente electrónico, calcula la retención contra la TRD y registra la bitácora de
auditoría. Es el contexto donde viven las cuatro propiedades del documento de
archivo (autenticidad, fiabilidad, integridad, disponibilidad).

Es también el contexto que más debe sobrevivir al tiempo: es la semilla del SGDEA
completo. Por eso se especifica con rigor desde la Etapa 0 aunque se implemente de
forma mínima.

**Dentro de la frontera:** custodia del original, modelo del documento y del
expediente, TRD versionada, cálculo de retención, bitácora de auditoría, recepción
de sugerencias como propuestas, materialización por decisión humana.

**Fuera de la frontera (de este contexto):** OCR y extracción (contexto Extracción),
clasificación automática (contexto Clasificación), indexación y búsqueda (contexto
Indexación y Búsqueda), interfaz de validación (contexto Validación Humana).

**Fuera de alcance de esta spec / Etapa 0:** firma electrónica y estampado
cronológico (integración futura), ejecución de disposición final y transferencias
(Etapa 5).

---

## 2. Lenguaje ubicuo

- **Documento de archivo** — unidad documental con valor de evidencia bajo custodia
  del sistema.
- **Original inmutable** — los bytes de origen del documento, almacenados en modo
  de una sola escritura, con huella criptográfica.
- **Expediente electrónico** — agrupación ordenada de documentos de archivo
  relativos a un mismo asunto o trámite; mantiene índice electrónico, foliado y
  hoja de control.
- **TRD** — Tabla de Retención Documental: estructura de series y subseries con sus
  tiempos de retención y disposición final. Se gestiona como objeto versionado.
- **Serie / Subserie** — nodos de clasificación de la TRD a los que se asigna un
  documento o expediente.
- **Sugerencia** — propuesta emitida por un contexto probabilístico
  (clasificación, metadato, agrupamiento); porta modelo, evidencia y confianza.
  **No es estado.**
- **Decisión humana** — acto explícito de un usuario que acepta, corrige o rechaza
  una o más sugerencias, o que actúa de forma manual; es lo único que transiciona
  el estado de un documento.
- **Evento de auditoría** — registro inmutable de una transición de estado.
- **Política de retención** — regla derivada de aplicar una versión de la TRD a un
  expediente; produce fechas de retención y disposición.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Documento de archivo** (agregado raíz) — referencia a su original inmutable,
  metadatos, clasificación (serie/subserie + versión de TRD), estado de ciclo de
  vida, procedencia, foliación dentro del expediente.
- **Expediente electrónico** (agregado raíz) — conjunto ordenado de documentos,
  índice electrónico, foliado, hoja de control, estado.
- **Original inmutable** — bytes + algoritmo y valor de huella + fecha de custodia.
- **TRD** — versión, vigencia, árbol de series/subseries, reglas de retención.
- **Sugerencia** — tipo, contenido propuesto, identificador de modelo, referencias
  de evidencia, confianza, fecha; vinculada a un documento o expediente.
- **Decisión humana** — actor, fecha, sugerencias referenciadas, resultado.
- **Evento de auditoría** — actor, fecha, tipo, estado anterior, estado posterior.

### Invariantes (no negociables)

1. El **original inmutable** se escribe una sola vez. Nunca se modifica. Su
   integridad es verificable en todo momento contra su huella.
2. Ninguna **sugerencia** modifica la clasificación, los metadatos ni el estado de
   un documento o expediente. Solo una **decisión humana** lo hace.
3. Toda transición de estado genera un **evento de auditoría** inmutable y
   atribuible. No existe transición sin evento.
4. La **retención** se calcula de forma determinística y reproducible a partir de
   una versión específica de la TRD. La TRD es versionada para que toda decisión
   histórica siga siendo reproducible.
5. El **foliado** y el **índice electrónico** de un expediente son derivados y
   siempre consistentes con su conjunto de documentos.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje | Tratamiento |
|--------|---------|-------------|
| Captura/Ingesta | Solicitud de custodia de un original | Crea Documento + Original inmutable |
| Clasificación | Sugerencia de serie/subserie | Se almacena como Sugerencia. **No** cambia estado |
| Enriquecimiento | Sugerencia de metadatos | Se almacena como Sugerencia. **No** cambia estado |
| Validación Humana | Decisión humana | Materializa clasificación / metadatos / estado |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Indexación y Búsqueda | Estado materializado del documento (para indexar) |
| Validación Humana | Documento + sugerencias pendientes de decisión |
| Seguridad y Acceso | Eventos de auditoría |
| Cualquier consumidor autorizado | Original inmutable + estado de retención |

### Capa anticorrupción

Toda entrada proveniente de un contexto probabilístico (Clasificación,
Enriquecimiento) cruza una capa de traducción que la convierte en `Sugerencia`. La
capa anticorrupción **garantiza** que un dato probabilístico no pueda expresarse
como estado del documento. Es la materialización del principio P-01.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-RC-001 · Custodia del original inmutable**
Cuando Captura/Ingesta deposita un artefacto, el contexto almacena sus bytes en modo
de una sola escritura y registra su huella criptográfica.
- Dado un original depositado, Cuando se consulta, Entonces sus bytes son idénticos
  a los depositados y su huella coincide.
- Dado un intento de modificar un original, Cuando se ejecuta, Entonces se rechaza y
  se genera un evento de auditoría.

**RF-RC-002 · Registro de procedencia**
Todo documento de archivo conserva su procedencia: fuente, fecha de ingesta e
identificador del lote o flujo de origen.
- Dado un documento, Cuando se consulta su procedencia, Entonces incluye fuente,
  fecha y lote/flujo.

**RF-RC-003 · Recepción de sugerencias como propuestas**
Las sugerencias de clasificación y metadatos se almacenan vinculadas a un documento,
portando modelo, evidencia y confianza, sin alterar su estado.
- Dado un documento sin decisión humana, Cuando se recibe una sugerencia, Entonces
  su clasificación y estado permanecen sin cambio.
- Dada una sugerencia almacenada, Cuando se consulta, Entonces expone modelo,
  evidencia y confianza.

**RF-RC-004 · Materialización por decisión humana**
La clasificación, los metadatos y el estado de ciclo de vida de un documento cambian
únicamente mediante una decisión humana, que registra al actor.
- Dada una decisión humana sobre un documento, Cuando se aplica, Entonces el cambio
  de estado queda registrado con el actor y la fecha.
- Dado cualquier cambio de clasificación o estado sin decisión humana asociada,
  Entonces el sistema lo impide.

**RF-RC-005 · Bitácora de auditoría inmutable**
Toda transición de estado genera un evento de auditoría de solo anexado, atribuible,
fechado y con estado anterior y posterior.
- Dada una transición de estado, Cuando ocurre, Entonces existe un evento con actor,
  fecha, tipo, estado anterior y posterior.
- Dado un evento de auditoría existente, Cuando se intenta modificar o borrar,
  Entonces la operación se rechaza.

**RF-RC-006 · TRD como objeto versionado**
La TRD/CCD se almacena como objeto versionado; series, subseries, tiempos de
retención y disposición final son consultables por versión.
- Dada una clasificación de documento, Cuando se consulta, Entonces referencia una
  versión específica de la TRD.
- Dada una nueva versión de la TRD, Cuando se publica, Entonces las clasificaciones
  previas conservan su referencia a la versión anterior.

**RF-RC-007 · Cálculo determinístico de retención**
Dado un documento o expediente clasificado contra una versión de la TRD, las fechas
de retención y disposición se calculan de forma determinística y reproducible.
- Dado el mismo documento y la misma versión de TRD, Cuando se recalcula la
  retención, Entonces el resultado es idéntico.

**RF-RC-008 · Conformación del expediente electrónico**
Los documentos se agrupan en expedientes; el expediente mantiene índice electrónico,
foliado y hoja de control derivados y consistentes.
- Dado un documento agregado a un expediente, Cuando se consulta el expediente,
  Entonces el índice y el foliado reflejan el documento.

**RF-RC-009 · Verificación de integridad**
El contexto puede verificar, por demanda y de forma programada, que los originales
almacenados coinciden con sus huellas registradas, y reportar discrepancias.
- Dada una verificación de integridad, Cuando un original no coincide con su huella,
  Entonces se reporta como discrepancia y se genera un evento de auditoría.

**RF-RC-010 · Recuperación del original**
Un consumidor autorizado puede recuperar los bytes del original; cada recuperación
genera un evento de auditoría de acceso.
- Dada una recuperación autorizada, Cuando se completa, Entonces existe un evento de
  acceso con actor y fecha.

---

## 6. Requisitos no funcionales

**RNF-RC-001 · Integridad criptográfica** — la huella de cada original usa un
algoritmo criptográficamente robusto y verificable de forma independiente.

**RNF-RC-002 · Bitácora a prueba de manipulación** — el almacenamiento de eventos de
auditoría es de solo anexado y permite detectar manipulación.

**RNF-RC-003 · Paridad de despliegue** — todo lo anterior funciona idéntico en SaaS
y en appliance on-premise, incluyendo entornos sin conectividad saliente (P-02, P-10).

**RNF-RC-004 · Reproducibilidad** — el cálculo de retención es reproducible años
después, lo que exige conservar las versiones de TRD usadas.

**RNF-RC-005 · Recuperación a volumen** — la recuperación de originales y estados
mantiene un desempeño aceptable con fondos de millones de documentos.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-RC-001 | Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015) (documento electrónico de archivo); ISO 15489 | PENDIENTE | ☐ |
| RF-RC-002 | Requisitos funcionales de SGDEA (AGN); ISO 16175 | PENDIENTE | ☐ |
| RF-RC-003 | Constitución del proyecto P-01 (control de diseño) | N/A | ☐ |
| RF-RC-004 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-RC-005 | Requisitos funcionales de SGDEA (AGN); ISO 15489 | PENDIENTE | ☐ |
| RF-RC-006 | Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019); Decreto 1080 de 2015 | PENDIENTE | ☐ |
| RF-RC-007 | Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019); Ley 594 de 2000 | PENDIENTE | ☐ |
| RF-RC-008 | Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015) (expediente electrónico) | PENDIENTE | ☐ |
| RF-RC-009 | Requisitos funcionales de SGDEA (AGN); ISO 15489 | PENDIENTE | ☐ |
| RF-RC-010 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Algoritmo de huella y si la bitácora de auditoría usa además una
  cadena de huellas encadenadas para prueba de manipulación.
- **[CLARIFICAR]** Esquema exacto de metadatos obligatorios — depende de la TRD del
  design partner y de los requisitos del AGN; se fija con dato real en la Etapa 0.
- **[CLARIFICAR]** Modelo de estados del ciclo de vida del documento y del
  expediente: enumerar las transiciones válidas.
- **[CLARIFICAR]** Integración futura de firma electrónica y estampado cronológico:
  identificar el punto de extensión sin implementarlo aún.
- **[CLARIFICAR]** Política de versionado de la TRD: cómo se importa y se publica una
  nueva versión sin afectar clasificaciones vigentes.
