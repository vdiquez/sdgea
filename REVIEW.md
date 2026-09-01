OK: fortalecimiento de T-60 conforme con RF-UI-001; sin VETO.

# Revisión de `33429d7` — aserción explícita de `localStorage` para RF-UI-001

## Alcance contra la spec

El único cambio está en `contexts/ui-demo/e2e/rf-ui-001-autenticacion.spec.ts`.
Amplía el caso válido de RF-UI-001 (`specs/008-ui-demo/spec.md` §5) después del
login real: lee `localStorage["sgdea-ui-demo:sesion"]`, comprueba que contiene el
`id` y el `actor` de la identidad realmente creada, y que no contiene
`credencialHash`. Es precisamente el fortalecimiento no bloqueante pendiente de
la revisión anterior.

El criterio válido exige conservar la identidad autenticada para la sesión. La
aserción nueva lo comprueba en su ubicación real, en vez de inferirlo sólo por el
texto de Inicio. No añade comportamiento, endpoints, ni regla de negocio.

## Principios constitucionales

- **P-01: conforme.** El cambio no incorpora ni invoca capacidad probabilística,
  no genera una sugerencia y no escribe estado documental. La única escritura que
  observa es el estado efímero de sesión del navegador, expresamente fuera del
  núcleo de records.
- **P-03: conforme.** No incorpora una capacidad externa ni un acceso directo a
  ella. El e2e sigue creando rol e identidad y autenticando a través de las rutas
  HTTP publicadas por Seguridad y Acceso y el proxy curado.
- **P-08: conforme.** El commit no crea ninguna transición de documento o
  expediente. La autenticación que el flujo ejercita se audita en el backend:
  `GestionDeAccesos.autenticar` anexa `AUTENTICACION_EXITOSA` o
  `AUTENTICACION_FALLIDA` antes de retornar o rechazar. El estado de sesión local
  no es una transición de records sujeta a P-08.

## Honestidad de los tests

La prueba no está amañada: usa Playwright y crea un rol y una identidad reales por
HTTP antes de enviar el formulario de login. No intercepta `fetch`, no simula la
respuesta de autenticación y la aserción de almacenamiento se ejecuta en la misma
página que atravesó UI, proxy y servicio real. La identidad esperada procede de
los datos generados para esa ejecución, por lo que detectaría tanto no persistir
la sesión como persistir el hash de credencial.

El caso inválido existente continúa verificando el rechazo visible y la ausencia
de sesión visible; el cambio no lo debilita ni altera sus precondiciones.

## Control de specs, normativa y umbrales

El commit no modifica archivos bajo `specs/` y no introduce referencias
normativas, valores de umbral ni afirmaciones regulatorias nuevas. No procede
agregar tareas a `TODO.md`.

## Verificación ejecutada

- `git show --check HEAD` y `git diff --check HEAD^ HEAD`: sin errores de
  espacios.
- Se intentó `npm.cmd --prefix contexts/ui-demo run test:e2e --
  e2e/rf-ui-001-autenticacion.spec.ts`. No pudo arrancar el stack requerido:
  no había servidor en `localhost:5173` y el cliente Docker de este entorno no
  tiene permiso para acceder al daemon (`Access is denied`). Los dos fallos son
  de disponibilidad del entorno, antes de ejecutar el flujo de la aplicación;
  no constituyen un fallo atribuido al commit.
