from datetime import datetime
from typing import Generator

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session, sessionmaker

import dominio
from persistencia import AlmacenDeUnidades, crear_fabrica_de_sesiones

# specs/spec-infra-servicios.md §7 · Contrato mínimo — normalizacion. Cada
# endpoint traduce uno a uno un método de dominio ya probado por TDD (T-33);
# este módulo no añade regla de negocio alguna, solo entrada/salida HTTP —
# mismo criterio que los *Controller.kt de los contextos Kotlin.
app = FastAPI(title="normalizacion")

_fabrica_de_sesiones: sessionmaker[Session] | None = None


def _obtener_fabrica() -> sessionmaker[Session]:
    global _fabrica_de_sesiones
    if _fabrica_de_sesiones is None:
        _fabrica_de_sesiones = crear_fabrica_de_sesiones()
    return _fabrica_de_sesiones


def obtener_sesion() -> Generator[Session, None, None]:
    sesion = _obtener_fabrica()()
    try:
        yield sesion
    finally:
        sesion.close()


def obtener_almacen(sesion: Session = Depends(obtener_sesion)) -> AlmacenDeUnidades:
    return AlmacenDeUnidades(sesion)


def _o_404(unidad: dominio.UnidadDocumentalCandidata | None, id: str) -> dominio.UnidadDocumentalCandidata:
    if unidad is None:
        raise HTTPException(status_code=404, detail=f"No encontrado: {id}")
    return unidad


@app.exception_handler(dominio.ErrorDeDominio)
def _manejar_error_de_dominio(request, exc: dominio.ErrorDeDominio):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=409, content={"error": str(exc)})


class ProcedenciaDto(BaseModel):
    fuente: str
    fecha: datetime
    disparador: str
    lote_o_flujo_id: str


class RecibirItemRequest(BaseModel):
    id: str
    lote_id: str
    item_ingesta_id: str
    procedencia: ProcedenciaDto
    huella_de_contenido: str | None = None
    es_caso_trivial: bool = False


class SugerenciaDeLimitesRequest(BaseModel):
    modelo_id: str
    evidencia: list[str] = []
    confianza: float
    fecha: datetime


class ConfirmacionDeLimitesRequest(BaseModel):
    actor: str
    fecha: datetime


class NormalizarRequest(BaseModel):
    formato_normalizado: str


class ValidacionRequest(BaseModel):
    condicion: dominio.CondicionDeNormalizacion


# RF-NO-001/003
@app.post("/unidades", status_code=201)
def recibir_item(
    request: RecibirItemRequest, almacen: AlmacenDeUnidades = Depends(obtener_almacen)
) -> dominio.UnidadDocumentalCandidata:
    unidad = dominio.recibir_item(
        id=request.id,
        lote_id=request.lote_id,
        item_ingesta_id=request.item_ingesta_id,
        procedencia=dominio.ProcedenciaHeredada(
            fuente=request.procedencia.fuente,
            fecha=request.procedencia.fecha,
            disparador=request.procedencia.disparador,
            lote_o_flujo_id=request.procedencia.lote_o_flujo_id,
            item_ingesta_id=request.item_ingesta_id,
        ),
        huella_de_contenido=request.huella_de_contenido,
        es_caso_trivial=request.es_caso_trivial,
    )
    almacen.guardar(unidad)
    return unidad


@app.get("/unidades/{id}")
def consultar(id: str, almacen: AlmacenDeUnidades = Depends(obtener_almacen)) -> dominio.UnidadDocumentalCandidata:
    return _o_404(almacen.buscar(id), id)


# RF-NO-002: componente FICTICIO — solo recibe una sugerencia ya calculada.
@app.post("/unidades/{id}/sugerencia-limites")
def recibir_sugerencia_de_limites(
    id: str, request: SugerenciaDeLimitesRequest, almacen: AlmacenDeUnidades = Depends(obtener_almacen)
) -> dominio.UnidadDocumentalCandidata:
    unidad = _o_404(almacen.buscar(id), id)
    actualizada = dominio.recibir_sugerencia_de_limites(
        unidad,
        dominio.SugerenciaDeLimites(
            modelo_id=request.modelo_id, evidencia=request.evidencia, confianza=request.confianza, fecha=request.fecha
        ),
    )
    almacen.guardar(actualizada)
    return actualizada


# RF-NO-004: cierra el ciclo que RF-VH-005 dejó abierto — Validación Humana
# llama a este endpoint (specs/spec-infra-servicios.md §6/§9).
@app.post("/unidades/{id}/confirmacion-limites")
def confirmar_limites(
    id: str, request: ConfirmacionDeLimitesRequest, almacen: AlmacenDeUnidades = Depends(obtener_almacen)
) -> dominio.UnidadDocumentalCandidata:
    unidad = _o_404(almacen.buscar(id), id)
    actualizada = dominio.confirmar_limites(unidad, actor=request.actor, fecha=request.fecha)
    almacen.guardar(actualizada)
    return actualizada


# RF-NO-005
@app.post("/unidades/{id}/normalizacion")
def normalizar(
    id: str, request: NormalizarRequest, almacen: AlmacenDeUnidades = Depends(obtener_almacen)
) -> dominio.UnidadDocumentalCandidata:
    unidad = _o_404(almacen.buscar(id), id)
    actualizada = dominio.normalizar(unidad, formato_normalizado=request.formato_normalizado)
    almacen.guardar(actualizada)
    return actualizada


# RF-NO-009
@app.post("/unidades/{id}/validacion")
def validar(
    id: str, request: ValidacionRequest, almacen: AlmacenDeUnidades = Depends(obtener_almacen)
) -> dominio.UnidadDocumentalCandidata:
    unidad = _o_404(almacen.buscar(id), id)
    actualizada = dominio.marcar_cuarentena_o_rechazo(unidad, request.condicion)
    almacen.guardar(actualizada)
    return actualizada


# RF-NO-006/010: la huella de las unidades ya entregadas se calcula aquí, no
# la envía el llamador — es lectura del propio almacén, igual que
# `documentosSinClasificar` en records-custodia (T-28) calcula su filtro a
# partir de lo ya persistido, no de lo que declare cada petición.
@app.post("/unidades/{id}/entrega")
def entregar(id: str, almacen: AlmacenDeUnidades = Depends(obtener_almacen)) -> dominio.UnidadDocumentalCandidata:
    unidad = _o_404(almacen.buscar(id), id)
    huellas_ya_entregadas = {
        u.huella_de_contenido
        for u in almacen.todas()
        if u.estado == dominio.EstadoUnidadDocumental.ENTREGADA_A_EXTRACCION and u.huella_de_contenido is not None
    }
    actualizada = dominio.entregar(unidad, huellas_ya_entregadas)
    almacen.guardar(actualizada)
    return actualizada


# RF-NO-008. FastAPI/Pydantic no serializa las `@property` de un dataclass
# stdlib (a diferencia de Kotlin/Jackson, que sí serializa `val ... get()`) —
# por eso `terminales`/`sin_perdida_silenciosa` se exponen explícitas aquí en
# vez de devolver `ConteoPorEstado` tal cual.
@app.get("/lotes/{lote_id}/conteo")
def conteo_por_estado(lote_id: str, almacen: AlmacenDeUnidades = Depends(obtener_almacen)) -> dict:
    conteo = dominio.contar_por_estado(almacen.de_lote(lote_id))
    return {
        "por_estado": conteo.por_estado,
        "total": conteo.total,
        "terminales": conteo.terminales,
        "sin_perdida_silenciosa": conteo.sin_perdida_silenciosa,
    }
