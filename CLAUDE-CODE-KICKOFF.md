# Prompt de arranque para Claude Code — Andamiaje del repositorio

> **Cómo usar este archivo.** Es el prompt inicial para la primera sesión de Claude
> Code del proyecto. Coloca el directorio `specs/` completo en la raíz del repo,
> abre Claude Code en esa raíz y pega la sección **"PROMPT"** de abajo como primer
> mensaje. El resto del archivo es contexto para ti, el humano, no para pegar.
>
> Antes de empezar, decide los puntos marcados `‹DECIDIR›` en la sección
> "Preparación". Claude Code no debe inventarlos.

---

## Preparación (decisiones del humano antes de la sesión)

Estos puntos no los puede decidir el agente. Defínelos y sustitúyelos en el prompt:

- `‹LENGUAJE›` — lenguaje(s) de implementación del proyecto.
- `‹GESTOR›` — gestor de paquetes / herramienta de build.
- `‹EMPAQUETADO›` — tecnología de contenedores y de empaquetado del appliance.
- `‹CI›` — plataforma de integración continua.

Si aún no quieres comprometerte con el stack, puedes pedirle al agente que primero
**proponga** opciones (ver "Variante" al final) y decidir después. Pero el código no
se escribe hasta que el stack esté fijado.

---

## PROMPT

```
Eres el ingeniero que arranca un proyecto nuevo. Tu tarea en esta sesión es el
ANDAMIAJE del repositorio: estructura, contratos y esqueletos. NO vas a implementar
lógica de negocio todavía.

## Contexto que debes leer primero, en este orden

1. .specify/memory/constitution.md — los principios no negociables del proyecto. Gobiernan
   todo lo que hagas. Si algo que vas a hacer contradice un principio, detente y
   pregúntame.
2. specs/README.md — cómo funciona Spec-Driven Development en este repo.
3. specs/contexts/spec-records-custodia.md — bounded context del núcleo determinístico.
4. specs/contexts/spec-captura-ingesta.md — bounded context de entrada al pipeline.
5. specs/eval/edd-harness.md — diseño del arnés de evaluación.
6. specs/eval/eval-clasificacion.md — spec de evaluación de un componente.

Estas specs son la FUENTE DE VERDAD. No las contradigas y no las "mejores" por tu
cuenta. Si encuentras una ambigüedad o un vacío, NO lo resuelvas inventando: añádelo
a una lista de preguntas y pregúntame al final.

## Restricciones de stack (decididas, no negociables en esta sesión)

- Lenguaje(s): ‹LENGUAJE›
- Gestor de paquetes / build: ‹GESTOR›
- Contenedores / empaquetado del appliance: ‹EMPAQUETADO›
- Integración continua: ‹CI›

## Principios que más afectan esta sesión

- P-02: UN SOLO código base; el appliance es los mismos contenedores empaquetados.
  No crees una "variante on-prem". 
- P-03: capa de abstracción sobre TODA capacidad externa (almacenamiento de objetos,
  OCR, embeddings, inferencia LLM, índice vectorial, índice léxico). Cada una es una
  interfaz con, como mínimo, dos implementaciones intercambiables: una para SaaS y
  una autoalojada para on-premise. El código de orquestación NO sabe cuál está activa.
- P-01: la frontera entre lo probabilístico y lo determinístico es física. Habrá una
  capa anticorrupción; en esta sesión solo creas su ubicación estructural, no su
  lógica.
- P-07: la estructura debe permitir cortes verticales de punta a punta, no fomentar
  capas horizontales.

## Lo que SÍ debes hacer en esta sesión

1. Proponer la estructura de directorios del repositorio ANTES de crear nada.
   Preséntamela y espera mi aprobación. Debe reflejar los **nueve** bounded contexts
   nombrados en las specs (Captura/Ingesta, **Normalización**, **Extracción**,
   Clasificación, Enriquecimiento, Indexación y Búsqueda, Records/Custodia, Seguridad
   y Acceso, Validación Humana), aunque la mayoría queden como esqueletos vacíos por
   ahora.

2. Tras mi aprobación, andamiar el repositorio:
   - El directorio specs/ ya existe; intégralo, no lo muevas.
   - Estructura de los **nueve** bounded contexts (esqueletos; solo Records/Custodia y
     Captura/Ingesta se desarrollarán pronto).
   - Definir, COMO CÓDIGO, las interfaces de las seis capas de abstracción de P-03.
     Solo las interfaces / contratos y un esqueleto de implementación por modo
     (SaaS y on-premise) que aún no hace nada. Sin lógica real.
   - Ubicación estructural de la capa anticorrupción entre los contextos
     probabilísticos y Records/Custodia.
   - Esqueleto del arnés de evaluación como módulo ejecutable: debe poder cargar un
     set patrón, correr un componente y emitir una boleta de resultados. Sin
     componentes reales todavía; un componente ficticio de prueba basta para validar
     que el arnés corre de punta a punta.
   - Configuración de integración continua que: construya el proyecto, corra las
     pruebas y ejecute el arnés de evaluación.
   - Un README raíz que explique la estructura y cómo arrancar.
   - Andamiaje de empaquetado para ambos modos de despliegue (P-02).

3. Mantén los cambios pequeños y revisables. Trabaja en incrementos y muéstrame cada
   incremento antes de seguir. No generes todo el repositorio en un solo paso.

4. Al terminar, entrégame: un resumen de lo creado, la lista de preguntas/ambigüedades
   que encontraste en las specs, y la propuesta de los siguientes pasos.

## Lo que NO debes hacer en esta sesión

- NO implementar lógica de negocio de ningún bounded context.
- NO implementar ninguna de las seis capacidades externas; solo sus interfaces.
- NO implementar ningún componente probabilístico real (clasificación, OCR, etc.).
- NO elegir el stack por tu cuenta: usar el que está fijado arriba.
- NO inventar valores de umbrales de evaluación: las specs dicen que se calibran en
  la Etapa 1.
- NO inventar referencias normativas: las tablas de trazabilidad tienen celdas
  PENDIENTE a propósito; déjalas así.
- NO tomar decisiones de arquitectura que contradigan la constitución; ante la duda,
  pregunta.

## Forma de trabajo

Primero confírmame que leíste las seis specs y resúmeme en pocas líneas el principio
P-01 y el propósito del bounded context Records/Custodia, para que yo verifique que
el contexto se cargó bien. Luego propón la estructura de directorios y espera mi
aprobación. No escribas código antes de eso.
```

