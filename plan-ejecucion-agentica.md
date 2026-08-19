# Plan de Ejecución Agéntica — Etapas 0 → 1
### Claude Code + Codex como línea de producción continua · Julio 2026

---

## 0 · Tesis operativa

El proyecto tiene dos hilos y solo uno se puede automatizar:

| Hilo | ¿Automatizable? | Camino crítico |
|---|---|---|
| **Técnico** — "¿el repo refleja las specs?" | Sí: loop Claude Code ↔ Codex con tests como árbitro | No |
| **Comercial** — "¿ya firmé al socio de diseño?" | No: reuniones, convenio, muestra, archivista | **Sí** |

**Regla de oro del plan:** el sistema agéntico existe para devolverte horas, y esas horas van al hilo comercial. La métrica de éxito del plan no es "commits generados": es RF con tests verdes **y** one-pagers enviados. El propio `edd-harness.md` lo dice: sin set patrón real, la Etapa 1 no puede arrancar. El loop construye todo lo que se puede construir *hasta el muro*, y el muro es comercial.

**El principio del proceso espeja el del producto.** En el producto: *la IA propone, el archivista decide* (P-01). En el proceso de desarrollo: **los agentes proponen, tú dispones**. Esa frontera también se hace física: un hook de git impide que cualquier commit toque `specs/` sin tu intervención explícita (`HUMAN=1`).

---

## 1 · Mapa de automatización

| Régimen | Trabajo | Árbitro / gate |
|---|---|---|
| **Autónomo (loop)** | Núcleo determinístico RC + CI, arnés EDD ejecutable, pipeline CI, andamiaje de empaquetado dual (P-02) | Tests derivados de los criterios *Dado/Cuando/Entonces* + revisión de Codex |
| **Con gate humano** | Correcciones de corpus (F0), decisión de stack, estructura del repo, cada incremento del andamiaje | Tu aprobación del diff / de la propuesta |
| **Bloqueado hasta dato real** | Clasificación, OCR, límites, extracción, recuperación, Q&A | Set patrón etiquetado (P-05; regla de oro 3.1: nunca sintético para medir) |
| **Nunca autónomo** | Referencias normativas, valores de umbral, convenio, one-pager, cualquier edición de `specs/` | Solo humano (hook de git + constitución de ejecución) |

---

## 2 · Fases

### F0 · Pre-vuelo — correcciones de corpus (gated · ~30 min)
Una sesión de Claude Code que **propone diffs y no comitea**. Tú revisas `git diff specs/` y comiteas con `HUMAN=1`. Las tres correcciones exactas están en el **Apéndice A**:

1. `eval-clasificacion.md §4.5` — eliminar "auto-aceptarse sin revisión"; redefinir cobertura como **candidatos a aprobación masiva mediada por humano** (consistente con P-01 / RF-RC-004 / P-09).
2. `CLAUDE-CODE-KICKOFF.md` + `README.md` — la lista de **siete** contextos pasa a **nueve**: las specs ya tratan *Normalización* y *Extracción* como contextos propios (spec-captura-ingesta §1; tabla de componentes del edd-harness §2). Sin esto, el andamiaje nace mal.
3. Normativa — reemplazar Acuerdos 002 de 2014 / 003 de 2015 / 004 de 2019 por **Acuerdo AGN 001 de 2024** con la nota "(compila el antiguo Acuerdo X)", como ya hace el one-pager. Las celdas de *Referencia específica* siguen **PENDIENTE**: los números de artículo los fija el archivista del socio, nunca un agente.

### F1 · Fundación — stack, spec-kit y andamiaje (gated · 1–2 sesiones)
1. **D1 · Decisión de stack.** Si no está tomada, correr primero la sesión "Variante" del kickoff (el agente compara 2–3 opciones contra P-02/P-03/P-10; tú decides). El loop no arranca sin `‹LENGUAJE›/‹GESTOR›/‹EMPAQUETADO›/‹CI›` fijados.
2. `specify init` (spec-kit) con integraciones Claude Code + Codex.
3. **Sesión de andamiaje interactiva** con el PROMPT del kickoff corregido (9 contextos). Tu propio kickoff exige aprobar la estructura antes de crear archivos: ese gate se respeta, no se automatiza.
4. Cierre de F1: `codex exec` revisa el esqueleto completo contra la constitución → primera `REVIEW.md`.

### F2 · Corte vertical determinístico (autónomo · la jugada clave)
En lugar de "toda la ingesta y luego todo RC" (anti-P-07), el loop construye **una rebanada de punta a punta con un clasificador ficticio** — el mismo componente de prueba que el kickoff ya exige para validar el arnés:

```
ingesta de lote → validación/cuarentena → custodia del original
→ Sugerencia FICTICIA vía capa anticorrupción → decisión humana
→ bitácora inmutable → conciliación FUID
```

