#!/usr/bin/env python3
"""Cuenta tokens usados por Claude y Codex a partir de .loop/logs/.

.loop/ está en .gitignore (es efímero, local a cada máquina) — este script
regenera tokens/CONTEO-TOKENS.md a partir de lo que exista localmente. No
inventa ninguna cifra: si un log no trae datos de uso (intento fallido antes
de completar, rate limit, etc.) se cuenta como fila en cero y queda listado
en "Adjuntos sin datos de uso", nunca se omite en silencio.

Modo (desarrollador / validador) se deriva del rol real que jugó el agente en
esa invocación, no del nombre del agente:
  - Claude Code: siempre "desarrollador" (implementador — CLAUDE_STEP_PROMPT,
    PREFLIGHT_PROMPT). Nunca revisa en este proyecto.
  - Codex: "validador" por defecto (CODEX_REVIEW_PROMPT / revisiones
    interactivas ad-hoc "codex-review-*"), EXCEPTO cuando el tag de la
    invocación contiene "-lead" (CODEX_LEAD_PROMPT: Claude en rate limit
    sostenido, Codex implementa) -> ahí es "desarrollador". El tag
    "-selfreview" (CODEX_SELFREVIEW_PROMPT, Codex revisando su propio commit
    de modo "-lead") sigue siendo "validador" — el rol es revisar, aunque sea
    autorrevisión.

Semántica de "total_tokens" (documentada aquí para que la cifra no se
malinterprete, no es una convención inventada — se deriva de cómo cada CLI
reporta "usage"):
  - Claude (`claude --output-format json`): usage.input_tokens,
    usage.cache_creation_input_tokens y usage.cache_read_input_tokens son
    TRES buckets ADITIVOS de entrada (la API de Anthropic los reporta por
    separado; ninguno es subconjunto de otro). total = input + cache_creation
    + cache_read + output.
  - Codex (`codex exec --json`): cada evento "turn.completed" trae
    usage.input_tokens (el contexto completo de esa vuelta) y
    usage.cached_input_tokens como SUBCONJUNTO informativo de input_tokens
    (cuánto de ese input vino de caché), no un bucket aparte. total = input
    + output (cached_input_tokens se reporta aparte solo como referencia).

Costo en USD: Claude expone `total_cost_usd` directo. Codex (`exec --json`)
no expone ningún campo de costo — se deja en blanco, no se inventa una tarifa.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LOG_DIR = REPO_ROOT / ".loop" / "logs"
OUT_FILE = Path(__file__).resolve().parent / "CONTEO-TOKENS.md"

FILENAME_RE = re.compile(r"^(claude|codex)-(.+)\.(json|log)$")


@dataclass
class Fila:
    archivo: str
    agente: str
    modo: str
    tag: str
    input_tokens: int = 0
    cache_creation_tokens: int = 0
    cache_read_tokens: int = 0
    output_tokens: int = 0
    costo_usd: float | None = None
    sin_datos: bool = False

    @property
    def total_tokens(self) -> int:
        return (
            self.input_tokens
            + self.cache_creation_tokens
            + self.cache_read_tokens
            + self.output_tokens
        )


def clasificar_modo(agente: str, tag: str) -> str:
    if agente == "claude":
        return "desarrollador"
    # agente == "codex"
    partes = tag.split("-")
    if "lead" in partes:
        return "desarrollador"
    return "validador"


def parsear_claude(path: Path, tag: str) -> Fila:
    agente = "claude"
    modo = clasificar_modo(agente, tag)
    try:
        data = json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return Fila(path.name, agente, modo, tag, sin_datos=True)
    usage = data.get("usage") or {}
    if not usage:
        return Fila(path.name, agente, modo, tag, sin_datos=True)
    return Fila(
        archivo=path.name,
        agente=agente,
        modo=modo,
        tag=tag,
        input_tokens=int(usage.get("input_tokens") or 0),
        cache_creation_tokens=int(usage.get("cache_creation_input_tokens") or 0),
        cache_read_tokens=int(usage.get("cache_read_input_tokens") or 0),
        output_tokens=int(usage.get("output_tokens") or 0),
        costo_usd=data.get("total_cost_usd"),
    )


def parsear_codex(path: Path, tag: str) -> Fila:
    agente = "codex"
    modo = clasificar_modo(agente, tag)
    input_tok = cached_tok = output_tok = 0
    encontrado = False
    try:
        texto = path.read_text(encoding="utf-8", errors="replace")
    except UnicodeDecodeError:
        return Fila(path.name, agente, modo, tag, sin_datos=True)
    for linea in texto.splitlines():
        linea = linea.strip()
        if not linea or '"type":"turn.completed"' not in linea:
            continue
        try:
            evento = json.loads(linea)
        except json.JSONDecodeError:
            continue
        usage = evento.get("usage") or {}
        if not usage:
            continue
        encontrado = True
        input_tok += int(usage.get("input_tokens") or 0)
        cached_tok += int(usage.get("cached_input_tokens") or 0)
        output_tok += int(usage.get("output_tokens") or 0)
    if not encontrado:
        return Fila(path.name, agente, modo, tag, sin_datos=True)
    fila = Fila(
        archivo=path.name,
        agente=agente,
        modo=modo,
        tag=tag,
        input_tokens=input_tok,
        output_tokens=output_tok,
    )
    fila._cached_subset = cached_tok  # type: ignore[attr-defined]
    return fila


def descubrir_filas() -> list[Fila]:
    filas: list[Fila] = []
    if not LOG_DIR.exists():
        return filas
    for path in sorted(LOG_DIR.glob("*")):
        if path.name.startswith("tests-"):
            continue
        m = FILENAME_RE.match(path.name)
        if not m:
            continue
        agente, tag, _ext = m.groups()
        if agente == "claude":
            filas.append(parsear_claude(path, tag))
        elif agente == "codex":
            filas.append(parsear_codex(path, tag))
    return filas


def agrupar(filas: list[Fila]) -> dict[tuple[str, str], dict]:
    grupos: dict[tuple[str, str], dict] = {}
    for f in filas:
        clave = (f.agente, f.modo)
        g = grupos.setdefault(
            clave,
            {
                "invocaciones": 0,
                "sin_datos": 0,
                "input": 0,
                "cache_creation": 0,
                "cache_read": 0,
                "output": 0,
                "total": 0,
                "costo_usd": 0.0,
                "tiene_costo": False,
            },
        )
        g["invocaciones"] += 1
        if f.sin_datos:
            g["sin_datos"] += 1
            continue
        g["input"] += f.input_tokens
        g["cache_creation"] += f.cache_creation_tokens
        g["cache_read"] += f.cache_read_tokens
        g["output"] += f.output_tokens
        g["total"] += f.total_tokens
        if f.costo_usd is not None:
            g["costo_usd"] += f.costo_usd
            g["tiene_costo"] = True
    return grupos


def fmt_num(n: int) -> str:
    return f"{n:,}".replace(",", ".")


def generar_markdown(filas: list[Fila]) -> str:
    grupos = agrupar(filas)
    orden = [
        ("claude", "desarrollador"),
        ("codex", "desarrollador"),
        ("codex", "validador"),
    ]
    total_general = sum(g["total"] for g in grupos.values())

    L: list[str] = []
    L.append("# Conteo de tokens — Claude vs Codex, desarrollador vs validador")
    L.append("")
    L.append(
        "Generado por `tokens/contar_tokens.py` a partir de `.loop/logs/` "
        "(gitignored, local a esta máquina — este archivo es un snapshot, "
        "no un histórico compartido entre máquinas). Re-ejecutar el script "
        "tras cada corrida de `./orquestador.sh loop` (o revisión manual de "
        "Codex) para actualizarlo: `uv run python tokens/contar_tokens.py`."
    )
    L.append("")
    L.append(
        "**Modo** = rol jugado en esa invocación, no el nombre del agente: "
        "Claude Code siempre implementa (\"desarrollador\"); Codex revisa "
        "(\"validador\") salvo cuando el tag de la invocación trae `-lead` "
        "(Codex implementó porque Claude estaba en rate limit sostenido, ver "
        "`CODEX_LEAD_PROMPT` en `orquestador.sh`) — ahí también cuenta como "
        "\"desarrollador\". Ver el docstring del script para la definición "
        "exacta de `total_tokens` en cada CLI (los buckets NO son "
        "comparables 1:1 entre Claude y Codex — ver nota al pie)."
    )
    L.append("")
    L.append(f"**Total general (todas las invocaciones registradas): {fmt_num(total_general)} tokens.**")
    L.append("")
    L.append("## Resumen por agente y modo")
    L.append("")
    L.append(
        "| Agente | Modo | Invocaciones | Sin datos de uso | Input | "
        "Cache creation | Cache read | Output | Total | Costo USD |"
    )
    L.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for agente, modo in orden:
        g = grupos.get((agente, modo))
        if g is None:
            L.append(
                f"| {agente} | {modo} | 0 | 0 | 0 | 0 | 0 | 0 | 0 | — |"
            )
            continue
        costo = f"${g['costo_usd']:.4f}" if g["tiene_costo"] else "—"
        cache_creation = fmt_num(g["cache_creation"]) if agente == "claude" else "n/a"
        cache_read = fmt_num(g["cache_read"]) if agente == "claude" else "n/a"
        L.append(
            f"| {agente} | {modo} | {g['invocaciones']} | {g['sin_datos']} | "
            f"{fmt_num(g['input'])} | {cache_creation} | {cache_read} | "
            f"{fmt_num(g['output'])} | {fmt_num(g['total'])} | {costo} |"
        )
    L.append("")
    L.append(
        "`n/a` en Codex: `cached_input_tokens` es un subconjunto informativo "
        "de `input_tokens` (no un bucket aditivo), así que no aplica una "
        "columna de cache separada aditiva como en Claude — ver docstring."
    )
    L.append("")

    L.append("## Detalle por invocación")
    L.append("")
    L.append("| Archivo | Agente | Modo | Tag | Input | Output | Total | Costo USD |")
    L.append("|---|---|---|---|---:|---:|---:|---:|")
    for f in filas:
        if f.sin_datos:
            L.append(f"| {f.archivo} | {f.agente} | {f.modo} | {f.tag} | — | — | — | — |")
            continue
        costo = f"${f.costo_usd:.4f}" if f.costo_usd is not None else "—"
        L.append(
            f"| {f.archivo} | {f.agente} | {f.modo} | {f.tag} | "
            f"{fmt_num(f.input_tokens + f.cache_creation_tokens + f.cache_read_tokens)} | "
            f"{fmt_num(f.output_tokens)} | {fmt_num(f.total_tokens)} | {costo} |"
        )
    L.append("")

    sin_datos = [f.archivo for f in filas if f.sin_datos]
    if sin_datos:
        L.append("## Adjuntos sin datos de uso (intentos fallidos, rate limit, etc.)")
        L.append("")
        for nombre in sin_datos:
            L.append(f"- {nombre}")
        L.append("")

    return "\n".join(L) + "\n"


def main() -> None:
    filas = descubrir_filas()
    OUT_FILE.write_text(generar_markdown(filas), encoding="utf-8")
    print(f"Escrito {OUT_FILE} con {len(filas)} invocaciones analizadas.")


if __name__ == "__main__":
    main()
