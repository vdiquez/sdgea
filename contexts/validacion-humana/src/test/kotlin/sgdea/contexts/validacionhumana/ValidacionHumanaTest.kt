package sgdea.contexts.validacionhumana

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun sugerencia(
    documentoId: String = "doc-1",
    contenidoPropuesto: String = "serie-1",
    confianza: Double = 0.5,
    fecha: Instant = Instant.parse("2026-08-26T00:00:00Z"),
) = SugerenciaPendiente(
    documentoId = documentoId,
    tipo = "clasificacion",
    contenidoPropuesto = contenidoPropuesto,
    modeloId = "emisor-ficticio-v0",
    evidencia = listOf("pagina-1"),
    confianza = confianza,
    fecha = fecha,
)

private class FuenteDeSugerenciasEnMemoria(private val sugerencias: List<SugerenciaPendiente>) : FuenteDeSugerencias {
    override fun pendientes(): List<SugerenciaPendiente> = sugerencias
}

private class RegistradorDeDecisionesEnMemoria : RegistradorDeDecisiones {
    val decisionesMaterializadas = mutableListOf<DecisionDeClasificacion>()
    override fun materializar(decision: DecisionDeClasificacion) {
        decisionesMaterializadas.add(decision)
    }
}

private class VerificadorDePermisosEnMemoria(private val permitido: Boolean) : VerificadorDePermisos {
    override fun tienePermiso(identidadId: String, accion: String, tipoRecurso: String): Boolean = permitido
}

private class ConfirmadorDeLimitesEnMemoria : ConfirmadorDeLimites {
    val confirmaciones = mutableListOf<Triple<String, String, Instant>>()
    override fun confirmar(unidadId: String, actor: String, fecha: Instant) {
        confirmaciones.add(Triple(unidadId, actor, fecha))
    }
}

private fun unidadPendienteDeLimites(
    unidadId: String = "unidad-1",
    confianza: Double = 0.5,
    fecha: Instant = Instant.parse("2026-08-27T00:00:00Z"),
) = UnidadPendienteDeLimites(
    unidadId = unidadId,
    loteId = "lote-1",
    modeloId = "emisor-ficticio-v0",
    evidencia = listOf("pagina-1"),
    confianza = confianza,
    fecha = fecha,
)

private class FuenteDeSugerenciasDeLimitesEnMemoria(private val unidades: List<UnidadPendienteDeLimites>) :
    FuenteDeSugerenciasDeLimites {
    override fun pendientes(): List<UnidadPendienteDeLimites> = unidades
}

// RF-VH-001/002 · Agregación de sugerencias en colas de revisión, orden por confianza
class ColaDeRevisionTest {

    @Test
    fun `dadas sugerencias pendientes de varios documentos, cuando se consultan, aparecen ordenadas de menor a mayor confianza`() {
        val cola = ColaDeRevision(
            FuenteDeSugerenciasEnMemoria(
                listOf(sugerencia(documentoId = "doc-alta", confianza = 0.9), sugerencia(documentoId = "doc-baja", confianza = 0.1)),
            ),
        )

        val ordenadas = cola.ordenadasPorConfianza()

        assertEquals(listOf("doc-baja", "doc-alta"), ordenadas.map { it.documentoId })
    }

    @Test
    fun `dado un umbral, cuando se consultan las candidatas a aprobacion masiva, solo incluye las de confianza igual o superior`() {
        val cola = ColaDeRevision(
            FuenteDeSugerenciasEnMemoria(
                listOf(sugerencia(documentoId = "doc-alta", confianza = 0.95), sugerencia(documentoId = "doc-baja", confianza = 0.3)),
            ),
        )

        val candidatas = cola.candidatasAAprobacionMasiva(umbralDeConfianza = 0.9)

        assertEquals(listOf("doc-alta"), candidatas.map { it.documentoId })
    }

