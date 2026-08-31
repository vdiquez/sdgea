package sgdea.contexts.seguridadacceso.persistencia

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

// specs/spec-infra-servicios.md §5: "Rol (nombre, permisos) -> tabla roles, con
// permisos serializado a JSON" — mismo tratamiento que evidencia/series en
// records-custodia: estructura anidada de longitud variable sin una tabla hija
// que ningún RF pide todavía. Un rol sí se redefine (RF-SA-002), así que este
// almacén usa el guardado normal de Spring Data, no EntityManager.persist.
@Entity
@Table(name = "roles")
class RolEntity(
    @Id
    var nombre: String = "",

    @Column(name = "permisos_json", nullable = false, columnDefinition = "text")
    var permisosJson: String = "[]",
)

// specs/spec-infra-servicios.md §5: "Identidad (id, actor, credencialHash,
// estado, roles) -> tabla identidades, con roles como lista de nombres". `actor`
// es único porque `autenticar` busca por actor (RF-SA-001); `rolesJson` guarda
// solo los nombres, no los permisos completos, para no duplicar la definición
// del rol (que vive en `roles`) en cada identidad que lo tiene asignado.
@Entity
@Table(name = "identidades")
class IdentidadEntity(
    @Id
    var id: String = "",

    @Column(nullable = false, unique = true)
    var actor: String = "",

    @Column(name = "credencial_hash", nullable = false)
    var credencialHash: String = "",

    @Column(nullable = false)
    var estado: String = "ACTIVA",

    @Column(name = "roles_json", nullable = false, columnDefinition = "text")
    var rolesJson: String = "[]",
)

// specs/spec-infra-servicios.md §5: "EventoSeguridad -> tabla eventos_seguridad,
// de solo inserción" — mismo tratamiento que rc_eventos_auditoria en
// records-custodia (RF-RC-005): solo se escribe vía EntityManager.persist en el
// almacén (Almacenes.kt), nunca merge/update, para que un UPDATE sea físicamente
// imposible desde este contexto (RF-SA-005/RNF-SA-003).
@Entity
@Table(name = "eventos_seguridad")
class EventoSeguridadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var actor: String = "",

    @Column(nullable = false)
    var fecha: Instant = Instant.EPOCH,

    @Column(nullable = false)
    var tipo: String = "",

    var recurso: String? = null,
)

interface RolJpaRepository : JpaRepository<RolEntity, String>

interface IdentidadJpaRepository : JpaRepository<IdentidadEntity, String> {
    fun findByActor(actor: String): IdentidadEntity?
}
