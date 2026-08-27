package sgdea.contexts.validacionhumana.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.validacionhumana.AccesoDenegadoException
import sgdea.contexts.validacionhumana.ColaDeLimites
import sgdea.contexts.validacionhumana.UnidadPendienteDeLimites
import sgdea.contexts.validacionhumana.VerificadorDePermisos

// specs/007-validacion-humana/spec.md §4/§5 (T-39). RF-VH-001: cola separada
// de /colas/clasificacion — revisar una sugerencia de límites no produce una
// decisión de clasificación, produce una confirmación
// (POST /unidades/{unidadId}/confirmacion-limites, T-38). Sin ruta /masivo:
// el `[CLARIFICAR]` de la spec (§8) sobre aprobación masiva para sugerencias
// distintas de clasificación sigue abierto.
@RestController
@RequestMapping("/colas/limites")
class ColasDeLimitesController(
    private val cola: ColaDeLimites,
    private val permisos: VerificadorDePermisos,
) {

    @GetMapping
    fun listar(@RequestParam identidadId: String): List<UnidadPendienteDeLimites> {
        exigirPermisoDeLectura(identidadId)
        return cola.ordenadasPorConfianza()
    }

    @GetMapping("/estado")
    fun estado(): EstadoDeColaResponse {
        val (volumen, masAntigua) = cola.volumenYAntiguedadDeLaCola()
        return EstadoDeColaResponse(volumen, masAntigua)
    }

    private fun exigirPermisoDeLectura(identidadId: String) {
        if (!permisos.tienePermiso(identidadId, "leer", "documento")) {
            throw AccesoDenegadoException("La identidad '$identidadId' no tiene permiso para ver esta cola.")
        }
    }
}
