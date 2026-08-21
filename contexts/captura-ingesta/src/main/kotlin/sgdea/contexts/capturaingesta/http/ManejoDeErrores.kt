package sgdea.contexts.capturaingesta.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

// specs/spec-infra-servicios.md §5: convención de error única para los dos
// servicios (T-19, corrige VETO de Codex — antes captura-ingesta devolvía el
// cuerpo por defecto de ResponseStatusException y records-custodia
// {"error": ...}, dos formatos distintos pese a que la spec exige uno solo).
// Idéntica en forma a sgdea.contexts.recordscustodia.http.ManejoDeErrores:
// NoSuchElementException (id no encontrado) -> 404 con {"error": mensaje}.
// Sigue sin fijar RFC 7807 (§5/§7 lo deja [CLARIFICAR], no bloqueante).
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noEncontrado(ex: NoSuchElementException): Map<String, String?> = mapOf("error" to ex.message)
}
