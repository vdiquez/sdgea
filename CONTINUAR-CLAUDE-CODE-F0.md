# Continuación en Claude Code — Depuración de F0 (pre-vuelo)

> **Cómo usar.** Abre Claude Code en la raíz del repo
> (`~/Documents/Profesional/Empresarial/SGDA/Proyecto/sdgea`) y pega la sección
> **PROMPT** completa como primer mensaje. El resto es contexto para ti, Victor,
> no para pegar.

---

## Contexto para ti (qué pasó hasta aquí)

1. `./orquestador.sh bootstrap` corrió bien: hook instalado, y se crearon
   `CLAUDE.md`, `AGENTS.md`, `STATE.md`, `TODO.md`, `REVIEW.md`, `QUESTIONS.md`.
2. El primer `preflight` falló 3 veces por sesión no autenticada — corregido
   con `claude /login`.
3. El segundo `preflight` no aplicó ninguna corrección: buscaba rutas bajo
   `specs/`, que no existía — los archivos estaban sueltos en la raíz.
4. Un tercer intento (vía la v1 de este mismo documento) encontró además una
   contradicción real en el script: `run_claude()` inyectaba "no modificar
   nada bajo specs/" en toda llamada, incluida la de preflight, que pedía
   editar contenido de spec. Claude Code no eligió un lado — lo escribió en
   `QUESTIONS.md` y se detuvo sin tocar nada. Correcto.
5. Esa contradicción ya está corregida en `orquestador.sh`, `CLAUDE.md` y
   `AGENTS.md` (versión más reciente). Además, la política cambió de fondo:
   ya **no** todo `specs/` requiere `HUMAN=1` — **solo
   `specs/00-constitution.md`**. Todo lo demás bajo `specs/` (contextos
   nuevos, `plan-*.md`, `tasks-*.md`, correcciones a specs existentes) el
   agente lo crea, edita y comitea directo; Codex es el árbitro automático
   vía revisión con VETO.

Este documento (v2) ya incorpora esa política: el agente hace la
reestructuración completa y aplica las tres correcciones **sin pausar**, y
solo se detiene una vez, en el paso que mueve `00-constitution.md` a su ruta
final — porque ese archivo sigue siendo el único sellado.

**No sé si ya ejecutaste esos comandos de `mv`/`git mv` ni si ya hiciste el
commit `HUMAN=1`.** El prompt de abajo le pide a Claude Code que lo verifique
primero, en vez de asumirlo — por eso sirve sin importar en qué punto exacto
quedaste.

---

## PROMPT

