package sgdea.contexts.validacionhumana.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.validacionhumana.AccesoDenegadoException
import sgdea.contexts.validacionhumana.ColaDeRevision
import sgdea.contexts.validacionhumana.SugerenciaPendiente
import sgdea.contexts.validacionhumana.VerificadorDePermisos

// specs/007-validacion-humana/spec.md §4/§5. RF-VH-007: antes de mostrar una
// sugerencia se verifica el permiso del actor — por eso `identidadId` es
// obligatorio incluso para las rutas de solo lectura, salvo `/estado`, que no
// expone contenido de ninguna sugerencia (solo volumen y antigüedad,
// RF-VH-010).
@RestController
@RequestMapping("/colas/clasificacion")
class ColasController(
    private val cola: ColaDeRevision,
    private val permisos: VerificadorDePermisos,
) {

    @GetMapping
    fun listar(@RequestParam identidadId: String): List<SugerenciaPendiente> {
        exigirPermisoDeLectura(identidadId)
        return cola.ordenadasPorConfianza()
    }

    @GetMapping("/masivo")
    fun candidatas(@RequestParam identidadId: String, @RequestParam umbral: Double): List<SugerenciaPendiente> {
        exigirPermisoDeLectura(identidadId)
        return cola.candidatasAAprobacionMasiva(umbral)
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
