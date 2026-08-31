# Spec · Capa de Presentación: UI de Demostración

| Campo | Valor |
|-------|-------|
| Código de contexto | `UI` |
| Tipo | Presentación — gobernado por SDD. No introduce reglas de negocio nuevas: orquesta y expone, desde el navegador, operaciones ya especificadas y probadas en los nueve bounded contexts (`specs/001-normalizacion` .. `specs/007-validacion-humana`, `specs/contexts/spec-captura-ingesta.md`, `specs/contexts/spec-records-custodia.md`). Mismo criterio que `spec-infra-servicios.md`: "cómo se empaqueta y expone", no "qué reglas nuevas aplica". |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-01, P-02, P-03, P-08 |
| Decidido por | Victor, 2026-08-31 (ver conversación de esta sesión — stack, alcance, marcado de FICTICIOS y régimen de disciplina, las cuatro decisiones que requerían su aprobación) |

---

## 1. Propósito y frontera del contexto

El backend de los nueve bounded contexts del corte vertical está completo (T-01..T-58)
pero solo es demostrable hoy vía `curl`/Postman. Para conseguir un socio de diseño
(F4 de `plan-ejecucion-agentica.md`) y para que el propio equipo se adiestre
funcionalmente en el sistema, se necesita una capa de interacción real: una
aplicación web que un humano pueda operar en una demo de ventas, contando la misma
historia de punta a punta que ya prueba la colección Postman (`postman/README.md`):
ingesta de un lote → custodia del original → sugerencia de clasificación (FICTICIA)
→ decisión humana que materializa → validación humana (cola, decisión, aprobación
masiva) → seguimiento de Normalización/Extracción → sugerencia de enriquecimiento
(FICTICIA) → búsqueda e indexación, con recuperación por relevancia y preguntas y
respuestas (FICTICIAS) → bitácora de auditoría consolidada.

**Dentro de la frontera:** pantallas que orquestan llamadas HTTP a los endpoints ya
existentes de los nueve contextos; autenticación de la sesión de demo contra
Seguridad y Acceso; presentación uniforme y honesta de qué es una operación
determinística real y qué es una salida de un componente FICTICIO (disciplina
constitucional, P-01, extendida a la capa visual); un proxy inverso curado que
expone al navegador solo las rutas necesarias para la demo, sin romper la decisión
ya tomada en `spec-infra-servicios.md` §10 de que ciertos servicios no deben
exponerse fuera de la red interna de docker-compose.

**Fuera de la frontera:** cualquier regla de negocio nueva (todo lo que la UI
muestra ya está especificado y probado en el contexto backend correspondiente);
autenticación/sesión con token real más allá de lo que Seguridad y Acceso ya expone
(no existe todavía — ver §8); multi-tenencia; internacionalización; diseño de marca
final (paleta, logo — depende del socio de diseño, ver §8); modo "solo lectura" para
prospectos sin supervisión (ver §8); cobertura de los endpoints administrativos o de
depuración que la colección Postman ya prueba pero que no aportan a la narrativa de
venta (p. ej. reintentar una autenticación fallida a propósito).

---

## 2. Lenguaje ubicuo

- **Componente FICTICIO** — mismo término que usa la constitución: una salida
  probabilística simulada (clasificación, agrupamiento, OCR, embeddings, ranking de
  relevancia, respuesta de Q&A). La UI nunca la presenta como un resultado real de
  IA sin marcarla.
- **Marca de simulación** — el elemento visual (badge/etiqueta) que distingue una
  salida FICTICIA de una operación determinística real. Aparece siempre que la
  pantalla muestra el resultado de un componente FICTICIO, sin excepción (P-01,
  RNF-UI-002).
- **Sesión de demo** — la identidad autenticada contra Seguridad y Acceso
  (`POST /identidades/autenticacion`) que la UI conserva en el cliente para
  reenviarla como `actor` en las llamadas siguientes — mismo patrón que usa la
  colección Postman, porque Seguridad y Acceso todavía no emite un token de sesión
  real (ver §8).
- **Proxy curado** — el servicio (nuevo, de infraestructura pura) que el navegador
  sí puede alcanzar: traduce una ruta pública fija (`/api/<contexto>/...`) a la red
  interna de docker-compose, exponiendo solo lo que la demo necesita — nunca un
  acceso genérico a cualquier puerto interno.
- **Narrativa de demo** — la secuencia guionizada de pantallas que reproduce el
  flujo de punta a punta ya verificado por la carpeta 1-10 de
  `postman/SGDEA-coleccion.postman_collection.json`.

