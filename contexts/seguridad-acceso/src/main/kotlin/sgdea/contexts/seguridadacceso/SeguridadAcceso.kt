package sgdea.contexts.seguridadacceso

import java.security.MessageDigest
import java.time.Instant

// RF-SA-004: niveles de clasificación de la información (Ley 1712 de 2014).
// Orden creciente de restricción, expresado por el orden de declaración del enum
// (Kotlin lo usa para `ordinal`/`compareTo`): PUBLICA < CLASIFICADA < RESERVADA.
enum class NivelClasificacion {
    PUBLICA,
    CLASIFICADA,
    RESERVADA,
}

enum class EstadoIdentidad {
    ACTIVA,
    SUSPENDIDA,
}

enum class ResultadoAutorizacion {
    PERMITIDO,
    DENEGADO,
}

// specs/006-seguridad-acceso/spec.md §3: un permiso autoriza una acción sobre un
// tipo de recurso, hasta un nivel de clasificación máximo que cubre.
data class Permiso(
    val accion: String,
    val tipoRecurso: String,
    val nivelClasificacionMaximo: NivelClasificacion = NivelClasificacion.RESERVADA,
) {
    fun cubre(accion: String, tipoRecurso: String, nivel: NivelClasificacion): Boolean =
        this.accion == accion && this.tipoRecurso == tipoRecurso && nivel.ordinal <= nivelClasificacionMaximo.ordinal
}

data class Rol(
    val nombre: String,
    val permisos: List<Permiso> = emptyList(),
)

data class Identidad(
    val id: String,
    val actor: String,
    val credencialHash: String,
    val roles: List<Rol> = emptyList(),
    val estado: EstadoIdentidad = EstadoIdentidad.ACTIVA,
)

// RF-SA-005/RF-SA-010: evento de seguridad — autenticación o autorización, nunca
// una transición de estado de documento (eso es P-08 en cada contexto de dominio;
// esto es el mismo principio aplicado al acceso). Deliberadamente no tiene ningún
// campo de credencial (RF-SA-007): no puede filtrar lo que no declara.
data class EventoSeguridad(
    val actor: String,
    val fecha: Instant,
    val tipo: String,
    val recurso: String? = null,
)

class CredencialesInvalidasException(mensaje: String) : RuntimeException(mensaje)

class IdentidadSuspendidaException(mensaje: String) : RuntimeException(mensaje)

private fun <T> T?.oFaltante(id: String): T = this ?: throw NoSuchElementException("No encontrado: $id")

interface AlmacenDeEventosDeSeguridad {
    fun anexar(evento: EventoSeguridad)
    fun todos(): List<EventoSeguridad>
}

class AlmacenDeEventosDeSeguridadEnMemoria : AlmacenDeEventosDeSeguridad {
    private val eventos = mutableListOf<EventoSeguridad>()
    override fun anexar(evento: EventoSeguridad) {
        eventos.add(evento)
    }
    override fun todos(): List<EventoSeguridad> = eventos.toList()
}

// RF-SA-005/RNF-SA-003: bitácora de solo anexado para eventos de seguridad — mismo
// tratamiento que BitacoraAuditoria en records-custodia (RF-RC-005), aplicado aquí
// a autenticación/autorización en vez de a una transición de estado de documento.
class BitacoraSeguridad(private val almacen: AlmacenDeEventosDeSeguridad = AlmacenDeEventosDeSeguridadEnMemoria()) {
    val todos: List<EventoSeguridad> get() = almacen.todos()
    fun anexar(evento: EventoSeguridad) = almacen.anexar(evento)
}

interface AlmacenDeIdentidades {
    fun guardar(identidad: Identidad)
    fun buscar(id: String): Identidad?
    fun buscarPorActor(actor: String): Identidad?
}

class AlmacenDeIdentidadesEnMemoria : AlmacenDeIdentidades {
    private val identidades = mutableMapOf<String, Identidad>()
    override fun guardar(identidad: Identidad) {
        identidades[identidad.id] = identidad
    }
    override fun buscar(id: String): Identidad? = identidades[id]
    override fun buscarPorActor(actor: String): Identidad? = identidades.values.find { it.actor == actor }
}

interface AlmacenDeRoles {
    fun guardar(rol: Rol)
    fun buscar(nombre: String): Rol?
}

class AlmacenDeRolesEnMemoria : AlmacenDeRoles {
    private val roles = mutableMapOf<String, Rol>()
    override fun guardar(rol: Rol) {
        roles[rol.nombre] = rol
    }
    override fun buscar(nombre: String): Rol? = roles[nombre]
}

