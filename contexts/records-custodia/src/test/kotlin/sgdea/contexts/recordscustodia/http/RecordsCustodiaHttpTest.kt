package sgdea.contexts.recordscustodia.http

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

// specs/spec-infra-servicios.md §4 · Contrato mínimo — records-custodia.
// Cada endpoint traduce un método de dominio ya probado por TDD
// (T-03/T-08/T-09/T-11); estas pruebas verifican la traducción HTTP y la
// persistencia entre peticiones separadas (lo nuevo de T-17), no reglas de
// negocio nuevas.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordsCustodiaHttpTest {

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = TestRestTemplate()

    private fun url(path: String) = "http://localhost:$port$path"

    private val fecha = "2026-08-21T10:00:00Z"

    private fun custodiarDocumento(id: String, contenido: String = "contenido de $id"): Map<*, *> {
        val request = mapOf(
            "id" to id,
            "bytesBase64" to Base64.getEncoder().encodeToString(contenido.toByteArray()),
            "actor" to "sistema-ingesta",
            "fecha" to fecha,
            "procedencia" to mapOf("fuente" to "escaner-sala-3", "fecha" to fecha, "loteOFlujoId" to "lote-001"),
        )
        val response = restTemplate.postForEntity(url("/documentos"), request, Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!
    }

    @Test
    fun `POST documentos custodia un original y responde con su huella - RF-RC-001`() {
        val body = custodiarDocumento("doc-http-001", "contenido original")

        assertEquals("doc-http-001", body["id"])
        assertTrue((body["huella"] as String).isNotBlank())
    }

    @Test
    fun `GET documentos original refleja el original persistido en una peticion POST anterior - RF-RC-001`() {
        custodiarDocumento("doc-http-002", "contenido persistido")

        val response = restTemplate.getForEntity(url("/documentos/doc-http-002/original"), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val bytes = Base64.getDecoder().decode(response.body!!["bytes"] as String)
        assertEquals("contenido persistido", String(bytes))
    }

    @Test
    fun `GET documentos procedencia refleja la procedencia persistida - RF-RC-002`() {
        custodiarDocumento("doc-http-003")

        val response = restTemplate.getForEntity(url("/documentos/doc-http-003/procedencia"), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("escaner-sala-3", response.body!!["fuente"])
        assertEquals("lote-001", response.body!!["loteOFlujoId"])
    }

    @Test
    fun `GET documentos de un id inexistente responde 404 con el formato de error unificado - specs-infra-servicios §5`() {
        val response = restTemplate.getForEntity(url("/documentos/no-existe"), Map::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue((response.body!!["error"] as String).contains("no-existe"))
    }

    @Test
    fun `POST documentos decisiones materializa la clasificacion y persiste entre peticiones - RF-RC-004`() {
        custodiarDocumento("doc-http-004")
        val decision = mapOf(
            "actor" to "archivista-1",
            "fecha" to "2026-08-21T12:00:00Z",
            "sugerenciasReferenciadas" to emptyList<Any>(),
            "clasificacionResultante" to mapOf(
                "documentoId" to "doc-http-004",
                "trdVersion" to 1,
                "serieId" to "serie-1",
                "subserieId" to null,
            ),
        )

        val postResponse = restTemplate.postForEntity(url("/documentos/doc-http-004/decisiones"), decision, Map::class.java)
        assertEquals(HttpStatus.OK, postResponse.statusCode)

        val getResponse = restTemplate.getForEntity(url("/documentos/doc-http-004"), Map::class.java)
        val clasificacion = getResponse.body!!["clasificacion"] as Map<*, *>
        assertEquals("serie-1", clasificacion["serieId"])
    }

    @Test
    fun `POST sugerencias y GET documentos sugerencias exponen la sugerencia recibida - RF-RC-003`() {
        custodiarDocumento("doc-http-005")
        val entrada = mapOf(
            "documentoId" to "doc-http-005",
            "tipo" to "clasificacion",
            "contenidoPropuesto" to "serie-1",
            "modeloId" to "emisor-ficticio-v0",
            "evidencia" to listOf("pagina-1"),
            "confianza" to 0.42,
            "fecha" to fecha,
        )

        val postResponse = restTemplate.postForEntity(url("/sugerencias"), entrada, Map::class.java)
        assertEquals(HttpStatus.CREATED, postResponse.statusCode)

        val getResponse = restTemplate.getForEntity(url("/documentos/doc-http-005/sugerencias"), List::class.java)
        val sugerencias = getResponse.body as List<*>
        assertEquals(1, sugerencias.size)
        assertEquals("emisor-ficticio-v0", (sugerencias[0] as Map<*, *>)["modeloId"])
    }

    @Test
    fun `GET sugerencias pendientes incluye la sugerencia de un documento sin clasificar y la excluye tras materializar - RF-VH-001`() {
        custodiarDocumento("doc-http-005b")
        val entrada = mapOf(
            "documentoId" to "doc-http-005b",
            "tipo" to "clasificacion",
            "contenidoPropuesto" to "serie-1",
            "modeloId" to "emisor-ficticio-v0",
            "evidencia" to listOf("pagina-1"),
            "confianza" to 0.42,
            "fecha" to fecha,
        )
        restTemplate.postForEntity(url("/sugerencias"), entrada, Map::class.java)

        val antes = restTemplate.getForEntity(url("/sugerencias/pendientes"), List::class.java)
        assertTrue((antes.body as List<*>).any { (it as Map<*, *>)["documentoId"] == "doc-http-005b" })

        val decision = mapOf(
            "actor" to "archivista-1",
            "fecha" to fecha,
            "sugerenciasReferenciadas" to emptyList<Any>(),
            "clasificacionResultante" to mapOf("documentoId" to "doc-http-005b", "trdVersion" to 1, "serieId" to "serie-1"),
        )
        restTemplate.postForEntity(url("/documentos/doc-http-005b/decisiones"), decision, Map::class.java)

        val despues = restTemplate.getForEntity(url("/sugerencias/pendientes"), List::class.java)
        assertTrue((despues.body as List<*>).none { (it as Map<*, *>)["documentoId"] == "doc-http-005b" })
    }

    @Test
    fun `GET documentos correcciones incluye una decision marcada esCorreccion y excluye una que no lo es - RF-VH-009`() {
        custodiarDocumento("doc-http-005c")
        val correccion = mapOf(
            "actor" to "archivista-1",
            "fecha" to fecha,
            "sugerenciasReferenciadas" to emptyList<Any>(),
            "clasificacionResultante" to mapOf("documentoId" to "doc-http-005c", "trdVersion" to 1, "serieId" to "serie-corregida"),
            "esCorreccion" to true,
        )
        restTemplate.postForEntity(url("/documentos/doc-http-005c/decisiones"), correccion, Map::class.java)

        custodiarDocumento("doc-http-005d")
        val aceptacion = mapOf(
            "actor" to "archivista-1",
            "fecha" to fecha,
            "sugerenciasReferenciadas" to emptyList<Any>(),
            "clasificacionResultante" to mapOf("documentoId" to "doc-http-005d", "trdVersion" to 1, "serieId" to "serie-1"),
            "esCorreccion" to false,
        )
        restTemplate.postForEntity(url("/documentos/doc-http-005d/decisiones"), aceptacion, Map::class.java)

        val response = restTemplate.getForEntity(url("/documentos/correcciones"), List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val correcciones = response.body as List<*>
        assertTrue(correcciones.any { (it as Map<*, *>)["estadoPosterior"] == "serie-corregida" })
        assertTrue(correcciones.none { (it as Map<*, *>)["estadoPosterior"] == "serie-1" })
        val entrada = correcciones.first { (it as Map<*, *>)["estadoPosterior"] == "serie-corregida" } as Map<*, *>
        assertEquals("PENDIENTE_DE_REREVISION", entrada["estadoDeRevision"])
        assertEquals("archivista-1", entrada["actor"])
    }

    @Test
    fun `POST verificacion-integridad de un documento intacto no reporta discrepancia - RF-RC-009`() {
        custodiarDocumento("doc-http-006")
        val request = mapOf("actor" to "auditor-1", "fecha" to "2026-08-21T13:00:00Z")

        val response = restTemplate.postForEntity(url("/documentos/doc-http-006/verificacion-integridad"), request, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!["coincide"])
    }

    @Test
    fun `POST verificacion-integridad agregado agrupa todos los originales custodiados - RF-RC-009`() {
        custodiarDocumento("doc-http-007")
        custodiarDocumento("doc-http-008")
        val request = mapOf("actor" to "auditor-1", "fecha" to "2026-08-21T13:00:00Z")

        val response = restTemplate.postForEntity(url("/verificacion-integridad"), request, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val resultados = response.body!!["resultados"] as List<*>
        assertTrue(resultados.size >= 2)
    }

    @Test
    fun `POST trd y GET trd version exponen la version publicada y persisten entre peticiones - RF-RC-006`() {
        val trd = mapOf(
            "version" to 7,
            "vigenteDesde" to fecha,
            "series" to listOf(
                mapOf(
                    "id" to "serie-1",
                    "nombre" to "Gestión documental",
                    "reglaRetencion" to mapOf("tiempoRetencionAnios" to 5, "disposicionFinal" to "Conservación total"),
                    "subseries" to emptyList<Any>(),
                ),
            ),
        )

        val postResponse = restTemplate.postForEntity(url("/trd"), trd, Map::class.java)
        assertEquals(HttpStatus.CREATED, postResponse.statusCode)

        val getResponse = restTemplate.getForEntity(url("/trd/7"), Map::class.java)
        assertEquals(HttpStatus.OK, getResponse.statusCode)
        assertEquals(7, getResponse.body!!["version"])
    }

    @Test
    fun `GET trd de una version inexistente responde 404`() {
        val response = restTemplate.getForEntity(url("/trd/999"), Map::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST trd sobre una version ya publicada se rechaza y no la sobrescribe - RF-RC-006, T-19`() {
        val trd = mapOf(
            "version" to 8,
            "vigenteDesde" to fecha,
            "series" to listOf(
                mapOf(
                    "id" to "serie-1",
                    "nombre" to "Gestión documental",
                    "reglaRetencion" to mapOf("tiempoRetencionAnios" to 5, "disposicionFinal" to "Conservación total"),
                    "subseries" to emptyList<Any>(),
                ),
            ),
        )
        restTemplate.postForEntity(url("/trd"), trd, Map::class.java)

        val trdModificada = trd + mapOf("vigenteDesde" to "2026-09-01T00:00:00Z")
        val response = restTemplate.postForEntity(url("/trd"), trdModificada, Map::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        val getResponse = restTemplate.getForEntity(url("/trd/8"), Map::class.java)
        assertEquals(fecha, getResponse.body!!["vigenteDesde"])
    }

    @Test
    fun `GET eventos-auditoria expone los eventos de custodia y de recepcion de sugerencias - P-08, T-48`() {
        custodiarDocumento("doc-http-009")
        val entrada = mapOf(
            "documentoId" to "doc-http-009",
            "tipo" to "clasificacion",
            "contenidoPropuesto" to "serie-1",
            "modeloId" to "emisor-ficticio-clasificacion-v0",
            "evidencia" to listOf("pagina-1"),
            "confianza" to 0.9,
            "fecha" to fecha,
        )
        restTemplate.postForEntity(url("/sugerencias"), entrada, Map::class.java)

        val response = restTemplate.getForEntity(url("/eventos-auditoria"), List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val eventos = (response.body as List<*>).map { it as Map<*, *> }
        assertTrue(eventos.any { it["tipo"] == "ORIGINAL_CUSTODIADO" && it["actor"] == "sistema-ingesta" })
        assertTrue(eventos.any { it["tipo"] == "SUGERENCIA_RECIBIDA" && it["actor"] == "emisor-ficticio-clasificacion-v0" })
    }
}