---

## 3. Modelo de dominio

Este contexto no tiene agregados propios — no persiste estado de negocio (P-08 no
aplica a un almacén nuevo, porque no hay ninguno; la bitácora que muestra es la que
ya persisten los contextos backend). Su único estado propio es de sesión de cliente
(identidad autenticada), efímero y sin persistencia de servidor.

### Vistas (equivalente a "agregados" para un contexto de presentación)

- **Sesión** — identidad autenticada (id, actor, roles) conservada en el cliente.
- **Panel de un documento** — vista compuesta que combina, para un `documento_id`,
  el original custodiado (Records/Custodia), su clasificación materializada o
  pendiente, sus sugerencias (Records/Custodia vía `GET
  /documentos/{id}/sugerencias`), y el estado de su unidad documental en
  Normalización/Extracción si existe.
- **Cola de revisión** — la lista ordenada por confianza que expone Validación
  Humana (`GET /colas/...`), presentada por tipo.
- **Resultado de búsqueda** — la lista de entradas que devuelve Indexación y
  Búsqueda (`POST /busquedas`), cada una con su marca de simulación donde
  corresponda (embedding) y sin ella donde no (coincidencia léxica).
- **Bitácora consolidada** — la unión, por documento o por sesión de demo, de los
  eventos de auditoría que exponen los `GET /eventos-auditoria` de cada contexto
  que participó en el flujo mostrado.

### Invariantes (no negociables)

- Ninguna pantalla puede mostrar una salida de un componente FICTICIO sin su marca
  de simulación (P-01 extendido a la capa visual — RNF-UI-002).
- Ninguna decisión (materialización, aprobación masiva, confirmación de límites) se
  ejecuta sin una acción explícita del usuario humano — la UI nunca auto-decide ni
  pre-marca una opción como "aceptada por defecto" (mismo invariante que
  `spec-007-validacion-humana` hereda de P-01/P-09).
- El navegador nunca llama directamente a un puerto interno de docker-compose:
  siempre pasa por el proxy curado (RNF-UI-001).

---

## 4. Contrato del contexto

### Entradas (inbound)

Ninguna — este contexto no expone una API propia para que otro contexto lo consuma.
Es una aplicación de un solo usuario humano (el operador de la demo) en cada
sesión de navegador.

### Salidas (outbound) — consume, no redefine

Todas las llamadas HTTP van al proxy curado, que las reenvía sin modificar el
contrato a los endpoints ya documentados en `spec-infra-servicios.md` (§3 a §13):

| Contexto | Endpoints que la UI consume (subconjunto) |
|---|---|
| Seguridad y Acceso | `POST /identidades/autenticacion`, `POST /autorizacion` (indirecto, vía los demás servicios) |
| Captura/Ingesta | `POST /lotes`, `GET /lotes/{id}/conteo`, `GET /lotes/{id}/conciliacion`, `POST /lotes/{id}/items/{id}/validacion` |
| Records/Custodia | `POST /documentos`, `GET /documentos/{id}`, `GET /documentos/{id}/original`, `POST /documentos/{id}/decisiones`, `GET /documentos/{id}/sugerencias`, `GET /eventos-auditoria`, `POST /documentos/{id}/verificacion` |
| Normalización | `GET /unidades/{id}`, `GET /eventos-auditoria` |
| Extracción | `GET /textos/{id}`, `GET /eventos-auditoria` |
| Clasificación | `POST /clasificaciones`, `POST /agrupamientos`, `POST /no-clasificables` |
| Validación Humana | `GET /colas/{tipo}`, `POST /decisiones`, `POST /decisiones/masivo` |
| Enriquecimiento | `POST /enriquecimientos` |
| Indexación y Búsqueda | `POST /busquedas`, `POST /recuperaciones`, `POST /preguntas`, `GET /eventos-auditoria` |

Ningún endpoint nuevo se agrega a ningún backend para que esta capa exista — es
lectura y orquestación pura sobre el contrato ya implementado. Si la narrativa de
demo necesitara un endpoint que hoy no existe, eso es una tarea del contexto backend
correspondiente, no de este.

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.
> Cada RF-UI referencia el/los RF del contexto backend que surface — no redefine su
> criterio de aceptación, solo el de mostrarlo correctamente.

**RF-UI-001 · Autenticación de la sesión de demo**
Un operador inicia sesión con un actor y credencial reales de Seguridad y Acceso; la
UI conserva la identidad para el resto de la sesión.
- Dadas credenciales válidas, Cuando el operador inicia sesión, Entonces la UI
  guarda la identidad autenticada y la reenvía como `actor` en las llamadas
  siguientes (RF-SA-001).
