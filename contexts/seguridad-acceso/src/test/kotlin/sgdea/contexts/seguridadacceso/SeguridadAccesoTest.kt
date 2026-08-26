package sgdea.contexts.seguridadacceso

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// RF-SA-001 · Autenticación de identidades
class AutenticacionDeIdentidadesTest {

    @Test
    fun `dado un actor con credenciales correctas, cuando se autentica, obtiene una identidad valida`() {
        val gestion = GestionDeAccesos()
        gestion.crearIdentidad(id = "id-1", actor = "archivista-1", credencial = "clave-correcta")

        val identidad = gestion.autenticar(actor = "archivista-1", credencial = "clave-correcta", fecha = Instant.parse("2026-08-25T00:00:00Z"))

        assertEquals("archivista-1", identidad.actor)
        assertTrue(gestion.eventosDeSeguridad.any { it.tipo == "AUTENTICACION_EXITOSA" && it.actor == "archivista-1" })
    }

    @Test
    fun `dado un actor con credenciales incorrectas, cuando se autentica, se rechaza y se registra el intento fallido`() {
        val gestion = GestionDeAccesos()
        gestion.crearIdentidad(id = "id-1", actor = "archivista-1", credencial = "clave-correcta")

        assertFailsWith<CredencialesInvalidasException> {
            gestion.autenticar(actor = "archivista-1", credencial = "clave-incorrecta", fecha = Instant.parse("2026-08-25T00:00:00Z"))
        }
        assertTrue(gestion.eventosDeSeguridad.any { it.tipo == "AUTENTICACION_FALLIDA" && it.actor == "archivista-1" })
    }

    @Test
    fun `dada una identidad suspendida con credenciales correctas, cuando se autentica, se rechaza`() {
        val almacen = AlmacenDeIdentidadesEnMemoria()
        val gestion = GestionDeAccesos(almacenDeIdentidades = almacen)
        gestion.crearIdentidad(id = "id-1", actor = "archivista-1", credencial = "clave-correcta")
        almacen.guardar(almacen.buscar("id-1")!!.copy(estado = EstadoIdentidad.SUSPENDIDA))

        assertFailsWith<IdentidadSuspendidaException> {
            gestion.autenticar(actor = "archivista-1", credencial = "clave-correcta", fecha = Instant.parse("2026-08-25T00:00:00Z"))
        }
        assertTrue(gestion.eventosDeSeguridad.any { it.tipo == "AUTENTICACION_FALLIDA" })
    }
}

// RF-SA-002 · Gestión de roles y permisos
class GestionDeRolesYPermisosTest {

    @Test
    fun `dado un nuevo rol con permisos, cuando se configura, queda disponible para asignarse sin cambios de codigo`() {
        val gestionDeRoles = GestionDeRoles()
        val permiso = Permiso(accion = "leer", tipoRecurso = "documento")

        val rol = gestionDeRoles.crear(nombre = "archivista", permisos = listOf(permiso))

        assertEquals("archivista", gestionDeRoles.buscar("archivista").nombre)
        assertEquals(listOf(permiso), rol.permisos)
    }
}

// RF-SA-003 · Autorización por defecto denegada
class AutorizacionPorDefectoDenegadaTest {

    private val fecha = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun `dada una identidad sin ningun rol, cuando solicita una accion, se deniega`() {
        val gestion = GestionDeAccesos()
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave")

        val resultado = gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha)

        assertEquals(ResultadoAutorizacion.DENEGADO, resultado)
    }

    @Test
    fun `dada una identidad con un rol que cubre la accion y el recurso, cuando la solicita, se permite`() {
        val gestion = GestionDeAccesos()
        val rol = Rol(nombre = "archivista", permisos = listOf(Permiso(accion = "leer", tipoRecurso = "documento")))
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave", roles = listOf(rol))

        val resultado = gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha)

        assertEquals(ResultadoAutorizacion.PERMITIDO, resultado)
    }

    @Test
    fun `dada una solicitud sin una regla de autorizacion que la permita explicitamente, se deniega`() {
        val gestion = GestionDeAccesos()
        val rol = Rol(nombre = "archivista", permisos = listOf(Permiso(accion = "leer", tipoRecurso = "documento")))
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave", roles = listOf(rol))

        val resultado = gestion.autorizar(identidadId = "id-1", accion = "borrar", tipoRecurso = "documento", fecha = fecha)

        assertEquals(ResultadoAutorizacion.DENEGADO, resultado)
    }
}

// RF-SA-004 · Clasificación de la información y su efecto en el acceso
class ClasificacionDeLaInformacionTest {

