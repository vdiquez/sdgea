package sgdea.contexts.validacionhumana

import java.time.Instant

// specs/007-validacion-humana/spec.md §3: este contexto NO tiene estado propio
// — sus datos reales (sugerencias, documentos, permisos) viven en
// Records/Custodia y Seguridad y Acceso. `SugerenciaPendiente` es la forma
// local que Validación Humana necesita para decidir, no una copia del tipo de
// dominio de records-custodia (independencia entre bounded contexts: ningún
// módulo Gradle de un contexto depende del código de otro).
data class SugerenciaPendiente(
    val documentoId: String,
    val tipo: String,
    val contenidoPropuesto: String,
    val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
    val fecha: Instant,
)

// Réplica local mínima de `Clasificacion` (records-custodia): lo que hace
// falta para construir el cuerpo de `POST /documentos/{id}/decisiones`.
data class ClasificacionPropuesta(
    val trdVersion: Int,
    val serieId: String,
    val subserieId: String? = null,
)

enum class TipoDeDecision {
    ACEPTACION,
    CORRECCION,
}

data class DecisionDeClasificacion(
    val documentoId: String,
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<SugerenciaPendiente>,
    val clasificacionResultante: ClasificacionPropuesta,
    val tipo: TipoDeDecision,
)

// Puertos (P-03: el dominio desconoce cuál implementación está detrás). En
// producción los tres los implementa un cliente HTTP real contra
// records-custodia/seguridad-acceso (configuracion/IntegracionHttp.kt); en
// los tests de este archivo los implementan dobles simples en memoria — mismo
// patrón que los `AlmacenDe*EnMemoria` de los demás contextos, aplicado aquí a
// llamadas de red en vez de a una base de datos.
interface FuenteDeSugerencias {
    fun pendientes(): List<SugerenciaPendiente>
}

interface RegistradorDeDecisiones {
    fun materializar(decision: DecisionDeClasificacion)
}

interface VerificadorDePermisos {
    fun tienePermiso(identidadId: String, accion: String, tipoRecurso: String): Boolean
}

class AccesoDenegadoException(mensaje: String) : RuntimeException(mensaje)

// RF-VH-001/002: agrega las sugerencias pendientes de Records/Custodia y las
// ordena por confianza ascendente — el archivista revisa primero lo más
// incierto (P-09; specs/eval/eval-clasificacion.md §4.6).
class ColaDeRevision(private val fuente: FuenteDeSugerencias) {

    fun ordenadasPorConfianza(): List<SugerenciaPendiente> = fuente.pendientes().sortedBy { it.confianza }

    // RF-VH-004: candidatas a aprobación masiva — confianza igual o por
    // encima del umbral (el umbral en sí es [CLARIFICAR], ver spec §8; aquí
    // solo se aplica el que reciba el llamador).
    fun candidatasAAprobacionMasiva(umbralDeConfianza: Double): List<SugerenciaPendiente> =
        fuente.pendientes().filter { it.confianza >= umbralDeConfianza }

    fun volumenYAntiguedadDeLaCola(): Pair<Int, Instant?> {
        val pendientes = fuente.pendientes()
        return pendientes.size to pendientes.minOfOrNull { it.fecha }
    }
}

// RF-VH-003/004/006/007/008: decide sobre una sugerencia (o varias, en
// bloque), verifica permiso antes de decidir, distingue aceptación de
// corrección, y envía la decisión a Records/Custodia — nunca materializa
// nada por su cuenta (P-01): `registrador` es quien de verdad escribe el
// estado, siempre en el otro contexto.
class GestionDeDecisiones(
    private val registrador: RegistradorDeDecisiones,
    private val permisos: VerificadorDePermisos,
) {

    fun decidir(
        identidadId: String,
        sugerencia: SugerenciaPendiente,
        clasificacionResultante: ClasificacionPropuesta,
        actor: String,
        fecha: Instant,
    ): DecisionDeClasificacion {
        exigirPermiso(identidadId)
        val decision = construirDecision(sugerencia, clasificacionResultante, actor, fecha)
        registrador.materializar(decision)
        return decision
    }

    // RF-VH-004: una sola acción explícita produce una decisión por cada
    // sugerencia candidata, todas con el mismo actor y fecha, referenciando
    // cada una explícitamente — nunca un bloque opaco que oculte cuáles se
    // decidieron (RNF-VH-004).
    fun aprobarEnBloque(
        identidadId: String,
        candidatas: List<SugerenciaPendiente>,
        resolver: (SugerenciaPendiente) -> ClasificacionPropuesta,
        actor: String,
        fecha: Instant,
    ): List<DecisionDeClasificacion> {
        exigirPermiso(identidadId)
        return candidatas.map { sugerencia ->
            val decision = construirDecision(sugerencia, resolver(sugerencia), actor, fecha)
            registrador.materializar(decision)
            decision
        }
    }

    private fun exigirPermiso(identidadId: String) {
        if (!permisos.tienePermiso(identidadId, "decidir", "documento")) {
            throw AccesoDenegadoException("La identidad '$identidadId' no tiene permiso para decidir sobre este recurso.")
        }
    }

    private fun construirDecision(
        sugerencia: SugerenciaPendiente,
        clasificacionResultante: ClasificacionPropuesta,
        actor: String,
        fecha: Instant,
    ): DecisionDeClasificacion {
        // RF-VH-008: coincide con la convención del EMISOR FICTICIO (T-08) de
        // portar la serie propuesta en `contenidoPropuesto` como texto plano;
        // no hay un contrato formal más rico todavía (spec-records-custodia.md
        // no define el formato de `contenidoPropuesto` por tipo de sugerencia).
        val tipo = if (sugerencia.contenidoPropuesto == clasificacionResultante.serieId) {
            TipoDeDecision.ACEPTACION
        } else {
            TipoDeDecision.CORRECCION
        }
        return DecisionDeClasificacion(
            documentoId = sugerencia.documentoId,
            actor = actor,
            fecha = fecha,
            sugerenciasReferenciadas = listOf(sugerencia),
            clasificacionResultante = clasificacionResultante,
            tipo = tipo,
        )
    }
}
