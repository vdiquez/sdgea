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

class ModificacionDeEventoAuditoriaRechazadaException(mensaje: String) : RuntimeException(mensaje)

// RF-RC-005 / RNF-RC-002: bitácora de auditoría de solo anexado. `anexar` es
// la única operación que añade contenido; todo intento de modificar o borrar
// un evento ya anexado se rechaza. No implementa encadenamiento de huellas
// entre eventos (spec §8 [CLARIFICAR] sobre si la bitácora además necesita
// una cadena de huellas) — eso es una decisión pendiente distinta de "solo
// anexado, sin modificar ni borrar", que es lo único que exige RF-RC-005.
class BitacoraAuditoria {

    private val eventos = mutableListOf<EventoAuditoria>()

    val todos: List<EventoAuditoria> get() = eventos.toList()

    fun anexar(evento: EventoAuditoria) {
        eventos.add(evento)
    }

    fun intentarModificar(indice: Int, eventoNuevo: EventoAuditoria) {
        eventos[indice]
        throw ModificacionDeEventoAuditoriaRechazadaException(
            "El evento de auditoría en la posición $indice ya existe; modificarlo se rechaza.",
        )
    }

    fun intentarBorrar(indice: Int) {
        eventos[indice]
        throw ModificacionDeEventoAuditoriaRechazadaException(
            "El evento de auditoría en la posición $indice ya existe; borrarlo se rechaza.",
        )
    }
}

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
// inmutable, conserva su procedencia y, una vez materializada, su
// clasificación (RF-RC-004). Los metadatos y el estado de ciclo de vida
// completo quedan fuera de alcance (spec §8, [CLARIFICAR] del modelo de
// estados).
data class DocumentoDeArchivo(
    val id: String,
    val originalId: String,
    val procedencia: Procedencia,
    val clasificacion: Clasificacion? = null,
)

// RF-RC-001: custodia el original en modo de una sola escritura y registra su huella
// criptográfica; un intento de modificarlo se rechaza y genera un evento de auditoría.
// RF-RC-002: cada documento conserva su procedencia completa.
class CustodiaOriginales {

    private val originales = mutableMapOf<String, OriginalInmutable>()
    private val documentos = mutableMapOf<String, DocumentoDeArchivo>()
    private val bitacora = BitacoraAuditoria()

