import pytest
from fastapi.testclient import TestClient

from api import app, obtener_enviador
from integracion import ServicioNoDisponibleError

FECHA = "2026-08-29T00:00:00Z"


# Doble de EnviadorDeSugerenciasHttp inyectado vía dependency_overrides —
# mismo criterio que _EnviadorDePrueba en clasificacion/tests/test_api.py
# (T-45): estos tests verifican que la capa HTTP compone dominio.py y
# reenvía correctamente; la forma exacta de la petición saliente ya la cubre
# tests/test_integracion.py contra el cliente real.
class _EnviadorDePrueba:
    def __init__(self):
        self.enviadas = []

    def enviar(self, sugerencia):
        self.enviadas.append(sugerencia)


class _EnviadorQueFalla:
    def enviar(self, sugerencia):
        raise ServicioNoDisponibleError("records-custodia no respondió al recibir la sugerencia.")


@pytest.fixture()
def enviador():
    return _EnviadorDePrueba()


@pytest.fixture()
def client(enviador):
    app.dependency_overrides[obtener_enviador] = lambda: enviador
    yield TestClient(app)
    app.dependency_overrides.clear()


def _texto(texto_extraido_id="texto-1", documento_id="documento-1", estado="EXTRAIDO"):
    return {
        "texto_extraido_id": texto_extraido_id,
        "documento_id": documento_id,
        "contenido": "contenido del documento",
        "estado": estado,
    }


def _valor_propuesto(campo="remitente", valor_original="Juan Pérez", valor_normalizado="JUAN PEREZ", confianza=0.82):
    return {
        "campo": campo,
        "valor_original": valor_original,
        "valor_normalizado": valor_normalizado,
        "confianza": confianza,
        "evidencia": ["Firma: Juan Pérez"],
    }


# RF-EN-001..004/006/008 · Recepción, valores propuestos y envío por campo
class TestEnriquecerConValoresPropuestos:
    def test_post_enriquecimientos_reenvia_una_sugerencia_de_metadato_por_valor_propuesto(self, client, enviador):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "valores_propuestos": [_valor_propuesto()],
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 201
        assert len(enviador.enviadas) == 1
        assert enviador.enviadas[0].tipo == "metadato"
        assert enviador.enviadas[0].documento_id == "documento-1"
        assert response.json()[0]["contenido_propuesto"] == "remitente=JUAN PEREZ"

    def test_post_enriquecimientos_sobre_texto_no_extraido_responde_409_y_no_reenvia_nada(self, client, enviador):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(estado="PENDIENTE_DE_EXTRACCION"),
                "valores_propuestos": [_valor_propuesto()],
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 409
        assert response.json()["error"]
        assert enviador.enviadas == []

    def test_post_enriquecimientos_responde_502_si_records_custodia_no_esta_disponible(self):
        app.dependency_overrides[obtener_enviador] = lambda: _EnviadorQueFalla()
        cliente = TestClient(app)

        response = cliente.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "valores_propuestos": [_valor_propuesto()],
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        app.dependency_overrides.clear()
        assert response.status_code == 502
        assert response.json()["error"]


# RF-EN-005/008 · Granularidad por campo, incluidos los "no encontrado"
class TestEnriquecerConCampoNoEncontrado:
    def test_post_enriquecimientos_con_campo_no_encontrado_reenvia_marca_explicita(self, client, enviador):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "campos_no_encontrados": ["asunto"],
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 201
        assert len(enviador.enviadas) == 1
        assert response.json()[0]["contenido_propuesto"] == "asunto=NO_ENCONTRADO"
        assert response.json()[0]["confianza"] == 0.0

    def test_post_enriquecimientos_distingue_cada_campo_en_una_sugerencia_saliente_propia(self, client, enviador):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "valores_propuestos": [_valor_propuesto(campo="remitente")],
                "campos_no_encontrados": ["asunto"],
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 201
        assert len(enviador.enviadas) == 2
        contenidos = {saliente["contenido_propuesto"] for saliente in response.json()}
        assert contenidos == {"remitente=JUAN PEREZ", "asunto=NO_ENCONTRADO"}


# RF-EN-009 · Cero pérdida silenciosa
class TestCeroPerdidaSilenciosa:
    def test_post_enriquecimientos_sin_valores_y_con_razon_responde_200_con_marca_y_no_reenvia_nada(
        self, client, enviador
    ):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
                "razon_no_enriquecible": "Texto extraído vacío.",
            },
        )

        assert response.status_code == 200
        assert response.json()["razon"] == "Texto extraído vacío."
        assert response.json()["documento_id"] == "documento-1"
        assert enviador.enviadas == []

    def test_post_enriquecimientos_sin_valores_ni_razon_responde_409_y_no_reenvia_nada(self, client, enviador):
        response = client.post(
            "/enriquecimientos",
            json={
                "texto": _texto(),
                "modelo_id": "enriquecedor-ficticio-v1",
                "actor": "enriquecedor-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 409
        assert response.json()["error"]
        assert enviador.enviadas == []
