from abc import ABC, abstractmethod


class VectorIndex(ABC):
    @abstractmethod
    def indexar(self, id: str, vector: list[float], metadatos: dict) -> None: ...

    @abstractmethod
    def buscar(self, vector: list[float], top_k: int) -> list[str]: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedVectorIndex(VectorIndex):
    def indexar(self, id: str, vector: list[float], metadatos: dict) -> None:
        raise NotImplementedError

    def buscar(self, vector: list[float], top_k: int) -> list[str]:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedVectorIndex(VectorIndex):
    def indexar(self, id: str, vector: list[float], metadatos: dict) -> None:
        raise NotImplementedError

    def buscar(self, vector: list[float], top_k: int) -> list[str]:
        raise NotImplementedError
