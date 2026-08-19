# `/specs` — Especificaciones del proyecto (SDD)

Este directorio es la **fuente de verdad** del producto. Bajo Spec-Driven Development
(SDD), las especificaciones no son notas previas: son el artefacto que el equipo y
los agentes de implementación (Claude Code) consumen para construir. El código se
escribe **contra** estas specs, no antes que ellas.

## Estructura

```
specs/
├── README.md                       ← este archivo
├── 00-constitution.md              ← principios no negociables (gobiernan TODO)
└── contexts/
    ├── spec-records-custodia.md    ← bounded context determinístico (núcleo de records)
    └── spec-captura-ingesta.md     ← bounded context de entrada al pipeline
```

A medida que el roadmap avance se agregarán las specs de los demás bounded contexts
(Normalización, Extracción, Clasificación, Enriquecimiento, Indexación y Búsqueda,
Seguridad y Acceso, Validación Humana).

## Las tres capas de un contexto

Cada bounded context se especifica en tres niveles. La Etapa 0 produce solo el
**nivel 1 (spec)**; los niveles 2 y 3 se generan al iniciar la implementación de
cada contexto (típicamente ya dentro de Claude Code).

| Nivel | Archivo | Responde | Cuándo |
|-------|---------|----------|--------|
| 1 · Spec | `spec-*.md` | *Qué* y *por qué*: dominio, contrato, requisitos, trazabilidad | Etapa 0 |
| 2 · Plan | `plan-*.md` | *Cómo*: decisiones técnicas, stack, estructura de módulos | Al iniciar el contexto |
| 3 · Tasks | `tasks-*.md` | Desglose ejecutable de tareas | Al iniciar el contexto |

## Convención de identificadores de requisito

`RF-<CTX>-NNN` (requisito funcional) y `RNF-<CTX>-NNN` (no funcional), donde `<CTX>`
es el código del contexto: `RC` = Records/Custodia, `CI` = Captura/Ingesta.
Cada requisito es **verificable**: lleva criterios de aceptación en formato
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
