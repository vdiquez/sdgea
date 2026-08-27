package sgdea.contexts.validacionhumana.http

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.validacionhumana.DecisionDeClasificacion
import sgdea.contexts.validacionhumana.GestionDeDecisiones

// specs/007-validacion-humana/spec.md §5. RF-VH-003/004/006/007/008: cada
// endpoint traduce uno a uno un método de dominio ya probado por TDD (T-29);
// esta clase no añade regla de negocio alguna.
@RestController
@RequestMapping("/decisiones")
class DecisionesController(private val gestion: GestionDeDecisiones) {

    @PostMapping
    fun decidir(@RequestBody request: DecidirRequest): DecisionDeClasificacion =
        gestion.decidir(
            identidadId = request.identidadId,
            sugerencia = request.sugerencia,
            clasificacionResultante = request.clasificacionResultante,
            actor = request.actor,
            fecha = request.fecha,
        )

    @PostMapping("/masivo")
    fun aprobarEnBloque(@RequestBody request: AprobarEnBloqueRequest): List<DecisionDeClasificacion> =
        gestion.aprobarEnBloque(
            identidadId = request.identidadId,
            candidatas = request.candidatas,
            resolver = { sugerencia ->
                request.resoluciones[sugerencia.documentoId]
                    ?: throw NoSuchElementException("Sin resolución para el documento '${sugerencia.documentoId}' en la aprobación en bloque.")
            },
            actor = request.actor,
            fecha = request.fecha,
        )
}
