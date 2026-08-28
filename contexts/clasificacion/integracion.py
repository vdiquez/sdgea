import os

import httpx

from dominio import EnviadorDeSugerencias, SugerenciaSaliente


# specs/spec-infra-servicios.md §12 (T-45): único consumidor de
# POST /sugerencias en records-custodia desde este contexto — mismo patrón de
# adaptador HTTP real que RegistradorDeDecisionesHttp en validacion-humana
# (Kotlin, T-30) y VerificadorDeAutorizacionHttp en extraccion (Python,
# T-41b). `documentoId`/`tipo`/`contenidoPropuesto`/`modeloId`/`evidencia`/
# `confianza`/`fecha` en camelCase porque Spring/Jackson serializa así del
# lado de records-custodia (ver http/Dtos.kt · RecibirSugerenciaRequest).
def url_base_records_custodia() -> str:
    return os.environ.get("RECORDS_CUSTODIA_BASE_URL", "http://localhost:8082")


class ServicioNoDisponibleError(Exception):
    pass


# Implementación real del puerto `EnviadorDeSugerencias` (P-03, corrección
# tras VETO real de Codex sobre commit 17642a7 — ver REVIEW.md): api.py
# depende del puerto declarado en dominio.py, nunca de esta clase concreta.
class EnviadorDeSugerenciasHttp(EnviadorDeSugerencias):
    def __init__(self, base_url: str | None = None, client: httpx.Client | None = None):
        self._base_url = base_url or url_base_records_custodia()
        self._client = client or httpx.Client()

    def enviar(self, sugerencia: SugerenciaSaliente) -> None:
        try:
            respuesta = self._client.post(
                f"{self._base_url}/sugerencias",
                json={
                    "documentoId": sugerencia.documento_id,
                    "tipo": sugerencia.tipo,
                    "contenidoPropuesto": sugerencia.contenido_propuesto,
                    "modeloId": sugerencia.modelo_id,
                    "evidencia": sugerencia.evidencia,
                    "confianza": sugerencia.confianza,
                    "fecha": sugerencia.fecha.isoformat(),
                },
                timeout=5.0,
            )
            respuesta.raise_for_status()
        except httpx.HTTPError as error:
            raise ServicioNoDisponibleError(
                "records-custodia no respondió al recibir la sugerencia."
            ) from error
