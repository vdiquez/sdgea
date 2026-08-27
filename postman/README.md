# Colección Postman — corte vertical

Cubre 23 de los 26 endpoints reales de `specs/spec-infra-servicios.md`
(captura-ingesta + records-custodia + seguridad-acceso + validacion-humana),
en el orden en que se probaron manualmente con `curl` — los 3 endpoints de
validacion-humana que faltan (candidatas a aprobación masiva, aprobación en
bloque, estado de la cola) ya tienen su propia cobertura en los tests de
Gradle del módulo (T-30), no aquí. 33 peticiones: publicar la misma versión
de TRD dos veces para demostrar el fix del VETO de Codex (T-19); validar un
ítem como corrupto (RF-CI-006, T-02) antes de leer el conteo por estado — por
eso la petición 02 espera un ítem ya `EN_CUARENTENA`; en Seguridad y Acceso
(T-27), autenticar dos veces (correcta e incorrecta) y autorizar dos veces
(antes y después de revocar el rol); y la carpeta 4 (T-32) es un **flujo
end-to-end real** entre los tres servicios: identidad → custodia → sugerencia
→ cola de revisión → decisión → clasificación materializada.

## Levantar el stack (con puertos locales para Postman)

Los servicios normalmente NO exponen puertos al host (`specs/spec-infra-servicios.md`
§9 — sin que captura-ingesta/records-custodia llamen de verdad a
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
   `identidad_id_vh`/`rol_vh`/`actor_vh`/`documento_id_vh` — generadas con
   timestamp en la primera petición de cada flujo, así que correr la
   colección varias veces no colisiona)

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
flujo end-to-end real de la carpeta 4.

## Bajar el stack

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml down
```
