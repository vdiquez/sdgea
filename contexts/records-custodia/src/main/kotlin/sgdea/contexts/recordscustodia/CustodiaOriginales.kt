package sgdea.contexts.recordscustodia

import java.security.MessageDigest
import java.time.Instant

// Original inmutable: bytes + algoritmo y valor de huella + fecha de custodia (spec §3).
data class OriginalInmutable(
    val id: String,
    val bytes: ByteArray,
    val algoritmoHuella: String,
    val huella: String,
    val fechaCustodia: Instant,
)

// Evento de auditoría (spec §3): atribuible, fechado, con estado anterior y posterior.
// Boceto mínimo para satisfacer RF-RC-001; la bitácora de solo anexado a prueba de
// manipulación es RF-RC-005 (T-10), fuera de alcance de esta tarea.
data class EventoAuditoria(
    val actor: String,
    val fecha: Instant,
    val tipo: String,
    val estadoAnterior: String?,
    val estadoPosterior: String?,
)

class ModificacionDeOriginalRechazadaException(mensaje: String) : RuntimeException(mensaje)

// RF-RC-001: custodia el original en modo de una sola escritura y registra su huella
// criptográfica; un intento de modificarlo se rechaza y genera un evento de auditoría.
class CustodiaOriginales {

    private val originales = mutableMapOf<String, OriginalInmutable>()
    private val eventos = mutableListOf<EventoAuditoria>()

    val eventosDeAuditoria: List<EventoAuditoria> get() = eventos.toList()

    fun custodiar(id: String, bytes: ByteArray, actor: String, fecha: Instant): OriginalInmutable {
        val original = OriginalInmutable(
            id = id,
            bytes = bytes,
            algoritmoHuella = ALGORITMO_HUELLA,
            huella = calcularHuella(bytes),
            fechaCustodia = fecha,
        )
        originales[id] = original
        eventos.add(
            EventoAuditoria(
                actor = actor,
                fecha = fecha,
                tipo = "ORIGINAL_CUSTODIADO",
                estadoAnterior = null,
                estadoPosterior = "CUSTODIADO",
            ),
        )
        return original
    }

    fun consultar(id: String): OriginalInmutable = originales.getValue(id)

    // El original ya custodiado nunca se sobrescribe: toda solicitud de cambio se
    // rechaza y deja evento de auditoría, sin tocar los bytes ni la huella almacenados.
    fun intentarModificar(id: String, bytesNuevos: ByteArray, actor: String, fecha: Instant) {
        originales.getValue(id)
        eventos.add(
            EventoAuditoria(
                actor = actor,
                fecha = fecha,
                tipo = "INTENTO_MODIFICACION_RECHAZADO",
                estadoAnterior = "CUSTODIADO",
                estadoPosterior = "CUSTODIADO",
            ),
        )
        throw ModificacionDeOriginalRechazadaException(
            "El original '$id' está bajo custodia inmutable; la modificación se rechaza.",
        )
    }

    private fun calcularHuella(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITMO_HUELLA).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        // RNF-RC-001: algoritmo criptográficamente robusto y verificable de forma
        // independiente. SHA-256 es el estándar por defecto; spec §8 deja abierto si
        // además se necesita otro algoritmo o encadenamiento de huellas (RF-RC-005).
        const val ALGORITMO_HUELLA = "SHA-256"
    }
}
