package sgdea.contexts.seguridadacceso.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.seguridadacceso.GestionDeAccesos
import sgdea.contexts.seguridadacceso.GestionDeRoles
import sgdea.contexts.seguridadacceso.Identidad

// specs/spec-infra-servicios.md §5 · Contrato mínimo — seguridad-acceso. Cada
// endpoint traduce uno a uno un método de dominio ya implementado y probado por
// TDD (T-23); esta clase no añade regla de negocio alguna, solo entrada/salida
// HTTP.
@RestController
@RequestMapping("/identidades")
class IdentidadesController(
    private val gestion: GestionDeAccesos,
    private val gestionDeRoles: GestionDeRoles,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@RequestBody request: CrearIdentidadRequest): Identidad =
        gestion.crearIdentidad(
            id = request.id,
            actor = request.actor,
            credencial = request.credencial,
            roles = request.roles.map { gestionDeRoles.buscar(it) },
        )

    @PostMapping("/autenticacion")
    fun autenticar(@RequestBody request: AutenticarRequest): Identidad =
        gestion.autenticar(actor = request.actor, credencial = request.credencial, fecha = request.fecha)

    @PostMapping("/{id}/roles")
    fun asignarRol(@PathVariable id: String, @RequestBody request: AsignarRolRequest): Identidad =
        gestion.asignarRol(identidadId = id, rol = gestionDeRoles.buscar(request.rol))

    @DeleteMapping("/{id}/roles/{rol}")
    fun revocarRol(@PathVariable id: String, @PathVariable rol: String): Identidad =
        gestion.revocarRol(identidadId = id, nombreRol = rol)
}
