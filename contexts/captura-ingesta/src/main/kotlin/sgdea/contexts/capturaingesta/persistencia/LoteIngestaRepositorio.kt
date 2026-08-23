package sgdea.contexts.capturaingesta.persistencia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import sgdea.contexts.capturaingesta.ArtefactoOrigen
import sgdea.contexts.capturaingesta.EstadoItemIngesta
import sgdea.contexts.capturaingesta.InventarioOrigen
import sgdea.contexts.capturaingesta.ItemIngesta
import sgdea.contexts.capturaingesta.LoteIngesta
import sgdea.contexts.capturaingesta.Procedencia

// specs/spec-infra-servicios.md §3: "exponerlas por HTTP exige que el
// LoteIngesta que produce cargarLote se persista para que
// contarPorEstado/conciliar puedan operar sobre el mismo lote en una
// petición posterior". Este componente es el único punto que traduce entre
// el agregado de dominio (inmutable, sin anotaciones JPA) y las entidades de
// persistencia — el dominio no sabe que existe Postgres.
@Component
class LoteIngestaRepositorio(
    private val jpaRepository: LoteJpaRepository,
    private val objectMapper: ObjectMapper,
) {

    fun guardar(lote: LoteIngesta) {
        val entity = LoteEntity(
            id = lote.id,
            inventarioJson = objectMapper.writeValueAsString(lote.inventario.registros),
        )
        entity.items = lote.items.map { item ->
            ItemIngestaEntity(
                id = item.id,
                lote = entity,
                artefactoId = item.artefacto.id,
                artefactoNombre = item.artefacto.nombre,
                estado = item.estado.name,
                procedenciaFuente = item.procedencia.fuente,
                procedenciaFecha = item.procedencia.fecha,
                procedenciaDisparador = item.procedencia.disparador,
                procedenciaLoteOFlujoId = item.procedencia.loteOFlujoId,
                razonValidacion = item.razonValidacion,
            )
        }.toMutableList()
        jpaRepository.save(entity)
    }

    fun buscar(loteId: String): LoteIngesta? {
        val entity = jpaRepository.findById(loteId).orElse(null) ?: return null
        val registros: List<String> = objectMapper.readValue(entity.inventarioJson)
        val items = entity.items.map { itemEntity ->
            ItemIngesta(
                id = itemEntity.id,
                loteId = entity.id,
                artefacto = ArtefactoOrigen(id = itemEntity.artefactoId, nombre = itemEntity.artefactoNombre),
                estado = EstadoItemIngesta.valueOf(itemEntity.estado),
                procedencia = Procedencia(
                    fuente = itemEntity.procedenciaFuente,
                    fecha = itemEntity.procedenciaFecha,
                    disparador = itemEntity.procedenciaDisparador,
                    loteOFlujoId = itemEntity.procedenciaLoteOFlujoId,
                ),
                razonValidacion = itemEntity.razonValidacion,
            )
        }
        return LoteIngesta(id = entity.id, inventario = InventarioOrigen(registros), items = items)
    }
}
