package sgdea.contexts.recordscustodia.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.AccesoDenegadoException
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.EventoAuditoria
import sgdea.contexts.recordscustodia.VerificadorDeAutorizacion

// P-08 / T-48: observabilidad de la bitácora de auditoría — mismo criterio que
// GET /eventos-seguridad (seguridad-acceso) y GET /eventos-auditoria
// (normalizacion, extraccion). `custodia.eventosDeAuditoria` expone la misma
// BitacoraAuditoria compartida entre CustodiaOriginales y
// CapaAnticorrupcionSugerencias (T-20, RecordsCustodiaConfig.bitacoraAuditoria),
// así que también incluye los eventos SUGERENCIA_RECIBIDA que otros contextos
// (p. ej. Clasificación) generan al enviar una sugerencia vía POST /sugerencias.
//
// T-63 (specs/008-ui-demo/spec.md §1): la UI de demo necesita este endpoint
// para RF-UI-005/RF-UI-011, así que entra en el alcance del prerrequisito de
// arquitectura -- mismo criterio de `identidadId` que DocumentosController.
@RestController
@RequestMapping("/eventos-auditoria")
class EventosAuditoriaController(
    private val custodia: CustodiaOriginales,
    private val permisos: VerificadorDeAutorizacion,
) {

    @GetMapping
    fun listar(@RequestParam identidadId: String): List<EventoAuditoria> {
        if (!permisos.tienePermiso(identidadId, "leer", "documento")) {
            throw AccesoDenegadoException("La identidad '$identidadId' no tiene permiso para ver la bitácora de auditoría.")
        }
        return custodia.eventosDeAuditoria
    }
}