    @Test
    fun `dada una cola, cuando se consulta su volumen y antiguedad, expone ambos`() {
        val cola = ColaDeRevision(
            FuenteDeSugerenciasEnMemoria(
                listOf(
                    sugerencia(documentoId = "doc-1", fecha = Instant.parse("2026-08-20T00:00:00Z")),
                    sugerencia(documentoId = "doc-2", fecha = Instant.parse("2026-08-25T00:00:00Z")),
                ),
            ),
        )

        val (volumen, masAntigua) = cola.volumenYAntiguedadDeLaCola()

        assertEquals(2, volumen)
        assertEquals(Instant.parse("2026-08-20T00:00:00Z"), masAntigua)
    }
}

// RF-VH-003/006/007/008 · Revisión y decisión individual
class GestionDeDecisionesIndividualTest {

    private val fecha = Instant.parse("2026-08-26T10:00:00Z")

    @Test
    fun `dada una sugerencia pendiente, cuando un actor autorizado la acepta, se produce una decision atribuible marcada como aceptacion`() {
        val registrador = RegistradorDeDecisionesEnMemoria()
        val gestion = GestionDeDecisiones(registrador, VerificadorDePermisosEnMemoria(permitido = true))

        val decision = gestion.decidir(
            identidadId = "id-1",
            sugerencia = sugerencia(contenidoPropuesto = "serie-1"),
            clasificacionResultante = ClasificacionPropuesta(trdVersion = 1, serieId = "serie-1"),
            actor = "archivista-1",
            fecha = fecha,
        )

        assertEquals(TipoDeDecision.ACEPTACION, decision.tipo)
        assertEquals("archivista-1", decision.actor)
        assertEquals(fecha, decision.fecha)
        assertEquals(1, registrador.decisionesMaterializadas.size)
    }

    @Test
    fun `dada una sugerencia pendiente, cuando un actor autorizado la corrige con una serie distinta, se produce una decision marcada como correccion`() {
        val registrador = RegistradorDeDecisionesEnMemoria()
        val gestion = GestionDeDecisiones(registrador, VerificadorDePermisosEnMemoria(permitido = true))

        val decision = gestion.decidir(
            identidadId = "id-1",
            sugerencia = sugerencia(contenidoPropuesto = "serie-1"),
            clasificacionResultante = ClasificacionPropuesta(trdVersion = 1, serieId = "serie-2"),
            actor = "archivista-1",
            fecha = fecha,
        )

        assertEquals(TipoDeDecision.CORRECCION, decision.tipo)
    }

    @Test
    fun `dado un actor sin permiso sobre el recurso, cuando intenta decidir, se deniega y no se materializa nada`() {
        val registrador = RegistradorDeDecisionesEnMemoria()
        val gestion = GestionDeDecisiones(registrador, VerificadorDePermisosEnMemoria(permitido = false))

        assertFailsWith<AccesoDenegadoException> {
            gestion.decidir(
                identidadId = "id-sin-permiso",
                sugerencia = sugerencia(),
                clasificacionResultante = ClasificacionPropuesta(trdVersion = 1, serieId = "serie-1"),
                actor = "archivista-1",
                fecha = fecha,
            )
        }
        assertTrue(registrador.decisionesMaterializadas.isEmpty())
    }
}

// RF-VH-004 · Aprobación masiva de candidatos de alta confianza
class AprobacionMasivaTest {

    private val fecha = Instant.parse("2026-08-26T10:00:00Z")

    @Test
    fun `dado un conjunto de candidatos, cuando un actor autorizado los aprueba en bloque, se produce una decision por cada uno con el mismo actor y fecha`() {
        val registrador = RegistradorDeDecisionesEnMemoria()
        val gestion = GestionDeDecisiones(registrador, VerificadorDePermisosEnMemoria(permitido = true))
        val candidatas = listOf(
            sugerencia(documentoId = "doc-1", contenidoPropuesto = "serie-1"),
            sugerencia(documentoId = "doc-2", contenidoPropuesto = "serie-2"),
        )

        val decisiones = gestion.aprobarEnBloque(
            identidadId = "id-1",
            candidatas = candidatas,
            resolver = { ClasificacionPropuesta(trdVersion = 1, serieId = it.contenidoPropuesto) },
            actor = "archivista-1",
            fecha = fecha,
        )

        assertEquals(2, decisiones.size)
        assertTrue(decisiones.all { it.actor == "archivista-1" && it.fecha == fecha })
        assertEquals(setOf("doc-1", "doc-2"), decisiones.map { it.documentoId }.toSet())
        assertEquals(2, registrador.decisionesMaterializadas.size)
    }

