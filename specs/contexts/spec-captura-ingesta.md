# Spec · Bounded Context: Captura / Ingesta

| Campo | Valor |
|-------|-------|
| Código de contexto | `CI` |
| Tipo | Determinístico — gobernado por SDD |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-02, P-03, P-07, P-08 |

---

## 1. Propósito y frontera del contexto

Captura/Ingesta es la **puerta de entrada** del pipeline. Recibe artefactos de
origen de los dos casos de uso del producto, los valida, registra su procedencia y
los entrega al contexto de Normalización. No interpreta el contenido: no hace OCR,
no clasifica, no detecta límites de documento.

Su responsabilidad central es una garantía simple y dura: **ningún artículo
recibido se pierde de forma silenciosa**. Todo lo que entra termina en un estado
terminal contabilizado.

**Dentro de la frontera:** ingesta por lote (fondos acumulados), ingesta por flujo
de eventos (flujo activo), configuración de fuentes, validación y cuarentena,
idempotencia, conciliación contra inventario, registro de procedencia, entrega a
Normalización.

**Fuera de la frontera (de este contexto):** normalización, deduplicación de
contenido y **detección de límites de documento** (contexto Normalización); OCR y
extracción (contexto Extracción); custodia del original (contexto Records/Custodia).

> Nota de alcance: en fondos acumulados, un único archivo de origen (p. ej. un PDF)
> suele contener una caja entera con muchos documentos y expedientes. Captura/Ingesta
> **no asume** que un artefacto de origen equivale a un documento; trata el artefacto
> como una unidad opaca y delega su separación a Normalización.

---

## 2. Lenguaje ubicuo

- **Artefacto de origen** — el fichero o mensaje tal como llega, sin interpretar.
- **Ítem de ingesta** — la unidad que el contexto rastrea: un artefacto de origen
  más su procedencia y su estado.
- **Lote de ingesta** — conjunto de artefactos de un fondo acumulado, normalmente
  acompañado de un inventario de origen.
- **Flujo de ingesta** — ingesta continua dirigida por eventos para el flujo activo.
- **Inventario de origen** — el listado (FUID u hoja de cálculo) que acompaña a un
  fondo acumulado y describe lo que el lote debería contener.
- **Fuente** — conector configurable de origen (carpeta observada, buzón de correo,
  escáner, API de un SGDEA incumbente) con sus parámetros y credenciales.
- **Conciliación** — comparación entre lo declarado en el inventario y lo realmente
  recibido.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Lote de ingesta** (agregado raíz) — fuente, inventario de origen asociado,
  conjunto de ítems, estado de avance, marcas de pausa/reanudación.
- **Flujo de ingesta** (agregado raíz) — fuente, estado de la suscripción a eventos.
- **Ítem de ingesta** (entidad) — referencia al artefacto de origen, huella de
  contenido, procedencia, estado.
- **Inventario de origen** — registros declarados, formato de origen.
- **Fuente** (entidad de configuración) — tipo, parámetros, credenciales.

### Estados del ítem de ingesta

`Recibido` → `Validado` → `En cola` → `Entregado a Normalización`
Ramas terminales alternativas: `Rechazado` (no soportado / corrupto) y
`En cuarentena` (requiere intervención). Todo ítem alcanza un estado terminal:
`Entregado`, `Rechazado` o `En cuarentena`.

### Invariantes (no negociables)

1. Todo ítem recibido alcanza un estado terminal contabilizado. No hay pérdida
   silenciosa (P-08).
2. Reingestar un artefacto de contenido idéntico no crea un ítem duplicado: se
   reconoce y se vincula al existente (idempotencia).
3. Todo ítem conserva su procedencia completa: fuente, fecha, disparador y
   lote/flujo de origen.
4. La conciliación de un lote siempre cuadra: cada registro del inventario y cada
   ítem recibido quedan explicados.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Operador (fondos acumulados) | Carga de un lote: artefactos de origen + inventario |
| Fuente configurada (flujo activo) | Evento de disponibilidad de un nuevo artefacto |
| Administrador | Alta / configuración de una Fuente |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Normalización | Ítem de ingesta validado, con su artefacto de origen y procedencia |
| Records/Custodia | Procedencia del ítem (para el registro del documento) |
| Seguridad y Acceso | Eventos de auditoría de ingesta |
| Operador | Reporte de conciliación del lote; ítems en cuarentena |

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-CI-001 · Ingesta por lote para fondos acumulados**
El contexto acepta la carga de un lote de artefactos de origen de calidad mixta
junto con un inventario de origen.
- Dado un lote con artefactos e inventario, Cuando se carga, Entonces cada artefacto
  produce un ítem de ingesta en estado `Recibido`.

**RF-CI-002 · Conciliación contra inventario**
Para un lote, el contexto concilia los ítems recibidos contra el inventario de
origen y reporta faltantes y sobrantes.
- Dado un lote conciliado, Cuando se consulta el reporte, Entonces lista los
  registros del inventario sin ítem recibido y los ítems sin registro en inventario.

**RF-CI-003 · Ingesta por flujo de eventos para flujo activo**
El contexto ingiere artefactos de forma continua desde fuentes dirigidas por
eventos: carpetas observadas, correo, escáner o API de un SGDEA incumbente.
- Dado un nuevo artefacto disponible en una fuente, Cuando se dispara el evento,
  Entonces se crea un ítem de ingesta sin intervención manual.

