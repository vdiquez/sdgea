package sgdea.contexts.recordscustodia.configuracion

import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sgdea.contexts.recordscustodia.CapaAnticorrupcionSugerencias
import sgdea.contexts.recordscustodia.Sugerencia
import sgdea.contexts.recordscustodia.SugerenciaEntrante

// T-21 (corrige VETO de Codex sobre P-08/T-20, ver REVIEW.md): AlmacenDeSugerenciasJpa
// y AlmacenDeEventosJpa (Almacenes.kt) son beans `@Transactional` independientes; sin un
// límite que englobe ambas llamadas, cada una confirma su propia transacción por la
// semántica proxy de Spring, y un fallo al anexar el evento deja la sugerencia ya
// persistida sin su evento de auditoría. CapaAnticorrupcionSugerencias se mantiene sin
// anotaciones Spring (ver RecordsCustodiaConfig, para que los tests unitarios T-03..T-11
// la sigan construyendo sin contexto Spring), así que este wrapper —y no el dominio— es
// quien abre la transacción real que ambas escrituras heredan por la propagación
// REQUIRED de Spring (la que aplica por defecto): si `anexar` falla, el `guardar` de la
// sugerencia se revierte con ella.
@Service
class RecepcionDeSugerenciasTransaccional(
    private val capa: CapaAnticorrupcionSugerencias,
) {
    @Transactional
    fun recibir(entrada: SugerenciaEntrante, fecha: Instant): Sugerencia = capa.recibir(entrada, fecha)
}
