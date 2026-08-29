from datetime import datetime

import dominio
from fastapi import Depends, FastAPI
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from integracion import EnviadorDeSugerenciasHttp, ServicioNoDisponibleError

# specs/spec-infra-servicios.md §13 (T-50) · Contrato mínimo — enriquecimiento.
# Sin persistencia propia (specs/004-enriquecimiento/spec.md §3, mismo criterio
# que clasificacion, T-45) — el único endpoint compone las funciones puras de
# dominio.py (T-49) y reenvía el resultado a records-custodia vía
# POST /sugerencias (RF-EN-006), mismo criterio de orquestador HTTP sin tablas
# propias que clasificacion/validacion-humana.
app = FastAPI(title="enriquecimiento")


# P-03 (lección de T-45-corrección aplicada desde el inicio, ver dominio.py):
# la anotación de retorno y de los parámetros Depends(...) más abajo usan el
# puerto dominio.EnviadorDeSugerencias, nunca la clase concreta.
def obtener_enviador() -> dominio.EnviadorDeSugerencias:
    return EnviadorDeSugerenciasHttp()


@app.exception_handler(dominio.ErrorDeDominio)
def _manejar_error_de_dominio(request, exc: dominio.ErrorDeDominio):
    return JSONResponse(status_code=409, content={"error": str(exc)})


# 502 porque el fallo es de records-custodia (servicio aguas abajo), no de
# esta petición — mismo criterio y formato de error unificado `{"error": ...}`
# que el resto del proyecto (T-19, T-45).
@app.exception_handler(ServicioNoDisponibleError)
def _manejar_servicio_no_disponible(request, exc: ServicioNoDisponibleError):
    return JSONResponse(status_code=502, content={"error": str(exc)})


class TextoExtraidoDto(BaseModel):
    texto_extraido_id: str
    documento_id: str
    contenido: str
    estado: str


class ValorPropuestoDto(BaseModel):
    campo: str
    valor_original: str
    valor_normalizado: str
    confianza: float
    evidencia: list[str]


class EnriquecerRequest(BaseModel):
    texto: TextoExtraidoDto
    valores_propuestos: list[ValorPropuestoDto] = []
    campos_no_encontrados: list[str] = []
    modelo_id: str
    actor: str
    fecha: datetime
    razon_no_enriquecible: str | None = None


def _texto_disponible(texto: TextoExtraidoDto) -> dominio.TextoDisponible:
    return dominio.recibir_texto_extraido(
        texto_extraido_id=texto.texto_extraido_id,
        documento_id=texto.documento_id,
        contenido=texto.contenido,
        estado=texto.estado,
    )


# RF-EN-001..006/008/009/010: única puerta de entrada HTTP. `evaluar_texto`
# (T-49, dominio.py) ya bifurca hacia SugerenciaDeMetadatos (RF-EN-002..006/
# 008/010, hay al menos un valor propuesto o campo marcado "no encontrado") o
# hacia MarcaNoEnriquecible (RF-EN-009, sin ninguno, con la razón declarada
# por el llamador) — esta capa HTTP no reimplementa esa bifurcación, solo la
# expone. Un único endpoint, no dos separados como
# /clasificaciones + /no-clasificables en clasificacion, porque aquí la
# propia operación de dominio ya decide cuál de las dos salidas corresponde a
# partir de la misma entrada; separar la ruta duplicaría esa decisión en la
# capa HTTP y reabriría el hueco que el segundo VETO de Codex sobre T-49 cerró
# (ver STATE.md).
@app.post("/enriquecimientos")
def enriquecer(
    request: EnriquecerRequest, enviador: dominio.EnviadorDeSugerencias = Depends(obtener_enviador)
) -> JSONResponse:
    texto = _texto_disponible(request.texto)
    valores: list[dominio.ValorPropuesto | dominio.CampoNoEncontrado] = [
        dominio.proponer_valor(
            campo=valor.campo,
            valor_original=valor.valor_original,
            valor_normalizado=valor.valor_normalizado,
            confianza=valor.confianza,
            evidencia=valor.evidencia,
        )
        for valor in request.valores_propuestos
    ] + [dominio.marcar_campo_no_encontrado(campo) for campo in request.campos_no_encontrados]

    resultado = dominio.evaluar_texto(
        texto,
        valores,
        modelo_id=request.modelo_id,
        actor=request.actor,
        fecha=request.fecha,
        razon_no_enriquecible=request.razon_no_enriquecible,
    )

    if isinstance(resultado, dominio.SugerenciaDeMetadatos):
        salientes = dominio.a_sugerencia_saliente(resultado)
        for saliente in salientes:
            enviador.enviar(saliente)
        return JSONResponse(status_code=201, content=jsonable_encoder(salientes))

    return JSONResponse(status_code=200, content=jsonable_encoder(resultado))
