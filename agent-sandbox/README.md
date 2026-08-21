# Sandbox del loop agéntico

No es el empaquetado del producto (eso es `deploy/`). Esto es la infraestructura
para correr `./orquestador.sh loop` de forma aislada, tal como pide
`plan-ejecucion-agentica.md` §4 — sin volúmenes del host más allá del repo y de
los tres archivos de autenticación descritos abajo.

Corre como usuario `agent` (no root): Claude Code rechaza
`--dangerously-skip-permissions` si detecta root/sudo, y `CONTAINED=1` depende
de ese flag.

## Construir

```
docker build -t sgdea-agent-sandbox:latest -f agent-sandbox/Dockerfile agent-sandbox
```

## Autenticación

Dentro del contenedor no hay navegador, así que el login interactivo de
`claude`/`codex` no es viable. Se eligió reusar la sesión del host en vez de
API keys separadas (evita facturación aparte de la suscripción), montando
**solo** los tres archivos de credenciales, de solo lectura — no los
directorios completos `~/.claude` o `~/.codex`, que traen caché, historial de
sesiones y hasta un perfil de Chrome ajenos a la autenticación:

- `~/.claude/.credentials.json` → `/home/agent/.claude/.credentials.json`
- `~/.claude.json` → `/home/agent/.claude.json`
- `~/.codex/auth.json` → `/home/agent/.codex/auth.json`

Esto relaja el "sin credenciales reales" del plan a propósito: el contenedor
sí ve estos tres archivos (no el resto del host). Si `codex` pide además
`~/.codex/config.toml` para arrancar, hay que añadirlo a `run.sh` de la misma
forma (montaje puntual, no el directorio).

Verificado (2026-08-20): `codex login status` → "Logged in using ChatGPT";
`claude -p "..." --dangerously-skip-permissions` devuelve una respuesta real
(no simulada) usando las credenciales montadas.

## `.venv` y `.gradle` — no van en el bind mount

`run.sh` los monta como volúmenes nombrados (`sgdea-agent-venv`,
`sgdea-agent-gradle`), no como parte del bind mount del repo. Son artefactos
de plataforma (venv de Linux, caché de Gradle); compartirlos con el host los
corrompe — pasó una vez: `uv` no podía borrar un symlink que el contenedor
Linux había creado, estando ya en Windows. Si corres el sandbox con `docker
run` a mano en vez de `run.sh`, replica esos dos `-v`.

## Correr

```
./agent-sandbox/run.sh
```

Dentro del contenedor, en `/repo`:

```
codex login status   # confirma que autentica con las credenciales montadas
./orquestador.sh loop
```
