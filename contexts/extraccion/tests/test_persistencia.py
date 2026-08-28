from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from dominio import EventoAuditoria, ProcedenciaHeredada, recibir_unidad
from persistencia import AlmacenDeTextos, Base

PROCEDENCIA = ProcedenciaHeredada(
    fuente="escaner-sala-3",
    fecha=datetime.fromisoformat("2026-08-27T00:00:00+00:00"),
    disparador="carga_por_lote",
    lote_o_flujo_id="lote-001",
    item_ingesta_id="item-001",
    unidad_documental_id="unidad-1",
)


@pytest.fixture()
def almacen():
    # StaticPool: misma razón que en normalizacion/tests/test_persistencia.py
    # — una única conexión sqlite:///:memory: compartida durante todo el test.
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    sesion = sessionmaker(bind=engine)()
    return AlmacenDeTextos(sesion)


# P-08 (mismo criterio verificado en normalizacion/T-37 y en Kotlin por
# CustodiaTransaccionalTest/RecepcionDeSugerenciasTransaccionalTest, T-21/T-22):
# el texto extraído y su evento de auditoría se escriben en una única
# transacción — nunca puede quedar uno persistido sin el otro.
class TestAtomicidadDeGuardarConEvento:
    def test_guardar_con_evento_persiste_texto_y_evento_juntos(self, almacen):
        texto, evento = recibir_unidad(
            id="texto-1",
            unidad_documental_candidata_id="unidad-1",
            procedencia=PROCEDENCIA,
            actor="sistema-extraccion",
            fecha=PROCEDENCIA.fecha,
        )

        almacen.guardar_con_evento(texto, evento)

        assert almacen.buscar("texto-1") is not None
        assert len(almacen.eventos_de_auditoria()) == 1

    def test_si_falla_el_anexado_del_evento_el_texto_tampoco_queda_persistido(self, almacen):
        texto, _ = recibir_unidad(
            id="texto-2",
            unidad_documental_candidata_id="unidad-1",
            procedencia=PROCEDENCIA,
            actor="sistema-extraccion",
            fecha=PROCEDENCIA.fecha,
        )
        # `actor=None` viola la restricción NOT NULL de `eventos_auditoria.actor`
        # (una violación real de la base de datos, no un doble simulado) — el
        # rollback explícito de `guardar_con_evento` debe deshacer también el
        # `merge` del texto hecho en la misma transacción.
        evento_invalido = EventoAuditoria(
            actor=None, fecha=PROCEDENCIA.fecha, tipo="TEXTO_EXTRAIDO_RECIBIDO", estado_anterior=None, estado_posterior="X"
        )

        with pytest.raises(IntegrityError):
            almacen.guardar_con_evento(texto, evento_invalido)

        assert almacen.buscar("texto-2") is None
        assert almacen.eventos_de_auditoria() == []
