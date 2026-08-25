# Spec · Bounded Context: Extracción

| Campo | Valor |
|-------|-------|
| Código de contexto | `EX` |
| Tipo | Híbrido — núcleo determinístico (SDD) con un componente probabilístico bajo EDD (OCR / extracción de texto, ver `specs/eval/edd-harness.md` §2 y §4) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-03, P-05, P-06, P-08, P-09 |

---

## 1. Propósito y frontera del contexto

Extracción convierte cada unidad documental candidata que le entrega Normalización
en su **texto extraído**, distinguiendo dos soportes: **born-digital** (el texto ya
existe embebido en el artefacto; extracción determinística) y **escaneo** (requiere
OCR, componente probabilístico gobernado por EDD). El texto extraído es el insumo
que consumen Clasificación, Enriquecimiento e Indexación y Búsqueda.

No interpreta el significado del texto: no clasifica, no decide metadatos, no indexa.
Tampoco decide límites de documento — eso ya lo resolvió Normalización.

**Dentro de la frontera:** recepción de unidades documentales candidatas
normalizadas, determinación de soporte (born-digital / escaneo), extracción
determinística de texto embebido, OCR probabilístico gobernado por EDD,
estratificación de calidad de la extracción, enrutamiento de extracciones de baja
confianza a la cola de revisión humana, propagación de procedencia, entrega del
texto extraído a Clasificación, Enriquecimiento e Indexación y Búsqueda.

**Fuera de la frontera (de este contexto):** detección de límites de documento y
normalización de formato (contexto Normalización); clasificación (contexto
Clasificación); extracción de metadatos estructurados —valores de campos concretos—
(contexto Enriquecimiento, que consume el texto de este contexto); indexación y
recuperación (contexto Indexación y Búsqueda).

**Por qué es un contexto híbrido:** `specs/eval/edd-harness.md` §2 clasifica "OCR /
extracción de texto" como un componente **probabilístico** gobernado por EDD (P-05),
con su propia métrica principal (tasa de error de carácter, §4). La extracción de
texto ya embebido en un artefacto born-digital, en cambio, no requiere inferencia:
es una operación determinística de lectura de formato, especificada bajo SDD (P-06),
igual que el resto de este contexto (recepción, enrutamiento por soporte,
estratificación de calidad, propagación de procedencia, entrega).

**Por qué el texto extraído no cruza la misma capa anticorrupción que una
Sugerencia de clasificación:** el texto extraído no es en sí mismo estado
archivístico de un documento (no es una serie, una subserie, ni un metadato
materializado) — es el insumo intermedio que otros componentes probabilísticos
posteriores (Clasificación, Enriquecimiento) consumen para producir sus propias
Sugerencias, esas sí sujetas a la capa anticorrupción de `spec-records-custodia.md`
§4. Por eso el componente de OCR se gobierna con un **gate de EDD a nivel de
componente** (P-05: el motor de OCR no se libera sin pasar su gate de CER) en vez de
una confirmación humana por instancia. Esta lectura es razonable pero no está escrita
en ninguna spec previa como regla explícita — queda marcada en la sección 8.

---

## 2. Lenguaje ubicuo

- **Unidad documental candidata** — la entrada de este contexto, tal como la entrega
  Normalización (ver `specs/001-normalizacion/spec.md` §2).
- **Soporte** — born-digital o escaneo; determina si la extracción de texto es
  determinística (born-digital) o probabilística vía OCR (escaneo).
- **Texto extraído** — el resultado de la extracción: el texto de la unidad
  documental candidata, junto con su soporte de origen y su calidad/confianza.
- **Calidad de extracción** — la señal que estratifica el texto extraído por
  confiabilidad (p. ej. una tasa de error de carácter estimada, ver
  `specs/eval/edd-harness.md` §4); alimenta la cola de revisión humana de baja
  confianza (P-09).
- **Cola de revisión de baja confianza** — el conjunto de extracciones cuya calidad
  cae bajo un umbral, pendientes de que un humano las inspeccione o corrija.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Texto extraído** (agregado raíz) — referencia a la unidad documental candidata
  de origen, soporte, contenido textual (por página o global), calidad/confianza,
  estado, procedencia heredada.

### Estados del texto extraído

`Pendiente de extracción` → `Extraído` (terminal de éxito; desde aquí se entrega en
paralelo a Clasificación, Enriquecimiento e Indexación y Búsqueda — ver §4).
Ramas terminales alternativas: `Rechazado` (extracción imposible incluso con OCR,
no recuperable sin un artefacto nuevo) y `En cuarentena` (calidad de extracción bajo
el umbral mínimo aceptable, recuperable con reescaneo o revisión humana) — mismo
criterio de `RF-CI-006`, aplicado ahora una tercera vez en el pipeline (Captura/
Ingesta, Normalización, Extracción).

