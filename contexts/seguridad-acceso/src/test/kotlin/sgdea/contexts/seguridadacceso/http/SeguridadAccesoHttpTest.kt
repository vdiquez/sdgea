package sgdea.contexts.seguridadacceso.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

// specs/spec-infra-servicios.md §5 · Contrato mínimo — seguridad-acceso. Cada
// endpoint traduce un método de dominio ya probado por TDD (T-23); estas
// pruebas verifican la traducción HTTP y la persistencia entre peticiones
// separadas, no reglas de negocio nuevas.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeguridadAccesoHttpTest {

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = TestRestTemplate()

    private fun url(path: String) = "http://localhost:$port$path"

    private val fecha = "2026-08-25T10:00:00Z"

    private fun crearRol(nombre: String, accion: String = "leer", tipoRecurso: String = "documento", nivel: String = "RESERVADA"): Map<*, *> {
        val request = mapOf(
            "nombre" to nombre,
            "permisos" to listOf(mapOf("accion" to accion, "tipoRecurso" to tipoRecurso, "nivelClasificacionMaximo" to nivel)),
        )
        val response = restTemplate.postForEntity(url("/roles"), request, Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!
    }

    private fun crearIdentidad(id: String, actor: String, credencial: String = "clave-$id", roles: List<String> = emptyList()): Map<*, *> {
        val request = mapOf("id" to id, "actor" to actor, "credencial" to credencial, "roles" to roles)
        val response = restTemplate.postForEntity(url("/identidades"), request, Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!
    }

    @Test
    fun `POST identidades crea una identidad y su credencial no viaja en texto plano en la respuesta - RF-SA-001, RF-SA-007`() {
        val body = crearIdentidad("id-http-001", "actor-http-001", credencial = "mi-clave-secreta")

        assertEquals("id-http-001", body["id"])
        assertTrue(body["credencialHash"] != "mi-clave-secreta")
    }

    @Test
    fun `POST identidades autenticacion autentica con credenciales correctas - RF-SA-001`() {
        crearIdentidad("id-http-002", "actor-http-002", credencial = "clave-correcta")

        val response = restTemplate.postForEntity(
            url("/identidades/autenticacion"),
            mapOf("actor" to "actor-http-002", "credencial" to "clave-correcta", "fecha" to fecha),
            Map::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("actor-http-002", response.body!!["actor"])
    }

    @Test
    fun `POST identidades autenticacion con credenciales incorrectas responde 401 - RF-SA-001`() {
        crearIdentidad("id-http-003", "actor-http-003", credencial = "clave-correcta")

        val response = restTemplate.postForEntity(
            url("/identidades/autenticacion"),
            mapOf("actor" to "actor-http-003", "credencial" to "clave-incorrecta", "fecha" to fecha),
            Map::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST autorizacion permite cuando la identidad tiene un rol que cubre la accion, y persiste entre peticiones - RF-SA-003, RF-SA-004`() {
        crearRol("rol-http-001")
        crearIdentidad("id-http-004", "actor-http-004", roles = listOf("rol-http-001"))

        val response = restTemplate.postForEntity(
            url("/autorizacion"),
            mapOf(
                "identidadId" to "id-http-004",
                "accion" to "leer",
                "tipoRecurso" to "documento",
                "nivelClasificacion" to "RESERVADA",
                "fecha" to fecha,
            ),
            Map::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("PERMITIDO", response.body!!["resultado"])
    }

    @Test
    fun `POST autorizacion deniega por defecto cuando la identidad no tiene ningun rol - RF-SA-003`() {
        crearIdentidad("id-http-005", "actor-http-005")

        val response = restTemplate.postForEntity(
            url("/autorizacion"),
            mapOf("identidadId" to "id-http-005", "accion" to "leer", "tipoRecurso" to "documento", "fecha" to fecha),
            Map::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("DENEGADO", response.body!!["resultado"])
    }

    @Test
    fun `DELETE identidades roles revoca de inmediato el acceso que dependia de ese rol - RF-SA-006`() {
        crearRol("rol-http-002")
        crearIdentidad("id-http-006", "actor-http-006", roles = listOf("rol-http-002"))
        val autorizarRequest = mapOf(
            "identidadId" to "id-http-006",
            "accion" to "leer",
            "tipoRecurso" to "documento",
            "nivelClasificacion" to "RESERVADA",
            "fecha" to fecha,
        )
        val antes = restTemplate.postForEntity(url("/autorizacion"), autorizarRequest, Map::class.java)
        assertEquals("PERMITIDO", antes.body!!["resultado"])

        restTemplate.delete(url("/identidades/id-http-006/roles/rol-http-002"))

        val despues = restTemplate.postForEntity(url("/autorizacion"), autorizarRequest, Map::class.java)
        assertEquals("DENEGADO", despues.body!!["resultado"])
    }

    @Test
    fun `GET eventos-seguridad refleja los intentos de autenticacion y las decisiones de autorizacion - RF-SA-005, RF-SA-010`() {
        crearIdentidad("id-http-007", "actor-http-007", credencial = "clave-007")
        restTemplate.postForEntity(
            url("/identidades/autenticacion"),
            mapOf("actor" to "actor-http-007", "credencial" to "clave-007", "fecha" to fecha),
            Map::class.java,
        )
        restTemplate.postForEntity(
            url("/autorizacion"),
            mapOf("identidadId" to "id-http-007", "accion" to "leer", "tipoRecurso" to "documento", "fecha" to fecha),
            Map::class.java,
        )

        val response = restTemplate.getForEntity(url("/eventos-seguridad"), List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val eventos = response.body!!
        assertTrue(eventos.any { (it as Map<*, *>)["tipo"] == "AUTENTICACION_EXITOSA" && it["actor"] == "actor-http-007" })
        assertTrue(eventos.any { (it as Map<*, *>)["tipo"] == "AUTORIZACION_DENEGADA" && it["actor"] == "actor-http-007" })
    }

    @Test
    fun `POST identidades autenticacion de un actor inexistente responde 401 con el formato de error unificado - specs-infra-servicios §6`() {
        val response = restTemplate.postForEntity(
            url("/identidades/autenticacion"),
            mapOf("actor" to "no-existe", "credencial" to "cualquiera", "fecha" to fecha),
            Map::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue((response.body!!["error"] as String).contains("no-existe"))
    }
}
