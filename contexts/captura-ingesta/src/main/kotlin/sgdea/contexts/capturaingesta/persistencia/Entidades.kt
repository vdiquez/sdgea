package sgdea.contexts.capturaingesta.persistencia

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

// specs/spec-infra-servicios.md §3: "LoteIngesta (id, inventario) -> tabla
// lotes_ingesta". `inventario` (List<String>) se guarda serializado a JSON en
// una sola columna: es la forma más simple de representar una lista de
// longitud variable sin introducir una tabla hija adicional que la spec no
// pide; decisión de implementación, no una regla de negocio.
@Entity
@Table(name = "lotes_ingesta")
class LoteEntity(
    @Id
    var id: String = "",

    @Column(name = "inventario_json", nullable = false, columnDefinition = "text")
    var inventarioJson: String = "[]",

    @OneToMany(mappedBy = "lote", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<ItemIngestaEntity> = mutableListOf(),
)

// specs/spec-infra-servicios.md §3: "ItemIngesta (...) -> tabla items_ingesta,
// con lote_id como llave foránea a lotes_ingesta".
@Entity
@Table(name = "items_ingesta")
class ItemIngestaEntity(
    @Id
    var id: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    var lote: LoteEntity? = null,

    @Column(name = "artefacto_id", nullable = false)
    var artefactoId: String = "",

    @Column(name = "artefacto_nombre", nullable = false)
    var artefactoNombre: String = "",

    @Column(nullable = false)
    var estado: String = "",

    @Column(name = "procedencia_fuente", nullable = false)
    var procedenciaFuente: String = "",

    @Column(name = "procedencia_fecha", nullable = false)
    var procedenciaFecha: Instant = Instant.EPOCH,

    @Column(name = "procedencia_disparador", nullable = false)
    var procedenciaDisparador: String = "",

    @Column(name = "procedencia_lote_o_flujo_id", nullable = false)
    var procedenciaLoteOFlujoId: String = "",
)

interface LoteJpaRepository : JpaRepository<LoteEntity, String>
