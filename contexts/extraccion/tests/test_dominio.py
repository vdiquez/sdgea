from datetime import datetime

import pytest
from dominio import (
    CondicionDeExtraccion,
    ErrorDeDominio,
    EstadoTextoExtraido,
    ProcedenciaHeredada,
    ResultadoOcr,
    Soporte,
    TextoExtraido,
    candidatas_a_revision_por_baja_confianza,
    contar_por_estado,
    determinar_soporte,
    entregar,
    extraer_texto_born_digital,
    marcar_cuarentena_o_rechazo,
    recibir_resultado_ocr,
    recibir_unidad,
)

PROCEDENCIA = ProcedenciaHeredada(
    fuente="escaner-sala-3",
    fecha=datetime.fromisoformat("2026-08-27T00:00:00+00:00"),
    disparador="carga_por_lote",
    lote_o_flujo_id="lote-001",
    item_ingesta_id="item-001",
    unidad_documental_id="unidad-1",
)


def _texto_pendiente() -> TextoExtraido:
    texto, _ = recibir_unidad(
        id="texto-1",
        unidad_documental_candidata_id="unidad-1",
        procedencia=PROCEDENCIA,
        actor="sistema-extraccion",
        fecha=PROCEDENCIA.fecha,
    )
    return texto


def _texto_born_digital() -> TextoExtraido:
    texto, _ = determinar_soporte(_texto_pendiente(), Soporte.BORN_DIGITAL, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)
    return texto


def _texto_escaneo() -> TextoExtraido:
    texto, _ = determinar_soporte(_texto_pendiente(), Soporte.ESCANEO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)
    return texto


# RF-EX-001 · Recepción de unidades documentales normalizadas
class TestRecepcion:
    def test_dada_una_unidad_entregada_a_extraccion_cuando_se_recibe_queda_pendiente_de_extraccion(self):
        texto, evento = recibir_unidad(
            id="texto-1",
            unidad_documental_candidata_id="unidad-1",
            procedencia=PROCEDENCIA,
            actor="sistema-extraccion",
            fecha=PROCEDENCIA.fecha,
        )

        assert texto.estado == EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION
        assert texto.unidad_documental_candidata_id == "unidad-1"
        assert texto.procedencia == PROCEDENCIA
        assert evento.estado_anterior is None
        assert evento.estado_posterior == "PENDIENTE_DE_EXTRACCION"


