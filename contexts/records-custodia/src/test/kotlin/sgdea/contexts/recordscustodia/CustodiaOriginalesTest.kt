package sgdea.contexts.recordscustodia

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// RF-RC-001 · Custodia del original inmutable
class CustodiaOriginalesTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-20T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    @Test
    fun `dado un original depositado, cuando se consulta, sus bytes y su huella coinciden con lo depositado`() {
        val custodia = CustodiaOriginales()
        val bytes = "contenido del documento".toByteArray()

        custodia.custodiar(
            id = "doc-1",
            bytes = bytes,
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-20T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val original = custodia.consultar("doc-1")

        assertContentEquals(bytes, original.bytes)
        assertEquals(huellaSha256(bytes), original.huella)
    }

    @Test
    fun `dado un intento de modificar un original, cuando se ejecuta, se rechaza y se genera un evento de auditoria`() {
        val custodia = CustodiaOriginales()
        val bytesOriginales = "contenido original".toByteArray()
        custodia.custodiar(
            id = "doc-1",
            bytes = bytesOriginales,
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-20T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )

        assertFailsWith<ModificacionDeOriginalRechazadaException> {
            custodia.intentarModificar(
                id = "doc-1",
                bytesNuevos = "contenido alterado".toByteArray(),
                actor = "usuario-malicioso",
                fecha = Instant.parse("2026-08-20T01:00:00Z"),
            )
        }

        assertContentEquals(bytesOriginales, custodia.consultar("doc-1").bytes)
        assertTrue(custodia.eventosDeAuditoria.any { it.tipo == "INTENTO_MODIFICACION_RECHAZADO" && it.actor == "usuario-malicioso" })
    }

    private fun huellaSha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

// RF-RC-002 · Registro de procedencia
// Dado un documento, Cuando se consulta su procedencia, Entonces incluye fuente, fecha y lote/flujo.
class RegistroDeProcedenciaTest {

    @Test
    fun `un documento custodiado con procedencia la expone completa al consultarla`() {
        val custodia = CustodiaOriginales()
        val fecha = Instant.parse("2026-08-20T10:00:00Z")
        val procedencia = Procedencia(fuente = "escaner-sala-3", fecha = fecha, loteOFlujoId = "lote-001")

        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = fecha,
            procedencia = procedencia,
        )

        assertEquals(procedencia, custodia.consultarProcedencia("doc-1"))
    }
}

// RF-RC-003 · Recepción de sugerencias como propuestas
class RecepcionDeSugerenciasTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-21T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    // EMISOR FICTICIO: no representa un clasificador real (constitución: ningún
    // componente probabilístico real se implementa fuera del arnés); solo
    // ejercita el contrato de traducción de la capa anticorrupción.
    private val sugerenciaFicticiaEntrante = SugerenciaEntrante(
        documentoId = "doc-1",
        tipo = "clasificacion",
        contenidoPropuesto = "serie-1",
        modeloId = "emisor-ficticio-v0",
        evidencia = listOf("pagina-1"),
        confianza = 0.42,
    )

    @Test
    fun `dado un documento sin decision humana, cuando se recibe una sugerencia, su clasificacion y estado permanecen sin cambio`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val documentoAntes = custodia.consultarDocumento("doc-1")
        val capa = CapaAnticorrupcionSugerencias(custodia)

        capa.recibir(sugerenciaFicticiaEntrante, fecha = Instant.parse("2026-08-21T01:00:00Z"))

        assertEquals(documentoAntes, custodia.consultarDocumento("doc-1"))
    }

    @Test
    fun `dada una sugerencia almacenada, cuando se consulta, expone modelo, evidencia y confianza`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val capa = CapaAnticorrupcionSugerencias(custodia)

        capa.recibir(sugerenciaFicticiaEntrante, fecha = Instant.parse("2026-08-21T01:00:00Z"))
        val sugerencias = capa.sugerenciasDe("doc-1")

        assertEquals(1, sugerencias.size)
        assertEquals("emisor-ficticio-v0", sugerencias[0].modeloId)
        assertEquals(listOf("pagina-1"), sugerencias[0].evidencia)
        assertEquals(0.42, sugerencias[0].confianza)
    }

    // P-08: toda transición de estado, incluida la recepción de una sugerencia, genera
    // un evento de auditoría inmutable, atribuible, fechado y con estado anterior/posterior.
    @Test
    fun `dada una sugerencia recibida, cuando se procesa, se anexa un evento de auditoria atribuible con actor y fecha`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val bitacora = BitacoraAuditoria()
        val capa = CapaAnticorrupcionSugerencias(custodia, bitacora = bitacora)
        val fechaRecepcion = Instant.parse("2026-08-21T01:00:00Z")

        capa.recibir(sugerenciaFicticiaEntrante, fecha = fechaRecepcion)

        val evento = bitacora.todos.single { it.tipo == "SUGERENCIA_RECIBIDA" }
        assertEquals("emisor-ficticio-v0", evento.actor)
        assertEquals(fechaRecepcion, evento.fecha)
        assertEquals(null, evento.estadoAnterior)
        assertEquals("SUGERENCIA_RECIBIDA", evento.estadoPosterior)
    }
}

