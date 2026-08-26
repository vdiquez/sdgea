# Colección Postman — corte vertical

Cubre los 21 endpoints reales de `specs/spec-infra-servicios.md`
(captura-ingesta + records-custodia + seguridad-acceso), en el orden en que se
probaron manualmente con `curl`. 25 peticiones (cuatro de más sobre el total de
endpoints, todas deliberadas): publicar la misma versión de TRD dos veces para
demostrar el fix del VETO de Codex (T-19); validar un ítem como corrupto
(RF-CI-006, T-02) antes de leer el conteo por estado — por eso la petición 02
espera un ítem ya `EN_CUARENTENA`; y en Seguridad y Acceso (T-27), autenticar
dos veces (correcta e incorrecta) y autorizar dos veces (antes y después de
revocar el rol) para probar RF-SA-001 y RF-SA-006 de punta a punta.

## Levantar el stack (con puertos locales para Postman)

Los servicios normalmente NO exponen puertos al host (`specs/spec-infra-servicios.md`
§7 — sin Seguridad y Acceso, no deben salir de la red interna). Para probarlos
desde Postman en tu máquina hay un overlay aparte, solo para desarrollo local:

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml up -d --build
```

## Importar en Postman

1. Import → `SGDEA-coleccion.postman_collection.json`
2. Import → `SGDEA-local.postman_environment.json`, y selecciónalo como entorno activo (arriba a la derecha)
3. Collection Runner → correr la colección completa de arriba a abajo (las
   peticiones encadenan IDs vía variables de entorno: `lote_id`, `documento_id`,
   `trd_version` — generadas con timestamp en las peticiones 01, 04 y 08, así
   que correr la colección varias veces no colisiona)

## Validar sin abrir Postman (Newman)

```
npx newman run SGDEA-coleccion.postman_collection.json -e SGDEA-local.postman_environment.json
```

Verificado (2026-08-22): 15/15 peticiones, 32/32 aserciones, dos corridas
seguidas sin fallos. Reverificado (2026-08-23, tras T-02/RF-CI-006): 16/16
peticiones, 35/35 aserciones, dos corridas seguidas sin fallos. Reverificado
(2026-08-25, tras T-27/Seguridad y Acceso): 25/25 peticiones, 54/54
aserciones, dos corridas seguidas sin fallos.

## Bajar el stack

```
docker compose -f ../deploy/docker-compose.saas.yml -f ../deploy/docker-compose.local-ports.yml down
```
