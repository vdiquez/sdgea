from datetime import datetime
from typing import Generator

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session, sessionmaker

import dominio
from persistencia import AlmacenDeTextos, crear_fabrica_de_sesiones

# specs/spec-infra-servicios.md §11 · Contrato mínimo — extraccion. Cada
# endpoint traduce uno a uno un método de dominio ya probado por TDD (T-40),
# incluida la bitácora de auditoría (P-08, presente desde el primer commit de
# este contexto); este módulo no añade regla de negocio alguna, solo
# entrada/salida HTTP — mismo criterio que api.py en normalizacion (T-34).
app = FastAPI(title="extraccion")

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


def obtener_almacen(sesion: Session = Depends(obtener_sesion)) -> AlmacenDeTextos:
    return AlmacenDeTextos(sesion)


def _o_404(texto: dominio.TextoExtraido | None, id: str) -> dominio.TextoExtraido:
    if texto is None:
        raise HTTPException(status_code=404, detail=f"No encontrado: {id}")
    return texto


@app.exception_handler(dominio.ErrorDeDominio)
def _manejar_error_de_dominio(request, exc: dominio.ErrorDeDominio):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=409, content={"error": str(exc)})


class ProcedenciaDto(BaseModel):
    fuente: str
    fecha: datetime
    disparador: str
    lote_o_flujo_id: str
    item_ingesta_id: str
    unidad_documental_id: str


class RecibirUnidadRequest(BaseModel):
    id: str
    unidad_documental_candidata_id: str
    procedencia: ProcedenciaDto
    actor: str
    fecha: datetime


class SoporteRequest(BaseModel):
    soporte: dominio.Soporte
    actor: str
    fecha: datetime


class ExtraccionBornDigitalRequest(BaseModel):
    contenido: str
    actor: str
    fecha: datetime


class SugerenciaOcrRequest(BaseModel):
    modelo_id: str
    contenido: str
    calidad: float
    evidencia: list[str] = []
    fecha: datetime


class ConfirmacionRequest(BaseModel):
    actor: str
    fecha: datetime


class ValidacionRequest(BaseModel):
    condicion: dominio.CondicionDeExtraccion
    actor: str
    fecha: datetime


