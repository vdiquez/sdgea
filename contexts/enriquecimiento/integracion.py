import os

import httpx

from dominio import EnviadorDeSugerencias, SugerenciaSaliente


# specs/spec-infra-servicios.md §13 (T-50): único consumidor de
# POST /sugerencias en records-custodia desde este contexto — mismo patrón de
# adaptador HTTP real que EnviadorDeSugerenciasHttp en clasificacion (T-45).
# documentoId/tipo/contenidoPropuesto/modeloId/evidencia/confianza/fecha en
# camelCase porque Spring/Jackson serializa así del lado de records-custodia
# (ver http/Dtos.kt · RecibirSugerenciaRequest).
def url_base_records_custodia() -> str:
    return os.environ.get("RECORDS_CUSTODIA_BASE_URL", "http://localhost:8082")


class ServicioNoDisponibleError(Exception):
    pass


# Implementación real del puerto `EnviadorDeSugerencias` (P-03, aplicado
# desde el inicio — lección de T-45-corrección, ver dominio.py): api.py
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
