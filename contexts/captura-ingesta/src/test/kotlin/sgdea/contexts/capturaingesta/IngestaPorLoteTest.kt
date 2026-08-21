package sgdea.contexts.capturaingesta

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// RF-CI-001 · Ingesta por lote para fondos acumulados
// Dado un lote con artefactos e inventario, Cuando se carga,
// Entonces cada artefacto produce un ítem de ingesta en estado `Recibido`.
class IngestaPorLoteTest {

    private val fecha = Instant.parse("2026-08-20T10:00:00Z")

    @Test
    fun `cargar un lote con artefactos e inventario produce un item Recibido por artefacto`() {
        val artefactos = listOf(
            ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf"),
            ArtefactoOrigen(id = "a2", nombre = "expediente-002.tif"),
            ArtefactoOrigen(id = "a3", nombre = "expediente-003.docx"),
        )
        val inventario = InventarioOrigen(registros = listOf("a1", "a2", "a3"))

        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = artefactos,
            inventario = inventario,
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

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
            fuente = "escaner-sala-3",
            fecha = fecha,
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
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

        assertEquals(emptyList(), lote.items)
    }
}

// RF-CI-007 · Registro de procedencia
// Dado un ítem de ingesta, Cuando se consulta, Entonces expone su procedencia completa.
class RegistroDeProcedenciaTest {

    @Test
    fun `un item de ingesta expone su procedencia completa fuente fecha disparador y lote`() {
        val artefacto = ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf")
        val fecha = Instant.parse("2026-08-20T10:00:00Z")

        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = listOf(artefacto),
            inventario = InventarioOrigen(registros = listOf("a1")),
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

        val item = lote.items.single()
        assertEquals("escaner-sala-3", item.procedencia.fuente)
        assertEquals(fecha, item.procedencia.fecha)
        assertEquals("carga_por_lote", item.procedencia.disparador)
        assertEquals("lote-001", item.procedencia.loteOFlujoId)
    }
}

// RF-CI-008 · Cero pérdida silenciosa
// Dado un lote procesado, Cuando se suma por estado, Entonces la cuenta de
// `Entregado` + `Rechazado` + `En cuarentena` iguala el total de ítems
// recibidos.
//
// Las transiciones reales Recibido -> ... -> estado terminal las produce la
// lógica de validación/entrega de RF-CI-006/RF-CI-010 (fuera de alcance de
// esta tarea, T-02 sigue bloqueada por [CLARIFICAR]). Aquí se construyen
// ítems ya en sus estados terminales/no terminales para probar únicamente el
// invariante de conteo del §3 de la spec, sin inventar esa lógica de
// transición.
class CeroPerdidaSilenciosaTest {

    private val fecha = Instant.parse("2026-08-20T10:00:00Z")

    private fun item(id: String, estado: EstadoItemIngesta) = ItemIngesta(
        id = id,
        loteId = "lote-001",
        artefacto = ArtefactoOrigen(id = id, nombre = "$id.pdf"),
        estado = estado,
        procedencia = Procedencia(
            fuente = "escaner-sala-3",
            fecha = fecha,
            disparador = "carga_por_lote",
            loteOFlujoId = "lote-001",
        ),
    )

    @Test
    fun `un lote completamente procesado no pierde items la cuenta terminal iguala el total recibido`() {
        val lote = LoteIngesta(
            id = "lote-001",
            inventario = InventarioOrigen(registros = listOf("a1", "a2", "a3", "a4")),
            items = listOf(
                item("a1", EstadoItemIngesta.ENTREGADO),
                item("a2", EstadoItemIngesta.ENTREGADO),
                item("a3", EstadoItemIngesta.RECHAZADO),
                item("a4", EstadoItemIngesta.EN_CUARENTENA),
            ),
        )

        val conteo = contarPorEstado(lote)

        assertEquals(4, conteo.total)
        assertEquals(4, conteo.terminales)
        assertEquals(true, conteo.sinPerdidaSilenciosa)
    }

    @Test
    fun `un lote con un item aun no terminal no cuadra y delata perdida potencial`() {
        val lote = LoteIngesta(
            id = "lote-002",
            inventario = InventarioOrigen(registros = listOf("a1", "a2")),
            items = listOf(
                item("a1", EstadoItemIngesta.ENTREGADO),
                item("a2", EstadoItemIngesta.RECIBIDO),
            ),
        )

        val conteo = contarPorEstado(lote)

        assertEquals(2, conteo.total)
        assertEquals(1, conteo.terminales)
        assertEquals(false, conteo.sinPerdidaSilenciosa)
    }
}

// RF-CI-002 · Conciliación contra inventario
// Dado un lote conciliado, Cuando se consulta el reporte, Entonces lista los
// registros del inventario sin ítem recibido y los ítems sin registro en
// inventario.
class ConciliacionContraInventarioTest {

    private val fecha = Instant.parse("2026-08-20T10:00:00Z")

    @Test
    fun `un lote donde inventario e items recibidos coinciden no reporta faltantes ni sobrantes`() {
        val artefactos = listOf(
            ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf"),
            ArtefactoOrigen(id = "a2", nombre = "expediente-002.tif"),
        )
        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = artefactos,
            inventario = InventarioOrigen(registros = listOf("a1", "a2")),
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

        val reporte = conciliar(lote)

        assertEquals(emptyList(), reporte.faltantes)
        assertEquals(emptyList(), reporte.sobrantes)
    }

    @Test
    fun `un registro del inventario sin item recibido aparece como faltante`() {
        val artefactos = listOf(ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf"))
        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = artefactos,
            inventario = InventarioOrigen(registros = listOf("a1", "a2")),
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

        val reporte = conciliar(lote)

        assertEquals(listOf("a2"), reporte.faltantes)
        assertEquals(emptyList(), reporte.sobrantes)
    }

    @Test
    fun `un item recibido sin registro en inventario aparece como sobrante`() {
        val artefactos = listOf(
            ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf"),
            ArtefactoOrigen(id = "a2", nombre = "expediente-002.tif"),
        )
        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = artefactos,
            inventario = InventarioOrigen(registros = listOf("a1")),
            fuente = "escaner-sala-3",
            fecha = fecha,
        )

        val reporte = conciliar(lote)

        assertEquals(emptyList(), reporte.faltantes)
        val sobrante = reporte.sobrantes.single()
        assertEquals("a2", sobrante.artefacto.id)
    }
}
