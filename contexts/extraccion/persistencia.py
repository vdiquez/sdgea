import json
import os
from datetime import datetime

from sqlalchemy import DateTime, Float, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

from dominio import (
    EstadoTextoExtraido,
    EventoAuditoria,
    ProcedenciaHeredada,
    Soporte,
    SugerenciaOcr,
    TextoExtraido,
)

# specs/spec-infra-servicios.md §11: Postgres por contexto, sin esquema
# compartido. Mismas variables de entorno que los otros contextos Python
# (normalizacion, T-34) y Kotlin (DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD).
def url_de_base_de_datos() -> str:
    host = os.environ.get("DB_HOST", "localhost")
    port = os.environ.get("DB_PORT", "5432")
    nombre = os.environ.get("DB_NAME", "sgdea")
    usuario = os.environ.get("DB_USER", "sgdea")
    clave = os.environ.get("DB_PASSWORD", "sgdea")
    return f"postgresql+psycopg://{usuario}:{clave}@{host}:{port}/{nombre}"


class Base(DeclarativeBase):
    pass


# specs/spec-infra-servicios.md §11: "TextoExtraido -> tabla
# textos_extraidos". Procedencia y sugerencia de OCR se aplanan en columnas
# propias, mismo tratamiento que UnidadDocumentalEntity en normalizacion
# (T-34). `evidencia` (lista) se serializa a JSON en una columna de texto,
# mismo criterio que `sugerencia_evidencia_json` en normalizacion.
class TextoExtraidoEntity(Base):
    __tablename__ = "textos_extraidos"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    unidad_documental_candidata_id: Mapped[str] = mapped_column(String, nullable=False)
    estado: Mapped[str] = mapped_column(String, nullable=False)
    soporte: Mapped[str | None] = mapped_column(String, nullable=True)
    contenido: Mapped[str | None] = mapped_column(Text, nullable=True)
    calidad: Mapped[float | None] = mapped_column(Float, nullable=True)
    razon: Mapped[str | None] = mapped_column(Text, nullable=True)

    procedencia_fuente: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    procedencia_disparador: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_lote_o_flujo_id: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_item_ingesta_id: Mapped[str] = mapped_column(String, nullable=False)
    procedencia_unidad_documental_id: Mapped[str] = mapped_column(String, nullable=False)

    sugerencia_ocr_modelo_id: Mapped[str | None] = mapped_column(String, nullable=True)
    sugerencia_ocr_contenido: Mapped[str | None] = mapped_column(Text, nullable=True)
    sugerencia_ocr_calidad: Mapped[float | None] = mapped_column(Float, nullable=True)
    sugerencia_ocr_evidencia_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    sugerencia_ocr_fecha: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


# P-08: bitácora de solo anexado, mismo tratamiento que `eventos_auditoria` en
# normalizacion (T-37) y records-custodia — se persiste con `session.add`
# dentro de la MISMA transacción que el texto extraído (ver
# `AlmacenDeTextos.guardar_con_evento`), nunca con un commit propio, para no
# recrear el riesgo de atomicidad que T-21/T-22/T-37 corrigieron en los otros
# contextos.
class EventoAuditoriaEntity(Base):
    __tablename__ = "eventos_auditoria"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    actor: Mapped[str] = mapped_column(String, nullable=False)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    tipo: Mapped[str] = mapped_column(String, nullable=False)
    estado_anterior: Mapped[str | None] = mapped_column(String, nullable=True)
    estado_posterior: Mapped[str | None] = mapped_column(String, nullable=True)


def _evento_a_fila(evento: EventoAuditoria) -> EventoAuditoriaEntity:
    return EventoAuditoriaEntity(
        actor=evento.actor,
        fecha=evento.fecha,
        tipo=evento.tipo,
        estado_anterior=evento.estado_anterior,
        estado_posterior=evento.estado_posterior,
    )


def _evento_a_dominio(fila: EventoAuditoriaEntity) -> EventoAuditoria:
    return EventoAuditoria(
        actor=fila.actor,
        fecha=fila.fecha,
        tipo=fila.tipo,
        estado_anterior=fila.estado_anterior,
        estado_posterior=fila.estado_posterior,
    )


