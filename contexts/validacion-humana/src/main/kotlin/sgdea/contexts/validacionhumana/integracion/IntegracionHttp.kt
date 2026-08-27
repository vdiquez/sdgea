package sgdea.contexts.validacionhumana.integracion

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import sgdea.contexts.validacionhumana.DecisionDeClasificacion
import sgdea.contexts.validacionhumana.FuenteDeSugerencias
import sgdea.contexts.validacionhumana.RegistradorDeDecisiones
import sgdea.contexts.validacionhumana.SugerenciaPendiente
import sgdea.contexts.validacionhumana.VerificadorDePermisos

// Primera integración HTTP real entre servicios de este proyecto: hasta ahora
// cada contexto se probaba de forma aislada (Postman contra uno a la vez).
// Validación Humana no tiene datos propios (specs/007-validacion-humana/spec.md
// §3), así que sus puertos de dominio los implementa un cliente HTTP real
// contra records-custodia y seguridad-acceso — no un adaptador ficticio, porque
// ambos servicios son deterministas y ya están construidos y probados.
@Configuration
class ClienteHttpConfig {
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .build()
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
