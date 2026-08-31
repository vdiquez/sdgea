from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from dominio import EventoAuditoria, ProcedenciaHeredada, recibir_item
from persistencia import AlmacenDeUnidades, Base, EventoAuditoriaEntity

PROCEDENCIA = ProcedenciaHeredada(
    fuente="escaner-sala-3",
    fecha=datetime.fromisoformat("2026-08-26T00:00:00+00:00"),
    disparador="carga_por_lote",
    lote_o_flujo_id="lote-001",
    item_ingesta_id="item-001",
)


@pytest.fixture()
def almacen():
    # StaticPool: misma razón que en test_api.py — una única conexión
    # sqlite:///:memory: compartida durante todo el test.
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    sesion = sessionmaker(bind=engine)()
    return AlmacenDeUnidades(sesion)


# VETO real de Codex sobre T-56 (ver STATE.md/T-58): esta tabla compartía el
# nombre genérico `eventos_auditoria` con records-custodia y extraccion en el
# mismo Postgres -- GET /eventos-auditoria de cualquiera de los tres devolvía
# eventos de los otros. Guarda de regresión directa (mismo criterio que
# TestAislamientoDeTablasPorContexto en indexacion-busqueda, T-56): si alguien
# revierte el prefijo `no_`, esto falla en rojo antes de llegar a un Postgres
# compartido real.
class TestAislamientoDeTablaPorContexto:
    def test_la_tabla_de_eventos_tiene_prefijo_propio_unico(self):
        assert EventoAuditoriaEntity.__tablename__ == "no_eventos_auditoria"


# P-08 (hallazgo V-01, ver REVIEW.md) · la unidad y su evento de auditoría se
# escriben en una única transacción — mismo criterio verificado en Kotlin por
# CustodiaTransaccionalTest/RecepcionDeSugerenciasTransaccionalTest (T-21/T-22).
class TestAtomicidadDeGuardarConEvento:
    def test_guardar_con_evento_persiste_unidad_y_evento_juntos(self, almacen):
        unidad, evento = recibir_item(
            id="unidad-1",
            lote_id="lote-001",
            item_ingesta_id="item-001",
            procedencia=PROCEDENCIA,
            huella_de_contenido=None,
            es_caso_trivial=False,
            actor="sistema-normalizacion",
        )

        almacen.guardar_con_evento(unidad, evento)

        assert almacen.buscar("unidad-1") is not None
        assert len(almacen.eventos_de_auditoria()) == 1

    def test_si_falla_el_anexado_del_evento_la_unidad_tampoco_queda_persistida(self, almacen):
        unidad, _ = recibir_item(
            id="unidad-2",
            lote_id="lote-001",
            item_ingesta_id="item-001",
            procedencia=PROCEDENCIA,
            huella_de_contenido=None,
            es_caso_trivial=False,
            actor="sistema-normalizacion",
        )
        # `actor=None` viola la restricción NOT NULL de `no_eventos_auditoria.actor`
        # (una violación real de la base de datos, no un doble simulado) — el
        # rollback explícito de `guardar_con_evento` debe deshacer también el
        # `merge` de la unidad hecho en la misma transacción.
        evento_invalido = EventoAuditoria(
            actor=None, fecha=PROCEDENCIA.fecha, tipo="UNIDAD_RECIBIDA", estado_anterior=None, estado_posterior="X"
        )

        with pytest.raises(IntegrityError):
            almacen.guardar_con_evento(unidad, evento_invalido)

        assert almacen.buscar("unidad-2") is None
        assert almacen.eventos_de_auditoria() == []
