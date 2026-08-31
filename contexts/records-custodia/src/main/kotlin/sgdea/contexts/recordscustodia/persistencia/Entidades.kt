package sgdea.contexts.recordscustodia.persistencia

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

// specs/spec-infra-servicios.md §4: "OriginalInmutable -> tabla
// originales_inmutables, escritura de una sola vez". Esta entidad solo se
// escribe vía AlmacenDeOriginalesJpa.guardar, que usa EntityManager.persist
// (nunca merge/update) para que el UPDATE sea físicamente imposible desde
// este contexto, tal como exige la spec a nivel de acceso a datos.
@Entity
@Table(name = "originales_inmutables")
class OriginalEntity(
    @Id
    var id: String = "",

    @Lob
    @Column(nullable = false)
    var bytes: ByteArray = ByteArray(0),

    @Column(name = "algoritmo_huella", nullable = false)
    var algoritmoHuella: String = "",

    @Column(nullable = false)
    var huella: String = "",

    @Column(name = "fecha_custodia", nullable = false)
    var fechaCustodia: Instant = Instant.EPOCH,
)

// specs/spec-infra-servicios.md §4: "DocumentoDeArchivo -> tabla
// documentos_archivo, con original_id como llave foránea". A diferencia del
// original, el documento sí se actualiza (RF-RC-004 cambia su clasificación),
// así que esta entidad usa el guardado normal de Spring Data (merge/insert).
// `original` es `@ManyToOne`/`@JoinColumn` (T-19, corrige VETO de Codex: antes
// era una columna escalar sin FK real) para que el DDL de Hibernate impida un
// `original_id` que no exista en `originales_inmutables`.
@Entity
@Table(name = "documentos_archivo")
class DocumentoEntity(
    @Id
    var id: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_id", nullable = false)
    var original: OriginalEntity? = null,

    @Column(name = "procedencia_fuente", nullable = false)
    var procedenciaFuente: String = "",

    @Column(name = "procedencia_fecha", nullable = false)
    var procedenciaFecha: Instant = Instant.EPOCH,

    @Column(name = "procedencia_lote_o_flujo_id", nullable = false)
    var procedenciaLoteOFlujoId: String = "",

    @Column(name = "clasificacion_trd_version")
    var clasificacionTrdVersion: Int? = null,

    @Column(name = "clasificacion_serie_id")
    var clasificacionSerieId: String? = null,

    @Column(name = "clasificacion_subserie_id")
    var clasificacionSubserieId: String? = null,
)

// specs/spec-infra-servicios.md §4: "EventoAuditoria (via BitacoraAuditoria)
// -> tabla rc_eventos_auditoria, de solo inserción". Mismo tratamiento que
// originales_inmutables: solo se escribe vía EntityManager.persist. El id
// autoincremental fija el orden de anexado, que es lo que BitacoraAuditoria
// necesita para `en(indice)`.
// Prefijo `rc_` (VETO real de Codex sobre T-56, ver STATE.md): esta tabla
// compartía nombre genérico con la de normalizacion y extraccion en el
// mismo Postgres (`docker-compose.saas.yml`), así que GET /eventos-auditoria
// de cualquiera de los tres devolvía eventos de los otros -- contradice
// spec-infra-servicios.md §2 ("cada contexto mapea sus propios agregados a
// sus propias tablas"). Mismo patrón de aislamiento que `ib_` en
// indexacion-busqueda (T-56/T-58).
@Entity
@Table(name = "rc_eventos_auditoria")
class EventoAuditoriaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var actor: String = "",

    @Column(nullable = false)
    var fecha: Instant = Instant.EPOCH,

    @Column(nullable = false)
    var tipo: String = "",

    @Column(name = "estado_anterior")
    var estadoAnterior: String? = null,

    @Column(name = "estado_posterior")
    var estadoPosterior: String? = null,

    // `columnDefinition` con DEFAULT explícito (T-39, bug real encontrado en
    // verificación Docker): sin él, Hibernate genera `ALTER TABLE ... ADD
    // COLUMN es_correccion boolean not null` sin valor por defecto, que
    // Postgres rechaza si la tabla ya tiene filas ("contains null values") —
    // `ddl-auto: update` no es una herramienta de migración real (ver
    // comentario en application.yml), así que esta columna debe poder
    // agregarse sobre una tabla con datos existentes.
    @Column(name = "es_correccion", nullable = false, columnDefinition = "boolean not null default false")
    var esCorreccion: Boolean = false,
)

