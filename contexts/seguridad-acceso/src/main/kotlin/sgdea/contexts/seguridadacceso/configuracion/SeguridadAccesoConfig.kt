package sgdea.contexts.seguridadacceso.configuracion

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sgdea.contexts.seguridadacceso.BitacoraSeguridad
import sgdea.contexts.seguridadacceso.GestionDeAccesos
import sgdea.contexts.seguridadacceso.GestionDeRoles
import sgdea.contexts.seguridadacceso.persistencia.AlmacenDeEventosDeSeguridadJpa
import sgdea.contexts.seguridadacceso.persistencia.AlmacenDeIdentidadesJpa
import sgdea.contexts.seguridadacceso.persistencia.AlmacenDeRolesJpa

// specs/spec-infra-servicios.md §5: "persistirlo en Postgres es reemplazar ese
// estado en memoria por tablas, sin cambiar el contrato de los métodos" (mismo
// principio que records-custodia, §4). GestionDeAccesos y GestionDeRoles siguen
// siendo clases de dominio planas (sin anotaciones Spring) para que los tests
// unitarios de T-23 las sigan construyendo sin contexto Spring; esta clase es el
// único punto que las conecta a los puertos respaldados por Postgres.
@Configuration
class SeguridadAccesoConfig {

    @Bean
    fun bitacoraSeguridad(almacen: AlmacenDeEventosDeSeguridadJpa): BitacoraSeguridad = BitacoraSeguridad(almacen)

    @Bean
    fun gestionDeRoles(almacen: AlmacenDeRolesJpa): GestionDeRoles = GestionDeRoles(almacen)

    @Bean
    fun gestionDeAccesos(almacenDeIdentidades: AlmacenDeIdentidadesJpa, bitacora: BitacoraSeguridad): GestionDeAccesos =
        GestionDeAccesos(almacenDeIdentidades = almacenDeIdentidades, bitacora = bitacora)
}
