# Componente FICTICIO del arnés (constitución, disciplina de alcance): valida que
# el arnés corre de punta a punta. Nunca un modelo real.
class ComponenteFicticio:
    def predecir(self, entrada: str) -> str:
        return entrada.split()[0].lower() if entrada else ""