def _a_dominio(fila: TextoExtraidoEntity) -> TextoExtraido:
    sugerencia_ocr = None
    if fila.sugerencia_ocr_modelo_id is not None:
        sugerencia_ocr = SugerenciaOcr(
            modelo_id=fila.sugerencia_ocr_modelo_id,
            contenido=fila.sugerencia_ocr_contenido or "",
            calidad=fila.sugerencia_ocr_calidad or 0.0,
            evidencia=json.loads(fila.sugerencia_ocr_evidencia_json or "[]"),
            fecha=fila.sugerencia_ocr_fecha,
        )
    return TextoExtraido(
        id=fila.id,
        unidad_documental_candidata_id=fila.unidad_documental_candidata_id,
        procedencia=ProcedenciaHeredada(
            fuente=fila.procedencia_fuente,
            fecha=fila.procedencia_fecha,
            disparador=fila.procedencia_disparador,
            lote_o_flujo_id=fila.procedencia_lote_o_flujo_id,
            item_ingesta_id=fila.procedencia_item_ingesta_id,
            unidad_documental_id=fila.procedencia_unidad_documental_id,
        ),
        estado=EstadoTextoExtraido[fila.estado],
        soporte=Soporte[fila.soporte] if fila.soporte else None,
        contenido=fila.contenido,
        calidad=fila.calidad,
        razon=fila.razon,
        sugerencia_ocr=sugerencia_ocr,
    )


def _a_fila(texto: TextoExtraido) -> TextoExtraidoEntity:
    return TextoExtraidoEntity(
        id=texto.id,
        unidad_documental_candidata_id=texto.unidad_documental_candidata_id,
        estado=texto.estado.name,
        soporte=texto.soporte.name if texto.soporte else None,
        contenido=texto.contenido,
        calidad=texto.calidad,
        razon=texto.razon,
        procedencia_fuente=texto.procedencia.fuente,
        procedencia_fecha=texto.procedencia.fecha,
        procedencia_disparador=texto.procedencia.disparador,
        procedencia_lote_o_flujo_id=texto.procedencia.lote_o_flujo_id,
        procedencia_item_ingesta_id=texto.procedencia.item_ingesta_id,
        procedencia_unidad_documental_id=texto.procedencia.unidad_documental_id,
        sugerencia_ocr_modelo_id=texto.sugerencia_ocr.modelo_id if texto.sugerencia_ocr else None,
        sugerencia_ocr_contenido=texto.sugerencia_ocr.contenido if texto.sugerencia_ocr else None,
        sugerencia_ocr_calidad=texto.sugerencia_ocr.calidad if texto.sugerencia_ocr else None,
        sugerencia_ocr_evidencia_json=(json.dumps(texto.sugerencia_ocr.evidencia) if texto.sugerencia_ocr else None),
        sugerencia_ocr_fecha=texto.sugerencia_ocr.fecha if texto.sugerencia_ocr else None,
    )


# Mismo patrón que AlmacenDeUnidades en normalizacion (T-34/T-37): un
# repositorio simple sobre una `Session` de SQLAlchemy, sin ORM "vivo" más
# allá de la traducción explícita a/desde el dominio — dominio.py no importa
# nada de SQLAlchemy.
class AlmacenDeTextos:
    def __init__(self, session: Session):
        self._session = session

    # El texto extraído y su evento de auditoría se anexan en la MISMA
    # transacción de SQLAlchemy — ninguna llamada intermedia hace su propio
    # `commit()`. Si el anexado del evento falla, el `rollback()` explícito
    # deshace también la escritura del texto: no puede existir una
    # transición confirmada sin su evento (mismo criterio que
    # `guardar_con_evento` en normalizacion, T-37).
    def guardar_con_evento(self, texto: TextoExtraido, evento: EventoAuditoria) -> None:
        try:
            self._session.merge(_a_fila(texto))
            self._session.add(_evento_a_fila(evento))
            self._session.commit()
        except Exception:
            self._session.rollback()
            raise

    def buscar(self, id: str) -> TextoExtraido | None:
        fila = self._session.get(TextoExtraidoEntity, id)
        return _a_dominio(fila) if fila else None

    def todas(self) -> list[TextoExtraido]:
        filas = self._session.execute(select(TextoExtraidoEntity)).scalars().all()
        return [_a_dominio(fila) for fila in filas]

    def de_lote(self, lote_o_flujo_id: str) -> list[TextoExtraido]:
        filas = (
            self._session.execute(
                select(TextoExtraidoEntity).where(TextoExtraidoEntity.procedencia_lote_o_flujo_id == lote_o_flujo_id)
            )
            .scalars()
            .all()
        )
        return [_a_dominio(fila) for fila in filas]

    def eventos_de_auditoria(self) -> list[EventoAuditoria]:
        filas = self._session.execute(select(EventoAuditoriaEntity).order_by(EventoAuditoriaEntity.id)).scalars().all()
        return [_evento_a_dominio(fila) for fila in filas]


def crear_fabrica_de_sesiones(url: str | None = None) -> sessionmaker[Session]:
    engine = create_engine(url or url_de_base_de_datos())
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine)
