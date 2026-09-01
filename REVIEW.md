OK: `d5a5d1a` corrige íntegramente el VETO de `aaf9929`; el proxy curado expone solo los cinco endpoints autorizados de Records/Custodia, sin objeciones constitucionales.

# Revisión de `d5a5d1a` — corrección del proxy curado de T-63

## Dictamen

`contexts/ui-demo/nginx.conf` sustituye el prefijo genérico por exactamente cinco rutas+métodos hacia Records/Custodia:

- `GET /documentos/{id}`
- `GET /documentos/{id}/original`
- `GET /documentos/{id}/sugerencias`
- `GET /eventos-auditoria`
- `POST /documentos/{id}/verificacion-integridad`

Los bloques parametrizados están anclados de inicio a fin y usan `limit_except`; por tanto, ni métodos distintos ni segmentos adicionales se reenvían. `location /api/records-custodia/ { return 404; }` cierra el resto del prefijo. En concreto, `POST /documentos` y `POST /documentos/{id}/decisiones` ya no alcanzan el backend a través del proxy.

`GET /documentos/correcciones` queda excluido expresamente antes del patrón que reconoce `/documentos/{id}`. La misma exclusión aparece en el `bypass()` de `vite.config.ts`; este comparte las mismas cinco parejas ruta+método y devuelve `false` para todo lo demás. En Vite 8.2.2, ese resultado escribe una respuesta 404 y termina la solicitud antes de `proxy.web`, así que el proxy de desarrollo no reabre la superficie bloqueada.

El nuevo e2e no usa dobles: siembra identidad, rol y documento contra los servicios reales, comprueba que la lectura permitida llega por el `baseURL` del proxy y distingue el `400` real del backend de un `404` del proxy. Además comprueba por el proxy los 404 de custodiar, decisiones, procedencia, correcciones, verificación agregada y sugerencias pendientes. Esto cubre de forma efectiva la regresión vetada, incluido el caso de colisión `correcciones`.

## Constitución y specs

- **`specs/008-ui-demo/spec.md` §§1 y 4 / `specs/spec-infra-servicios.md` §4: conforme.** La exposición resultante es exactamente el subconjunto de cinco endpoints con autorización real de T-63. Las rutas no autorizadas no tienen ruta pública.
- **P-01 y P-09: conforme.** `POST /documentos/{id}/decisiones` deja de ser invocable por navegador; se preserva la orquestación servidor-a-servidor de Validación Humana.
- **P-02, P-03 y P-08: conforme.** No cambia el stack, el dominio ni la bitácora, y el ajuste de proxy no incorpora integración o estado de negocio nuevos.
- No se modificó la constitución, no se introdujeron referencias normativas ni umbrales inventados, ni componentes probabilísticos reales.

## Verificación ejecutada

- Inspección de las reglas Nginx y Vite contra las cinco rutas declaradas en las specs y contra los demás endpoints del contrato de Records/Custodia.
- Inspección del middleware instalado de Vite: `bypass() === false` devuelve 404 antes del reenvío.
- `git diff HEAD^ HEAD --check`: correcto.
- `npm.cmd --prefix contexts/ui-demo run build`: correcto.
- `npm.cmd --prefix contexts/ui-demo test`: 1 prueba, correcta.
- Configuración Compose SaaS+demo+local-ports y on-prem: válida. No fue posible ejecutar Docker/e2e en vivo en este sandbox porque el daemon de Docker requiere privilegios no disponibles.
