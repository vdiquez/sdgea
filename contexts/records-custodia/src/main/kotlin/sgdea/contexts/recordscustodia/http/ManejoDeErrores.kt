package sgdea.contexts.recordscustodia.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

// specs/spec-infra-servicios.md §5: formato de error queda [CLARIFICAR]
// ("no bloqueante"); este manejador solo traduce "id no encontrado" del
// dominio (NoSuchElementException, lanzada por CustodiaOriginales/RegistroTrd
// cuando se consulta un id inexistente) a 404, sin fijar RFC 7807.
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noEncontrado(ex: NoSuchElementException): Map<String, String?> = mapOf("error" to ex.message)
}
