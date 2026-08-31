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
// `entityManager.getReference` evita una consulta extra para obtener el
// `OriginalEntity` que respalda la FK (T-19, corrige VETO de Codex): el
// original siempre existe ya en la base cuando se guarda el documento
// (`CustodiaOriginales.custodiar` guarda el original antes que el documento).
@Component
@Transactional
class AlmacenDeDocumentosJpa(
    private val jpaRepository: DocumentoJpaRepository,
) : AlmacenDeDocumentos {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun guardar(documento: DocumentoDeArchivo) {
        jpaRepository.save(
            DocumentoEntity(
                id = documento.id,
                original = entityManager.getReference(OriginalEntity::class.java, documento.originalId),
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

    // RF-VH-001: respalda CustodiaOriginales.documentosSinClasificar() — la
    // cola de revisión de Validación Humana necesita ver todos los documentos,
    // no uno a la vez.
    override fun todos(): List<DocumentoDeArchivo> = jpaRepository.findAll().map { it.toDominio() }

    private fun DocumentoEntity.toDominio() = DocumentoDeArchivo(
        id = id,
        originalId = original!!.id,
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
                esCorreccion = evento.esCorreccion,
            ),
        )
    }

    // `todos()` combina la tabla nueva (rc_eventos_auditoria) con la tabla
    // heredada compartida (VETO real de Codex sobre T-58, ver STATE.md): el
    // rename de T-58 no debía dejar inaccesible el historial previo. Solo se
    // recuperan de la tabla heredada los TIPOS exclusivos de records-custodia
    // -- "VALIDACION_APLICADA" lo usan también normalizacion y extraccion
    // sobre esa MISMA tabla compartida, sin ninguna columna que identifique
    // el contexto de origen, así que no hay forma honesta de atribuir esas
    // filas a un único contexto (quedan fuera de la recuperación en los tres,
    // ver STATE.md). Ordenado por `fecha`, no por `id`: los dos orígenes
    // tienen secuencias de autoincremento independientes.
    override fun todos(): List<EventoAuditoria> {
        val nuevos = entityManager.createQuery("select e from EventoAuditoriaEntity e", EventoAuditoriaEntity::class.java)
            .resultList
            .map {
                EventoAuditoria(
                    actor = it.actor,
                    fecha = it.fecha,
                    tipo = it.tipo,
                    estadoAnterior = it.estadoAnterior,
                    estadoPosterior = it.estadoPosterior,
                    esCorreccion = it.esCorreccion,
                )
            }
        val heredados = if (existeTablaEventosHeredada()) {
            entityManager.createQuery(
                "select e from EventoAuditoriaLegacyEntity e where e.tipo in :tipos",
                EventoAuditoriaLegacyEntity::class.java,
            )
                .setParameter("tipos", TIPOS_PROPIOS_EN_TABLA_HEREDADA)
                .resultList
                .map {
                    EventoAuditoria(
                        actor = it.actor,
                        fecha = it.fecha,
                        tipo = it.tipo,
                        estadoAnterior = it.estadoAnterior,
                        estadoPosterior = it.estadoPosterior,
                        esCorreccion = it.esCorreccion,
                    )
                }
        } else {
            emptyList()
        }
        return (nuevos + heredados).sortedBy { it.fecha }
    }

    // `information_schema.tables` (estándar SQL, soportado por Postgres y
    // H2) en vez de `DatabaseMetaData` cruda: en H2 (tests) la tabla heredada
    // existe siempre porque `EventoAuditoriaLegacyEntity` es un `@Entity`
    // real que `ddl-auto: create-drop` crea igual que cualquier otro -- este
    // chequeo protege el camino de producción, donde un volumen de Postgres
    // nunca antes usado no la tiene. `lower(table_name)`: H2 guarda los
    // nombres sin comillas en mayúsculas por defecto, Postgres en
    // minúsculas.
    private fun existeTablaEventosHeredada(): Boolean {
        val conteo = entityManager
            .createNativeQuery("select count(*) from information_schema.tables where lower(table_name) = 'eventos_auditoria'")
            .singleResult as Number
        return conteo.toInt() > 0
    }

    companion object {
        private val TIPOS_PROPIOS_EN_TABLA_HEREDADA = setOf(
            "ORIGINAL_CUSTODIADO",
            "DECISION_HUMANA_MATERIALIZADA",
            "INTENTO_MODIFICACION_RECHAZADO",
            "DISCREPANCIA_DE_INTEGRIDAD",
            "SUGERENCIA_RECIBIDA",
        )
    }

    override fun en(indice: Int): EventoAuditoria = todos()[indice]
}

// `entityManager.getReference` evita una consulta extra para obtener el
// `DocumentoEntity` que respalda la FK (T-19, corrige VETO de Codex): la capa
// anticorrupción ya exige que el documento exista (`consultarDocumento`)
// antes de guardar la sugerencia.
@Component
@Transactional
class AlmacenDeSugerenciasJpa(
    private val jpaRepository: SugerenciaJpaRepository,
    private val objectMapper: ObjectMapper,
) : AlmacenDeSugerencias {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun guardar(sugerencia: Sugerencia) {
        jpaRepository.save(
            SugerenciaEntity(
                documento = entityManager.getReference(DocumentoEntity::class.java, sugerencia.documentoId),
                tipo = sugerencia.tipo,
                contenidoPropuesto = sugerencia.contenidoPropuesto,
                modeloId = sugerencia.modeloId,
                evidenciaJson = objectMapper.writeValueAsString(sugerencia.evidencia),
                confianza = sugerencia.confianza,
                fecha = sugerencia.fecha,
                formaOriginal = sugerencia.formaOriginal,
            ),
        )
    }

    override fun de(documentoId: String): List<Sugerencia> =
        jpaRepository.findByDocumento_Id(documentoId).map { entity ->
            Sugerencia(
                documentoId = entity.documento!!.id,
                tipo = entity.tipo,
                contenidoPropuesto = entity.contenidoPropuesto,
                modeloId = entity.modeloId,
                evidencia = objectMapper.readValue(entity.evidenciaJson),
                confianza = entity.confianza,
                fecha = entity.fecha,
                formaOriginal = entity.formaOriginal,
            )
        }
}

// T-19 (corrige VETO de Codex): `trd_versiones` recibe el mismo tratamiento
// de solo-inserción que `originales_inmutables`/`rc_eventos_auditoria` —
// `entityManager.persist` en vez de `JpaRepository.save`, para que un
// `version` repetido falle también a nivel de acceso a datos y no solo
// dependa del rechazo que ya hace `RegistroTrd.publicar` en el dominio.
@Component
@Transactional
class AlmacenDeTrdJpa(
    private val objectMapper: ObjectMapper,
) : AlmacenDeTrd {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun guardar(trd: Trd) {
        entityManager.persist(
            TrdVersionEntity(
                version = trd.version,
                vigenteDesde = trd.vigenteDesde,
                seriesJson = objectMapper.writeValueAsString(trd.series),
            ),
        )
    }

    override fun buscar(version: Int): Trd? =
        entityManager.find(TrdVersionEntity::class.java, version)?.let { entity ->
            Trd(version = entity.version, vigenteDesde = entity.vigenteDesde, series = objectMapper.readValue<List<Serie>>(entity.seriesJson))
        }
}