### Invariantes (no negociables)

1. Todo texto extraído se rastrea hasta su unidad documental candidata de origen y,
   transitivamente, hasta su procedencia completa.
2. El **soporte** determina el mecanismo de extracción: born-digital extrae el texto
   ya embebido de forma determinística; escaneo invoca el componente probabilístico
   de OCR, gobernado por sus propios gates de EDD (P-05) antes de liberarse.
3. Todo texto extraído alcanza un estado terminal contabilizado: no hay pérdida
   silenciosa (P-08), mismo patrón que Captura/Ingesta y Normalización.
4. Ninguna extracción de baja calidad se descarta ni se entrega en silencio como si
   fuera confiable: siempre conserva su medida de calidad y, bajo el umbral
   correspondiente, queda visible en la cola de revisión humana (P-09).

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Normalización | Unidad documental candidata normalizada, con su procedencia (RF-NO-010) |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Clasificación | Texto extraído del documento |
| Enriquecimiento | Texto extraído del documento |
| Indexación y Búsqueda | Texto extraído del documento |
| Validación Humana | Extracciones de baja confianza pendientes de revisión (P-09) |
| Seguridad y Acceso | Eventos de auditoría de extracción |
| Operador | Reporte de textos en cuarentena o rechazados |

El mismo `Texto extraído` en estado `Extraído` se entrega en paralelo a los tres
consumidores probabilísticos posteriores; no hay un estado distinto por consumidor,
igual que `spec-records-custodia.md` §4 entrega el mismo documento materializado a
varios destinos sin duplicar su modelo de estados.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-EX-001 · Recepción de unidades documentales normalizadas**
Toda unidad documental candidata que Normalización entrega (RF-NO-010) genera un
texto extraído en estado `Pendiente de extracción`, conservando su procedencia.
- Dada una unidad documental candidata `Entregada a Extracción`, Cuando Extracción
  la recibe, Entonces existe un texto extraído vinculado a ella en estado
  `Pendiente de extracción`.

**RF-EX-002 · Determinación de soporte**
El contexto determina si una unidad documental candidata es born-digital o de
escaneo antes de decidir el mecanismo de extracción.
- Dada una unidad documental candidata, Cuando se determina su soporte, Entonces
  queda marcada como `born-digital` o `escaneo` antes de iniciar la extracción.

**RF-EX-003 · Extracción determinística de texto embebido (born-digital)**
Para un soporte born-digital, el texto ya embebido en el artefacto se extrae de
forma determinística, sin invocar el componente probabilístico.
- Dada una unidad documental candidata `born-digital`, Cuando se extrae su texto,
  Entonces el texto extraído queda en `Extraído` con calidad máxima y sin invocar
  OCR.

**RF-EX-004 · Extracción probabilística de texto vía OCR (escaneo)**
Para un soporte de escaneo, el contexto invoca el componente probabilístico de OCR
(gobernado por EDD, ver `specs/eval/edd-harness.md` §2 y §4), que produce el texto
extraído junto con su calidad estimada.
- Dada una unidad documental candidata de `escaneo`, Cuando se extrae su texto vía
  OCR, Entonces el texto extraído queda en `Extraído` con una calidad estimada
  asociada.

**RF-EX-005 · Estratificación de calidad de la extracción**
Todo texto extraído conserva una medida de calidad/confianza, sin importar su
soporte de origen.
- Dado un texto extraído, Cuando se consulta, Entonces expone su calidad/confianza
  y su soporte de origen.

**RF-EX-006 · Enrutamiento de baja confianza a revisión humana**
Un texto extraído cuya calidad cae bajo el umbral mínimo se enruta a la cola de
revisión humana por confianza (P-09), sin que eso le impida entregarse aguas abajo
marcado como de baja confianza.
- Dado un texto extraído con calidad bajo el umbral mínimo, Cuando se evalúa,
  Entonces aparece en la cola de revisión humana de baja confianza y conserva su
  marca de baja calidad al entregarse.

**RF-EX-007 · Propagación de procedencia**
Cada texto extraído hereda la procedencia completa de su unidad documental
candidata de origen.
- Dado un texto extraído, Cuando se consulta su procedencia, Entonces incluye la
  procedencia completa de su unidad documental candidata de origen.

