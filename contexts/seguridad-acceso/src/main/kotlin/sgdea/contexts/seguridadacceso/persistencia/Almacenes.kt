package sgdea.contexts.seguridadacceso.persistencia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import sgdea.contexts.seguridadacceso.AlmacenDeEventosDeSeguridad
import sgdea.contexts.seguridadacceso.AlmacenDeIdentidades
import sgdea.contexts.seguridadacceso.AlmacenDeRoles
import sgdea.contexts.seguridadacceso.EstadoIdentidad
import sgdea.contexts.seguridadacceso.EventoSeguridad
import sgdea.contexts.seguridadacceso.Identidad
import sgdea.contexts.seguridadacceso.Permiso
import sgdea.contexts.seguridadacceso.Rol

// specs/spec-infra-servicios.md §5: implementación Postgres de los puertos
// declarados en SeguridadAcceso.kt (P-03: el dominio desconoce cuál
// implementación de almacenamiento está activa).
@Component
@Transactional
class AlmacenDeRolesJpa(
    private val jpaRepository: RolJpaRepository,
    private val objectMapper: ObjectMapper,
) : AlmacenDeRoles {

    override fun guardar(rol: Rol) {
        jpaRepository.save(RolEntity(nombre = rol.nombre, permisosJson = objectMapper.writeValueAsString(rol.permisos)))
    }

    override fun buscar(nombre: String): Rol? =
        jpaRepository.findById(nombre).orElse(null)?.let {
            Rol(nombre = it.nombre, permisos = objectMapper.readValue<List<Permiso>>(it.permisosJson))
        }
}

// `rolesJpaRepository` resuelve cada nombre de rol guardado en `rolesJson` contra
// la tabla `roles` para reconstruir la `Identidad` completa (con sus permisos),
// evitando duplicar la definición del rol en cada identidad.
@Component
@Transactional
class AlmacenDeIdentidadesJpa(
    private val jpaRepository: IdentidadJpaRepository,
    private val rolesJpaRepository: RolJpaRepository,
    private val objectMapper: ObjectMapper,
) : AlmacenDeIdentidades {

    override fun guardar(identidad: Identidad) {
        jpaRepository.save(
            IdentidadEntity(
                id = identidad.id,
                actor = identidad.actor,
                credencialHash = identidad.credencialHash,
                estado = identidad.estado.name,
                rolesJson = objectMapper.writeValueAsString(identidad.roles.map { it.nombre }),
            ),
        )
    }

    override fun buscar(id: String): Identidad? = jpaRepository.findById(id).orElse(null)?.toDominio()

    override fun buscarPorActor(actor: String): Identidad? = jpaRepository.findByActor(actor)?.toDominio()

    private fun IdentidadEntity.toDominio(): Identidad {
        val nombresDeRoles = objectMapper.readValue<List<String>>(rolesJson)
        val roles = nombresDeRoles.mapNotNull { nombre ->
            rolesJpaRepository.findById(nombre).orElse(null)?.let {
                Rol(nombre = it.nombre, permisos = objectMapper.readValue<List<Permiso>>(it.permisosJson))
            }
        }
        return Identidad(
            id = id,
            actor = actor,
            credencialHash = credencialHash,
            roles = roles,
            estado = EstadoIdentidad.valueOf(estado),
        )
    }
}

// Mismo tratamiento de solo-inserción que AlmacenDeEventosJpa en records-custodia
// (RF-RC-005): la bitácora de seguridad es de solo anexado (RF-SA-005/RNF-SA-003),
// así que `anexar` nunca debe poder convertirse en un UPDATE.
@Component
@Transactional
class AlmacenDeEventosDeSeguridadJpa : AlmacenDeEventosDeSeguridad {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun anexar(evento: EventoSeguridad) {
        entityManager.persist(
            EventoSeguridadEntity(actor = evento.actor, fecha = evento.fecha, tipo = evento.tipo, recurso = evento.recurso),
        )
    }

    override fun todos(): List<EventoSeguridad> =
        entityManager.createQuery("select e from EventoSeguridadEntity e order by e.id", EventoSeguridadEntity::class.java)
            .resultList
            .map { EventoSeguridad(actor = it.actor, fecha = it.fecha, tipo = it.tipo, recurso = it.recurso) }
}
