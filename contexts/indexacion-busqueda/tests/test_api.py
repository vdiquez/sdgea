import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from api import app, obtener_sesion, obtener_verificador
from persistencia import Base

FECHA = "2026-08-30T00:00:00Z"


# Doble de VerificadorDePermisos inyectado vía dependency_overrides -- mismo
# criterio que _VerificadorDePrueba en extraccion (T-41b): sin él, estos
# tests dependerían de una llamada HTTP real contra seguridad-acceso.
# Deniega solo "documento-sin-permiso", usado exclusivamente por los tests
# de RF-IB-008; el resto de este archivo usa documentos que este doble sí
# permite.
class _VerificadorDePrueba:
    def tiene_permiso(self, actor: str, documento_id: str) -> bool:
        return documento_id != "documento-sin-permiso"


@pytest.fixture()
def client():
    # StaticPool: misma razón que en normalizacion/extraccion (T-34/T-41) --
    # una única conexión sqlite:///:memory: compartida durante todo el test.
    # NO se sobreescriben obtener_indice_lexico/obtener_indice_vectorial: al
    # usar la sesión de prueba (vía obtener_sesion overrideado), estos tests
    # ejercitan los adaptadores AUTOALOJADOS reales contra SQLite -- mismo
    # criterio de honestidad que TestIndiceLexicoAutoalojado en
    # test_persistencia.py, no solo la composición HTTP↔dominio.
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    fabrica_de_sesiones = sessionmaker(bind=engine)

    def _sesion_de_prueba():
        sesion = fabrica_de_sesiones()
        try:
            yield sesion
        finally:
            sesion.close()

    app.dependency_overrides[obtener_sesion] = _sesion_de_prueba
    app.dependency_overrides[obtener_verificador] = lambda: _VerificadorDePrueba()
    yield TestClient(app)
    app.dependency_overrides.clear()


def _crear_e_indexar(client, id_="entrada-1", documento_id="documento-1", texto="el gato subió al tejado", metadatos=None):
    client.post(
        "/entradas",
        json={"id": id_, "documento_id": documento_id, "texto_extraido": texto, "metadatos": metadatos or {}, "actor": "sistema", "fecha": FECHA},
    )
    return client.post(
        "/entradas/" + id_ + "/indexacion",
        json={"texto_extraido": texto, "metadatos": metadatos or {}, "embedding": [0.1, 0.2], "actor": "sistema", "fecha": FECHA},
    )


# RF-IB-001
class TestRecepcionEIndexacion:
    def test_post_entradas_crea_una_entrada_pendiente(self, client):
        response = client.post(
            "/entradas",
            json={"id": "entrada-1", "documento_id": "documento-1", "texto_extraido": "x", "actor": "sistema", "fecha": FECHA},
        )

        assert response.status_code == 201
        assert response.json()["estado"] == "PENDIENTE_DE_INDEXACION"

    def test_post_indexacion_deja_la_entrada_indexada(self, client):
        response = _crear_e_indexar(client)

        assert response.status_code == 200
        assert response.json()["estado"] == "INDEXADA"
        assert response.json()["embedding"] == [0.1, 0.2]


# RF-IB-004
class TestActualizacion:
    def test_post_actualizacion_rectifica_metadatos(self, client):
        _crear_e_indexar(client, metadatos={"serie": "100"})

        response = client.post(
            "/entradas/entrada-1/actualizacion", json={"metadatos": {"serie": "200"}, "actor": "ana", "fecha": FECHA}
        )

        assert response.status_code == 200
        assert response.json()["metadatos"] == {"serie": "200"}

    # VETO real de Codex sobre 53ce657 (ver STATE.md): un embedding nuevo
    # declarado en la actualización debe quedar realmente persistido en
    # `indices_vectoriales`, no solo reflejado en la respuesta HTTP de este
    # endpoint. Esta prueba lo comprueba con una petición POSTERIOR e
    # independiente (una búsqueda), que reconstruye el agregado leyendo el
    # puerto de nuevo -- no reutiliza el valor devuelto por la actualización.
    def test_post_actualizacion_con_embedding_nuevo_lo_deja_persistido_de_verdad(self, client):
        _crear_e_indexar(client, texto="el gato subió al tejado")

        respuesta_actualizacion = client.post(
            "/entradas/entrada-1/actualizacion", json={"embedding": [0.9, 0.8], "actor": "ana", "fecha": FECHA}
        )
        assert respuesta_actualizacion.json()["embedding"] == [0.9, 0.8]

        respuesta_busqueda = client.post("/busquedas", json={"termino": "tejado", "actor": "ana", "fecha": FECHA})

        assert [r["embedding"] for r in respuesta_busqueda.json()] == [[0.9, 0.8]]


