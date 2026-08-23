package sgdea.contexts.capturaingesta.http

import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sgdea.contexts.capturaingesta.ArtefactoOrigen
import sgdea.contexts.capturaingesta.CondicionValidacion
import sgdea.contexts.capturaingesta.ConteoPorEstado
import sgdea.contexts.capturaingesta.InventarioOrigen
import sgdea.contexts.capturaingesta.ItemIngesta
import sgdea.contexts.capturaingesta.LoteIngesta
import sgdea.contexts.capturaingesta.ReporteConciliacion
import sgdea.contexts.capturaingesta.cargarLote
import sgdea.contexts.capturaingesta.conciliar
import sgdea.contexts.capturaingesta.contarPorEstado
import sgdea.contexts.capturaingesta.persistencia.LoteIngestaRepositorio
import sgdea.contexts.capturaingesta.validar

// specs/spec-infra-servicios.md §3 · Contrato mínimo — captura-ingesta.
// Cada endpoint traduce uno a uno una función de dominio ya implementada y
// probada por TDD (T-01, T-05, T-06); esta clase no añade regla de negocio
// alguna, solo entrada/salida HTTP y el punto de persistencia entre
// peticiones que exige la spec.
data class ArtefactoDto(val id: String, val nombre: String)

data class CargarLoteRequest(
    val loteId: String,
    val artefactos: List<ArtefactoDto>,
    val inventario: List<String>,
    val fuente: String,
    val fecha: Instant,
)

data class ValidarItemRequest(val condicion: CondicionValidacion)

@RestController
@RequestMapping("/lotes")
class LotesController(
    private val repositorio: LoteIngestaRepositorio,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun cargar(@RequestBody request: CargarLoteRequest): LoteIngesta {
        val lote = cargarLote(
            loteId = request.loteId,
            artefactos = request.artefactos.map { ArtefactoOrigen(id = it.id, nombre = it.nombre) },
            inventario = InventarioOrigen(request.inventario),
            fuente = request.fuente,
            fecha = request.fecha,
        )
        repositorio.guardar(lote)
        return lote
    }

    @GetMapping("/{loteId}/conteo")
    fun conteo(@PathVariable loteId: String): ConteoPorEstado {
        val lote = buscarOFallar(loteId)
        return contarPorEstado(lote)
    }

    @GetMapping("/{loteId}/conciliacion")
    fun conciliacion(@PathVariable loteId: String): ReporteConciliacion {
        val lote = buscarOFallar(loteId)
        return conciliar(lote)
    }

    // RF-CI-006 (specs/spec-infra-servicios.md §3): traduce `validar` sobre
    // el ítem indicado y persiste el lote con ese ítem actualizado.
    @PostMapping("/{loteId}/items/{itemId}/validacion")
    fun validarItem(
        @PathVariable loteId: String,
        @PathVariable itemId: String,
        @RequestBody request: ValidarItemRequest,
    ): ItemIngesta {
        val lote = buscarOFallar(loteId)
        val item = lote.items.find { it.id == itemId }
            ?: throw NoSuchElementException("Ítem no encontrado: $itemId")
        val itemValidado = validar(item, request.condicion)
        val loteActualizado = lote.copy(
            items = lote.items.map { if (it.id == itemId) itemValidado else it },
        )
        repositorio.guardar(loteActualizado)
        return itemValidado
    }

    // specs/spec-infra-servicios.md §5 (T-19, corrige VETO de Codex): antes
    // usaba ResponseStatusException, que produce un cuerpo distinto al de
    // records-custodia. NoSuchElementException + ManejoDeErrores es la misma
    // excepción de dominio y el mismo cuerpo {"error": ...} que usa
    // records-custodia para "id no encontrado" — una sola convención de
    // error aplicada en los dos contextos.
    private fun buscarOFallar(loteId: String): LoteIngesta =
        repositorio.buscar(loteId) ?: throw NoSuchElementException("Lote no encontrado: $loteId")
}
