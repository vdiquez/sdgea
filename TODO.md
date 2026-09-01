# TODO — F2/F3: corte vertical determinístico + arnés (clasificador ficticio)
- [x] T-01 RF-CI-001 Ingesta por lote: artefactos + inventario -> ítems `Recibido`
- [x] T-02 RF-CI-006 Validación y cuarentena con razón registrada — taxonomía
      resuelta por Victor en QUESTIONS.md (2026-08-23): corrupto/ilegible ->
      En cuarentena (recuperable), formato no soportado -> Rechazado (no
      recuperable). Implementado `validar()` + endpoint HTTP + persistencia +
      colección Postman actualizada y revalidada con Newman.
- [x] T-03 RF-RC-001 Custodia del original inmutable (WORM + huella verificable)
- [x] T-04 RF-RC-002 + RF-CI-007 Procedencia completa de punta a punta
- [x] T-05 RF-CI-008 Cero pérdida silenciosa: suma de estados terminales cuadra
- [x] T-06 RF-CI-002 Conciliación contra inventario (FUID): faltantes y sobrantes
- [x] T-07 RF-RC-006 TRD como objeto versionado (estructura mínima)
- [x] T-08 RF-RC-003 Sugerencia vía capa anticorrupción, con EMISOR FICTICIO; no toca estado
- [x] T-09 RF-RC-004 Materialización solo por decisión humana (actor + fecha)
- [x] T-10 RF-RC-005 Bitácora inmutable de solo anexado; modificar/borrar se rechaza
- [x] T-11 RF-RC-009 Verificación de integridad por demanda con reporte de discrepancias
- [x] T-12 Arnés: cargar set de juguete, correr componente ficticio, emitir boleta versionada
- [x] T-13 CI: security-review cableado (anthropics/claude-code-security-review);
      AgentShield PENDIENTE explícito y no bloqueante (ver QUESTIONS.md 2026-08-21)
- [x] T-14 Empaquetado dual (P-02) — decisión resuelta (QUESTIONS.md 2026-08-21):
      servicio HTTP por contexto, Spring Boot, Postgres por contexto sin esquema
      compartido. Contrato mínimo en specs/spec-infra-servicios.md. Desglosada en
      T-15..T-18:
- [x] T-15 specs/spec-infra-servicios.md escrita (contrato HTTP mínimo +
      mapeo de persistencia para captura-ingesta y records-custodia)
- [x] T-16 captura-ingesta como servicio HTTP (Spring Boot) + persistencia Postgres,
      contra specs/spec-infra-servicios.md §3
- [x] T-17 records-custodia como servicio HTTP (Spring Boot) + persistencia Postgres,
      contra specs/spec-infra-servicios.md §4
- [x] T-18 Dockerfiles reales (captura-ingesta, records-custodia) + wiring en
      deploy/docker-compose.{saas,onprem}.yml
- [x] T-19 Corrige el VETO de Codex sobre T-16/T-17/T-18 (ver commit 582dd67):
      RF-RC-006 (RegistroTrd.publicar rechaza versión repetida a nivel de
      dominio + entityManager.persist), FK reales en DocumentoEntity y
      SugerenciaEntity, formato de error unificado entre los dos servicios.
      Verificado por una segunda revisión de Codex — los tres puntos quedan
      confirmados como corregidos.
- [x] T-20 P-08: recepción de sugerencia sin evento de auditoría (VETO de
      Codex sobre T-19, ver REVIEW.md — se mantenía tras revisar T-19,
      confirmado independientemente por segunda vez, no era un falso
      positivo). Corregido: `CapaAnticorrupcionSugerencias` recibe una
      `BitacoraAuditoria` (mismo patrón que `CustodiaOriginales`) y `recibir`
      anexa un `EventoAuditoria` (`tipo = "SUGERENCIA_RECIBIDA"`,
      `estadoAnterior = null`, `estadoPosterior = "SUGERENCIA_RECIBIDA"`) con
      `entrada.modeloId` como actor de sistema atribuible — dato ya existente
      en el contrato desde T-08, sin inventar un campo nuevo.
      `RecordsCustodiaConfig` comparte un único bean `BitacoraAuditoria` entre
      `custodiaOriginales` y `capaAnticorrupcionSugerencias`, así que el
      servicio HTTP anexa al mismo log de auditoría. TDD: 1 test nuevo
      (`RecepcionDeSugerenciasTest`, "se anexa un evento de auditoria
      atribuible con actor y fecha") escrito contra el hallazgo de Codex antes
      de tocar el código de producción; `./test.sh` en verde (Gradle BUILD
      SUCCESSFUL; pytest del arnés: 4 passed).
- [x] T-21 P-08 / RF-RC-005: hacer atómica la persistencia de la sugerencia y
      su `EventoAuditoria` en `CapaAnticorrupcionSugerencias.recibir`. Añadir
      una prueba de integración que provoque un fallo al anexar el evento y
      verifique que la sugerencia tampoco queda persistida; no puede existir
      una recepción confirmada sin su evento de auditoría.
      Corregido: `AlmacenDeSugerenciasJpa` y `AlmacenDeEventosJpa` son beans
      `@Transactional` independientes (Almacenes.kt), así que sin un límite
      que englobe ambas llamadas cada una confirma su propia transacción por
      la semántica proxy de Spring. `CapaAnticorrupcionSugerencias` sigue
      siendo una clase de dominio plana (sin anotaciones Spring, para que
      T-03..T-11 la sigan construyendo sin contexto Spring); el límite
      transaccional se abre en un wrapper nuevo,
      `configuracion/RecepcionDeSugerenciasTransaccional` (`@Service`,
      `recibir` anotado `@Transactional`), que es lo que ahora inyecta
      `SugerenciasController` en vez de `CapaAnticorrupcionSugerencias`
      directo. Al abrir la transacción ahí, ambas escrituras la heredan por
      la propagación REQUIRED de Spring (la que aplica por defecto): si
      `anexar` falla, el `guardar` de la sugerencia se revierte con ella.
      TDD: `RecepcionDeSugerenciasTransaccionalTest` — `@SpringBootTest` con
      `@MockitoBean` sobre `AlmacenDeEventosJpa` que fuerza el fallo solo en
      el evento `SUGERENCIA_RECIBIDA` (no en el de `custodiar`), contra la
      persistencia real (H2 en test, mismo mecanismo transaccional que
      Postgres) — no los almacenes en memoria, que es lo que Codex señaló
      como insuficiente en su VETO sobre T-20. Confirmado en rojo primero
      (quitando `@Transactional` del wrapper falla la aserción de que la
      sugerencia no quedó persistida) y en verde después. `./test.sh` en
      verde (Gradle BUILD SUCCESSFUL, 29 tests en records-custodia; pytest
      del arnés: 4 passed).
- [x] T-22 Mismo riesgo de atomicidad que T-21, extendido a
      `CustodiaOriginales.custodiar` (tres almacenes JPA independientes:
      original, documento, evento) y `materializar` (dos: documento, evento)
      — anotado como riesgo latente en la nota de T-21, ahora cerrado por
      decisión de Victor (2026-08-24: corregir ambos de una vez, mismo root
      cause). Nuevo wrapper `CustodiaTransaccional` (`@Service`, métodos
      `custodiar`/`materializar` anotados `@Transactional`), inyectado en
      `DocumentosController` en lugar de `CustodiaOriginales` directo para
      esos dos endpoints; `CustodiaOriginales` se mantiene sin anotaciones
      Spring. TDD: `CustodiaTransaccionalTest` (`@SpringBootTest`,
      `@MockitoBean` sobre `AlmacenDeEventosJpa`) con dos casos — fallo al
      anexar el evento de `custodiar` (ni original ni documento quedan
      persistidos) y fallo al anexar el evento de `materializar` (la
      clasificación no queda persistida) — confirmados en rojo quitando
      `@Transactional` del wrapper y en verde restaurándolo. `./test.sh` en
      verde (31 tests en records-custodia; pytest del arnés: 4 passed).

# Implementación de specs/006-seguridad-acceso/spec.md (2026-08-25, modo agéntico)
- [x] T-23 Dominio de Seguridad y Acceso (`contexts/seguridad-acceso`, módulo
      Kotlin/Spring convertido de esqueleto vacío al mismo patrón que
      captura-ingesta/records-custodia): `GestionDeAccesos` (autenticar,
      crearIdentidad, asignarRol/revocarRol, autorizar) y `GestionDeRoles`,
      clases de dominio planas sin anotaciones Spring. Cubre RF-SA-001 (auten-
      ticación), RF-SA-002 (gestión de roles/permisos sin cambios de código),
      RF-SA-003 (autorización denegar-por-defecto), RF-SA-004 (clasificación
      de la información con `NivelClasificacion` PUBLICA/CLASIFICADA/
      RESERVADA), RF-SA-005 (registro de eventos de seguridad), RF-SA-006
      (revocación inmediata — `autorizar` siempre lee el estado vigente, sin
      caché), RF-SA-007 (protección de credenciales — solo se guarda un hash
      SHA-256, `EventoSeguridad` no tiene campo de credencial) y RF-SA-010
      (cero pérdida silenciosa — todo intento de autenticación/autorización
      anexa un evento). RF-SA-008 se prueba en la capa HTTP (endpoint
      expuesto a otros contextos); RF-SA-009 se cumple por construcción (sin
      ninguna llamada de red externa en el dominio).
      Diseño deliberado para evitar desde el inicio el riesgo de atomicidad
      de T-21/T-22: cada operación pública de `GestionDeAccesos` escribe en
      un solo almacén (identidades O bitácora, nunca ambos a la vez) —
      asignar/revocar rol no genera evento de seguridad porque RF-SA-005 no
      lo exige, así que no hay una segunda escritura que pueda quedar
      huérfana; no hace falta un wrapper `@Transactional` porque no hay
      operación de dos escrituras que envolver.
      TDD: 14 tests nuevos (`SeguridadAccesoTest.kt`) contra los Dado/Cuando/
      Entonces de la spec, verdes en el primer intento. `./gradlew
      :contexts:seguridad-acceso:test` en verde.
- [x] T-24 `specs/spec-infra-servicios.md` §5 (nueva) — contrato HTTP mínimo
      de seguridad-acceso: 7 endpoints (`POST /identidades`, `POST
      /identidades/autenticacion`, `POST /identidades/{id}/roles`, `DELETE
      /identidades/{id}/roles/{rol}`, `POST /roles`, `POST /autorizacion`,
      `GET /eventos-seguridad`) más el mapeo de persistencia (tablas
      `identidades`, `roles`, `eventos_seguridad`). Renumeradas §5→§6 (formato
      de error), §6→§7 (trazabilidad), §7→§8 (decisiones pendientes). §8
      actualizada: el `[CLARIFICAR]` de autenticación/autorización ya no dice
      "el contexto no existe" — ahora dice explícitamente que existe (T-23)
      pero que captura-ingesta/records-custodia todavía no lo invocan; esa
      integración real queda fuera de alcance de esta tarea, anotada como
      brecha explícita.
- [x] T-25 Servicio HTTP (Spring Boot) + persistencia Postgres para
      seguridad-acceso, contra `specs/spec-infra-servicios.md` §5:
      `IdentidadesController`, `RolesController`, `AutorizacionController`,
      `EventosSeguridadController`, `ManejoDeErrores` (401 credenciales
      inválidas, 403 identidad suspendida, 404 no encontrado — mismo patrón
      canónico de T-19). Persistencia: `RolEntity`/`IdentidadEntity`/
      `EventoSeguridadEntity` + `AlmacenDeRolesJpa`/`AlmacenDeIdentidadesJpa`/
      `AlmacenDeEventosDeSeguridadJpa`, igual convención que records-custodia
      (JSON en columna de texto para `permisos`/`roles`, `EntityManager.persist`
      sin merge/update para `eventos_seguridad`). `SeguridadAccesoConfig`
      conecta las clases de dominio planas a los almacenes JPA. Puerto 8083
      (siguiente disponible tras 8081/8082).
      TDD: 8 tests HTTP nuevos (`SeguridadAccesoHttpTest.kt`), verdes en el
      primer intento junto con los 14 de dominio (22 tests totales en el
      módulo). `./test.sh` en verde (repo completo).
- [x] T-26 Dockerfile real de seguridad-acceso (mismo patrón que
      `contexts/records-custodia/Dockerfile`: build multi-módulo, tests
      omitidos porque `./test.sh` ya los corre en CI) + wiring en
      `deploy/docker-compose.{saas,onprem}.yml` (servicio `seguridad-acceso`,
      sin `ports:` — mismo criterio de red interna que los otros dos hasta
      que exista la integración real) y en
      `deploy/docker-compose.local-ports.yml` (puerto 8083, solo para
      Postman/curl desde el host).
- [x] T-27 Colección Postman extendida: carpeta nueva "3. Seguridad-Acceso"
      (9 peticiones, 16→24 renumeradas dentro de la carpeta, cubriendo los 7
      endpoints reales de §5: crear rol, crear identidad sin roles, asignar
      rol, autenticar OK, autenticar mal -> 401, autorizar -> PERMITIDO,
      revocar rol, autorizar tras revocar -> DENEGADO, consultar
      eventos-seguridad). Variables de entorno nuevas:
      `seguridad_acceso_base_url` (puerto 8083), `actor_sa`,
      `identidad_id_sa`, `rol_sa` (generadas con timestamp, mismo patrón que
      `documento_id`/`lote_id`, para que correr la colección varias veces no
      colisione contra la restricción `unique` de `identidades.actor`).
      Revalidada con el stack real levantado (`docker compose -f
      docker-compose.saas.yml -f docker-compose.local-ports.yml up -d
      --build`) y `npx newman run` — 25/25 peticiones, 54/54 aserciones, dos
      corridas seguidas sin fallos; stack bajado al terminar.
      **Con esto, Seguridad y Acceso (specs/006-seguridad-acceso/spec.md)
      queda completo de punta a punta: dominio (T-23) → contrato HTTP (T-24)
      → servicio + persistencia (T-25) → Docker (T-26) → Postman/Newman
      (T-27) — mismo ciclo completo que recibieron captura-ingesta y
      records-custodia.**

# Implementación de specs/007-validacion-humana/spec.md (2026-08-26, modo agéntico)
# Decisión de Victor: orquestador real sobre records-custodia + seguridad-acceso
# (primera integración HTTP real entre servicios del proyecto), no adaptadores
# en memoria con integración diferida.
- [x] T-28 `GET /sugerencias/pendientes` en records-custodia (ver STATE.md
      para el detalle completo): agrega sugerencias de todos los documentos
      sin clasificar, base de la cola de revisión de Validación Humana
      (RF-VH-001). `AlmacenDeDocumentos.todos()` nuevo,
      `CustodiaOriginales.documentosSinClasificar()`,
      `CapaAnticorrupcionSugerencias.sugerenciasPendientes()`. TDD: 2 tests de
      dominio + 1 HTTP nuevo, verdes junto con toda la suite existente de
      records-custodia.
- [x] T-29 Dominio de Validación Humana (ver STATE.md para el detalle
      completo): `ColaDeRevision` + `GestionDeDecisiones` sobre tres puertos
      (`FuenteDeSugerencias`, `RegistradorDeDecisiones`,
      `VerificadorDePermisos`), sin persistencia propia (spec §3). Cubre
      RF-VH-001/002/003/004/006/007/008/010. TDD: 8 tests nuevos con dobles
      en memoria, verdes en el primer intento.
- [x] T-30 Servicio HTTP + adaptadores HTTP reales para Validación Humana
      (ver STATE.md para el detalle completo): `spec-infra-servicios.md` §6
      nueva (5 endpoints). `ColasController`/`DecisionesController` +
      `integracion/IntegracionHttp.kt` (RestTemplate real contra
      records-custodia y seguridad-acceso — primera integración HTTP real
      entre servicios del proyecto). Puerto 8084. RF-VH-005 deliberadamente
      sin contrato: Normalización no existe como servicio. TDD: 4 tests de
      adaptadores (MockRestServiceServer) + 5 HTTP del servicio propio, 17
      en el módulo, verdes en el primer intento. `./test.sh` en verde.
- [x] T-31 Dockerfile + wiring en docker-compose para Validación Humana (ver
      STATE.md para el detalle completo). **Verificado con un flujo de
      punta a punta real de los cuatro servicios a la vez** (identidad+rol
      → custodia+sugerencia → cola de VH → decisión → clasificación
      materializada → cola vacía), primer intento sin fallos.
- [x] T-32 Colección Postman: carpeta "4. Validación-Humana (flujo
      end-to-end)" (8 peticiones, ver STATE.md para el detalle completo).
      Revalidada con los cuatro servicios corriendo a la vez — 33/33
      peticiones, 66/66 aserciones, dos corridas seguidas sin fallos.
      **Con esto, Validación Humana queda completa de punta a punta
      (T-28..T-32).**