- Dadas credenciales inválidas, Cuando el operador intenta iniciar sesión, Entonces
  la UI muestra el rechazo sin conservar ninguna identidad.

**RF-UI-002 · Ingesta de un lote de documentos**
Un operador registra un lote y ve su conteo por estado.
- Dado un lote nuevo con al menos un ítem, Cuando el operador lo registra, Entonces
  la UI muestra el lote con sus ítems en estado `Recibido` (RF-CI-001).
- Dado un lote con ítems en distintos estados terminales, Cuando el operador
  consulta el conteo, Entonces la UI muestra el desglose y si hay pérdida silenciosa
  (RF-CI-008).

**RF-UI-003 · Custodia y verificación de integridad del original**
Un operador ve el original custodiado de un documento y puede verificar su
integridad bajo demanda.
- Dado un documento custodiado, Cuando el operador lo abre, Entonces la UI muestra
  su huella, algoritmo y procedencia (RF-RC-001/002).
- Dado un documento custodiado, Cuando el operador pide verificar su integridad,
  Entonces la UI muestra si la huella coincide (RF-RC-009).

**RF-UI-004 · Sugerencia de clasificación (FICTICIA) y decisión humana**
Un operador ve una sugerencia de clasificación simulada y decide materializarla o
no.
- Dado un documento sin clasificación, Cuando se genera una sugerencia de
  clasificación, Entonces la UI la muestra con su marca de simulación y su
  confianza (RF-CL-001..003, marcada FICTICIA).
- Dada una sugerencia visible, Cuando el operador decide materializarla, Entonces la
  UI la envía como decisión humana explícita y refleja la clasificación resultante
  (RF-RC-004).

**RF-UI-005 · Cola de validación humana y decisión individual**
Un operador ve las colas de revisión por tipo, ordenadas por confianza, y decide
sobre una sugerencia puntual.
- Dada una cola con sugerencias pendientes, Cuando el operador la abre, Entonces la
  UI las muestra ordenadas de menor a mayor confianza (RF-VH-001/002).
- Dada una sugerencia de la cola, Cuando el operador la acepta o la corrige,
  Entonces la UI produce la decisión humana y la retira de la cola (RF-VH-003).

**RF-UI-006 · Aprobación masiva de candidatos de alta confianza**
Un operador aprueba en bloque los candidatos que superan el umbral de la curva
cobertura-error, en una sola acción explícita.
- Dado un conjunto de candidatos a aprobación masiva, Cuando el operador los aprueba
  en bloque, Entonces la UI muestra una única acción confirmada y cada candidato
  queda decidido, referenciando la sugerencia que lo originó (RF-VH-004).

**RF-UI-007 · Seguimiento del pipeline de Normalización/Extracción**
Un operador ve en qué etapa del pipeline está un documento entregado a
Normalización.
- Dado un documento entregado a Normalización, Cuando el operador lo consulta,
  Entonces la UI muestra su estado (pendiente de límites, normalizada, entregada a
  Extracción, extraída) sin inventar una etapa que el backend no reporte
  (RF-NO-001..006, RF-EX-001..003).

**RF-UI-008 · Sugerencia de enriquecimiento de metadatos (FICTICIA)**
Un operador ve valores de metadatos propuestos y campos no encontrados,
distinguibles entre sí.
- Dado un texto extraído, Cuando se genera una sugerencia de enriquecimiento,
  Entonces la UI muestra cada valor propuesto con su marca de simulación y cada
  campo no encontrado, distinguibles (RF-EN-002..006/008, marcada FICTICIA).

**RF-UI-009 · Búsqueda léxica y filtrada**
Un operador busca por palabra clave y filtro de metadatos sobre el índice ya
construido.
- Dado un término de búsqueda y un filtro, Cuando el operador busca, Entonces la UI
  muestra solo las entradas indexadas que el actor autenticado tiene permiso de ver
  (RF-IB-005/008), sin marca de simulación (búsqueda léxica es real).

**RF-UI-010 · Recuperación por relevancia y preguntas y respuestas (FICTICIAS)**
Un operador pide una respuesta a una pregunta sobre el acervo indexado y ve la
respuesta con sus citas, o una negativa apropiada si no hay evidencia suficiente.
- Dada una pregunta con evidencia permitida, Cuando el operador la hace, Entonces la
  UI muestra la respuesta con sus citas, marcada FICTICIA (RF-IB-007).
