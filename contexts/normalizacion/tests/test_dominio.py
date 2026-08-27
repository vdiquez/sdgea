from datetime import datetime

import pytest
from dominio import (
    CondicionDeNormalizacion,
    ConfirmacionHumanaDeLimites,
    EstadoUnidadDocumental,
    ErrorDeDominio,
    ProcedenciaHeredada,
    SugerenciaDeLimites,
    UnidadDocumentalCandidata,
    confirmar_limites,
    contar_por_estado,
    entregar,
    marcar_cuarentena_o_rechazo,
    normalizar,
    pendientes_de_limites,
    recibir_item,
    recibir_sugerencia_de_limites,
)

PROCEDENCIA = ProcedenciaHeredada(
    fuente="escaner-sala-3",
    fecha=datetime.fromisoformat("2026-08-26T00:00:00+00:00"),
    disparador="carga_por_lote",
    lote_o_flujo_id="lote-001",
    item_ingesta_id="item-001",
)


def _unidad_pendiente(huella: str | None = None) -> UnidadDocumentalCandidata:
    unidad, _ = recibir_item(
        id="unidad-1",
        lote_id="lote-001",
        item_ingesta_id="item-001",
        procedencia=PROCEDENCIA,
        huella_de_contenido=huella,
        es_caso_trivial=False,
        actor="sistema-normalizacion",
    )
    return unidad


# RF-NO-001/003 · Recepción de ítems validados y caso trivial
class TestRecepcionDeItems:
    def test_dado_un_artefacto_no_trivial_la_unidad_queda_pendiente_de_limites(self):
        unidad, evento = recibir_item(
            id="unidad-1",
            lote_id="lote-001",
            item_ingesta_id="item-001",
            procedencia=PROCEDENCIA,
            huella_de_contenido=None,
            es_caso_trivial=False,
            actor="sistema-normalizacion",
        )

        assert unidad.estado == EstadoUnidadDocumental.PENDIENTE_DE_LIMITES
        assert unidad.sugerencia_de_limites is None
        assert evento.estado_posterior == "PENDIENTE_DE_LIMITES"
        assert evento.actor == "sistema-normalizacion"

    def test_dado_un_artefacto_declarado_de_un_unico_documento_la_unidad_queda_con_limites_confirmados(self):
        unidad, evento = recibir_item(
            id="unidad-1",
            lote_id="lote-001",
            item_ingesta_id="item-001",
            procedencia=PROCEDENCIA,
            huella_de_contenido=None,
            es_caso_trivial=True,
            actor="sistema-normalizacion",
        )

        assert unidad.estado == EstadoUnidadDocumental.LIMITES_CONFIRMADOS
        assert unidad.sugerencia_de_limites is None
        assert evento.estado_posterior == "LIMITES_CONFIRMADOS"


# RF-NO-002 · Detección probabilística de límites (componente FICTICIO)
class TestSugerenciaDeLimites:
    def test_una_sugerencia_de_limites_no_confirma_la_unidad_por_si_sola(self):
        unidad = _unidad_pendiente()
        sugerencia = SugerenciaDeLimites(
            modelo_id="emisor-ficticio-v0", evidencia=["pagina-1"], confianza=0.42, fecha=PROCEDENCIA.fecha
        )

        actualizada, evento = recibir_sugerencia_de_limites(unidad, sugerencia)

        assert actualizada.sugerencia_de_limites == sugerencia
        assert actualizada.estado == EstadoUnidadDocumental.PENDIENTE_DE_LIMITES
        assert evento.actor == "emisor-ficticio-v0"
        assert evento.estado_anterior is None

    def test_no_se_puede_recibir_una_sugerencia_para_una_unidad_que_ya_no_esta_pendiente(self):
        unidad, _ = confirmar_limites(_unidad_pendiente(), actor="archivista-1", fecha=PROCEDENCIA.fecha)
        sugerencia = SugerenciaDeLimites(modelo_id="m", evidencia=[], confianza=0.5, fecha=PROCEDENCIA.fecha)

        with pytest.raises(ErrorDeDominio):
            recibir_sugerencia_de_limites(unidad, sugerencia)


