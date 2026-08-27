package sgdea.contexts.validacionhumana.http

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.validacionhumana.GestionDeLimites

// specs/007-validacion-humana/spec.md §5 (RF-VH-005). Mismo path que
// Normalización expone (POST /unidades/{id}/confirmacion-limites,
// spec-infra-servicios.md §7) para que ambos contratos sean simétricos —
// este endpoint solo verifica permiso y reenvía, no añade regla de negocio.
@RestController
@RequestMapping("/unidades")
class LimitesController(private val gestion: GestionDeLimites) {

    @PostMapping("/{unidadId}/confirmacion-limites")
    fun confirmar(@PathVariable unidadId: String, @RequestBody request: ConfirmarLimitesRequest): ConfirmacionDeLimitesResponse {
        gestion.confirmar(
            identidadId = request.identidadId,
            unidadId = unidadId,
            actor = request.actor,
            fecha = request.fecha,
        )
        return ConfirmacionDeLimitesResponse(unidadId = unidadId, actor = request.actor, fecha = request.fecha)
    }
}
