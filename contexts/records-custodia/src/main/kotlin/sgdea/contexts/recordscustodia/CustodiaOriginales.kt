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
// `esCorreccion` (T-39, RF-VH-009): distingue una decisión que corrige una
// sugerencia de una que la acepta o de una decisión manual — con default
// `false` para que ningún sitio existente que construye `EventoAuditoria`
// (custodia, intento de modificación, discrepancia de integridad) necesite
// cambiar. Solo `materializar` la fija según `DecisionHumana.esCorreccion`.
data class EventoAuditoria(
    val actor: String,
    val fecha: Instant,
    val tipo: String,
    val estadoAnterior: String?,
    val estadoPosterior: String?,
    val esCorreccion: Boolean = false,
)

class ModificacionDeOriginalRechazadaException(mensaje: String) : RuntimeException(mensaje)

class ModificacionDeEventoAuditoriaRechazadaException(mensaje: String) : RuntimeException(mensaje)

// specs/spec-infra-servicios.md §4: puertos de almacenamiento (P-03) que
// reemplazan los mapas/listas en memoria de este contexto por una
// implementación intercambiable. El valor por defecto de cada puerto es la
// misma implementación en memoria que estas clases ya usaban, así que ningún
// test de dominio existente (T-01..T-11) cambia; la tarea que ejecuta esta
// spec (T-17) inyecta implementaciones respaldadas por Postgres detrás de
// estos mismos puertos, sin tocar el contrato de los métodos públicos.
interface AlmacenDeEventos {
    fun anexar(evento: EventoAuditoria)
    fun todos(): List<EventoAuditoria>
    fun en(indice: Int): EventoAuditoria
}

class AlmacenDeEventosEnMemoria : AlmacenDeEventos {
    private val eventos = mutableListOf<EventoAuditoria>()
    override fun anexar(evento: EventoAuditoria) {
        eventos.add(evento)
    }
    override fun todos(): List<EventoAuditoria> = eventos.toList()
    override fun en(indice: Int): EventoAuditoria = eventos[indice]
}

// RF-RC-005 / RNF-RC-002: bitácora de auditoría de solo anexado. `anexar` es
// la única operación que añade contenido; todo intento de modificar o borrar
// un evento ya anexado se rechaza. No implementa encadenamiento de huellas
// entre eventos (spec §8 [CLARIFICAR] sobre si la bitácora además necesita
// una cadena de huellas) — eso es una decisión pendiente distinta de "solo
// anexado, sin modificar ni borrar", que es lo único que exige RF-RC-005.
class BitacoraAuditoria(private val almacen: AlmacenDeEventos = AlmacenDeEventosEnMemoria()) {

    val todos: List<EventoAuditoria> get() = almacen.todos()

    fun anexar(evento: EventoAuditoria) {
        almacen.anexar(evento)
    }

    fun intentarModificar(indice: Int, eventoNuevo: EventoAuditoria) {
        almacen.en(indice)
        throw ModificacionDeEventoAuditoriaRechazadaException(
            "El evento de auditoría en la posición $indice ya existe; modificarlo se rechaza.",
        )
    }

