import json
import os
from datetime import datetime

from sqlalchemy import DateTime, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

from dominio import (
    EntradaDeIndice,
    EstadoEntradaDeIndice,
    EventoAuditoria,
    EventoDeAcceso,
    IndiceLexico,
    IndiceVectorial,
)

# specs/spec-infra-servicios.md §14: Postgres por contexto, sin esquema
# compartido. Mismas variables de entorno que el resto de contextos Python
# (normalizacion T-34, extraccion T-41, clasificacion no aplica).
def url_de_base_de_datos() -> str:
    host = os.environ.get("DB_HOST", "localhost")
    port = os.environ.get("DB_PORT", "5432")
    nombre = os.environ.get("DB_NAME", "sgdea")
    usuario = os.environ.get("DB_USER", "sgdea")
    clave = os.environ.get("DB_PASSWORD", "sgdea")
    return f"postgresql+psycopg://{usuario}:{clave}@{host}:{port}/{nombre}"


class Base(DeclarativeBase):
    pass


# specs/spec-infra-servicios.md §14: "EntradaDeIndice -> tabla
# entradas_de_indice". `metadatos` se serializa a JSON en columna de texto,
# mismo criterio que `inventario` en captura-ingesta / `evidencia` en
# records-custodia. SIN columna de embedding a propósito (VETO real de Codex
# sobre 5a9f822, ver STATE.md): el vector vive únicamente en
# `indices_vectoriales`, detrás de `IndiceVectorialAutoalojado` -- guardarlo
# también aquí sería la misma "ruta directa, no detrás de IndiceVectorial"
# que Codex vetó.
class EntradaDeIndiceEntity(Base):
    __tablename__ = "entradas_de_indice"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    documento_id: Mapped[str] = mapped_column(String, nullable=False)
    estado: Mapped[str] = mapped_column(String, nullable=False)
    texto_extraido: Mapped[str | None] = mapped_column(Text, nullable=True)
    metadatos_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")
    fecha_indexacion: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


# P-03 (VETO real de Codex sobre 5a9f822, ver STATE.md): tabla propia para
# el vector -- antes vivía en `entradas_de_indice.embedding_json`, escrito
# por `AlmacenDeEntradas` directamente, y `IndiceVectorialAutoalojado.
# indexar()` era un `pass`: la capacidad nunca hacía trabajo real, y
# "recuperar por similitud semántica" (RF-IB-003) no tenía ninguna operación
# de lectura detrás del puerto. Ahora el vector SOLO vive aquí.
class VectorDeEntradaEntity(Base):
    __tablename__ = "indices_vectoriales"

    entrada_id: Mapped[str] = mapped_column(String, primary_key=True)
    embedding_json: Mapped[str] = mapped_column(Text, nullable=False)


# P-08: bitácora de solo anexado para transiciones de EntradaDeIndice
# (recepción/indexación/actualización) -- mismo tratamiento WORM que
# `eventos_auditoria` en normalizacion/extraccion/records-custodia
# (`session.add`, nunca `update`).
class EventoAuditoriaEntity(Base):
    __tablename__ = "eventos_auditoria"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    actor: Mapped[str] = mapped_column(String, nullable=False)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    tipo: Mapped[str] = mapped_column(String, nullable=False)
    estado_anterior: Mapped[str | None] = mapped_column(String, nullable=True)
    estado_posterior: Mapped[str | None] = mapped_column(String, nullable=True)


# P-08/RF-IB-009 (VETO real de Codex sobre 22b6b09/e356158, ver STATE.md):
# tabla separada de `eventos_auditoria` porque el criterio Dado/Cuando/
# Entonces de RF-IB-009 pide "actor, fecha y los documentos accedidos", no un
# estado anterior/posterior -- una consulta no transiciona ningún estado.
# También de solo anexado (`session.add`, nunca `update`).
class EventoDeAccesoEntity(Base):
    __tablename__ = "eventos_de_acceso"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    actor: Mapped[str] = mapped_column(String, nullable=False)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    tipo: Mapped[str] = mapped_column(String, nullable=False)
    documentos_accedidos_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")


def _entrada_a_fila(entrada: EntradaDeIndice) -> EntradaDeIndiceEntity:
    return EntradaDeIndiceEntity(
        id=entrada.id,
        documento_id=entrada.documento_id,
        estado=entrada.estado.value,
        texto_extraido=entrada.texto_extraido,
        metadatos_json=json.dumps(entrada.metadatos),
        fecha_indexacion=entrada.fecha_indexacion,
    )


