package sgdea.contexts.recordscustodia.persistencia

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import sgdea.contexts.recordscustodia.EventoAuditoria

// VETO real de Codex sobre T-58 (ver STATE.md): renombrar `eventos_auditoria`
// a `rc_eventos_auditoria` sin preservar la lectura del historial existente
// viola P-08. Esta prueba siembra la tabla HEREDADA directamente -- como si
// viniera de antes de T-58, con una fila propia de records-custodia, una
// fila de OTRO contexto simulado y una fila con un tipo AMBIGUO
// ("VALIDACION_APLICADA", que normalizacion y extraccion también usan sobre
// la misma tabla compartida, sin ninguna columna que identifique el
// contexto de origen) -- y comprueba que `AlmacenDeEventosJpa.todos()`
// recupera solo lo propio inequívoco, nunca lo ajeno ni lo ambiguo, además
// de lo nuevo escrito después del rename.
@SpringBootTest
@Transactional
class AlmacenDeEventosLegacyTest {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var almacen: AlmacenDeEventosJpa

    @Test
    fun `todos recupera el historial heredado propio, ignora el ajeno y el ambiguo, e incluye lo nuevo`() {
        entityManager.persist(
            EventoAuditoriaLegacyEntity(
                actor = "victor",
                fecha = Instant.parse("2026-08-20T00:00:00Z"),
                tipo = "ORIGINAL_CUSTODIADO",
                estadoAnterior = null,
                estadoPosterior = "CUSTODIADO",
            ),
        )
        // Fila heredada de OTRO contexto -- nunca debe aparecer aquí.
        entityManager.persist(
            EventoAuditoriaLegacyEntity(
                actor = "sistema-normalizacion",
                fecha = Instant.parse("2026-08-20T00:05:00Z"),
                tipo = "UNIDAD_RECIBIDA",
                estadoAnterior = null,
                estadoPosterior = "PENDIENTE_DE_LIMITES",
            ),
        )
        // Fila heredada AMBIGUA -- sin forma honesta de atribuirla, no debe
        // recuperarse tampoco.
        entityManager.persist(
            EventoAuditoriaLegacyEntity(
                actor = "sistema-extraccion",
                fecha = Instant.parse("2026-08-20T00:10:00Z"),
                tipo = "VALIDACION_APLICADA",
                estadoAnterior = "PENDIENTE_DE_EXTRACCION",
                estadoPosterior = "EXTRAIDO",
            ),
        )
        entityManager.flush()

        almacen.anexar(
            EventoAuditoria(
                actor = "victor",
                fecha = Instant.parse("2026-08-31T00:00:00Z"),
                tipo = "DECISION_HUMANA_MATERIALIZADA",
                estadoAnterior = null,
                estadoPosterior = "serie-1",
            ),
        )

        val tipos = almacen.todos().map { it.tipo }

        assertEquals(listOf("ORIGINAL_CUSTODIADO", "DECISION_HUMANA_MATERIALIZADA"), tipos)
        assertTrue("UNIDAD_RECIBIDA" !in tipos)
        assertTrue("VALIDACION_APLICADA" !in tipos)
    }
}