# RF-NO-004 · Confirmación humana de límites
class TestConfirmacionDeLimites:
    def test_dada_una_sugerencia_pendiente_cuando_un_humano_la_confirma_queda_con_limites_confirmados_y_actor_y_fecha(self):
        unidad = _unidad_pendiente()

        confirmada, evento = confirmar_limites(unidad, actor="archivista-1", fecha=PROCEDENCIA.fecha)

        assert confirmada.estado == EstadoUnidadDocumental.LIMITES_CONFIRMADOS
        assert confirmada.confirmacion_limites == ConfirmacionHumanaDeLimites(
            actor="archivista-1", fecha=PROCEDENCIA.fecha
        )
        assert evento.actor == "archivista-1"
        assert evento.estado_anterior == "PENDIENTE_DE_LIMITES"
        assert evento.estado_posterior == "LIMITES_CONFIRMADOS"

    def test_no_se_puede_confirmar_limites_dos_veces(self):
        unidad, _ = confirmar_limites(_unidad_pendiente(), actor="archivista-1", fecha=PROCEDENCIA.fecha)

        with pytest.raises(ErrorDeDominio):
            confirmar_limites(unidad, actor="archivista-2", fecha=PROCEDENCIA.fecha)


# RF-NO-005 · Normalización a formato de preservación
class TestNormalizacion:
    def test_dada_una_unidad_con_limites_confirmados_cuando_se_normaliza_queda_normalizada_con_referencia_al_formato(self):
        unidad, _ = confirmar_limites(_unidad_pendiente(), actor="archivista-1", fecha=PROCEDENCIA.fecha)

        normalizada, evento = normalizar(
            unidad, formato_normalizado="application/pdf", actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha
        )

        assert normalizada.estado == EstadoUnidadDocumental.NORMALIZADA
        assert normalizada.formato_normalizado == "application/pdf"
        assert evento.estado_anterior == "LIMITES_CONFIRMADOS"
        assert evento.estado_posterior == "NORMALIZADA"

    def test_no_se_puede_normalizar_una_unidad_sin_limites_confirmados(self):
        unidad = _unidad_pendiente()

        with pytest.raises(ErrorDeDominio):
            normalizar(unidad, formato_normalizado="application/pdf", actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)


# RF-NO-009 · Validación y cuarentena de unidades candidatas
class TestValidacionYCuarentena:
    @pytest.mark.parametrize(
        "condicion",
        [CondicionDeNormalizacion.CORRUPTO, CondicionDeNormalizacion.ILEGIBLE],
    )
    def test_corrupto_o_ilegible_queda_en_cuarentena_con_razon(self, condicion):
        unidad, evento = marcar_cuarentena_o_rechazo(_unidad_pendiente(), condicion, actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)

        assert unidad.estado == EstadoUnidadDocumental.EN_CUARENTENA
        assert unidad.razon
        assert evento.estado_posterior == "EN_CUARENTENA"

    def test_formato_no_soportado_queda_rechazada_con_razon(self):
        unidad, evento = marcar_cuarentena_o_rechazo(
            _unidad_pendiente(), CondicionDeNormalizacion.FORMATO_NO_SOPORTADO, actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha
        )

        assert unidad.estado == EstadoUnidadDocumental.RECHAZADA
        assert unidad.razon
        assert evento.estado_posterior == "RECHAZADA"


# RF-NO-006/010 · Deduplicación y entrega a Extracción
class TestEntregaYDeduplicacion:
    def test_una_unidad_normalizada_sin_duplicado_se_entrega_a_extraccion(self):
        confirmada, _ = confirmar_limites(_unidad_pendiente(huella="huella-a"), actor="archivista-1", fecha=PROCEDENCIA.fecha)
        unidad, _ = normalizar(confirmada, formato_normalizado="application/pdf", actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)

        entregada, evento = entregar(unidad, huellas_ya_entregadas=set(), actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)

        assert entregada.estado == EstadoUnidadDocumental.ENTREGADA_A_EXTRACCION
        assert evento.estado_anterior == "NORMALIZADA"
        assert evento.estado_posterior == "ENTREGADA_A_EXTRACCION"

    def test_una_unidad_cuyo_contenido_ya_fue_entregado_queda_vinculada_a_duplicado(self):
        confirmada, _ = confirmar_limites(
            _unidad_pendiente(huella="huella-repetida"), actor="archivista-1", fecha=PROCEDENCIA.fecha
        )
        unidad, _ = normalizar(confirmada, formato_normalizado="application/pdf", actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)

        entregada, evento = entregar(
            unidad, huellas_ya_entregadas={"huella-repetida"}, actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha
        )

        assert entregada.estado == EstadoUnidadDocumental.VINCULADA_A_DUPLICADO
        assert evento.estado_posterior == "VINCULADA_A_DUPLICADO"

    def test_no_se_puede_entregar_una_unidad_que_no_esta_normalizada(self):
        unidad = _unidad_pendiente()

        with pytest.raises(ErrorDeDominio):
            entregar(unidad, huellas_ya_entregadas=set(), actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)


