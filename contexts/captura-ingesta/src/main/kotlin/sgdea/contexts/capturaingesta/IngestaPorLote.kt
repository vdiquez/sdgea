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
// produce `cargarLote` (RF-CI-001). RECHAZADO/EN_CUARENTENA los produce
// `validar` (RF-CI-006); ENTREGADO queda declarado para que RF-CI-008 pueda
// contarlo, aunque la transición que lo alcanza (entrega de RF-CI-010)
// todavía no está implementada.
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
// `razonValidacion` queda `null` mientras el ítem no ha pasado por `validar`
// (RF-CI-006); una vez `Rechazado` o `En cuarentena`, registra la razón —
// RF-CI-006 exige que nunca se descarte en silencio.
data class ItemIngesta(
    val id: String,
    val loteId: String,
    val artefacto: ArtefactoOrigen,
    val estado: EstadoItemIngesta,
    val procedencia: Procedencia,
    val razonValidacion: String? = null,
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

// RF-CI-002: reporte de conciliación de un lote contra su inventario de
// origen (spec §2/§3, invariante 4). `faltantes` son los registros del
// inventario que no tienen ítem recibido con ese id de artefacto; `sobrantes`
// son los ítems recibidos cuyo artefacto no aparece en el inventario.
data class ReporteConciliacion(
    val faltantes: List<String>,
    val sobrantes: List<ItemIngesta>,
)

fun conciliar(lote: LoteIngesta): ReporteConciliacion {
    val idsRecibidos = lote.items.map { it.artefacto.id }.toSet()
    val idsInventario = lote.inventario.registros.toSet()
    return ReporteConciliacion(
        faltantes = lote.inventario.registros.filter { it !in idsRecibidos },
        sobrantes = lote.items.filter { it.artefacto.id !in idsInventario },
    )
}

// RF-CI-006: condiciones de validación que llevan a una rama terminal.
// Taxonomía resuelta por Victor en QUESTIONS.md (2026-08-23), antes bloqueada
// por [CLARIFICAR] en spec-captura-ingesta.md §8: si un humano puede
// destrabar el mismo artefacto dentro del sistema actual, la condición va a
// EN_CUARENTENA; si la única salida es un artefacto distinto o un cambio de
// sistema, va a RECHAZADO. Sin gradación de severidad adicional que la spec
// no define.
enum class CondicionValidacion {
    CORRUPTO,
    ILEGIBLE,
    FORMATO_NO_SOPORTADO,
}

// RF-CI-006: valida un ítem `Recibido` y lo mueve a su rama terminal con
// razón registrada — nunca se descarta en silencio.
fun validar(item: ItemIngesta, condicion: CondicionValidacion): ItemIngesta {
    val (estado, razon) = when (condicion) {
        CondicionValidacion.CORRUPTO ->
            EstadoItemIngesta.EN_CUARENTENA to "Artefacto corrupto: requiere reescaneo o confirmación manual."
        CondicionValidacion.ILEGIBLE ->
            EstadoItemIngesta.EN_CUARENTENA to "Artefacto ilegible: requiere juicio de calidad humano."
        CondicionValidacion.FORMATO_NO_SOPORTADO ->
            EstadoItemIngesta.RECHAZADO to "Formato no soportado: requiere un artefacto nuevo o soporte de formato añadido al sistema."
    }
    return item.copy(estado = estado, razonValidacion = razon)
}
