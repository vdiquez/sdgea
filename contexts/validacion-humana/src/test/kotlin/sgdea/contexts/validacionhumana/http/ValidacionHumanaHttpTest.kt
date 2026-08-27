package sgdea.contexts.validacionhumana.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

// specs/007-validacion-humana/spec.md §4/§5. Estos tests levantan el servicio
// real de validacion-humana (RANDOM_PORT) pero interceptan sus llamadas de
// salida a records-custodia/seguridad-acceso con `MockRestServiceServer` — no
// hay stack multi-servicio real aquí (eso lo cubre Postman/Newman contra
// Docker, T-32); esto verifica que ESTE servicio traduce bien su propio
// contrato HTTP hacia y desde los otros dos.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ValidacionHumanaHttpTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplateAguasAbajo: RestTemplate

    private val restTemplate = TestRestTemplate()

    private fun url(path: String) = "http://localhost:$port$path"

    private val fecha = "2026-08-26T10:00:00Z"

    private fun mockearAutorizacion(resultado: String, servidor: MockRestServiceServer) {
        servidor.expect(requestTo("http://localhost:8083/autorizacion"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"resultado":"$resultado"}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `GET colas clasificacion deniega cuando seguridad-acceso responde DENEGADO - RF-VH-007`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("DENEGADO", servidor)

        val response = restTemplate.getForEntity(url("/colas/clasificacion?identidadId=id-1"), Map::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        servidor.verify()
    }

    @Test
    fun `GET colas clasificacion devuelve la cola ordenada por confianza cuando el permiso se concede - RF-VH-001, RF-VH-002`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("PERMITIDO", servidor)
        servidor.expect(requestTo("http://localhost:8082/sugerencias/pendientes"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """[
                        {"documentoId":"doc-alta","tipo":"clasificacion","contenidoPropuesto":"serie-1","modeloId":"m","evidencia":[],"confianza":0.9,"fecha":"$fecha"},
                        {"documentoId":"doc-baja","tipo":"clasificacion","contenidoPropuesto":"serie-1","modeloId":"m","evidencia":[],"confianza":0.1,"fecha":"$fecha"}
                    ]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = restTemplate.getForEntity(url("/colas/clasificacion?identidadId=id-1"), List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val cola = response.body as List<*>
        assertEquals(listOf("doc-baja", "doc-alta"), cola.map { (it as Map<*, *>)["documentoId"] })
        servidor.verify()
    }

    @Test
    fun `POST decisiones acepta la sugerencia y responde con la decision materializada - RF-VH-003, RF-VH-006, RF-VH-008`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("PERMITIDO", servidor)
        servidor.expect(requestTo("http://localhost:8082/documentos/doc-1/decisiones"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        val body = mapOf(
            "identidadId" to "id-1",
            "sugerencia" to mapOf(
                "documentoId" to "doc-1",
                "tipo" to "clasificacion",
                "contenidoPropuesto" to "serie-1",
                "modeloId" to "emisor-ficticio-v0",
                "evidencia" to listOf("pagina-1"),
                "confianza" to 0.9,
                "fecha" to fecha,
            ),
            "clasificacionResultante" to mapOf("trdVersion" to 1, "serieId" to "serie-1"),
            "actor" to "archivista-1",
            "fecha" to fecha,
        )

        val response = restTemplate.postForEntity(url("/decisiones"), body, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ACEPTACION", response.body!!["tipo"])
        assertEquals("doc-1", response.body!!["documentoId"])
        servidor.verify()
    }

    @Test
    fun `POST decisiones masivo produce una decision por cada candidata referenciada explicitamente - RF-VH-004`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("PERMITIDO", servidor)
        servidor.expect(requestTo("http://localhost:8082/documentos/doc-1/decisiones")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        servidor.expect(requestTo("http://localhost:8082/documentos/doc-2/decisiones")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        fun sugerencia(documentoId: String) = mapOf(
            "documentoId" to documentoId,
            "tipo" to "clasificacion",
            "contenidoPropuesto" to "serie-1",
            "modeloId" to "m",
            "evidencia" to emptyList<String>(),
            "confianza" to 0.95,
            "fecha" to fecha,
        )
        val body = mapOf(
            "identidadId" to "id-1",
            "candidatas" to listOf(sugerencia("doc-1"), sugerencia("doc-2")),
            "resoluciones" to mapOf(
                "doc-1" to mapOf("trdVersion" to 1, "serieId" to "serie-1"),
                "doc-2" to mapOf("trdVersion" to 1, "serieId" to "serie-1"),
            ),
            "actor" to "archivista-1",
            "fecha" to fecha,
        )

        val response = restTemplate.postForEntity(url("/decisiones/masivo"), body, List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val decisiones = response.body as List<*>
        assertEquals(2, decisiones.size)
        assertTrue(decisiones.all { (it as Map<*, *>)["actor"] == "archivista-1" })
        servidor.verify()
    }

    @Test
    fun `GET colas clasificacion estado no requiere identidad y expone volumen - RF-VH-010`() {
        // Sin mockear /autorizacion a propósito: /estado no expone contenido de
        // ninguna sugerencia, así que no exige permiso (ver ColasController).
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        servidor.expect(requestTo("http://localhost:8082/sugerencias/pendientes"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val response = restTemplate.getForEntity(url("/colas/clasificacion/estado"), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body!!["volumen"])
        servidor.verify()
    }

    @Test
    fun `POST unidades confirmacion-limites llama a Normalizacion con actor y fecha cuando el permiso se concede - RF-VH-005`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("PERMITIDO", servidor)
        servidor.expect(requestTo("http://localhost:8085/unidades/unidad-1/confirmacion-limites"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        val body = mapOf("identidadId" to "id-1", "actor" to "archivista-1", "fecha" to fecha)
        val response = restTemplate.postForEntity(url("/unidades/unidad-1/confirmacion-limites"), body, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("unidad-1", response.body!!["unidadId"])
        assertEquals("archivista-1", response.body!!["actor"])
        servidor.verify()
    }

    @Test
    fun `POST unidades confirmacion-limites deniega cuando seguridad-acceso responde DENEGADO - RF-VH-007`() {
        val servidor = MockRestServiceServer.createServer(restTemplateAguasAbajo)
        mockearAutorizacion("DENEGADO", servidor)

        val body = mapOf("identidadId" to "id-1", "actor" to "archivista-1", "fecha" to fecha)
        val response = restTemplate.postForEntity(url("/unidades/unidad-1/confirmacion-limites"), body, Map::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        servidor.verify()
    }
}
