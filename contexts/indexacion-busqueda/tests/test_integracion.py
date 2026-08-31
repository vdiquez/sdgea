import json

import httpx
import pytest

from integracion import (
    ComponenteProbabilisticoNoImplementadoError,
    GeneradorDeEmbeddingsAutoalojado,
    GeneradorDeEmbeddingsGestionado,
    IndiceLexicoGestionado,
    IndiceVectorialGestionado,
    ModeloDeLenguajeAutoalojado,
    ModeloDeLenguajeGestionado,
    VerificadorDePermisosHttp,
)


# P-03 (VETO real de Codex, ver STATE.md): VerificadorDePermisosHttp es el
# puerto REAL que sí se invoca en producción -- httpx.MockTransport
# intercepta a nivel de transporte, así que este test verifica la forma
# exacta de la petición que el cliente construye de verdad, mismo criterio
# de honestidad que VerificadorDeAutorizacionHttp en extraccion (T-41b).
class TestVerificadorDePermisosHttp:
    def test_tiene_permiso_hace_post_autorizacion_con_el_cuerpo_exacto_y_devuelve_true_si_permitido(self):
        peticiones: list[httpx.Request] = []

        def responder(request: httpx.Request) -> httpx.Response:
            peticiones.append(request)
            return httpx.Response(200, json={"resultado": "PERMITIDO"})

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        verificador = VerificadorDePermisosHttp(base_url="http://seguridad-acceso-test", client=cliente)

        resultado = verificador.tiene_permiso("ana", "documento-1")

        assert resultado is True
        assert len(peticiones) == 1
        peticion = peticiones[0]
        assert peticion.method == "POST"
        assert str(peticion.url) == "http://seguridad-acceso-test/autorizacion"
        cuerpo = json.loads(peticion.content)
        assert cuerpo["identidadId"] == "ana"
        assert cuerpo["accion"] == "consultar"
        assert cuerpo["tipoRecurso"] == "documento"
        assert cuerpo["recurso"] == "documento-1"
        assert "fecha" in cuerpo

    def test_tiene_permiso_devuelve_false_si_denegado(self):
        cliente = httpx.Client(transport=httpx.MockTransport(lambda r: httpx.Response(200, json={"resultado": "DENEGADO"})))
        verificador = VerificadorDePermisosHttp(base_url="http://seguridad-acceso-test", client=cliente)

        assert verificador.tiene_permiso("ana", "documento-1") is False


class TestIndiceLexicoGestionado:
    def test_indexar_hace_post_entradas_con_id_y_contenido(self):
        peticiones: list[httpx.Request] = []

        def responder(request: httpx.Request) -> httpx.Response:
            peticiones.append(request)
            return httpx.Response(201, json={})

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        indice = IndiceLexicoGestionado(base_url="http://indice-lexico-test", client=cliente)

        indice.indexar("entrada-1", "contenido de prueba")

        assert len(peticiones) == 1
        cuerpo = json.loads(peticiones[0].content)
        assert cuerpo == {"id": "entrada-1", "contenido": "contenido de prueba"}

    def test_buscar_hace_get_busquedas_con_el_termino(self):
        peticiones: list[httpx.Request] = []

        def responder(request: httpx.Request) -> httpx.Response:
            peticiones.append(request)
            return httpx.Response(200, json=[])

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        indice = IndiceLexicoGestionado(base_url="http://indice-lexico-test", client=cliente)

        indice.buscar("tejado")

        assert len(peticiones) == 1
        assert peticiones[0].url.params["termino"] == "tejado"


class TestIndiceVectorialGestionado:
    def test_indexar_hace_post_entradas_con_id_y_embedding(self):
        peticiones: list[httpx.Request] = []

        def responder(request: httpx.Request) -> httpx.Response:
            peticiones.append(request)
            return httpx.Response(201, json={})

        cliente = httpx.Client(transport=httpx.MockTransport(responder))
        indice = IndiceVectorialGestionado(base_url="http://indice-vectorial-test", client=cliente)

        indice.indexar("entrada-1", [0.1, 0.2])

        cuerpo = json.loads(peticiones[0].content)
        assert cuerpo == {"id": "entrada-1", "embedding": [0.1, 0.2]}


# P-01/P-03 (constitución, disciplina de alcance): ninguno de estos cuatro
# adaptadores calcula nada probabilístico real -- deben fallar de forma
# explícita si alguien los invoca, nunca simular un resultado. Existen solo
# para que el seam de P-03 esté completo (dos variantes por capacidad); en
# este vertical slice nada los llama nunca (el llamador entrega
# embedding/respuesta/citas ya calculados directamente a dominio.py).
class TestAdaptadoresFicticiosNuncaCalculanNadaReal:
    @pytest.mark.parametrize(
        "adaptador",
        [GeneradorDeEmbeddingsGestionado(), GeneradorDeEmbeddingsAutoalojado()],
    )
    def test_generar_siempre_falla_explicitamente(self, adaptador):
        with pytest.raises(ComponenteProbabilisticoNoImplementadoError):
            adaptador.generar("cualquier texto")

    @pytest.mark.parametrize(
        "adaptador",
        [ModeloDeLenguajeGestionado(), ModeloDeLenguajeAutoalojado()],
    )
    def test_responder_siempre_falla_explicitamente(self, adaptador):
        with pytest.raises(ComponenteProbabilisticoNoImplementadoError):
            adaptador.responder("¿alguna pregunta?", ["contexto"])