// VETO real de Codex sobre T-58 (ver STATE.md): renombrar `eventos_auditoria`
// a `rc_eventos_auditoria` sin preservar la lectura del historial ya
// existente viola P-08 -- los eventos anteriores al rename seguían físicamente
// en la tabla vieja, pero GET /eventos-auditoria dejaba de poder leerlos.
// Esta entidad es de SOLO LECTURA sobre esa tabla heredada -- `anexar()`
// nunca escribe aquí, solo `AlmacenDeEventosJpa.todos()` la consulta como
// fallback. Mismas columnas que `EventoAuditoriaEntity` tenía antes del
// rename (incluida `es_correccion`, que Hibernate ya le había agregado en
// T-39 a la tabla compartida).
@Entity
@Table(name = "eventos_auditoria")
class EventoAuditoriaLegacyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var actor: String = "",

    @Column(nullable = false)
    var fecha: Instant = Instant.EPOCH,

    @Column(nullable = false)
    var tipo: String = "",

    @Column(name = "estado_anterior")
    var estadoAnterior: String? = null,

    @Column(name = "estado_posterior")
    var estadoPosterior: String? = null,

    @Column(name = "es_correccion", nullable = false, columnDefinition = "boolean not null default false")
    var esCorreccion: Boolean = false,
)

// specs/spec-infra-servicios.md §4: "Sugerencia -> tabla sugerencias, con
// documento_id como llave foránea". `evidencia` (List<String>) se serializa a
// JSON en una columna de texto, misma decisión que `inventario` en
// captura-ingesta (T-16): representar una lista de longitud variable sin una
// tabla hija que la spec no pide. `documento` es `@ManyToOne`/`@JoinColumn`
// (T-19, corrige VETO de Codex: antes era una columna escalar sin FK real)
// para que el DDL de Hibernate impida un `documento_id` que no exista en
// `documentos_archivo`.
@Entity
@Table(name = "sugerencias")
class SugerenciaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    var documento: DocumentoEntity? = null,

    @Column(nullable = false)
    var tipo: String = "",

    @Column(name = "contenido_propuesto", nullable = false, columnDefinition = "text")
    var contenidoPropuesto: String = "",

    @Column(name = "modelo_id", nullable = false)
    var modeloId: String = "",

    @Column(name = "evidencia_json", nullable = false, columnDefinition = "text")
    var evidenciaJson: String = "[]",

    @Column(nullable = false)
    var confianza: Double = 0.0,

    @Column(nullable = false)
    var fecha: Instant = Instant.EPOCH,

    // T-53: opcional -- solo Enriquecimiento la declara (RF-EN-003).
    @Column(name = "forma_original", nullable = true, columnDefinition = "text")
    var formaOriginal: String? = null,
)

// specs/spec-infra-servicios.md §4: "Trd / RegistroTrd -> tabla
// trd_versiones, con version como parte de la llave". El árbol de
// series/subseries se serializa a JSON en una columna de texto por la misma
// razón que `evidencia`: es una estructura anidada de longitud variable que
// la spec no pide modelar en tablas propias.
@Entity
@Table(name = "trd_versiones")
class TrdVersionEntity(
    @Id
    var version: Int = 0,

    @Column(name = "vigente_desde", nullable = false)
    var vigenteDesde: Instant = Instant.EPOCH,

    @Column(name = "series_json", nullable = false, columnDefinition = "text")
    var seriesJson: String = "[]",
)

interface DocumentoJpaRepository : JpaRepository<DocumentoEntity, String>

interface SugerenciaJpaRepository : JpaRepository<SugerenciaEntity, Long> {
    fun findByDocumento_Id(documentoId: String): List<SugerenciaEntity>
}
