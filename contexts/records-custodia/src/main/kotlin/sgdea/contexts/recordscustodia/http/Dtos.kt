package sgdea.contexts.recordscustodia.http

import java.time.Instant
import sgdea.contexts.recordscustodia.Clasificacion
import sgdea.contexts.recordscustodia.Procedencia
import sgdea.contexts.recordscustodia.Sugerencia

// specs/spec-infra-servicios.md §4 · Contrato mínimo — records-custodia.
// Cada DTO existe solo donde el tipo de dominio no basta tal cual como
// cuerpo HTTP (bytes binarios, o un método que junta varios parámetros
// sueltos); donde el tipo de dominio ya es un data class serializable
// (Procedencia, Clasificacion, Sugerencia, Trd) se usa directamente, igual
// que LotesController (T-16) hizo con LoteIngesta.
data class CustodiarRequest(
    val id: String,
    val bytesBase64: String,
    val actor: String,
    val fecha: Instant,
    val procedencia: Procedencia,
)

data class DecisionRequest(
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<Sugerencia> = emptyList(),
    val clasificacionResultante: Clasificacion,
    val esCorreccion: Boolean = false,
)

// RF-VH-009 (T-39): respuesta de GET /documentos/correcciones — envuelve el
// evento de auditoría de una decisión-corrección con un campo explícito que
// marca su estado frente al set patrón del arnés. El mecanismo real de
// re-revisión sigue [CLARIFICAR] (specs/eval/edd-harness.md §9); este campo
// solo declara honestamente que todavía no se promovió a verdad de
// referencia, no implementa el flujo de revisión en sí.
data class CorreccionPendienteDeRerevisionResponse(
    val actor: String,
    val fecha: Instant,
    val estadoAnterior: String?,
    val estadoPosterior: String?,
    val estadoDeRevision: String = "PENDIENTE_DE_REREVISION",
)

data class VerificacionRequest(
    val actor: String,
    val fecha: Instant,
)

data class RecibirSugerenciaRequest(
    val documentoId: String,
    val tipo: String,
    val contenidoPropuesto: String,
    val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
    val fecha: Instant,
)
