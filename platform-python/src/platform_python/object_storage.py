from abc import ABC, abstractmethod


# P-03: toda implementación self-hosted debe operar sin conectividad saliente (P-10).
class ObjectStorage(ABC):
    @abstractmethod
    def put(self, key: str, content: bytes) -> None: ...

    @abstractmethod
    def get(self, key: str) -> bytes: ...

    @abstractmethod
    def exists(self, key: str) -> bool: ...

    @abstractmethod
    def delete(self, key: str) -> None: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedObjectStorage(ObjectStorage):
    def put(self, key: str, content: bytes) -> None:
        raise NotImplementedError

    def get(self, key: str) -> bytes:
        raise NotImplementedError

    def exists(self, key: str) -> bool:
        raise NotImplementedError

    def delete(self, key: str) -> None:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedObjectStorage(ObjectStorage):
    def put(self, key: str, content: bytes) -> None:
        raise NotImplementedError

    def get(self, key: str) -> bytes:
        raise NotImplementedError

    def exists(self, key: str) -> bool:
        raise NotImplementedError

    def delete(self, key: str) -> None:
        raise NotImplementedError
