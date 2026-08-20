# Sandbox del loop agéntico (plan-ejecucion-agentica.md §4 "Operación").
# NO es el empaquetado del producto — eso vive en deploy/. Esta imagen solo
# ejecuta orquestador.sh de forma aislada: sin volúmenes del host, sin
# credenciales reales horneadas, mismo Ubuntu que asume el plan.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        git \
        curl \
        ca-certificates \
        nodejs \
        npm \
    && rm -rf /var/lib/apt/lists/*

# Claude Code rechaza --dangerously-skip-permissions corriendo como root/sudo
# (protección propia), y CONTAINED=1 depende de ese flag — el usuario NO puede
# ser root.
RUN useradd --create-home --shell /bin/bash agent
RUN npm install -g @anthropic-ai/claude-code@2.1.237 @openai/codex@0.148.0

USER agent
WORKDIR /home/agent

# uv gestiona su propio Python 3.12 — no depende del intérprete del sistema.
# Se instala como 'agent', no como root, para que quede en su HOME.
ENV PATH="/home/agent/.local/bin:${PATH}"
RUN curl -LsSf https://astral.sh/uv/install.sh | sh \
    && uv python install 3.12

WORKDIR /repo
ENTRYPOINT ["/bin/bash"]
