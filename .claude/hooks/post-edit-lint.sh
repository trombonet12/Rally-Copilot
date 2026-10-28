#!/usr/bin/env bash
# PostToolUse (Edit|Write|MultiEdit): lint rápido del archivo editado según extensión.
# Falla ABIERTO: si la herramienta no está instalada o excede el tiempo, avisa y sale con 0.
# Si el lint encuentra errores, sale con 2 (no bloquea; el mensaje llega a Claude).
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILE="$(node "$DIR/hook-input.mjs" file_path 2>/dev/null)" || exit 0
[ -n "$FILE" ] && [ -f "$FILE" ] || exit 0
P="$(printf '%s' "$FILE" | tr '\134' '/')"
warn() { printf '{"systemMessage":"%s"}\n' "$1"; exit 0; }
report() { # $1 = herramienta, $2 = código, $3 = salida
  [ "$2" -eq 0 ] && exit 0
  [ "$2" -eq 124 ] && warn "$1 excedió el tiempo del hook; se valida con el gate de Gradle."
  printf '%s encontró problemas en %s:\n%s\n' "$1" "$P" "$3" >&2
  exit 2
}
case "$P" in
  *.kt | *.kts)
    command -v ktlint >/dev/null 2>&1 || exit 0
    OUT="$(timeout 4 ktlint --relative "$FILE" 2>&1)"; report ktlint $? "$OUT"
    ;;
  *.json)
    OUT="$(timeout 4 node -e 'JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"))' "$FILE" 2>&1)"; report "JSON" $? "$OUT"
    ;;
esac
exit 0
