# Colección Postman — corte vertical

Cubre 50 de los 58 endpoints reales de `specs/spec-infra-servicios.md`
(captura-ingesta + records-custodia + seguridad-acceso + validacion-humana +
normalizacion + extraccion + clasificacion), en el orden en que se probaron
manualmente con `curl` — los 8 que faltan ya tienen su propia cobertura fuera
de esta colección: en validacion-humana, candidatas a aprobación masiva, aprobación
en bloque y estado de la cola de clasificación (tests de Gradle, T-30); en
records-custodia y normalizacion, `GET /sugerencias/pendientes` (T-28) y
`GET /unidades/pendientes-de-limites` (T-39) — ambos se ejercitan
indirectamente aquí (Validación Humana los llama al resolver
`/colas/clasificacion` y `/colas/limites`) y tienen su propia cobertura
directa en Gradle/pytest, pero ninguna petición de esta colección los llama
por su ruta original; en extraccion, `GET /textos/{id}`,
`GET /textos/pendientes-de-revision` y `GET /textos/{id}/entrega` — los tres
tienen su propia cobertura directa en `tests/test_api.py` (T-41), simplemente
esta colección no necesitó llamarlos para demostrar el ciclo completo. 68
peticiones: publicar la misma versión de TRD
dos veces para demostrar el fix del VETO de Codex (T-19); validar un ítem
como corrupto (RF-CI-006, T-02) antes de leer el conteo por estado — por eso
la petición 02 espera un ítem ya `EN_CUARENTENA`; en Seguridad y Acceso
(T-27), autenticar dos veces (correcta e incorrecta) y autorizar dos veces
(antes y después de revocar el rol); la carpeta 4 (T-32, ampliada en T-38
con el permiso `confirmar`/`documento`) es un **flujo end-to-end real**
entre tres servicios: identidad → custodia → sugerencia → cola de revisión →
decisión → clasificación materializada; la carpeta 5 (T-36, ampliada en
T-37) ejercita el ciclo completo de Normalización — **primer contexto
Python/FastAPI del proyecto** (T-33..T-35)— hasta entregar a Extracción, más
un segundo ítem que se rechaza por formato no soportado (RF-NO-009) para
que el conteo final cuadre con dos unidades terminales, y una petición
final que consulta la bitácora de auditoría (`GET /eventos-auditoria`, P-08,
hallazgo V-01 de la revisión acumulada de Codex) y verifica que cada evento
trae actor y fecha; y la carpeta 6 (T-38/T-39) cubre tres cosas: el
**segundo flujo end-to-end real** del proyecto (Validación Humana confirma
límites en Normalización, T-38); la **cola de límites** de Validación Humana
(RF-VH-001/002/010, T-39) — una segunda unidad con sugerencia de límites,
consultada vía `GET /colas/limites` y `/colas/limites/estado`; y una
**corrección** (serie decidida distinta de la sugerida, RF-VH-008) cuya
huella queda consultable en `GET /documentos/correcciones` de
records-custodia, marcada `PENDIENTE_DE_REREVISION` (RF-VH-009, T-39); y la
carpeta 7 (T-43, peticiones 54-67) ejercita el ciclo completo de Extracción
— **primer contexto Python del proyecto que exige autorización real de
seguridad-acceso para materializar** (RF-EX-011/P-03): rol + identidad con
permiso `confirmar`/`documento` (mismo patrón que la carpeta 4/T-38); una
unidad born-digital (extracción determinística, calidad 1.0); una segunda
unidad por escaneo con una sugerencia de OCR ficticia que NO materializa por
sí sola (P-01), confirmada primero con un actor sin permiso -> `403` y luego
con el actor autorizado -> `Extraído`; una tercera unidad marcada `CORRUPTO`
-> `En cuarentena` (RF-EX-009) para que el conteo final cuadre con tres
unidades terminales; y una petición final contra `GET /eventos-auditoria`
(P-08); y la carpeta 8 (T-47, peticiones 68-74) ejercita el ciclo completo
de Clasificación — **segundo flujo end-to-end real de solo dos servicios**
(Clasificación no tiene persistencia propia, `specs/003-clasificacion/spec.md`
§3, así que no aparece como un tercer servicio "con estado" en la cadena):
custodiar un documento en records-custodia; clasificar con dos candidatas
FICTICIAS y comprobar que la respuesta y el reenvío a
`POST /sugerencias` llegan ordenados por confianza **descendente**
(RF-CL-003 — al revés que las colas de Validación Humana) verificado
consultando `GET /documentos/{id}/sugerencias` en records-custodia; agrupar
a un expediente propuesto (RF-CL-005/006) y verificar el mismo endpoint;
marcar un texto como no clasificable (RF-CL-010) y verificar que el conteo
de sugerencias en records-custodia **no cambia** — su destino es el
"Operador" (reporte), no Records/Custodia; y la petición 75 (T-48) —
records-custodia no exponía lectura de su propia bitácora de auditoría (a
diferencia de normalización/extracción, que sí tenían `GET
/eventos-auditoria`) — consulta el `GET /eventos-auditoria` nuevo y verifica
que los eventos `SUGERENCIA_RECIBIDA` que generaron las peticiones 69
(clasificar) y 71 (agrupar) aparecen atribuibles a
`clasificador-ficticio-v1`/`agrupador-ficticio-v1`, con actor y fecha no
vacíos; y la carpeta 9 (T-52, peticiones 76-80) ejercita el ciclo completo
de Enriquecimiento — **tercer flujo end-to-end real de solo dos servicios**
(Enriquecimiento tampoco tiene persistencia propia,
`specs/004-enriquecimiento/spec.md` §3, mismo criterio que Clasificación):
custodiar un documento en records-custodia; enriquecer con un valor
propuesto FICTICIO y un campo marcado "no encontrado" en la misma llamada
(`evaluar_texto` bifurca internamente, un único endpoint
`POST /enriquecimientos` en vez de rutas separadas) y verificar que ambos
llegan distinguibles por campo (RF-EN-002..006/008) consultando
`GET /documentos/{id}/sugerencias` en records-custodia; marcar un texto
"no enriquecible" con razón (RF-EN-009) y verificar que el conteo de
sugerencias en records-custodia **no cambia**.

