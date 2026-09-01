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

// RF-VH-001 (T-39): forma local de una sugerencia de límites pendiente de
// Normalización — mismo criterio de independencia entre bounded contexts que
// `SugerenciaPendiente` frente a `Sugerencia` (records-custodia). No es la
// misma cola que `SugerenciaPendiente`/`ColaDeRevision` (clasificación):
// revisar una sugerencia de límites no produce una `DecisionDeClasificacion`,
// produce una confirmación (`GestionDeLimites.confirmar`, T-38) — por eso es
// un tipo y una cola separados, no una variante del mismo.
data class UnidadPendienteDeLimites(
    val unidadId: String,
    val loteId: String,
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

// RF-VH-001 (T-39): puerto de la cola de límites — en producción lo implementa
// un cliente HTTP real contra Normalización (`GET /unidades/pendientes-de-limites`,
// T-39/§7), primer consumidor real de ese endpoint.
interface FuenteDeSugerenciasDeLimites {
    fun pendientes(): List<UnidadPendienteDeLimites>
}

// RF-VH-005: confirmación (o corrección) de límites de documento pendiente en
// Normalización. Normalización no distingue "confirmar" de "corregir" como
// operaciones separadas — `confirmar_limites` (dominio.py) admite límites
// "idénticos, ajustados o re-trazados" bajo una única llamada (RF-NO-004) —
// este puerto refleja exactamente esa misma unificación, no inventa una
// operación de corrección aparte que Normalización no tiene.
interface ConfirmadorDeLimites {
    fun confirmar(unidadId: String, actor: String, fecha: Instant)
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

// RF-VH-001/002 (T-39): agrega las unidades con sugerencia de límites
// pendiente de Normalización y las ordena por confianza ascendente — mismo
// criterio que `ColaDeRevision`, pero sin aprobación masiva: el
// `[CLARIFICAR]` de la spec (§8) sobre si existe un mecanismo de aprobación
// masiva análogo para sugerencias distintas de clasificación sigue abierto,
// así que esta cola no lo inventa.
class ColaDeLimites(private val fuente: FuenteDeSugerenciasDeLimites) {

    fun ordenadasPorConfianza(): List<UnidadPendienteDeLimites> = fuente.pendientes().sortedBy { it.confianza }

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
        // Corrección real (VETO de Codex sobre T-62/UI, ver STATE.md): el
        // EMISOR FICTICIO de Clasificación (T-45, `a_sugerencia_saliente_de_
        // clasificacion`) porta "serie/subserie", no solo "serie", cuando hay
        // subserie -- comparar solo contra `serieId` marcaba SIEMPRE como
        // corrección una aceptación exacta con subserie. `contenidoEsperado`
        // reconstruye el mismo formato que produce esa convención para que la
        // comparación sea honesta con ambas formas (con y sin subserie).
        val contenidoEsperado = clasificacionResultante.subserieId
            ?.let { "${clasificacionResultante.serieId}/$it" }
            ?: clasificacionResultante.serieId
        val tipo = if (sugerencia.contenidoPropuesto == contenidoEsperado) {
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

// RF-VH-005/007: cierra el ciclo que spec-infra-servicios.md §9 dejó abierto
// — un actor autorizado confirma (o corrige) los límites de una unidad
// documental candidata de Normalización desde Validación Humana, verificando
// permiso antes de reenviar la confirmación (mismo criterio que
// GestionDeDecisiones: nunca confirma nada por su cuenta, P-01 — es
// Normalización quien de verdad transiciona el estado y anexa el evento de
// auditoría con actor y fecha, T-37).
class GestionDeLimites(
    private val confirmador: ConfirmadorDeLimites,
    private val permisos: VerificadorDePermisos,
) {

    fun confirmar(identidadId: String, unidadId: String, actor: String, fecha: Instant) {
        if (!permisos.tienePermiso(identidadId, "confirmar", "documento")) {
            throw AccesoDenegadoException("La identidad '$identidadId' no tiene permiso para confirmar límites de este recurso.")
        }
        confirmador.confirmar(unidadId, actor, fecha)
    }
}