# Implementación de specs/001-normalizacion/spec.md (2026-08-26, modo agéntico)
# Hallazgo importante: contexts/normalizacion YA es un proyecto Python real
# (pyproject.toml + miembro del workspace uv), decisión de stack ya tomada en
# docker-compose.saas.yml ("Python/FastAPI") — no Kotlin como los 4 anteriores.
- [x] T-33 Dominio de Normalización en Python (ver STATE.md para el detalle
      completo): `dominio.py` — `recibir_item`, `recibir_sugerencia_de_limites`
      (componente FICTICIO, RF-NO-002), `confirmar_limites`, `normalizar`,
      `marcar_cuarentena_o_rechazo`, `entregar`, `contar_por_estado`. Cubre
      RF-NO-001/002/003/004/005/006/008/009/010. `pyproject.toml` con
      dependencias reales; `test.sh` extendido con
      `uv run --directory contexts/normalizacion pytest`. TDD: 16 tests
      nuevos, verdes en el primer intento.
- [x] T-34 Servicio HTTP (FastAPI) + persistencia (SQLAlchemy + Postgres)
      para Normalización (ver STATE.md para el detalle completo, incluidos
      dos bugs reales de stack encontrados y corregidos: `StaticPool` para
      SQLite en memoria, y que FastAPI no serializa `@property` de
      dataclasses). 8 endpoints contra `spec-infra-servicios.md` §7 (nueva).
      RF-VH-005 sigue sin cerrarse: Normalización ya expone el endpoint,
      Validación Humana todavía no lo llama. TDD: 14 tests HTTP nuevos, 30
      en el módulo, verdes. `./test.sh` en verde.
- [x] T-35 Dockerfile real de normalizacion — primer contenedor Python
      (ver STATE.md para el detalle completo). **Verificado con un flujo de
      punta a punta real de los cinco servicios a la vez** (recibir ítem →
      sugerencia de límites → confirmación → normalizar → entregar →
      conteo), primer intento sin fallos. Wiring en docker-compose (puerto
      8085, con Postgres propio).
- [x] T-36 Colección Postman: carpeta "5. Normalizacion" (9 peticiones, ver
      STATE.md para el detalle completo, incluido un bug real de test
      encontrado en la primera revalidación: huella de contenido sin
      timestamp, causando un falso "duplicado" en la segunda corrida).
      Revalidada con los cinco servicios corriendo a la vez — 42/42
      peticiones, 79/79 aserciones tras la corrección. **Con esto,
      Normalización queda completa de punta a punta (T-33..T-36).**

# Hallazgos de revisión acumulada `65c3c43..HEAD` (2026-08-27)
- [x] T-37 RF-NO-008 / P-08 (corrige VETO V-01) — cada función de dominio de
      Normalización (`recibir_item`, `recibir_sugerencia_de_limites`,
      `confirmar_limites`, `normalizar`, `marcar_cuarentena_o_rechazo`,
      `entregar`) devuelve ahora `tuple[UnidadDocumentalCandidata,
      EventoAuditoria]`; `AlmacenDeUnidades.guardar_con_evento(unidad, evento)`
      persiste ambos en una única transacción SQLAlchemy (`merge`+`add`+
      `commit`, con `rollback()` explícito si falla), sobre una nueva tabla
      `eventos_auditoria`. Nuevo endpoint `GET /eventos-auditoria` (mismo
      criterio que `GET /eventos-seguridad`). Atomicidad verificada con un
      test real (violación de restricción NOT NULL, no un doble simulado) en
      `tests/test_persistencia.py` — confirma que si el evento no se anexa, la
      unidad tampoco queda persistida. 35/35 tests de `normalizacion`
      (18 dominio + 15 API + 2 persistencia), `./test.sh` completo verde.
      Colección Postman "5. Normalizacion" ampliada con `actor`/`fecha` en los
      cuerpos afectados y una petición nueva (42) contra
      `/eventos-auditoria`; revalidada con los cinco servicios corriendo a la
      vez — 43/43 peticiones, 81/81 aserciones, dos corridas seguidas sin
      fallos. `specs/spec-infra-servicios.md` §7 actualizada.
- [x] T-38 RF-VH-005 / RF-NO-004 (cierra el ciclo RF-VH-005, decisión de
      Victor 2026-08-27) — nuevo puerto `ConfirmadorDeLimites` +
      `GestionDeLimites` (verifica permiso `confirmar`/`documento`) en el
      dominio de Validación Humana; adaptador HTTP real
      `ConfirmadorDeLimitesHttp` contra `POST /unidades/{id}/confirmacion-
      limites` de Normalización (primer consumidor real de ese endpoint);
      nuevo endpoint simétrico `POST /unidades/{unidadId}/confirmacion-
      limites` en Validación Humana (`LimitesController`). 23/23 tests
      Kotlin del módulo (2 dominio + 2 integración + 2 HTTP nuevos),
      `./test.sh` completo verde.
      **Bug real encontrado y corregido, no cubierto por
      `MockRestServiceServer`** (que nunca abre un socket real): el
      `RestTemplate` de Validación Humana usaba por defecto
      `JdkClientHttpRequestFactory` (Spring Boot 3.5, sin Apache
      HttpComponents/Jetty en el classpath), cuyo `HttpClient` intenta un
      upgrade h2c en texto plano que Tomcat ignora pero que `uvicorn`
      (Normalización) rechaza como petición inválida (`400`, sin enrutarla
      siquiera a FastAPI) — reproducido contra el stack Docker real con
      logging DEBUG. Corregido fijando `HttpClient.Version.HTTP_1_1`
      explícito; ver `specs/spec-infra-servicios.md` §6 para el diagnóstico
      completo (relevante para los cuatro contextos Python restantes).
      Colección Postman: rol de la carpeta 4 (T-32) ampliado con el permiso
      `confirmar`/`documento`; carpeta nueva "6. Cierre RF-VH-005" (peticiones
      43-45) — segundo flujo end-to-end real del proyecto, esta vez entre
      Validación Humana y Normalización. Revalidada con los cinco servicios
      corriendo a la vez — la primera corrida encontró el bug de arriba;
      corregido y confirmado con dos corridas seguidas limpias después:
      46/46 peticiones, 87/87 aserciones.
      `specs/spec-infra-servicios.md` §6 y §9/§10 actualizadas (RF-VH-005 ya
      no aparece como brecha abierta).
