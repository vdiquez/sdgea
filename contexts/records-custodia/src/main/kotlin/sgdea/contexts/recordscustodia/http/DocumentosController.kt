package sgdea.contexts.recordscustodia.http

import java.util.Base64
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.CapaAnticorrupcionSugerencias
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.DecisionHumana
import sgdea.contexts.recordscustodia.DocumentoDeArchivo
import sgdea.contexts.recordscustodia.OriginalInmutable
import sgdea.contexts.recordscustodia.Procedencia
import sgdea.contexts.recordscustodia.ResultadoVerificacionIntegridad
import sgdea.contexts.recordscustodia.Sugerencia
import sgdea.contexts.recordscustodia.configuracion.CustodiaTransaccional

// specs/spec-infra-servicios.md §4 · Contrato mínimo — records-custodia.
// Cada endpoint traduce uno a uno un método de dominio ya implementado y
// probado por TDD (T-03, T-08, T-09, T-11); esta clase no añade regla de
// negocio alguna, solo entrada/salida HTTP. `custodiar` y `materializar` pasan
// por `CustodiaTransaccional` (no por `custodia` directo) porque cada uno hace
// más de una escritura a través de almacenes JPA independientes — ver
// CustodiaTransaccional para el riesgo de atomicidad que evita.
@RestController
@RequestMapping("/documentos")
class DocumentosController(
    private val custodia: CustodiaOriginales,
    private val custodiaTransaccional: CustodiaTransaccional,
    private val capa: CapaAnticorrupcionSugerencias,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun custodiar(@RequestBody request: CustodiarRequest): OriginalInmutable =
        custodiaTransaccional.custodiar(
            id = request.id,
            bytes = Base64.getDecoder().decode(request.bytesBase64),
            actor = request.actor,
            fecha = request.fecha,
            procedencia = request.procedencia,
        )

    @GetMapping("/{id}/original")
    fun original(@PathVariable id: String): OriginalInmutable = custodia.consultar(id)

    @GetMapping("/{id}")
    fun documento(@PathVariable id: String): DocumentoDeArchivo = custodia.consultarDocumento(id)

    @GetMapping("/{id}/procedencia")
    fun procedencia(@PathVariable id: String): Procedencia = custodia.consultarProcedencia(id)

    @PostMapping("/{id}/decisiones")
    fun materializar(@PathVariable id: String, @RequestBody request: DecisionRequest): DocumentoDeArchivo =
        custodiaTransaccional.materializar(
            DecisionHumana(
                documentoId = id,
                actor = request.actor,
                fecha = request.fecha,
                sugerenciasReferenciadas = request.sugerenciasReferenciadas,
                clasificacionResultante = request.clasificacionResultante,
                esCorreccion = request.esCorreccion,
            ),
        )

    // RF-VH-009 (T-39). A diferencia de FastAPI/Starlette (normalizacion,
    // GET /unidades/pendientes-de-limites), Spring MVC no resuelve rutas por
    // orden de declaración: elige el patrón más específico para cada
    // petición, así que "/correcciones" nunca choca con "/{id}" sin importar
    // el orden en que aparezcan aquí.
    @GetMapping("/correcciones")
    fun correcciones(): List<CorreccionPendienteDeRerevisionResponse> =
        custodia.correccionesPendientesDeRerevision().map {
            CorreccionPendienteDeRerevisionResponse(
                actor = it.actor,
                fecha = it.fecha,
                estadoAnterior = it.estadoAnterior,
                estadoPosterior = it.estadoPosterior,
            )
        }

    @PostMapping("/{id}/verificacion-integridad")
    fun verificarIntegridad(@PathVariable id: String, @RequestBody request: VerificacionRequest): ResultadoVerificacionIntegridad =
        custodia.verificarIntegridad(id, request.actor, request.fecha)

    @GetMapping("/{id}/sugerencias")
    fun sugerencias(@PathVariable id: String): List<Sugerencia> = capa.sugerenciasDe(id)
}