// RF-VH-001 (specs/007-validacion-humana/spec.md): sugerencias pendientes a
// través de todos los documentos, base de la cola de revisión de Validación
// Humana.
class SugerenciasPendientesTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-26T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    private val sugerenciaFicticiaEntrante = SugerenciaEntrante(
        documentoId = "doc-1",
        tipo = "clasificacion",
        contenidoPropuesto = "serie-1",
        modeloId = "emisor-ficticio-v0",
        evidencia = listOf("pagina-1"),
        confianza = 0.42,
    )

    @Test
    fun `una sugerencia de un documento sin clasificar aparece en las pendientes`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-26T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val capa = CapaAnticorrupcionSugerencias(custodia)
        capa.recibir(sugerenciaFicticiaEntrante, fecha = Instant.parse("2026-08-26T01:00:00Z"))

        assertEquals(1, capa.sugerenciasPendientes().size)
    }

    @Test
    fun `una sugerencia de un documento ya clasificado no aparece en las pendientes`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-26T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val capa = CapaAnticorrupcionSugerencias(custodia)
        capa.recibir(sugerenciaFicticiaEntrante, fecha = Instant.parse("2026-08-26T01:00:00Z"))

        custodia.materializar(
            DecisionHumana(
                documentoId = "doc-1",
                actor = "archivista-1",
                fecha = Instant.parse("2026-08-26T02:00:00Z"),
                sugerenciasReferenciadas = emptyList(),
                clasificacionResultante = Clasificacion(documentoId = "doc-1", trdVersion = 1, serieId = "serie-1"),
            ),
        )

        assertTrue(capa.sugerenciasPendientes().isEmpty())
    }
}

// RF-RC-004 · Materialización por decisión humana
class MaterializacionPorDecisionHumanaTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-21T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    @Test
    fun `dada una decision humana sobre un documento, cuando se aplica, el cambio queda registrado con el actor y la fecha`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val clasificacion = Clasificacion(documentoId = "doc-1", trdVersion = 1, serieId = "serie-1")
        val decision = DecisionHumana(
            documentoId = "doc-1",
            actor = "archivista-1",
            fecha = Instant.parse("2026-08-21T02:00:00Z"),
            sugerenciasReferenciadas = emptyList(),
            clasificacionResultante = clasificacion,
        )

        val documento = custodia.materializar(decision)

        assertEquals(clasificacion, documento.clasificacion)
        assertEquals(clasificacion, custodia.consultarDocumento("doc-1").clasificacion)
        assertTrue(
            custodia.eventosDeAuditoria.any {
                it.tipo == "DECISION_HUMANA_MATERIALIZADA" && it.actor == "archivista-1" && it.fecha == decision.fecha
            },
        )
    }

    @Test
    fun `dado cualquier cambio de clasificacion sin decision humana asociada, el sistema lo impide`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )
        val capa = CapaAnticorrupcionSugerencias(custodia)
        val sugerenciaFicticiaEntrante = SugerenciaEntrante(
            documentoId = "doc-1",
            tipo = "clasificacion",
            contenidoPropuesto = "serie-1",
            modeloId = "emisor-ficticio-v0",
            evidencia = listOf("pagina-1"),
            confianza = 0.42,
        )

        capa.recibir(sugerenciaFicticiaEntrante, fecha = Instant.parse("2026-08-21T01:00:00Z"))

        assertEquals(null, custodia.consultarDocumento("doc-1").clasificacion)
    }
}

