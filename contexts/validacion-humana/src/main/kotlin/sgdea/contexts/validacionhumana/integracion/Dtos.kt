package sgdea.contexts.validacionhumana.integracion

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import sgdea.contexts.validacionhumana.SugerenciaPendiente

// Cuerpo de POST /documentos/{id}/decisiones en records-custodia
// (spec-infra-servicios.md §4). `sugerenciasReferenciadas` reutiliza
// `SugerenciaPendiente` tal cual: sus campos coinciden uno a uno con los de
// `Sugerencia` en records-custodia (documentoId, tipo, contenidoPropuesto,
// modeloId, evidencia, confianza, fecha), así que Jackson serializa el mismo
// JSON que records-custodia ya sabe leer, sin una copia adicional del tipo.
// `esCorreccion` (T-39, RF-VH-009): Validación Humana ya sabe si la decisión
// coincidió con la sugerencia que la originó o la corrigió
// (`GestionDeDecisiones.construirDecision`, `DecisionDeClasificacion.tipo`)
// — records-custodia no recalcula esa comparación, solo persiste esta
// bandera para exponer las correcciones como candidatas a re-revisión
// (`GET /documentos/correcciones`).
data class DecisionRequestDto(
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<SugerenciaPendiente>,
    val clasificacionResultante: ClasificacionResultanteDto,
    val esCorreccion: Boolean,
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

// Cuerpo de POST /unidades/{id}/confirmacion-limites en normalizacion
// (spec-infra-servicios.md §7, RF-NO-004).
data class ConfirmacionDeLimitesRequestDto(
    val actor: String,
    val fecha: Instant,
)

// Cuerpo (parcial) de la respuesta de GET /unidades/pendientes-de-limites en
// normalizacion (spec-infra-servicios.md §7, T-39). Python/FastAPI serializa
// en snake_case (convención de ese contexto desde T-33), a diferencia de los
// contextos Kotlin — primera vez que Validación Humana necesita mapear
// campos snake_case explícitos (@JsonProperty) en vez de solo descartar la
// respuesta como hace `ConfirmadorDeLimitesHttp`. `@JsonIgnoreProperties
// (ignoreUnknown = true)` porque esta unidad solo necesita un subconjunto de
// lo que normalizacion expone (id, lote_id y la sugerencia de límites).
@JsonIgnoreProperties(ignoreUnknown = true)
data class SugerenciaDeLimitesDto(
    @JsonProperty("modelo_id") val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
    val fecha: Instant,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UnidadPendienteDeLimitesDto(
    val id: String,
    @JsonProperty("lote_id") val loteId: String,
    @JsonProperty("sugerencia_de_limites") val sugerenciaDeLimites: SugerenciaDeLimitesDto,
)
