OK: commit `37a53b5` conforme.

Alcance revisado: T-48; nuevo `GET /eventos-auditoria` de
`records-custodia`, su prueba HTTP, la petición Postman 75 y la actualización
de `specs/spec-infra-servicios.md` §4.

- P-01: conforme. El endpoint es exclusivamente de lectura sobre
  `CustodiaOriginales.eventosDeAuditoria`; no acepta ni materializa una
  sugerencia, ni modifica documento o expediente. Las sugerencias siguen
  entrando por `POST /sugerencias` hacia
  `CapaAnticorrupcionSugerencias`, y la única mutación de clasificación sigue
  siendo `materializar(DecisionHumana)`.
- P-03: conforme. No se añade ni se consume capacidad externa; el controlador
  usa el agregado/puerto de dominio ya cableado en `RecordsCustodiaConfig`.
- P-08: conforme. La lectura devuelve la única `BitacoraAuditoria` compartida
  por custodia y la capa anticorrupción, por lo que hace observable tanto
  `ORIGINAL_CUSTODIADO` como `SUGERENCIA_RECIBIDA`; no crea una vía paralela
  ni mutable de auditoría. La emisión de ambos eventos ya es responsabilidad
  de las transiciones existentes y está cubierta además por las pruebas de
  dominio/transaccionales previas.
- Honestidad de pruebas: conforme. El nuevo test HTTP custodia un documento,
  envía una sugerencia por el endpoint real y solo entonces consulta el nuevo
  endpoint; exige `200` y ambos eventos con sus actores correctos. La petición
  Postman 75 extiende esa comprobación al flujo de Clasificación y exige los
  dos emisores ficticios esperados, además de actor y fecha no vacíos. No hay
  mocks, datos precargados ni aserciones que omitan el comportamiento nuevo.
- Control adicional de `specs/`: conforme. La única modificación de spec
  incorpora el contrato de lectura y referencias internas ya existentes
  (P-08, T-20/T-48 y endpoints análogos). No añade referencias normativas
  (Acuerdo, Ley, Decreto, ISO) ni introduce umbrales numéricos.

Verificación ejecutable: intenté
`./gradlew :contexts:records-custodia:test --rerun-tasks`, con un
`GRADLE_USER_HOME` escribible dentro del repo. No fue posible ejecutarla en
este sandbox porque no tiene la distribución Gradle 9.7 en caché y la descarga
está bloqueada por la restricción de red (`Permission denied: getsockopt`). No
es un fallo del proyecto ni altera el dictamen estático; el commit documenta
dos corridas reales de Newman, 76/76 peticiones y 121/121 aserciones.
