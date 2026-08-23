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