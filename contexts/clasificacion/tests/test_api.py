import pytest
from fastapi.testclient import TestClient

from api import app, obtener_enviador
from integracion import ServicioNoDisponibleError

FECHA = "2026-08-28T00:00:00Z"


# Doble de EnviadorDeSugerenciasHttp inyectado vía dependency_overrides —
# mismo criterio que _VerificadorDePrueba en extraccion/tests/test_api.py
# (T-41b): estos tests verifican que la capa HTTP compone dominio.py y
# reenvía en el orden correcto; la forma exacta de la petición saliente ya la
# cubre tests/test_integracion.py contra el cliente real.
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


def _candidata(serie="Serie A", subserie="Subserie A1", confianza=0.87, trd_version=3, modelo_id="clasificador-ficticio-v1"):
    return {
        "trd_version": trd_version,
        "serie": serie,
        "subserie": subserie,
        "confianza": confianza,
        "evidencia": ["fragmento relevante"],
        "modelo_id": modelo_id,
        "fecha": FECHA,
    }


# RF-CL-001/002/004 · Recepción, clasificación contra la TRD vigente y envío
class TestClasificar:
    def test_post_clasificaciones_reenvia_una_sugerencia_de_clasificacion_por_candidata(self, client, enviador):
        response = client.post("/clasificaciones", json={"texto": _texto(), "candidatas": [_candidata()]})

        assert response.status_code == 201
        assert len(enviador.enviadas) == 1
        assert enviador.enviadas[0].tipo == "clasificacion"
        assert enviador.enviadas[0].documento_id == "documento-1"
        assert response.json()[0]["contenido_propuesto"] == "Serie A/Subserie A1"

    def test_post_clasificaciones_sobre_texto_no_extraido_responde_409_y_no_reenvia_nada(self, client, enviador):
        response = client.post(
            "/clasificaciones", json={"texto": _texto(estado="PENDIENTE_DE_EXTRACCION"), "candidatas": [_candidata()]}
        )

        assert response.status_code == 409
        assert response.json()["error"]
        assert enviador.enviadas == []

    def test_post_clasificaciones_responde_502_si_records_custodia_no_esta_disponible(self):
        app.dependency_overrides[obtener_enviador] = lambda: _EnviadorQueFalla()
        cliente = TestClient(app)

        response = cliente.post("/clasificaciones", json={"texto": _texto(), "candidatas": [_candidata()]})

        app.dependency_overrides.clear()
        assert response.status_code == 502
        assert response.json()["error"]


# RF-CL-003 · Ranking de sugerencias por confianza
class TestRankingPorConfianza:
    def test_post_clasificaciones_reenvia_y_devuelve_ordenadas_de_mayor_a_menor_confianza(self, client, enviador):
        candidatas = [
            _candidata(serie="Serie B", subserie="Subserie B1", confianza=0.30),
            _candidata(serie="Serie A", subserie="Subserie A1", confianza=0.91),
            _candidata(serie="Serie C", subserie="Subserie C1", confianza=0.55),
        ]

        response = client.post("/clasificaciones", json={"texto": _texto(), "candidatas": candidatas})

        confianzas_respuesta = [sugerencia["confianza"] for sugerencia in response.json()]
        confianzas_enviadas = [sugerencia.confianza for sugerencia in enviador.enviadas]
        assert confianzas_respuesta == [0.91, 0.55, 0.30]
        assert confianzas_enviadas == [0.91, 0.55, 0.30]


# RF-CL-005/006 · Agrupamiento probabilístico en expedientes
class TestAgrupar:
    def test_post_agrupamientos_a_expediente_existente_reenvia_con_tipo_agrupamiento(self, client, enviador):
        response = client.post(
            "/agrupamientos",
            json={
                "texto": _texto(),
                "expediente_propuesto": "expediente-7",
                "confianza": 0.66,
                "evidencia": ["fragmento"],
                "modelo_id": "agrupador-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 201
        assert len(enviador.enviadas) == 1
        assert enviador.enviadas[0].tipo == "agrupamiento"
        assert response.json()["contenido_propuesto"] == "expediente-7"

    def test_post_agrupamientos_a_expediente_nuevo_reenvia_con_marca_explicita(self, client, enviador):
        response = client.post(
            "/agrupamientos",
            json={
                "texto": _texto(),
                "expediente_propuesto": None,
                "confianza": 0.4,
                "evidencia": ["fragmento"],
                "modelo_id": "agrupador-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.json()["contenido_propuesto"] == "EXPEDIENTE_NUEVO"
        assert enviador.enviadas[0].contenido_propuesto == "EXPEDIENTE_NUEVO"


# RF-CL-010 · Cero pérdida silenciosa
class TestNoClasificable:
    def test_post_no_clasificables_devuelve_la_marca_y_no_reenvia_nada_a_records_custodia(self, client, enviador):
        response = client.post(
            "/no-clasificables",
            json={
                "texto": _texto(),
                "razon": "Texto extraído vacío.",
                "actor": "clasificador-ficticio-v1",
                "fecha": FECHA,
            },
        )

        assert response.status_code == 200
        assert response.json()["razon"] == "Texto extraído vacío."
        assert response.json()["documento_id"] == "documento-1"
        assert enviador.enviadas == []
