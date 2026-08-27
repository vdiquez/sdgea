VETO: P-08 incumplido en Normalización: sus transiciones persistidas no generan un evento de auditoría inmutable, atribuible y fechado.

# Revisión acumulada `65c3c43..HEAD` — T-22 a T-36

Revisado el rango completo de 27 commits (`65c3c43..HEAD`; 82 archivos, 9.348
altas/122 bajas) contra `AGENTS.md`, la constitución, `STATE.md`, las diez
specs indicadas y `specs/spec-infra-servicios.md`.

## Vetos

### V-01 · P-08 no implementado en Normalización

`specs/001-normalizacion/spec.md` declara como salida los eventos de auditoría
de normalización y hace que las transiciones de la unidad documental sean parte
del ciclo protegido por P-08. Sin embargo, `dominio.py` realiza las transiciones
`recibir_item`, `confirmar_limites`, `normalizar`,
`marcar_cuarentena_o_rechazo` y `entregar`, mientras que `api.py` sólo persiste
la unidad mediante `AlmacenDeUnidades.guardar`. `persistencia.py:128-130` hace
un `merge` y `commit` exclusivamente sobre `unidades_documentales`; no existe
modelo, puerto, tabla ni anexado de `EventoAuditoria`.

Por tanto una unidad puede pasar a `Límites confirmados`, `Normalizada`,
`Entregada a Extracción`, `En cuarentena`, `Rechazada` o `Vinculada a duplicado`
sin evento con actor, fecha y estados anterior/posterior. Es una violación
directa de P-08 y de RF-NO-008, no una ausencia de integración secundaria.
La corrección debe introducir la bitácora append-only y una unidad transaccional
que abarque la actualización de la unidad y el evento; de otro modo se recreará
el mismo riesgo de atomicidad que T-21/T-22 corrigieron en Records/Custodia.

Los 30 tests de Normalización no lo detectan: ningún test consulta ni provoca
fallo del almacén de auditoría porque éste no existe.

### V-02 · Se implementaron decisiones explícitamente marcadas `[CLARIFICAR]`

La spec de Seguridad y Acceso, §8, deja pendientes tanto el modelo concreto de
autorización (RBAC/ABAC/híbrido) como el proveedor de identidad. T-23 eligió e
implementó ambos: `Rol` + `Permiso` y `GestionDeAccesos` constituyen RBAC, y
`crearIdentidad` crea un almacén propio de identidades con credencial hasheada.
No hay respuesta en `QUESTIONS.md` que autorice esas elecciones.

Esto contradice la instrucción vinculante de `AGENTS.md`: ante `[CLARIFICAR]`
real se registra la pregunta y se detiene, sin convertir una alternativa de la
spec en política de producto. La elección de SHA-256 para la credencial tampoco
resuelve la ambigüedad de proveedor ni la política de credenciales; no se trata
de un mero detalle interno. Deben volver a marcarse como pendientes y obtener
la decisión del design partner antes de sostener la afirmación de que el
contexto está completo.

## Hallazgos que no añaden otro veto

- **P-01:** pasa en Records/Custodia, Validación Humana y Normalización para el
  camino implementado. Las sugerencias de clasificación pasan por
  `CapaAnticorrupcionSugerencias`; Validación Humana usa puertos y Records/Custodia
  materializa con una decisión humana; la sugerencia ficticia de límites no
  cambia por sí sola el estado a `Límites confirmados`. No se halló clasificador,
  OCR ni otro componente probabilístico real.
- **P-03:** pasa para las capacidades efectivamente usadas. Validación Humana
  declara los puertos `FuenteDeSugerencias`, `RegistradorDeDecisiones` y
  `VerificadorDePermisos`, con adaptadores HTTP concretos confinados a
  `integracion/IntegracionHttp.kt`. Los puertos de persistencia de los
  contextos Kotlin permanecen separados de sus adaptadores JPA. Normalización
  no invoca OCR, almacenamiento de objetos, embeddings, LLM ni índices; por
  ello no se aprecia consumo directo de una capacidad crítica de P-03.
- **Atomicidad de T-22:** pasa. `CustodiaTransaccional` encierra
  `custodiar` (original, documento y evento) y `materializar` (documento y
  evento); `RecepcionDeSugerenciasTransaccional` sigue haciendo lo propio para
  sugerencia y auditoría. Las pruebas de rollback usan JPA real y sólo doblan
  el adaptador que falla, por lo que no están amañadas.
