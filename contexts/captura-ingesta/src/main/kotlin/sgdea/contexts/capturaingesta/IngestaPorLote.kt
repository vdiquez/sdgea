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

enum class EstadoItemIngesta {
    RECIBIDO,
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
