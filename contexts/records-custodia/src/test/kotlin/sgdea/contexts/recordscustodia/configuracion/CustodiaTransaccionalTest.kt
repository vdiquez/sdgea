package sgdea.contexts.recordscustodia.configuracion

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import sgdea.contexts.recordscustodia.Clasificacion
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.DecisionHumana
import sgdea.contexts.recordscustodia.EventoAuditoria
import sgdea.contexts.recordscustodia.Procedencia
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeEventosJpa

// Riesgo latente anotado junto con T-21 (P-08, ver STATE.md): prueba de integración
// contra el cableado Spring/JPA real (H2 en test, mismo mecanismo transaccional que
// Postgres en producción) — no los almacenes en memoria, que no exponen este riesgo.
// Fuerza el fallo del último paso (anexar el evento) y comprueba que las escrituras
// anteriores del mismo caso de uso tampoco quedan persistidas.
@SpringBootTest
class CustodiaTransaccionalTest {

    @Autowired
    private lateinit var custodia: CustodiaOriginales

    @Autowired
    private lateinit var wrapper: CustodiaTransaccional

    @MockitoBean
    private lateinit var almacenDeEventos: AlmacenDeEventosJpa

    // Kotlin inserta un chequeo de no-nulidad sobre el valor de retorno de
    // `ArgumentMatchers.any()` (tipo plataforma) al pasarlo a un parámetro Kotlin
    // no-nulo, lo que revienta con NPE antes de que Mockito llegue a registrar el
    // matcher. Envolverlo en una función Kotlin propia evita el chequeo (el valor de
    // retorno nunca se usa; solo importa el efecto secundario de registrar el
    // matcher en Mockito).
    private fun <T> cualquiera(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private val procedenciaDePrueba = Procedencia(
        fuente = "escaner-sala-3",
        fecha = Instant.parse("2026-08-24T00:00:00Z"),
        loteOFlujoId = "lote-001",
    )

    @Test
    fun `si anexar el evento de custodia falla, ni el original ni el documento quedan persistidos`() {
        Mockito.doAnswer { invocation ->
            val evento = invocation.getArgument<EventoAuditoria>(0)
            if (evento.tipo == "ORIGINAL_CUSTODIADO") {
                throw RuntimeException("fallo forzado en la bitácora para el riesgo de atomicidad de custodiar")
            }
            null
        }.`when`(almacenDeEventos).anexar(cualquiera())

        assertFailsWith<RuntimeException> {
            wrapper.custodiar(
                id = "doc-atomicidad-001",
                bytes = "contenido".toByteArray(),
                actor = "sistema-ingesta",
                fecha = Instant.parse("2026-08-24T00:00:00Z"),
                procedencia = procedenciaDePrueba,
            )
        }

        assertFailsWith<NoSuchElementException> { custodia.consultar("doc-atomicidad-001") }
        assertFailsWith<NoSuchElementException> { custodia.consultarDocumento("doc-atomicidad-001") }
    }

    @Test
    fun `si anexar el evento de materializacion falla, la clasificacion tampoco queda persistida`() {
        wrapper.custodiar(
            id = "doc-atomicidad-002",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = Instant.parse("2026-08-24T00:00:00Z"),
            procedencia = procedenciaDePrueba,
        )

        Mockito.doAnswer { invocation ->
            val evento = invocation.getArgument<EventoAuditoria>(0)
            if (evento.tipo == "DECISION_HUMANA_MATERIALIZADA") {
                throw RuntimeException("fallo forzado en la bitácora para el riesgo de atomicidad de materializar")
            }
            null
        }.`when`(almacenDeEventos).anexar(cualquiera())

        val decision = DecisionHumana(
            documentoId = "doc-atomicidad-002",
            actor = "archivista-1",
            fecha = Instant.parse("2026-08-24T01:00:00Z"),
            sugerenciasReferenciadas = emptyList(),
            clasificacionResultante = Clasificacion(documentoId = "doc-atomicidad-002", trdVersion = 1, serieId = "serie-1"),
        )

        assertFailsWith<RuntimeException> { wrapper.materializar(decision) }

        assertEquals(null, custodia.consultarDocumento("doc-atomicidad-002").clasificacion)
    }
}
