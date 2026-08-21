package sgdea.contexts.recordscustodia.persistencia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import sgdea.contexts.recordscustodia.AlmacenDeDocumentos
import sgdea.contexts.recordscustodia.AlmacenDeEventos
import sgdea.contexts.recordscustodia.AlmacenDeOriginales
import sgdea.contexts.recordscustodia.AlmacenDeSugerencias
import sgdea.contexts.recordscustodia.AlmacenDeTrd
import sgdea.contexts.recordscustodia.Clasificacion
import sgdea.contexts.recordscustodia.DocumentoDeArchivo
import sgdea.contexts.recordscustodia.EventoAuditoria
import sgdea.contexts.recordscustodia.OriginalInmutable
import sgdea.contexts.recordscustodia.Procedencia
import sgdea.contexts.recordscustodia.Serie
import sgdea.contexts.recordscustodia.Sugerencia
import sgdea.contexts.recordscustodia.Trd

// specs/spec-infra-servicios.md §4: implementación Postgres de los puertos
// declarados en CustodiaOriginales.kt (P-03: el dominio desconoce cuál
// implementación de almacenamiento está activa). `guardar` usa
// `entityManager.persist`, no `JpaRepository.save`: persist solo emite
// INSERT, nunca UPDATE, que es la garantía que la spec exige a nivel de
// acceso a datos para la tabla de solo-una-escritura.
@Component
@Transactional
class AlmacenDeOriginalesJpa : AlmacenDeOriginales {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun guardar(original: OriginalInmutable) {
        entityManager.persist(
            OriginalEntity(
                id = original.id,
                bytes = original.bytes,
                algoritmoHuella = original.algoritmoHuella,
                huella = original.huella,
                fechaCustodia = original.fechaCustodia,
            ),
        )
    }

    override fun buscar(id: String): OriginalInmutable? =
        entityManager.find(OriginalEntity::class.java, id)?.toDominio()

    override fun todos(): List<OriginalInmutable> =
        entityManager.createQuery("select o from OriginalEntity o", OriginalEntity::class.java)
            .resultList
            .map { it.toDominio() }

    private fun OriginalEntity.toDominio() =
        OriginalInmutable(id = id, bytes = bytes, algoritmoHuella = algoritmoHuella, huella = huella, fechaCustodia = fechaCustodia)
}

// El documento sí se actualiza (RF-RC-004), así que este puerto usa el
// guardado normal de Spring Data en vez de EntityManager.persist.
@Component
@Transactional
class AlmacenDeDocumentosJpa(
    private val jpaRepository: DocumentoJpaRepository,
) : AlmacenDeDocumentos {

    override fun guardar(documento: DocumentoDeArchivo) {
        jpaRepository.save(
            DocumentoEntity(
                id = documento.id,
                originalId = documento.originalId,
                procedenciaFuente = documento.procedencia.fuente,
                procedenciaFecha = documento.procedencia.fecha,
                procedenciaLoteOFlujoId = documento.procedencia.loteOFlujoId,
                clasificacionTrdVersion = documento.clasificacion?.trdVersion,
                clasificacionSerieId = documento.clasificacion?.serieId,
                clasificacionSubserieId = documento.clasificacion?.subserieId,
            ),
        )
    }

    override fun buscar(id: String): DocumentoDeArchivo? =
        jpaRepository.findById(id).orElse(null)?.toDominio()

    private fun DocumentoEntity.toDominio() = DocumentoDeArchivo(
        id = id,
        originalId = originalId,
        procedencia = Procedencia(fuente = procedenciaFuente, fecha = procedenciaFecha, loteOFlujoId = procedenciaLoteOFlujoId),
        clasificacion = clasificacionTrdVersion?.let { version ->
            Clasificacion(documentoId = id, trdVersion = version, serieId = clasificacionSerieId!!, subserieId = clasificacionSubserieId)
        },
    )
}

// specs/spec-infra-servicios.md §4: mismo tratamiento de solo-inserción que
// AlmacenDeOriginalesJpa — la bitácora es de solo anexado (RF-RC-005), así
// que `anexar` nunca debe poder convertirse en un UPDATE. El id
// autoincremental (orden de inserción) es el índice que `en(indice)` expone.
@Component
@Transactional
class AlmacenDeEventosJpa : AlmacenDeEventos {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun anexar(evento: EventoAuditoria) {
        entityManager.persist(
            EventoAuditoriaEntity(
                actor = evento.actor,
                fecha = evento.fecha,
                tipo = evento.tipo,
                estadoAnterior = evento.estadoAnterior,
                estadoPosterior = evento.estadoPosterior,
            ),
        )
    }

    override fun todos(): List<EventoAuditoria> =
        entityManager.createQuery("select e from EventoAuditoriaEntity e order by e.id", EventoAuditoriaEntity::class.java)
            .resultList
            .map { EventoAuditoria(actor = it.actor, fecha = it.fecha, tipo = it.tipo, estadoAnterior = it.estadoAnterior, estadoPosterior = it.estadoPosterior) }

    override fun en(indice: Int): EventoAuditoria = todos()[indice]
}

@Component
@Transactional
class AlmacenDeSugerenciasJpa(
    private val jpaRepository: SugerenciaJpaRepository,
    private val objectMapper: ObjectMapper,
) : AlmacenDeSugerencias {

    override fun guardar(sugerencia: Sugerencia) {
        jpaRepository.save(
            SugerenciaEntity(
                documentoId = sugerencia.documentoId,
                tipo = sugerencia.tipo,
                contenidoPropuesto = sugerencia.contenidoPropuesto,
                modeloId = sugerencia.modeloId,
                evidenciaJson = objectMapper.writeValueAsString(sugerencia.evidencia),
                confianza = sugerencia.confianza,
                fecha = sugerencia.fecha,
            ),
        )
    }

    override fun de(documentoId: String): List<Sugerencia> =
        jpaRepository.findByDocumentoId(documentoId).map { entity ->
            Sugerencia(
                documentoId = entity.documentoId,
                tipo = entity.tipo,
                contenidoPropuesto = entity.contenidoPropuesto,
                modeloId = entity.modeloId,
                evidencia = objectMapper.readValue(entity.evidenciaJson),
                confianza = entity.confianza,
                fecha = entity.fecha,
            )
        }
}

@Component
@Transactional
class AlmacenDeTrdJpa(
    private val jpaRepository: TrdVersionJpaRepository,
    private val objectMapper: ObjectMapper,
) : AlmacenDeTrd {

    override fun guardar(trd: Trd) {
        jpaRepository.save(
            TrdVersionEntity(
                version = trd.version,
                vigenteDesde = trd.vigenteDesde,
                seriesJson = objectMapper.writeValueAsString(trd.series),
            ),
        )
    }

    override fun buscar(version: Int): Trd? =
        jpaRepository.findById(version).orElse(null)?.let { entity ->
            Trd(version = entity.version, vigenteDesde = entity.vigenteDesde, series = objectMapper.readValue<List<Serie>>(entity.seriesJson))
        }
}
