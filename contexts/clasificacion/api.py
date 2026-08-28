from datetime import datetime

import dominio
from fastapi import Depends, FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from integracion import EnviadorDeSugerenciasHttp, ServicioNoDisponibleError

# specs/spec-infra-servicios.md §12 (T-45) · Contrato mínimo — clasificacion.
# Sin persistencia propia (specs/003-clasificacion/spec.md §3: "no mantiene
# estado propio de sus sugerencias después de entregarlas") — cada endpoint
# compone las funciones puras de dominio.py (T-44) y reenvía el resultado a
# records-custodia vía POST /sugerencias (RF-CL-004/006), mismo criterio de
# orquestador HTTP sin tablas propias que validacion-humana (T-30).
app = FastAPI(title="clasificacion")


# P-03 (corrección tras VETO real de Codex sobre commit 17642a7, ver
# REVIEW.md): la anotación de retorno y de los parámetros `Depends(...)` más
# abajo usan el puerto `dominio.EnviadorDeSugerencias`, nunca la clase
# concreta — mismo criterio que `dominio.VerificadorDeAutorizacion` en
# extraccion (T-41b). Esta función sigue siendo el único lugar que construye
# la implementación real.
def obtener_enviador() -> dominio.EnviadorDeSugerencias:
    return EnviadorDeSugerenciasHttp()


@app.exception_handler(dominio.ErrorDeDominio)
def _manejar_error_de_dominio(request, exc: dominio.ErrorDeDominio):
    return JSONResponse(status_code=409, content={"error": str(exc)})


# 502 porque el fallo es de records-custodia (servicio aguas abajo), no de
# esta petición — mismo criterio que ServicioNoDisponibleException en
# validacion-humana (http/ManejoDeErrores.kt, T-30) y formato de error
# unificado `{"error": mensaje}` del resto del proyecto (T-19).
@app.exception_handler(ServicioNoDisponibleError)
def _manejar_servicio_no_disponible(request, exc: ServicioNoDisponibleError):
    return JSONResponse(status_code=502, content={"error": str(exc)})


class TextoExtraidoDto(BaseModel):
    texto_extraido_id: str
    documento_id: str
    contenido: str
    estado: str


class CandidataDeClasificacionDto(BaseModel):
    trd_version: int
    serie: str
    subserie: str
    confianza: float
    evidencia: list[str]
    modelo_id: str
    fecha: datetime


class ClasificarRequest(BaseModel):
    texto: TextoExtraidoDto
    candidatas: list[CandidataDeClasificacionDto]


class AgruparRequest(BaseModel):
    texto: TextoExtraidoDto
    expediente_propuesto: str | None = None
    confianza: float
    evidencia: list[str]
    modelo_id: str
    fecha: datetime


class NoClasificableRequest(BaseModel):
    texto: TextoExtraidoDto
    razon: str
    actor: str
    fecha: datetime


def _texto_disponible(texto: TextoExtraidoDto) -> dominio.TextoDisponible:
    return dominio.recibir_texto_extraido(
        texto_extraido_id=texto.texto_extraido_id,
        documento_id=texto.documento_id,
        contenido=texto.contenido,
        estado=texto.estado,
    )


# RF-CL-001/002/003/004: recibe el texto y una o más candidatas de
# clasificación (ya calculadas por el llamador FICTICIO, RF-CL-002), las
# ordena por confianza descendente (RF-CL-003) y reenvía cada una a
# records-custodia en ese orden (RF-CL-004).
@app.post("/clasificaciones", status_code=201)
def clasificar(
    request: ClasificarRequest, enviador: dominio.EnviadorDeSugerencias = Depends(obtener_enviador)
) -> list[dominio.SugerenciaSaliente]:
    texto = _texto_disponible(request.texto)
    sugerencias = [
        dominio.clasificar(
            texto,
            trd_version=candidata.trd_version,
            serie=candidata.serie,
            subserie=candidata.subserie,
            confianza=candidata.confianza,
            evidencia=candidata.evidencia,
            modelo_id=candidata.modelo_id,
            fecha=candidata.fecha,
        )
        for candidata in request.candidatas
    ]
    sugerencias = dominio.exigir_al_menos_una_candidata(sugerencias)
    ordenadas = dominio.ordenar_por_confianza(sugerencias)
    salientes = [dominio.a_sugerencia_saliente_de_clasificacion(sugerencia) for sugerencia in ordenadas]
    for saliente in salientes:
        enviador.enviar(saliente)
    return salientes


# RF-CL-005/006: agrupamiento probabilístico en expedientes — una sola
# candidata por petición (a diferencia de RF-CL-003, ningún RF exige ranking
# de expedientes candidatos).
@app.post("/agrupamientos", status_code=201)
def agrupar(
    request: AgruparRequest, enviador: dominio.EnviadorDeSugerencias = Depends(obtener_enviador)
) -> dominio.SugerenciaSaliente:
    texto = _texto_disponible(request.texto)
    sugerencia = dominio.agrupar(
        texto,
        expediente_propuesto=request.expediente_propuesto,
        confianza=request.confianza,
        evidencia=request.evidencia,
        modelo_id=request.modelo_id,
        fecha=request.fecha,
    )
    saliente = dominio.a_sugerencia_saliente_de_agrupamiento(sugerencia)
    enviador.enviar(saliente)
    return saliente


# RF-CL-010: no reenvía nada a records-custodia — la spec (§4) nombra su
# destino como "Operador" (reporte), no Records/Custodia; sin persistencia
# propia (§3) el reporte es la propia respuesta HTTP síncrona, no se inventa
# un almacén ni un canal de notificación que la spec no pide.
@app.post("/no-clasificables")
def marcar_no_clasificable(request: NoClasificableRequest) -> dominio.MarcaNoClasificable:
    texto = _texto_disponible(request.texto)
    return dominio.marcar_no_clasificable(texto, razon=request.razon, actor=request.actor, fecha=request.fecha)
