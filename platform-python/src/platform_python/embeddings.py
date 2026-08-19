from abc import ABC, abstractmethod


class Embeddings(ABC):
    @abstractmethod
    def calcular(self, textos: list[str]) -> list[list[float]]: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedEmbeddings(Embeddings):
    def calcular(self, textos: list[str]) -> list[list[float]]:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedEmbeddings(Embeddings):
    def calcular(self, textos: list[str]) -> list[list[float]]:
        raise NotImplementedError
