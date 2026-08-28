import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from api import app, obtener_sesion
from persistencia import Base

FECHA = "2026-08-27T00:00:00Z"


@pytest.fixture()
def client():
    # StaticPool: mismo motivo que en normalizacion/tests/test_api.py — una
    # única conexión sqlite:///:memory: compartida durante todo el test.
    engine = create_engine(
        "sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    fabrica_de_sesiones = sessionmaker(bind=engine)

    def _sesion_de_prueba():
        sesion = fabrica_de_sesiones()
        try:
            yield sesion
        finally:
            sesion.close()

    app.dependency_overrides[obtener_sesion] = _sesion_de_prueba
    yield TestClient(app)
    app.dependency_overrides.clear()


def _procedencia(lote_o_flujo_id="lote-001", item_ingesta_id="item-001", unidad_documental_id="unidad-1"):
    return {
        "fuente": "escaner-sala-3",
        "fecha": FECHA,
        "disparador": "carga_por_lote",
        "lote_o_flujo_id": lote_o_flujo_id,
        "item_ingesta_id": item_ingesta_id,
        "unidad_documental_id": unidad_documental_id,
    }


def _recibir_unidad(client, id_, unidad_documental_candidata_id="unidad-1", lote_o_flujo_id="lote-001", actor="sistema-extraccion"):
    return client.post(
        "/textos",
        json={
            "id": id_,
            "unidad_documental_candidata_id": unidad_documental_candidata_id,
            "procedencia": _procedencia(lote_o_flujo_id=lote_o_flujo_id, unidad_documental_id=unidad_documental_candidata_id),
            "actor": actor,
            "fecha": FECHA,
        },
    )


def _determinar_soporte(client, id_, soporte, actor="sistema-extraccion"):
    return client.post(f"/textos/{id_}/soporte", json={"soporte": soporte, "actor": actor, "fecha": FECHA})


def _extraer_born_digital(client, id_, contenido="contenido embebido", actor="sistema-extraccion"):
    return client.post(f"/textos/{id_}/extraccion-born-digital", json={"contenido": contenido, "actor": actor, "fecha": FECHA})


def _recibir_sugerencia_ocr(client, id_, calidad=0.73, modelo_id="ocr-ficticio-v0", contenido="texto reconocido"):
    return client.post(
        f"/textos/{id_}/sugerencia-ocr",
        json={"modelo_id": modelo_id, "contenido": contenido, "calidad": calidad, "evidencia": ["pagina-1"], "fecha": FECHA},
    )


def _confirmar(client, id_, actor="archivista-1"):
    return client.post(f"/textos/{id_}/confirmacion", json={"actor": actor, "fecha": FECHA})


def _validar(client, id_, condicion, actor="sistema-extraccion"):
    return client.post(f"/textos/{id_}/validacion", json={"condicion": condicion, "actor": actor, "fecha": FECHA})


def _flujo_escaneo_confirmado(client, id_, lote_o_flujo_id="lote-001", calidad=0.73):
    _recibir_unidad(client, id_, lote_o_flujo_id=lote_o_flujo_id)
    _determinar_soporte(client, id_, "ESCANEO")
    _recibir_sugerencia_ocr(client, id_, calidad=calidad)
    return _confirmar(client, id_)


# RF-EX-001 · Recepción de unidades documentales normalizadas
class TestRecepcion:
    def test_post_textos_crea_un_texto_pendiente_de_extraccion(self, client):
        response = _recibir_unidad(client, "texto-1")

        assert response.status_code == 201
        assert response.json()["estado"] == "PENDIENTE_DE_EXTRACCION"

    def test_get_textos_de_un_id_inexistente_responde_404(self, client):
        response = client.get("/textos/no-existe")

        assert response.status_code == 404

    def test_get_textos_refleja_lo_persistido_en_una_peticion_previa(self, client):
        _recibir_unidad(client, "texto-consulta")

        response = client.get("/textos/texto-consulta")

        assert response.status_code == 200
        assert response.json()["unidad_documental_candidata_id"] == "unidad-1"


# RF-EX-002 · Determinación de soporte
class TestDeterminacionDeSoporte:
    def test_marcar_born_digital_no_cambia_el_estado(self, client):
        _recibir_unidad(client, "texto-2")

        response = _determinar_soporte(client, "texto-2", "BORN_DIGITAL")

        assert response.json()["soporte"] == "BORN_DIGITAL"
        assert response.json()["estado"] == "PENDIENTE_DE_EXTRACCION"

    def test_marcar_escaneo_no_cambia_el_estado(self, client):
        _recibir_unidad(client, "texto-3")

        response = _determinar_soporte(client, "texto-3", "ESCANEO")

        assert response.json()["soporte"] == "ESCANEO"


# RF-EX-003 · Extracción determinística de texto embebido (born-digital)
class TestExtraccionBornDigital:
    def test_extraer_born_digital_deja_extraido_con_calidad_maxima(self, client):
        _recibir_unidad(client, "texto-4")
        _determinar_soporte(client, "texto-4", "BORN_DIGITAL")

        response = _extraer_born_digital(client, "texto-4")

        assert response.json()["estado"] == "EXTRAIDO"
        assert response.json()["contenido"] == "contenido embebido"
        assert response.json()["calidad"] == 1.0

    def test_extraer_born_digital_sin_soporte_determinado_responde_409(self, client):
        _recibir_unidad(client, "texto-5")

        response = _extraer_born_digital(client, "texto-5")

        assert response.status_code == 409
        assert response.json()["error"]


# RF-EX-004 · Sugerencia de OCR (componente FICTICIO) — no materializa sola
class TestSugerenciaOcr:
    def test_recibir_sugerencia_de_ocr_no_materializa_el_texto(self, client):
        _recibir_unidad(client, "texto-6")
        _determinar_soporte(client, "texto-6", "ESCANEO")

        response = _recibir_sugerencia_ocr(client, "texto-6", calidad=0.73)

        assert response.status_code == 200
        assert response.json()["estado"] == "PENDIENTE_DE_EXTRACCION"
        assert response.json()["contenido"] is None
        assert response.json()["sugerencia_ocr"]["modelo_id"] == "ocr-ficticio-v0"


# RF-EX-011 · Confirmación humana de la extracción vía OCR
class TestConfirmacionDeExtraccion:
    def test_confirmar_deja_extraido_con_actor_y_fecha(self, client):
        _recibir_unidad(client, "texto-7")
        _determinar_soporte(client, "texto-7", "ESCANEO")
        _recibir_sugerencia_ocr(client, "texto-7", calidad=0.73)

        response = _confirmar(client, "texto-7", actor="archivista-1")

        assert response.json()["estado"] == "EXTRAIDO"
        assert response.json()["contenido"] == "texto reconocido"
        assert response.json()["calidad"] == 0.73

    def test_confirmar_sin_sugerencia_pendiente_responde_409(self, client):
        _recibir_unidad(client, "texto-8")
        _determinar_soporte(client, "texto-8", "ESCANEO")

        response = _confirmar(client, "texto-8")

        assert response.status_code == 409
        assert response.json()["error"]


# RF-EX-005 · Estratificación de calidad de la extracción
class TestEstratificacionDeCalidad:
    def test_texto_extraido_expone_calidad_y_soporte_born_digital(self, client):
        _recibir_unidad(client, "texto-9")
        _determinar_soporte(client, "texto-9", "BORN_DIGITAL")
        _extraer_born_digital(client, "texto-9")

        response = client.get("/textos/texto-9")

        assert response.json()["calidad"] == 1.0
        assert response.json()["soporte"] == "BORN_DIGITAL"

    def test_texto_extraido_expone_calidad_y_soporte_escaneo(self, client):
        _flujo_escaneo_confirmado(client, "texto-10", calidad=0.6)

        response = client.get("/textos/texto-10")

        assert response.json()["calidad"] == 0.6
        assert response.json()["soporte"] == "ESCANEO"


# RF-EX-006 · Enrutamiento de baja confianza a revisión humana
class TestRevisionPorBajaConfianza:
    def test_get_textos_pendientes_de_revision_incluye_solo_los_bajo_el_umbral(self, client):
        _flujo_escaneo_confirmado(client, "texto-11", calidad=0.3)
        _flujo_escaneo_confirmado(client, "texto-12", calidad=0.9)

        response = client.get("/textos/pendientes-de-revision", params={"umbral": 0.5})

        assert response.status_code == 200
        ids = [t["id"] for t in response.json()]
        assert "texto-11" in ids
        assert "texto-12" not in ids

    def test_get_textos_pendientes_de_revision_no_rompe_el_ruteo_de_get_textos_id(self, client):
        _recibir_unidad(client, "texto-13")

        response = client.get("/textos/texto-13")

        assert response.status_code == 200
        assert response.json()["id"] == "texto-13"


# RF-EX-007 · Propagación de procedencia
class TestPropagacionDeProcedencia:
    def test_texto_extraido_conserva_la_procedencia_completa(self, client):
        _recibir_unidad(client, "texto-14", unidad_documental_candidata_id="unidad-x")

        response = client.get("/textos/texto-14")

        assert response.json()["procedencia"]["unidad_documental_id"] == "unidad-x"
        assert response.json()["procedencia"]["fuente"] == "escaner-sala-3"


# RF-EX-008 · Cero pérdida silenciosa
class TestConteoPorEstado:
    def test_conteo_cuadra_cuando_todos_los_textos_del_lote_llegaron_a_un_terminal(self, client):
        _recibir_unidad(client, "texto-15", lote_o_flujo_id="lote-conteo")
        _validar(client, "texto-15", "FORMATO_NO_SOPORTADO")

        response = client.get("/lotes/lote-conteo/conteo")

        assert response.status_code == 200
        assert response.json()["total"] == 1
        assert response.json()["terminales"] == 1
        assert response.json()["sin_perdida_silenciosa"] is True

    def test_conteo_no_cuadra_cuando_hay_un_texto_no_terminal(self, client):
        _recibir_unidad(client, "texto-16", lote_o_flujo_id="lote-conteo-2")

        response = client.get("/lotes/lote-conteo-2/conteo")

        assert response.json()["sin_perdida_silenciosa"] is False


# RF-EX-009 · Validación y cuarentena de extracciones
class TestValidacionYCuarentena:
    def test_validacion_corrupto_queda_en_cuarentena_con_razon(self, client):
        _recibir_unidad(client, "texto-17")

        response = _validar(client, "texto-17", "CORRUPTO")

        assert response.json()["estado"] == "EN_CUARENTENA"
        assert response.json()["razon"]

    def test_validacion_formato_no_soportado_queda_rechazado(self, client):
        _recibir_unidad(client, "texto-18")

        response = _validar(client, "texto-18", "FORMATO_NO_SOPORTADO")

        assert response.json()["estado"] == "RECHAZADO"


# RF-EX-010 · Entrega a Clasificación, Enriquecimiento e Indexación y Búsqueda
class TestEntrega:
    def test_entregar_un_texto_extraido_devuelve_procedencia_y_calidad(self, client):
        _recibir_unidad(client, "texto-19")
        _determinar_soporte(client, "texto-19", "BORN_DIGITAL")
        _extraer_born_digital(client, "texto-19")

        response = client.get("/textos/texto-19/entrega")

        assert response.status_code == 200
        assert response.json()["calidad"] == 1.0

    def test_entregar_un_texto_no_extraido_responde_409(self, client):
        _recibir_unidad(client, "texto-20")

        response = client.get("/textos/texto-20/entrega")

        assert response.status_code == 409


# P-08 · toda transición queda en una bitácora consultable, con actor, fecha
# y estado anterior/posterior — desde el primer commit de este contexto
# (T-40), no como corrección posterior (a diferencia de normalizacion/T-37).
class TestAuditoria:
    def test_cada_transicion_de_un_texto_queda_registrada_en_la_bitacora(self, client):
        _recibir_unidad(client, "texto-21")
        _validar(client, "texto-21", "CORRUPTO", actor="sistema-validacion")

        eventos = client.get("/eventos-auditoria").json()

        tipos = [evento["tipo"] for evento in eventos]
        assert "TEXTO_EXTRAIDO_RECIBIDO" in tipos
        assert "VALIDACION_APLICADA" in tipos
        evento_validacion = next(e for e in eventos if e["tipo"] == "VALIDACION_APLICADA")
        assert evento_validacion["actor"] == "sistema-validacion"
        assert evento_validacion["estado_anterior"] == "PENDIENTE_DE_EXTRACCION"
        assert evento_validacion["estado_posterior"] == "EN_CUARENTENA"
