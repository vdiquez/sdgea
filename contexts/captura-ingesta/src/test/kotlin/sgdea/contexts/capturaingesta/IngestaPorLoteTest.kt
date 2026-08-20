package sgdea.contexts.capturaingesta

import kotlin.test.Test
import kotlin.test.assertEquals

// RF-CI-001 · Ingesta por lote para fondos acumulados
// Dado un lote con artefactos e inventario, Cuando se carga,
// Entonces cada artefacto produce un ítem de ingesta en estado `Recibido`.
class IngestaPorLoteTest {

    @Test
    fun `cargar un lote con artefactos e inventario produce un item Recibido por artefacto`() {
        val artefactos = listOf(
            ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf"),
            ArtefactoOrigen(id = "a2", nombre = "expediente-002.tif"),
            ArtefactoOrigen(id = "a3", nombre = "expediente-003.docx"),
        )
        val inventario = InventarioOrigen(registros = listOf("a1", "a2", "a3"))

        val lote = cargarLote(loteId = "lote-001", artefactos = artefactos, inventario = inventario)

        assertEquals(artefactos.size, lote.items.size)
        artefactos.forEach { artefacto ->
            val item = lote.items.single { it.artefacto == artefacto }
            assertEquals(EstadoItemIngesta.RECIBIDO, item.estado)
        }
    }

    @Test
    fun `cada item de ingesta conserva referencia a su artefacto de origen y a su lote`() {
        val artefacto = ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf")

        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = listOf(artefacto),
            inventario = InventarioOrigen(registros = listOf("a1")),
        )

        val item = lote.items.single()
        assertEquals(artefacto, item.artefacto)
        assertEquals(lote.id, item.loteId)
    }

    @Test
    fun `un lote sin artefactos no produce items`() {
        val lote = cargarLote(
            loteId = "lote-vacio",
            artefactos = emptyList(),
            inventario = InventarioOrigen(registros = emptyList()),
        )

        assertEquals(emptyList(), lote.items)
    }
}