- **Atomicidad nueva:** Seguridad y Acceso escribe un único almacén por operación
  y Validación Humana no mantiene estado propio. La aprobación masiva puede
  completar decisiones anteriores si falla una posterior, pero cada decisión
  materializada la conserva Records/Custodia con su propia transacción y evento;
  la spec no exige semántica todo-o-nada para el lote. El único defecto de
  atomicidad confirmado es V-01 cuando se añada la auditoría faltante.
- **Honestidad de MockRestServiceServer:** pasa. Los tests de
  `IntegracionHttpTest` ejercitan los adaptadores de producción
  (`RestTemplate`, URL, método, serialización y traducción de respuesta/error),
  no un puerto falso. Los de `ValidacionHumanaHttpTest` levantan el servicio
  real con `RANDOM_PORT` y sólo simulan las dependencias remotas. Es el límite
  correcto de un test de contrato HTTP; el flujo Docker/Postman complementa,
  pero no sustituye, esas pruebas.
- **Honestidad de FastAPI/SQLite:** pasa. `test_api.py` enruta peticiones por
  la aplicación FastAPI real y emplea el repositorio SQLAlchemy real. El override
  de dependencia cambia únicamente la sesión a SQLite en memoria; `StaticPool`
  hace que `create_all` y las sesiones de las solicitudes usen la misma base.
  No hay doble que preconfigure resultados. Ejecución local de esta parte:
  **30 passed**.
- **Cobertura declarada como “completa”:** no es exacta. RF-VH-005 no está
  implementado: Validación Humana no tiene `ConfirmadorDeLimites` ni adaptador
  HTTP hacia Normalización, tal como reconoce `spec-infra-servicios.md` §6/§10.
  Además, RF-VH-001 nombra sugerencias de Clasificación, Enriquecimiento,
  Normalización y Extracción, pero el código sólo consume Records/Custodia; y
  RF-VH-009 no expone ni conserva las correcciones para re-revisión. Se añadieron
  tareas para estas brechas de RF existentes.

## Specs nuevas y trazabilidad

Las siete specs bajo `001-...` a `007-...` conservan la estructura exigida:
propósito/frontera, lenguaje ubicuo, modelo e invariantes, contrato,
RF-<CTX>-NNN (diez por contexto) con criterio Dado/Cuando/Entonces,
requisitos no funcionales, tabla de trazabilidad y pendientes. Las referencias
preexistentes (AGN, Acuerdo AGN 001 de 2024, Acuerdo 003 de 2015, Decreto 1080
de 2015, Ley 594 de 2000, ISO 15489 e ISO 16175) se preservan con referencia
específica `PENDIENTE`. No se detectó umbral numérico nuevo inventado: los
umbrales de confianza permanecen pendientes o son parámetros de entrada.

Las referencias nuevas en `specs/006-seguridad-acceso/spec.md` son válidas:

- Ley 1712 de 2014: **Ley de Transparencia y del Derecho de Acceso a la
  Información Pública Nacional**.
- Ley Estatutaria 1581 de 2012: **disposiciones generales para la protección
  de datos personales**.

Ambas son leyes colombianas reales, tal como consta en el Gestor Normativo de
Función Pública ([Ley 1712](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=56882),
[Ley 1581](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=49981)).
La spec no inventa artículos: para RF-SA-001, 004 y 007 la columna de referencia
específica queda correctamente `PENDIENTE`. Por ello no hay veto normativo ni
por umbrales en el rango.

## Verificación ejecutable

`uv run --directory contexts/normalizacion pytest` se ejecutó con caché temporal:
30 passed. La suite total `bash ./test.sh` no pudo ejecutarse en este entorno:
el wrapper Gradle necesitó descargar Gradle 9.7.0 y el sandbox bloqueó el socket
(`java.net.SocketException: Permission denied: getsockopt`). No se afirma una
ejecución local verde de la parte Kotlin.

## Resultado

El rango queda **VETADO** hasta corregir V-01 y resolver explícitamente los
`[CLARIFICAR]` materializados en V-02. Las tareas T-37 a T-39 se añadieron al
final de `TODO.md` y derivan exclusivamente de RF-NO-008 y RF-VH-001/005/009.