# RF-IB-005/008/009
class TestBusqueda:
    def test_post_busquedas_encuentra_por_termino_real_y_persiste_el_evento_de_acceso(self, client):
        _crear_e_indexar(client, texto="el gato subió al tejado")

        response = client.post("/busquedas", json={"termino": "tejado", "actor": "ana", "fecha": FECHA})

        assert response.status_code == 200
        assert [r["documento_id"] for r in response.json()] == ["documento-1"]
        # P-03 (VETO real de Codex sobre 53ce657, ver STATE.md): el resultado
        # debe traer su embedding real, recuperado a través del puerto -- no
        # `None` (síntoma exacto del acoplamiento ya corregido).
        assert response.json()[0]["embedding"] == [0.1, 0.2]

        eventos = client.get("/eventos-auditoria").json()
        accesos = [e for e in eventos["accesos"] if e["tipo"] == "BUSQUEDA_LEXICA"]
        assert len(accesos) == 1
        assert accesos[0]["documentos_accedidos"] == ["documento-1"]

    def test_post_busquedas_no_devuelve_un_documento_sin_permiso(self, client):
        _crear_e_indexar(client, id_="entrada-1", documento_id="documento-sin-permiso", texto="acta de reunión")

        response = client.post("/busquedas", json={"termino": "acta", "actor": "ana", "fecha": FECHA})

        assert response.json() == []


# RF-IB-006 (FICTICIO)
class TestRecuperacionPorRelevancia:
    def test_post_recuperaciones_preserva_el_orden_declarado_por_el_llamador(self, client):
        _crear_e_indexar(client, id_="entrada-1", documento_id="documento-1", texto="a")
        _crear_e_indexar(client, id_="entrada-2", documento_id="documento-2", texto="b")

        response = client.post(
            "/recuperaciones", json={"entrada_ids_ordenados": ["entrada-2", "entrada-1"], "actor": "ana", "fecha": FECHA}
        )

        assert response.status_code == 200
        assert [r["id"] for r in response.json()] == ["entrada-2", "entrada-1"]
        assert [r["embedding"] for r in response.json()] == [[0.1, 0.2], [0.1, 0.2]]


# RF-IB-007/010 (FICTICIO)
class TestPreguntas:
    def test_post_preguntas_con_cita_permitida_responde_con_la_cita(self, client):
        _crear_e_indexar(client, documento_id="documento-1")

        response = client.post(
            "/preguntas",
            json={
                "pregunta": "¿dónde subió el gato?",
                "respuesta": "Al tejado.",
                "citas": [{"documento_id": "documento-1", "fragmento": "el gato subió al tejado"}],
                "modelo_id": "qa-ficticio-v1",
                "actor": "ana",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 200
        assert response.json()["citas"] == [{"documento_id": "documento-1", "fragmento": "el gato subió al tejado"}]

    def test_post_preguntas_sin_evidencia_permitida_responde_negativa_apropiada(self, client):
        response = client.post(
            "/preguntas",
            json={
                "pregunta": "¿algo sin evidencia?",
                "modelo_id": "qa-ficticio-v1",
                "actor": "ana",
                "fecha": FECHA,
                "razon_negativa": "No hay evidencia suficiente en el acervo.",
            },
        )

        assert response.status_code == 200
        assert response.json()["razon"] == "No hay evidencia suficiente en el acervo."
