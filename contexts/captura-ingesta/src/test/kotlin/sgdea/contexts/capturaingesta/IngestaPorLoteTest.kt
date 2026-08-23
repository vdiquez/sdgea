package sgdea.contexts.capturaingesta

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
// lógica de validación (RF-CI-006, ver ValidacionYCuarentenaTest) y de
// entrega (RF-CI-010, aún sin implementar). Aquí se construyen ítems ya en
// sus estados terminales/no terminales para probar únicamente el invariante
// de conteo del §3 de la spec, sin acoplar esta prueba a `validar`.
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

// RF-CI-006 · Validación y cuarentena
// Dado un artefacto corrupto/ilegible/de formato no soportado, Cuando se
// valida, Entonces el ítem queda `En cuarentena` o `Rechazado` con razón
// registrada. Taxonomía de condición -> rama terminal resuelta por Victor en
// QUESTIONS.md (2026-08-23), antes bloqueada por [CLARIFICAR]: recuperable
// dentro del sistema actual -> En cuarentena; solo recuperable con un
// artefacto distinto o un cambio de sistema -> Rechazado.
class ValidacionYCuarentenaTest {

    private val fecha = Instant.parse("2026-08-23T10:00:00Z")

    private fun itemRecibido(): ItemIngesta {
        val lote = cargarLote(
            loteId = "lote-001",
            artefactos = listOf(ArtefactoOrigen(id = "a1", nombre = "expediente-001.pdf")),
            inventario = InventarioOrigen(registros = listOf("a1")),
            fuente = "escaner-sala-3",
            fecha = fecha,
        )
        return lote.items.single()
    }

    @Test
    fun `un artefacto corrupto queda En cuarentena con razon registrada`() {
        val itemValidado = validar(itemRecibido(), CondicionValidacion.CORRUPTO)

        assertEquals(EstadoItemIngesta.EN_CUARENTENA, itemValidado.estado)
        assertTrue(itemValidado.razonValidacion?.isNotBlank() == true)
    }

    @Test
    fun `un artefacto ilegible queda En cuarentena con razon registrada`() {
        val itemValidado = validar(itemRecibido(), CondicionValidacion.ILEGIBLE)

        assertEquals(EstadoItemIngesta.EN_CUARENTENA, itemValidado.estado)
        assertTrue(itemValidado.razonValidacion?.isNotBlank() == true)
    }

    @Test
    fun `un artefacto de formato no soportado queda Rechazado con razon registrada`() {
        val itemValidado = validar(itemRecibido(), CondicionValidacion.FORMATO_NO_SOPORTADO)

        assertEquals(EstadoItemIngesta.RECHAZADO, itemValidado.estado)
        assertTrue(itemValidado.razonValidacion?.isNotBlank() == true)
    }

    @Test
    fun `validar conserva la identidad y procedencia del item, solo cambia estado y razon`() {
        val original = itemRecibido()

        val itemValidado = validar(original, CondicionValidacion.CORRUPTO)

        assertEquals(original.id, itemValidado.id)
        assertEquals(original.loteId, itemValidado.loteId)
        assertEquals(original.artefacto, itemValidado.artefacto)
        assertEquals(original.procedencia, itemValidado.procedencia)
    }
}
