# Colección Postman — corte vertical

Cubre 33 de los 36 endpoints reales de `specs/spec-infra-servicios.md`
(captura-ingesta + records-custodia + seguridad-acceso + validacion-humana +
normalizacion), en el orden en que se probaron manualmente con `curl` — los 3
endpoints de validacion-humana que faltan (candidatas a aprobación masiva,
aprobación en bloque, estado de la cola) ya tienen su propia cobertura en los
tests de Gradle del módulo (T-30), no aquí. 46 peticiones: publicar la misma
versión de TRD dos veces para demostrar el fix del VETO de Codex (T-19);
validar un ítem como corrupto (RF-CI-006, T-02) antes de leer el conteo por
estado — por eso la petición 02 espera un ítem ya `EN_CUARENTENA`; en
Seguridad y Acceso (T-27), autenticar dos veces (correcta e incorrecta) y
autorizar dos veces (antes y después de revocar el rol); la carpeta 4 (T-32,
ampliada en T-38 con el permiso `confirmar`/`documento`) es un **flujo
end-to-end real** entre tres servicios: identidad → custodia → sugerencia →
cola de revisión → decisión → clasificación materializada; la carpeta 5
(T-36, ampliada en T-37) ejercita el ciclo completo de Normalización —
**primer contexto Python/FastAPI del proyecto** (T-33..T-35)— hasta entregar
a Extracción, más un segundo ítem que se rechaza por formato no soportado
(RF-NO-009) para que el conteo final cuadre con dos unidades terminales, y
una petición final que consulta la bitácora de auditoría
(`GET /eventos-auditoria`, P-08, hallazgo V-01 de la revisión acumulada de
Codex) y verifica que cada evento trae actor y fecha; y la carpeta 6 (T-38,
cierra RF-VH-005) es el **segundo flujo end-to-end real**, esta vez entre
Validación Humana y Normalización: recibe una unidad no trivial en
Normalización, la confirma desde Validación Humana (reutilizando la
identidad/rol de la carpeta 4) y verifica en Normalización que quedó con
`LIMITES_CONFIRMADOS` atribuidos al actor de Validación Humana.

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
   `unidad_id_vh_no`/`lote_id_vh_no` (carpeta 6, T-38) — generadas con
   timestamp en la primera petición de cada flujo, así que correr la
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
confirmado con dos corridas seguidas limpias después.

## Bajar el stack

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml down
```
