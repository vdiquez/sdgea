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
- [ ] T-46 Dockerfile real de clasificacion + wiring en
      `docker-compose.{saas,onprem,local-ports}.yml`, mismo patrón Python
      que T-35/T-42 pero SIN Postgres propio (mismo criterio que
      validacion-humana, T-31, sin `DB_HOST`/etc. — solo
      `RECORDS_CUSTODIA_BASE_URL` y `depends_on: records-custodia`); puerto
      siguiente disponible (8087) en `docker-compose.local-ports.yml`.
- [ ] T-47 Colección Postman con el ciclo completo de Clasificación,
      mismo patrón que T-36/T-43: custodiar un documento en records-custodia
      → enviar una sugerencia de clasificación FICTICIA vía Clasificación →
      verificar en records-custodia (`GET /documentos/{id}/sugerencias`)
      que llegó como `Sugerencia` real → repetir para agrupamiento → un
      texto marcado no clasificable. Segundo flujo end-to-end real de tres
      servicios (records-custodia + Clasificación, mismo nivel que
      Validación Humana↔Normalización en T-38). Verificar con Docker real y
      dos corridas de Newman seguidas sin fallos — si el loop headless
      llega hasta aquí sin poder tocar Docker/`npx` (mismo límite real que
      T-43), es correcto que deje el trabajo sin comitear y explique por
      qué, no que finja haberlo verificado; Victor o una sesión interactiva
      lo retoma.