// RF-RC-005 · Bitácora de auditoría inmutable
class BitacoraDeAuditoriaInmutableTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-21T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    @Test
    fun `dada una transicion de estado, cuando ocurre, existe un evento con actor, fecha, tipo, estado anterior y posterior`() {
        val custodia = CustodiaOriginales()
        val fecha = Instant.parse("2026-08-21T00:00:00Z")

        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = fecha,
            procedencia = procedenciaDePrueba,
        )

        val evento = custodia.eventosDeAuditoria.single { it.tipo == "ORIGINAL_CUSTODIADO" }
        assertEquals("sistema-ingesta", evento.actor)
        assertEquals(fecha, evento.fecha)
        assertEquals(null, evento.estadoAnterior)
        assertEquals("CUSTODIADO", evento.estadoPosterior)
    }

    @Test
    fun `dado un evento de auditoria existente, cuando se intenta modificar, se rechaza`() {
        val bitacora = BitacoraAuditoria()
        bitacora.anexar(
            EventoAuditoria(
                actor = "sistema-ingesta",
                fecha = Instant.parse("2026-08-21T00:00:00Z"),
                tipo = "ORIGINAL_CUSTODIADO",
                estadoAnterior = null,
                estadoPosterior = "CUSTODIADO",
            ),
        )

        assertFailsWith<ModificacionDeEventoAuditoriaRechazadaException> {
            bitacora.intentarModificar(
                indice = 0,
                eventoNuevo = EventoAuditoria(
                    actor = "atacante",
                    fecha = Instant.parse("2026-08-21T01:00:00Z"),
                    tipo = "ORIGINAL_CUSTODIADO",
                    estadoAnterior = null,
                    estadoPosterior = "ALTERADO",
                ),
            )
        }
        assertEquals(1, bitacora.todos.size)
        assertEquals("CUSTODIADO", bitacora.todos[0].estadoPosterior)
    }

    @Test
    fun `dado un evento de auditoria existente, cuando se intenta borrar, se rechaza`() {
        val bitacora = BitacoraAuditoria()
        bitacora.anexar(
            EventoAuditoria(
                actor = "sistema-ingesta",
                fecha = Instant.parse("2026-08-21T00:00:00Z"),
                tipo = "ORIGINAL_CUSTODIADO",
                estadoAnterior = null,
                estadoPosterior = "CUSTODIADO",
            ),
        )

        assertFailsWith<ModificacionDeEventoAuditoriaRechazadaException> {
            bitacora.intentarBorrar(indice = 0)
        }
        assertEquals(1, bitacora.todos.size)
    }
}

// RF-RC-006 · TRD como objeto versionado
class TrdComoObjetoVersionadoTest {

    private fun trd(version: Int) = Trd(
        version = version,
        vigenteDesde = Instant.parse("2026-08-2${version}T00:00:00Z"),
        series = listOf(
            Serie(
                id = "serie-1",
                nombre = "Gestión documental",
                reglaRetencion = ReglaRetencion(tiempoRetencionAnios = 5, disposicionFinal = "Conservación total"),
            ),
        ),
    )

    @Test
    fun `dada una clasificacion de documento, cuando se consulta, referencia una version especifica de la TRD`() {
        val registro = RegistroTrd()
        registro.publicar(trd(1))

        val clasificacion = Clasificacion(documentoId = "doc-1", trdVersion = 1, serieId = "serie-1")

        assertEquals(1, clasificacion.trdVersion)
        assertEquals(1, registro.version(clasificacion.trdVersion).version)
    }

