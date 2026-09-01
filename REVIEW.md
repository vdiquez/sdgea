OK: T-60 es conforme con RF-UI-001; sin VETO.

# Revisión de `c7d5317` — T-60: autenticación de la sesión de demo

## Alcance y criterios de aceptación

El commit implementa únicamente RF-UI-001 de `specs/008-ui-demo/spec.md`:
el formulario llama a `POST /api/seguridad-acceso/identidades/autenticacion`
mediante el cliente HTTP común y, ante éxito, guarda solamente `id`, `actor` y
`roles` antes de navegar al inicio. El navegador no apunta a puertos internos.
El error 401 se muestra con `role="alert"` y no ejecuta `guardarSesion`.

La respuesta real de Seguridad y Acceso contiene `credencialHash`, pero
`Login.tsx` construye explícitamente el objeto persistido con los tres campos
permitidos. La interfaz `Sesion` tampoco admite ese campo. Por inspección, el
valor almacenado en `localStorage` no puede incluir `credencialHash`.

## Principios constitucionales

- **P-01: conforme.** No hay componente probabilístico ni escritura de una
  sugerencia o de un documento. La UI sólo conserva estado de sesión de cliente.
- **P-03: conforme.** Seguridad y Acceso es un contexto interno ya especificado;
  la UI lo consume mediante su interfaz HTTP publicada y el proxy curado, no una
  capacidad externa crítica consumida directamente.
- **P-08: conforme.** La sesión de cliente no es estado de documento/expediente.
  El intento de acceso sí llega al servicio real: `GestionDeAccesos.autenticar`
  anexa `AUTENTICACION_EXITOSA` o `AUTENTICACION_FALLIDA` a su bitácora antes de
  devolver o rechazar. No se introduce una transición backend sin auditoría.

## Honestidad de pruebas

`rf-ui-001-autenticacion.spec.ts` no usa un doble para autenticación. Antes del
caso válido crea un rol y una identidad a través de `/api/seguridad-acceso`, y
después opera el formulario desde Playwright. El caso inválido usa un actor que
no existe y comprueba el rechazo y que el inicio continúa sin sesión visible.
Por tanto, las pruebas ejercitan el contrato y el proxy reales, no una respuesta
simulada acomodada al componente.

La cobertura es honesta pero mejorable: el e2e infiere la sesión desde la vista
de inicio; no inspecciona el contenido de `localStorage`. Conviene añadir a una
próxima tarea de la spec UI una aserción explícita de que la sesión válida
contiene `id`/`actor`/`roles` y no contiene `credencialHash`. No impide aprobar
este commit: el filtrado está implementado de forma explícita y el criterio de
rechazo queda cubierto por el flujo real.

## Verificación ejecutada

- `npm.cmd --prefix contexts/ui-demo test` — 1 archivo, 1 prueba, verde.
- `npm.cmd --prefix contexts/ui-demo run build` — verde.
- `git diff --check HEAD^ HEAD` — sin errores de espacios.
- El e2e de Playwright no se pudo correr en este entorno: Docker devuelve
  `Access is denied` al conectar con el daemon. La revisión estática confirma
  que está configurado contra `http://localhost:8090` y contra el stack real.

## Control de specs, normativa y umbrales

El diff no modifica archivos bajo `specs/`; no aplica el chequeo diferencial de
referencias normativas ni umbrales. Tampoco añade una referencia normativa ni un
umbral numérico nuevo.
