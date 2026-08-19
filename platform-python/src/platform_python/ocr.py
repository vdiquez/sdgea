from abc import ABC, abstractmethod


class OCR(ABC):
    @abstractmethod
    def extraer_texto(self, artefacto: bytes) -> str: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedOCR(OCR):
    def extraer_texto(self, artefacto: bytes) -> str:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedOCR(OCR):
    def extraer_texto(self, artefacto: bytes) -> str:
        raise NotImplementedError