    @Test
    fun `dada una nueva version de la TRD, cuando se publica, las clasificaciones previas conservan su referencia a la version anterior`() {
        val registro = RegistroTrd()
        registro.publicar(trd(1))
        val clasificacionPrevia = Clasificacion(documentoId = "doc-1", trdVersion = 1, serieId = "serie-1")

        registro.publicar(trd(2))

        assertEquals(1, clasificacionPrevia.trdVersion)
        assertEquals(1, registro.version(1).version)
        assertEquals(2, registro.version(2).version)
    }

    @Test
    fun `dada una version ya publicada, cuando se publica de nuevo, la publicacion se rechaza y no la sobrescribe`() {
        val registro = RegistroTrd()
        registro.publicar(trd(1))

        val trdModificada = trd(1).copy(vigenteDesde = Instant.parse("2026-08-25T00:00:00Z"))

        assertFailsWith<PublicacionDeTrdRechazadaException> {
            registro.publicar(trdModificada)
        }
        assertEquals(trd(1).vigenteDesde, registro.version(1).vigenteDesde)
    }
}

// RF-RC-009 · Verificación de integridad
class VerificacionDeIntegridadTest {

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-21T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    @Test
    fun `dado un original que coincide con su huella, cuando se verifica, no se reporta discrepancia ni se genera evento`() {
        val custodia = CustodiaOriginales()
        custodia.custodiar(
            id = "doc-1",
            bytes = "contenido original".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )

        val resultado = custodia.verificarIntegridad(
            id = "doc-1",
            actor = "auditor-1",
            fecha = Instant.parse("2026-08-21T03:00:00Z"),
        )

        assertTrue(resultado.coincide)
        assertEquals(resultado.huellaRegistrada, resultado.huellaCalculada)
        assertTrue(custodia.eventosDeAuditoria.none { it.tipo == "DISCREPANCIA_DE_INTEGRIDAD" })
    }

    @Test
    fun `dada una verificacion de integridad, cuando un original no coincide con su huella, se reporta como discrepancia y se genera un evento de auditoria`() {
        val bytesOriginales = "contenido original".toByteArray()
        val bytesDivergentesEnElMedio = "contenido corrompido en el medio".toByteArray()
        val custodia = CustodiaOriginales(lectorDeAlmacenamiento = { bytesDivergentesEnElMedio })
        custodia.custodiar(
            id = "doc-1",
            bytes = bytesOriginales,
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-21T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )

        val resultado = custodia.verificarIntegridad(
            id = "doc-1",
            actor = "auditor-1",
            fecha = Instant.parse("2026-08-21T03:00:00Z"),
        )

        assertTrue(!resultado.coincide)
        assertTrue(
            custodia.eventosDeAuditoria.any {
                it.tipo == "DISCREPANCIA_DE_INTEGRIDAD" && it.actor == "auditor-1" && it.fecha == Instant.parse("2026-08-21T03:00:00Z")
            },
        )
    }

    @Test
    fun `verificarTodos agrega las discrepancias de todos los originales custodiados en un unico reporte`() {
        val bytesOk = "contenido intacto".toByteArray()
        val bytesDivergentes = "contenido corrompido".toByteArray()
        val custodia = CustodiaOriginales(
            lectorDeAlmacenamiento = { id -> if (id == "doc-corrupto") bytesDivergentes else bytesOk },
        )
        custodia.custodiar("doc-1", bytesOk, "sistema-ingesta", Instant.parse("2026-08-21T00:00:00Z"), procedenciaDePrueba)
        custodia.custodiar("doc-corrupto", bytesOk, "sistema-ingesta", Instant.parse("2026-08-21T00:00:00Z"), procedenciaDePrueba)

        val reporte = custodia.verificarTodos(actor = "auditor-1", fecha = Instant.parse("2026-08-21T03:00:00Z"))

        assertEquals(2, reporte.resultados.size)
        assertEquals(listOf("doc-corrupto"), reporte.discrepancias.map { it.id })
    }
}
