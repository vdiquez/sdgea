OK: commit `6fd0497` conforme.

Alcance revisado: `orquestador.sh` y `test-run-claude.sh`. No modifica
`specs/`, por lo que no aplica el control adicional de referencias normativas
ni umbrales.

- P-01: no interviene en documentos, sugerencias ni materialización de estado.
- P-03: no añade ni consume capacidades externas del producto; el doble de
  `claude` es local, temporal y exclusivo de la prueba del orquestador.
- P-08: no introduce transiciones de documento o expediente.
- Honestidad: `./test-run-claude.sh` pasó. Sustituye el binario por un doble
  ejecutable en `PATH` y ejerce `run_claude()` con las mismas señales que
  reconoce `is_rate_limited()`: éxito (rc=0, 1 llamada), rate-limit sostenido
  (rc=2 tras exactamente 2 llamadas) y error genérico sin esa señal (rc=1 tras
  3 llamadas). No está configurado para pasar por mera inspección: registra y
  comprueba las invocaciones reales; el backoff queda reducido a 1 s solamente
  para que la prueba sea reproducible. La integración `cmd_loop` ya consume
  explícitamente rc=2 para invocar `run_codex`; su contrato de retorno queda
  cubierto por esta prueba.

La exclusión de `test.sh` está documentada junto a `run_claude()` y es razonable:
esta prueba de herramienta duerme los reintentos reales del camino genérico.