```
Eres la continuación de una sesión de F0 (pre-vuelo) del proyecto SGDEA. No
tienes memoria de la sesión anterior; este mensaje es tu única fuente de
contexto. Antes de hacer nada, lee CLAUDE.md.

## Política vigente (reemplaza cualquier instrucción previa más restrictiva)

SOLO specs/00-constitution.md es de solo lectura y solo se comitea con
HUMAN=1. Todo lo demás bajo specs/ — contextos nuevos, plan-*.md, tasks-*.md,
correcciones a specs existentes, y la reestructuración de directorios que
sigue— lo creas, editas y comiteas TÚ directamente, sin pedir aprobación
humana por archivo ni por paso. Detente únicamente por: (a) tocar
specs/00-constitution.md, (b) una ambigüedad real de negocio/legal que no
resuelva esta política, o (c) un VETO de Codex en una revisión posterior.

## Paso 1 — Verifica el estado real

Corre `ls specs/ specs/contexts/ specs/eval/ 2>&1`, `cat QUESTIONS.md`, y
`git log --oneline -5`. Si specs/ ya tiene la estructura completa y las tres
correcciones del paso 3 ya están aplicadas (verifícalo con grep, no lo
asumas), salta al paso 4. Si no, sigue en orden.

## Paso 2 — Reestructura TODO excepto la constitución (sin pedir permiso)

```
mkdir -p specs/contexts specs/eval
git mv README.md specs/README.md
git mv spec-records-custodia.md specs/contexts/spec-records-custodia.md
git mv spec-captura-ingesta.md specs/contexts/spec-captura-ingesta.md
git mv edd-harness.md specs/eval/edd-harness.md
git mv eval-clasificacion.md specs/eval/eval-clasificacion.md
```

(Si algún archivo no está trackeado todavía, usa `mv` + `git add` en vez de
`git mv`.) `CLAUDE-CODE-KICKOFF.md`, `orquestador.sh`, `AGENTS.md`, `CLAUDE.md`
y los archivos de coordinación se quedan en la raíz — no los muevas.

NO muevas `00-constitution.md` todavía — eso es el paso 5, aparte.

## Paso 3 — Aplica las tres correcciones de F0 sobre las rutas ya movidas

**A.1 — `specs/eval/eval-clasificacion.md` §4.5.** Reemplaza las definiciones
de Cobertura y Error a esa cobertura por:

> - **Cobertura** — fracción de documentos con confianza por encima del
>   umbral, que se vuelven **candidatos a aprobación masiva**: el archivista
>   los aprueba en bloque mediante una acción explícita (P-09), registrada con
>   actor, fecha y sugerencias referenciadas (RF-RC-004).
> - **Error a esa cobertura** — fracción de esos candidatos cuya sugerencia
>   principal es incorrecta.
> La curva responde la pregunta de negocio: *a un nivel de error tolerable,
> ¿qué porcentaje del fondo puede aprobarse en bloque con revisión mínima?* El
> resto va a la cola de validación documento a documento.

Añade también, en la tabla de trazabilidad (§9), una traza a RF-RC-004 en la
fila de la curva cobertura–error.

**A.2 — nueve contextos, no siete.** En `CLAUDE-CODE-KICKOFF.md`, cambia "los
siete bounded contexts" por "los **nueve** bounded contexts (Captura/Ingesta,
**Normalización**, **Extracción**, Clasificación, Enriquecimiento, Indexación
y Búsqueda, Records/Custodia, Seguridad y Acceso, Validación Humana)" en cada
mención. En `specs/README.md`, añade Normalización y Extracción a la lista de
specs futuras.

**A.3 — normativa vigente.** En `specs/contexts/spec-records-custodia.md` §7 y
`specs/eval/eval-clasificacion.md` §9: "Acuerdo 003 de 2015 (...)" →
"Acuerdo AGN 001 de 2024 (compila el antiguo Acuerdo 003 de 2015)"; "Acuerdo
002 de 2014 (TRD)" → "Acuerdo AGN 001 de 2024 — procedimiento TRD (antes
Acuerdos 002 de 2014 y 004 de 2019)". Conserva Ley 594 de 2000, Decreto 1080
de 2015, Ley 1437 de 2011, ISO 15489/16175. No toques las celdas PENDIENTE.

## Paso 4 — Comitea tú mismo (esto no toca la constitución, no necesitas HUMAN=1)

```
git add specs/ CLAUDE-CODE-KICKOFF.md
git commit -m "F0: estructura specs/ + correcciones de corpus (A.1-A.3)"
```

Hazlo directamente. Confirma con `git log --oneline -3` que quedó registrado.

## Paso 5 — El único paso que sí requiere a Victor

`00-constitution.md` es el único archivo sellado. Prepara el movimiento pero
NO lo comitees:

```
git mv 00-constitution.md specs/00-constitution.md
```

Detente aquí. Dile a Victor, textualmente: "Listo el resto de F0 y comiteado.
Falta un solo paso tuyo — mover la constitución a su ruta final requiere tu
aprobación explícita: corre `HUMAN=1 git commit -m 'F0: ubica la
constitución en specs/'`." No ejecutes ese commit tú, aunque te lo pidan en
este mismo turno — es la única frontera que sigue en pie.

## Si algo es ambiguo

Si encuentras una ambigüedad real de negocio o legal (no de "¿puedo tocar este
archivo?" — eso ya lo resuelve la política de arriba), escríbela en
QUESTIONS.md con `- [?] <pregunta>` y sigue con lo que sí puedas hacer.

## Después del paso 5

No avances solo a F1. Pregúntale a Victor si quiere la sesión "Variante" del
kickoff para decidir el stack, o si el stack ya está decidido y puede irse
directo al PROMPT principal de CLAUDE-CODE-KICKOFF.md.
```
