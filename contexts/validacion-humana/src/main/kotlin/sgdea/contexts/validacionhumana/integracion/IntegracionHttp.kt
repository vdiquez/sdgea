package sgdea.contexts.validacionhumana.integracion

import java.net.http.HttpClient
import java.time.Duration
import java.util.function.Supplier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import sgdea.contexts.validacionhumana.ConfirmadorDeLimites
import sgdea.contexts.validacionhumana.DecisionDeClasificacion
import sgdea.contexts.validacionhumana.FuenteDeSugerencias
import sgdea.contexts.validacionhumana.FuenteDeSugerenciasDeLimites
import sgdea.contexts.validacionhumana.RegistradorDeDecisiones
import sgdea.contexts.validacionhumana.SugerenciaPendiente
import sgdea.contexts.validacionhumana.TipoDeDecision
import sgdea.contexts.validacionhumana.UnidadPendienteDeLimites
import sgdea.contexts.validacionhumana.VerificadorDePermisos

// Primera integración HTTP real entre servicios de este proyecto: hasta ahora
// cada contexto se probaba de forma aislada (Postman contra uno a la vez).
// Validación Humana no tiene datos propios (specs/007-validacion-humana/spec.md
// §3), así que sus puertos de dominio los implementa un cliente HTTP real
// contra records-custodia, seguridad-acceso y normalizacion — no un adaptador
// ficticio, porque los tres son deterministas/ya están construidos y probados.
@Configuration
class ClienteHttpConfig {
    // Hallazgo real de T-38: el `java.net.http.HttpClient` que Spring Boot 3.5
    // elige por defecto (`JdkClientHttpRequestFactory`, sin Apache
    // HttpComponents/Jetty en el classpath) intenta un upgrade h2c en texto
    // plano si no se fija la versión. Tomcat (records-custodia,
    // seguridad-acceso) lo ignora; uvicorn (normalizacion, primer backend no
    // Java de este proyecto) lo rechaza como petición HTTP inválida (400) —
    // se reprodujo con `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG` contra el
    // stack real. Fijar HTTP/1.1 explícito evita el intento de upgrade.
    // Los timeouts se fijan sobre el propio `HttpClient`/`JdkClientHttpRequestFactory`
    // en vez de con `RestTemplateBuilder.connectTimeout/readTimeout`: esos dos
    // métodos configuran el factory por reflexión contra una lista fija de
    // fábricas conocidas y `JdkClientHttpRequestFactory` no calza ahí
    // ("does not have a suitable setConnectTimeout method").
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        val httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(Duration.ofSeconds(5))
        return builder.requestFactory(Supplier { requestFactory }).build()
    }
}

class ServicioNoDisponibleException(mensaje: String, causa: Throwable) : RuntimeException(mensaje, causa)

@Component
class FuenteDeSugerenciasHttp(
    private val restTemplate: RestTemplate,
    @Value("\${records-custodia.base-url}") private val baseUrl: String,
) : FuenteDeSugerencias {

    override fun pendientes(): List<SugerenciaPendiente> =
        try {
            restTemplate.getForObject("$baseUrl/sugerencias/pendientes", Array<SugerenciaPendiente>::class.java)
                ?.toList() ?: emptyList()
        } catch (ex: Exception) {
            throw ServicioNoDisponibleException("records-custodia no respondió al listar sugerencias pendientes.", ex)
        }
}

@Component
class RegistradorDeDecisionesHttp(
    private val restTemplate: RestTemplate,
    @Value("\${records-custodia.base-url}") private val baseUrl: String,
) : RegistradorDeDecisiones {

    override fun materializar(decision: DecisionDeClasificacion) {
        val cuerpo = DecisionRequestDto(
            actor = decision.actor,
            fecha = decision.fecha,
            sugerenciasReferenciadas = decision.sugerenciasReferenciadas,
            clasificacionResultante = ClasificacionResultanteDto(
                documentoId = decision.documentoId,
                trdVersion = decision.clasificacionResultante.trdVersion,
                serieId = decision.clasificacionResultante.serieId,
                subserieId = decision.clasificacionResultante.subserieId,
            ),
            esCorreccion = decision.tipo == TipoDeDecision.CORRECCION,
        )
        try {
            restTemplate.postForEntity("$baseUrl/documentos/${decision.documentoId}/decisiones", cuerpo, Map::class.java)
        } catch (ex: Exception) {
            throw ServicioNoDisponibleException("records-custodia no respondió al materializar la decisión.", ex)
        }
    }
}

@Component
class VerificadorDePermisosHttp(
    private val restTemplate: RestTemplate,
    @Value("\${seguridad-acceso.base-url}") private val baseUrl: String,
) : VerificadorDePermisos {

    override fun tienePermiso(identidadId: String, accion: String, tipoRecurso: String): Boolean {
        val respuesta = try {
            restTemplate.postForObject(
                "$baseUrl/autorizacion",
                AutorizarRequestDto(identidadId = identidadId, accion = accion, tipoRecurso = tipoRecurso, fecha = java.time.Instant.now()),
                AutorizarResponseDto::class.java,
            )
        } catch (ex: Exception) {
            throw ServicioNoDisponibleException("seguridad-acceso no respondió al verificar el permiso.", ex)
        }
        return respuesta?.resultado == "PERMITIDO"
    }
}

// RF-VH-005: cierra el ciclo que spec-infra-servicios.md §9 dejó abierto —
// Normalización ya expone POST /unidades/{id}/confirmacion-limites desde
// T-33/T-34; este es el primer consumidor real de ese endpoint.
@Component
class ConfirmadorDeLimitesHttp(
    private val restTemplate: RestTemplate,
    @Value("\${normalizacion.base-url}") private val baseUrl: String,
) : ConfirmadorDeLimites {

    override fun confirmar(unidadId: String, actor: String, fecha: java.time.Instant) {
        try {
            restTemplate.postForEntity(
                "$baseUrl/unidades/$unidadId/confirmacion-limites",
                ConfirmacionDeLimitesRequestDto(actor = actor, fecha = fecha),
                Map::class.java,
            )
        } catch (ex: Exception) {
            throw ServicioNoDisponibleException("normalizacion no respondió al confirmar los límites.", ex)
        }
    }
}

// RF-VH-001 (T-39): primer consumidor real de GET /unidades/pendientes-de-limites
// en normalizacion — mismo criterio que FuenteDeSugerenciasHttp, pero contra un
// backend Python/FastAPI que serializa en snake_case (integracion/Dtos.kt).
@Component
class FuenteDeSugerenciasDeLimitesHttp(
    private val restTemplate: RestTemplate,
    @Value("\${normalizacion.base-url}") private val baseUrl: String,
) : FuenteDeSugerenciasDeLimites {

    override fun pendientes(): List<UnidadPendienteDeLimites> =
        try {
            restTemplate.getForObject("$baseUrl/unidades/pendientes-de-limites", Array<UnidadPendienteDeLimitesDto>::class.java)
                ?.map {
                    UnidadPendienteDeLimites(
                        unidadId = it.id,
                        loteId = it.loteId,
                        modeloId = it.sugerenciaDeLimites.modeloId,
                        evidencia = it.sugerenciaDeLimites.evidencia,
                        confianza = it.sugerenciaDeLimites.confianza,
                        fecha = it.sugerenciaDeLimites.fecha,
                    )
                } ?: emptyList()
        } catch (ex: Exception) {
            throw ServicioNoDisponibleException("normalizacion no respondió al listar unidades pendientes de límites.", ex)
        }
}
