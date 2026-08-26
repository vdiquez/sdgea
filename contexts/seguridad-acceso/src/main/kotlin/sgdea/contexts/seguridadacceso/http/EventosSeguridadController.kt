package sgdea.contexts.seguridadacceso.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.seguridadacceso.EventoSeguridad
import sgdea.contexts.seguridadacceso.GestionDeAccesos

// RF-SA-005/RF-SA-010: observabilidad de la bitácora de seguridad.
@RestController
@RequestMapping("/eventos-seguridad")
class EventosSeguridadController(private val gestion: GestionDeAccesos) {

    @GetMapping
    fun listar(): List<EventoSeguridad> = gestion.eventosDeSeguridad
}
