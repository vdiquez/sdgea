from abc import ABC, abstractmethod


class LexicalIndex(ABC):
    @abstractmethod
    def indexar(self, id: str, texto: str, metadatos: dict) -> None: ...

    @abstractmethod
    def buscar(self, consulta: str, top_k: int) -> list[str]: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedLexicalIndex(LexicalIndex):
    def indexar(self, id: str, texto: str, metadatos: dict) -> None:
        raise NotImplementedError

    def buscar(self, consulta: str, top_k: int) -> list[str]:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedLexicalIndex(LexicalIndex):
    def indexar(self, id: str, texto: str, metadatos: dict) -> None:
        raise NotImplementedError

    def buscar(self, consulta: str, top_k: int) -> list[str]:
        raise NotImplementedError
