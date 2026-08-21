package sgdea.contexts.capturaingesta

import java.time.Instant
import java.util.UUID

// Artefacto de origen: el fichero tal como llega, sin interpretar (spec §2).
data class ArtefactoOrigen(
    val id: String,
    val nombre: String,
)

// Inventario de origen: lo que el lote declara contener (spec §2). La
// conciliación contra estos registros es RF-CI-002, fuera del alcance de
// RF-CI-001.
data class InventarioOrigen(
    val registros: List<String>,
)

// Estados del ítem de ingesta (spec §3). RECIBIDO es el único estado que
// produce `cargarLote` (RF-CI-001); los demás se declaran aquí porque
// RF-CI-008 exige poder contarlos, aunque las transiciones que los alcanzan
// (validación/cuarentena de RF-CI-006, entrega de RF-CI-010) todavía no están
// implementadas — RF-CI-006 sigue bloqueada por [CLARIFICAR] en la spec.
enum class EstadoItemIngesta {
    RECIBIDO,
    ENTREGADO,
    RECHAZADO,
    EN_CUARENTENA,
}

// Procedencia de un ítem de ingesta (spec §2/§3, RF-CI-007): fuente, fecha,
// disparador y el identificador del lote o flujo de origen.
data class Procedencia(
    val fuente: String,
    val fecha: Instant,
    val disparador: String,
    val loteOFlujoId: String,
)

// Ítem de ingesta: artefacto de origen + procedencia + estado (spec §3).
data class ItemIngesta(
    val id: String,
    val loteId: String,
    val artefacto: ArtefactoOrigen,
    val estado: EstadoItemIngesta,
    val procedencia: Procedencia,
)

data class LoteIngesta(
    val id: String,
    val inventario: InventarioOrigen,
    val items: List<ItemIngesta>,
)

// RF-CI-001: cargar un lote produce un ítem `Recibido` por cada artefacto.
// RF-CI-007: cada ítem registra su procedencia completa (fuente, fecha,
// disparador y lote de origen); "carga_por_lote" es el disparador de este
// caso de uso, tal como lo nombra la entrada "Carga de un lote" del §4 de la
// spec — no es una política de negocio inventada.
fun cargarLote(
    loteId: String,
    artefactos: List<ArtefactoOrigen>,
    inventario: InventarioOrigen,
    fuente: String,
    fecha: Instant,
): LoteIngesta {
    val items = artefactos.map { artefacto ->
        ItemIngesta(
            id = UUID.randomUUID().toString(),
            loteId = loteId,
            artefacto = artefacto,
            estado = EstadoItemIngesta.RECIBIDO,
            procedencia = Procedencia(
                fuente = fuente,
                fecha = fecha,
                disparador = "carga_por_lote",
                loteOFlujoId = loteId,
            ),
        )
    }
    return LoteIngesta(id = loteId, inventario = inventario, items = items)
}

// RF-CI-008: invariante "cero pérdida silenciosa" — la cuenta de ítems en un
// estado terminal (Entregado, Rechazado, En cuarentena) debe igualar el total
// de ítems recibidos en el lote. Si no cuadra, hay ítems atascados en un
// estado no terminal (procesamiento incompleto) o ausentes.
data class ConteoPorEstado(
    val porEstado: Map<EstadoItemIngesta, Int>,
    val total: Int,
) {
    val terminales: Int
        get() = (porEstado[EstadoItemIngesta.ENTREGADO] ?: 0) +
            (porEstado[EstadoItemIngesta.RECHAZADO] ?: 0) +
            (porEstado[EstadoItemIngesta.EN_CUARENTENA] ?: 0)

    val sinPerdidaSilenciosa: Boolean
        get() = terminales == total
}

fun contarPorEstado(lote: LoteIngesta): ConteoPorEstado {
    val porEstado = lote.items.groupingBy { it.estado }.eachCount()
    return ConteoPorEstado(porEstado = porEstado, total = lote.items.size)
}
