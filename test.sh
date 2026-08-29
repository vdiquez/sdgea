#!/usr/bin/env bash
# Árbitro objetivo del loop agéntico (orquestador.sh): un solo binario para que
# --allowedTools lo autorice completo en modo no-contenido (ver TEST_BIN).
set -euo pipefail
bash test-run-id.sh
./gradlew test --no-daemon
uv run --directory eval-harness pytest
uv run --directory contexts/normalizacion pytest
uv run --directory contexts/extraccion pytest
uv run --directory contexts/clasificacion pytest
