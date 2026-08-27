import json
import os
from datetime import datetime

from sqlalchemy import DateTime, Float, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

from dominio import (
    ConfirmacionHumanaDeLimites,
    EstadoUnidadDocumental,
    ProcedenciaHeredada,
    SugerenciaDeLimites,
    UnidadDocumentalCandidata,
)

# specs/spec-infra-servicios.md §7: Postgres por contexto, sin esquema
# compartido. Mismas variables de entorno que los contextos Kotlin
# (DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD) para que docker-compose no
# necesite un mecanismo distinto por lenguaje.
def url_de_base_de_datos() -> str:
    host = os.environ.get("DB_HOST", "localhost")
    port = os.environ.get("DB_PORT", "5432")
    nombre = os.environ.get("DB_NAME", "sgdea")
    usuario = os.environ.get("DB_USER", "sgdea")
    clave = os.environ.get("DB_PASSWORD", "sgdea")
    return f"postgresql+psycopg://{usuario}:{clave}@{host}:{port}/{nombre}"


class Base(DeclarativeBase):
    pass


# specs/spec-infra-servicios.md §7: "UnidadDocumentalCandidata -> tabla
# unidades_documentales". Procedencia, sugerencia de límites y confirmación se
# aplanan en columnas propias (mismo tratamiento que Procedencia en
# captura-ingesta/T-16), en vez de tablas separadas que ningún RF pide
# todavía. `evidencia` (lista) se serializa a JSON en una columna de texto,
# mismo criterio que `evidencia_json` en records-custodia.
class UnidadDocumentalEntity(Base):
    __tablename__ = "unidades_documentales"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    lote_id: Mapped[str] = mapped_column(String, nullable=False)
    item_ingesta_id: Mapped[str] = mapped_column(String, nullable=False)
    estado: Mapped[str] = mapped_column(String, nullable=False)
    huella_de_contenido: Mapped[str | None] = mapped_column(String, nullable=True)
    razon: Mapped[str | None] = mapped_column(Text, nullable=True)
    formato_normalizado: Mapped[str | None] = mapped_column(String, nullable=True)

    procedencia_fuente: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    procedencia_disparador: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_lote_o_flujo_id: Mapped[str] = mapped_column(String, nullable=False)

    sugerencia_modelo_id: Mapped[str | None] = mapped_column(String, nullable=True)
    sugerencia_evidencia_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    sugerencia_confianza: Mapped[float | None] = mapped_column(Float, nullable=True)
    sugerencia_fecha: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    confirmacion_actor: Mapped[str | None] = mapped_column(String, nullable=True)
    confirmacion_fecha: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


def _a_dominio(fila: UnidadDocumentalEntity) -> UnidadDocumentalCandidata:
    sugerencia = None
    if fila.sugerencia_modelo_id is not None:
        sugerencia = SugerenciaDeLimites(
            modelo_id=fila.sugerencia_modelo_id,
            evidencia=json.loads(fila.sugerencia_evidencia_json or "[]"),
            confianza=fila.sugerencia_confianza or 0.0,
            fecha=fila.sugerencia_fecha,
        )
    confirmacion = None
    if fila.confirmacion_actor is not None:
        confirmacion = ConfirmacionHumanaDeLimites(actor=fila.confirmacion_actor, fecha=fila.confirmacion_fecha)
    return UnidadDocumentalCandidata(
        id=fila.id,
        lote_id=fila.lote_id,
        item_ingesta_id=fila.item_ingesta_id,
        procedencia=ProcedenciaHeredada(
            fuente=fila.procedencia_fuente,
            fecha=fila.procedencia_fecha,
            disparador=fila.procedencia_disparador,
            lote_o_flujo_id=fila.procedencia_lote_o_flujo_id,
            item_ingesta_id=fila.item_ingesta_id,
        ),
        estado=EstadoUnidadDocumental[fila.estado],
        huella_de_contenido=fila.huella_de_contenido,
        sugerencia_de_limites=sugerencia,
        confirmacion_limites=confirmacion,
        razon=fila.razon,
        formato_normalizado=fila.formato_normalizado,
    )


def _a_fila(unidad: UnidadDocumentalCandidata) -> UnidadDocumentalEntity:
    return UnidadDocumentalEntity(
        id=unidad.id,
        lote_id=unidad.lote_id,
        item_ingesta_id=unidad.item_ingesta_id,
        estado=unidad.estado.name,
        huella_de_contenido=unidad.huella_de_contenido,
        razon=unidad.razon,
        formato_normalizado=unidad.formato_normalizado,
        procedencia_fuente=unidad.procedencia.fuente,
        procedencia_fecha=unidad.procedencia.fecha,
        procedencia_disparador=unidad.procedencia.disparador,
        procedencia_lote_o_flujo_id=unidad.procedencia.lote_o_flujo_id,
        sugerencia_modelo_id=unidad.sugerencia_de_limites.modelo_id if unidad.sugerencia_de_limites else None,
        sugerencia_evidencia_json=(
            json.dumps(unidad.sugerencia_de_limites.evidencia) if unidad.sugerencia_de_limites else None
        ),
        sugerencia_confianza=unidad.sugerencia_de_limites.confianza if unidad.sugerencia_de_limites else None,
        sugerencia_fecha=unidad.sugerencia_de_limites.fecha if unidad.sugerencia_de_limites else None,
        confirmacion_actor=unidad.confirmacion_limites.actor if unidad.confirmacion_limites else None,
        confirmacion_fecha=unidad.confirmacion_limites.fecha if unidad.confirmacion_limites else None,
    )


# RF-VH-001-style (mismo patrón que AlmacenDeIdentidadesJpa en Kotlin): un
# repositorio simple sobre una `Session` de SQLAlchemy, sin ORM "vivo" más
# allá de la traducción explícita a/desde el dominio — el dominio
# (dominio.py) no importa nada de SQLAlchemy.
class AlmacenDeUnidades:
    def __init__(self, session: Session):
        self._session = session

    def guardar(self, unidad: UnidadDocumentalCandidata) -> None:
        self._session.merge(_a_fila(unidad))
        self._session.commit()

    def buscar(self, id: str) -> UnidadDocumentalCandidata | None:
        fila = self._session.get(UnidadDocumentalEntity, id)
        return _a_dominio(fila) if fila else None

    def todas(self) -> list[UnidadDocumentalCandidata]:
        filas = self._session.execute(select(UnidadDocumentalEntity)).scalars().all()
        return [_a_dominio(fila) for fila in filas]

    def de_lote(self, lote_id: str) -> list[UnidadDocumentalCandidata]:
        filas = (
            self._session.execute(select(UnidadDocumentalEntity).where(UnidadDocumentalEntity.lote_id == lote_id))
            .scalars()
            .all()
        )
        return [_a_dominio(fila) for fila in filas]


def crear_fabrica_de_sesiones(url: str | None = None) -> sessionmaker[Session]:
    engine = create_engine(url or url_de_base_de_datos())
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine)