**RF-EX-008 · Cero pérdida silenciosa**
Todo texto extraído alcanza un estado terminal contabilizado.
- Dado un conjunto de unidades documentales candidatas procesadas, Cuando se suma
  por estado, Entonces la cuenta de `Extraído` + `Rechazado` + `En cuarentena`
  iguala el total de unidades recibidas.

**RF-EX-009 · Validación y cuarentena de extracciones**
Una extracción imposible o de calidad irrecuperable se pone en cuarentena o se
rechaza con una razón explícita, con el mismo criterio que RF-CI-006: recuperable
dentro del sistema actual (p. ej. reescaneo) → `En cuarentena`; solo recuperable con
un artefacto nuevo → `Rechazado`.
- Dada una unidad documental candidata cuya extracción falla pero es recuperable
  con un reescaneo, Cuando se detecta, Entonces el texto extraído queda `En
  cuarentena` con razón registrada.
- Dada una unidad documental candidata cuya extracción no es recuperable sin un
  artefacto nuevo, Cuando se detecta, Entonces el texto extraído queda `Rechazado`
  con razón registrada.

**RF-EX-010 · Entrega a Clasificación, Enriquecimiento e Indexación y Búsqueda**
El contexto entrega el texto extraído a los tres contextos consumidores mediante un
contrato estable; no clasifica, no decide metadatos ni indexa.
- Dado un texto extraído en `Extraído`, Cuando se entrega, Entonces Clasificación,
  Enriquecimiento e Indexación y Búsqueda lo reciben con su procedencia y su
  calidad/confianza.

---

## 6. Requisitos no funcionales

**RNF-EX-001 · Rendimiento a volumen** — la extracción (determinística y vía OCR)
sostiene fondos acumulados de millones de páginas sin degradación inaceptable.

**RNF-EX-002 · Paridad de despliegue** — toda la funcionalidad opera idéntica en
SaaS y en appliance on-premise (P-02, P-10); el motor de OCR se consume detrás de
una interfaz propia con una implementación autoalojada (P-03), nunca directo.

**RNF-EX-003 · Trazabilidad de calidad** — todo texto extraído conserva su medida
de calidad y su soporte de origen, consultables después — necesario para separar un
fallo de clasificación propio de un fallo heredado de OCR (ya anticipado en
`specs/eval/eval-clasificacion.md` §7).

**RNF-EX-004 · Observabilidad de la cola de revisión** — el volumen y la antigüedad
de las extracciones de baja confianza pendientes de revisión son consultables en
tiempo real.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-EX-001 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-EX-002 | Constitución del proyecto P-06 (alcance determinístico del enrutamiento por soporte) | N/A | ☐ |
| RF-EX-003 | Requisitos funcionales de SGDEA (AGN); ISO 16175 | PENDIENTE | ☐ |
| RF-EX-004 | Constitución del proyecto P-05 (evaluación antes que código, para lo probabilístico) | N/A | ☐ |
| RF-EX-005 | Constitución del proyecto P-05, P-09 (calidad alimenta la cola de revisión) | N/A | ☐ |
| RF-EX-006 | Constitución del proyecto P-09 (validación humana como producto) | N/A | ☐ |
| RF-EX-007 | Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015); ISO 16175 (metadatos de procedencia) | PENDIENTE | ☐ |
| RF-EX-008 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |
| RF-EX-009 | Requisitos funcionales de SGDEA (AGN) — captura | PENDIENTE | ☐ |
| RF-EX-010 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Si la corrección humana de un texto de baja confianza reemplaza
  el texto extraído original o se registra como una anotación separada, y si esa
  corrección retroalimenta el set patrón del arnés (mismo mecanismo de
  re-revisión que `specs/eval/edd-harness.md` §6.5 deja pendiente).
- **[CLARIFICAR]** Motor(es) de OCR concretos a evaluar — la constitución prohíbe
  construir un motor de OCR propio desde cero ("Disciplina de alcance") pero no fija
  cuál se usa o se ajusta; es decisión de la Etapa 1, informada por el arnés.
  Cualquiera que se elija debe tener una implementación autoalojada viable para
  on-premise (P-03, P-10) o el gate de paridad de despliegue (RNF-EX-002) no pasa.
- **[CLARIFICAR]** Umbral de calidad que separa "flujo automático aguas abajo" de
  "cuarentena por calidad irrecuperable" — se calibra con el arnés, mismo patrón que
  los gates de `eval-clasificacion.md` §6 (estructura en Etapa 0, valores en Etapa 1).
- **[CLARIFICAR]** Si Clasificación y Enriquecimiento deben propagar la marca de
  baja confianza del texto extraído hacia su propia confianza compuesta, o tratarla
  como una señal independiente en la estratificación del arnés.