---

## Por qué el prompt está hecho así

- **Carga la constitución primero y la pone por encima de todo.** Es el mecanismo de
  SDD: el agente recibe los principios como contexto permanente y restrictivo.
- **Pide un resumen de verificación antes de actuar.** Confirma que el agente
  realmente cargó las specs y no va a alucinar sobre ellas.
- **Exige aprobar la estructura antes de crear archivos.** Evita que el agente genere
  un repositorio entero en una dirección equivocada.
- **Acota el alcance con una lista explícita de NO.** El riesgo con un agente capaz
  es que "se entusiasme" e implemente lógica de negocio sin specs aprobadas. La
  sesión de andamiaje produce contratos y esqueletos, no lógica.
- **Convierte las ambigüedades en preguntas, no en invenciones.** Las celdas
  PENDIENTE y los `[CLARIFICAR]` de las specs son deliberados; el agente debe
  respetarlos.
- **Trabajo incremental y revisable.** Cada incremento se muestra antes de seguir:
  control humano sobre un proceso rápido.

## Variante: si aún no has decidido el stack

Si quieres que el agente te ayude a decidir el stack, antepón esta tarea como una
sesión previa y separada:

```
Antes de andamiar nada: lee .specify/memory/constitution.md y las dos specs de contexto.
Propón 2 o 3 opciones de stack (lenguaje, build, contenedores, CI) coherentes con la
constitución — en especial con P-02 (un solo código base para SaaS y on-premise),
P-03 (capas de abstracción) y P-10 (operación on-premise sin conectividad saliente,
incluso en entornos aislados). Para cada opción, indica ventajas, riesgos y su
encaje con esos principios. NO escribas código ni estructura de repositorio: solo la
comparación, para que yo decida.
```

Una vez decidas, vuelves al PROMPT principal con los `‹...›` ya sustituidos.

## Después de esta sesión

El andamiaje cierra el trabajo técnico de la Etapa 0. Queda un único punto en el
camino crítico, y no es de software: **cerrar el design partner** y construir con su
archivista el set patrón etiquetado descrito en `specs/eval/edd-harness.md`. Sin ese
dato real, el arnés no tiene contra qué medir y la Etapa 1 (el primer corte vertical)
no puede arrancar — es el criterio de salida de la Etapa 0.