# RF-EX-001
@app.post("/textos", status_code=201)
def recibir_unidad(
    request: RecibirUnidadRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto, evento = dominio.recibir_unidad(
        id=request.id,
        unidad_documental_candidata_id=request.unidad_documental_candidata_id,
        procedencia=dominio.ProcedenciaHeredada(
            fuente=request.procedencia.fuente,
            fecha=request.procedencia.fecha,
            disparador=request.procedencia.disparador,
            lote_o_flujo_id=request.procedencia.lote_o_flujo_id,
            item_ingesta_id=request.procedencia.item_ingesta_id,
            unidad_documental_id=request.procedencia.unidad_documental_id,
        ),
        actor=request.actor,
        fecha=request.fecha,
    )
    almacen.guardar_con_evento(texto, evento)
    return texto


# RF-EX-006 (mismo criterio que GET /sugerencias/pendientes en
# records-custodia, T-28, y GET /unidades/pendientes-de-limites en
# normalizacion, T-39): declarado ANTES de GET /textos/{id} porque
# FastAPI/Starlette resuelve rutas por orden de declaración — si fuera al
# revés, "pendientes-de-revision" se interpretaría como un {id} literal.
# `umbral` es obligatorio, sin valor por defecto: la spec (§8
# [CLARIFICAR]) deja el umbral de calidad sin fijar, así que nunca se
# inventa uno aquí — lo declara el llamador en cada consulta.
@app.get("/textos/pendientes-de-revision")
def textos_pendientes_de_revision(
    umbral: float, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> list[dominio.TextoExtraido]:
    return dominio.candidatas_a_revision_por_baja_confianza(almacen.todas(), umbral)


@app.get("/textos/{id}")
def consultar(id: str, almacen: AlmacenDeTextos = Depends(obtener_almacen)) -> dominio.TextoExtraido:
    return _o_404(almacen.buscar(id), id)


# RF-EX-002
@app.post("/textos/{id}/soporte")
def determinar_soporte(
    id: str, request: SoporteRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    actualizado, evento = dominio.determinar_soporte(texto, request.soporte, actor=request.actor, fecha=request.fecha)
    almacen.guardar_con_evento(actualizado, evento)
    return actualizado


# RF-EX-003
@app.post("/textos/{id}/extraccion-born-digital")
def extraer_texto_born_digital(
    id: str, request: ExtraccionBornDigitalRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    actualizado, evento = dominio.extraer_texto_born_digital(
        texto, contenido=request.contenido, actor=request.actor, fecha=request.fecha
    )
    almacen.guardar_con_evento(actualizado, evento)
    return actualizado


# RF-EX-004: componente FICTICIO — solo recibe una sugerencia ya calculada.
@app.post("/textos/{id}/sugerencia-ocr")
def recibir_sugerencia_ocr(
    id: str, request: SugerenciaOcrRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    actualizado, evento = dominio.recibir_sugerencia_ocr(
        texto,
        dominio.SugerenciaOcr(
            modelo_id=request.modelo_id,
            contenido=request.contenido,
            calidad=request.calidad,
            evidencia=request.evidencia,
            fecha=request.fecha,
        ),
    )
    almacen.guardar_con_evento(actualizado, evento)
    return actualizado


# RF-EX-011: única operación que materializa Extraído a partir de una
# sugerencia de OCR (P-01) — ver QUESTIONS.md 2026-08-27.
@app.post("/textos/{id}/confirmacion")
def confirmar_extraccion(
    id: str, request: ConfirmacionRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    actualizado, evento = dominio.confirmar_extraccion(texto, actor=request.actor, fecha=request.fecha)
    almacen.guardar_con_evento(actualizado, evento)
    return actualizado


# RF-EX-009
@app.post("/textos/{id}/validacion")
def validar(
    id: str, request: ValidacionRequest, almacen: AlmacenDeTextos = Depends(obtener_almacen)
) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    actualizado, evento = dominio.marcar_cuarentena_o_rechazo(
        texto, request.condicion, actor=request.actor, fecha=request.fecha
    )
    almacen.guardar_con_evento(actualizado, evento)
    return actualizado


# RF-EX-010: entregar() es una validación de solo lectura (no transiciona
# estado — "Extraído" ya es terminal de éxito, spec §3), por eso no anexa
# evento ni recibe actor/fecha; un texto no Extraído responde 409 vía el
# mismo manejador de ErrorDeDominio que el resto de este módulo.
@app.get("/textos/{id}/entrega")
def entregar(id: str, almacen: AlmacenDeTextos = Depends(obtener_almacen)) -> dominio.TextoExtraido:
    texto = _o_404(almacen.buscar(id), id)
    return dominio.entregar(texto)


# RF-EX-008. FastAPI/Pydantic no serializa las `@property` de un dataclass
# stdlib — por eso `terminales`/`sin_perdida_silenciosa` se exponen
# explícitas aquí en vez de devolver `ConteoPorEstado` tal cual (mismo
# hallazgo que normalizacion, T-34).
@app.get("/lotes/{lote_o_flujo_id}/conteo")
def conteo_por_estado(lote_o_flujo_id: str, almacen: AlmacenDeTextos = Depends(obtener_almacen)) -> dict:
    conteo = dominio.contar_por_estado(almacen.de_lote(lote_o_flujo_id))
    return {
        "por_estado": conteo.por_estado,
        "total": conteo.total,
        "terminales": conteo.terminales,
        "sin_perdida_silenciosa": conteo.sin_perdida_silenciosa,
    }


# P-08: observabilidad de la bitácora, mismo criterio que
# GET /eventos-auditoria en normalizacion y GET /eventos-seguridad en
# seguridad-acceso.
@app.get("/eventos-auditoria")
def eventos_de_auditoria(almacen: AlmacenDeTextos = Depends(obtener_almacen)) -> list[dominio.EventoAuditoria]:
    return almacen.eventos_de_auditoria()
