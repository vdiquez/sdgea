import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from api import app, obtener_sesion
from persistencia import Base

FECHA = "2026-08-26T00:00:00Z"


@pytest.fixture()
def client():
    # StaticPool: sqlite:///:memory: crea una base nueva y vacía por cada
    # conexión que abre el pool por defecto — sin esto, `create_all` y las
    # sesiones de cada petición verían bases distintas. StaticPool fuerza una
    # única conexión compartida durante todo el test.
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


def _procedencia():
    return {"fuente": "escaner-sala-3", "fecha": FECHA, "disparador": "carga_por_lote", "lote_o_flujo_id": "lote-001"}


def _crear_unidad(client, id_, lote_id, item_id, es_caso_trivial, huella=None, actor="sistema-normalizacion"):
    return client.post(
        "/unidades",
        json={
            "id": id_,
            "lote_id": lote_id,
            "item_ingesta_id": item_id,
            "procedencia": _procedencia(),
            "huella_de_contenido": huella,
            "es_caso_trivial": es_caso_trivial,
            "actor": actor,
        },
    )


def _normalizar(client, id_, actor="sistema-normalizacion"):
    return client.post(
        f"/unidades/{id_}/normalizacion",
        json={"formato_normalizado": "application/pdf", "actor": actor, "fecha": FECHA},
    )


def _validar(client, id_, condicion, actor="sistema-normalizacion"):
    return client.post(f"/unidades/{id_}/validacion", json={"condicion": condicion, "actor": actor, "fecha": FECHA})


def _entregar(client, id_, actor="sistema-normalizacion"):
    return client.post(f"/unidades/{id_}/entrega", json={"actor": actor, "fecha": FECHA})


# RF-NO-001/003 · Recepción de ítems validados y caso trivial
class TestRecepcionYConsulta:
    def test_post_unidades_crea_una_unidad_pendiente_de_limites(self, client):
        response = _crear_unidad(client, "unidad-1", "lote-001", "item-001", es_caso_trivial=False)

        assert response.status_code == 201
        assert response.json()["estado"] == "PENDIENTE_DE_LIMITES"

    def test_post_unidades_con_caso_trivial_confirma_limites_de_inmediato(self, client):
        response = _crear_unidad(client, "unidad-2", "lote-001", "item-002", es_caso_trivial=True)

        assert response.json()["estado"] == "LIMITES_CONFIRMADOS"

    def test_get_unidades_de_un_id_inexistente_responde_404(self, client):
        response = client.get("/unidades/no-existe")

        assert response.status_code == 404

    def test_get_unidades_refleja_lo_persistido_en_una_peticion_previa(self, client):
        _crear_unidad(client, "unidad-consulta", "lote-001", "item-c", es_caso_trivial=False)

        response = client.get("/unidades/unidad-consulta")

        assert response.status_code == 200
        assert response.json()["lote_id"] == "lote-001"


# RF-NO-002/004 · Sugerencia y confirmación de límites
class TestSugerenciaYConfirmacion:
    def test_una_sugerencia_de_limites_no_confirma_la_unidad_por_si_sola(self, client):
        _crear_unidad(client, "unidad-3", "lote-002", "item-003", es_caso_trivial=False)

        response = client.post(
            "/unidades/unidad-3/sugerencia-limites",
            json={"modelo_id": "emisor-ficticio-v0", "evidencia": ["pagina-1"], "confianza": 0.4, "fecha": FECHA},
        )

        assert response.status_code == 200
        assert response.json()["estado"] == "PENDIENTE_DE_LIMITES"
        assert response.json()["sugerencia_de_limites"]["modelo_id"] == "emisor-ficticio-v0"

    def test_confirmar_limites_los_deja_confirmados_con_actor_y_fecha(self, client):
        _crear_unidad(client, "unidad-4", "lote-002", "item-004", es_caso_trivial=False)

        response = client.post("/unidades/unidad-4/confirmacion-limites", json={"actor": "archivista-1", "fecha": FECHA})

        assert response.json()["estado"] == "LIMITES_CONFIRMADOS"
        assert response.json()["confirmacion_limites"]["actor"] == "archivista-1"

    def test_confirmar_limites_dos_veces_responde_409(self, client):
        _crear_unidad(client, "unidad-5", "lote-002", "item-005", es_caso_trivial=True)

        response = client.post("/unidades/unidad-5/confirmacion-limites", json={"actor": "archivista-1", "fecha": FECHA})

        assert response.status_code == 409
        assert response.json()["error"]


# RF-NO-005/009 · Normalización, validación y cuarentena
class TestNormalizacionYCuarentena:
    def test_normalizar_una_unidad_con_limites_confirmados(self, client):
        _crear_unidad(client, "unidad-6", "lote-003", "item-006", es_caso_trivial=True)

        response = _normalizar(client, "unidad-6")

        assert response.json()["estado"] == "NORMALIZADA"
        assert response.json()["formato_normalizado"] == "application/pdf"

    def test_validacion_corrupto_queda_en_cuarentena_con_razon(self, client):
        _crear_unidad(client, "unidad-7", "lote-003", "item-007", es_caso_trivial=False)

        response = _validar(client, "unidad-7", "CORRUPTO")

        assert response.json()["estado"] == "EN_CUARENTENA"
        assert response.json()["razon"]

    def test_validacion_formato_no_soportado_queda_rechazada(self, client):
        _crear_unidad(client, "unidad-8", "lote-003", "item-008", es_caso_trivial=False)

        response = _validar(client, "unidad-8", "FORMATO_NO_SOPORTADO")

        assert response.json()["estado"] == "RECHAZADA"


