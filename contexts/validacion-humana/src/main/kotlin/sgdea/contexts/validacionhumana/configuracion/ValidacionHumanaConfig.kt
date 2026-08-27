package sgdea.contexts.validacionhumana.configuracion

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sgdea.contexts.validacionhumana.ColaDeRevision
import sgdea.contexts.validacionhumana.FuenteDeSugerencias
import sgdea.contexts.validacionhumana.GestionDeDecisiones
import sgdea.contexts.validacionhumana.RegistradorDeDecisiones
import sgdea.contexts.validacionhumana.VerificadorDePermisos

// `ColaDeRevision` y `GestionDeDecisiones` siguen siendo clases de dominio
// planas (sin anotaciones Spring, para que T-29 las siga construyendo con
// dobles en memoria sin contexto Spring); esta clase es el único punto que
// las conecta a los adaptadores HTTP reales (integracion/IntegracionHttp.kt).
@Configuration
class ValidacionHumanaConfig {

    @Bean
    fun colaDeRevision(fuente: FuenteDeSugerencias): ColaDeRevision = ColaDeRevision(fuente)

    @Bean
    fun gestionDeDecisiones(registrador: RegistradorDeDecisiones, permisos: VerificadorDePermisos): GestionDeDecisiones =
        GestionDeDecisiones(registrador, permisos)
}
