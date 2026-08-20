OK: cierre de F1 sin objeciones

# Revisión de F1 — Fundación

Alcance revisado: contenido efectivo de los commits `af24e22`, `a890e83` y
`37a6125`, contra `AGENTS.md`, la constitución vigente en
`.specify/memory/constitution.md` y el estado de F1. No se evaluaron los cambios
sin confirmar ajenos a esos commits.

## Estructura y P-01 / P-02

- Están presentes los nueve bounded contexts bajo `contexts/`: Captura/Ingesta,
  Normalización, Extracción, Clasificación, Enriquecimiento, Indexación y Búsqueda,
  Records/Custodia, Seguridad y Acceso y Validación Humana. Son esqueletos: no hay
  lógica de dominio ni escritura de estado.
- No hay código que permita a los contextos probabilísticos escribir el estado de
  `records-custodia`. La capa anticorrupción no se implementó prematuramente: su
  ubicación y contrato se declaran donde corresponde, en
  `specs/contexts/spec-records-custodia.md` §4; traduce entradas probabilísticas a
  `Sugerencia` y conserva la decisión humana como única transición de estado.
- Los compose SaaS y on-prem describen el mismo conjunto de contextos/código base;
  solo varía la implementación detrás de P-03. No existe una variante de código
  on-prem separada.

## P-03 y P-10

- Las seis capacidades externas están detrás de contratos propios:
  `ObjectStorage`, `OCR`, `Embeddings`, `LLMInference`, `VectorIndex` y
  `LexicalIndex`. Cada una tiene esqueletos `Managed` y `SelfHosted`.
  `ObjectStorage` está también en la plataforma Kotlin; las seis están disponibles
  en la plataforma Python, que concentra las capacidades de la capa probabilística.
- Las implementaciones no seleccionan proveedor ni realizan trabajo: Kotlin usa
  `TODO()` y Python `NotImplementedError`. La mención de MinIO en el compose
  on-prem es explícitamente un ejemplo candidato, no una decisión ni una
  integración.
- La configuración on-prem exige explícitamente capacidades autoalojadas y sin
  conectividad saliente. El análisis del contenido de los esqueletos no encontró
  clientes HTTP, URLs ni dependencias de proveedores; por tanto no hay supuesto de
  salida de red en el código self-hosted.

## P-05 / P-06 y honestidad del arnés

- `eval-harness/` es infraestructura determinística: carga registros, invoca el
  protocolo del componente y calcula `total`, `aciertos`, `exactitud` y detalle.
  `ComponenteFicticio` se identifica expresamente como FICTICIO y solo devuelve la
  primera palabra normalizada; no hay modelo real ni OCR/clasificador disfrazado.
- Las pruebas no están amañadas: el primer caso suministra dos predicciones, una
  correcta y otra incorrecta, y verifica la boleta completa (`2`, `1`, `0.5` y el
  detalle). El segundo verifica la carga JSON; el tercero recorre fixture →
  componente ficticio → arnés y exige una exactitud estrictamente entre 0 y 1.
  Esto prueba el cálculo de la boleta, no solo que se construya un objeto.
- Ejecución: `uv run --directory eval-harness pytest -p no:cacheprovider
  --basetemp <temporal-aislado>` → **3 passed**. Se aisló el temporal porque el
  directorio temporal/caché preexistente de Windows no era accesible en este
  entorno; no es un fallo del arnés.

## Constitución, spec-kit y trazabilidad

- La migración conserva el texto material de la constitución; el stub
  `specs/00-constitution.md` apunta a `.specify/memory/constitution.md`, que queda
  declarado como el único archivo sellado y sujeto a `HUMAN=1`.
- El diff de `37a6125` sobre `specs/` solo convierte la constitución en stub y
  actualiza su índice. La nueva constitución no añade referencias a Acuerdo, Ley,
  Decreto o ISO, ni umbrales numéricos inventados frente al original. Las fechas de
  versión documentan la migración y no son umbrales.
- El andamiaje de spec-kit (`.specify/`, integraciones Codex/Claude y plantillas)
  no introduce implementación de producto ni contradice los principios aplicables.

Conclusión: el esqueleto de F1 respeta P-01, P-02, P-03, P-05, P-06 y P-10. No se
identificó motivo constitucional, normativo o de umbral para veto.
