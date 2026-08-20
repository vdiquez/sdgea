package sgdea.contexts.capturaingesta

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

// Ítem de ingesta: artefacto de origen + procedencia + estado (spec §3).
data class ItemIngesta(
    val id: String,
    val loteId: String,
    val artefacto: ArtefactoOrigen,
    val estado: EstadoItemIngesta,
)

data class LoteIngesta(
    val id: String,
    val inventario: InventarioOrigen,
    val items: List<ItemIngesta>,
)

// RF-CI-001: cargar un lote produce un ítem `Recibido` por cada artefacto.
fun cargarLote(
    loteId: String,
    artefactos: List<ArtefactoOrigen>,
    inventario: InventarioOrigen,
): LoteIngesta {
    val items = artefactos.map { artefacto ->
        ItemIngesta(
            id = UUID.randomUUID().toString(),
            loteId = loteId,
            artefacto = artefacto,
            estado = EstadoItemIngesta.RECIBIDO,
        )
    }
    return LoteIngesta(id = loteId, inventario = inventario, items = items)
}
