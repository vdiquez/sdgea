package sgdea.contexts.validacionhumana.http

import java.time.Instant
import sgdea.contexts.validacionhumana.ClasificacionPropuesta
import sgdea.contexts.validacionhumana.SugerenciaPendiente

data class DecidirRequest(
    val identidadId: String,
    val sugerencia: SugerenciaPendiente,
    val clasificacionResultante: ClasificacionPropuesta,
    val actor: String,
    val fecha: Instant,
)

// `resoluciones` mapea documentoId -> la clasificación resultante para ese
// documento; se resuelve así, no con un único valor, porque la aprobación en
// bloque cubre varios documentos a la vez (RF-VH-004).
data class AprobarEnBloqueRequest(
    val identidadId: String,
    val candidatas: List<SugerenciaPendiente>,
    val resoluciones: Map<String, ClasificacionPropuesta>,
    val actor: String,
    val fecha: Instant,
)

data class EstadoDeColaResponse(
    val volumen: Int,
    val masAntigua: Instant?,
)

data class ConfirmarLimitesRequest(
    val identidadId: String,
    val actor: String,
    val fecha: Instant,
)

// Confirmación de lo que ya se conocía antes de llamar a Normalización
// (unidadId/actor/fecha) — no una copia de `UnidadDocumentalCandidata`, que
// vive solo en Normalización (independencia entre bounded contexts).
data class ConfirmacionDeLimitesResponse(
    val unidadId: String,
    val actor: String,
    val fecha: Instant,
)
