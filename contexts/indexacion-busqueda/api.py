from dataclasses import replace
from datetime import datetime
from typing import Generator

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session, sessionmaker

import dominio
from integracion import VerificadorDePermisosHttp
from persistencia import AlmacenDeEntradas, IndiceLexicoAutoalojado, IndiceVectorialAutoalojado, crear_fabrica_de_sesiones

# specs/spec-infra-servicios.md §14 · Contrato mínimo — indexacion-busqueda.
# Cada endpoint traduce un método de dominio ya probado por TDD (T-54); este
# módulo no añade regla de negocio, solo entrada/salida HTTP y el cableado de
# los puertos P-03 (IndiceLexico/IndiceVectorial/VerificadorDePermisos) —
# mismo criterio que api.py en extraccion (T-41).
app = FastAPI(title="indexacion-busqueda")

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


def obtener_almacen(sesion: Session = Depends(obtener_sesion)) -> AlmacenDeEntradas:
    return AlmacenDeEntradas(sesion)


# P-03: implementaciones AUTOALOJADAS reales inyectadas vía Depends — el
# endpoint (y las funciones de dominio.py) desconocen que detrás hay una
# consulta directa a Postgres. La variante GESTIONADA (integracion.py)
# existe como clase intercambiable pero no se inyecta aquí por defecto: no
# hay ningún servicio gestionado real desplegado en este proyecto.
def obtener_indice_lexico(sesion: Session = Depends(obtener_sesion)) -> dominio.IndiceLexico:
    return IndiceLexicoAutoalojado(sesion)


def obtener_indice_vectorial(sesion: Session = Depends(obtener_sesion)) -> dominio.IndiceVectorial:
    return IndiceVectorialAutoalojado(sesion)


def obtener_verificador() -> dominio.VerificadorDePermisos:
    return VerificadorDePermisosHttp()


def _o_404(entrada: dominio.EntradaDeIndice | None, id: str) -> dominio.EntradaDeIndice:
    if entrada is None:
        raise HTTPException(status_code=404, detail=f"No encontrado: {id}")
    return entrada


# CUARTO VETO real de Codex (ver STATE.md): `AlmacenDeEntradas`/
# `IndiceLexicoAutoalojado` ya no leen la tabla de `IndiceVectorial`
# directamente (eso acoplaba la orquestación a la variante AUTOALOJADA) --
# siempre devuelven `embedding=None`. Esta es la ÚNICA función que lo
# completa, y lo hace a través del puerto ya inyectado (sea cual sea la
# variante activa, gestionada o autoalojada) — la orquestación (api.py)
# puede combinar dos puertos sin acoplarse a ninguno de los dos.
def _con_embedding(entrada: dominio.EntradaDeIndice, indice_vectorial: dominio.IndiceVectorial) -> dominio.EntradaDeIndice:
    if entrada.estado != dominio.EstadoEntradaDeIndice.INDEXADA:
        return entrada
    return replace(entrada, embedding=indice_vectorial.obtener(entrada.id))


@app.exception_handler(dominio.ErrorDeDominio)
def _manejar_error_de_dominio(request, exc: dominio.ErrorDeDominio):
    return JSONResponse(status_code=409, content={"error": str(exc)})


class DocumentoMaterializadoRequest(BaseModel):
    id: str
    documento_id: str
    texto_extraido: str
    metadatos: dict[str, str] = {}
    actor: str
    fecha: datetime


class IndexacionRequest(BaseModel):
    texto_extraido: str
    metadatos: dict[str, str] = {}
    embedding: list[float]
    actor: str
    fecha: datetime


class ActualizacionRequest(BaseModel):
    texto_extraido: str | None = None
    metadatos: dict[str, str] | None = None
    embedding: list[float] | None = None
    actor: str
    fecha: datetime


class BusquedaRequest(BaseModel):
    termino: str
    filtros: dict[str, str] = {}
    actor: str
    fecha: datetime


# RF-IB-006 (FICTICIO): el llamador ya calculó el orden de relevancia y lo
# declara aquí como una lista de ids de entrada, en ese orden — esta capa
# HTTP resuelve cada id a su `EntradaDeIndice` real, preservando el orden
# recibido (dominio.recuperar_por_relevancia nunca reordena).
class RecuperacionRequest(BaseModel):
    entrada_ids_ordenados: list[str]
    actor: str
    fecha: datetime


class CitaDto(BaseModel):
    documento_id: str
    fragmento: str


# RF-IB-007/010 (FICTICIO): respuesta/citas ya calculadas por el llamador, o
# una razón de negativa apropiada declarada — mismo criterio que
# MarcaNoClasificable/MarcaNoEnriquecible.
class PreguntaRequest(BaseModel):
    pregunta: str
    respuesta: str | None = None
    citas: list[CitaDto] = []
    modelo_id: str
    actor: str
    fecha: datetime
    razon_negativa: str | None = None


# RF-IB-001
@app.post("/entradas", status_code=201)
def recibir_documento_materializado(
    request: DocumentoMaterializadoRequest, almacen: AlmacenDeEntradas = Depends(obtener_almacen)
) -> dominio.EntradaDeIndice:
    documento = dominio.recibir_documento_materializado(
        documento_id=request.documento_id, texto_extraido=request.texto_extraido, metadatos=request.metadatos
    )
    pendiente, evento = dominio.crear_entrada_pendiente(request.id, documento, actor=request.actor, fecha=request.fecha)
    almacen.guardar_con_evento(pendiente, evento)
    return pendiente


