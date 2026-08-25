package sgdea.contexts.recordscustodia.configuracion

import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sgdea.contexts.recordscustodia.CustodiaOriginales
import sgdea.contexts.recordscustodia.DecisionHumana
import sgdea.contexts.recordscustodia.DocumentoDeArchivo
import sgdea.contexts.recordscustodia.OriginalInmutable
import sgdea.contexts.recordscustodia.Procedencia

// Riesgo latente anotado junto con T-21 (ver STATE.md): `CustodiaOriginales.custodiar`
// escribe en tres beans `@Transactional` independientes (AlmacenDeOriginalesJpa,
// AlmacenDeDocumentosJpa, AlmacenDeEventosJpa) y `materializar` en dos (documento,
// evento); sin un límite que englobe cada conjunto de escrituras, cada una confirma
// su propia transacción por la semántica proxy de Spring, y un fallo tardío (p. ej.
// al anexar el evento de auditoría) deja el original custodiado o la clasificación
// cambiada sin su evento — la misma violación de P-08 que causó el VETO de Codex
// sobre T-20. CustodiaOriginales se mantiene sin anotaciones Spring (para que
// T-01..T-11 la sigan construyendo sin contexto Spring, ver RecordsCustodiaConfig);
// este wrapper —y no el dominio— abre la transacción real que ambas escrituras
// heredan por la propagación REQUIRED de Spring (la que aplica por defecto).
@Service
class CustodiaTransaccional(
    private val custodia: CustodiaOriginales,
) {
    @Transactional
    fun custodiar(id: String, bytes: ByteArray, actor: String, fecha: Instant, procedencia: Procedencia): OriginalInmutable =
        custodia.custodiar(id, bytes, actor, fecha, procedencia)

    @Transactional
    fun materializar(decision: DecisionHumana): DocumentoDeArchivo =
        custodia.materializar(decision)
}
