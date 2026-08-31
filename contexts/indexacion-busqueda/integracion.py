import os
from datetime import datetime, timezone

import httpx

from dominio import GeneradorDeEmbeddings, IndiceLexico, IndiceVectorial, ModeloDeLenguaje, VerificadorDePermisos


class ComponenteProbabilisticoNoImplementadoError(Exception):
    """La constitución prohíbe implementar un componente probabilístico real
    (embeddings, inferencia LLM). Estos dos adaptadores (gestionado y
    autoalojado) existen solo para que el seam de P-03 esté completo
    estructuralmente -- ninguno debería invocarse nunca en este vertical
    slice, porque el llamador entrega embedding/respuesta/citas YA
    CALCULADOS directamente a `dominio.indexar()`/`dominio.responder_qa()`.
    Si algo llega a invocar uno de estos dos adaptadores, es un error de
    cableado (alguien intentó calcular en vez de recibir), no un caso de uso
    real -- por eso levantan, en vez de simular un resultado."""


# P-03 (VETO real de Codex sobre e356158/3793486, ver STATE.md): las cuatro
# capacidades de la spec §1 necesitan DOS variantes de despliegue reales
# (gestionada/SaaS y autoalojada/on-premise sin salida de red — RNF-IB-002),
# no un doble en memoria + una implementación real. Los dos puertos REALES
# (IndiceLexico, IndiceVectorial) ya tienen su variante autoalojada en
# persistencia.py (consulta directa a Postgres); aquí van las variantes
# GESTIONADAS -- clientes HTTP genéricos contra una URL configurable por
# variable de entorno, sin nombrar ningún producto concreto (el
# `[CLARIFICAR]` de motor/modelo sigue abierto en la spec §8). Ninguna está
# desplegada en docker-compose todavía porque no hay ningún servicio
# gestionado real en este proyecto -- existen como clases reales e
# intercambiables, no como una entrada de compose.
def url_indice_lexico_gestionado() -> str | None:
    return os.environ.get("INDICE_LEXICO_ENDPOINT_URL")


def url_indice_vectorial_gestionado() -> str | None:
    return os.environ.get("INDICE_VECTORIAL_ENDPOINT_URL")


class IndiceLexicoGestionado(IndiceLexico):
    def __init__(self, base_url: str | None = None, client: httpx.Client | None = None):
        self._base_url = base_url or url_indice_lexico_gestionado()
        self._client = client or httpx.Client()

    def indexar(self, entrada_id: str, contenido: str) -> None:
        respuesta = self._client.post(f"{self._base_url}/entradas", json={"id": entrada_id, "contenido": contenido}, timeout=5.0)
        respuesta.raise_for_status()

    def buscar(self, termino: str):
        respuesta = self._client.get(f"{self._base_url}/busquedas", params={"termino": termino}, timeout=5.0)
        respuesta.raise_for_status()
        return respuesta.json()


class IndiceVectorialGestionado(IndiceVectorial):
    def __init__(self, base_url: str | None = None, client: httpx.Client | None = None):
        self._base_url = base_url or url_indice_vectorial_gestionado()
        self._client = client or httpx.Client()

    def indexar(self, entrada_id: str, embedding: list[float]) -> None:
        respuesta = self._client.post(
            f"{self._base_url}/entradas", json={"id": entrada_id, "embedding": embedding}, timeout=5.0
        )
        respuesta.raise_for_status()

    def obtener(self, entrada_id: str) -> list[float] | None:
        respuesta = self._client.get(f"{self._base_url}/entradas/{entrada_id}", timeout=5.0)
        if respuesta.status_code == 404:
            return None
        respuesta.raise_for_status()
        return respuesta.json()["embedding"]


# Dos capacidades FICTICIAS (P-03 completo, nunca invocadas en este vertical
# slice -- ver ComponenteProbabilisticoNoImplementadoError arriba).
class GeneradorDeEmbeddingsGestionado(GeneradorDeEmbeddings):
    def generar(self, texto: str) -> list[float]:
        raise ComponenteProbabilisticoNoImplementadoError(
            "GeneradorDeEmbeddingsGestionado no calcula embeddings reales (disciplina constitucional); "
            "el llamador debe entregar el embedding ya calculado."
        )


class GeneradorDeEmbeddingsAutoalojado(GeneradorDeEmbeddings):
    def generar(self, texto: str) -> list[float]:
        raise ComponenteProbabilisticoNoImplementadoError(
            "GeneradorDeEmbeddingsAutoalojado no calcula embeddings reales (disciplina constitucional); "
            "el llamador debe entregar el embedding ya calculado."
        )


class ModeloDeLenguajeGestionado(ModeloDeLenguaje):
    def responder(self, pregunta: str, contexto: list[str]) -> str:
        raise ComponenteProbabilisticoNoImplementadoError(
            "ModeloDeLenguajeGestionado no genera respuestas reales (disciplina constitucional); "
            "el llamador debe entregar la respuesta y las citas ya calculadas."
        )


class ModeloDeLenguajeAutoalojado(ModeloDeLenguaje):
    def responder(self, pregunta: str, contexto: list[str]) -> str:
        raise ComponenteProbabilisticoNoImplementadoError(
            "ModeloDeLenguajeAutoalojado no genera respuestas reales (disciplina constitucional); "
            "el llamador debe entregar la respuesta y las citas ya calculadas."
        )


# P-03: implementación real del puerto VerificadorDePermisos -- mismo patrón
# que VerificadorDeAutorizacionHttp en extraccion (T-41b): consulta
# `POST /autorizacion` de seguridad-acceso una vez por documento candidato
# (specs/spec-infra-servicios.md §5 es un endpoint POR RECURSO, no una lista
# masiva -- no existe "dame todos los documentos permitidos" y esta tarea no
# inventa uno).
def url_base_seguridad_acceso() -> str:
    return os.environ.get("SEGURIDAD_ACCESO_BASE_URL", "http://localhost:8083")


class VerificadorDePermisosHttp(VerificadorDePermisos):
    def __init__(self, base_url: str | None = None, client: httpx.Client | None = None):
        self._base_url = base_url or url_base_seguridad_acceso()
        self._client = client or httpx.Client()

    def tiene_permiso(self, actor: str, documento_id: str) -> bool:
        respuesta = self._client.post(
            f"{self._base_url}/autorizacion",
            json={
                "identidadId": actor,
                "accion": "consultar",
                "tipoRecurso": "documento",
                "recurso": documento_id,
                "fecha": datetime.now(timezone.utc).isoformat(),
            },
            timeout=5.0,
        )
        respuesta.raise_for_status()
        return respuesta.json()["resultado"] == "PERMITIDO"
