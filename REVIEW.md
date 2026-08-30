OK: T-55 corrige los dos motivos del VETO anterior; sin violaciones de P-01, P-03 ni P-08.

# Revisión de `3793486bf8f5214f206eb20270d2c4bea0515190`

## Alcance y contexto

El commit modifica únicamente `TODO.md`: ajusta el diseño pendiente de T-55
para el contexto [Indexación y Búsqueda](specs/005-indexacion-busqueda/spec.md).
No incorpora código ni pruebas ejecutables, ni modifica archivos bajo `specs/`.

Por tanto, no aplica el control reforzado de referencias normativas y umbrales
por ruta modificada. De todas formas, el diff no añade Acuerdos, Leyes,
Decretos, ISO ni valores numéricos: RNF-IB-002, los cuatro componentes y el
`[CLARIFICAR]` de motores/modelos ya existen en la spec. Postgres es parte del
stack ya decidido en STATE.md; las URL de configuración no son una nueva
referencia normativa ni un umbral.

## Principios constitucionales

- **P-01 — conforme.** T-55 mantiene embeddings, orden semántico, respuesta y
  citas como valores ficticios ya entregados por el llamador. Sus adaptadores
  gestionado/autoalojado no calculan nada probabilístico y fallan de forma
  explícita si se invocan; no hay una ruta planificada que escriba el estado de
  Records/Custodia sin una decisión humana.
- **P-03 — conforme.** La tarea ahora ordena una interfaz y dos variantes de
  despliegue reales e intercambiables para índice léxico, índice vectorial,
  generador de embeddings y modelo de lenguaje: autoalojada sin salida de red
  y gestionada mediante endpoint configurable. Además, Seguridad y Acceso se
  consume mediante `VerificadorDePermisos`, no directamente desde el dominio.
  El hecho de que el compose actual use por defecto la variante autoalojada no
  elimina la segunda implementación ni contradice la paridad exigida.
- **P-08 — conforme.** Cada ruta de consulta debe guardar su evento de acceso
  en la bitácora append-only, de forma atómica y antes de responder. Se
  distingue correctamente de `guardar_con_evento` para indexación/actualización
  y se exige exponer ambos tipos mediante `GET /eventos-auditoria`.

## Honestidad de pruebas

No hay tests nuevos en este commit, por lo que no existe una prueba ejecutable
que pueda estar amañada. La especificación de TDD corregida es honesta respecto
a RF-IB-009: debe ejecutar la consulta HTTP real y luego consultar la bitácora;
no permite satisfacer el criterio inspeccionando solo un evento devuelto ni una
función auxiliar aislada. Al implementar T-55 habrá que comprobar que la prueba
cubra las rutas efectivamente declaradas y que la persistencia sea transaccional.

## Dictamen

La modificación resuelve los dos hallazgos bloqueantes de `e356158`: ya no
confunde dobles en memoria con variantes de despliegue y ya no confunde devolver
un evento con emitirlo persistentemente. Sin VETO.
