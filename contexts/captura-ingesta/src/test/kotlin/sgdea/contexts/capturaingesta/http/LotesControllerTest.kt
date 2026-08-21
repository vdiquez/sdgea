package sgdea.contexts.capturaingesta.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

// specs/spec-infra-servicios.md §3 · Contrato mínimo — captura-ingesta.
// Cada endpoint traduce un método de dominio ya probado por TDD (T-01/T-05/T-06);
// estas pruebas verifican la traducción HTTP y la persistencia entre peticiones
// separadas (que es lo nuevo de T-16), no reglas de negocio nuevas.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LotesControllerTest {

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = TestRestTemplate()

    private fun url(path: String) = "http://localhost:$port$path"

    private val fecha = "2026-08-21T10:00:00Z"

    @Test
    fun `POST lotes carga un lote y responde con items Recibido por artefacto - RF-CI-001 RF-CI-007`() {
        val request = mapOf(
            "loteId" to "lote-http-001",
            "artefactos" to listOf(
                mapOf("id" to "a1", "nombre" to "expediente-001.pdf"),
                mapOf("id" to "a2", "nombre" to "expediente-002.tif"),
            ),
            "inventario" to listOf("a1", "a2"),
            "fuente" to "escaner-sala-3",
            "fecha" to fecha,
        )

        val response = restTemplate.postForEntity(url("/lotes"), request, Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val items = response.body!!["items"] as List<*>
        assertEquals(2, items.size)
        items.forEach {
            val item = it as Map<*, *>
            assertEquals("RECIBIDO", item["estado"])
            val procedencia = item["procedencia"] as Map<*, *>
            assertEquals("escaner-sala-3", procedencia["fuente"])
            assertEquals("carga_por_lote", procedencia["disparador"])
        }
    }

    @Test
    fun `GET conteo refleja el lote persistido en una peticion POST anterior - RF-CI-008`() {
        val loteId = "lote-http-002"
        val request = mapOf(
            "loteId" to loteId,
            "artefactos" to listOf(mapOf("id" to "a1", "nombre" to "x.pdf")),
            "inventario" to listOf("a1"),
            "fuente" to "escaner-sala-3",
            "fecha" to fecha,
        )
        restTemplate.postForEntity(url("/lotes"), request, Map::class.java)

        val response = restTemplate.getForEntity(url("/lotes/$loteId/conteo"), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body!!["total"])
        assertEquals(0, response.body!!["terminales"])
        assertEquals(false, response.body!!["sinPerdidaSilenciosa"])
    }

    @Test
    fun `GET conciliacion detecta faltantes y sobrantes del lote persistido - RF-CI-002`() {
        val loteId = "lote-http-003"
        val request = mapOf(
            "loteId" to loteId,
            "artefactos" to listOf(mapOf("id" to "a1", "nombre" to "x.pdf")),
            "inventario" to listOf("a1", "a2"),
            "fuente" to "escaner-sala-3",
            "fecha" to fecha,
        )
        restTemplate.postForEntity(url("/lotes"), request, Map::class.java)

        val response = restTemplate.getForEntity(url("/lotes/$loteId/conciliacion"), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("a2"), response.body!!["faltantes"])
        assertTrue((response.body!!["sobrantes"] as List<*>).isEmpty())
    }

    @Test
    fun `GET conteo de un lote inexistente responde 404 con el formato de error unificado - specs-infra-servicios §5`() {
        val response = restTemplate.getForEntity(url("/lotes/no-existe/conteo"), Map::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue((response.body!!["error"] as String).contains("no-existe"))
    }
}
