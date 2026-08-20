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