Cubre RF-CI-001/002/006/007/008 y RF-RC-001/002/003/004/005/006/009 con tests nacidos de sus criterios de aceptación. **El día que firmes al socio no se construye el sistema: se enchufa el componente real donde estaba el ficticio y se calibra.** La espera comercial se convierte en ventaja técnica.

### F3 · Arnés ejecutable + CI + appliance (autónomo)
El arnés carga un set patrón (de juguete por ahora), corre el componente ficticio y emite una **boleta de resultados versionada** integrada al pipeline de CI. Se añaden los gates de seguridad que ya tienes elegidos (AgentShield + plugin security-review de Anthropic) y el andamiaje de empaquetado para ambos modos (P-02). F2 y F3 se intercalan en el mismo `TODO.md`.

### F4 · El muro — socio de diseño y set patrón (humano · arranca HOY en paralelo)
El loop no puede cruzar esto y el plan lo dice sin eufemismos:
- Enviar el one-pager a 3–5 entidades calificadas con PINAR/PGD y SECOP II.
- Reunión de 45 min → convenio (proteger **9.2** y negociar el **Anexo 1** como lo que es: la negociación del set patrón; no aceptar una muestra "fácil" — espeja edd-harness §3.3).
- Entrega de muestra bajo Anexo 3 → etiquetado por capas con el archivista (decidir herramienta de anotación, [CLARIFICAR] pendiente del harness).
- **Tarea automatizable mientras tanto:** Skill_Seekers sobre el PDF oficial del Acuerdo 001 de 2024 → skill normativa para los agentes (con tu verificación puntual; extracción, nunca invención).

### F5 · Etapa 1 con dato real (mixto)
Línea base de clasificación → calibrar gates absolutos (§5.1) → el loop vuelve, ahora con **dos árbitros**: tests + boleta del arnés (gate de no-regresión §5.2 activo desde el día uno). El componente probabilístico se desarrolla bajo EDD; el producto sigue bajo "la IA propone, el archivista decide".

---

## 3 · Arquitectura del sistema agéntico

**Roles.** Claude Code implementa (TDD contra los criterios de la spec). Codex revisa cada commit contra la constitución y puede **VETAR**. Git es la memoria compartida; ninguna sesión recuerda nada, todo estado vive en archivos comiteados.

**Archivos de coordinación** (los crea `orquestador.sh bootstrap`):

| Archivo | Función |
|---|---|
| `STATE.md` | Fase actual, tarea en curso, decisiones tomadas |
| `TODO.md` | Cola priorizada `- [ ]`; `- [x]` hecho; `- [?]` bloqueada esperándote |
| `REVIEW.md` | Última revisión de Codex; `VETO: <motivo>` en línea 1 detiene el loop |
| `QUESTIONS.md` | Escalaciones (solo-anexado): todo `[CLARIFICAR]` va aquí, nunca se inventa |
| `BLOCKED.md` | Escrito al detenerse: motivo + cómo reanudar |
| `CLAUDE.md` / `AGENTS.md` | Contexto permanente de cada agente; ambos apuntan a `.specify/memory/constitution.md` |

**Constitución de ejecución** (embebida en CLAUDE.md/AGENTS.md y reforzada con `--append-system-prompt`): no tocar `specs/`; no inventar normativa ni umbrales; no implementar componentes probabilísticos reales; no cambiar el stack; ante ambigüedad → `QUESTIONS.md` y parar la tarea.

**Patrón 1 (implementado): ping-pong headless.** `claude -p … --output-format json` implementa → tests → `codex exec --json` revisa. Depurable, simple, cada paso deja log.

**Patrón 2 (cuando el loop esté estable): Codex como herramienta MCP.** `claude mcp add codex -- codex mcp-server` y Claude delega revisiones dentro de su propia sesión. Más elegante, menos observable; migrar solo cuando confíes en el Patrón 1.

---

## 4 · Operación

- **Sandbox.** Todo corre en un contenedor Docker dedicado (tu Ubuntu 24 lo soporta de sobra) montando **solo el repo**, sin credenciales reales. Únicamente ahí se admite `CONTAINED=1` (que activa `--dangerously-skip-permissions`).
- **Ventanas de 5 h.** Los runs headless consumen la misma ventana rodante que tus sesiones interactivas (Claude y Codex). El script detecta `rate_limit`/`overloaded`/429, hace backoff exponencial (10 → 90 min) y **reanuda la misma sesión** de Claude con `--resume <session_id>` capturado del JSON.
- **Watchdog.** Dos iteraciones sin commits nuevos → el loop se detiene solo (agentes girando en vacío).
- **Tope de intentos.** 3 intentos fallidos de tests sobre la misma tarea → `- [?]` + `BLOCKED.md` + notificación. El loop nunca "insiste hasta romper".
- **Notificaciones.** `NTFY_TOPIC=tu-topic` envía push vía ntfy.sh al bloquearse o terminar; sin configurar, solo log.
- **Digest diario (10 min).** `./orquestador.sh digest` imprime estado, últimos commits, la revisión de Codex y tus preguntas pendientes. Es tu ritual de lectura — resuelve el patrón de "artefactos más rápido de lo que los digiero": el loop produce, el digest narra.
- **Cron opcional** para respetar ventanas:
  `0 6,12,18 * * * cd /repo && CONTAINED=1 MAX_ITER=10 ./orquestador.sh loop >> .loop/cron.log 2>&1`