    fun intentarBorrar(indice: Int) {
        almacen.en(indice)
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

// RF-RC-009: resultado de verificar un original contra su huella registrada.
data class ResultadoVerificacionIntegridad(
    val id: String,
    val coincide: Boolean,
    val huellaRegistrada: String,
    val huellaCalculada: String,
)

// RF-RC-009: reporte de una corrida de verificación sobre todos los originales
// custodiados; `discrepancias` es el subconjunto que no coincide con su huella.
data class ReporteVerificacionIntegridad(
    val resultados: List<ResultadoVerificacionIntegridad>,
) {
    val discrepancias: List<ResultadoVerificacionIntegridad> get() = resultados.filterNot { it.coincide }
}

interface AlmacenDeOriginales {
    fun guardar(original: OriginalInmutable)
    fun buscar(id: String): OriginalInmutable?
    fun todos(): List<OriginalInmutable>
}

class AlmacenDeOriginalesEnMemoria : AlmacenDeOriginales {
    private val originales = mutableMapOf<String, OriginalInmutable>()
    override fun guardar(original: OriginalInmutable) {
        originales[original.id] = original
    }
    override fun buscar(id: String): OriginalInmutable? = originales[id]
    override fun todos(): List<OriginalInmutable> = originales.values.toList()
}

interface AlmacenDeDocumentos {
    fun guardar(documento: DocumentoDeArchivo)
    fun buscar(id: String): DocumentoDeArchivo?
    fun todos(): List<DocumentoDeArchivo>
}

class AlmacenDeDocumentosEnMemoria : AlmacenDeDocumentos {
    private val documentos = mutableMapOf<String, DocumentoDeArchivo>()
    override fun guardar(documento: DocumentoDeArchivo) {
        documentos[documento.id] = documento
    }
    override fun buscar(id: String): DocumentoDeArchivo? = documentos[id]
    override fun todos(): List<DocumentoDeArchivo> = documentos.values.toList()
}

private fun <T> T?.oClaveFaltante(id: String): T = this ?: throw NoSuchElementException("Key $id is missing in the map.")

// RF-RC-001: custodia el original en modo de una sola escritura y registra su huella
// criptográfica; un intento de modificarlo se rechaza y genera un evento de auditoría.
// RF-RC-002: cada documento conserva su procedencia completa.
//
// `lectorDeAlmacenamiento` es el seam mínimo de RF-RC-009: la verificación de
// integridad debe poder detectar que el medio de almacenamiento divergió de la
// huella registrada (bit-rot, corrupción del medio) sin que este contexto exponga
// ninguna API pública para mutar un original ya custodiado (invariante 1). Por
// defecto lee el propio registro en memoria, así que en operación normal siempre
// coincide; las pruebas lo sustituyen para simular la divergencia sin tocar
// `consultar`/`custodiar`.
class CustodiaOriginales(
    private val lectorDeAlmacenamiento: ((id: String) -> ByteArray)? = null,
    private val almacenDeOriginales: AlmacenDeOriginales = AlmacenDeOriginalesEnMemoria(),
    private val almacenDeDocumentos: AlmacenDeDocumentos = AlmacenDeDocumentosEnMemoria(),
    private val bitacora: BitacoraAuditoria = BitacoraAuditoria(),
) {

    val eventosDeAuditoria: List<EventoAuditoria> get() = bitacora.todos

    // RF-VH-009 (T-39): las correcciones quedan disponibles para re-revisión,
    // sin incorporarse en crudo al set patrón del arnés — el mecanismo exacto
    // de esa re-revisión sigue [CLARIFICAR] (specs/eval/edd-harness.md §9);
    // esto solo expone las candidatas, marcadas como tales, no decide cómo se
    // promueven a verdad de referencia.
    fun correccionesPendientesDeRerevision(): List<EventoAuditoria> = eventosDeAuditoria.filter { it.esCorreccion }

    fun custodiar(id: String, bytes: ByteArray, actor: String, fecha: Instant, procedencia: Procedencia): OriginalInmutable {
        val original = OriginalInmutable(
            id = id,
            bytes = bytes,
            algoritmoHuella = ALGORITMO_HUELLA,
            huella = calcularHuella(bytes),
            fechaCustodia = fecha,
        )
        almacenDeOriginales.guardar(original)
        almacenDeDocumentos.guardar(DocumentoDeArchivo(id = id, originalId = id, procedencia = procedencia))
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

    fun consultar(id: String): OriginalInmutable = almacenDeOriginales.buscar(id).oClaveFaltante(id)

    fun consultarProcedencia(id: String): Procedencia = almacenDeDocumentos.buscar(id).oClaveFaltante(id).procedencia

    fun consultarDocumento(id: String): DocumentoDeArchivo = almacenDeDocumentos.buscar(id).oClaveFaltante(id)

    // RF-VH-001 (specs/007-validacion-humana/spec.md): Validación Humana necesita
    // agregar sugerencias de documentos que todavía no tienen una decisión
    // materializada, a través de todos los documentos, no de uno a la vez
    // (GET /documentos/{id}/sugerencias, RF-RC-003, ya existía pero es por
    // documento). `clasificacion == null` es la señal que RF-RC-004 ya produce
    // — no se inventa un campo de estado nuevo para esto.
    fun documentosSinClasificar(): List<DocumentoDeArchivo> = almacenDeDocumentos.todos().filter { it.clasificacion == null }

    // RF-RC-004: única operación que puede cambiar la clasificación de un
    // documento. No existe ningún otro método público que la mute — recibir
    // una Sugerencia (CapaAnticorrupcionSugerencias.recibir) nunca la toca
    // (spec §3 invariante 2). Deja un evento de auditoría con el actor y la
    // fecha de la decisión humana, tal como exige el criterio.
    fun materializar(decision: DecisionHumana): DocumentoDeArchivo {
        val documentoActual = almacenDeDocumentos.buscar(decision.documentoId).oClaveFaltante(decision.documentoId)
        val documentoActualizado = documentoActual.copy(clasificacion = decision.clasificacionResultante)
        almacenDeDocumentos.guardar(documentoActualizado)
        bitacora.anexar(
            EventoAuditoria(
                actor = decision.actor,
                fecha = decision.fecha,
                tipo = "DECISION_HUMANA_MATERIALIZADA",
                estadoAnterior = documentoActual.clasificacion?.serieId,
                estadoPosterior = decision.clasificacionResultante.serieId,
                esCorreccion = decision.esCorreccion,
            ),
        )
        return documentoActualizado
    }

    // El original ya custodiado nunca se sobrescribe: toda solicitud de cambio se
    // rechaza y deja evento de auditoría, sin tocar los bytes ni la huella almacenados.
    fun intentarModificar(id: String, bytesNuevos: ByteArray, actor: String, fecha: Instant) {
        almacenDeOriginales.buscar(id).oClaveFaltante(id)
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

    // RF-RC-009: verifica, por demanda, que el original almacenado bajo `id`
    // coincide con su huella registrada; si no coincide, se reporta como
    // discrepancia y se genera un evento de auditoría. La ejecución "de forma
    // programada" que menciona el RF es responsabilidad de un disparador externo
    // (cron/scheduler) que invoque este mismo método u `verificarTodos`; no es
    // lógica de dominio y queda fuera de alcance de esta tarea.
    fun verificarIntegridad(id: String, actor: String, fecha: Instant): ResultadoVerificacionIntegridad {
        val original = almacenDeOriginales.buscar(id).oClaveFaltante(id)
        val bytesAlmacenados = lectorDeAlmacenamiento?.invoke(id) ?: original.bytes
        val huellaCalculada = calcularHuella(bytesAlmacenados)
        val resultado = ResultadoVerificacionIntegridad(
            id = id,
            coincide = huellaCalculada == original.huella,
            huellaRegistrada = original.huella,
            huellaCalculada = huellaCalculada,
        )
        if (!resultado.coincide) {
            bitacora.anexar(
                EventoAuditoria(
                    actor = actor,
                    fecha = fecha,
                    tipo = "DISCREPANCIA_DE_INTEGRIDAD",
                    estadoAnterior = "CUSTODIADO",
                    estadoPosterior = "DISCREPANCIA_DETECTADA",
                ),
            )
        }
        return resultado
    }

    // RF-RC-009: corre `verificarIntegridad` sobre todos los originales
    // custodiados y agrega el resultado en un único reporte de discrepancias.
    fun verificarTodos(actor: String, fecha: Instant): ReporteVerificacionIntegridad =
        ReporteVerificacionIntegridad(almacenDeOriginales.todos().map { verificarIntegridad(it.id, actor, fecha) })

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
// formaOriginal (T-53, VETO real de Codex sobre T-52): opcional -- solo
// Enriquecimiento tiene un concepto de "forma tal como aparece en el
// documento" distinto del valor propuesto (RF-EN-003); Clasificación no lo
// tiene y sigue enviando null, sin que el contrato se lo exija.
data class Sugerencia(
    val documentoId: String,
    val tipo: String,
    val contenidoPropuesto: String,
    val modeloId: String,
    val evidencia: List<String>,
    val confianza: Double,
    val fecha: Instant,
    val formaOriginal: String? = null,
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
    val formaOriginal: String? = null,
)

// Decisión humana (spec §2/§3, RF-RC-004): acto explícito de un usuario que
// materializa una clasificación sobre un documento, referenciando las
// sugerencias que la motivaron (si las hubo) o actuando de forma manual
// (sugerenciasReferenciadas vacía). Es lo único que transiciona el estado de
// un documento (P-01).
// `esCorreccion` (T-39, RF-VH-009): la asigna el llamador (Validación Humana
// ya sabe si la decisión coincidió con la sugerencia que la originó o la
// corrigió, `GestionDeDecisiones.construirDecision`) — records-custodia no
// recalcula esa comparación, solo la persiste para exponerla como candidata a
// re-revisión. Default `false`: una decisión manual sin sugerencia asociada
// nunca es una "corrección".
data class DecisionHumana(
    val documentoId: String,
    val actor: String,
    val fecha: Instant,
    val sugerenciasReferenciadas: List<Sugerencia>,
    val clasificacionResultante: Clasificacion,
    val esCorreccion: Boolean = false,
)

interface AlmacenDeSugerencias {
    fun guardar(sugerencia: Sugerencia)
    fun de(documentoId: String): List<Sugerencia>
}

class AlmacenDeSugerenciasEnMemoria : AlmacenDeSugerencias {
    private val sugerencias = mutableListOf<Sugerencia>()
    override fun guardar(sugerencia: Sugerencia) {
        sugerencias.add(sugerencia)
    }
    override fun de(documentoId: String): List<Sugerencia> = sugerencias.filter { it.documentoId == documentoId }
}

// Capa anticorrupción (spec §4): traduce la entrada de un contexto
// probabilístico en una Sugerencia vinculada a un documento ya custodiado,
// sin tocar su clasificación ni su estado. Es la materialización de P-01: la
// única forma de cambiar el estado de un documento es una decisión humana
// (RF-RC-004), nunca una sugerencia.
class CapaAnticorrupcionSugerencias(
    private val custodia: CustodiaOriginales,
    private val almacen: AlmacenDeSugerencias = AlmacenDeSugerenciasEnMemoria(),
    private val bitacora: BitacoraAuditoria = BitacoraAuditoria(),
) {

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
            formaOriginal = entrada.formaOriginal,
        )
        almacen.guardar(sugerencia)
        // P-08: la recepción de una sugerencia es una transición que exige evento de
        // auditoría inmutable, atribuible, fechado y con estado anterior/posterior — no
        // solo la materialización (RF-RC-004). Actor de sistema: modeloId ya es dato del
        // contrato de entrada (T-08), no un campo nuevo inventado.
        bitacora.anexar(
            EventoAuditoria(
                actor = entrada.modeloId,
                fecha = fecha,
                tipo = "SUGERENCIA_RECIBIDA",
                estadoAnterior = null,
                estadoPosterior = "SUGERENCIA_RECIBIDA",
            ),
        )
        return sugerencia
    }

    fun sugerenciasDe(documentoId: String): List<Sugerencia> = almacen.de(documentoId)

    // RF-VH-001: la cola de revisión de Validación Humana se construye sobre
    // esto — todas las sugerencias de todos los documentos sin clasificar
    // todavía, sin importar cuál los generó.
    fun sugerenciasPendientes(): List<Sugerencia> = custodia.documentosSinClasificar().flatMap { sugerenciasDe(it.id) }
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

interface AlmacenDeTrd {
    fun guardar(trd: Trd)
    fun buscar(version: Int): Trd?
}

class AlmacenDeTrdEnMemoria : AlmacenDeTrd {
    private val versiones = mutableMapOf<Int, Trd>()
    override fun guardar(trd: Trd) {
        versiones[trd.version] = trd
    }
    override fun buscar(version: Int): Trd? = versiones[version]
}

class PublicacionDeTrdRechazadaException(mensaje: String) : RuntimeException(mensaje)

// RF-RC-006: registro de versiones publicadas de la TRD. Publicar una nueva
// versión solo añade una entrada; nunca sobrescribe ni retira una versión
// anterior, así que toda `Clasificacion` que referencia una versión ya
// publicada sigue resolviendo contra esa misma versión después de que se
// publique una nueva. La comprobación de "ya existe" vive aquí, en el
// dominio, y no en cada `AlmacenDeTrd` (P-03): así la garantía es la misma
// sin importar la implementación del puerto (en memoria o Postgres, T-19 —
// corrige VETO de Codex, que encontró que la implementación JPA sobrescribía
// una versión ya publicada porque nunca llegaba a rechazarla).
class RegistroTrd(private val almacen: AlmacenDeTrd = AlmacenDeTrdEnMemoria()) {

    fun publicar(trd: Trd) {
        if (almacen.buscar(trd.version) != null) {
            throw PublicacionDeTrdRechazadaException(
                "La versión ${trd.version} de la TRD ya fue publicada; no se puede sobrescribir.",
            )
        }
        almacen.guardar(trd)
    }

    fun version(numero: Int): Trd = almacen.buscar(numero).oClaveFaltante(numero.toString())
}
