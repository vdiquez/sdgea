import json
from dataclasses import asdict
from pathlib import Path

from componente_ficticio import ComponenteFicticio
from harness import cargar_set_patron, correr_arnes


def main() -> None:
    fixture = Path(__file__).parent / "fixtures" / "set_patron_ficticio.json"
    set_patron = cargar_set_patron(fixture)
    boleta = correr_arnes(set_patron, ComponenteFicticio())
    print(json.dumps(asdict(boleta), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
