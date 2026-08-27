package sgdea.contexts.validacionhumana.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import sgdea.contexts.validacionhumana.AccesoDenegadoException
import sgdea.contexts.validacionhumana.integracion.ServicioNoDisponibleException

// specs/spec-infra-servicios.md §6: formato de error queda [CLARIFICAR] ("no
// bloqueante"); mismo tratamiento canónico que los demás contextos (T-19):
// NoSuchElementException -> 404. `ServicioNoDisponibleException` es nueva en
// este proyecto: es la primera vez que un servicio depende en el camino
// crítico de otro servicio HTTP (records-custodia/seguridad-acceso) — 502
// porque el fallo es de un servicio aguas abajo, no de esta petición.
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noEncontrado(ex: NoSuchElementException): Map<String, String?> = mapOf("error" to ex.message)

    @ExceptionHandler(AccesoDenegadoException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun accesoDenegado(ex: AccesoDenegadoException): Map<String, String?> = mapOf("error" to ex.message)

    @ExceptionHandler(ServicioNoDisponibleException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun servicioNoDisponible(ex: ServicioNoDisponibleException): Map<String, String?> = mapOf("error" to ex.message)
}
