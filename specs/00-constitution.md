# 00 · Constitución del Proyecto

**Producto:** Capa AI-native de clasificación e indexación documental
**Estado:** Borrador — Etapa 0
**Alcance:** Documento rector. Toda spec, todo plan y toda implementación deben
ser consistentes con esta constitución. Ante conflicto, la constitución prevalece.

---

## El objeto a proteger

El producto manipula **documentos de archivo**. Un documento de archivo no es un
archivo cualquiera: es evidencia. El núcleo del sistema existe para preservar
cuatro propiedades sobre cada documento a lo largo de todo su ciclo de vida:

- **Autenticidad** — es lo que dice ser y fue creado por quien dice haberlo creado.
- **Fiabilidad** — su contenido representa fielmente la actuación que documenta.
- **Integridad** — está completo y no ha sido alterado de forma no autorizada.
- **Disponibilidad** — puede localizarse, recuperarse y leerse cuando se necesita.

Toda decisión de diseño se evalúa contra estas cuatro propiedades. La inteligencia
artificial del producto sirve a la **disponibilidad** y acelera la **clasificación**;
nunca debe poner en riesgo la autenticidad o la integridad.

---

## Principios

### P-01 · La IA propone, el motor de records dispone
La inteligencia artificial es probabilística; el documento de archivo es evidencia
y exige determinismo. La IA **nunca** escribe directamente sobre el estado de un
documento o expediente. Emite *sugerencias* —clasificación, metadatos, agrupamiento—
que portan modelo, evidencia y confianza. Una sugerencia solo se materializa
mediante una *decisión humana* explícita. Esta frontera es física en la
arquitectura: existe como capa anticorrupción entre los contextos probabilísticos
y el núcleo de records.

### P-02 · Un solo código base, dos modos de despliegue
SaaS y appliance on-premise se construyen del mismo código base. El appliance es
ese mismo conjunto de contenedores empaquetado como instalador. No existe una
"versión on-prem" separada. Toda funcionalidad nace compatible con ambos modos.

### P-03 · Capas de abstracción sobre toda capacidad externa
Ninguna capacidad crítica —almacenamiento de objetos, OCR, embeddings, inferencia
LLM, índice vectorial, índice léxico— se consume directamente. Cada una se accede
a través de una interfaz propia con al menos dos implementaciones intercambiables
(gestionada para SaaS, autoalojada para on-premise). El código de orquestación
desconoce cuál implementación está activa.

### P-04 · Trazabilidad regulatoria de primera clase
Todo requisito del núcleo de records cita su fuente normativa. La trazabilidad no
es documentación posterior: es parte de la spec desde su creación. La unión de las
tablas de trazabilidad es la matriz de conformidad y se mantiene viva junto al código.

### P-05 · Evaluación antes que código, para lo probabilístico (EDD)
Todo componente probabilístico —clasificación, extracción, recuperación, Q&A— se
desarrolla contra un arnés de evaluación con métricas y umbrales definidos **antes**
de escribir el componente. Ningún componente probabilístico se libera sin pasar sus
*gates*. Los sets de evaluación se construyen con dato real de cliente, nunca sintético.

### P-06 · Especificación antes que código, para lo determinístico (SDD)
Todo componente determinístico —ingesta, núcleo de records, indexación, seguridad,
validación— se especifica antes de implementarse. La spec es la fuente de verdad;
el código se escribe contra ella. Las specs son modulares por contexto y se
entregan dentro de cadencia iterativa: SDD no es un gran documento por adelantado.

### P-07 · Cortes verticales, nunca capas horizontales
Cada incremento de producto atraviesa el pipeline completo de punta a punta sobre
un alcance angosto. No se construye "toda la ingesta" y luego "toda la
clasificación": se construye una rebanada delgada y profunda, y se ensancha.

### P-08 · Auditabilidad total e inmutable
Toda transición de estado de un documento o expediente —custodia, recepción de
sugerencia, decisión humana, cálculo de retención, acceso— genera un evento de
auditoría inmutable, atribuible (actor humano o de sistema), fechado y con estado
anterior y posterior. La bitácora es de solo anexado y a prueba de manipulación.

### P-09 · Humano-en-el-loop como producto, no como respaldo
La validación humana no es un mecanismo de emergencia: es funcionalidad central. Las
colas de revisión por confianza, las acciones masivas y la captura de correcciones
son producto de primer nivel. Cada corrección alimenta el flywheel de datos.

### P-10 · Soberanía del dato del cliente
En modo on-premise, el dato del cliente y la inferencia que lo procesa no salen de
su frontera de infraestructura. El producto debe ser plenamente funcional sin
conectividad saliente, incluyendo entornos aislados.

---

## Disciplina de alcance (qué NO se construye en las Etapas 0–4)

- Motor de BPM / workflow completo.
- Firma electrónica y estampado cronológico desde cero — se integran.
- Motor de OCR propio desde cero — se usa o se ajusta uno existente.
- Funciones genéricas de ECM que no diferencian.
- Aplicaciones móviles tempranas.
- Ciclo de vida de records completo (disposición final ejecutada, transferencias):
  es Etapa 5.

---

## Cómo se usa esta constitución

Es contexto permanente para el equipo y para los agentes de implementación. Cada
spec declara qué principios la gobiernan de forma destacada. Cada plan técnico se
revisa contra ella. Un plan o una implementación que contradiga un principio se
rechaza o exige una enmienda explícita y fechada de la constitución.
