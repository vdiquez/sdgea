package sgdea.contexts.recordscustodia.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import sgdea.contexts.recordscustodia.AccesoDenegadoException
import sgdea.contexts.recordscustodia.PublicacionDeTrdRechazadaException

// specs/spec-infra-servicios.md §5: formato de error queda [CLARIFICAR]
// ("no bloqueante"); este manejador solo traduce "id no encontrado" del
// dominio (NoSuchElementException, lanzada por CustodiaOriginales/RegistroTrd
// cuando se consulta un id inexistente) a 404, sin fijar RFC 7807.
// T-19 (corrige VETO de Codex): esta es la convención canónica, replicada
// tal cual en sgdea.contexts.capturaingesta.http.ManejoDeErrores — antes
// captura-ingesta usaba ResponseStatusException con un cuerpo distinto.
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noEncontrado(ex: NoSuchElementException): Map<String, String?> = mapOf("error" to ex.message)

    // T-19: sin este manejador, el rechazo de RegistroTrd.publicar (RF-RC-006)
    // se traducía en un 500 sin manejar en vez de una respuesta HTTP con
    // sentido; 409 porque el conflicto es con el estado ya publicado, no con
    // la sintaxis de la petición.
    @ExceptionHandler(PublicacionDeTrdRechazadaException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun publicacionRechazada(ex: PublicacionDeTrdRechazadaException): Map<String, String?> = mapOf("error" to ex.message)

    // T-63 / specs/008-ui-demo/spec.md §1: mismo tratamiento canónico que
    // AccesoDenegadoException en validacion-humana (T-30, http/ManejoDeErrores.kt).
    @ExceptionHandler(AccesoDenegadoException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun accesoDenegado(ex: AccesoDenegadoException): Map<String, String?> = mapOf("error" to ex.message)
}
