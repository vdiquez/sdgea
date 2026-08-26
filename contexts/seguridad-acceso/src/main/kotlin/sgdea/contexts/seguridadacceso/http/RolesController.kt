package sgdea.contexts.seguridadacceso.http

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.seguridadacceso.GestionDeRoles
import sgdea.contexts.seguridadacceso.Rol

@RestController
@RequestMapping("/roles")
class RolesController(private val gestionDeRoles: GestionDeRoles) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@RequestBody request: CrearRolRequest): Rol =
        gestionDeRoles.crear(nombre = request.nombre, permisos = request.permisos)
}
