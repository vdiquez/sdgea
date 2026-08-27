package sgdea.contexts.validacionhumana.integracion

import java.time.Instant
import sgdea.contexts.validacionhumana.SugerenciaPendiente

// Cuerpo de POST /documentos/{id}/decisiones en records-custodia
// (spec-infra-servicios.md §4). `sugerenciasReferenciadas` reutiliza
// `SugerenciaPendiente` tal cual: sus campos coinciden uno a uno con los de
// `Sugerencia` en records-custodia (documentoId, tipo, contenidoPropuesto,
// modeloId, evidencia, confianza, fecha), así que Jackson serializa el mismo
// JSON que records-custodia ya sabe leer, sin una copia adicional del tipo.
data class DecisionRequestDto(
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<SugerenciaPendiente>,
    val clasificacionResultante: ClasificacionResultanteDto,
)

// Réplica del cuerpo de `Clasificacion` en records-custodia — a diferencia de
// `ClasificacionPropuesta` (dominio de Validación Humana), este SÍ lleva
// `documentoId` porque es lo que records-custodia exige en el JSON.
data class ClasificacionResultanteDto(
    val documentoId: String,
    val trdVersion: Int,
    val serieId: String,
    val subserieId: String? = null,
)

// Cuerpo de POST /autorizacion en seguridad-acceso (spec-infra-servicios.md
// §5). `nivelClasificacion` se fija en `PUBLICA` por ahora: dónde se captura
// el nivel de clasificación real de un documento sigue `[CLARIFICAR]`
// (specs/006-seguridad-acceso/spec.md §8) — no se inventa un valor real
// mientras esa decisión no exista.
data class AutorizarRequestDto(
    val identidadId: String,
    val accion: String,
    val tipoRecurso: String,
    val nivelClasificacion: String = "PUBLICA",
    val fecha: Instant,
)

data class AutorizarResponseDto(
    val resultado: String,
)
