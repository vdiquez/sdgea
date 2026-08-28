import json
from datetime import datetime

import httpx
import pytest

from dominio import SugerenciaSaliente
from integracion import EnviadorDeSugerenciasHttp, ServicioNoDisponibleError

FECHA = datetime.fromisoformat("2026-08-28T00:00:00+00:00")


def _sugerencia() -> SugerenciaSaliente:
    return SugerenciaSaliente(
        documento_id="documento-1",
        tipo="clasificacion",
        contenido_propuesto="Serie A/Subserie A1",
        modelo_id="clasificador-ficticio-v1",
        evidencia=["fragmento relevante"],
        confianza=0.87,
        fecha=FECHA,
    )


# T-45: no un doble que nunca abre una conexión — httpx.MockTransport
# intercepta a nivel de transporte, así que estos tests verifican la forma
# exacta (método, URL, cuerpo JSON) de la petición que EnviadorDeSugerenciasHttp
# construye de verdad, mismo criterio de honestidad que IntegracionHttpTest en
# validacion-humana (Kotlin, T-30) con MockRestServiceServer.
class TestEnviarSugerencia:
    def test_enviar_hace_post_sugerencias_con_el_cuerpo_exacto_que_espera_records_custodia(self):
        peticiones: list[httpx.Request] = []

        def responder(request: httpx.Request) -> httpx.Response:
            peticiones.append(request)
            return httpx.Response(201, json={"documentoId": "documento-1"})

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        enviador = EnviadorDeSugerenciasHttp(base_url="http://records-custodia-test", client=cliente)

        enviador.enviar(_sugerencia())

        assert len(peticiones) == 1
        peticion = peticiones[0]
        assert peticion.method == "POST"
        assert str(peticion.url) == "http://records-custodia-test/sugerencias"
        cuerpo = json.loads(peticion.content)
        assert cuerpo == {
            "documentoId": "documento-1",
            "tipo": "clasificacion",
            "contenidoPropuesto": "Serie A/Subserie A1",
            "modeloId": "clasificador-ficticio-v1",
            "evidencia": ["fragmento relevante"],
            "confianza": 0.87,
            "fecha": "2026-08-28T00:00:00+00:00",
        }

    def test_enviar_lanza_servicio_no_disponible_si_records_custodia_responde_error(self):
        def responder(request: httpx.Request) -> httpx.Response:
            return httpx.Response(500)

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        enviador = EnviadorDeSugerenciasHttp(base_url="http://records-custodia-test", client=cliente)

        with pytest.raises(ServicioNoDisponibleError):
            enviador.enviar(_sugerencia())

    def test_enviar_lanza_servicio_no_disponible_si_la_conexion_falla(self):
        def responder(request: httpx.Request) -> httpx.Response:
            raise httpx.ConnectError("conexión rechazada", request=request)

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        enviador = EnviadorDeSugerenciasHttp(base_url="http://records-custodia-test", client=cliente)

        with pytest.raises(ServicioNoDisponibleError):
            enviador.enviar(_sugerencia())
