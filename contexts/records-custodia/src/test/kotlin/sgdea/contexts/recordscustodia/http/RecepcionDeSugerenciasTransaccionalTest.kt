package sgdea.contexts.recordscustodia.http

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import sgdea.contexts.recordscustodia.CapaAnticorrupcionSugerencias
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.EventoAuditoria
import sgdea.contexts.recordscustodia.Procedencia
import sgdea.contexts.recordscustodia.SugerenciaEntrante
import sgdea.contexts.recordscustodia.configuracion.RecepcionDeSugerenciasTransaccional
import sgdea.contexts.recordscustodia.persistencia.AlmacenDeEventosJpa

// T-21 (corrige VETO de Codex sobre P-08/T-20, ver REVIEW.md): prueba de
// integración contra el cableado Spring/JPA real (H2 en test, mismo
// mecanismo transaccional que Postgres en producción) — no los almacenes en
// memoria que ya cubren T-08/T-20. Fuerza el fallo del último paso (anexar
// el evento de la sugerencia) y comprueba que el primer paso (guardar la
// sugerencia) tampoco queda persistido: no puede existir una recepción
// confirmada sin su evento de auditoría.
@SpringBootTest
class RecepcionDeSugerenciasTransaccionalTest {

    @Autowired
    private lateinit var custodia: CustodiaOriginales

    @Autowired
    private lateinit var wrapper: RecepcionDeSugerenciasTransaccional

    @Autowired
    private lateinit var capa: CapaAnticorrupcionSugerencias

    @MockitoBean
    private lateinit var almacenDeEventos: AlmacenDeEventosJpa

    // Kotlin inserta un chequeo de no-nulidad sobre el valor de retorno de
    // `ArgumentMatchers.any()` (tipo plataforma) al pasarlo a un parámetro
    // Kotlin no-nulo, lo que revienta con NPE antes de que Mockito llegue a
    // registrar el matcher. Envolverlo en una función Kotlin propia evita el
    // chequeo (el valor de retorno nunca se usa; solo importa el efecto
    // secundario de registrar el matcher en Mockito).
    private fun <T> cualquiera(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    fun `si anexar el evento falla, la sugerencia tampoco queda persistida - P-08`() {
        Mockito.doAnswer { invocation ->
            val evento = invocation.getArgument<EventoAuditoria>(0)
            if (evento.tipo == "SUGERENCIA_RECIBIDA") {
                throw RuntimeException("fallo forzado en la bitácora para T-21")
            }
            null
        }.`when`(almacenDeEventos).anexar(cualquiera())

        val fecha = Instant.parse("2026-08-22T10:00:00Z")
        custodia.custodiar(
            id = "doc-t21-001",
            bytes = "contenido".toByteArray(),
            actor = "sistema-ingesta",
            fecha = fecha,
            procedencia = Procedencia(fuente = "escaner-sala-3", fecha = fecha, loteOFlujoId = "lote-001"),
        )

        assertFailsWith<RuntimeException> {
            wrapper.recibir(
                SugerenciaEntrante(
                    documentoId = "doc-t21-001",
                    tipo = "clasificacion",
                    contenidoPropuesto = "serie-1",
                    modeloId = "emisor-ficticio-v0",
                    evidencia = listOf("pagina-1"),
                    confianza = 0.42,
                ),
                fecha = fecha.plusSeconds(60),
            )
        }

        assertTrue(capa.sugerenciasDe("doc-t21-001").isEmpty())
    }
}