---

## 5 · Función de fitness (por qué el loop no degrada el código)

Sin árbitro objetivo, dos LLMs iterando solos degradan el código en vez de evolucionarlo. Aquí el árbitro existe por diseño:

1. **Tests verdes** — cada RF trae criterios *Dado/Cuando/Entonces*; el prompt obliga a escribir primero los tests que los expresan. Verde = avanzar; rojo 3 veces = parar y preguntarte.
2. **Revisión adversarial** — Codex verifica P-01/P-03/P-08 y la *honestidad* de los tests (¿prueban el criterio o están amañados?). Un `VETO:` detiene el loop; tú decides, los agentes no se auto-revierten.
3. **(F5) La boleta del arnés** — se suma como segundo árbitro con el gate de no-regresión.

---

## 6 · Métricas y anti-trampa

**Cuentan:** RF con tests verdes (corte 1 ≈ 12 de 20) · boleta del arnés corriendo en CI · one-pagers enviados / reuniones / **firma**.
**No cuentan:** número de commits, líneas generadas, iteraciones del loop.

**La trampa de productividad, nombrada:** un loop nocturno produciendo código es la forma más seductora de progreso aparente. Regla semanal fija: **primero una acción del hilo comercial, después mirar el loop.** Si en dos semanas el loop avanza y el pipeline comercial no, el plan está fallando aunque el repo brille.

---

## 7 · Riesgos

| Riesgo | Mitigación |
|---|---|
| Degradación por iteración | Tests obligatorios + tope de intentos + watchdog |
| Fuga de alcance del agente | Constitución de ejecución + hook de git sobre `specs/` + VETO de Codex |
| Rate limits parten el trabajo | Backoff + `--resume` (Claude) / `codex exec resume` (Codex); estado siempre en archivos |
| Seguridad del entorno | Contenedor dedicado, sin volúmenes del host ni credenciales; AgentShield + security-review en CI |
| Riesgo legal/normativo | Agentes jamás tocan normativa, umbrales ni documentos comerciales |
| Trampa de productividad | Sección 6; el digest incluye el recordatorio comercial |

---

## Apéndice A · Textos sugeridos para F0

**A.1 — `eval-clasificacion.md §4.5`** (reemplaza las dos definiciones):
> - **Cobertura** — fracción de documentos con confianza por encima del umbral, que se vuelven **candidatos a aprobación masiva**: el archivista los aprueba en bloque mediante una acción explícita (P-09), registrada con actor, fecha y sugerencias referenciadas (RF-RC-004).
> - **Error a esa cobertura** — fracción de esos candidatos cuya sugerencia principal es incorrecta.
> La curva responde la pregunta de negocio: *a un nivel de error tolerable, ¿qué porcentaje del fondo puede aprobarse en bloque con revisión mínima?* El resto va a la cola de validación documento a documento.

Revisar también la fila de trazabilidad (§9): la curva ya traza a la promesa de producto; añadir traza a RF-RC-004.

**A.2 — `CLAUDE-CODE-KICKOFF.md` (punto 1 de "Lo que SÍ debes hacer")**:
> Debe reflejar los **nueve** bounded contexts nombrados en las specs (Captura/Ingesta, **Normalización**, **Extracción**, Clasificación, Enriquecimiento, Indexación y Búsqueda, Records/Custodia, Seguridad y Acceso, Validación Humana), aunque la mayoría queden como esqueletos vacíos por ahora.

Y en `README.md`, la lista de specs futuras incluye Normalización y Extracción.

**A.3 — Normativa** (`spec-records-custodia.md §7`, `eval-clasificacion.md §9`):
- "Acuerdo 003 de 2015 (…)" → "Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015)".
- "Acuerdo 002 de 2014 (TRD)" → "Acuerdo AGN 001 de 2024 — procedimiento TRD (antes Acuerdos 002 de 2014 y 004 de 2019)".
- Se conservan Ley 594 de 2000, Decreto 1080 de 2015, Ley 1437 de 2011, ISO 15489/16175. Celdas *Referencia específica*: **PENDIENTE**, intactas.

## Apéndice B · Comandos de referencia

```bash
./orquestador.sh bootstrap    # CLAUDE.md, AGENTS.md, archivos de coordinación, hook de specs/
./orquestador.sh preflight    # F0: propone los diffs de A.1–A.3 y se detiene para tu revisión
./orquestador.sh seed core    # siembra TODO.md con el corte vertical determinístico (F2+F3)
./orquestador.sh loop         # el loop autónomo (correr dentro del contenedor)
./orquestador.sh digest       # tu lectura diaria de 10 minutos
./orquestador.sh status       # estado rápido
```
