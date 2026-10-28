#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit): advierte (no bloquea) al modificar archivos generados.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILE="$(node "$DIR/hook-input.mjs" file_path 2>/dev/null)" || exit 0
P="$(printf '%s' "$FILE" | tr '\134' '/')"
case "/$P" in
  */generated/* | */build/* | */dist/*)
    printf '{"systemMessage":"Aviso: se está modificando un archivo generado (%s). Cambia la fuente y regenera."}\n' "${P##*/}"
    ;;
esac
exit 0
