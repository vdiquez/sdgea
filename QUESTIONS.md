# QUESTIONS — solo anexado; las responde el humano

## 2026-08-18 · F0 preflight bloqueado — [?]

Tarea recibida (Apéndice A del plan, vía `orquestador.sh preflight`): aplicar tres
correcciones en el árbol de trabajo, sin comitear, sobre `specs/eval/eval-clasificacion.md`
§4.5/§9, `specs/contexts/spec-records-custodia.md` §7, `CLAUDE-CODE-KICKOFF.md` y
`specs/README.md`.

No pude ejecutarla porque encontré dos problemas que no puedo resolver sin inventar
una decisión:

**A. No existe el directorio `specs/` en este repo.** Ninguno de los archivos vive en
las rutas que indica la tarea. En su lugar hay archivos planos en la raíz cuyo
contenido corresponde: `00-constitution.md`, `README.md` (el contenido de este último
es literalmente la descripción de `/specs`, incluyendo el árbol `specs/README.md`,
`specs/00-constitution.md`, `specs/contexts/...`), `spec-records-custodia.md`,
`spec-captura-ingesta.md`, `edd-harness.md`, `eval-clasificacion.md`. Los logs de
`.loop/logs/claude-preflight-1.json` y `-2.json` muestran que los intentos previos
fallaron por "Not logged in", no por este motivo — es decir, `specs/` nunca se colocó
en este repo, contra lo que pide `CLAUDE-CODE-KICKOFF.md` ("Coloca el directorio
`specs/` completo en la raíz del repo... antes de empezar").

**B. Conflicto directo de instrucciones.** El sistema me añade la regla dura "no
modificar nada bajo specs/" en la misma invocación que me pide editar contenido de
spec (`eval-clasificacion.md`, `spec-records-custodia.md`). `CLAUDE.md`/`AGENTS.md`
dicen además "specs/** es SOLO LECTURA para agentes". El hook de pre-commit instalado
por `orquestador.sh bootstrap` sugiere que la intención real es: los agentes SÍ
pueden editar el árbol de trabajo bajo `specs/` en F0, pero NUNCA comitear ese cambio
sin `HUMAN=1` — pero eso no es lo que dice la regla dura tal como está escrita, y no
quiero asumir cuál de las dos prevalece.

**Qué necesito para desbloquear:**
1. ¿Debo crear el directorio `specs/` y mover/reorganizar los archivos planos de la
   raíz dentro de él (según el árbol que ya describe `README.md`), o los archivos se
   quedan donde están y las rutas de la tarea son solo nombres lógicos?
2. Confirmar que, para la fase F0 específicamente, "no modificar nada bajo specs/" no
   aplica a las tres correcciones pedidas (el bloqueo real es solo sobre el commit,
   protegido por el hook `HUMAN=1`), o indicarme lo contrario.

**Las tres correcciones quedan listas para aplicar en cuanto se resuelva lo anterior**
(ya verifiqué que el contenido actual encaja con lo que pide el Apéndice A):
- `eval-clasificacion.md` §4.5: hoy dice "podrían auto-aceptarse sin revisión" /
  "auto-aceptaciones". Cambiar Cobertura → fracción de documentos sobre el umbral que
  se vuelven CANDIDATOS A APROBACIÓN MASIVA, aprobados en bloque por el archivista
  mediante acción explícita (P-09) registrada con actor y fecha (RF-RC-004); Error a
  esa cobertura → fracción de esos candidatos cuya sugerencia principal es incorrecta.
  Ajustar la pregunta de negocio de la sección y añadir traza a RF-RC-004 en §9 (hoy
  §9 solo traza RF-RC-004 a P-09, sin mencionar la acción P-09/RF-RC-004 de aprobación
  masiva explícitamente).
- `CLAUDE-CODE-KICKOFF.md` (línea ~76) y `specs/README.md`/`README.md`: "siete bounded
  contexts" → nueve, añadiendo Normalización y Extracción (ya nombrados como contextos
  aparte en `spec-captura-ingesta.md` §1 y en la tabla de `edd-harness.md` §2).
