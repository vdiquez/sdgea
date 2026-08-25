# `/specs` — Especificaciones del proyecto (SDD)

Este directorio es la **fuente de verdad** del producto. Bajo Spec-Driven Development
(SDD), las especificaciones no son notas previas: son el artefacto que el equipo y
los agentes de implementación (Claude Code) consumen para construir. El código se
escribe **contra** estas specs, no antes que ellas.

## Estructura

```
specs/
├── README.md                       ← este archivo
├── 00-constitution.md              ← stub → ../.specify/memory/constitution.md
├── spec-infra-servicios.md         ← infraestructura transversal (no es de dominio, ver abajo)
├── contexts/
│   ├── spec-records-custodia.md    ← bounded context determinístico (núcleo de records)
│   └── spec-captura-ingesta.md     ← bounded context de entrada al pipeline
└── 001-normalizacion/
    └── spec.md                     ← bounded context híbrido (SDD + un componente EDD)
```

Records/Custodia y Captura/Ingesta se escribieron a mano antes de instalar Spec Kit y
viven en `contexts/spec-<nombre>.md`. Desde Normalización en adelante, cada bounded
context nuevo se crea con `/speckit-specify` en su propia carpeta numerada
`specs/NNN-<nombre>/spec.md` (con `.specify/feature.json` apuntando a la carpeta
activa) — es el mecanismo que espera `/speckit-plan` y `/speckit-tasks` para
continuar la implementación de ese contexto; no pueden apuntar directo a
`contexts/*.md`. El **contenido** de cada spec sigue el mismo rigor sin importar la
carpeta: código de contexto, RF-<CTX>-NNN con Dado/Cuando/Entonces, trazabilidad
regulatoria y `[CLARIFICAR]` explícitos — ver la sección siguiente.

A medida que el roadmap avance se agregarán las specs de los demás bounded contexts
(Extracción, Clasificación, Enriquecimiento, Indexación y Búsqueda, Seguridad y
Acceso, Validación Humana), cada una en su propia `specs/NNN-<nombre>/`.

`spec-infra-servicios.md` vive en la raíz de `specs/`, no en `contexts/`: define
*cómo se empaqueta y expone* el dominio ya especificado (servicios HTTP,
framework de bootstrap, mapeo de persistencia), no reglas de negocio nuevas —
por eso no se mezcla con las specs de dominio.

## Las tres capas de un contexto

Cada bounded context se especifica en tres niveles. La Etapa 0 produce solo el
**nivel 1 (spec)**; los niveles 2 y 3 se generan al iniciar la implementación de
cada contexto (típicamente ya dentro de Claude Code).

| Nivel | Archivo | Responde | Cuándo |
|-------|---------|----------|--------|
| 1 · Spec | `contexts/spec-*.md` (los dos escritos a mano) o `NNN-<nombre>/spec.md` (los creados con `/speckit-specify`) | *Qué* y *por qué*: dominio, contrato, requisitos, trazabilidad | Etapa 0 |
| 2 · Plan | `plan-*.md` | *Cómo*: decisiones técnicas, stack, estructura de módulos | Al iniciar el contexto |
| 3 · Tasks | `tasks-*.md` | Desglose ejecutable de tareas | Al iniciar el contexto |

## Convención de identificadores de requisito

`RF-<CTX>-NNN` (requisito funcional) y `RNF-<CTX>-NNN` (no funcional), donde `<CTX>`
es el código del contexto: `RC` = Records/Custodia, `CI` = Captura/Ingesta, `NO` =
Normalización. Cada requisito es **verificable**: lleva criterios de aceptación en formato
*Dado / Cuando / Entonces*, que son la base de las pruebas (SDD) o de los sets de
evaluación (EDD).

## Estados de un documento de spec

`Borrador` → `En revisión` → `Aprobada` → `Implementada`. Las specs de la Etapa 0
nacen en `Borrador` y solo pasan a `Aprobada` tras ser validadas con el archivista
del design partner.

## Trazabilidad regulatoria

Todo requisito del núcleo de records traza a una fuente normativa. La columna
*Referencia específica* de cada tabla de trazabilidad queda **PENDIENTE** hasta
fijarla contra el documento oficial correspondiente — no se inventan números de
cláusula. Una vez fijada, la unión de todas las tablas constituye la **matriz de
conformidad** que se presenta en RFPs y auditorías.

## Cómo consume esto Claude Code

Al pasar a implementación: este directorio se versiona dentro del repo, junto al
código. El agente recibe la constitución como contexto permanente y la spec del
contexto a implementar como objetivo. La constitución es restrictiva: ningún plan
ni implementación puede contradecirla.