    val eventosDeAuditoria: List<EventoAuditoria> get() = bitacora.todos

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
        bitacora.anexar(
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

    fun consultarDocumento(id: String): DocumentoDeArchivo = documentos.getValue(id)

    // RF-RC-004: única operación que puede cambiar la clasificación de un
    // documento. No existe ningún otro método público que la mute — recibir
    // una Sugerencia (CapaAnticorrupcionSugerencias.recibir) nunca la toca
    // (spec §3 invariante 2). Deja un evento de auditoría con el actor y la
    // fecha de la decisión humana, tal como exige el criterio.
    fun materializar(decision: DecisionHumana): DocumentoDeArchivo {
        val documentoActual = documentos.getValue(decision.documentoId)
        val documentoActualizado = documentoActual.copy(clasificacion = decision.clasificacionResultante)
        documentos[decision.documentoId] = documentoActualizado
        bitacora.anexar(
            EventoAuditoria(
                actor = decision.actor,
                fecha = decision.fecha,
                tipo = "DECISION_HUMANA_MATERIALIZADA",
                estadoAnterior = documentoActual.clasificacion?.serieId,
                estadoPosterior = decision.clasificacionResultante.serieId,
            ),
        )
        return documentoActualizado
    }

    // El original ya custodiado nunca se sobrescribe: toda solicitud de cambio se
    // rechaza y deja evento de auditoría, sin tocar los bytes ni la huella almacenados.
    fun intentarModificar(id: String, bytesNuevos: ByteArray, actor: String, fecha: Instant) {
        originales.getValue(id)
        bitacora.anexar(
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

// Sugerencia (spec §2/§3, RF-RC-003): propuesta emitida por un contexto
// probabilístico (Clasificación, Enriquecimiento); porta modelo, evidencia y
// confianza. No es estado: nunca modifica la clasificación, los metadatos ni
// el estado de un documento (spec §3 invariante 2 / P-01).
data class Sugerencia(
    val documentoId: String,
    val tipo: String,
    val contenidoPropuesto: String,
    val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
    val fecha: Instant,
)

// Entrada cruda proveniente de un contexto probabilístico (spec §4, entradas
// "Sugerencia de serie/subserie" y "Sugerencia de metadatos"), antes de cruzar
// la capa anticorrupción. EMISOR FICTICIO: sustituye a Clasificación o
// Enriquecimiento — la constitución prohíbe implementar aquí un componente
// probabilístico real; esto solo ejercita el contrato de traducción de
// RF-RC-003.
data class SugerenciaEntrante(
    val documentoId: String,
    val tipo: String,
    val contenidoPropuesto: String,
    val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
)

// Decisión humana (spec §2/§3, RF-RC-004): acto explícito de un usuario que
// materializa una clasificación sobre un documento, referenciando las
// sugerencias que la motivaron (si las hubo) o actuando de forma manual
// (sugerenciasReferenciadas vacía). Es lo único que transiciona el estado de
// un documento (P-01).
data class DecisionHumana(
    val documentoId: String,
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<Sugerencia>,
    val clasificacionResultante: Clasificacion,
)

// Capa anticorrupción (spec §4): traduce la entrada de un contexto
// probabilístico en una Sugerencia vinculada a un documento ya custodiado,
// sin tocar su clasificación ni su estado. Es la materialización de P-01: la
// única forma de cambiar el estado de un documento es una decisión humana
// (RF-RC-004), nunca una sugerencia.
class CapaAnticorrupcionSugerencias(private val custodia: CustodiaOriginales) {

    private val sugerencias = mutableListOf<Sugerencia>()

    fun recibir(entrada: SugerenciaEntrante, fecha: Instant): Sugerencia {
        custodia.consultarDocumento(entrada.documentoId)
        val sugerencia = Sugerencia(
            documentoId = entrada.documentoId,
            tipo = entrada.tipo,
            contenidoPropuesto = entrada.contenidoPropuesto,
            modeloId = entrada.modeloId,
            evidencia = entrada.evidencia,
            confianza = entrada.confianza,
            fecha = fecha,
        )
        sugerencias.add(sugerencia)
        return sugerencia
    }

    fun sugerenciasDe(documentoId: String): List<Sugerencia> =
        sugerencias.filter { it.documentoId == documentoId }
}

// Regla de retención de una serie/subserie de la TRD (spec §2/§3): tiempo de
// retención y disposición final. El tiempo y la disposición concretos son
// dato del design partner, no una referencia normativa inventada por esta
// tarea; aquí solo se modela la estructura que los porta.
data class ReglaRetencion(
    val tiempoRetencionAnios: Int,
    val disposicionFinal: String,
)

// Subserie: nodo hoja del árbol de clasificación de la TRD (spec §2).
data class Subserie(
    val id: String,
    val nombre: String,
    val reglaRetencion: ReglaRetencion,
)

// Serie: nodo del árbol de clasificación de la TRD (spec §2); puede tener
// subseries.
data class Serie(
    val id: String,
    val nombre: String,
    val reglaRetencion: ReglaRetencion,
    val subseries: List<Subserie> = emptyList(),
)

// TRD/CCD (spec §2/§3, RF-RC-006): objeto versionado — versión, vigencia y
// árbol de series/subseries con sus reglas de retención y disposición final.
data class Trd(
    val version: Int,
    val vigenteDesde: Instant,
    val series: List<Serie>,
)

// Clasificación de un documento contra una versión específica de la TRD
// (spec §3, RF-RC-006): referencia la serie/subserie y el número de versión
// de TRD usada en el momento de clasificar, de modo que esa referencia
// sobrevive a la publicación de versiones posteriores de la TRD. El resto
// del agregado Documento de archivo (metadatos, estado de ciclo de vida) es
// alcance de RF-RC-003 en adelante, no de esta tarea.
data class Clasificacion(
    val documentoId: String,
    val trdVersion: Int,
    val serieId: String,
    val subserieId: String? = null,
)

// RF-RC-006: registro de versiones publicadas de la TRD. Publicar una nueva
// versión solo añade una entrada; nunca sobrescribe ni retira una versión
// anterior, así que toda `Clasificacion` que referencia una versión ya
// publicada sigue resolviendo contra esa misma versión después de que se
// publique una nueva.
class RegistroTrd {

    private val versiones = mutableMapOf<Int, Trd>()

    fun publicar(trd: Trd) {
        versiones[trd.version] = trd
    }

    fun version(numero: Int): Trd = versiones.getValue(numero)
}