- Dada una pregunta sin evidencia suficiente o sin permiso sobre la única evidencia
  disponible, Cuando el operador la hace, Entonces la UI muestra la negativa
  apropiada con su razón, nunca una respuesta inventada (RF-IB-008/010).

**RF-UI-011 · Bitácora de auditoría consolidada**
Un operador ve, para un documento o para la sesión de demo, los eventos de
auditoría de todos los contextos que participaron, con actor y fecha.
- Dado un documento que pasó por varios contextos, Cuando el operador consulta su
  bitácora, Entonces la UI muestra los eventos de cada contexto que lo tocó, cada
  uno con actor y fecha no vacíos (P-08).

**RF-UI-012 · Distinción visual universal de componentes FICTICIOS**
Requisito transversal, no ligado a una sola pantalla: la marca de simulación es un
único componente reutilizado en toda la UI, nunca una implementación distinta por
pantalla.
- Dada cualquier pantalla que muestre una salida de Clasificación, Enriquecimiento,
  la sugerencia de OCR de Extracción, el embedding/orden de relevancia/respuesta de
  Indexación y Búsqueda, Cuando se renderiza, Entonces usa el mismo componente de
  marca de simulación, visible sin interacción adicional (RNF-UI-002).

---

## 6. Requisitos no funcionales

**RNF-UI-001 · El navegador nunca alcanza un puerto interno directamente**
Toda llamada sale hacia el proxy curado, nunca hacia `captura-ingesta`,
`records-custodia` u otro puerto interno listado en `spec-infra-servicios.md` §10
como no expuesto — coherente con esa decisión ya tomada, no la reabre.

**RNF-UI-002 · Marca de simulación siempre visible**
Ninguna salida de un componente FICTICIO se muestra sin su marca, en ninguna
pantalla, sin excepción — ver RF-UI-012.

**RNF-UI-003 · Demo reproducible con un solo comando**
`docker compose -f deploy/docker-compose.saas.yml -f deploy/docker-compose.demo.yml
up -d --build` deja la UI y los nueve contextos listos para operar la narrativa
completa — mismo criterio de reproducibilidad que ya exige `postman/README.md`
para el stack de Postman.

**RNF-UI-004 · TDD contra los criterios Dado/Cuando/Entonces**
Cada RF-UI-NNN tiene al menos una prueba automatizada que ejercita la operación
real contra los servicios reales (Playwright end-to-end sobre el stack de
docker-compose, mismo criterio de honestidad que exige la constitución: nunca un
doble aislado que pueda pasar aunque el camino real esté roto). Las piezas de UI
sin lógica de orquestación (componentes puros de presentación) pueden probarse en
aislamiento (Vitest + Testing Library).

---

## 7. Trazabilidad

Este contexto no introduce requisitos con trazabilidad regulatoria propia: cada
RF-UI-NNN hereda la trazabilidad del RF backend que expone (ver la tabla de §4 y las
referencias inline de §5). No hay ninguna referencia normativa, cláusula ni umbral
nuevo en esta spec — el único origen de verdad para eso sigue siendo
`spec-records-custodia.md` §7 y las tablas de trazabilidad de cada contexto
probabilístico.

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Mecanismo de sesión con token real en Seguridad y Acceso — hoy
  `POST /identidades/autenticacion` solo devuelve la `Identidad`, sin token ni
  expiración. Esta spec decide, como alcance consciente (no como referencia
  normativa ni umbral inventado), conservar el `identidadId` en el cliente y
  reenviarlo como `actor` — mismo patrón que ya usa la colección Postman. Si el
  proyecto necesita sesión con expiración/revocación real más adelante, es una
  ampliación de `specs/006-seguridad-acceso/spec.md`, no de esta capa.
- **[CLARIFICAR]** Diseño visual final (paleta, logo, identidad de marca) — depende
  de decisiones de marca/socio de diseño que no existen todavía; esta spec no fija
  ningún valor de diseño, solo el comportamiento funcional.
- **[CLARIFICAR]** Si la demo necesita un dataset de ejemplo pre-cargado ("modo
  showcase") o si cada demo arranca en blanco y se llena en vivo durante la sesión
  de ventas — decisión de negocio/guion comercial, no técnica.
- **[CLARIFICAR]** Si se necesita un modo de solo-lectura para que un prospecto
  explore sin supervisión de un humano del equipo, o si la demo siempre la conduce
  alguien del equipo — afecta si RF-UI-004/006 (decisiones) deben poder
  deshabilitarse.

---
