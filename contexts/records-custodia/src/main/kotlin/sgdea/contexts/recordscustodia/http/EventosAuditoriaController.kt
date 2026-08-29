package sgdea.contexts.recordscustodia.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.EventoAuditoria

// P-08 / T-48: observabilidad de la bitácora de auditoría — mismo criterio que
// GET /eventos-seguridad (seguridad-acceso) y GET /eventos-auditoria
// (normalizacion, extraccion). `custodia.eventosDeAuditoria` expone la misma
// BitacoraAuditoria compartida entre CustodiaOriginales y
// CapaAnticorrupcionSugerencias (T-20, RecordsCustodiaConfig.bitacoraAuditoria),
// así que también incluye los eventos SUGERENCIA_RECIBIDA que otros contextos
// (p. ej. Clasificación) generan al enviar una sugerencia vía POST /sugerencias.
@RestController
@RequestMapping("/eventos-auditoria")
class EventosAuditoriaController(private val custodia: CustodiaOriginales) {

    @GetMapping
    fun listar(): List<EventoAuditoria> = custodia.eventosDeAuditoria
}
