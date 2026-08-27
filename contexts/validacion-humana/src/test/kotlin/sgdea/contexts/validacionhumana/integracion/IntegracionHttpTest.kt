package sgdea.contexts.validacionhumana.integracion

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import sgdea.contexts.validacionhumana.ClasificacionPropuesta
import sgdea.contexts.validacionhumana.DecisionDeClasificacion
import sgdea.contexts.validacionhumana.SugerenciaPendiente
import sgdea.contexts.validacionhumana.TipoDeDecision

// Primera integración HTTP real entre servicios del proyecto: estos tests no
// levantan records-custodia ni seguridad-acceso reales, pero sí verifican que
// los adaptadores construyen exactamente la petición que esos servicios ya
// exponen (spec-infra-servicios.md §4/§5) y traducen sus respuestas al tipo de
// dominio de Validación Humana — `MockRestServiceServer` intercepta antes de
// abrir cualquier socket real.
@SpringBootTest
class IntegracionHttpTest {

    @Autowired
    private lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var fuenteDeSugerencias: FuenteDeSugerenciasHttp

    @Autowired
    private lateinit var registradorDeDecisiones: RegistradorDeDecisionesHttp

    @Autowired
    private lateinit var verificadorDePermisos: VerificadorDePermisosHttp

    @Autowired
    private lateinit var confirmadorDeLimites: ConfirmadorDeLimitesHttp

    private val fecha = Instant.parse("2026-08-26T10:00:00Z")

    @Test
    fun `pendientes traduce el JSON de records-custodia a SugerenciaPendiente`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8082/sugerencias/pendientes"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """[{"documentoId":"doc-1","tipo":"clasificacion","contenidoPropuesto":"serie-1","modeloId":"emisor-ficticio-v0","evidencia":["pagina-1"],"confianza":0.42,"fecha":"2026-08-26T10:00:00Z"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val pendientes = fuenteDeSugerencias.pendientes()

        assertEquals(1, pendientes.size)
        assertEquals("doc-1", pendientes[0].documentoId)
        assertEquals(0.42, pendientes[0].confianza)
        servidor.verify()
    }

    @Test
    fun `pendientes lanza ServicioNoDisponibleException si records-custodia falla`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8082/sugerencias/pendientes")).andRespond(withServerError())

        assertFailsWith<ServicioNoDisponibleException> { fuenteDeSugerencias.pendientes() }
    }

    @Test
    fun `materializar envia la decision al endpoint de decisiones del documento correspondiente`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8082/documentos/doc-1/decisiones"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        registradorDeDecisiones.materializar(
            DecisionDeClasificacion(
                documentoId = "doc-1",
                actor = "archivista-1",
                fecha = fecha,
                sugerenciasReferenciadas = listOf(
                    SugerenciaPendiente(
                        documentoId = "doc-1",
                        tipo = "clasificacion",
                        contenidoPropuesto = "serie-1",
                        modeloId = "emisor-ficticio-v0",
                        evidencia = listOf("pagina-1"),
                        confianza = 0.9,
                        fecha = fecha,
                    ),
                ),
                clasificacionResultante = ClasificacionPropuesta(trdVersion = 1, serieId = "serie-1"),
                tipo = TipoDeDecision.ACEPTACION,
            ),
        )

        servidor.verify()
    }

    @Test
    fun `tienePermiso interpreta PERMITIDO y DENEGADO de seguridad-acceso`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8083/autorizacion"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"resultado":"PERMITIDO"}""", MediaType.APPLICATION_JSON))

        assertTrue(verificadorDePermisos.tienePermiso("id-1", "leer", "documento"))
        servidor.verify()

        val servidor2 = MockRestServiceServer.createServer(restTemplate)
        servidor2.expect(requestTo("http://localhost:8083/autorizacion"))
            .andRespond(withSuccess("""{"resultado":"DENEGADO"}""", MediaType.APPLICATION_JSON))

        assertFalse(verificadorDePermisos.tienePermiso("id-1", "leer", "documento"))
        servidor2.verify()
    }

    // RF-VH-005: primer consumidor real de POST /unidades/{id}/confirmacion-limites en normalizacion
    @Test
    fun `confirmar envia actor y fecha al endpoint de confirmacion-limites de normalizacion`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8085/unidades/unidad-1/confirmacion-limites"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        confirmadorDeLimites.confirmar("unidad-1", "archivista-1", fecha)

        servidor.verify()
    }

    @Test
    fun `confirmar lanza ServicioNoDisponibleException si normalizacion falla`() {
        val servidor = MockRestServiceServer.createServer(restTemplate)
        servidor.expect(requestTo("http://localhost:8085/unidades/unidad-1/confirmacion-limites")).andRespond(withServerError())

        assertFailsWith<ServicioNoDisponibleException> { confirmadorDeLimites.confirmar("unidad-1", "archivista-1", fecha) }
    }
}
