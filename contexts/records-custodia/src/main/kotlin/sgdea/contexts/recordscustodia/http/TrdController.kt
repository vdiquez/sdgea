package sgdea.contexts.recordscustodia.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.RegistroTrd
import sgdea.contexts.recordscustodia.Trd

// specs/spec-infra-servicios.md §4: POST/GET /trd -> RF-RC-006
// (RegistroTrd.publicar / .version). `Trd` ya es un data class serializable,
// así que se usa directamente como cuerpo de petición y de respuesta, sin
// DTO intermedio, igual que LotesController (T-16) hizo con LoteIngesta.
@RestController
@RequestMapping("/trd")
class TrdController(
    private val registro: RegistroTrd,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publicar(@RequestBody trd: Trd): Trd {
        registro.publicar(trd)
        return trd
    }

    @GetMapping("/{version}")
    fun version(@PathVariable version: Int): Trd = registro.version(version)
}