- [x] T-39 RF-VH-001 / RF-VH-009 (decisión de Victor 2026-08-27, "Si, sigamos
      con T-39") — cola de límites en Validación Humana (parcial: Normalización
      sí, Extracción/Enriquecimiento no porque no existen como servicios) y
      correcciones expuestas como candidatas a re-revisión.
      **RF-VH-001/002/010**: nuevo `GET /unidades/pendientes-de-limites` en
      Normalización (`dominio.pendientes_de_limites`, filtra `PENDIENTE_DE_
      LIMITES` con sugerencia ya recibida — mismo criterio que
      `sugerenciasPendientes` en records-custodia, T-28); nuevos
      `UnidadPendienteDeLimites`/`FuenteDeSugerenciasDeLimites`/`ColaDeLimites`
      en el dominio de Validación Humana + adaptador HTTP real
      `FuenteDeSugerenciasDeLimitesHttp` (primer consumidor de ese endpoint;
      primera vez que Jackson necesita mapear JSON snake_case explícito,
      `@JsonProperty`) + endpoints `GET /colas/limites` y
      `GET /colas/limites/estado`. Sin `/masivo`: el `[CLARIFICAR]` de la spec
      sobre aprobación masiva para sugerencias no-clasificación sigue abierto.
      **Extracción y Enriquecimiento quedan fuera, documentado explícitamente
      en spec-infra-servicios.md §10**: no existen como servicios todavía
      (solo `main.py`/`pyproject.toml` de andamiaje), no es una decisión de
      negocio ni un `[CLARIFICAR]`, es una dependencia real que falta.
      **RF-VH-009**: `EventoAuditoria`/`DecisionHumana` en records-custodia
      ganan `esCorreccion: Boolean` (default `false`); `materializar` lo
      persiste; nuevo `correccionesPendientesDeRerevision()` +
      `GET /documentos/correcciones`, cada entrada marcada
      `estadoDeRevision: "PENDIENTE_DE_REREVISION"`. Validación Humana ya
      calculaba esto (`GestionDeDecisiones.construirDecision` → `TipoDe
      Decision`) y lo descartaba; ahora `RegistradorDeDecisionesHttp.
      materializar` lo envía. El mecanismo real de re-revisión sigue
      `[CLARIFICAR]` (`specs/eval/edd-harness.md` §9, ya lo estaba antes de
      esta tarea) — esto solo expone las candidatas, no decide cómo se
      promueven a verdad de referencia.
      TDD: 3 tests nuevos en `normalizacion` (40/40 en el módulo); 8 tests
      nuevos en `validacion-humana` (31/31 en el módulo: dominio, integración
      con `MockRestServiceServer` — incluida una prueba real del mapeo
      snake_case — y HTTP); 3 tests nuevos en `records-custodia` (37/37 en el
      módulo: dominio y HTTP). `./test.sh` completo del repo en verde.
      **Dos bugs reales encontrados y corregidos en la verificación contra
      Docker, ninguno detectado por los tests de Gradle/pytest** (que usan
      dobles/H2 con `create-drop`, no Postgres real con datos existentes):
      (1) ruteo — `GET /unidades/pendientes-de-limites` debía declararse
      ANTES de `GET /unidades/{id}` en FastAPI/Starlette (resuelve por orden
      de declaración, a diferencia de Spring MVC), o "pendientes-de-limites"
      se interpretaba como un `{id}` literal; (2) DDL — agregar
      `es_correccion boolean not null` sin `DEFAULT` generó un `ALTER TABLE`
      que Postgres rechaza sobre una tabla con filas existentes ("contains
      null values"), corregido con `columnDefinition = "boolean not null
      default false"`.
      Colección Postman: rol de la carpeta 4 ampliado (ya tenía
      `confirmar`/`documento` desde T-38); carpeta 6 renombrada y ampliada de
      3 a 11 peticiones (43-53) — cola de límites (46-49) y corrección
      pendiente de re-revisión (50-53). Revalidada con los cinco servicios
      corriendo a la vez — la primera corrida encontró el bug de DDL de
      arriba (reiniciando el volumen de Postgres para partir de un esquema
      limpio); corregido y confirmado con dos corridas seguidas limpias
      después: 54/54 peticiones, 97/97 aserciones.
      `specs/spec-infra-servicios.md` §4/§6/§7/§9/§10 actualizadas.

# Extracción

Decisión de Victor, 2026-08-27: "continuar con Extracción en modo agéntico"
vía `./orquestador.sh loop` — esta vez con revisión de Codex tras cada
commit, como en T-01..T-22. Sigue `specs/002-extraccion/spec.md`
(RF-EX-001..010) y el mismo patrón que Normalización (T-33..T-36): contexto
híbrido, Python/FastAPI, con OCR como componente FICTICIO (nunca se
implementa un motor de OCR real — constitución, disciplina de alcance).

Lecciones ya aprendidas en Normalización/Validación Humana que NO deben
repetirse aquí (ver STATE.md para el detalle completo de cada una):
- P-08 desde el inicio (hallazgo V-01, T-37): cada función de transición de
  dominio debe devolver también un `EventoAuditoria` (actor, fecha, tipo,
  estado_anterior, estado_posterior) — no lo agregues como fix posterior.
- En FastAPI/Starlette, cualquier ruta GET literal (p. ej.
  `/textos-extraidos/pendientes-de-revision`) debe declararse ANTES que su
  ruta hermana con `{id}` (resuelve por orden de declaración, a diferencia
  de Spring MVC) — hallazgo real de T-39.
- Nunca inventar el umbral de calidad, el motor de OCR concreto, ni el
  mecanismo de re-revisión de correcciones de texto — los tres están
  `[CLARIFICAR]` en `specs/002-extraccion/spec.md` §8; recíbelos como
  parámetro del llamador o déjalos fuera de alcance, nunca un valor fijo
  inventado.
- [x] T-40 RF-EX-001..010 — dominio de Extracción en Python
      (`contexts/extraccion/dominio.py`). Implementado siguiendo el mismo
      patrón que `contexts/normalizacion/dominio.py` (T-33): estados
      `PENDIENTE_DE_EXTRACCION` -> `EXTRAIDO` (terminal de éxito) |
      `RECHAZADO` | `EN_CUARENTENA` (terminales de fallo); `CondicionDeExtraccion`
      (CORRUPTO/ILEGIBLE -> En cuarentena, FORMATO_NO_SOPORTADO -> Rechazado,
      mismo mapeo ya ratificado para RF-CI-006); `determinar_soporte`
      (BORN_DIGITAL/ESCANEO, RF-EX-002, no cambia estado, solo marca);
      `extraer_texto_born_digital` (RF-EX-003, calidad 1.0, nunca invoca OCR);
      `recibir_resultado_ocr` (RF-EX-004 — componente FICTICIO, recibe un
      `ResultadoOcr` YA CALCULADO por el llamador, actor = modelo_id, mismo
      criterio que T-20); calidad/soporte expuestos como campos del agregado
      (RF-EX-005); `candidatas_a_revision_por_baja_confianza(textos, umbral)`
      con umbral recibido como parámetro, nunca inventado (RF-EX-006);
      `ProcedenciaHeredada` propagada sin tocar en ninguna transición
      (RF-EX-007); `entregar()` valida estado `Extraído` y devuelve el mismo
      texto sin diferenciar por consumidor (RF-EX-010); `contar_por_estado`
      para cero pérdida silenciosa (RF-EX-008). Cada función de transición
      devuelve `(TextoExtraido, EventoAuditoria)` desde el primer commit — P-08
      no fue un fix posterior aquí. `uv run --directory contexts/extraccion
      pytest` agregado a `test.sh` en este mismo commit.
      TDD: 25 tests nuevos (`tests/test_dominio.py`), uno por rama de cada
      Dado/Cuando/Entonces de RF-EX-001..010 más una clase de auditoría de
      transiciones (P-08); verdes en el primer intento. `./test.sh` completo
      en verde (Gradle BUILD SUCCESSFUL — 25 tareas up-to-date; pytest:
      eval-harness 4 passed, normalizacion 40 passed, extraccion 25 passed).
      **VETO real de Codex sobre este commit (`dd97fb4`)**: `recibir_resultado_ocr`
      materializaba `Extraído` directo desde un resultado probabilístico, sin
      Sugerencia ni decisión humana (P-01). Corregido tras decisión de Victor
      (2026-08-27, ver `QUESTIONS.md`): `recibir_resultado_ocr` ahora solo
      adjunta el resultado (`TextoExtraido.resultado_ocr`), sin tocar estado;
      nueva `confirmar_extraccion(texto, actor, fecha)` (RF-EX-011, nueva RF)
      es la única que materializa `Extraído`. También corregido un hallazgo
      secundario de la misma revisión: `marcar_cuarentena_o_rechazo` ahora
      exige `Pendiente de extracción` como precondición (no admitía
      transiciones desde un estado ya terminal). 29/29 tests en el módulo tras
      el fix. `specs/002-extraccion/spec.md` actualizada (§1, RF-EX-004,
      RF-EX-011 nueva, §7).
      **Segunda vuelta de Codex sobre ese mismo fix (commit `e623ad6`): VETO
      mantenido** — aplazar la materialización no bastaba; lo adjunto al
      agregado (`ResultadoOcr`, sin `evidencia`) seguía sin forma de
      `Sugerencia` (P-01 exige cruzar la capa anticorrupción *como
      Sugerencia*, no solo diferir la decisión). También señaló un defecto de
      P-08: el evento de recepción usaba un sentinel que no es un valor real
      de `EstadoTextoExtraido`. No requirió nueva decisión de Victor — es
      consistencia con el patrón ya ratificado. Corregido: `ResultadoOcr` →
      `SugerenciaOcr` con `evidencia: list[str]` (mismo shape que
      `SugerenciaDeLimites`/`Sugerencia`); `recibir_resultado_ocr` →
      `recibir_sugerencia_ocr`; eventos de `recibir_sugerencia_ocr` y
      `determinar_soporte` (mismo defecto, corregido por consistencia) ahora
      usan `estado_anterior=estado_posterior=texto.estado.value` en vez de un
      sentinel inventado. 29/29 tests tras el segundo fix. Ver commit
      siguiente para el detalle completo.
- [x] T-41 RF-EX-001..011 — Servicio HTTP (FastAPI) + persistencia
      (SQLAlchemy + Postgres) para Extracción, mismo patrón que T-34
      (Normalización): cada endpoint traduce un método de dominio ya
      probado, incluido `POST .../confirmacion` para `confirmar_extraccion`
      (RF-EX-011); `guardar_con_evento(texto, evento)` persiste ambos en una
      única transacción con rollback explícito si falla (mismo criterio que
      `AlmacenDeUnidades` en Normalización, T-37 — verificado con un test
      real de violación NOT NULL, no un doble simulado); `GET
      /eventos-auditoria` desde el principio; `GET
      /textos/pendientes-de-revision?umbral=` declarado ANTES de `GET
      /textos/{id}` (mismo criterio que `GET /sugerencias/pendientes`, T-28,
      y `GET /unidades/pendientes-de-limites`, T-39). Variables de entorno
      idénticas a los otros contextos Python:
      `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`.
      **Nota operativa real sobre esta tarea**: implementada por el loop
      headless (`./orquestador.sh loop`), que produjo el código completo
      (53/53 tests) pero quedó bloqueado sin comitear — `bash ./test.sh` y
      `./gradlew test` no calzan con el patrón `--allowedTools
      "Bash(./test.sh *)"` de `orquestador.sh` (solo matchea la forma
      literal `./test.sh`, sin prefijo `bash`), y el proceso terminó
      preguntando algo que nadie podía responder en una sesión headless. El
      trabajo quedó sin comitear en el árbol de trabajo; retomado en sesión
      interactiva: inspeccionado, verificado con `./test.sh` completo
      (Gradle + los tres módulos Python, incluido extraccion 53/53) y
      comiteado. `specs/spec-infra-servicios.md` §11 (nueva) escrita — no
      existía todavía cuando el loop headless comiteó T-40, así que los
      comentarios de `api.py`/`persistencia.py` que decían "§8" (sección
      equivocada — esa es "Formato de error") se corrigieron a "§11".
- [x] T-41b RF-EX-011 / P-03 — corrige el VETO de Codex sobre commit `cf93d84`
      (REVIEW.md lo dejó sin comitear: el mensaje del commit decía "Codex
      confirma OK" pero una revisión posterior sobre ese mismo commit
      encontró que `confirmar_extraccion` acepta cualquier `str` como actor
      sin verificar autorización — RF-EX-011 es el único de los tres RF
      equivalentes del proyecto cuyo criterio dice literalmente "un actor
      **autorizado**", no solo "una decisión humana"/"un humano"). Ver
      QUESTIONS.md 2026-08-27 y STATE.md para el detalle completo. Corregido:
      puerto `VerificadorDeAutorizacion` (P-03, `dominio.py`) +
      `AccesoDenegadoError` (403); `confirmar_extraccion` lo exige y rechaza
      antes de materializar. Implementación real: `integracion.py`,
      `VerificadorDeAutorizacionHttp` contra `POST /autorizacion` de
      seguridad-acceso (primer consumidor Python de ese endpoint,
      `SEGURIDAD_ACCESO_BASE_URL`). TDD: 2 tests de dominio (permitido/
      denegado) + 1 test HTTP (403), 55/55 tests en el módulo. `./test.sh`
      completo del repo en verde. `specs/spec-infra-servicios.md`
      (§9/§10/§11) y `specs/002-extraccion/spec.md` (§1/§7) actualizadas.
- [x] T-42 Dockerfile real de extraccion + wiring en
      `docker-compose.{saas,onprem,local-ports}.yml`, mismo patrón que T-35
      (Normalización): build en dos etapas con `uv sync --no-dev --frozen`;
      Postgres propio; puerto siguiente disponible (8086) en
      `docker-compose.local-ports.yml`. `SEGURIDAD_ACCESO_BASE_URL` cableada
      en el servicio (RF-EX-011/T-41b, `VerificadorDeAutorizacionHttp` contra
      `POST /autorizacion`); `depends_on: [postgres, seguridad-acceso]`.
      T-42 es infraestructura de empaquetado (como T-18/T-35), no un RF con
      Dado/Cuando/Entonces propio — verificación por honestidad: `./test.sh`
      completo en verde (Gradle 25 tareas; pytest: eval-harness 4,
      normalizacion 40, extraccion 55, todos passed) confirma que
      Dockerfile/compose no rompieron nada existente. Mismo límite que
      T-16..T-18/T-35: `docker` no está instalado en este entorno, así que la
      imagen y `docker compose up` reales no se construyeron ni se corrieron
      aquí — falta esa verificación de punta a punta (junto con T-43) cuando
      alguien la corra en un entorno con Docker disponible.
- [x] T-43 Colección Postman con el ciclo completo de Extracción, mismo
      patrón que T-36 (Normalización): recepción -> determinación de
      soporte -> extracción (born-digital y vía OCR ficticio) -> conteo por
      estado -> consulta de la bitácora de auditoría. Carpeta nueva
      "7. Extraccion" (peticiones 54-67): setup de identidad autorizada en
      seguridad-acceso (RF-EX-011/P-03, mismo patrón que la carpeta 4/T-38);
      unidad born-digital (extracción determinística, calidad 1.0); segunda
      unidad por escaneo con sugerencia de OCR ficticia que NO materializa
      por sí sola (P-01), confirmada primero con un actor sin permiso ->
      `403` y luego con el actor autorizado -> `Extraído` (frontera real de
      autorización, RF-EX-011); tercera unidad `CORRUPTO` -> `En cuarentena`
      (RF-EX-009) para que el conteo final cuadre con tres unidades
      terminales; y `GET /eventos-auditoria` (P-08).
      **Construida por el loop headless, comiteada en sesión interactiva**
      (mismo patrón que T-41): el loop headless completó la colección
      correctamente pero se negó, con razón, a marcarla terminada o
      comitearla — T-43 exige verificar con Docker/Newman reales, y
      `docker compose`/`npx` no están en el `--allowedTools` de
      `orquestador.sh` (a propósito, por seguridad). Dejó el trabajo sin
      comitear y una explicación clara de por qué en vez de fingir éxito —
      comportamiento correcto, no un error. Retomado en sesión interactiva:
      levantado el stack Docker real (`docker compose ... up -d --build`,
      incluido el contenedor `extraccion` por primera vez), verificado con
      Newman dos corridas seguidas: **68/68 peticiones, 113/113 aserciones**,
      limpio desde la primera corrida (incluida la frontera de autorización
      403/200). `postman/README.md` actualizado con los conteos reales y el
      resultado de la verificación.

# Clasificación

Decisión de Victor, 2026-08-28 ("Sigamos con alguno de los contextos que nos
hace falta" → confirmó Clasificación entre las tres opciones). Sigue
`specs/003-clasificacion/spec.md` (RF-CL-001..010).

**Hallazgo arquitectónico real, léelo antes de empezar T-44** (evita
re-descubrirlo o inventar persistencia que la spec no pide):
- §3 de la spec dice explícitamente "Este contexto **no mantiene estado
  propio** de sus sugerencias después de entregarlas". A diferencia de
  Normalización/Extracción (que SÍ tienen un agregado persistido con
  máquina de estados), Clasificación es como Validación Humana: un
  orquestador HTTP **sin Postgres propio**, que produce sugerencias
  (componente FICTICIO) y las reenvía de verdad a records-custodia. No crees
  `persistencia.py` ni una tabla — sería inventar estado que la spec dice
  explícitamente que no existe.
- Records-custodia YA expone `POST /sugerencias` (T-08) con forma genérica
  (`documentoId, tipo, contenidoPropuesto, modeloId, evidencia, confianza,
  fecha`) — `tipo` es un `String` libre, así que acepta tanto
  `tipo="clasificacion"` (serie/subserie en `contenidoPropuesto`) como
  `tipo="agrupamiento"` (expediente propuesto, o el marcador de "expediente
  nuevo", en `contenidoPropuesto`) SIN ningún cambio en records-custodia. No
  hace falta tocar Kotlin para esta tarea.
- La spec (§8) señala una inconsistencia real: `spec-records-custodia.md`
  RF-RC-008 (conformación del expediente electrónico) **no está
  implementado en código** (`grep` sobre `contexts/records-custodia/src` no
  encuentra ningún `Expediente`) — es una brecha preexistente, documentada,
  no algo que esta tarea deba resolver. La sugerencia de agrupamiento se
  envía igual (records-custodia ya la almacena como `Sugerencia` genérica),
  solo que todavía no hay un agregado `Expediente` que la materialice.
- La spec dice literalmente: "el evento de auditoría de la recepción de la
  sugerencia ya lo emite Records/Custodia al recibirla — Clasificación no
  duplica ese evento por su lado" (T-20 ya lo implementó ahí). Este contexto
  **no necesita su propio P-08** — no hay estado propio que transicionar.
- RF-CL-003 ordena por confianza **descendente** (mayor primero — la mejor
  candidata primero), a diferencia de `ColaDeRevision`/`ColaDeLimites` en
  Validación Humana, que ordenan ascendente (más incierto primero, para
  revisión). No es un error si el orden sale al revés del patrón de VH —
  es intencional, verifícalo contra el criterio Dado/Cuando/Entonces del RF,
  no contra el código de VH.
- Nunca inventar cuántas sugerencias top-N emitir por documento, ni qué
  cuenta exactamente como "no clasificable" — ambos están `[CLARIFICAR]` en
  `specs/003-clasificacion/spec.md` §8. El llamador declara explícitamente
  si un texto es "no clasificable" (mismo criterio que
  `CondicionDeExtraccion`/`CondicionDeNormalizacion`/`CondicionValidacion` en
  los otros tres contextos), el dominio no lo detecta.
- Como en Extracción (T-41b), OCR/clasificación son componentes FICTICIOS:
  este contexto nunca calcula una clasificación ni un agrupamiento reales,
  solo recibe un modelo_id/evidencia/confianza YA CALCULADOS por el llamador.

- [x] T-44 RF-CL-001..010 — dominio de Clasificación en Python
      (`contexts/clasificacion/dominio.py`). `recibir_texto_extraido`
      (RF-CL-001) es la única puerta de entrada — `clasificar`/`agrupar`/
      `marcar_no_clasificable` solo aceptan el `TextoDisponible` que
      devuelve. `SugerenciaDeClasificacion` y `SugerenciaDeAgrupamiento`
      (componentes FICTICIOS, ambos con modelo_id/evidencia/confianza/fecha
      obligatorios en el constructor — invariante 3 estructural, no
      validada en runtime, mismo criterio que `Sugerencia` en
      records-custodia); `MarcaNoClasificable` (RF-CL-010, razón declarada
      por el llamador); `ordenar_por_confianza(sugerencias)` DESCENDENTE
      (RF-CL-003, ver nota arriba); cada sugerencia referencia la versión de
      TRD vigente al momento de generarse, inmutable después (RF-CL-009 —
      dataclass frozen, no se recalcula si se publica una TRD nueva).
      `a_sugerencia_saliente_de_clasificacion`/`_de_agrupamiento`
      (RF-CL-004/006): traducción pura a la forma genérica que ya acepta
      `POST /sugerencias` de records-custodia (`SugerenciaEntrante`, T-08) —
      sin llamada HTTP, eso es T-45. Test estructural para RF-CL-007: el
      módulo no expone ninguna operación de materialización/aprobación,
      mismo criterio que T-09. Sin agregado persistido, sin
      `EventoAuditoria` propio (ver nota arquitectónica arriba de por qué).
      `uv run --directory contexts/clasificacion pytest` agregado a
      `test.sh` en este mismo commit (lección real de T-40). TDD: 14 tests
      nuevos (`tests/test_dominio.py`), verdes en el primer intento.
      `./test.sh` completo del repo en verde (Gradle BUILD SUCCESSFUL;
      pytest: eval-harness 4, normalizacion 40, extraccion 55,
      clasificacion 14).
- [x] T-45 RF-CL-001..010 — Servicio HTTP (FastAPI) para Clasificación, SIN
      persistencia propia (ver nota arquitectónica arriba), implementado
      contra `specs/spec-infra-servicios.md` §12 (nueva). Tres endpoints:
      `POST /clasificaciones` (acepta una o más candidatas de serie/subserie
      en un solo cuerpo — necesario para que RF-CL-003 pueda ordenar por
      confianza descendente sobre un conjunto conocido, ya que el contexto no
      guarda estado entre peticiones), `POST /agrupamientos` (una sola
      candidata, ningún RF exige ranking de expedientes) y
      `POST /no-clasificables` (RF-CL-010, no reenvía nada — su destino es
      "Operador"/reporte, no Records/Custodia, según la tabla de salidas §4
      de la spec). Cada endpoint compone las funciones puras de
      `dominio.py` (T-44: `recibir_texto_extraido`, `clasificar`,
      `ordenar_por_confianza`, `agrupar`, `marcar_no_clasificable`,
      `a_sugerencia_saliente_de_*`) y reenvía con `EnviadorDeSugerenciasHttp`
      (`integracion.py`, cliente `httpx` real) contra `POST /sugerencias` de
      records-custodia, con el cuerpo camelCase exacto que espera
      (`RecibirSugerenciaRequest`). Variable de entorno:
      `RECORDS_CUSTODIA_BASE_URL`. Manejo de errores: `ErrorDeDominio` → 409,
      `ServicioNoDisponibleError` (records-custodia no responde) → 502,
      mismo criterio que `validacion-humana`.
      TDD: 3 tests nuevos de integración (`tests/test_integracion.py`, con
      `httpx.MockTransport` — verifica método/URL/cuerpo JSON exactos de la
      petición saliente, no un doble que nunca abre conexión, cerrando la
      brecha real que dejó `extraccion`/T-41b sin este tipo de prueba) + 7
      tests nuevos de API (`tests/test_api.py`, con doble vía
      `dependency_overrides`), 24 en el módulo junto con los 14 de dominio
      (T-44). Confirmado en rojo quitando la llamada a `enviador.enviar` en
      `POST /clasificaciones` (3 tests fallan) antes de restaurarla en verde.
      `./test.sh` completo del repo en verde (Gradle BUILD SUCCESSFUL;
      pytest: eval-harness 4, normalizacion 40, extraccion 55,
      clasificacion 24).
- [x] T-45-corrección RF-CL-010 / P-03 — VETO real de Codex sobre commit
      `17642a7` (ver REVIEW.md), corregido interactivamente sin requerir
      nueva decisión de Victor (consistencia con un patrón ya ratificado, no
      un fork de negocio nuevo): (1) P-03 — nuevo puerto
      `dominio.EnviadorDeSugencias` (`Protocol`, mismo criterio que
      `VerificadorDeAutorizacion` en extraccion/T-41b);
      `EnviadorDeSugerenciasHttp` (`integracion.py`) ahora lo implementa
      explícitamente; `api.py` (`obtener_enviador`, ambos `Depends(...)`)
      tipa contra el puerto, nunca contra la clase concreta. (2) RF-CL-010 —
      nueva función pura `dominio.exigir_al_menos_una_candidata`: rechaza
      con `ErrorDeDominio` (409) una lista de candidatas vacía en
      `POST /clasificaciones`, en vez de responder 201 con `[]` sin ninguna
      salida explícita; no inventa el criterio de negocio de qué es "no
      clasificable" (spec §8, sigue `[CLARIFICAR]`), solo cierra el hueco de
      la API. TDD: 3 tests nuevos (2 de dominio + 1 de API), confirmados en
      rojo antes de implementar; 27/27 tests del módulo en verde.
      `./test.sh` completo del repo en verde (Gradle BUILD SUCCESSFUL;
      pytest: eval-harness 4, normalizacion 40, extraccion 55,
      clasificacion 27).
- [x] T-46 Dockerfile real de clasificacion + wiring en
      `docker-compose.{saas,onprem,local-ports}.yml`, mismo patrón Python
      que T-35/T-42 pero SIN Postgres propio (mismo criterio que
      validacion-humana, T-31, sin `DB_HOST`/etc. — solo
      `RECORDS_CUSTODIA_BASE_URL` y `depends_on: records-custodia`); puerto
      8087 en `docker-compose.local-ports.yml`.
      **Hallazgo real antes de escribir el Dockerfile**: `main.py` seguía
      siendo el stub sin tocar de la etapa 0 (`print("Hello from
      clasificacion!")`) — T-44/T-45 nunca lo tocaron porque los tests usan
      `TestClient`/`MockTransport` importando `api.app` directo, sin pasar
      nunca por `main.py`. Sin corregirlo, el `ENTRYPOINT` del contenedor
      habría impreso un saludo y salido, sin servir HTTP. Corregido al mismo
      patrón que `contexts/normalizacion/main.py` y
      `contexts/extraccion/main.py`: `uvicorn.run(app, ...,
      port=int(os.environ.get("SERVER_PORT", "8087")))`.
      `contexts/clasificacion/Dockerfile`: build en dos etapas con `uv sync
      --no-dev --frozen` (mismo patrón que normalizacion/extraccion), pero
      sin `persistencia.py` en la etapa final — este contexto no tiene tabla
      propia (specs/003-clasificacion/spec.md §3). Wiring en
      `docker-compose.{saas,onprem}.yml`: sin `DB_HOST`/Postgres, solo
      `RECORDS_CUSTODIA_BASE_URL` y `depends_on: [records-custodia]` — mismo
      criterio que `validacion-humana` (T-31).
      T-46 es infraestructura de empaquetado (como T-18/T-35/T-42), no un RF
      con Dado/Cuando/Entonces propio. Verificación de honestidad: `./test.sh`
      completo en verde tras el cambio (Gradle 25 tareas BUILD SUCCESSFUL;
      pytest: eval-harness 4, normalizacion 40, extraccion 55, clasificacion
      27, todos passed) confirma que el fix de `main.py` y el Dockerfile no
      rompieron nada existente; los tres `docker-compose*.yml` se revisaron
      línea por línea contra la indentación/estructura ya usada por los otros
      seis servicios (no se pudo correr un parser YAML automatizado ni
      `docker compose config` en esta sesión — `python`/`python3` sueltos y
      `docker` quedan fuera del conjunto de comandos permitido; mismo límite
      real que T-16..T-18/T-35/T-42 documentaron para Docker). Falta la
      verificación de punta a punta con Docker/Postman real (junto con T-47)
      cuando alguien la corra en un entorno con Docker disponible.
- [x] T-47 Colección Postman con el ciclo completo de Clasificación
      (carpeta "8. Clasificacion", peticiones 68-74). El loop headless dejó
      el borrador de la colección construido pero sin comitear durante seis
      sesiones consecutivas (docker compose/npx fuera del allowlist, mismo
      límite real que T-43 — documentado seis veces en STATE.md, cada vez
      confirmado por Codex como no ambiguo, no un `[?]`/QUESTIONS.md).
      Retomado interactivamente: revisado el borrador (correcto, mismo
      patrón que las carpetas 4/6/7), levantado el stack real de los siete
      servicios (`docker compose -f deploy/docker-compose.saas.yml -f
      deploy/docker-compose.local-ports.yml up -d --build`) y verificado con
      dos corridas de Newman seguidas sin fallos: 75/75 peticiones, 120/120
      aserciones. Flujo: custodiar un documento en records-custodia →
      clasificar con dos candidatas FICTICIAS, verificar orden descendente
      por confianza (RF-CL-003) en la respuesta y en
      `GET /documentos/{id}/sugerencias` → agrupar a un expediente
      propuesto (RF-CL-005/006), mismo endpoint → marcar no clasificable
      (RF-CL-010) y verificar que el conteo de sugerencias en
      records-custodia no cambia. Segundo flujo end-to-end real de solo dos
      servicios (Clasificación no tiene persistencia propia). `postman/
      README.md` actualizado (conteo de endpoints, carpeta 8, verificación).
- [x] T-48 P-08 — records-custodia no exponía ningún endpoint para consultar
      su propia bitácora de auditoría (a diferencia de
      normalizacion/extraccion, que sí tienen `GET /eventos-auditoria` sobre
      su propio contexto). Alcance real corregido respecto a la propuesta
      original de Codex (asumía que ya existía un método de consulta y solo
      faltaba el endpoint HTTP; en realidad `BitacoraAuditoria`/
      `CustodiaOriginales.eventosDeAuditoria` ya exponían la lista completa
      desde T-10 — no hizo falta tocarlos): (1) `EventosAuditoriaController`
      nuevo (`GET /eventos-auditoria`, mismo patrón que
      `EventosSeguridadController` de seguridad-acceso) devuelve
      `custodia.eventosDeAuditoria` — como `RecordsCustodiaConfig` comparte
      una única `BitacoraAuditoria` entre `CustodiaOriginales` y
      `CapaAnticorrupcionSugerencias` (T-20), incluye también los
      `SUGERENCIA_RECIBIDA` que otros contextos generan al reenviar una
      sugerencia vía `POST /sugerencias` (Clasificación, T-44..T-47). TDD: 1
      test nuevo en `RecordsCustodiaHttpTest.kt` (rojo con
      `HttpMessageNotReadableException` antes del controlador, verde
      después). (2) `specs/spec-infra-servicios.md` §4 actualizada. (3)
      Colección Postman: petición 75 en la carpeta "8. Clasificacion",
      verifica que las sugerencias de clasificación/agrupamiento (peticiones
      69/71) aparecen en la bitácora atribuibles a
      `clasificador-ficticio-v1`/`agrupador-ficticio-v1`.
      Implementado por el loop headless (código + tests + spec + borrador de
      Postman), dejado sin comitear a propósito por el mismo límite real que
      T-41/T-43/T-47 (`docker compose`/`npx` fuera del `allow` de
      `.claude/settings.local.json`, sin humano presente para aprobar).
      Retomado interactivamente: `./gradlew :contexts:records-custodia:test
      --rerun-tasks` en verde; stack Docker real de los siete servicios
      levantado y verificado con Newman dos corridas seguidas sin fallos:
      **76/76 peticiones, 121/121 aserciones**. `postman/README.md`
      actualizado (49/57 endpoints cubiertos, conteo real de la
      reverificación).
      **De paso, corregido un VETO real de Codex sobre un commit previo y no
      relacionado** (`4eb7497`, cambio en `orquestador.sh` del mecanismo de
      handoff a Codex por rate limit sostenido, hecho antes de esta sesión y
      nunca revisado): el commit afirmaba una verificación manual "con un
      claude simulado" sin dejar ninguna prueba reproducible. Corregido con
      `test-run-claude.sh` (doble real de `claude` en PATH, no amañado —
      detectó y corrigió un bug propio del doble antes de pasar en verde),
      cubre los tres códigos de retorno de `run_claude()` (0/2/1) y el punto
      exacto del handoff; no wireado a `test.sh` (toma ~90s por los backoffs
      reales de la rama de fallo genérico). Codex confirmó OK sobre el
      commit de corrección.

# Enriquecimiento

Decisión de Victor, 2026-08-29 ("Continuemos con el contexto de
Enriquecimiento"). Sigue `specs/004-enriquecimiento/spec.md` (RF-EN-001..010).
Mismo patrón que Normalización/Extracción/Clasificación: contexto híbrido,
Python/FastAPI (ya scaffolded en `contexts/enriquecimiento/`, miembro del
workspace uv, wiring documentado en `docker-compose.saas.yml`), sin
Postgres propio (spec §3: "igual que Clasificación, este contexto no
mantiene estado propio de sus sugerencias después de entregarlas" — mismo
criterio que validacion-humana/clasificacion, T-31/T-46).

**Dos `[CLARIFICAR]` reales en `specs/004-enriquecimiento/spec.md` §8, NO
resueltos aquí — no bloquean el corte vertical, pero definen su alcance**:
1. El esquema exacto de metadatos obligatorios (campos, formatos) — hereda
   el mismo `[CLARIFICAR]` de `spec-records-custodia.md` §8. Igual que
   Clasificación nunca inventó cuántas sugerencias top-N emitir, este
   contexto nunca inventa nombres de campo reales ("fecha", "remitente",
   etc.) como si fueran el esquema canónico: el "campo de metadato" es una
   cadena que el LLAMADOR declara al enviar el texto a evaluar (mismo
   criterio que `CondicionDeExtraccion`/`CondicionValidacion` en los otros
   contextos — el dominio no decide la taxonomía, la recibe).
2. Si Enriquecimiento depende de una clasificación (serie) confirmada o
   sugerida para saber qué campos son obligatorios, o si extrae un conjunto
   común de campos independiente de la serie — tensión real entre dos specs
   ya escritas, sin resolver. Se sortea de la misma forma: el llamador
   declara explícitamente qué campos evaluar en cada llamada: el dominio no
   decide "cuáles son los obligatorios para esta serie".
   Si en algún punto una tarea necesitara resolver esto de verdad (no solo
   sortearlo) para avanzar, es un `[CLARIFICAR]` real — QUESTIONS.md, no
   inventar.

**Brecha de implementación ya documentada en la spec (§8, no es tarea de
esta lista)**: `DocumentoDeArchivo`/`DecisionHumana` en records-custodia
solo modelan `clasificacionResultante`, no un campo de metadatos — la
materialización de metadatos no tiene dónde aterrizar todavía. No bloquea:
la sugerencia de todas formas llega a records-custodia como `Sugerencia`
genérica vía `POST /sugerencias` (mismo `tipo` libre que ya usa
Clasificación, `tipo="metadato"`, sin tocar Kotlin) — mismo criterio que la
brecha de RF-RC-008/Expediente que Clasificación dejó documentada y sin
resolver.

**Lecciones ya aprendidas en Normalización/Extracción/Clasificación que NO
deben repetirse aquí** (ver STATE.md para el detalle completo de cada una):
- P-08 desde el inicio (T-37): cada función de transición de dominio debe
  devolver también un `EventoAuditoria` — no agregarlo como fix posterior.
- En FastAPI/Starlette, cualquier ruta GET literal (p. ej.
  `/textos/pendientes-de-revision`) debe declararse ANTES que su ruta
  hermana con `{id}` (T-39/T-41).
- Persistencia atómica agregado+evento en una sola transacción con rollback
  explícito (`guardar_con_evento`, T-37) — aunque este contexto no persiste
  agregados propios (spec §3), si alguna tarea introduce persistencia real
  en algún punto, aplica el mismo patrón desde el primer commit.
- P-03: toda capacidad externa (records-custodia, seguridad-acceso si hace
  falta autorización) detrás de un puerto/`Protocol` propio, nunca
  tipado contra la clase HTTP concreta (T-45-corrección).
- RF-CL-010 (Clasificación) enseñó a no responder 200/201 con una lista
  vacía sin salida explícita cuando el criterio de negocio exige una acción
  — si algún RF de Enriquecimiento tiene una forma similar ("no
  enriquecible", RF-EN-009), exigir al menos una razón explícita, no un
  cuerpo vacío silencioso.
- Nunca inventar el esquema de metadatos ni la dependencia con Clasificación
  (ver arriba) — recíbelos como parámetro del llamador o déjalos fuera de
  alcance, nunca un valor fijo inventado.

- [x] T-49 RF-EN-001..010 — dominio de Enriquecimiento en Python
      (`contexts/enriquecimiento/dominio.py`), mismo patrón que
      `contexts/clasificacion/dominio.py` (T-44): `recibir_texto_extraido`
      (RF-EN-001) como única puerta de entrada; `ValorPropuesto` (campo,
      valor_original, valor_normalizado, confianza, evidencia — RF-EN-003/
      004) y campo marcado "no encontrado" explícito (RF-EN-005), ambos
      componentes FICTICIOS: el llamador entrega el valor/confianza/
      evidencia YA CALCULADOS, el dominio nunca extrae nada de verdad;
      `SugerenciaDeMetadatos` (agregado sin persistencia propia, spec §3);
      `a_sugerencia_saliente` — traducción pura a la forma genérica que ya
      acepta `POST /sugerencias` de records-custodia (`tipo="metadato"`),
      sin llamada HTTP (eso es T-50); `marcar_no_enriquecible` (RF-EN-009,
      razón declarada por el llamador, mismo criterio que
      `MarcaNoClasificable`); test estructural de que el módulo no expone
      ninguna operación de materialización (RF-EN-007, mismo criterio que
      T-09/T-44). Sin agregado persistido, sin `EventoAuditoria` propio
      (records-custodia ya emite el evento de recepción — mismo criterio
      que Clasificación, spec §4). `uv run --directory
      contexts/enriquecimiento pytest` agregado a `test.sh` en este mismo
      commit. TDD contra cada Dado/Cuando/Entonces de RF-EN-001..010.
- [x] T-50 RF-EN-001..010 — Servicio HTTP (FastAPI) para Enriquecimiento,
      SIN persistencia propia (spec §3), contra
      `specs/spec-infra-servicios.md` §13 (nueva). Mismo patrón que T-45
      (Clasificación): endpoint(s) que componen las funciones puras de
      `dominio.py` y reenvían con un cliente HTTP real
      (`EnviadorDeSugerenciasHttp`, detrás de un puerto/`Protocol` propio —
      lección de T-45-corrección aplicada desde el inicio, no como fix
      posterior) contra `POST /sugerencias` de records-custodia. Variable de
      entorno: `RECORDS_CUSTODIA_BASE_URL`. Manejo de errores: mismo
      criterio que clasificacion/extraccion (`ErrorDeDominio` → 409,
      `ServicioNoDisponibleError` → 502). TDD: tests de integración con
      `httpx.MockTransport` (verifica método/URL/cuerpo JSON exactos,
      lección de T-45) + tests de API con `dependency_overrides`.
      Ver STATE.md para el detalle completo (un único endpoint
      `POST /enriquecimientos`, no tres como en clasificacion, porque
      `evaluar_texto` ya bifurca por sí solo).
- [x] T-51 Dockerfile real de enriquecimiento + wiring en
      `docker-compose.{saas,onprem,local-ports}.yml`, mismo patrón que
      clasificacion (T-46): build en dos etapas con `uv sync --no-dev
      --frozen`, SIN Postgres propio, solo `RECORDS_CUSTODIA_BASE_URL` y
      `depends_on: [records-custodia]`; puerto 8088 (ya reservado por
      `specs/spec-infra-servicios.md` §13 desde T-50) en
      `docker-compose.local-ports.yml`.
      **Mismo hallazgo real que T-46 encontró para clasificacion**:
      `contexts/enriquecimiento/main.py` seguía siendo el stub sin tocar
      ("Hello from enriquecimiento!") — T-49/T-50 nunca lo tocaron porque
      los tests importan `api.app` directo. Corregido al mismo patrón que
      `contexts/clasificacion/main.py`
      (`uvicorn.run(app, ..., port=int(os.environ.get("SERVER_PORT",
      "8088")))`).
      T-51 es infraestructura de empaquetado (como T-18/T-35/T-42/T-46), no
      un RF con Dado/Cuando/Entonces propio. Verificación de honestidad:
      `./test.sh` completo en verde tras el cambio (Gradle 25 tareas BUILD
      SUCCESSFUL; pytest: eval-harness 4, normalizacion 40, extraccion 55,
      clasificacion 27, enriquecimiento 29, todos passed) confirma que el
      fix de `main.py` y el Dockerfile no rompieron nada existente; los
      tres `docker-compose*.yml` se validaron parseándolos con PyYAML
      (`uv run --directory eval-harness python -c "import yaml; ..."`,
      disponible en el entorno de esta sesión a diferencia de T-46) —
      confirma sintaxis válida y ausencia de `ports:` en el servicio nuevo
      de `saas.yml`/`onprem.yml`. Mismo límite que T-16..T-18/T-35/T-42/T-46:
      `docker` no está instalado en este entorno, así que la imagen y
      `docker compose up` reales no se construyeron ni se corrieron aquí —
      falta esa verificación de punta a punta (junto con T-52) cuando
      alguien la corra en un entorno con Docker disponible.
      `specs/spec-infra-servicios.md` §9 (trazabilidad) y §13 (nota de
      Dockerfile) actualizadas.
- [x] T-52 Colección Postman con el ciclo completo de Enriquecimiento
      (carpeta "9. Enriquecimiento", peticiones 76-81), mismo patrón que
      T-47 (Clasificación): flujo end-to-end real de dos servicios —
      custodiar documento en records-custodia → enriquecer con un valor
      propuesto FICTICIO y un campo marcado "no encontrado" en la misma
      llamada (`evaluar_texto` bifurca internamente, un único endpoint
      `POST /enriquecimientos`) → verificar en
      `GET /documentos/{id}/sugerencias` que ambos llegan distinguibles por
      campo (RF-EN-002..006/008) → marcar un texto "no enriquecible" con
      razón (RF-EN-009) y verificar que el conteo de sugerencias no cambia.
      Verificado con el stack Docker real de los ocho servicios y dos
      corridas de Newman seguidas sin fallos desde la primera: **81/81
      peticiones, 126/126 aserciones**. `postman/README.md` actualizado
      (50/58 endpoints cubiertos). `specs/spec-infra-servicios.md` §10
      actualizada por el loop durante T-51 (Enriquecimiento deja de
      aparecer como servicio que no existe para RF-VH-001).
      **Revisión de Codex sobre este commit: "OK CON OBSERVACIONES" (no
      VETO), dos hallazgos reales corregidos en el mismo día**: (1) P-08 no
      se verificaba para el flujo de Enriquecimiento — petición nueva 81
      (`GET /eventos-auditoria`, mismo cierre que la 75 le dio a
      Clasificación en T-48), reverificada: **82/82 peticiones, 127/127
      aserciones**, dos corridas seguidas sin fallos. (2) La frase "completo
      de punta a punta" se pasaba de la evidencia real: la forma genérica de
      `Sugerencia` en records-custodia no tiene campo para "forma original"
      (RF-EN-003) — Enriquecimiento la calcula y expone a nivel de dominio,
      pero se pierde al traducir a la salida; documentado como brecha real
      (no `[CLARIFICAR]` de negocio) en `specs/004-enriquecimiento/spec.md`
      §8, mismo tratamiento que la brecha de `DocumentoDeArchivo`/metadatos
      ya documentada ahí.
      **Con esto, Enriquecimiento (specs/004-enriquecimiento/spec.md) queda
      completo de punta a punta en el sentido del corte vertical del
      proyecto (dominio → servicio HTTP → Dockerfile/wiring → Postman/
      Newman verificado) — no en el sentido de que todo RF esté
      perfectamente cerrado (ver la brecha de "forma original" arriba):
      dominio (T-49, dos rondas de VETO real de Codex sobre RF-EN-009, ver
      STATE.md) → servicio HTTP (T-50) → Dockerfile/wiring (T-51) →
      Postman/Newman (T-52) — octavo bounded
      context del proyecto con el mismo ciclo completo.**
- [x] T-53 Trazabilidad E2E de Enriquecimiento contra
      `specs/004-enriquecimiento/spec.md` RF-EN-003/004/010 y P-08. Añadida
      por Codex durante la revisión de T-52; la mitad de P-08 ya había
      cerrado en el mismo commit de T-52 (petición 81). Esta tarea cierra el
      resto: extendido el contrato compartido `Sugerencia`/
      `SugerenciaEntrante` de records-custodia (T-08) con `formaOriginal:
      String?` — opcional, nulo por defecto, así que Clasificación (que usa
      el mismo contrato y no tiene concepto de "forma original") no tuvo
      que cambiar nada. Cambios: `CustodiaOriginales.kt`
      (`Sugerencia`/`SugerenciaEntrante`/`CapaAnticorrupcionSugerencias.
      recibir`), `http/Dtos.kt` (`RecibirSugerenciaRequest`),
      `http/SugerenciasController.kt`, `persistencia/Entidades.kt`
      (`SugerenciaEntity`, columna `forma_original` nullable — segura sobre
      tabla con filas existentes, a diferencia de `es_correccion` en T-39
      que era `not null`), `persistencia/Almacenes.kt`
      (`AlmacenDeSugerenciasJpa`). Del lado de Enriquecimiento:
      `dominio.py` (`SugerenciaSaliente.forma_original`,
      `a_sugerencia_saliente` lo puebla desde `ValorPropuesto.
      valor_original`, `None` para `CampoNoEncontrado`) e
      `integracion.py` (`formaOriginal` en el cuerpo JSON saliente).
      TDD: 1 test HTTP nuevo en `RecordsCustodiaHttpTest.kt` (confirmado en
      rojo antes del cambio de contrato) + 2 tests de dominio y 1 de
      integración actualizados en `enriquecimiento` (confirmados en rojo,
      `TypeError: unexpected keyword argument`) + 1 aserción nueva en
      `test_api.py`. `./test.sh` completo del repo en verde (40 tests en
      records-custodia, 29 en enriquecimiento). Colección Postman:
      peticiones 77/78 ampliadas para verificar que la forma original
      sobrevive de punta a punta; verificado con el stack Docker real de
      los ocho servicios y dos corridas de Newman seguidas sin fallos:
      **82/82 peticiones, 127/127 aserciones** (mismo conteo — se amplían
      aserciones existentes, no se agregan peticiones). `specs/
      spec-infra-servicios.md` §4/§13 y `specs/004-enriquecimiento/spec.md`
      §8 actualizadas (brecha cerrada). No se fijó ningún campo obligatorio
      ni umbral: los `[CLARIFICAR]` de la spec siguen abiertos.

# Indexación y Búsqueda

Decisión de Victor, 2026-08-30 ("Sigamos con Indexación y Búsqueda"). Sigue
`specs/005-indexacion-busqueda/spec.md` (RF-IB-001..010). Noveno y último
contexto del alcance original — con este se cierran los nueve.

**Este contexto es distinto y más grande que los tres anteriores (Normalización/
Extracción/Clasificación/Enriquecimiento), léelo completo antes de tocar
código**:

1. **SÍ tiene persistencia real** (a diferencia de Clasificación/
   Enriquecimiento): `Entrada de índice` es un agregado con estado
   (`Pendiente de indexación` → `Indexada`, actualizable — RF-IB-004), mismo
   patrón que Normalización/Extracción (Postgres propio, `guardar_con_evento`
   atómico desde el primer commit — lección de T-37).
2. **Tiene DOS componentes probabilísticos distintos**, no uno: Recuperación
   (ranking por relevancia semántica, RF-IB-006) y Q&A conversacional
   (RF-IB-007/010) — cada uno gobernado por EDD por separado (`edd-harness.md`
   §2/§4). No los confundas ni los fusiones en un solo "componente ficticio".
3. **Frontera real vs. FICTICIO — la más delicada de todo el proyecto, y la
   más fácil de malinterpretar (léela dos veces)**:
   - RF-IB-001/002/004/005 (recibir documento materializado, indexar
     léxicamente, actualizar la entrada, buscar por palabra clave + filtros
     de metadatos) son **REALES y deterministas** — la spec §1 lo dice
     explícito: "la construcción y el mantenimiento del índice en sí...
     es una operación determinística, especificada bajo SDD (P-06)". No hace
     falta ningún motor externo para esto: una búsqueda por palabra clave
     (substring/contención sobre el texto ya indexado) y un filtro por campos
     de metadatos ya declarados son código real, implementable sin inventar
     Elasticsearch/Postgres-FTS/lo que sea — la decisión de motor concreto es
     el `[CLARIFICAR]` de la spec §8 ("Etapa 1, informada por el arnés"), no
     algo que esta tarea deba resolver ni fingir.
   - RF-IB-003 (indexación vectorial): la ENTRADA guarda un campo para el
     embedding, pero el valor del embedding en sí es FICTICIO — igual que
     `confianza`/`evidencia` en `Sugerencia`, el llamador lo entrega YA
     CALCULADO; el dominio nunca invoca un modelo de embeddings real (P-03,
     una de las tres capacidades externas que la spec §1 nombra
     explícitamente para este contexto).
   - RF-IB-006 (recuperación por relevancia semántica) es el componente
     FICTICIO real: el llamador entrega el orden/score de relevancia YA
     CALCULADO (mismo criterio que `ordenar_por_confianza` en clasificación,
     salvo que aquí el ranking completo llega ya resuelto, no se recalcula
     nada) — el dominio nunca calcula similitud de embeddings.
   - RF-IB-007/010 (Q&A) es FICTICIO igual que Recuperación: el llamador
     entrega el texto de la respuesta y las citas YA CALCULADOS (o declara
     que no hay evidencia suficiente). **Aplica desde el primer commit la
     lección de los DOS VETOs reales de Codex sobre `evaluar_texto` en
     Enriquecimiento (T-49, ver STATE.md 2026-08-29)**: una única operación
     de evaluación que bifurca entre "respuesta con cita(s)" y "negativa
     apropiada" (RF-IB-010) — nunca una función que acepte una respuesta sin
     ninguna cita y la deje pasar como si fuera válida (eso es exactamente la
     alucinación que invariante 3/RF-IB-007 prohíbe). No repitas el error de
     escribir el guardia aislado del test conectado a nada: la prueba de
     aceptación debe observar la salida real de esa única operación.
   - RF-IB-008 (cero exposición sin permiso) y RF-IB-009 (auditoría de
     acceso) son **REALES**: filtrar una lista de candidatos contra un
     conjunto de documentos permitidos, y anexar un evento de auditoría, son
     operaciones deterministas — nada de esto es FICTICIO, aunque dependan de
     un dato (permisos) que sí viene de una capacidad externa real
     (Seguridad y Acceso).
4. **P-03 — el dominio NUNCA llama a Seguridad y Acceso.** `POST
   /autorizacion` de seguridad-acceso (specs/spec-infra-servicios.md §5) es
   una verificación POR RECURSO (`identidadId, accion, tipoRecurso,
   nivelClasificacion, recurso, fecha` → permitido/denegado), no una lista
   masiva — no existe un endpoint de "dame todos los documentos permitidos
   de este usuario" y esta tarea NO debe inventar uno. El filtrado de
   permisos en el dominio (`buscar`/`responder_qa`) recibe un parámetro YA
   RESUELTO (`documentos_permitidos: set[...]` o equivalente) — quien arma
   ese conjunto (llamando `/autorizacion` una vez por candidato, mismo
   patrón que `VerificadorDeAutorizacion` en extracción/T-41b) es la capa
   HTTP (T-55), nunca el dominio.
5. **[CLARIFICAR] real de la spec §8, NO resuelto aquí — sortéalo igual que
   los anteriores**: si Records/Custodia reenvía el texto extraído junto con
   el estado materializado, o si Indexación lo correlaciona por su cuenta.
   Se sortea con el mismo criterio de siempre: el llamador entrega AMBOS
   (documento materializado + texto extraído) ya correlacionados a la
   operación de recepción — el dominio no decide cómo se obtuvieron.
6. **Otros dos `[CLARIFICAR]` de la spec §8 que tampoco se resuelven aquí**:
   el umbral/mecanismo exacto de "negativa apropiada" (por eso RF-IB-010 se
   sortea con una razón/condición declarada por el llamador, no un umbral de
   confianza inventado) y si la reindexación ante un cambio materializado es
   inmediata o asíncrona (esta tarea implementa la operación de actualizar
   una entrada ya existente — RF-IB-004 — sin decidir el disparador ni la
   cadencia, igual que T-12 dejó la ejecución "programada" de
   `verificarTodos` fuera de alcance por ser responsabilidad de un disparador
   externo).

**Lecciones ya aprendidas en los contextos anteriores, aplícalas desde el
primer commit** (ver STATE.md para el detalle completo de cada una):
- P-08 desde el inicio (T-37): cada función de transición de dominio
  devuelve también un `EventoAuditoria`.
- En FastAPI/Starlette, cualquier ruta GET literal debe declararse ANTES que
  su ruta hermana con `{id}` (T-39/T-41).
- Persistencia atómica agregado+evento en una sola transacción con rollback
  explícito (`guardar_con_evento`, T-37).
- Toda capacidad externa detrás de un puerto/`Protocol` propio desde el
  inicio, nunca contra la clase HTTP concreta (T-45-corrección).
- Nunca una operación que acepte una entrada "vacía" (sin resultados, sin
  citas) y la deje pasar sin una salida explícita — RF-CL-010 y los dos
  VETOs de RF-EN-009 lo enseñaron dos veces ya.
- Migraciones: una columna nueva `NOT NULL` sin `columnDefinition` con
  default real falla contra una tabla con filas existentes (T-39/`es_correccion`)
  — cualquier columna nueva aquí debe traer su propio default real si no es
  nullable, o ser nullable si no aplica a todas las filas.
- Nunca inventar el motor de índice léxico/vectorial, el modelo de
  embeddings, el LLM de Q&A, ni el umbral de "negativa apropiada" — los
  cuatro siguen `[CLARIFICAR]` en la spec §8.

- [x] T-54 RF-IB-001..010 — dominio de Indexación y Búsqueda en Python
      (`contexts/indexacion-busqueda/dominio.py`), mismo patrón de agregado
      persistido que `contexts/normalizacion/dominio.py`/
      `contexts/extraccion/dominio.py` (T-33/T-40), **corregido tras VETO
      real de Codex sobre la siembra original de esta tarea (commit
      `c8f47d7`, ver STATE.md)** — dos hallazgos, ambos aplicados aquí desde
      el primer commit, no como fix posterior:
      **(1) P-03 — CUATRO puertos propios (`Protocol`), no solo
      `VerificadorDePermisos`.** La spec §1 nombra explícitamente cuatro
      capacidades externas para este contexto: índice léxico, índice
      vectorial, embeddings e inferencia LLM. Que la búsqueda léxica sea
      "determinista" (P-06) NO la exime de P-03 — P-03 abstrae la
      capacidad externa en sí (mismo criterio que `AlmacenDeUnidades` en
      normalización, que abstrae persistencia aunque persistir sea
      determinista), no solo lo probabilístico. `IndiceLexico` e
      `IndiceVectorial` (Protocol, declarados en `dominio.py`, mismo patrón
      que `EnviadorDeSugerencias`) representan las dos capacidades REALES;
      `GeneradorDeEmbeddings`/`ModeloDeLenguaje` (Protocol también) las dos
      FICTICIAS — declarados por completitud de P-03 aunque esta tarea
      nunca los invoque (el llamador entrega embedding/respuesta/citas YA
      CALCULADOS, disciplina constitucional de nunca implementar un
      componente probabilístico real; no hay ningún generador real que
      llamar). Las funciones puras de `dominio.py` NUNCA llaman estos
      puertos directamente (mismo patrón que `a_sugerencia_saliente` en
      clasificación/enriquecimiento, que nunca invoca `EnviadorDeSugerencias`
      — quien lo hace es `api.py`): `dominio.py` recibe ya resueltos los
      candidatos que devolvió `IndiceLexico`/`IndiceVectorial` (T-55 los
      invoca) y aplica sobre ellos la lógica pura (filtrado de permiso,
      construcción de evento). Dos adaptadores intercambiables por puerto
      real en T-55 (EnMemoria + uno real), sin nombrar motores ni modelos
      concretos (el `[CLARIFICAR]` de motor/modelo sigue abierto).
      **(2) P-08/RF-IB-009 — el evento de acceso NUNCA es una operación
      separada.** Nada de `emitir_evento_de_acceso(...)` como función
      aparte que un test pueda invocar aislada (eso permitiría que una
      consulta real no genere el evento y el test igual pasara — mismo
      defecto de raíz que los dos VETOs sobre `evaluar_texto`, aplicado
      esta vez a "quién genera el evento" en vez de "quién produce la
      salida"). Cada operación de consulta real (`buscar`,
      `recuperar_por_relevancia`, `responder_qa`) devuelve tanto su
      resultado como su propio `EventoAuditoria` de acceso, en la MISMA
      llamada — el test de aceptación de RF-IB-009 ejerce `buscar`/etc.
      directamente y observa que el evento sale de ahí, nunca de un
      guardia aparte.
      Resto del diseño (sin cambios respecto a la siembra original):
      `EstadoEntradaDeIndice` (`PENDIENTE_DE_INDEXACION`, `INDEXADA` — spec
      §3, sin estado de eliminación en esta etapa); `recibir_documento_
      materializado(...)` única puerta de entrada (documento_id,
      clasificación/metadatos YA MATERIALIZADOS, texto extraído — todo
      declarado por el llamador, sortea el `[CLARIFICAR]` de correlación de
      la spec §8); `indexar(...)` construye la `EntradaDeIndice` en
      `INDEXADA` (RF-IB-001/002) con campo de embedding FICTICIO
      (RF-IB-003, ya calculado); `actualizar_entrada(...)` (RF-IB-004);
      `aplicar_permisos_y_construir_evento(candidatos, documentos_
      permitidos, actor, fecha)` pura, REAL (RF-IB-008 — filtra la lista
      recibida, nunca expone lo no permitido, ni siquiera como cita) —
      compartida por `buscar`/`recuperar_por_relevancia`/`responder_qa`
      para que RNF-IB-003 (consistencia de permisos entre las tres rutas)
      sea estructural, no una coincidencia de implementación repetida tres
      veces; `responder_qa(...)` única operación de evaluación FICTICIO que
      bifurca entre `RespuestaQA` (con al menos una cita, RF-IB-007) y
      `NegativaApropiada` (RF-IB-010), con las citas ya filtradas por
      permiso antes de bifurcar. `uv run --directory
      contexts/indexacion-busqueda pytest` agregado a `test.sh` en este
      mismo commit. TDD contra cada Dado/Cuando/Entonces de RF-IB-001..010,
      incluidas permiso denegado (RF-IB-008) y negativa apropiada
      (RF-IB-010) ejercidas a través de la operación real, y el evento de
      acceso observado como salida de esa misma operación, no de un
      guardia aislado.
      **Implementado.** Un ajuste real sobre la siembra: en vez de que
      `recibir_documento_materializado` construya directamente la
      `EntradaDeIndice`, se dividió en dos pasos —
      `recibir_documento_materializado` devuelve un `DocumentoParaIndexar`
      (dato crudo, sin agregado, mismo patrón que `TextoDisponible` en
      clasificacion/enriquecimiento) y `crear_entrada_pendiente` es quien
      construye el agregado en `PENDIENTE_DE_INDEXACION` — necesario para que
      el estado `Pendiente de indexación` del modelo de dominio (spec §3) sea
      real y observable, no solo nombrado, antes de que `indexar` lo
      transicione a `INDEXADA`. El test de RF-IB-001 ejercita las tres
      llamadas en secuencia (recibir → crear entrada pendiente → indexar) y
      verifica el estado final `Indexada`, honrando el Dado/Cuando/Entonces
      literal sin perder la máquina de estados de dos pasos.
      Se agregó también un tipo `EventoDeAcceso` (actor, fecha, tipo,
      documentos_accedidos) distinto de `EventoAuditoria` (que sí tiene
      estado_anterior/posterior): RF-IB-009 pide literalmente "actor, fecha y
      los documentos accedidos" para una operación de solo lectura que no
      transiciona ningún estado — forzar el shape de `EventoAuditoria` habría
      exigido inventar un estado ficticio sin sentido. Ambos son "evento de
      auditoría" a efectos de P-08; `GET /eventos-auditoria` (T-55) expondrá
      los dos tipos. TDD real: 23 tests nuevos (`tests/test_dominio.py`),
      verdes en el primer intento (el módulo no existía antes de este commit,
      así que el `ImportError` inicial fue la confirmación en rojo).
      `./test.sh` completo del repo en verde (Gradle BUILD SUCCESSFUL; pytest:
      eval-harness 4, normalizacion 40, extraccion 55, clasificacion 27,
      enriquecimiento 29, indexacion-busqueda 23).
      **TERCER VETO real de Codex sobre este mismo commit (`22b6b09`), ver
      STATE.md — corregido junto con T-55 en un solo cambio siguiente
      (Codex exigió explícitamente que ambas tareas cerraran juntas)**: (1)
      P-03 — ningún puerto se ejercitaba de verdad (`documentos_permitidos`
      llegaba como `set` ya resuelto, sin invocar ningún `Protocol`).
      Corregido con el mismo patrón que `VerificadorDeAutorizacion` en
      extracción/T-41b (ya validado por Codex sin VETO): `buscar`/
      `recuperar_por_relevancia`/`responder_qa`/`indexar` ahora reciben el
      puerto (`IndiceLexico`/`IndiceVectorial`/`VerificadorDePermisos`) y lo
      invocan ellos mismos. (2) Bug real independiente: `EventoDeAcceso.
      documentos_accedidos` era `list[str]` dentro de un dataclass
      `frozen=True` — `frozen` impide reasignar el atributo pero NO impide
      mutar la lista referenciada; corregido a `tuple[str, ...]`, genuinamente
      inmutable. 23/23 tests siguen en verde tras ambas correcciones (+
      aserciones nuevas que confirman las llamadas reales a los puertos).
- [x] T-55 RF-IB-001..010 — Servicio HTTP (FastAPI) + persistencia
      (SQLAlchemy + Postgres) para Indexación y Búsqueda, mismo patrón que
      T-34/T-41 (Normalización/Extracción) — SÍ con Postgres propio, a
      diferencia de clasificación/enriquecimiento/validación-humana.
      **Corregido tras SEGUNDO VETO real de Codex (commit `e356158`, ver
      STATE.md): el primer intento de corrección seguía incompleto en dos
      puntos — ambos aplicados aquí desde el primer commit.**
      **(1) P-03 — dos variantes de DESPLIEGUE por cada una de las cuatro
      capacidades, no un doble en memoria + una implementación real.** Un
      adaptador `EnMemoria` es un doble de prueba, no una alternativa de
      despliegue (RNF-IB-002 exige explícitamente "operando idéntico en
      SaaS y on-premise... incluyendo entornos sin conectividad saliente" —
      la razón real por la que este contexto necesita DOS variantes reales
      y no solo "mismo contenedor, dos compose" como el resto del proyecto:
      SaaS puede usar un servicio gestionado externo, on-premise no puede
      salir a internet). Sin nombrar motores ni modelos concretos (el
      `[CLARIFICAR]` de motor/modelo sigue abierto, spec §8):
      - `IndiceLexico`: `IndiceLexicoAutoalojado` (consulta real contra el
        texto ya persistido en Postgres — la base ya decidida desde F1.D1,
        sin salir a red) e `IndiceLexicoGestionado` (cliente HTTP real
        contra una URL configurable por variable de entorno
        `INDICE_LEXICO_ENDPOINT_URL`, sin asumir ningún producto — igual
        que `RECORDS_CUSTODIA_BASE_URL` es configuración, no una decisión
        de producto). El contenedor real usa el autoalojado por defecto
        (no hay ningún servicio gestionado desplegado en este proyecto);
        el gestionado existe como clase real, intercambiable, aunque no se
        active en `docker-compose.saas.yml` todavía.
      - `IndiceVectorial`: mismo patrón —
        `IndiceVectorialAutoalojado`/`IndiceVectorialGestionado` — ambos
        solo almacenan/recuperan el campo de embedding FICTICIO (nunca
        similitud real; RF-IB-006 sigue recibiendo el orden ya calculado
        del llamador).
      - `GeneradorDeEmbeddings`/`ModeloDeLenguaje` (los dos FICTICIOS):
        también dos clases cada uno (`...Gestionado`/`...Autoalojado`),
        pero NINGUNA calcula nada real — ambas existen solo para que el
        seam de P-03 esté completo estructuralmente y lanzan un error
        explícito y documentado si algo las invoca (nunca deberían
        invocarse: el llamador entrega embedding/respuesta/citas ya
        calculados como parámetro, disciplina constitucional de no
        implementar un componente probabilístico real). No se conectan a
        ningún compose — no hay nada que desplegar para un adaptador que
        nunca se llama.
      **(2) P-08/RF-IB-009 — el evento de acceso se PERSISTE de forma
      solo-anexado, atómicamente, dentro de la MISMA petición HTTP de cada
      consulta — no basta con que la función de dominio lo devuelva.**
      `POST /busquedas`, `POST /recuperaciones` y `POST /preguntas`
      (o los nombres de ruta que correspondan) llaman
      `almacen.guardar_evento_de_acceso(evento)` (tabla `eventos_auditoria`
      de solo inserción, `entityManager`/`session.add` sin update — mismo
      criterio WORM que records-custodia, T-17) dentro del mismo request,
      antes de responder. TDD: el test de aceptación de RF-IB-009 hace la
      consulta real vía HTTP y DESPUÉS consulta `GET /eventos-auditoria`
      para confirmar que el evento quedó ahí — nunca inspecciona solo el
      valor devuelto por la función de dominio.
      `guardar_con_evento(entrada, evento)` (para indexar/actualizar, no
      para el evento de acceso de una consulta) en una única transacción
      con rollback explícito (T-37). `GET /eventos-auditoria` desde el
      principio (P-08) — expone tanto los eventos de indexación como los
      de acceso por consulta. `api.py` invoca
      `IndiceLexico`/`IndiceVectorial` para obtener candidatos y se los
      pasa a las funciones puras de `dominio.py` (T-54, punto 1). Puerto
      `VerificadorDePermisos` (P-03, dominio.py) + implementación HTTP real
      contra `POST /autorizacion` de seguridad-acceso (mismo patrón que
      `VerificadorDeAutorizacionHttp` en extracción, T-41b) — la capa HTTP
      arma `documentos_permitidos` antes de invocar las funciones puras del
      dominio; variable de entorno `SEGURIDAD_ACCESO_BASE_URL`. Cualquier
      ruta GET literal (p. ej. `/entradas/pendientes-de-indexacion` si
      aplica) declarada ANTES que su ruta hermana con `{id}` (T-39/T-41).
      `specs/spec-infra-servicios.md` §14 (nueva) con el contrato HTTP
      mínimo completo, incluidas las variables de entorno de las cuatro
      capacidades.
      **Implementado junto con la corrección del tercer VETO de T-54**
      (Codex exigió que ambas cerraran en el mismo cambio, ver arriba):
      `persistencia.py` (`AlmacenDeEntradas.guardar_con_evento`/
      `guardar_evento_de_acceso`, tablas `entradas_de_indice`/
      `eventos_auditoria`/`eventos_de_acceso` — dos bitácoras separadas
      porque `EventoAuditoria` y `EventoDeAcceso` tienen formas distintas;
      `IndiceLexicoAutoalojado` hace una consulta SQL `ILIKE` real contra
      Postgres, `IndiceVectorialAutoalojado` solo completa el seam porque el
      embedding ya se persiste con la entrada); `integracion.py`
      (`IndiceLexicoGestionado`/`IndiceVectorialGestionado`, clientes HTTP
      genéricos contra una URL configurable; `GeneradorDeEmbeddings`/
      `ModeloDeLenguaje` × 2 variantes, ambas lanzan
      `ComponenteProbabilisticoNoImplementadoError` explícito si se
      invocan; `VerificadorDePermisosHttp` contra `POST /autorizacion`);
      `api.py` (7 endpoints: `POST /entradas`, `POST /entradas/{id}/
      indexacion`, `POST /entradas/{id}/actualizacion`, `POST /busquedas`,
      `POST /recuperaciones`, `POST /preguntas`, `GET /eventos-auditoria`);
      `main.py` corregido (seguía siendo el stub "Hello from
      indexacion-busqueda!", mismo hallazgo que T-46 documentó para
      clasificacion). TDD: 23 tests de dominio (ya existían) + 9 de
      integración (`httpx.MockTransport`, verifica cuerpo exacto, y los
      cuatro adaptadores FICTICIOS fallan explícitamente) + 6 de
      persistencia (atomicidad con violación `IntegrityError` real, no
      simulada — mismo criterio que T-37/T-41 — más `IndiceLexicoAutoalojado`
      contra SQLite real) + 8 de API (incluida la prueba de aceptación
      literal de RF-IB-009: hace la consulta HTTP real y DESPUÉS lee
      `GET /eventos-auditoria`, nunca inspecciona solo el valor devuelto) =
      46/46 en el módulo. `./test.sh` completo del repo en verde.
- [x] T-56 Dockerfile real de indexacion-busqueda + wiring en
      `docker-compose.{saas,onprem,local-ports}.yml`, mismo patrón que
      normalizacion/extraccion (T-35/T-42) — CON Postgres propio (a
      diferencia de clasificacion/enriquecimiento, T-46/T-51); verificar
      `main.py` real antes de escribir el `ENTRYPOINT` (hallazgo real de
      T-46: el stub "Hello from..." nunca se tocó); siguiente puerto
      disponible en `docker-compose.local-ports.yml`;
      `depends_on: [postgres, seguridad-acceso]`.
      T-56 es infraestructura de empaquetado (como T-18/T-35/T-42), no un RF
      con Dado/Cuando/Entonces propio — verificación real, no solo por
      honestidad: a diferencia de T-42 (donde Docker no estaba disponible),
      aquí SÍ había Docker en el entorno, así que se construyó la imagen
      (`docker build`), se levantó el stack completo de nueve servicios
      (`docker compose -f saas.yml -f local-ports.yml up -d --build`, los
      nueve contenedores `Up`) y se ejercitó el flujo real por HTTP contra
      el contenedor: `POST /entradas` → `POST /entradas/{id}/indexacion` →
      `POST /busquedas` devolvió `[]` correctamente (RF-IB-008: el actor
      "ana" no es una identidad real de seguridad-acceso, así que el
      `VerificadorDePermisosHttp` la denegó de verdad — confirmado en los
      logs de `seguridad-acceso`, primera petición real que recibió ese
      contenedor). Stack detenido y removido (`docker compose down`) al
      terminar, imagen de prueba borrada.
      **Hallazgo real durante la verificación, y SEXTO VETO real de Codex
      sobre este mismo commit** (ver STATE.md): `GET /eventos-auditoria` en
      el contenedor recién levantado devolvió eventos de OTROS contextos
      (`ORIGINAL_CUSTODIADO`, `UNIDAD_RECIBIDA`, etc. de records-custodia/
      captura-ingesta/normalizacion) — las cuatro tablas de
      `contexts/indexacion-busqueda/persistencia.py` usaban nombres
      genéricos (`entradas_de_indice`, `eventos_auditoria`...) que ya
      existían en el mismo Postgres compartido, creadas por esos otros
      contextos. Codex vetó: "Debe aislarse por esquema o nombres de tabla
      por contexto, y demostrarse que el endpoint solo devuelve su propia
      bitácora, antes de aprobarlo" — no bastaba con registrarlo como
      tarea futura. Corregido en el mismo commit: las cuatro tablas de
      indexacion-busqueda llevan ahora el prefijo único `ib_`
      (`ib_entradas_de_indice`, `ib_indices_vectoriales`,
      `ib_eventos_auditoria`, `ib_eventos_de_acceso`); nuevo test
      `TestAislamientoDeTablasPorContexto` (guarda de regresión sobre
      `__tablename__`); reverificado en vivo contra Docker — `\dt` mostró
      las cuatro tablas `ib_*` junto a las originales sin tocarlas, y
      `GET /eventos-auditoria` devolvió únicamente el evento propio recién
      escrito, ninguno ajeno. T-58 queda acotada a los contextos que sí
      siguen colisionando ENTRE ELLOS (records-custodia/normalizacion/
      extraccion — `captura-ingesta` no tiene tabla `eventos_auditoria`
      propia, corrección de alcance hecha al implementar T-58) —
      indexacion-busqueda ya no participa de esa colisión.
- [x] T-57 Colección Postman con el ciclo completo de Indexación y
      Búsqueda, mismo patrón que T-36/T-43/T-47/T-52 — flujo end-to-end
      real: custodiar y materializar un documento en records-custodia
      (decisión humana real, RF-RC-004) → recibir su texto extraído
      (FICTICIO) → indexar → buscar por palabra clave y filtro (RF-IB-005,
      real) → recuperar por relevancia con orden FICTICIO ya calculado
      (RF-IB-006) → responder una pregunta con cita FICTICIO (RF-IB-007) →
      una pregunta sin evidencia suficiente → negativa apropiada (RF-IB-010)
      → un usuario sin permiso sobre el documento → verificar que no
      aparece en resultados ni citas (RF-IB-008, con una identidad real de
      seguridad-acceso sin el permiso correspondiente) → verificar
      `GET /eventos-auditoria` con el evento de acceso (RF-IB-009).
      Verificar con el stack Docker real de los nueve servicios (primera vez
      con `indexacion-busqueda`) y dos corridas de Newman seguidas sin
      fallos; actualizar `postman/README.md` con el conteo real.
      Carpeta 10 (peticiones 82-97) construida siguiendo exactamente ese
      flujo, con una identidad "sin permiso" implementada como un actor no
      registrado en seguridad-acceso (mismo patrón que la petición 62,
      T-43/RF-EX-011) en vez de una segunda identidad real con un rol
      distinto -- ambos caminos demuestran RF-IB-008 igual de bien y este
      es el ya aceptado por Codex en la carpeta 7. Además de las tres rutas
      de consulta, se probó RF-IB-008 dentro de `POST /preguntas`: una cita
      real pero sin permiso se filtra ANTES de decidir la rama, así que
      `responder_qa()` cae a `NegativaApropiada` aunque el llamador
      entregó una `respuesta`, nunca deja pasar una respuesta sustentada en
      evidencia no permitida (invariante 3).
      **Primer fallo real que encontró esta carpeta** (mismo patrón que la
      huella de contenido de Normalización en T-36, ya documentado en
      `postman/README.md`): el índice léxico es un Postgres persistente
      compartido por todas las corridas de la colección, así que un texto
      buscado sin un token único por corrida coincidía con las entradas de
      corridas anteriores -- la segunda corrida seguida encontró 2
      resultados donde se esperaba 1. Corregido embebiendo
      `{{documento_id_ib}}` en el texto indexado y en el término de
      búsqueda antes de dar la tarea por cerrada.
      **Segundo hallazgo, sobre el volumen de Postgres, no sobre la
      colección en sí**: la carpeta 2 publica el TRD v1 con una versión
      FIJA (petición 08); en un volumen que ya tiene un TRD v1 publicado
      por una sesión anterior (14 filas en `trd_versiones` al empezar esta
      tarea), esa petición falla con `409` en vez del `201` esperado -- no
      es un defecto de la colección, es que una reverificación completa
      siempre necesitó partir de un volumen limpio (el propio historial de
      `postman/README.md` ya lo documentaba una vez, T-39); ahora queda
      explícito en el README como requisito, no una nota aislada.
      Verificado con Docker real: `docker compose ... down -v` + `up -d
      --build` (nueve servicios), dos corridas de Newman seguidas sobre el
      mismo volumen sin bajarlo entre medias -- 98/98 peticiones, 143/143
      aserciones, cero fallos en ambas corridas tras la corrección.
      `postman/README.md` actualizado (carpeta 10, variables nuevas, nota
      sobre el volumen de Postgres, historial de reverificación).
      **Con esto se completan los nueve bounded contexts del alcance
      original del corte vertical.**
- [x] T-58 Aísla la tabla `eventos_auditoria` (y cualquier otra con nombre
      genérico compartido) entre records-custodia, normalizacion y
      extraccion — hallazgo real verificado durante T-56 (`docker exec
      deploy-postgres-1 psql -U sgdea -d sgdea -c "\d eventos_auditoria"`
      mostró UNA sola tabla física con columnas de records-custodia,
      incluida `es_correccion`, propia de ese contexto). Contradice
      spec-infra-servicios.md §2 ("cada contexto mapea sus propios
      agregados a sus propias tablas") y mezclaba la bitácora de auditoría
      (P-08) de contextos que no deberían verse entre sí.
      **Corrección de alcance sobre la redacción original de esta tarea**:
      al investigar antes de implementar, `captura-ingesta` resultó NO
      tener ninguna tabla `eventos_auditoria` propia (`grep -rn "@Table"
      contexts/captura-ingesta/...` solo muestra `lotes_ingesta`/
      `items_ingesta`) — nunca participó de la colisión, así que el alcance
      real era TRES contextos, no cuatro.
      `indexacion-busqueda` ya no participaba (corregida dentro de T-56 con
      el prefijo `ib_`); se aplicó el mismo patrón (prefijo por contexto)
      a los tres restantes: `rc_eventos_auditoria` (records-custodia,
      Kotlin/JPA, `@Table(name=...)`), `no_eventos_auditoria`
      (normalizacion, Python/SQLAlchemy, `__tablename__`),
      `ex_eventos_auditoria` (extraccion, ídem). Se prefirió el prefijo de
      tabla sobre un esquema Postgres propio (`CREATE SCHEMA`) por el mismo
      motivo que en T-56: más simple, y un esquema real complicaría
      `sqlite:///:memory:` en los tests Python.
      `specs/spec-infra-servicios.md` actualizada: nota nueva en §2
      documentando el hallazgo y la convención de prefijos; §4/§5/§7/§11
      con el nombre de tabla correcto en cada mapeo de persistencia (la
      nota histórica del bug de T-39 en §4, que cita el nombre de entonces,
      se dejó intacta como registro del incidente real, no una referencia
      viva).
      TDD: guarda de regresión sobre el nombre de tabla en los tres
      contextos — `TestAislamientoDeTablaPorContexto` en
      normalizacion/extraccion (mismo criterio que
      `TestAislamientoDeTablasPorContexto` de indexacion-busqueda en T-56)
      y `EntidadesTest` (reflexión sobre `@Table`) en records-custodia,
      primera prueba unitaria de este archivo que no depende de Spring —
      H2 en test no puede reproducir la colisión real porque cada módulo
      Kotlin corre contra su propia instancia `jdbc:h2:mem:...` aislada, así
      que la prueba de nombre de tabla es la única guarda posible en CI.
      Verificado con Docker real: `docker compose ... down -v` + `up -d
      --build` (nueve servicios), `\dt` confirmó las tres tablas nuevas
      (`rc_/no_/ex_eventos_auditoria`) sin tocar las demás; se escribió un
      evento real en records-custodia y otro en normalizacion y se confirmó
      que `GET /eventos-auditoria` de cada contexto (incluida extraccion,
      vacía) solo devuelve sus propios eventos — cero mezcla. Colección
      Postman completa (T-57) reverificada dos corridas seguidas sobre el
      mismo volumen limpio: 98/98 peticiones, 143/143 aserciones, sin
      fallos. `./test.sh` completo del repo en verde (Gradle incluido el
      nuevo `EntidadesTest`; pytest: eval-harness 4, normalizacion 41 [+1],
      extraccion 56 [+1], clasificacion 27, enriquecimiento 29,
      indexacion-busqueda 50).
      Sin umbral ni referencia normativa nueva inventada: es una corrección
      de infraestructura contra una decisión de spec ya escrita.
      **SEGUNDO VETO real de Codex, sobre este mismo cambio**: "el renombrado
      de `eventos_auditoria` no migra ni mantiene accesible el historial de
      auditoría ya existente" — P-08 exige que la bitácora siga siendo
      recuperable, y el rename por sí solo dejaba los eventos anteriores
      físicamente en la tabla vieja pero invisibles para `GET
      /eventos-auditoria`. Corregido SIN migrar datos (Codex ofreció esa vía
      alternativa explícitamente: "una transición de lectura compatible que
      los mantenga recuperables"): cada uno de los tres contextos combina su
      tabla nueva con una lectura de solo-consulta sobre la tabla heredada
      `eventos_auditoria` (si existe).
      **TERCER VETO real de Codex, sobre la primera versión de esa
      corrección**: el primer intento filtraba la tabla heredada solo por los
      `tipo` EXCLUSIVOS de cada contexto y EXCLUÍA `VALIDACION_APLICADA`
      (`normalizacion` y `extraccion` usan literalmente el mismo `tipo` sin
      ninguna columna de origen en la tabla heredada). Codex vetó también
      eso: "documentar la omisión tampoco la corrige" — P-08 exige que TODA
      transición histórica siga siendo recuperable, no solo la que se puede
      atribuir con certeza. Corregido exponiendo esas filas AMBIGUAS en los
      DOS contextos que podrían haberlas escrito, cada una marcada
      `origen_verificado=False` (campo nuevo en `EventoAuditoria`,
      `True` por defecto para todo evento real del contexto) — nunca
      omitidas, nunca atribuidas en falso a uno solo.
      Nuevas pruebas contra una base preexistente con eventos de varios
      contextos, exactamente lo que Codex pidió ("después de desplegar el
      cambio, cada endpoint debe devolver sus propios eventos heredados y los
      nuevos, sin mezcla"): `AlmacenDeEventosLegacyTest` (records-custodia,
      Kotlin/H2) y `TestLecturaCompatibleDeLaTablaHeredada` (normalizacion/
      extraccion, Python/SQLite) siembran la tabla heredada con una fila
      propia, una ajena y una ambigua, y comprueban que la propia Y la
      ambigua (marcada) se recuperan, nunca la ajena.
      Verificado con Docker real sobre un volumen limpio con la tabla
      heredada sembrada manualmente vía `psql` (simulando datos reales
      pre-T-58, no solo el caso de un volumen nuevo, incluida la fila
      `VALIDACION_APLICADA`): `GET /eventos-auditoria` de records-custodia
      devolvió únicamente su propio evento heredado; normalizacion y
      extraccion devolvieron cada uno su propio evento (`origen_verificado:
      true`) Y la fila ambigua compartida (`origen_verificado: false`),
      ninguno el evento ajeno del otro; un evento nuevo escrito después
      coexiste correctamente con lo heredado. `./test.sh` completo del repo
      en verde (Gradle incluidas las 14 suites de records-custodia; pytest:
      eval-harness 4, normalizacion 43 [+2], extraccion 58 [+2],
      clasificacion 27, enriquecimiento 29, indexacion-busqueda 50).
      Colección Postman completa reverificada dos corridas seguidas sobre
      volumen limpio: 98/98 peticiones, 143/143 aserciones, sin fallos.
      **Con T-58 cerrada, no queda ninguna tarea abierta en el backlog
      actual.**

- [x] T-59 Scaffold de la capa UI (`specs/008-ui-demo/spec.md`): React +
      Vite + TypeScript en `contexts/ui-demo/` (mismo criterio de ubicación
      que los demás contextos, aunque este no sea un bounded context de
      dominio), proxy inverso curado (nginx, nuevo servicio de
      infraestructura pura — traduce `/api/<contexto>/...` a la red interna
      de docker-compose, ver §2/§4 de la spec) y wiring en un nuevo
      `docker-compose.demo.yml` (overlay sobre `docker-compose.saas.yml`,
      mismo patrón que `docker-compose.local-ports.yml` pero sirviendo la
      UI construida además de mapear puertos). Sin ningún RF-UI todavía —
      infraestructura pura, mismo criterio que T-16/T-18/T-35 (Dockerfile +
      wiring antes del primer RF). El proxy solo enruta a los contextos NO
      bloqueados por el prerrequisito de §1 de la spec (Seguridad y Acceso,
      Clasificación, Validación Humana, Normalización, Extracción,
      Enriquecimiento, Indexación y Búsqueda) — Captura/Ingesta y
      Records/Custodia quedan sin ruta en el proxy hasta T-63.
      Vitest + Testing Library configurados para componentes puros;
      Playwright configurado para e2e contra el stack real de
      docker-compose (RNF-UI-004) — sin pruebas de RF todavía, solo que el
      andamiaje corre (`npm run build`, un smoke test de Playwright que
      confirma que la UI carga).
      **VETO real de Codex sobre la primera versión de este commit** (ver
      STATE.md): etiquetaba `MarcaDeSimulacion` como "RF-UI-012" entregado,
      contradiciendo la propia afirmación de T-59 de no implementar ningún
      RF-UI todavía — un componente sin ninguna pantalla que lo use no
      satisface ese RF (exige que TODA salida FICTICIA lo use). Corregido:
      recomentado como andamiaje compartido que T-61+ usará para cumplir
      RF-UI-012, sin reclamar el RF como cerrado.
      Hecho: `contexts/ui-demo/` (React 19 + Vite + TypeScript,
      react-router-dom para las rutas de cada RF-UI futuro), `src/api/
      cliente.ts` (helper `get`/`post` genérico contra `/api/<contexto>`,
      sin lógica de RF todavía), `MarcaDeSimulacion` (andamiaje compartido,
      sin RF cerrado todavía — ver nota arriba; su primera prueba Vitest +
      Testing Library verifica `role="status"` visible con texto, no un
      tooltip), `nginx.conf` (proxy curado, un bloque `location /api/<contexto>/` por cada
      contexto NO bloqueado, SPA fallback a `index.html` para
      react-router), `Dockerfile` (build Node + serve nginx, mismo
      contenedor sirve la UI y el proxy), `deploy/docker-compose.demo.yml`
      (nuevo overlay, puerto 8090, `depends_on` los siete contextos no
      bloqueados). Vite dev server con el mismo mapeo de proxy apuntando a
      `docker-compose.local-ports.yml` para iterar sin reconstruir la
      imagen.
      Verificado con Docker real: `docker build` + `docker compose -f
      saas.yml -f demo.yml up -d --build` (diez contenedores), `GET
      /api/seguridad-acceso/eventos-seguridad` vía el proxy devolvió 200
      real (no una respuesta estática) y `GET /api/clasificacion/no-existe`
      devolvió 404 real de Spring Boot — confirma que el proxy alcanza los
      servicios internos de verdad, no solo que nginx arranca. Smoke test
      de Playwright pasó contra `http://localhost:8090` (la UI dockerizada
      real) y contra el dev server de Vite. `./test.sh` actualizado con
      `npm --prefix contexts/ui-demo test`; completo del repo en verde
      (Gradle + pytest de siempre + el nuevo Vitest). `.dockerignore`
      ampliado con `node_modules`/`dist`/reportes de Playwright.
- [x] T-60 RF-UI-001 · Autenticación de la sesión de demo — pantalla de
      login real contra `POST /identidades/autenticacion` (Seguridad y
      Acceso) vía el proxy; sesión (identidad autenticada) guardada en el
      cliente (`localStorage`, ver `[CLARIFICAR]` de §8 sobre token real —
      no se inventa uno) y reenviada como `actor` en las llamadas
      siguientes. TDD: Playwright e2e con credenciales reales creadas
      contra el seguridad-acceso real del stack (rol + identidad, mismo
      patrón que la carpeta 3/4 de Postman) para el caso válido, y con
      credenciales inventadas para el caso de rechazo.
      Hecho: `src/sesion/sesion.ts` (guarda solo `id`/`actor`/`roles` en
      `localStorage` — deliberadamente NO persiste `credencialHash`, que sí
      viene en la respuesta real de `Identidad` pero esta capa nunca lo
      necesita ni lo reenvía); `src/paginas/Login.tsx` (formulario real,
      401 con `{"error": "..."}"` muestra el rechazo vía `role="alert"`,
      sin guardar ninguna sesión); ruta `/login` en `App.tsx`; `Inicio.tsx`
      ahora muestra "Sesión activa: {actor}" o un enlace a iniciar sesión.
      `e2e/rf-ui-001-autenticacion.spec.ts`: dos pruebas Playwright contra
      el stack real — la de credenciales válidas crea rol + identidad
      reales en seguridad-acceso primero (mismo patrón que la carpeta 3/4
      de Postman), después ejercita el login desde el navegador; la de
      rechazo confirma `role="alert"` visible y que `/` sigue mostrando el
      enlace de login (ninguna sesión quedó guardada).
      Verificado con Docker real: `docker compose -f saas.yml -f demo.yml
      up -d --build`, las tres pruebas e2e (smoke + las dos de RF-UI-001)
      en verde contra `http://localhost:8090`. `./test.sh` completo del
      repo en verde.
- [x] T-61 RF-UI-004 · Sugerencia de clasificación (FICTICIA) — pantalla
      que llama a `POST /clasificaciones` y muestra la sugerencia devuelta
      con la marca de simulación (RF-UI-012: componente reutilizable desde
      este primer uso, no una implementación ad hoc). TDD: Playwright e2e
      contra el clasificacion real del stack.
      Hecho: `src/paginas/Clasificacion.tsx` (formulario: documento, texto
      extraído, serie/subserie, confianza — el operador declara la
      candidata a mano, mismo criterio del resto del proyecto: nunca se
      implementa un clasificador real; `POST /clasificaciones` ya reenvía
      cada sugerencia a Records/Custodia servidor-a-servidor antes de
      responder, RF-CL-004/RF-RC-003); ruta `/clasificacion`; `Inicio.tsx`
      enlaza a ella cuando hay sesión activa. Primer uso real de
      `MarcaDeSimulacion` (cierra RF-UI-012 para este caso).
      **Hallazgo real durante la verificación**: `POST /clasificaciones`
      reenvía cada sugerencia a `POST /sugerencias` en Records/Custodia
      (servidor-a-servidor, no pasa por el proxy ni por el navegador), y
      `CapaAnticorrupcionSugerencias.recibir()` exige que el documento ya
      esté custodiado (`consultarDocumento`, 404 si no existe) — sin un
      documento real, la petición fallaba con 502
      ("records-custodia no respondió"). Como Records/Custodia sigue
      bloqueada para el navegador (prerrequisito de §1), el e2e crea el
      documento llamando directo al puerto local de records-custodia
      (`docker-compose.local-ports.yml`, 8082) como SETUP -- nunca a través
      de la UI. Documentado en `playwright.config.ts`: correr los e2e
      completos requiere las tres capas de compose juntas (`saas.yml` +
      `demo.yml` + `local-ports.yml`).
      Verificado con Docker real: `docker compose -f saas.yml -f demo.yml
      -f local-ports.yml up -d --build`, las cuatro pruebas e2e (smoke +
      RF-UI-001 ×2 + RF-UI-004) en verde contra `http://localhost:8090`.
      `./test.sh` completo del repo en verde.
- [x] T-62 RF-UI-005 · Cola de validación humana y decisión individual —
      pantalla que lista `GET /colas/clasificacion` ordenada por confianza
      y permite decidir (`POST /decisiones`) sobre la sugerencia de T-61;
      tras decidir, la sugerencia debe desaparecer de la cola. Este es el
      flujo funcional mínimo que Victor pidió (login → clasificación →
      decisión); no depende de Records/Custodia expuesto (Validación
      Humana ya orquesta la materialización servidor-a-servidor, ver §1 de
      la spec). TDD: Playwright e2e completo login→clasificar→decidir→cola
      vacía, contra el stack real.
      Hecho: `src/paginas/ColaDeValidacion.tsx` (lista la cola con
      `identidadId` de la sesión activa — `GET /colas/clasificacion`
      exige permiso `leer`/`documento`, RF-VH-007; decidir exige
      `decidir`/`documento`; al decidir, `POST /decisiones` con la
      `SugerenciaPendiente` completa y una `ClasificacionPropuesta`
      derivada de `contenidoPropuesto`, y la fila se retira localmente de
      la lista tras la respuesta real); ruta `/cola-validacion`;
      `Inicio.tsx` enlaza a ella.
      `e2e/rf-ui-005-flujo-clasificacion-decision.spec.ts`: el flujo
      COMPLETO de punta a punta que pidió Victor — crea rol (con los dos
      permisos) + identidad + documento custodiado (setup vía puertos
      locales), login real, genera la sugerencia, la ve en la cola marcada
      simulada, decide, y confirma que desaparece — sin ningún doble.
      **Hallazgo real durante la verificación**: el primer intento de este
      e2e falló por colisión de estado compartido entre corridas — la cola
      de Validación Humana es estado persistente (mismo patrón exacto que
      T-57/T-58 encontraron con Postman): con `serie`/`subserie` literales
      ("serie-1"/"subserie-1", reutilizados de T-61), la aserción sobre la
      cola encontraba TRES coincidencias (de corridas anteriores), no una.
      Corregido con `serie`/`subserie` únicos por corrida.
      **Codex no vetó constitucionalmente pero exigió corrección funcional
      antes de aceptar el commit**: "Aceptar decisión" llegaba a
      Records/Custodia como CORRECCIÓN, no como aceptación, para toda
      sugerencia de Clasificación con subserie. Causa raíz real de
      BACKEND, no de la UI: `ValidacionHumana.kt`
      (`GestionDeDecisiones.construirDecision`) comparaba
      `contenidoPropuesto == serieId`, asumiendo el formato "serie" plano
      del EMISOR FICTICIO original (T-08) — pero
      `a_sugerencia_saliente_de_clasificacion` (T-45, clasificacion) porta
      "serie/subserie". Nunca se había ejercitado antes end-to-end (los
      tests existentes de `ValidacionHumanaTest.kt` solo cubrían el caso
      sin subserie). Corregido: `contenidoEsperado` reconstruye
      "serie/subserie" cuando `subserieId` no es nulo. Dos pruebas
      unitarias nuevas (aceptación y corrección con subserie) = 5/5
      verdes en `ValidacionHumanaTest.kt`. E2e reforzado con verificación
      servidor-a-servidor (`GET /documentos/correcciones` antes/después,
      confirma que el volumen no cambió) y con los resultados de
      creación de rol/identidad verificados explícitamente.
      Verificado con Docker real: cinco pruebas e2e, dos corridas seguidas
      sin fallos contra `http://localhost:8090` (saas.yml + demo.yml +
      local-ports.yml). `./test.sh` completo del repo en verde.
- [x] T-63 Autorización real en Records/Custodia — cierra, PARA
      RECORDS/CUSTODIA únicamente (Captura/Ingesta queda fuera de alcance
      de esta tarea, ver T-64+), el prerrequisito de arquitectura de §1 de
      `specs/008-ui-demo/spec.md`: nuevo puerto `VerificadorDeAutorizacion`
      (mismo patrón ya aceptado por Codex en Extracción/T-41b —
      `VerificadorDeAutorizacionHttp` contra `POST /autorizacion` de
      Seguridad y Acceso), consultado en los endpoints que el proxy de la
      demo necesita exponer (`GET /documentos/{id}`, `GET
      /documentos/{id}/original`, `GET /documentos/{id}/sugerencias`,
      `POST /documentos/{id}/decisiones`, `GET /eventos-auditoria`, `POST
      /documentos/{id}/verificacion-integridad`), rechazando con 403 si el
      actor no tiene el permiso correspondiente. TDD: mismo criterio que
      T-41b — un test que confirma actor sin permiso → 403 y actor
      autorizado → 200/materializa. Tras esto: actualizar
      `spec-infra-servicios.md` §10 (Records/Custodia ya no queda listada
      como "sin deber exponerse"; Captura/Ingesta sigue así hasta que se
      haga lo mismo ahí) y `specs/008-ui-demo/spec.md` §1/§4/§5 (desbloquear
      las rutas de Records/Custodia en el proxy — RF-UI-003 deja de estar
      bloqueada; RF-UI-002 sigue bloqueada, depende de Captura/Ingesta).
      Añadir la ruta de Records/Custodia al proxy curado (T-59) una vez
      cerrado.
      Hecho: `VerificadorDeAutorizacion`/`AccesoDenegadoException` en
      `CustodiaOriginales.kt`; `VerificadorDeAutorizacionHttp` real en
      `integracion/IntegracionHttp.kt` (primera integración saliente de
      records-custodia, mismo contrato `POST /autorizacion` que
      validacion-humana/T-30). Corrección de alcance real frente al texto
      original de esta tarea: `POST /documentos/{id}/decisiones` NO se
      protegió — RF-UI-005 (ya aprobada por Codex) demuestra que el
      navegador nunca llama ese endpoint directamente, Validación Humana ya
      lo orquesta servidor-a-servidor; protegerlo habría sido inventar un
      requisito que ninguna spec pide. El alcance real quedó en cinco
      endpoints (`GET /documentos/{id}`, `GET /documentos/{id}/original`,
      `GET /documentos/{id}/sugerencias`, `GET /eventos-auditoria`,
      `POST /documentos/{id}/verificacion-integridad`) — exactamente los
      que `specs/008-ui-demo/spec.md` §1 (ya aprobada) dice que la UI
      necesita exponer. La verificación vive en la capa HTTP
      (`DocumentosController`/`EventosAuditoriaController`), no en el
      dominio — mismo criterio que RF-VH-007 en
      validacion-humana/http/ColasController.kt: es una comprobación de
      quién puede leer/actuar a través del proxy curado, no un invariante
      del agregado; los llamadores internos entre contextos (p. ej.
      `CapaAnticorrupcionSugerencias.recibir`) siguen llamando los métodos
      de dominio directamente, sin `identidadId`.
      **Hallazgo real durante la verificación contra Docker real** (no en
      los tests de Gradle, que mockean `/autorizacion` con
      `MockRestServiceServer`): el primer intento de `POST
      /documentos/{id}/verificacion-integridad` reutilizaba `request.actor`
      (el nombre libre que atribuye el evento de auditoría) como la
      identidad a autorizar — devolvía 403 incluso con un rol correcto,
      porque seguridad-acceso resuelve permisos por `identidadId` (el id
      real de la Identidad), no por ese nombre libre; ambos pueden
      coincidir por casualidad pero no son el mismo campo (mismo criterio
      que `DecisionRequest` en validacion-humana, que ya los mantiene
      separados). Corregido separando el DTO: `VerificacionRequest`
      (`actor`, `fecha`) sigue sirviendo al endpoint agregado no protegido
      `POST /verificacion-integridad`; el endpoint por documento usa el
      nuevo `VerificacionDeDocumentoRequest` (`identidadId`, `actor`,
      `fecha`). Verificado de nuevo contra Docker real: `GET
      /documentos/{id}` sin `identidadId` → 400; con `identidadId` sin rol
      → 403; `POST .../verificacion-integridad` con `identidadId`
      correcto y rol `verificar`/`documento` → 200; proxy
      `/api/records-custodia/...` de ui-demo (puerto 8090) reenvía y
      aplica la misma verificación. `./gradlew :contexts:records-custodia:test`
      46/46 verdes (11 nuevas: cinco pares 200/403 más la denegación de
      `/original`). Suite e2e de ui-demo (5 pruebas) sin regresión, dos
      corridas seguidas. `./test.sh` completo del repo en verde.
      `spec-infra-servicios.md` §4/§10 y `specs/008-ui-demo/spec.md`
      §1/§4/§5 actualizados: RF-UI-003 desbloqueada; RF-UI-002 (Captura/
      Ingesta) sigue bloqueada. `nginx.conf`/`vite.config.ts`/
      `docker-compose.demo.yml` ganan la ruta de records-custodia.
      `docker-compose.saas.yml`/`onprem.yml`: records-custodia gana
      `SEGURIDAD_ACCESO_BASE_URL` y `depends_on: seguridad-acceso`, sigue
      sin `ports:` (el prerrequisito habilita el proxy de ui-demo, no un
      puerto directo al host).
      **VETO real de Codex sobre el primer intento del proxy** (commit
      `aaf9929`, ver STATE.md): `location /api/records-custodia/ {
      proxy_pass ...; }` era un PREFIJO -- reenviaba todo el contrato, no
      solo los cinco endpoints protegidos; `POST .../decisiones` seguía
      alcanzable desde el navegador sin autorización. Corregido con cinco
      bloques `nginx.conf` explícitos por ruta+método (más un `return 404`
      de cierre) y un `bypass()` equivalente en `vite.config.ts`. Dos
      hallazgos reales adicionales durante esa corrección: "correcciones"
      colisiona con la forma de `/documentos/{id}` (excluido con un
      `location =` exacto/exclusión explícita en `bypass()`); `proxy_pass`
      con variables exige un `resolver 127.0.0.11` (DNS embebido de
      Docker) o nginx responde 502. Nuevo e2e
      `rnf-ui-001-proxy-curado-records-custodia.spec.ts` (pedido
      explícitamente por Codex) verifica las cinco rutas permitidas y las
      seis fuera de alcance a través del proxy real. 13 pruebas e2e, dos
      corridas seguidas en verde.
- [ ] T-64 RF-UI-011 · Bitácora de auditoría consolidada, alcance inicial:
      panel de decisión de T-62 — tras decidir sobre la sugerencia de
      clasificación, la UI consulta `GET /eventos-auditoria` de
      Records/Custodia (ahora expuesto tras T-63) y muestra el evento
      `SUGERENCIA_RECIBIDA`/`DECISION_HUMANA_MATERIALIZADA` atribuible,
      cerrando el flujo pedido por Victor: login → clasificación →
      decisión → bitácora. TDD: Playwright e2e que corre el flujo completo
      y verifica que la bitácora muestra el evento con actor y fecha no
      vacíos.
- [x] T-65 Corrección de aceptación individual de RF-UI-005 — al pulsar
      «Aceptar decisión» sobre la sugerencia de clasificación `serie/subserie`
      de T-61, Validación Humana debe materializar una aceptación
      (`esCorreccion=false`), no una corrección por comparar sólo `serieId`.
      Definir y aplicar la comparación conforme al contrato existente, sin
      inventar un formato nuevo; conservar una corrección explícita y
      distinguible si la UI la ofrece. TDD: ampliar el e2e T-62 o una prueba
      de integración real para comprobar la clasificación/evento resultante y
      ambas ramas aceptación/corrección; comprobar además las respuestas del
      setup de rol e identidad.
      Hecho: esta tarea fue creada por el propio Codex durante su revisión de
      T-62 (commit `f11c848`), describiendo un defecto que su misma revisión
      constató ya corregido en el commit siguiente, `a7af4b8`
      (`ValidacionHumana.kt::construirDecision` reconstruye
      `contenidoEsperado` como `serieId/subserieId` cuando hay subserie, con
      dos pruebas unitarias nuevas cubriendo aceptación y corrección). Se
      marca cerrada aquí, sin nuevo trabajo de dominio, por instrucción
      explícita de Codex en esa misma revisión: "Debe marcarse cerrada al
      corregir el e2e para no reabrir artificialmente una tarea completada."
