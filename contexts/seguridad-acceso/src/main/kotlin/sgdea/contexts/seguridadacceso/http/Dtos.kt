package sgdea.contexts.seguridadacceso.http

import java.time.Instant
import sgdea.contexts.seguridadacceso.NivelClasificacion
import sgdea.contexts.seguridadacceso.Permiso
import sgdea.contexts.seguridadacceso.ResultadoAutorizacion

// specs/spec-infra-servicios.md §5 · Contrato mínimo — seguridad-acceso. Cada DTO
// existe solo donde el tipo de dominio no basta tal cual como cuerpo HTTP; donde
// el tipo de dominio ya es un data class serializable (Permiso) se usa
// directamente, mismo criterio que records-custodia (T-17) aplicó con
// Procedencia/Clasificacion.
data class CrearIdentidadRequest(
    val id: String,
    val actor: String,
    val credencial: String,
    val roles: List<String> = emptyList(),
)

data class AutenticarRequest(
    val actor: String,
    val credencial: String,
    val fecha: Instant,
)

data class AsignarRolRequest(
    val rol: String,
)

data class CrearRolRequest(
    val nombre: String,
    val permisos: List<Permiso> = emptyList(),
)

data class AutorizarRequest(
    val identidadId: String,
    val accion: String,
    val tipoRecurso: String,
    val nivelClasificacion: NivelClasificacion = NivelClasificacion.PUBLICA,
    val recurso: String? = null,
    val fecha: Instant,
)

data class AutorizarResponse(
    val resultado: ResultadoAutorizacion,
)