# RF-EX-002 · Determinación de soporte
class TestDeterminacionDeSoporte:
    def test_una_unidad_queda_marcada_born_digital_antes_de_extraer(self):
        actualizado, evento = determinar_soporte(_texto_pendiente(), Soporte.BORN_DIGITAL, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert actualizado.soporte == Soporte.BORN_DIGITAL
        assert actualizado.estado == EstadoTextoExtraido.PENDIENTE_DE_EXTRACCION
        assert evento.tipo == "SOPORTE_DETERMINADO"

    def test_una_unidad_queda_marcada_escaneo_antes_de_extraer(self):
        actualizado, _ = determinar_soporte(_texto_pendiente(), Soporte.ESCANEO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert actualizado.soporte == Soporte.ESCANEO

    def test_no_se_puede_determinar_soporte_de_un_texto_ya_extraido(self):
        extraido, _ = extraer_texto_born_digital(_texto_born_digital(), contenido="hola", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        with pytest.raises(ErrorDeDominio):
            determinar_soporte(extraido, Soporte.ESCANEO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)


# RF-EX-003 · Extracción determinística de texto embebido (born-digital)
class TestExtraccionBornDigital:
    def test_dado_un_soporte_born_digital_cuando_se_extrae_queda_extraido_con_calidad_maxima_sin_ocr(self):
        extraido, evento = extraer_texto_born_digital(_texto_born_digital(), contenido="contenido embebido", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert extraido.estado == EstadoTextoExtraido.EXTRAIDO
        assert extraido.contenido == "contenido embebido"
        assert extraido.calidad == 1.0
        assert evento.tipo == "EXTRACCION_DETERMINISTICA_APLICADA"
        assert evento.estado_anterior == "PENDIENTE_DE_EXTRACCION"
        assert evento.estado_posterior == "EXTRAIDO"

    def test_no_se_puede_extraer_como_born_digital_un_texto_marcado_como_escaneo(self):
        with pytest.raises(ErrorDeDominio):
            extraer_texto_born_digital(_texto_escaneo(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

    def test_no_se_puede_extraer_como_born_digital_sin_soporte_determinado(self):
        with pytest.raises(ErrorDeDominio):
            extraer_texto_born_digital(_texto_pendiente(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)


# RF-EX-004 · Extracción probabilística de texto vía OCR (escaneo) — componente FICTICIO
class TestExtraccionViaOcr:
    def test_dado_un_soporte_de_escaneo_cuando_se_recibe_el_resultado_de_ocr_queda_extraido_con_calidad_estimada(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="texto reconocido", calidad=0.73, fecha=PROCEDENCIA.fecha)

        extraido, evento = recibir_resultado_ocr(_texto_escaneo(), resultado)

        assert extraido.estado == EstadoTextoExtraido.EXTRAIDO
        assert extraido.contenido == "texto reconocido"
        assert extraido.calidad == 0.73
        assert evento.actor == "ocr-ficticio-v0"
        assert evento.estado_anterior == "PENDIENTE_DE_EXTRACCION"
        assert evento.estado_posterior == "EXTRAIDO"

    def test_no_se_puede_recibir_resultado_de_ocr_para_un_texto_marcado_como_born_digital(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.5, fecha=PROCEDENCIA.fecha)

        with pytest.raises(ErrorDeDominio):
            recibir_resultado_ocr(_texto_born_digital(), resultado)


# RF-EX-005 · Estratificación de calidad de la extracción
class TestEstratificacionDeCalidad:
    def test_un_texto_extraido_expone_su_calidad_y_soporte_de_origen_born_digital(self):
        extraido, _ = extraer_texto_born_digital(_texto_born_digital(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert extraido.calidad == 1.0
        assert extraido.soporte == Soporte.BORN_DIGITAL

    def test_un_texto_extraido_expone_su_calidad_y_soporte_de_origen_escaneo(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.6, fecha=PROCEDENCIA.fecha)

        extraido, _ = recibir_resultado_ocr(_texto_escaneo(), resultado)

        assert extraido.calidad == 0.6
        assert extraido.soporte == Soporte.ESCANEO


# RF-EX-006 · Enrutamiento de baja confianza a revisión humana
class TestRevisionPorBajaConfianza:
    def test_un_texto_bajo_el_umbral_aparece_como_candidato_a_revision(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.3, fecha=PROCEDENCIA.fecha)
        extraido, _ = recibir_resultado_ocr(_texto_escaneo(), resultado)

        candidatas = candidatas_a_revision_por_baja_confianza([extraido], umbral=0.5)

        assert candidatas == [extraido]

    def test_un_texto_sobre_el_umbral_no_aparece_como_candidato(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.9, fecha=PROCEDENCIA.fecha)
        extraido, _ = recibir_resultado_ocr(_texto_escaneo(), resultado)

        assert candidatas_a_revision_por_baja_confianza([extraido], umbral=0.5) == []

    def test_un_texto_de_baja_confianza_conserva_su_marca_al_entregarse(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.2, fecha=PROCEDENCIA.fecha)
        extraido, _ = recibir_resultado_ocr(_texto_escaneo(), resultado)

        entregado = entregar(extraido)

        assert entregado.calidad == 0.2


# RF-EX-007 · Propagación de procedencia
class TestPropagacionDeProcedencia:
    def test_un_texto_extraido_conserva_la_procedencia_completa_de_su_unidad_de_origen(self):
        extraido, _ = extraer_texto_born_digital(_texto_born_digital(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert extraido.procedencia == PROCEDENCIA


# RF-EX-008 · Cero pérdida silenciosa
class TestCeroPerdidaSilenciosa:
    def test_la_cuenta_de_estados_terminales_iguala_el_total_cuando_todos_llegaron_a_un_terminal(self):
        extraido, _ = extraer_texto_born_digital(_texto_born_digital(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)
        rechazado, _ = marcar_cuarentena_o_rechazo(_texto_pendiente(), CondicionDeExtraccion.FORMATO_NO_SOPORTADO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        conteo = contar_por_estado([extraido, rechazado])

        assert conteo.total == 2
        assert conteo.terminales == 2
        assert conteo.sin_perdida_silenciosa is True

    def test_un_texto_no_terminal_rompe_la_invariante(self):
        conteo = contar_por_estado([_texto_pendiente()])

        assert conteo.sin_perdida_silenciosa is False


# RF-EX-009 · Validación y cuarentena de extracciones
class TestValidacionYCuarentena:
    @pytest.mark.parametrize("condicion", [CondicionDeExtraccion.CORRUPTO, CondicionDeExtraccion.ILEGIBLE])
    def test_corrupto_o_ilegible_queda_en_cuarentena_con_razon(self, condicion):
        texto, evento = marcar_cuarentena_o_rechazo(_texto_pendiente(), condicion, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert texto.estado == EstadoTextoExtraido.EN_CUARENTENA
        assert texto.razon
        assert evento.estado_posterior == "EN_CUARENTENA"

    def test_formato_no_soportado_queda_rechazado_con_razon(self):
        texto, evento = marcar_cuarentena_o_rechazo(_texto_pendiente(), CondicionDeExtraccion.FORMATO_NO_SOPORTADO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert texto.estado == EstadoTextoExtraido.RECHAZADO
        assert texto.razon
        assert evento.estado_posterior == "RECHAZADO"


# RF-EX-010 · Entrega a Clasificación, Enriquecimiento e Indexación y Búsqueda
class TestEntrega:
    def test_un_texto_extraido_se_entrega_con_su_procedencia_y_calidad(self):
        texto, _ = extraer_texto_born_digital(_texto_born_digital(), contenido="x", actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        entregado = entregar(texto)

        assert entregado.procedencia == PROCEDENCIA
        assert entregado.calidad == 1.0

    def test_no_se_puede_entregar_un_texto_que_no_esta_extraido(self):
        with pytest.raises(ErrorDeDominio):
            entregar(_texto_pendiente())


# P-08 · toda transición produce un evento de auditoría atribuible, fechado y con
# estado anterior/posterior — desde el primer commit de este contexto (lección de
# T-37/V-01 en normalizacion: no se repite el error de agregarlo como fix posterior).
class TestAuditoriaDeTransiciones:
    def test_recibir_unidad_produce_un_evento_sin_estado_anterior(self):
        _, evento = recibir_unidad(
            id="texto-1",
            unidad_documental_candidata_id="unidad-1",
            procedencia=PROCEDENCIA,
            actor="sistema-extraccion",
            fecha=PROCEDENCIA.fecha,
        )

        assert evento.tipo == "TEXTO_EXTRAIDO_RECIBIDO"
        assert evento.estado_anterior is None
        assert evento.fecha == PROCEDENCIA.fecha

    def test_recibir_resultado_ocr_usa_el_modelo_como_actor_del_evento(self):
        resultado = ResultadoOcr(modelo_id="ocr-ficticio-v0", contenido="x", calidad=0.5, fecha=PROCEDENCIA.fecha)

        _, evento = recibir_resultado_ocr(_texto_escaneo(), resultado)

        assert evento.actor == "ocr-ficticio-v0"

    def test_marcar_cuarentena_o_rechazo_conserva_el_estado_anterior_pendiente(self):
        _, evento = marcar_cuarentena_o_rechazo(_texto_pendiente(), CondicionDeExtraccion.CORRUPTO, actor="sistema-extraccion", fecha=PROCEDENCIA.fecha)

        assert evento.estado_anterior == "PENDIENTE_DE_EXTRACCION"
        assert evento.tipo == "VALIDACION_APLICADA"