    private val fecha = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun `dado un recurso reservado, cuando una identidad sin permiso para ese nivel lo solicita, se deniega`() {
        val gestion = GestionDeAccesos()
        val rol = Rol(
            nombre = "consulta-publica",
            permisos = listOf(Permiso(accion = "leer", tipoRecurso = "documento", nivelClasificacionMaximo = NivelClasificacion.PUBLICA)),
        )
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave", roles = listOf(rol))

        val resultado = gestion.autorizar(
            identidadId = "id-1",
            accion = "leer",
            tipoRecurso = "documento",
            nivelClasificacion = NivelClasificacion.RESERVADA,
            fecha = fecha,
        )

        assertEquals(ResultadoAutorizacion.DENEGADO, resultado)
    }

    @Test
    fun `dado un recurso reservado, cuando una identidad con permiso hasta ese nivel lo solicita, se permite`() {
        val gestion = GestionDeAccesos()
        val rol = Rol(
            nombre = "archivista-senior",
            permisos = listOf(Permiso(accion = "leer", tipoRecurso = "documento", nivelClasificacionMaximo = NivelClasificacion.RESERVADA)),
        )
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave", roles = listOf(rol))

        val resultado = gestion.autorizar(
            identidadId = "id-1",
            accion = "leer",
            tipoRecurso = "documento",
            nivelClasificacion = NivelClasificacion.RESERVADA,
            fecha = fecha,
        )

        assertEquals(ResultadoAutorizacion.PERMITIDO, resultado)
    }
}

// RF-SA-005 · Registro de eventos de seguridad
class RegistroDeEventosDeSeguridadTest {

    @Test
    fun `dado un intento de autenticacion y una decision de autorizacion, cuando ocurren, existen eventos atribuibles y fechados`() {
        val gestion = GestionDeAccesos()
        val fecha = Instant.parse("2026-08-25T10:00:00Z")
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave")

        gestion.autenticar(actor = "usuario-1", credencial = "clave", fecha = fecha)
        gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha)

        val autenticacion = gestion.eventosDeSeguridad.single { it.tipo == "AUTENTICACION_EXITOSA" }
        assertEquals("usuario-1", autenticacion.actor)
        assertEquals(fecha, autenticacion.fecha)

        val autorizacion = gestion.eventosDeSeguridad.single { it.tipo == "AUTORIZACION_DENEGADA" }
        assertEquals("usuario-1", autorizacion.actor)
        assertEquals(fecha, autorizacion.fecha)
    }
}

// RF-SA-006 · Revocación efectiva e inmediata
class RevocacionEfectivaEInmediataTest {

    private val fecha = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun `dada una identidad con un permiso revocado, cuando solicita la accion que dependia de el, se deniega desde la revocacion`() {
        val gestion = GestionDeAccesos()
        val rol = Rol(nombre = "archivista", permisos = listOf(Permiso(accion = "leer", tipoRecurso = "documento")))
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave", roles = listOf(rol))
        assertEquals(
            ResultadoAutorizacion.PERMITIDO,
            gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha),
        )

        gestion.revocarRol(identidadId = "id-1", nombreRol = "archivista")

        assertEquals(
            ResultadoAutorizacion.DENEGADO,
            gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha),
        )
    }
}

// RF-SA-007 · Protección de credenciales
class ProteccionDeCredencialesTest {

    @Test
    fun `dada una identidad creada, su credencial almacenada nunca es el texto plano original`() {
        val gestion = GestionDeAccesos()

        val identidad = gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "mi-clave-secreta")

        assertNotEquals("mi-clave-secreta", identidad.credencialHash)
    }

    @Test
    fun `dado un evento de seguridad, cuando se consulta, no contiene la credencial en texto plano`() {
        val gestion = GestionDeAccesos()
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "mi-clave-secreta")

        gestion.autenticar(actor = "usuario-1", credencial = "mi-clave-secreta", fecha = Instant.parse("2026-08-25T00:00:00Z"))

        assertTrue(gestion.eventosDeSeguridad.none { it.toString().contains("mi-clave-secreta") })
    }
}

// RF-SA-010 · Cero pérdida silenciosa de eventos de seguridad
class CeroPerdidaSilenciosaDeEventosTest {

    @Test
    fun `dado un conjunto de intentos de autenticacion y autorizacion, cada uno tiene su evento correspondiente`() {
        val gestion = GestionDeAccesos()
        val fecha = Instant.parse("2026-08-25T00:00:00Z")
        gestion.crearIdentidad(id = "id-1", actor = "usuario-1", credencial = "clave-correcta")

        gestion.autenticar(actor = "usuario-1", credencial = "clave-correcta", fecha = fecha)
        assertFailsWith<CredencialesInvalidasException> {
            gestion.autenticar(actor = "usuario-1", credencial = "clave-incorrecta", fecha = fecha)
        }
        gestion.autorizar(identidadId = "id-1", accion = "leer", tipoRecurso = "documento", fecha = fecha)

        assertEquals(3, gestion.eventosDeSeguridad.size)
        assertFalse(gestion.eventosDeSeguridad.isEmpty())
    }
}