# `embedding` SIEMPRE None aquí -- CUARTO VETO real de Codex (ver STATE.md):
# la primera corrección de este archivo tenía `AlmacenDeEntradas` e
# `IndiceLexicoAutoalojado` leyendo `VectorDeEntradaEntity` directamente
# (`_embedding_de`), acoplando la orquestación al detalle de la variante
# AUTOALOJADA de `IndiceVectorial` -- si se sustituyera por
# `IndiceVectorialGestionado`, este almacén seguiría leyendo la tabla local
# (vacía) en vez del puerto, perdiendo el embedding en silencio. Ningún
# adaptador de persistencia debe conocer la implementación activa de OTRO
# puerto. Quien SÍ puede combinar los dos (porque recibe ambos puertos ya
# inyectados, sea cual sea la variante activa) es la capa de orquestación
# (api.py) -- ver `_con_embedding()` ahí.
def _fila_a_entrada(fila: EntradaDeIndiceEntity) -> EntradaDeIndice:
    return EntradaDeIndice(
        id=fila.id,
        documento_id=fila.documento_id,
        estado=EstadoEntradaDeIndice(fila.estado),
        texto_extraido=fila.texto_extraido,
        metadatos=json.loads(fila.metadatos_json),
        embedding=None,
        fecha_indexacion=fila.fecha_indexacion,
    )


def _evento_auditoria_a_fila(evento: EventoAuditoria) -> EventoAuditoriaEntity:
    return EventoAuditoriaEntity(
        actor=evento.actor,
        fecha=evento.fecha,
        tipo=evento.tipo,
        estado_anterior=evento.estado_anterior,
        estado_posterior=evento.estado_posterior,
    )


def _fila_a_evento_auditoria(fila: EventoAuditoriaEntity) -> EventoAuditoria:
    return EventoAuditoria(
        actor=fila.actor, fecha=fila.fecha, tipo=fila.tipo, estado_anterior=fila.estado_anterior, estado_posterior=fila.estado_posterior
    )


def _evento_acceso_a_fila(evento: EventoDeAcceso) -> EventoDeAccesoEntity:
    return EventoDeAccesoEntity(
        actor=evento.actor, fecha=evento.fecha, tipo=evento.tipo, documentos_accedidos_json=json.dumps(list(evento.documentos_accedidos))
    )


def _fila_a_evento_acceso(fila: EventoDeAccesoEntity) -> EventoDeAcceso:
    return EventoDeAcceso(
        actor=fila.actor, fecha=fila.fecha, tipo=fila.tipo, documentos_accedidos=tuple(json.loads(fila.documentos_accedidos_json))
    )


# Mismo patrón que AlmacenDeTextos en extraccion (T-41/T-37): un repositorio
# simple sobre una `Session` de SQLAlchemy; dominio.py no importa nada de
# SQLAlchemy.
class AlmacenDeEntradas:
    def __init__(self, session: Session):
        self._session = session

    # Entrada + evento de TRANSICIÓN (recepción/indexación/actualización) en
    # la MISMA transacción, con rollback explícito si falla el anexado del
    # evento -- ninguna transición confirmada puede quedar sin su evento
    # (mismo criterio que T-37/T-21/T-22).
    def guardar_con_evento(self, entrada: EntradaDeIndice, evento: EventoAuditoria) -> None:
        try:
            self._session.merge(_entrada_a_fila(entrada))
            self._session.add(_evento_auditoria_a_fila(evento))
            self._session.commit()
        except Exception:
            self._session.rollback()
            raise

    def obtener(self, id: str) -> EntradaDeIndice | None:
        fila = self._session.get(EntradaDeIndiceEntity, id)
        return _fila_a_entrada(fila) if fila else None

    def todas_indexadas(self) -> list[EntradaDeIndice]:
        filas = (
            self._session.execute(
                select(EntradaDeIndiceEntity).where(EntradaDeIndiceEntity.estado == EstadoEntradaDeIndice.INDEXADA.value)
            )
            .scalars()
            .all()
        )
        return [_fila_a_entrada(fila) for fila in filas]

    def eventos_de_auditoria(self) -> list[EventoAuditoria]:
        filas = self._session.execute(select(EventoAuditoriaEntity).order_by(EventoAuditoriaEntity.id)).scalars().all()
        return [_fila_a_evento_auditoria(fila) for fila in filas]

    # P-08/RF-IB-009: el evento de acceso se persiste solo (no acompaña
    # ninguna escritura de EntradaDeIndice -- una consulta no muta el
    # agregado), pero SIGUE siendo un `commit()` propio dentro de la MISMA
    # petición HTTP que resolvió la consulta (ver api.py) -- no un job
    # diferido ni un "mejor esfuerzo".
    def guardar_evento_de_acceso(self, evento: EventoDeAcceso) -> None:
        try:
            self._session.add(_evento_acceso_a_fila(evento))
            self._session.commit()
        except Exception:
            self._session.rollback()
            raise

    def eventos_de_acceso(self) -> list[EventoDeAcceso]:
        filas = self._session.execute(select(EventoDeAccesoEntity).order_by(EventoDeAccesoEntity.id)).scalars().all()
        return [_fila_a_evento_acceso(fila) for fila in filas]


