package sgdea.contexts.recordscustodia.http

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.ReporteVerificacionIntegridad

// specs/spec-infra-servicios.md §4: POST /verificacion-integridad -> RF-RC-009
// (verificarTodos), separado de /documentos/{id}/verificacion-integridad
// porque no cuelga de un id de documento.
@RestController
@RequestMapping("/verificacion-integridad")
class VerificacionIntegridadController(
    private val custodia: CustodiaOriginales,
) {

    @PostMapping
    fun verificarTodos(@RequestBody request: VerificacionRequest): ReporteVerificacionIntegridad =
        custodia.verificarTodos(request.actor, request.fecha)
}
