package sgdea.contexts.recordscustodia.configuracion

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sgdea.contexts.recordscustodia.BitacoraAuditoria
import sgdea.contexts.recordscustodia.CapaAnticorrupcionSugerencias
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.RegistroTrd
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeDocumentosJpa
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeEventosJpa
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeOriginalesJpa
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeSugerenciasJpa
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeTrdJpa

// specs/spec-infra-servicios.md §4: "persistirlo en Postgres es reemplazar
// ese estado en memoria por tablas, sin cambiar el contrato de los métodos".
// CustodiaOriginales, BitacoraAuditoria, CapaAnticorrupcionSugerencias y
// RegistroTrd siguen siendo clases de dominio planas (sin anotaciones Spring)
// para que los tests unitarios de T-03..T-11 sigan construyéndolas sin
// contexto Spring; esta clase es el único punto que las conecta a los
// puertos respaldados por Postgres para el servicio HTTP.
@Configuration
class RecordsCustodiaConfig {

    @Bean
    fun bitacoraAuditoria(almacen: AlmacenDeEventosJpa): BitacoraAuditoria = BitacoraAuditoria(almacen)

    @Bean
    fun custodiaOriginales(
        almacenDeOriginales: AlmacenDeOriginalesJpa,
        almacenDeDocumentos: AlmacenDeDocumentosJpa,
        bitacora: BitacoraAuditoria,
    ): CustodiaOriginales = CustodiaOriginales(
        almacenDeOriginales = almacenDeOriginales,
        almacenDeDocumentos = almacenDeDocumentos,
        bitacora = bitacora,
    )

    @Bean
    fun capaAnticorrupcionSugerencias(
        custodia: CustodiaOriginales,
        almacen: AlmacenDeSugerenciasJpa,
    ): CapaAnticorrupcionSugerencias = CapaAnticorrupcionSugerencias(custodia, almacen)

    @Bean
    fun registroTrd(almacen: AlmacenDeTrdJpa): RegistroTrd = RegistroTrd(almacen)
}
