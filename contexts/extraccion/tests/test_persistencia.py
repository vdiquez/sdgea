from datetime import datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from dominio import EventoAuditoria, ProcedenciaHeredada, recibir_unidad
from persistencia import AlmacenDeTextos, Base, EventoAuditoriaEntity, _tabla_eventos_heredada

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


# VETO real de Codex sobre T-56 (ver STATE.md/T-58): esta tabla compartía el
# nombre genérico `eventos_auditoria` con records-custodia y normalizacion en
# el mismo Postgres -- GET /eventos-auditoria de cualquiera de los tres
# devolvía eventos de los otros. Guarda de regresión directa (mismo criterio
# que TestAislamientoDeTablasPorContexto en indexacion-busqueda, T-56): si
# alguien revierte el prefijo `ex_`, esto falla en rojo antes de llegar a un
# Postgres compartido real.
class TestAislamientoDeTablaPorContexto:
    def test_la_tabla_de_eventos_tiene_prefijo_propio_unico(self):
        assert EventoAuditoriaEntity.__tablename__ == "ex_eventos_auditoria"


# SEGUNDO VETO real de Codex sobre T-58 (ver STATE.md): la primera versión de
# esta prueba (y del código) EXCLUÍA "VALIDACION_APLICADA" de la
# recuperación -- Codex vetó también eso: "documentar la omisión tampoco la
# corrige", P-08 exige que toda transición histórica siga siendo recuperable.
# Esta prueba siembra la tabla HEREDADA directamente -- como si viniera de
# antes de T-58, con una fila propia de extraccion, una fila de OTRO contexto
# simulado (nunca debe aparecer) y una fila AMBIGUA ("VALIDACION_APLICADA",
# que normalizacion también usa sobre la misma tabla compartida, sin ninguna
# columna de origen) -- y comprueba que `eventos_de_auditoria()` recupera lo
# propio inequívoco (marcado `origen_verificado=True`) Y lo ambiguo (marcado
# `origen_verificado=False`, nunca omitido ni atribuido en falso), nunca lo
# ajeno, además de lo nuevo.
class TestLecturaCompatibleDeLaTablaHeredada:
    def test_recupera_el_historial_propio_y_el_ambiguo_marcado_ignora_el_ajeno(self):
        engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        Base.metadata.create_all(engine)
        _tabla_eventos_heredada.create(engine)
        sesion = sessionmaker(bind=engine)()
        sesion.execute(
            _tabla_eventos_heredada.insert(),
            [
                {
                    "actor": "sistema-extraccion",
                    "fecha": datetime.fromisoformat("2026-08-27T00:00:00+00:00"),
                    "tipo": "TEXTO_EXTRAIDO_RECIBIDO",
                    "estado_anterior": None,
                    "estado_posterior": "PENDIENTE_DE_EXTRACCION",
                },
                {
                    # Fila heredada de OTRO contexto -- nunca debe aparecer aquí.
                    "actor": "sistema-normalizacion",
                    "fecha": datetime.fromisoformat("2026-08-27T00:05:00+00:00"),
                    "tipo": "UNIDAD_RECIBIDA",
                    "estado_anterior": None,
                    "estado_posterior": "PENDIENTE_DE_LIMITES",
                },
                {
                    # Ambigua -- sin forma honesta de atribuirla a UN solo
                    # contexto, pero debe seguir siendo recuperable (P-08).
                    "actor": "sistema-normalizacion",
                    "fecha": datetime.fromisoformat("2026-08-27T00:10:00+00:00"),
                    "tipo": "VALIDACION_APLICADA",
                    "estado_anterior": "PENDIENTE_DE_LIMITES",
                    "estado_posterior": "NORMALIZADA",
                },
            ],
        )
        sesion.commit()
        almacen = AlmacenDeTextos(sesion)
        texto, evento = recibir_unidad(
            id="texto-heredado-1",
            unidad_documental_candidata_id="unidad-1",
            procedencia=PROCEDENCIA,
            actor="sistema-extraccion",
            fecha=PROCEDENCIA.fecha,
        )
        almacen.guardar_con_evento(texto, evento)

        eventos = almacen.eventos_de_auditoria()
        tipos = [e.tipo for e in eventos]

        assert tipos == ["TEXTO_EXTRAIDO_RECIBIDO", "TEXTO_EXTRAIDO_RECIBIDO", "VALIDACION_APLICADA"]
        assert "UNIDAD_RECIBIDA" not in tipos
        propios = [e for e in eventos if e.tipo == "TEXTO_EXTRAIDO_RECIBIDO"]
        assert all(e.origen_verificado for e in propios)
        ambiguo = next(e for e in eventos if e.tipo == "VALIDACION_APLICADA")
        assert ambiguo.origen_verificado is False

    def test_si_la_tabla_heredada_no_existe_no_falla(self, almacen):
        # Volumen nuevo, nunca usado antes de T-58 -- has_table() devuelve
        # False y el fallback se salta silenciosamente, sin lanzar.
        assert almacen.eventos_de_auditoria() == []


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
        # `actor=None` viola la restricción NOT NULL de `ex_eventos_auditoria.actor`
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
