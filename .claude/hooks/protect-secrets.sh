#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit|NotebookEdit): bloquea la escritura en rutas de secretos.
# Falla CERRADO: si no puede leer la entrada, bloquea (exit 2). Permite .env.example.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! FILE="$(node "$DIR/hook-input.mjs" file_path)"; then
  echo "BLOQUEADO: no se pudo leer la entrada del hook (fallo cerrado)." >&2
  exit 2
fi
[ -z "$FILE" ] && exit 0
P="$(printf '%s' "$FILE" | tr '\134' '/' | tr '[:upper:]' '[:lower:]')"
BASE="${P##*/}"
REASON=""
case "$BASE" in
  .env.example) exit 0 ;;
  .env | .env.*) REASON="archivo .env" ;;
  *.pem | *.key | *.jks | *.keystore | *.p12 | *.pfx) REASON="clave o keystore" ;;
  local.properties) REASON="local.properties (credenciales locales)" ;;
esac
case "/$P" in
  */secrets/*) REASON="${REASON:-directorio secrets}" ;;
  */.rallycopilot/* | */.ssh/*) REASON="${REASON:-directorio de credenciales}" ;;
esac
if [ -n "$REASON" ]; then
  echo "BLOQUEADO: escritura en ruta protegida ($REASON): $FILE. Usa .env.example o GitHub secrets." >&2
  exit 2
fi
exit 0
