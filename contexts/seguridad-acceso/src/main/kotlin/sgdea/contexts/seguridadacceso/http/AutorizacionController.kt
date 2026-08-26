package sgdea.contexts.seguridadacceso.http

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.seguridadacceso.GestionDeAccesos

// RF-SA-008: expone la decisión de autorización a cualquier otro contexto para
// que aplique su propio filtrado (ver spec-infra-servicios.md §7, RF-IB-008) —
// la integración real de captura-ingesta/records-custodia con este endpoint
// sigue sin implementarse (spec-infra-servicios.md §8).
@RestController
@RequestMapping("/autorizacion")
class AutorizacionController(private val gestion: GestionDeAccesos) {

    @PostMapping
    fun autorizar(@RequestBody request: AutorizarRequest): AutorizarResponse =
        AutorizarResponse(
            resultado = gestion.autorizar(
                identidadId = request.identidadId,
                accion = request.accion,
                tipoRecurso = request.tipoRecurso,
                nivelClasificacion = request.nivelClasificacion,
                recurso = request.recurso,
                fecha = request.fecha,
            ),
        )
}
