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

**Resuelto (Victor, 2026-08-27, ver `QUESTIONS.md`) — el resultado de OCR SÍ
exige confirmación humana antes de materializar `Extraído`:** un borrador
anterior de esta sección argumentaba que el texto extraído, al no ser en sí
mismo estado archivístico (no es una serie, subserie ni metadato
materializado), podía quedar exento de la capa anticorrupción que
`spec-records-custodia.md` §4 exige para una Sugerencia, gobernándose en su
lugar solo con un gate de EDD a nivel de componente (P-05: el motor de OCR no
se libera sin pasar su gate de CER). Esa lectura nunca llegó a ser una regla
explícita — el propio texto lo admitía ("razonable pero no está escrita...
como regla explícita") — y Codex la vetó al revisar la primera implementación
del dominio (T-40, commit `dd97fb4`) citando P-01: nada probabilístico escribe
estado por sí solo. Victor ratificó el veto como la lectura correcta: RF-EX-004
(§5) exige ahora una confirmación humana explícita entre recibir el resultado
de OCR y materializar `Extraído`, mismo patrón que RF-RC-004/RF-NO-004 en
records-custodia/Normalización, sin excepción para este contexto. El gate de
EDD a nivel de componente (P-05) sigue vigente — filtra qué motor de OCR se
libera a producción — pero ya no sustituye la confirmación por instancia; son
controles complementarios, no alternativos. El enrutamiento por calidad
(RF-EX-006) sigue siendo un control adicional posterior a la confirmación
(revisión reforzada de extracciones ya confirmadas pero de baja calidad), no
un sustituto de ella.

**Segunda vuelta (Codex, commit `e623ad6`) — aplazar la materialización no
basta; también debe cruzar como Sugerencia:** la primera corrección del
párrafo anterior movió el momento de la confirmación humana, pero seguía
adjuntando al agregado un `ResultadoOcr` sin `evidencia`, sin la forma de una
`Sugerencia`. Codex mantuvo el veto: P-01 exige que la salida probabilística
cruce la capa anticorrupción *como Sugerencia*, no solo que una decisión
humana la materialice después. Corregido: la sugerencia de OCR
(`SugerenciaOcr` en `dominio.py`) ahora porta `evidencia` y tiene exactamente
el mismo shape que `SugerenciaDeLimites` (Normalización) y `Sugerencia`
(records-custodia) — con `contenido` añadido porque eso es, precisamente, lo
que una sugerencia de OCR propone.

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
- **Sugerencia de OCR** — la propuesta que produce el componente probabilístico
  de OCR (modelo, contenido propuesto, calidad estimada, evidencia); no
  materializa el texto extraído por sí sola (RF-EX-004) — solo una
  confirmación humana lo hace (RF-EX-011). Mismo estatus que `Sugerencia`
  (records-custodia) y `SugerenciaDeLimites` (Normalización): una propuesta,
  no un hecho consumado.

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
(gobernado por EDD, ver `specs/eval/edd-harness.md` §2 y §4), que produce una
**sugerencia de extracción** (modelo, contenido propuesto, calidad estimada y
evidencia) — no un resultado ya terminado. Esa sugerencia cruza como tal la
capa anticorrupción del contexto y no materializa el texto extraído por sí
sola (P-01; resuelto 2026-08-27, ver §1 y RF-EX-011) — queda adjunta,
pendiente de la confirmación humana que exige RF-EX-011.
- Dada una unidad documental candidata de `escaneo`, Cuando se recibe la
  sugerencia de su extracción vía OCR, Entonces la sugerencia (contenido
  propuesto, calidad estimada y evidencia) queda adjunta al texto extraído,
  que permanece `Pendiente de extracción`.

**RF-EX-011 · Confirmación humana de la extracción vía OCR**
Una sugerencia de OCR nunca materializa el texto extraído por sí sola; solo
una confirmación humana explícita lo hace, mismo criterio que RF-RC-004
(records-custodia) y RF-NO-004 (Normalización) — resuelto 2026-08-27 (Victor,
ver `QUESTIONS.md`), corrige los VETOs de Codex sobre las dos primeras
implementaciones del dominio de este contexto (T-40, commits `dd97fb4` y
`e623ad6`).
- Dada una sugerencia de OCR adjunta a un texto extraído `Pendiente de
  extracción`, Cuando un actor autorizado la confirma, Entonces el texto
  extraído queda `Extraído` con el contenido y la calidad de la sugerencia
  confirmada, y con el actor y la fecha de la confirmación registrados.

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
| RF-EX-011 | Constitución del proyecto P-01; RF-RC-004; RF-NO-004 | N/A | ☐ |

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