# RF-NO-008 · Cero pérdida silenciosa
class TestCeroPerdidaSilenciosa:
    def test_la_cuenta_de_estados_terminales_iguala_el_total_cuando_todas_las_unidades_llegaron_a_un_terminal(self):
        confirmada, _ = confirmar_limites(_unidad_pendiente(huella="a"), actor="archivista-1", fecha=PROCEDENCIA.fecha)
        normalizada, _ = normalizar(confirmada, formato_normalizado="application/pdf", actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)
        entregada, _ = entregar(normalizada, huellas_ya_entregadas=set(), actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha)
        rechazada, _ = marcar_cuarentena_o_rechazo(
            _unidad_pendiente(), CondicionDeNormalizacion.FORMATO_NO_SOPORTADO, actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha
        )

        conteo = contar_por_estado([entregada, rechazada])

        assert conteo.total == 2
        assert conteo.terminales == 2
        assert conteo.sin_perdida_silenciosa is True

    def test_una_unidad_no_terminal_rompe_la_invariante(self):
        conteo = contar_por_estado([_unidad_pendiente()])

        assert conteo.sin_perdida_silenciosa is False


# P-08 (hallazgo V-01 de la revision acumulada de Codex, ver REVIEW.md) · toda
# transición produce un evento de auditoría atribuible, fechado y con estado
# anterior/posterior.
class TestAuditoriaDeTransiciones:
    def test_recibir_item_produce_un_evento_sin_estado_anterior(self):
        _, evento = recibir_item(
            id="unidad-1",
            lote_id="lote-001",
            item_ingesta_id="item-001",
            procedencia=PROCEDENCIA,
            huella_de_contenido=None,
            es_caso_trivial=False,
            actor="sistema-normalizacion",
        )

        assert evento.tipo == "UNIDAD_RECIBIDA"
        assert evento.estado_anterior is None
        assert evento.fecha == PROCEDENCIA.fecha

    def test_marcar_cuarentena_o_rechazo_conserva_el_estado_anterior_pendiente(self):
        _, evento = marcar_cuarentena_o_rechazo(
            _unidad_pendiente(), CondicionDeNormalizacion.CORRUPTO, actor="sistema-normalizacion", fecha=PROCEDENCIA.fecha
        )

        assert evento.estado_anterior == "PENDIENTE_DE_LIMITES"
        assert evento.tipo == "VALIDACION_APLICADA"


# RF-VH-001 (T-39) · Agregación de unidades con sugerencia de límites pendiente de confirmación
class TestPendientesDeLimites:
    def test_una_unidad_con_sugerencia_y_sin_confirmar_aparece_como_pendiente(self):
        unidad = _unidad_pendiente()
        sugerencia = SugerenciaDeLimites(modelo_id="m", evidencia=[], confianza=0.4, fecha=PROCEDENCIA.fecha)
        con_sugerencia, _ = recibir_sugerencia_de_limites(unidad, sugerencia)

        pendientes = pendientes_de_limites([con_sugerencia])

        assert pendientes == [con_sugerencia]

    def test_una_unidad_sin_sugerencia_todavia_no_es_pendiente_de_revision(self):
        assert pendientes_de_limites([_unidad_pendiente()]) == []

    def test_una_unidad_ya_confirmada_deja_de_ser_pendiente_aunque_tenga_sugerencia(self):
        unidad = _unidad_pendiente()
        sugerencia = SugerenciaDeLimites(modelo_id="m", evidencia=[], confianza=0.4, fecha=PROCEDENCIA.fecha)
        con_sugerencia, _ = recibir_sugerencia_de_limites(unidad, sugerencia)
        confirmada, _ = confirmar_limites(con_sugerencia, actor="archivista-1", fecha=PROCEDENCIA.fecha)

        assert pendientes_de_limites([confirmada]) == []
