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
}