# RF-NO-006/010 · Entrega a Extracción y deduplicación
class TestEntregaYDeduplicacion:
    def test_flujo_no_trivial_completo_hasta_entrega_a_extraccion(self, client):
        _crear_unidad(client, "unidad-9", "lote-004", "item-009", es_caso_trivial=False, huella="huella-a")
        client.post(
            "/unidades/unidad-9/sugerencia-limites",
            json={"modelo_id": "emisor-ficticio-v0", "evidencia": ["pagina-1"], "confianza": 0.4, "fecha": FECHA},
        )
        client.post("/unidades/unidad-9/confirmacion-limites", json={"actor": "archivista-1", "fecha": FECHA})
        _normalizar(client, "unidad-9")

        response = _entregar(client, "unidad-9")

        assert response.status_code == 200
        assert response.json()["estado"] == "ENTREGADA_A_EXTRACCION"

    def test_una_unidad_con_la_misma_huella_ya_entregada_queda_vinculada_a_duplicado(self, client):
        for id_, item_id in [("unidad-10a", "item-010a"), ("unidad-10b", "item-010b")]:
            _crear_unidad(client, id_, "lote-005", item_id, es_caso_trivial=True, huella="huella-repetida")
            _normalizar(client, id_)

        _entregar(client, "unidad-10a")
        segunda = _entregar(client, "unidad-10b")

        assert segunda.json()["estado"] == "VINCULADA_A_DUPLICADO"


# RF-NO-008 · Cero pérdida silenciosa
class TestConteoPorEstado:
    def test_conteo_cuadra_cuando_todas_las_unidades_del_lote_llegaron_a_un_terminal(self, client):
        _crear_unidad(client, "unidad-11", "lote-006", "item-011", es_caso_trivial=False)
        _validar(client, "unidad-11", "FORMATO_NO_SOPORTADO")

        response = client.get("/lotes/lote-006/conteo")

        assert response.status_code == 200
        assert response.json()["total"] == 1
        assert response.json()["terminales"] == 1
        assert response.json()["sin_perdida_silenciosa"] is True

    def test_conteo_no_cuadra_cuando_hay_una_unidad_no_terminal(self, client):
        _crear_unidad(client, "unidad-12", "lote-007", "item-012", es_caso_trivial=False)

        response = client.get("/lotes/lote-007/conteo")

        assert response.json()["sin_perdida_silenciosa"] is False


# RF-VH-001 (T-39) · Agregación de sugerencias de límites pendientes
class TestPendientesDeLimites:
    def test_get_unidades_pendientes_de_limites_incluye_solo_las_que_tienen_sugerencia_sin_confirmar(self, client):
        _crear_unidad(client, "unidad-14", "lote-009", "item-014", es_caso_trivial=False)
        client.post(
            "/unidades/unidad-14/sugerencia-limites",
            json={"modelo_id": "emisor-ficticio-v0", "evidencia": ["pagina-1"], "confianza": 0.4, "fecha": FECHA},
        )
        _crear_unidad(client, "unidad-15", "lote-009", "item-015", es_caso_trivial=False)  # sin sugerencia todavía

        response = client.get("/unidades/pendientes-de-limites")

        assert response.status_code == 200
        ids = [u["id"] for u in response.json()]
        assert "unidad-14" in ids
        assert "unidad-15" not in ids

    def test_get_unidades_pendientes_de_limites_no_rompe_el_ruteo_de_get_unidades_id(self, client):
        _crear_unidad(client, "unidad-16", "lote-009", "item-016", es_caso_trivial=False)

        response = client.get("/unidades/unidad-16")

        assert response.status_code == 200
        assert response.json()["id"] == "unidad-16"


# P-08 (hallazgo V-01 de la revisión acumulada de Codex, ver REVIEW.md) · toda
# transición queda en una bitácora consultable, con actor, fecha y estado
# anterior/posterior.
class TestAuditoria:
    def test_cada_transicion_de_una_unidad_queda_registrada_en_la_bitacora(self, client):
        _crear_unidad(client, "unidad-13", "lote-008", "item-013", es_caso_trivial=False)
        _validar(client, "unidad-13", "CORRUPTO", actor="sistema-validacion")

        eventos = client.get("/eventos-auditoria").json()

        tipos = [evento["tipo"] for evento in eventos]
        assert "UNIDAD_RECIBIDA" in tipos
        assert "VALIDACION_APLICADA" in tipos
        evento_validacion = next(e for e in eventos if e["tipo"] == "VALIDACION_APLICADA")
        assert evento_validacion["actor"] == "sistema-validacion"
        assert evento_validacion["estado_anterior"] == "PENDIENTE_DE_LIMITES"
        assert evento_validacion["estado_posterior"] == "EN_CUARENTENA"