fun huellaSha256(valor: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(valor.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

// RF-SA-002: alta, modificación y baja de roles y permisos sin cambios de código —
// son datos que este almacén guarda, no código nuevo por cada rol.
class GestionDeRoles(private val almacen: AlmacenDeRoles = AlmacenDeRolesEnMemoria()) {
    fun crear(nombre: String, permisos: List<Permiso>): Rol {
        val rol = Rol(nombre = nombre, permisos = permisos)
        almacen.guardar(rol)
        return rol
    }
    fun buscar(nombre: String): Rol = almacen.buscar(nombre).oFaltante(nombre)
}

// RF-SA-001/003/004/006/007/009: autenticación, gestión de identidades y decisión
// de autorización. Cada operación pública toca como máximo un almacén (identidades
// O bitácora, nunca ambos en la misma llamada) — a diferencia de
// CustodiaOriginales.custodiar/materializar (records-custodia), que necesitaron un
// wrapper @Transactional (T-22) porque escribían en varios almacenes JPA
// independientes a la vez. Aquí no hace falta ese wrapper porque el diseño evita
// desde el principio la escritura múltiple: asignar/revocar un rol no genera un
// evento de seguridad (RF-SA-005 solo exige evento para autenticación y decisiones
// de autorización, no para cambios de rol), así que no hay una segunda escritura
// que pueda quedar huérfana.
//
// `hashCredencial` es el seam mínimo para no acoplar el dominio a un algoritmo de
// hashing concreto: por defecto usa SHA-256 (mismo algoritmo que
// CustodiaOriginales.ALGORITMO_HUELLA), suficiente para Etapa 0/1 — spec §8 deja
// pendiente si se necesita un hash de contraseñas más robusto (bcrypt/Argon2) más
// adelante; cambiarlo no altera el contrato de esta clase.
//
// RF-SA-009 se cumple por construcción: ninguna operación de esta clase hace una
// llamada de red a un servicio externo.
class GestionDeAccesos(
    private val almacenDeIdentidades: AlmacenDeIdentidades = AlmacenDeIdentidadesEnMemoria(),
    private val bitacora: BitacoraSeguridad = BitacoraSeguridad(),
    private val hashCredencial: (String) -> String = ::huellaSha256,
) {
    val eventosDeSeguridad: List<EventoSeguridad> get() = bitacora.todos

    fun crearIdentidad(id: String, actor: String, credencial: String, roles: List<Rol> = emptyList()): Identidad {
        val identidad = Identidad(id = id, actor = actor, credencialHash = hashCredencial(credencial), roles = roles)
        almacenDeIdentidades.guardar(identidad)
        return identidad
    }

    // RF-SA-001: autentica solo si el actor existe, la credencial coincide con su
    // huella y la identidad no está suspendida. RF-SA-010: todo intento, exitoso o
    // no, anexa un evento — nunca se descarta en silencio.
    fun autenticar(actor: String, credencial: String, fecha: Instant): Identidad {
        val identidad = almacenDeIdentidades.buscarPorActor(actor)
        if (identidad == null || identidad.credencialHash != hashCredencial(credencial)) {
            bitacora.anexar(EventoSeguridad(actor = actor, fecha = fecha, tipo = "AUTENTICACION_FALLIDA"))
            throw CredencialesInvalidasException("Credenciales inválidas para '$actor'.")
        }
        if (identidad.estado == EstadoIdentidad.SUSPENDIDA) {
            bitacora.anexar(EventoSeguridad(actor = actor, fecha = fecha, tipo = "AUTENTICACION_FALLIDA"))
            throw IdentidadSuspendidaException("La identidad '$actor' está suspendida.")
        }
        bitacora.anexar(EventoSeguridad(actor = actor, fecha = fecha, tipo = "AUTENTICACION_EXITOSA"))
        return identidad
    }

    fun asignarRol(identidadId: String, rol: Rol) {
        val identidad = almacenDeIdentidades.buscar(identidadId).oFaltante(identidadId)
        almacenDeIdentidades.guardar(identidad.copy(roles = identidad.roles + rol))
    }

    // RF-SA-006: revocar surte efecto de inmediato porque `autorizar` siempre lee
    // el estado vigente de la identidad — no hay ninguna caché de la decisión que
    // pueda quedar desactualizada.
    fun revocarRol(identidadId: String, nombreRol: String) {
        val identidad = almacenDeIdentidades.buscar(identidadId).oFaltante(identidadId)
        almacenDeIdentidades.guardar(identidad.copy(roles = identidad.roles.filterNot { it.nombre == nombreRol }))
    }

    // RF-SA-003/004/008: deniega por defecto; solo permite si algún rol vigente de
    // la identidad tiene un permiso que cubre la acción, el tipo de recurso y el
    // nivel de clasificación solicitados. Expuesta para que cualquier otro
    // contexto la invoque y aplique su propio filtrado (RF-SA-008,
    // spec-infra-servicios.md §7, RF-IB-008).
    fun autorizar(
        identidadId: String,
        accion: String,
        tipoRecurso: String,
        nivelClasificacion: NivelClasificacion = NivelClasificacion.PUBLICA,
        recurso: String? = null,
        fecha: Instant,
    ): ResultadoAutorizacion {
        val identidad = almacenDeIdentidades.buscar(identidadId)
        val permitido = identidad != null &&
            identidad.estado == EstadoIdentidad.ACTIVA &&
            identidad.roles.any { rol -> rol.permisos.any { it.cubre(accion, tipoRecurso, nivelClasificacion) } }
        val resultado = if (permitido) ResultadoAutorizacion.PERMITIDO else ResultadoAutorizacion.DENEGADO
        bitacora.anexar(
            EventoSeguridad(
                actor = identidad?.actor ?: identidadId,
                fecha = fecha,
                tipo = if (permitido) "AUTORIZACION_PERMITIDA" else "AUTORIZACION_DENEGADA",
                recurso = recurso,
            ),
        )
        return resultado
    }
}
