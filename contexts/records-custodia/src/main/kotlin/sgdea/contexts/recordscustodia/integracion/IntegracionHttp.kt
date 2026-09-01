package sgdea.contexts.recordscustodia.integracion

import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import sgdea.contexts.recordscustodia.VerificadorDeAutorizacion

// T-63 (specs/008-ui-demo/spec.md §1, prerrequisito de arquitectura): primera
// integración saliente real de records-custodia -- hasta ahora este contexto
// solo era LLAMADO por otros (validacion-humana, clasificacion,
// enriquecimiento), nunca llamaba él mismo a otro servicio. Mismo contrato
// que VerificadorDePermisosHttp en validacion-humana (T-30) y
// VerificadorDeAutorizacionHttp en extraccion (T-41b): consulta
// `POST /autorizacion` de seguridad-acceso antes de responder.
//
// A diferencia de validacion-humana (que también llama a normalizacion, un
// backend uvicorn/FastAPI que rechaza el intento de upgrade h2c del
// `JdkClientHttpRequestFactory` por defecto de Spring Boot 3.5), el único
// destino de records-custodia es seguridad-acceso, que corre sobre Tomcat y
// lo ignora sin fallar (ver comentario de ClienteHttpConfig en
// validacion-humana/integracion/IntegracionHttp.kt) -- por eso aquí basta el
// `RestTemplate` que arma `RestTemplateBuilder` por defecto, sin fijar
// HTTP/1.1 explícito.
@Configuration
class ClienteHttpConfig {
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()
}

data class AutorizarRequestDto(
    val identidadId: String,
    val accion: String,
    val tipoRecurso: String,
    val nivelClasificacion: String = "PUBLICA",
    val fecha: Instant,
)

data class AutorizarResponseDto(
    val resultado: String,
)

@Component
class VerificadorDeAutorizacionHttp(
    private val restTemplate: RestTemplate,
    @Value("\${seguridad-acceso.base-url}") private val baseUrl: String,
) : VerificadorDeAutorizacion {

    override fun tienePermiso(actor: String, accion: String, tipoRecurso: String): Boolean {
        val respuesta = restTemplate.postForObject(
            "$baseUrl/autorizacion",
            AutorizarRequestDto(identidadId = actor, accion = accion, tipoRecurso = tipoRecurso, fecha = Instant.now()),
            AutorizarResponseDto::class.java,
        )
        return respuesta?.resultado == "PERMITIDO"
    }
}