# RF-IB-002/003
@app.post("/entradas/{id}/indexacion")
def indexar(
    id: str,
    request: IndexacionRequest,
    almacen: AlmacenDeEntradas = Depends(obtener_almacen),
    indice_lexico: dominio.IndiceLexico = Depends(obtener_indice_lexico),
    indice_vectorial: dominio.IndiceVectorial = Depends(obtener_indice_vectorial),
) -> dominio.EntradaDeIndice:
    entrada = _o_404(almacen.obtener(id), id)
    actualizada, evento = dominio.indexar(
        entrada,
        texto_extraido=request.texto_extraido,
        metadatos=request.metadatos,
        embedding=request.embedding,
        indice_lexico=indice_lexico,
        indice_vectorial=indice_vectorial,
        actor=request.actor,
        fecha=request.fecha,
    )
    almacen.guardar_con_evento(actualizada, evento)
    return actualizada


# RF-IB-004. `entrada` se enriquece con su embedding real ANTES de llamar al
# dominio: si `request.embedding` viene vacío (actualización parcial),
# `dominio.actualizar_entrada` preserva `entrada.embedding` tal cual —  sin
# este paso, el embedding ya persistido se perdería en la respuesta (aunque
# seguiría intacto en `indices_vectoriales`, ver VETO real de Codex sobre
# 53ce657 en STATE.md).
@app.post("/entradas/{id}/actualizacion")
def actualizar_entrada(
    id: str,
    request: ActualizacionRequest,
    almacen: AlmacenDeEntradas = Depends(obtener_almacen),
    indice_lexico: dominio.IndiceLexico = Depends(obtener_indice_lexico),
    indice_vectorial: dominio.IndiceVectorial = Depends(obtener_indice_vectorial),
) -> dominio.EntradaDeIndice:
    entrada = _con_embedding(_o_404(almacen.obtener(id), id), indice_vectorial)
    actualizada, evento = dominio.actualizar_entrada(
        entrada,
        indice_lexico=indice_lexico,
        indice_vectorial=indice_vectorial,
        actor=request.actor,
        fecha=request.fecha,
        texto_extraido=request.texto_extraido,
        metadatos=request.metadatos,
        embedding=request.embedding,
    )
    almacen.guardar_con_evento(actualizada, evento)
    return actualizada


# RF-IB-005/008/009: busca vía el índice léxico real, filtra por permiso
# (una llamada real a VerificadorDePermisos por candidato) y persiste el
# evento de acceso de forma append-only en la MISMA petición, ANTES de
# responder (P-08, VETO real de Codex sobre 22b6b09/e356158 — devolver el
# evento no basta, tiene que quedar en la bitácora).
@app.post("/busquedas")
def buscar(
    request: BusquedaRequest,
    almacen: AlmacenDeEntradas = Depends(obtener_almacen),
    indice: dominio.IndiceLexico = Depends(obtener_indice_lexico),
    indice_vectorial: dominio.IndiceVectorial = Depends(obtener_indice_vectorial),
    verificador: dominio.VerificadorDePermisos = Depends(obtener_verificador),
) -> list[dominio.EntradaDeIndice]:
    resultados, evento = dominio.buscar(
        indice=indice, termino=request.termino, filtros=request.filtros, verificador=verificador, actor=request.actor, fecha=request.fecha
    )
    almacen.guardar_evento_de_acceso(evento)
    return [_con_embedding(r, indice_vectorial) for r in resultados]


@app.post("/recuperaciones")
def recuperar_por_relevancia(
    request: RecuperacionRequest,
    almacen: AlmacenDeEntradas = Depends(obtener_almacen),
    indice_vectorial: dominio.IndiceVectorial = Depends(obtener_indice_vectorial),
    verificador: dominio.VerificadorDePermisos = Depends(obtener_verificador),
) -> list[dominio.EntradaDeIndice]:
    candidatos_ordenados = [
        _con_embedding(_o_404(almacen.obtener(id), id), indice_vectorial) for id in request.entrada_ids_ordenados
    ]
    resultados, evento = dominio.recuperar_por_relevancia(
        candidatos_ordenados=candidatos_ordenados, verificador=verificador, actor=request.actor, fecha=request.fecha
    )
    almacen.guardar_evento_de_acceso(evento)
    return resultados


@app.post("/preguntas")
def responder_qa(
    request: PreguntaRequest,
    almacen: AlmacenDeEntradas = Depends(obtener_almacen),
    verificador: dominio.VerificadorDePermisos = Depends(obtener_verificador),
) -> dominio.RespuestaQA | dominio.NegativaApropiada:
    citas = [dominio.Cita(documento_id=c.documento_id, fragmento=c.fragmento) for c in request.citas]
    resultado, evento = dominio.responder_qa(
        pregunta=request.pregunta,
        respuesta=request.respuesta,
        citas=citas,
        verificador=verificador,
        modelo_id=request.modelo_id,
        actor=request.actor,
        fecha=request.fecha,
        razon_negativa=request.razon_negativa,
    )
    almacen.guardar_evento_de_acceso(evento)
    return resultado


# P-08: expone tanto los eventos de transición de EntradaDeIndice
# (recepción/indexación/actualización) como los de acceso por consulta
# (RF-IB-009) — dos bitácoras distintas (persistencia.py), un solo punto de
# observabilidad, mismo criterio que GET /eventos-auditoria en
# normalizacion/extraccion/records-custodia.
@app.get("/eventos-auditoria")
def eventos_de_auditoria(almacen: AlmacenDeEntradas = Depends(obtener_almacen)) -> dict:
    return {
        "transiciones": almacen.eventos_de_auditoria(),
        "accesos": almacen.eventos_de_acceso(),
    }
