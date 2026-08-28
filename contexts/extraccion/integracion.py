import os
from datetime import datetime, timezone

import httpx

from dominio import VerificadorDeAutorizacion

# RF-EX-011 / P-03 (VETO real de Codex sobre commit cf93d84, ver REVIEW.md):
# implementación real del puerto VerificadorDeAutorizacion — primer
# consumidor Python de `POST /autorizacion` en seguridad-acceso. Mismo
# contrato y misma convención de variable de entorno que el resto de
# contextos (DB_HOST/etc.) y que VerificadorDePermisosHttp en
# validacion-humana (Kotlin, T-30): `identidadId`/`accion`/`tipoRecurso` en
# camelCase porque Spring/Jackson serializa así; `nivelClasificacion` se deja
# en su default (PUBLICA) del lado de seguridad-acceso porque RF-EX-011 no
# distingue por nivel de clasificación del documento.
def url_base_seguridad_acceso() -> str:
    return os.environ.get("SEGURIDAD_ACCESO_BASE_URL", "http://localhost:8083")


class VerificadorDeAutorizacionHttp(VerificadorDeAutorizacion):
    def __init__(self, base_url: str | None = None):
        self._base_url = base_url or url_base_seguridad_acceso()

    def tiene_permiso(self, actor: str, accion: str, tipo_recurso: str) -> bool:
        respuesta = httpx.post(
            f"{self._base_url}/autorizacion",
            json={
                "identidadId": actor,
                "accion": accion,
                "tipoRecurso": tipo_recurso,
                "fecha": datetime.now(timezone.utc).isoformat(),
            },
            timeout=5.0,
        )
        respuesta.raise_for_status()
        return respuesta.json()["resultado"] == "PERMITIDO"
