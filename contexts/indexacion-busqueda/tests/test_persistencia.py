from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from dominio import (
    EventoAuditoria,
    EventoDeAcceso,
    aplicar_permisos_y_construir_evento,
    crear_entrada_pendiente,
    indexar,
    recibir_documento_materializado,
)
from persistencia import AlmacenDeEntradas, Base, IndiceLexicoAutoalojado, IndiceVectorialAutoalojado

FECHA = datetime.fromisoformat("2026-08-30T00:00:00+00:00")


class _IndiceLexicoNoOp:
    def indexar(self, entrada_id, contenido):
        pass

    def buscar(self, termino):
        return []


class _IndiceVectorialNoOp:
    def indexar(self, entrada_id, embedding):
        pass

    def obtener(self, entrada_id):
        return None


class _PermiteTodo:
    def tiene_permiso(self, actor, documento_id):
        return True


@pytest.fixture()
def sesion():
    # StaticPool: misma razón que en normalizacion/extraccion (T-37/T-41) --
    # una única conexión sqlite:///:memory: compartida durante todo el test.
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine)()


@pytest.fixture()
def almacen(sesion):
    return AlmacenDeEntradas(sesion)


# P-08 (VETO real de Codex sobre 22b6b09/e356158, ver STATE.md): la entrada
# de índice y su evento de TRANSICIÓN se escriben en una única transacción --
# nunca puede quedar una persistida sin el otro.
class TestAtomicidadDeGuardarConEvento:
    def test_guardar_con_evento_persiste_entrada_y_evento_juntos(self, almacen):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="x", metadatos={})
        pendiente, evento = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)

        almacen.guardar_con_evento(pendiente, evento)

        assert almacen.obtener("entrada-1") is not None
        assert len(almacen.eventos_de_auditoria()) == 1

    def test_si_falla_el_anexado_del_evento_la_entrada_tampoco_queda_persistida(self, almacen):
        documento = recibir_documento_materializado(documento_id="documento-2", texto_extraido="x", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-2", documento, actor="sistema", fecha=FECHA)
        # `actor=None` viola la restricción NOT NULL de `eventos_auditoria.actor`
        # (violación real de la base de datos, no un doble simulado) -- el
        # rollback explícito de `guardar_con_evento` debe deshacer también el
        # `merge` de la entrada hecho en la misma transacción.
        evento_invalido = EventoAuditoria(
            actor=None, fecha=FECHA, tipo="ENTRADA_RECIBIDA", estado_anterior=None, estado_posterior="X"
        )

        with pytest.raises(IntegrityError):
            almacen.guardar_con_evento(pendiente, evento_invalido)

        assert almacen.obtener("entrada-2") is None
        assert almacen.eventos_de_auditoria() == []


# P-08/RF-IB-009 (VETO real de Codex, ver STATE.md): el evento de acceso
# debe quedar PERSISTIDO de forma solo-anexado -- este test hace la consulta
# de dominio real y DESPUÉS lo verifica leyendo la bitácora, nunca inspecciona
# solo el valor devuelto por la función de dominio (eso es exactamente lo
# que Codex señaló como insuficiente).
class TestPersistenciaDelEventoDeAcceso:
    def test_guardar_evento_de_acceso_lo_deja_leible_en_la_bitacora(self, almacen):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="contenido", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)
        indexada, _ = indexar(
            pendiente,
            texto_extraido="contenido",
            metadatos={},
            embedding=[0.1],
            indice_lexico=_IndiceLexicoNoOp(),
            indice_vectorial=_IndiceVectorialNoOp(),
            actor="sistema",
            fecha=FECHA,
        )
        almacen.guardar_con_evento(indexada, EventoAuditoria(actor="sistema", fecha=FECHA, tipo="X", estado_anterior=None, estado_posterior="INDEXADA"))

        # La operación de consulta REAL (no un evento construido a mano).
        _, evento = aplicar_permisos_y_construir_evento(
            [indexada], verificador=_PermiteTodo(), actor="ana", fecha=FECHA, tipo="BUSQUEDA_LEXICA"
        )
        assert almacen.eventos_de_acceso() == []  # todavía no se persistió

        almacen.guardar_evento_de_acceso(evento)

        persistidos = almacen.eventos_de_acceso()
        assert len(persistidos) == 1
        # sqlite:///:memory: no conserva tzinfo en el redondeo (limitación real
        # de SQLite, no de Postgres -- por eso se compara la fecha aparte, sin
        # tzinfo, en vez de una igualdad estricta del dataclass completo).
        assert persistidos[0].actor == "ana"
        assert persistidos[0].tipo == "BUSQUEDA_LEXICA"
        assert persistidos[0].documentos_accedidos == ("documento-1",)
        assert persistidos[0].fecha.replace(tzinfo=None) == FECHA.replace(tzinfo=None)

    def test_si_falla_el_guardado_del_evento_de_acceso_no_queda_nada_a_medias(self, almacen):
        evento_invalido = EventoDeAcceso(actor=None, fecha=FECHA, tipo="BUSQUEDA_LEXICA", documentos_accedidos=("documento-1",))

        with pytest.raises(IntegrityError):
            almacen.guardar_evento_de_acceso(evento_invalido)

        assert almacen.eventos_de_acceso() == []


