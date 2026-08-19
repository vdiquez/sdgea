from abc import ABC, abstractmethod


class LLMInference(ABC):
    @abstractmethod
    def completar(self, prompt: str) -> str: ...


# Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedLLMInference(LLMInference):
    def completar(self, prompt: str) -> str:
        raise NotImplementedError


# Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedLLMInference(LLMInference):
    def completar(self, prompt: str) -> str:
        raise NotImplementedError