- `spec-records-custodia.md` §7 y `eval-clasificacion.md` §9: reemplazar "Acuerdo 003
  de 2015" (filas RF-RC-001, RF-RC-008) por "Acuerdo AGN 001 de 2024 (compila el
  antiguo Acuerdo 003 de 2015)"; reemplazar "Acuerdo 002 de 2014 (TRD)" (filas
  RF-RC-006, RF-RC-007 en spec-records-custodia.md; fila de trazabilidad en
  eval-clasificacion.md §9) por "Acuerdo AGN 001 de 2024 — procedimiento TRD (antes
  Acuerdos 002 de 2014 y 004 de 2019)". Conservar Ley 594, Decreto 1080 de 2015, ISO
  15489/16175 tal cual. No tocar ninguna celda `PENDIENTE`.

No se modificó ningún archivo. `STATE.md` queda marcado como bloqueado en F0 hasta
respuesta humana.

## 2026-08-18 · Resuelto

Causa raíz corregida en run_claude() de orquestador.sh (regla dura
contradecía la tarea de preflight). Política vigente: solo
specs/00-constitution.md requiere HUMAN=1. Reintentar preflight.

## 2026-08-20 · T-02 RF-CI-006 bloqueada — [?]

Tarea tomada de TODO.md: "T-02 RF-CI-006 Validación y cuarentena con razón
registrada", primera tarea abierta tras T-01.

El criterio Dado/Cuando/Entonces de RF-CI-006 en
`specs/contexts/spec-captura-ingesta.md` §5 dice:

> Dado un artefacto corrupto, Cuando se valida, Entonces el ítem queda `En
> cuarentena` o `Rechazado` con razón registrada.

Ese criterio es una disyunción: no dice qué condición concreta (corrupto,
ilegible, formato no soportado, u otra) lleva a `Rechazado` y cuál lleva a `En
cuarentena`. El propio §8 de la misma spec ya lo señala como pregunta abierta
sin resolver:

> **[CLARIFICAR]** Criterios de cuarentena automática frente a rechazo: qué
> condiciones llevan a cada rama terminal.