# P-03 (VETO real de Codex sobre 22b6b09, ver STATE.md): implementación
# AUTOALOJADA real de `IndiceLexico` -- consulta directa contra Postgres (la
# base ya decidida desde F1.D1), sin salir a red. Es real: hace una consulta
# SQL genuina contra el texto ya persistido; `dominio.buscar()` aplica encima
# el filtro de metadatos/permiso, que sí es lógica de dominio pura.
class IndiceLexicoAutoalojado(IndiceLexico):
    def __init__(self, session: Session):
        self._session = session

    def indexar(self, entrada_id: str, contenido: str) -> None:
        # El texto ya queda persistido por AlmacenDeEntradas.guardar_con_evento
        # (mismo `commit` que la transición de indexación, misma tabla que
        # `buscar()` consulta abajo) -- a diferencia del caso de
        # IndiceVectorial (VETO real de Codex sobre 5a9f822, ver STATE.md),
        # aquí SÍ hay una única fuente de verdad real y consultable: no se
        # trata de "escribir en otro lado" sino de que la indexación léxica y
        # el agregado principal comparten la misma columna a esta escala de
        # referencia. `buscar()` demuestra la lectura real; no hace falta una
        # segunda escritura duplicada para que la capacidad sea genuina.
        pass

    def buscar(self, termino: str) -> list[EntradaDeIndice]:
        patron = f"%{termino}%"
        filas = (
            self._session.execute(
                select(EntradaDeIndiceEntity).where(
                    EntradaDeIndiceEntity.estado == EstadoEntradaDeIndice.INDEXADA.value,
                    EntradaDeIndiceEntity.texto_extraido.ilike(patron),
                )
            )
            .scalars()
            .all()
        )
        return [_fila_a_entrada(fila) for fila in filas]


# P-03 (VETO real de Codex sobre 5a9f822, ver STATE.md): implementación
# AUTOALOJADA real de `IndiceVectorial`, con almacenamiento y lectura
# propios en `indices_vectoriales` -- ya no un `pass` que dejaba el embedding
# persistiéndose por otra ruta. `indexar()` hace `session.add(...)` SIN
# commit propio: comparte la misma `Session` que `AlmacenDeEntradas`
# (inyectada desde la misma petición HTTP en api.py), así que la fila queda
# en la MISMA transacción que confirma `guardar_con_evento()` después --
# atómico con la entrada y su evento, sin necesitar un commit propio que
# rompería esa atomicidad. RF-IB-006 (similitud real) sigue sin
# implementarse aquí a propósito -- es el componente FICTICIO, el llamador
# entrega el orden ya calculado; este puerto solo guarda/recupera el vector.
class IndiceVectorialAutoalojado(IndiceVectorial):
    def __init__(self, session: Session):
        self._session = session

    def indexar(self, entrada_id: str, embedding: list[float]) -> None:
        self._session.merge(VectorDeEntradaEntity(entrada_id=entrada_id, embedding_json=json.dumps(embedding)))

    def obtener(self, entrada_id: str) -> list[float] | None:
        fila = self._session.get(VectorDeEntradaEntity, entrada_id)
        return json.loads(fila.embedding_json) if fila else None


def crear_fabrica_de_sesiones(url: str | None = None) -> sessionmaker[Session]:
    engine = create_engine(url or url_de_base_de_datos())
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine)
