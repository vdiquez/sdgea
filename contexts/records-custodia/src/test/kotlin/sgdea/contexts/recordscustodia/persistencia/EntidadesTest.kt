package sgdea.contexts.recordscustodia.persistencia

import jakarta.persistence.Table
import kotlin.test.Test
import kotlin.test.assertEquals

// VETO real de Codex sobre T-56 (ver STATE.md/T-58): `eventos_auditoria` era
// un nombre de tabla genérico compartido con normalizacion y extraccion en
// el mismo Postgres (`docker-compose.saas.yml`, DB_NAME=sgdea para los tres)
// -- GET /eventos-auditoria de cualquiera de los tres devolvía eventos de
// los otros. H2 en test no puede reproducir esa colisión (cada módulo
// Kotlin corre contra su propia instancia `jdbc:h2:mem:...` aislada), así
// que esta prueba es una guarda de regresión directa sobre el nombre de
// tabla -- mismo criterio que TestAislamientoDeTablasPorContexto en
// indexacion-busqueda (T-56): si alguien revierte el prefijo `rc_`, esto
// falla en rojo antes de llegar a un Postgres compartido real.
class EntidadesTest {

    @Test
    fun `la tabla de eventos de auditoria tiene prefijo propio unico`() {
        val nombreDeTabla = EventoAuditoriaEntity::class.java.getAnnotation(Table::class.java).name
        assertEquals("rc_eventos_auditoria", nombreDeTabla)
    }
}
