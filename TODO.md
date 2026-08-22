# TODO — F2/F3: corte vertical determinístico + arnés (clasificador ficticio)
- [x] T-01 RF-CI-001 Ingesta por lote: artefactos + inventario -> ítems `Recibido`
- [?] T-02 RF-CI-006 Validación y cuarentena con razón registrada
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