    @Test
    fun `dado un actor sin permiso, cuando intenta aprobar en bloque, se deniega y no se materializa ninguna`() {
        val registrador = RegistradorDeDecisionesEnMemoria()
        val gestion = GestionDeDecisiones(registrador, VerificadorDePermisosEnMemoria(permitido = false))

        assertFailsWith<AccesoDenegadoException> {
            gestion.aprobarEnBloque(
                identidadId = "id-sin-permiso",
                candidatas = listOf(sugerencia()),
                resolver = { ClasificacionPropuesta(trdVersion = 1, serieId = it.contenidoPropuesto) },
                actor = "archivista-1",
                fecha = fecha,
            )
        }
        assertTrue(registrador.decisionesMaterializadas.isEmpty())
    }
}

// RF-VH-005/007 · Confirmación (o corrección) de límites de documento en Normalización
class GestionDeLimitesTest {

    private val fecha = Instant.parse("2026-08-27T10:00:00Z")

    @Test
    fun `dada una sugerencia de limites pendiente, cuando un actor autorizado la confirma, Normalizacion recibe la confirmacion con actor y fecha`() {
        val confirmador = ConfirmadorDeLimitesEnMemoria()
        val gestion = GestionDeLimites(confirmador, VerificadorDePermisosEnMemoria(permitido = true))

        gestion.confirmar(identidadId = "id-1", unidadId = "unidad-1", actor = "archivista-1", fecha = fecha)

        assertEquals(1, confirmador.confirmaciones.size)
        assertEquals(Triple("unidad-1", "archivista-1", fecha), confirmador.confirmaciones[0])
    }

    @Test
    fun `dado un actor sin permiso sobre el recurso, cuando intenta confirmar limites, se deniega y Normalizacion no recibe nada`() {
        val confirmador = ConfirmadorDeLimitesEnMemoria()
        val gestion = GestionDeLimites(confirmador, VerificadorDePermisosEnMemoria(permitido = false))

        assertFailsWith<AccesoDenegadoException> {
            gestion.confirmar(identidadId = "id-sin-permiso", unidadId = "unidad-1", actor = "archivista-1", fecha = fecha)
        }
        assertTrue(confirmador.confirmaciones.isEmpty())
    }
}

// RF-VH-001/002 (T-39) · Agregación de la cola de límites en su propia cola, orden por confianza
class ColaDeLimitesTest {

    @Test
    fun `dadas unidades pendientes de limites de varios lotes, cuando se consultan, aparecen ordenadas de menor a mayor confianza`() {
        val cola = ColaDeLimites(
            FuenteDeSugerenciasDeLimitesEnMemoria(
                listOf(
                    unidadPendienteDeLimites(unidadId = "unidad-alta", confianza = 0.9),
                    unidadPendienteDeLimites(unidadId = "unidad-baja", confianza = 0.1),
                ),
            ),
        )

        val ordenadas = cola.ordenadasPorConfianza()

        assertEquals(listOf("unidad-baja", "unidad-alta"), ordenadas.map { it.unidadId })
    }

    @Test
    fun `dada una cola de limites, cuando se consulta su volumen y antiguedad, expone ambos`() {
        val cola = ColaDeLimites(
            FuenteDeSugerenciasDeLimitesEnMemoria(
                listOf(
                    unidadPendienteDeLimites(unidadId = "unidad-1", fecha = Instant.parse("2026-08-20T00:00:00Z")),
                    unidadPendienteDeLimites(unidadId = "unidad-2", fecha = Instant.parse("2026-08-25T00:00:00Z")),
                ),
            ),
        )

        val (volumen, masAntigua) = cola.volumenYAntiguedadDeLaCola()

        assertEquals(2, volumen)
        assertEquals(Instant.parse("2026-08-20T00:00:00Z"), masAntigua)
    }
}