## Levantar el stack (con puertos locales para Postman)

Los servicios normalmente NO exponen puertos al host (`specs/spec-infra-servicios.md`
§10 — sin que captura-ingesta/records-custodia llamen de verdad a
`/autorizacion`, no deben salir de la red interna; validacion-humana sí lo
hace, T-30). Para probarlos desde Postman en tu máquina hay un overlay
aparte, solo para desarrollo local:

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml up -d --build
```

## Importar en Postman

1. Import → `SGDEA-coleccion.postman_collection.json`
2. Import → `SGDEA-local.postman_environment.json`, y selecciónalo como entorno activo (arriba a la derecha)
3. Collection Runner → correr la colección completa de arriba a abajo (las
   peticiones encadenan IDs vía variables de entorno: `lote_id`, `documento_id`,
   `trd_version`, `identidad_id_sa`/`rol_sa`/`actor_sa`,
   `identidad_id_vh`/`rol_vh`/`actor_vh`/`documento_id_vh`,
   `unidad_id_no`/`unidad_id_no_2`/`lote_id_no`/`huella_no`,
   `unidad_id_vh_no`/`lote_id_vh_no` (carpeta 6, T-38),
   `unidad_id_vh_limites`/`lote_id_vh_limites`/`documento_id_vh_correccion`
   (carpeta 6, T-39),
   `rol_ex`/`actor_ex`/`identidad_id_ex`/`lote_id_ex`/`texto_id_ex`/`texto_id_ex_2`/`texto_id_ex_3`
   (carpeta 7, T-43), `documento_id_cl`/`sugerencias_count_cl` (carpeta 8, T-47),
   `documento_id_en`/`sugerencias_count_en` (carpeta 9, T-52)
   — generadas con timestamp en la primera petición de cada flujo, así que correr la
   colección varias veces no colisiona. La huella de contenido de
   Normalización también necesita timestamp: sin él, la segunda corrida
   detecta un duplicado real contra la primera y falla la aserción de
   "entregada a Extracción" — fue el primer fallo real que encontró este
   patrón, corregido en T-36.)

## Validar sin abrir Postman (Newman)

```
npx newman run SGDEA-coleccion.postman_collection.json -e SGDEA-local.postman_environment.json
```

Verificado (2026-08-22): 15/15 peticiones, 32/32 aserciones, dos corridas
seguidas sin fallos. Reverificado (2026-08-23, tras T-02/RF-CI-006): 16/16
peticiones, 35/35 aserciones, dos corridas seguidas sin fallos. Reverificado
(2026-08-25, tras T-27/Seguridad y Acceso): 25/25 peticiones, 54/54
aserciones, dos corridas seguidas sin fallos. Reverificado (2026-08-26, tras
T-32/Validación Humana, con los cuatro servicios corriendo a la vez): 33/33
peticiones, 66/66 aserciones, dos corridas seguidas sin fallos — incluye el
flujo end-to-end real de la carpeta 4. Reverificado (2026-08-26, tras
T-36/Normalización, con los cinco servicios corriendo a la vez): 42/42
peticiones, 79/79 aserciones — la primera corrida encontró un fallo real
(huella de contenido sin timestamp, ver arriba), corregido y confirmado con
dos corridas seguidas limpias después. Reverificado (2026-08-27, tras T-37/fix
del VETO V-01 — bitácora de auditoría en Normalización): 43/43 peticiones,
81/81 aserciones, dos corridas seguidas sin fallos. Reverificado (2026-08-27,
tras T-38/cierre de RF-VH-005): 46/46 peticiones, 87/87 aserciones — la
primera corrida encontró un fallo real y nuevo (502 Bad Gateway al confirmar
límites desde Validación Humana hacia Normalización, ver `spec-infra-
servicios.md` §6 para el diagnóstico completo: el `HttpClient` por defecto de
Spring Boot 3.5 intentaba un upgrade h2c que `uvicorn` rechazaba), corregido
fijando HTTP/1.1 explícito en el cliente HTTP de Validación Humana y
confirmado con dos corridas seguidas limpias después. Reverificado
(2026-08-27, tras T-39/colas de límites y correcciones pendientes de
re-revisión): 54/54 peticiones, 97/97 aserciones — la primera corrida
encontró un tercer fallo real (`500 Internal Server Error` al leer o escribir
`eventos_auditoria` en records-custodia: `ALTER TABLE ... ADD COLUMN
es_correccion boolean not null` sin `DEFAULT` falla contra una tabla con
filas existentes, ver `spec-infra-servicios.md` §4), corregido con
`columnDefinition = "boolean not null default false"` y confirmado con dos
corridas seguidas limpias después (volumen de Postgres reiniciado en limpio
para partir de un esquema consistente). Reverificado (2026-08-27, tras
T-43/ciclo completo de Extracción, con los seis servicios corriendo a la vez
— primer contexto Python que exige autorización real contra seguridad-acceso,
RF-EX-011/P-03): 68/68 peticiones, 113/113 aserciones, dos corridas seguidas
sin fallos desde la primera corrida — incluida la frontera de autorización
(actor sin permiso -> `403`, actor autorizado -> `Extraído`). Reverificado
(2026-08-28, tras T-47/ciclo completo de Clasificación, con los siete
servicios corriendo a la vez): 75/75 peticiones, 120/120 aserciones, dos
corridas seguidas sin fallos desde la primera corrida — incluido el orden
descendente por confianza (RF-CL-003) y que `POST /no-clasificables` no
agrega ninguna `Sugerencia` nueva en records-custodia (RF-CL-010).
Reverificado (2026-08-29, tras T-48/`GET /eventos-auditoria` nuevo en
records-custodia, con los siete servicios corriendo a la vez): 76/76
peticiones, 121/121 aserciones, dos corridas seguidas sin fallos desde la
primera corrida — incluida la petición 75, que confirma que las sugerencias
de Clasificación quedan en la bitácora de auditoría de records-custodia,
atribuibles a su modelo ficticio de origen. Reverificado (2026-08-30, tras
T-49..T-52/ciclo completo de Enriquecimiento, con los ocho servicios
corriendo a la vez — octavo bounded context del proyecto): 81/81
peticiones, 126/126 aserciones, dos corridas seguidas sin fallos desde la
primera corrida — incluido que `evaluar_texto()` bifurca correctamente
entre sugerencia (valor propuesto + campo no encontrado, distinguibles por
campo) y `MarcaNoEnriquecible` (RF-EN-009, sin reenviar nada a
records-custodia) desde el mismo endpoint `POST /enriquecimientos`.

## Bajar el stack

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml down
```
