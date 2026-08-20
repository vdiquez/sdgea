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

// Procedencia de un documento de archivo (spec §2/§3, RF-RC-002): fuente,
// fecha de ingesta e identificador del lote o flujo de origen. Es la misma
// información que Captura/Ingesta expone por ítem vía RF-CI-007 (ver
// spec-captura-ingesta.md §4, salida "Procedencia del ítem" hacia
// Records/Custodia); este contexto la recibe y la conserva junto al documento.
data class Procedencia(
    val fuente: String,
    val fecha: Instant,
    val loteOFlujoId: String,
)

// Documento de archivo (spec §3): boceto mínimo que referencia su original
// inmutable y conserva su procedencia. El resto del agregado (metadatos,
// clasificación, estado de ciclo de vida) es alcance de otras tareas
// (RF-RC-003 en adelante).
data class DocumentoDeArchivo(
    val id: String,
    val originalId: String,
    val procedencia: Procedencia,
)

// RF-RC-001: custodia el original en modo de una sola escritura y registra su huella
// criptográfica; un intento de modificarlo se rechaza y genera un evento de auditoría.
// RF-RC-002: cada documento conserva su procedencia completa.
class CustodiaOriginales {

    private val originales = mutableMapOf<String, OriginalInmutable>()
    private val documentos = mutableMapOf<String, DocumentoDeArchivo>()
    private val eventos = mutableListOf<EventoAuditoria>()

    val eventosDeAuditoria: List<EventoAuditoria> get() = eventos.toList()

    fun custodiar(id: String, bytes: ByteArray, actor: String, fecha: Instant, procedencia: Procedencia): OriginalInmutable {
        val original = OriginalInmutable(
            id = id,
            bytes = bytes,
            algoritmoHuella = ALGORITMO_HUELLA,
            huella = calcularHuella(bytes),
            fechaCustodia = fecha,
        )
        originales[id] = original
        documentos[id] = DocumentoDeArchivo(id = id, originalId = id, procedencia = procedencia)
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

    fun consultarProcedencia(id: String): Procedencia = documentos.getValue(id).procedencia

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