**RF-CI-004 · Configuración de fuentes**
Una Fuente es un conector configurable; se da de alta y se parametriza sin cambios
de código.
- Dada una nueva Fuente, Cuando se configura, Entonces queda disponible para ingesta
  sin desplegar código nuevo.

**RF-CI-005 · Idempotencia por contenido**
Reingestar un artefacto de contenido idéntico no genera un ítem duplicado.
- Dado un artefacto ya ingerido, Cuando se reingesta el mismo contenido, Entonces se
  reconoce como duplicado y se vincula al ítem existente.

**RF-CI-006 · Validación y cuarentena**
Los artefactos corruptos, ilegibles o de formato no soportado se ponen en cuarentena
o se rechazan con una razón explícita; nunca se descartan en silencio. La rama
terminal depende de si el mismo artefacto es recuperable dentro del sistema
actual (`En cuarentena`, requiere intervención humana) o si la única salida es
un artefacto distinto o un cambio de sistema (`Rechazado`, terminal):
- Dado un artefacto corrupto, Cuando se valida, Entonces el ítem queda `En
  cuarentena` con razón registrada (recuperable mediante reescaneo o
  confirmación manual).
- Dado un artefacto ilegible, Cuando se valida, Entonces el ítem queda `En
  cuarentena` con razón registrada (recuperable mediante juicio de calidad
  humano).
- Dado un artefacto de formato no soportado, Cuando se valida, Entonces el
  ítem queda `Rechazado` con razón registrada (no recuperable sin un
  artefacto nuevo o soporte de formato añadido al sistema).

**RF-CI-007 · Registro de procedencia**
Cada ítem registra fuente, fecha, disparador e identificador de lote o flujo, y los
entrega a Records/Custodia.
- Dado un ítem de ingesta, Cuando se consulta, Entonces expone su procedencia
  completa.

**RF-CI-008 · Cero pérdida silenciosa**
Todo ítem recibido alcanza un estado terminal contabilizado.
- Dado un lote procesado, Cuando se suma por estado, Entonces la cuenta de
  `Entregado` + `Rechazado` + `En cuarentena` iguala el total de ítems recibidos.

**RF-CI-009 · Reanudabilidad**
La ingesta de un lote puede pausarse, reanudarse y reintentarse sin reprocesar los
ítems ya completados.
- Dado un lote pausado, Cuando se reanuda, Entonces solo se procesan los ítems aún
  no terminales.

**RF-CI-010 · Entrega a Normalización**
El contexto entrega los ítems validados a Normalización mediante un contrato
estable; no realiza OCR ni clasificación ni separación de documentos.
- Dado un ítem `Validado`, Cuando se entrega, Entonces Normalización lo recibe con
  su artefacto de origen y procedencia, y el ítem pasa a `Entregado`.

---

## 6. Requisitos no funcionales

**RNF-CI-001 · Rendimiento a volumen** — la ingesta sostiene fondos acumulados de
millones de artefactos sin degradación inaceptable.

**RNF-CI-002 · Reanudabilidad y resistencia a fallos** — una interrupción no obliga
a reiniciar un lote desde cero (soporta RF-CI-009).

**RNF-CI-003 · Contrapresión** — ante saturación de los contextos posteriores, la
ingesta regula su ritmo sin perder ítems.

**RNF-CI-004 · Observabilidad** — el avance de un lote (recibidos, en cola,
entregados, en cuarentena) es consultable en tiempo real.

**RNF-CI-005 · Paridad de despliegue** — toda la funcionalidad opera idéntica en
SaaS y en appliance on-premise, incluyendo entornos sin conectividad saliente.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-CI-001 | Requisitos funcionales de SGDEA (AGN); guía de organización de fondos acumulados | PENDIENTE | ☐ |
| RF-CI-002 | Guía de organización de fondos acumulados; uso del FUID | PENDIENTE | ☐ |
| RF-CI-003 | Requisitos funcionales de SGDEA (AGN) — captura | PENDIENTE | ☐ |
| RF-CI-005 | Requisitos funcionales de SGDEA (AGN); ISO 16175 | PENDIENTE | ☐ |
| RF-CI-006 | Requisitos funcionales de SGDEA (AGN) — captura | PENDIENTE | ☐ |
| RF-CI-007 | Acuerdo 003 de 2015; ISO 16175 (metadatos de procedencia) | PENDIENTE | ☐ |
| RF-CI-008 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |
| RF-CI-010 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Formatos de inventario de origen a soportar en la Etapa 2 —
  depende de las variantes de FUID y de las hojas de cálculo reales del design
  partner; se fija con dato real.
- **[CLARIFICAR]** Mecanismo de ingesta por eventos para el flujo activo (carpeta,
  correo, escáner, API): cuáles entran en la Etapa 3 y en qué orden.
- **[CLARIFICAR]** Definición de "contenido idéntico" para idempotencia (RF-CI-005):
  huella de bytes exactos frente a equivalencia tras conversión de formato.
- **[CLARIFICAR]** Política de credenciales de las Fuentes en modo on-premise
  (almacenamiento y rotación sin servicios externos).