# P-03 (VETO real de Codex sobre 22b6b09, ver STATE.md): IndiceLexicoAutoalojado
# hace una consulta SQL real contra el texto ya persistido -- no un doble.
class TestIndiceLexicoAutoalojado:
    def test_buscar_encuentra_una_entrada_indexada_por_termino_real(self, sesion, almacen):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="x", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)
        indexada, evento = indexar(
            pendiente,
            texto_extraido="el gato subió al tejado",
            metadatos={},
            embedding=[0.1],
            indice_lexico=_IndiceLexicoNoOp(),
            indice_vectorial=_IndiceVectorialNoOp(),
            actor="sistema",
            fecha=FECHA,
        )
        almacen.guardar_con_evento(indexada, evento)

        indice = IndiceLexicoAutoalojado(sesion)
        resultados = indice.buscar("tejado")

        assert [r.id for r in resultados] == ["entrada-1"]

    def test_buscar_no_encuentra_una_entrada_pendiente_de_indexacion(self, sesion, almacen):
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="x", metadatos={})
        pendiente, evento = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)
        almacen.guardar_con_evento(pendiente, evento)

        indice = IndiceLexicoAutoalojado(sesion)

        assert indice.buscar("x") == []


# P-03 (VETO real de Codex sobre 5a9f822, ver STATE.md): IndiceVectorialAutoalojado
# tiene almacenamiento y lectura reales en su propia tabla -- ya no un doble
# ni una fachada vacía. `indexar()` no comitea por su cuenta: comparte la
# misma `Session` que `AlmacenDeEntradas.guardar_con_evento()`, así que este
# test demuestra la atomicidad real (una sola transacción) además de la
# capacidad de lectura.
class TestIndiceVectorialAutoalojado:
    def test_indexar_y_obtener_persisten_y_recuperan_el_embedding_real(self, sesion, almacen):
        indice_vectorial = IndiceVectorialAutoalojado(sesion)
        documento = recibir_documento_materializado(documento_id="documento-1", texto_extraido="x", metadatos={})
        pendiente, _ = crear_entrada_pendiente("entrada-1", documento, actor="sistema", fecha=FECHA)
        indexada, evento = indexar(
            pendiente,
            texto_extraido="x",
            metadatos={},
            embedding=[0.5, 0.25, 0.1],
            indice_lexico=_IndiceLexicoNoOp(),
            indice_vectorial=indice_vectorial,
            actor="sistema",
            fecha=FECHA,
        )
        # Todavía sin commit -- indice_vectorial.indexar() solo hizo
        # session.merge(), staged en la MISMA transacción que guardar_con_evento
        # confirma a continuación (atomicidad real, no una segunda escritura
        # independiente).
        almacen.guardar_con_evento(indexada, evento)

        assert indice_vectorial.obtener("entrada-1") == [0.5, 0.25, 0.1]
        # QUINTO VETO real de Codex sobre 53ce657 (ver STATE.md):
        # AlmacenDeEntradas ya NO lee `indices_vectoriales` -- eso acoplaba la
        # orquestación a la variante AUTOALOJADA. `obtener()` siempre
        # devuelve embedding=None; combinarlo con el puerto es
        # responsabilidad exclusiva de api.py (`_con_embedding`), el único
        # lugar que recibe ambos puertos sin conocer la variante activa.
        assert almacen.obtener("entrada-1").embedding is None

    def test_obtener_devuelve_none_si_nunca_se_indexo(self, sesion):
        indice_vectorial = IndiceVectorialAutoalojado(sesion)

        assert indice_vectorial.obtener("entrada-inexistente") is None