Para escribir TDD contra este RF necesito casos concretos ("dado un artefacto
con problema X, el ítem termina en Rechazado" / "dado un artefacto con
problema Y, el ítem termina en Cuarentena"). No existe ese mapeo en la spec.
Cualquier regla que use para decidirlo (p. ej. "formato no soportado siempre
rechaza porque es irrecuperable; corrupto/ilegible siempre pone en cuarentena
porque puede corregirse con reescaneo") sería una política de negocio que
estoy inventando, no algo que ya esté resuelto en la spec — exactamente el
tipo de ambigüedad real que la constitución de ejecución me pide no resolver
por mi cuenta.

**Qué necesito para desbloquear:** que el archivista del design partner (o
quien tenga la autoridad de negocio) defina, para RF-CI-006, la taxonomía
mínima de condiciones de validación (al menos: corrupto, ilegible, formato no
soportado) y para cada una indique si el ítem resultante debe quedar
`Rechazado` (terminal, sin intervención posible) o `En cuarentena` (requiere
intervención humana, potencialmente recuperable). Con ese mapeo puedo escribir
los tests Dado/Cuando/Entonces concretos e implementar RF-CI-006.

No se modificó ningún archivo de código para esta tarea. `TODO.md` queda con
T-02 marcada `- [?]`. Sigo con la siguiente tarea abierta que no dependa de
esta respuesta.

## 2026-08-21 · T-13 CI: gates AgentShield + security-review bloqueada — [?]

Tarea tomada de TODO.md: "T-13 CI: build + tests + arnés; gates AgentShield +
security-review" (primera `- [ ]` abierta; T-02 sigue `- [?]` sin resolver).

La parte "build + tests + arnés" ya existe en `.github/workflows/ci.yml`
(`build-kotlin` corre `./gradlew build`, `build-python` corre `uv sync` +
`pytest` sobre `eval-harness/`). Lo que falta y da nombre a la tarea son los
dos gates de seguridad, citados en `plan-ejecucion-agentica.md` líneas 59 y
135: "AgentShield + plugin security-review de Anthropic".

Revisé todo el repo buscando una referencia técnica concreta a **AgentShield**
(`grep -rli` sobre árbol completo, incluido `.github/`, `orquestador.sh`,
`CLAUDE-CODE-KICKOFF.md`, `agent-sandbox/`): no existe ningún paquete, acción
de GitHub, URL, binario ni archivo de configuración precedente. Las tres únicas
apariciones del nombre en todo el repo son en prosa
(`plan-ejecucion-agentica.md`, `STATE.md`, `TODO.md`/`orquestador.sh` — estas
últimas son solo el texto sembrado de la propia tarea T-13). El plan lo
describe como algo que el humano "ya tiene elegido" ("los gates de seguridad
que ya tienes elegidos"), es decir, es una decisión tomada fuera del repo cuya
integración concreta nunca se documentó aquí.

No puedo escribir el job de CI para este gate sin inventar: nombre de paquete,
referencia de acción de GitHub Marketplace (org/repo@versión), secretos
requeridos o forma de invocación. Adivinar cualquiera de esos datos equivale a
instalar una acción de terceros no verificada en el pipeline actuando como
"gate de seguridad" — exactamente el tipo de riesgo de cadena de suministro
que ese gate debería prevenir, no introducir. Es una ambigüedad real (decisión
de herramienta/infraestructura ya tomada por el humano pero no volcada al
repo), no procedimental.

El gate de **security-review** (plugin de Anthropic para Claude Code, ya
disponible como skill en este entorno) sí es identificable, pero decidí no
implementarlo solo y dejar el otro bloqueado: cablear la mitad de "gates
AgentShield + security-review" dejaría el job de CI en verde con apariencia de
gate de seguridad completo cuando en realidad falta la mitad, lo que es peor
que no tener ninguno. Prefiero resolver ambos gates juntos una vez esté claro
qué es AgentShield.

**Qué necesito para desbloquear:**
1. Referencia concreta de AgentShield: ¿es una GitHub Action de Marketplace
   (org/repo), un CLI/paquete a instalar, o un servicio SaaS con webhook? Con
   qué versión/tag fijar y qué secretos de CI requiere.
2. Confirmar que "plugin security-review de Anthropic" se refiere a la acción
   pública `anthropics/claude-code-security-review` (o indicar la referencia
   correcta) y qué secreto (p. ej. `ANTHROPIC_API_KEY`) debe configurarse en
   el repo de GitHub para que el job la use.

No se modificó `.github/workflows/ci.yml` ni ningún otro archivo de código
para esta tarea. `TODO.md` queda con T-13 marcada `- [?]`.

## 2026-08-21 · T-14 Empaquetado dual (P-02) bloqueada — [?]

Tarea tomada de TODO.md: "T-14 Empaquetado dual (P-02): mismos contenedores
como SaaS y como instalador appliance" (única tarea `- [ ]` abierta; T-02 y
T-13 siguen `- [?]` sin resolver).

Los esqueletos `deploy/docker-compose.saas.yml` y `deploy/docker-compose.onprem.yml`
(de F1) dejan escrito que los Dockerfiles reales "se añaden cuando cada
contexto tenga código (empezando por records-custodia y captura-ingesta)".
Esos dos contextos ya tienen código (T-01 a T-11), así que en principio T-14
tocaría escribir esos Dockerfiles y enchufarlos en ambos compose.

Verifiqué el estado real de esos módulos antes de escribir nada:

```
grep -rn "fun main\|SpringBootApplication\|FastAPI(" --include="*.kt" --include="*.py" .   → sin resultados
grep -rn "spring\|Spring" --include="*.toml" --include="*.kts" --include="*.properties" .  → sin resultados
```

Ningún contexto Kotlin tiene punto de entrada de aplicación: no hay `fun
main`, ninguna clase `@SpringBootApplication`, ni la dependencia de Spring
Boot declarada en ningún `build.gradle.kts` (ni el raíz, ni
`platform-kotlin`, ni `captura-ingesta`, ni `records-custodia`). Los tres son
bibliotecas Kotlin puras (`plugins { kotlin("jvm") }`) probadas con
`kotlin.test`, sin servidor HTTP, sin persistencia, sin ningún proceso de
larga duración. Esto contradice lo que registra `STATE.md` sobre F1.D1
("Kotlin/Spring Boot" como núcleo determinístico decidido): el stack se
decidió pero la dependencia de Spring nunca se añadió, y ningún RF
implementado hasta ahora (RF-CI-001/002/007/008, RF-RC-001/002/003/004/005/
006/009) requirió una capa de aplicación — todos se probaron llamando
directamente a las clases de dominio desde tests, sin necesidad de un
proceso ejecutable.

Un contenedor Docker necesita un `ENTRYPOINT`/`CMD` que ejecute algo; sin
punto de entrada, la única forma de escribir un Dockerfile "real" para estos
contextos es inventando de la nada una capa de aplicación: qué expone cada
contexto (¿API REST? ¿consumidor de cola? ¿librería embebida en otro
proceso?), en qué puerto, con qué framework de bootstrap, con qué mapeo de
persistencia hacia el `postgres` que ya declaran los compose. Ninguna spec de
contexto (`spec-captura-ingesta.md`, `spec-records-custodia.md`) define ese
límite de servicio — ambas describen reglas de dominio, no una interfaz de
proceso. Diseñar esa capa yo mismo violaría P-06 (SDD: "todo componente
determinístico se especifica antes de implementarse; la spec es la fuente de
verdad") y es exactamente el tipo de decisión de arquitectura que no me
corresponde inventar.

**Qué necesito para desbloquear:** definir, para al menos `captura-ingesta` y
`records-custodia`, el límite de servicio de cada contexto:
1. ¿Cada contexto se expone como su propio servicio HTTP (REST/gRPC), o los
   contextos determinísticos viven dentro de un único proceso Spring Boot
   (monolito modular) con un solo Dockerfile para el núcleo de records?
2. Si es HTTP: framework de bootstrap a usar (Spring Boot ya está en
   STATE.md, pero falta confirmarlo como decisión activa y no solo
   aspiracional) y el contrato mínimo de API por contexto (puede vivir en
   una spec nueva de infraestructura, no necesariamente en las specs de
   dominio ya escritas).
3. Persistencia: ¿los agregados de dominio ya escritos (p. ej.
   `CustodiaOriginales`, `BitacoraAuditoria`) se mapean a las tablas de
   `postgres` que ya declaran los compose, o eso es una tarea aparte?

Sin esa definición, cualquier Dockerfile que escriba sería una fachada vacía
(una imagen que empaqueta un jar sin nada que ejecutar) o una arquitectura
inventada sin respaldo de spec — ninguna de las dos es un empaquetado real
de P-02. No se modificó ningún `Dockerfile`, `docker-compose.*.yml` ni
archivo de código para esta tarea. `TODO.md` queda con T-14 marcada `- [?]`.
No queda ninguna tarea `- [ ]` abierta en TODO.md que no dependa de esta
respuesta: las tres restantes (T-02, T-13, T-14) están bloqueadas.

## 2026-08-21 · T-13 Resuelto (AgentShield diferido, security-review cableado)

Respuesta de Victor:
1. **AgentShield** — no hay decisión de herramienta tomada; el nombre en
   `plan-ejecucion-agentica.md` era un marcador, no una referencia concreta
   (confirmado: existen proyectos no relacionados con ese nombre). No se
   instala nada por nombre. Se deja un paso explícito y no bloqueante en el
   job de CI marcado PENDIENTE, en vez de omitirlo en silencio — reabre
   cuando evalúe candidatos reales.
2. **security-review** = `anthropics/claude-code-security-review`, secreto
   `ANTHROPIC_API_KEY` en el repo de GitHub. Cablear ahora.

Aplicado en `.github/workflows/ci.yml`: job `security-review` (dispara solo en
`pull_request`, usa `secrets.ANTHROPIC_API_KEY`) y job `agentshield-pendiente`
(step explícito, siempre verde, con el motivo por escrito).

## 2026-08-21 · T-14 Resuelto (servicios HTTP por contexto, Spring Boot, Postgres por contexto)

Respuesta de Victor:
1. Cada bounded context se expone como su propio proceso/servicio HTTP — no
   monolito modular. Empezar con `captura-ingesta` y `records-custodia`.
2. Framework: Spring Boot, confirmado como decisión activa (no solo
   aspiracional en STATE.md). El contrato mínimo de API por contexto vive en
   una spec nueva de infraestructura, `specs/spec-infra-servicios.md`, sin
   mezclarse con las specs de dominio ya escritas.
3. Persistencia: cada contexto mapea sus propios agregados a sus propias
   tablas en el `postgres` ya declarado en los compose; sin esquema
   compartido entre contextos.

Siguiente paso: escribir `specs/spec-infra-servicios.md` (P-06, spec antes de
código) y luego reabrir la implementación como tareas nuevas en TODO.md.

## 2026-08-23 · T-02 Resuelto (taxonomía cuarentena/rechazo para RF-CI-006)

Respuesta de Victor — mapeo de condición a rama terminal:

```
condición            → resultado        → intervención humana posible
─────────────────────────────────────────────────────────────────────
corrupto              → En cuarentena    → sí (reescaneo / confirmación manual)
ilegible               → En cuarentena    → sí (juicio de calidad)
formato no soportado   → Rechazado        → no (requiere artefacto nuevo o cambio de sistema)
```

Criterio: si un humano puede hacer algo con ese mismo artefacto dentro del
sistema actual para destrabarlo, va a `En cuarentena`; si la única salida es
un artefacto distinto (reenviado en otro formato) o un cambio de sistema
(soporte de formato nuevo), va a `Rechazado`. Sin umbral de severidad
adicional (p. ej. "corrupto leve/grave") — cada una de las tres condiciones
nombradas en RF-CI-006 mapea completa a una sola rama, sin inventar una
gradación que la spec no define.

Siguiente paso: escribir los tests Dado/Cuando/Entonces de RF-CI-006 contra
este mapeo e implementar T-02; marcar T-02 `- [x]` en TODO.md al cerrar.

## 2026-08-27 · V-02 (revisión acumulada de Codex, `65c3c43..HEAD`) — Ratificado

Hallazgo de Codex (ver `REVIEW.md`, revisión del rango T-22..T-36): T-23
implementó dos decisiones que `specs/006-seguridad-acceso/spec.md` §8 dejaba
explícitamente `[CLARIFICAR]` — el modelo de permisos (RBAC/ABAC/híbrido, "no
se fija sin dato real del design partner") y el proveedor de identidad (propio
vs. LDAP/SSO externo) — sin pasar por este archivo como exige la constitución
ante un `[CLARIFICAR]` real. Error de proceso propio: la spec pedía detenerse
y preguntar; implementé un valor por defecto razonado en su lugar.

Corregido preguntando directo a Victor (2026-08-27), en vez de seguir
construyendo sobre una decisión no ratificada:

1. **Modelo de permisos**: ratifica RBAC simple (`Rol` → lista de `Permiso`,
   cada uno con `accion` + `tipoRecurso` + `nivelClasificacionMaximo`) como
   decisión definitiva, no un valor por defecto provisional. Puede
   enriquecerse a futuro con atributos (ABAC — p. ej. dependencia
   organizacional) si el design partner lo requiere; no es una decisión que
   bloquee lo ya construido.
2. **Proveedor de identidad**: ratifica el almacén autoalojado (Postgres
   propio) como decisión definitiva. No hay proveedor externo (LDAP/AD/SSO)
   que integrar por ahora.

Siguiente paso: actualizar `specs/006-seguridad-acceso/spec.md` §8 quitando
los dos `[CLARIFICAR]` ya resueltos (mismo tratamiento que T-02 le dio a
`spec-captura-ingesta.md` §8), y corregir el hallazgo restante de la revisión
(V-01, P-08 en Normalización) como T-37.

## 2026-08-27 · T-40 Extracción — VETO de Codex sobre materialización de OCR — Ratificado

Contexto: Victor pidió continuar con Extracción en modo agéntico vía
`./orquestador.sh loop` (esta vez con Codex revisando cada commit, como en
T-01..T-22). En la primera iteración, la instancia headless implementó
`contexts/extraccion/dominio.py` (T-40) siguiendo literalmente el criterio
Dado/Cuando/Entonces de RF-EX-004 tal como estaba escrito: recibir un
resultado de OCR movía el `TextoExtraido` directo a `Extraído`, con su
contenido y calidad, sin ningún paso humano intermedio.

Codex vetó el commit (`dd97fb4`) citando P-01: "nada probabilístico escribe
estado; debe cruzar la capa anticorrupción como Sugerencia y solo una
decisión humana puede materializarlo". La propia spec
(`specs/002-extraccion/spec.md` §1, versión anterior a esta resolución)
argumentaba lo contrario — que el texto extraído no es estado archivístico
(no es serie/subserie/metadato) y por eso puede quedar exento de la capa
Sugerencia+decisión que sí exige `spec-records-custodia.md` §4, gobernándose
en su lugar solo con el gate de EDD a nivel de componente (P-05) — pero el
propio texto admitía que esa lectura "es razonable pero no está escrita...
como regla explícita". No era, pues, una decisión ratificada; era un
argumento razonable sin resolver, exactamente el tipo de ambigüedad real que
la constitución pide escalar antes de construir sobre ella.

Pregunté a Victor con dos caminos: (a) exigir confirmación humana explícita
del resultado de OCR antes de materializar, mismo patrón que
RF-RC-004/RF-NO-004 en el resto del proyecto (más costoso: un humano
interviene en cada extracción vía OCR, no solo en las de baja confianza); (b)
ratificar el diseño original de la spec, dejando el enrutamiento por calidad
(RF-EX-006) como único control humano, posterior a la materialización.

**Decisión de Victor: (a) — exigir confirmación humana.** Se corrigió
`dominio.py`: `recibir_resultado_ocr` ahora solo adjunta el resultado
(`TextoExtraido.resultado_ocr`), sin tocar el estado; nueva función
`confirmar_extraccion(texto, actor, fecha)` (RF-EX-011, nueva) es la única
que materializa `Extraído`, usando el contenido/calidad del resultado
adjunto — mismo patrón de dos pasos que
`recibir_sugerencia_de_limites`/`confirmar_limites` en Normalización.
`specs/002-extraccion/spec.md` actualizada: §1 documenta la resolución (ya no
argumenta la excepción), RF-EX-004 revisado (el resultado de OCR ya no
materializa por sí solo) y RF-EX-011 añadido con su Dado/Cuando/Entonces, §7
con la fila de trazabilidad nueva. El gate de EDD a nivel de componente
(P-05) y el enrutamiento por calidad (RF-EX-006) siguen vigentes como
controles complementarios, no sustitutos de la confirmación.

De paso se corrigió un segundo hallazgo, no bloqueante pero señalado por
Codex en la misma revisión: `marcar_cuarentena_o_rechazo` no rechazaba
transiciones desde un estado ya terminal (`Extraído`/`Rechazado`/`En
cuarentena`), pese a que la spec §3 las declara terminales. Ahora exige
`Pendiente de extracción` como precondición, con test nuevo.

29/29 tests en `contexts/extraccion` (25 originales + 4 nuevos: 3 de
`confirmar_extraccion`, 1 de la precondición corregida). Pendiente: comitear
esta corrección, pedirle a Codex que revise el nuevo commit contra el mismo
diff, y solo entonces retomar `./orquestador.sh loop` para T-41 en adelante.

## 2026-08-27 · T-40 Extracción — segunda vuelta de Codex, VETO mantenido — Corregido (no requirió nueva decisión de Victor)

Codex revisó el commit de corrección de arriba (`e623ad6`) y mantuvo el VETO
con un motivo distinto: aplazar la materialización con `confirmar_extraccion`
no bastaba, porque lo que `recibir_resultado_ocr` adjuntaba al agregado
(`ResultadoOcr`, sin `evidencia`) seguía sin tener forma de `Sugerencia` —
P-01 exige que la salida probabilística cruce la capa anticorrupción *como
Sugerencia*, no solo que una decisión humana la materialice después. También
señaló un defecto de P-08: el evento de recepción usaba un sentinel
(`estado_posterior="RESULTADO_OCR_RECIBIDO"`) que no es un valor real de
`EstadoTextoExtraido`.

Esto NO se escaló a Victor como una nueva decisión de negocio: es una
corrección de consistencia con el patrón ya ratificado en la ronda anterior
(toda salida probabilística de este proyecto cruza como una `Sugerencia*` con
`evidencia`, mismo shape en los tres contextos que ya lo hacen). Corregido
directamente: `ResultadoOcr` renombrado a `SugerenciaOcr` con `evidencia:
list[str]` añadido (mismo shape que `SugerenciaDeLimites` en Normalización y
`Sugerencia` en records-custodia, con `contenido` añadido porque eso es lo
que una sugerencia de OCR propone); `recibir_resultado_ocr` renombrado a
`recibir_sugerencia_ocr`; el evento de recepción ahora usa
`estado_anterior=estado_posterior=texto.estado.value` (honesto: la recepción
no cambia el estado) en vez del sentinel — y se corrigió el mismo patrón en
`determinar_soporte`, que tenía el defecto idéntico desde el primer commit,
por consistencia dentro del mismo archivo.

`specs/002-extraccion/spec.md` actualizada: §1 con una segunda nota de
resolución, §2 (lenguaje ubicuo) con el término "Sugerencia de OCR", RF-EX-004
y RF-EX-011 reescritos con vocabulario de sugerencia en vez de "resultado".
29/29 tests en el módulo. Pendiente: comitear y pedirle a Codex una tercera
revisión antes de retomar `./orquestador.sh loop`.