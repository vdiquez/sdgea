package sgdea.contexts.seguridadacceso.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import sgdea.contexts.seguridadacceso.CredencialesInvalidasException
import sgdea.contexts.seguridadacceso.IdentidadSuspendidaException

// specs/spec-infra-servicios.md §6: formato de error queda [CLARIFICAR] ("no
// bloqueante"); mismo tratamiento canónico que records-custodia/captura-ingesta
// (T-19): NoSuchElementException -> 404. Añade dos manejadores propios de este
// contexto para las excepciones de RF-SA-001/RF-SA-006.
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noEncontrado(ex: NoSuchElementException): Map<String, String?> = mapOf("error" to ex.message)

    @ExceptionHandler(CredencialesInvalidasException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun credencialesInvalidas(ex: CredencialesInvalidasException): Map<String, String?> = mapOf("error" to ex.message)

    @ExceptionHandler(IdentidadSuspendidaException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun identidadSuspendida(ex: IdentidadSuspendidaException): Map<String, String?> = mapOf("error" to ex.message)
}
