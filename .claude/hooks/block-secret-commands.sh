#!/usr/bin/env bash
# PreToolUse (Bash|PowerShell): bloquea comandos que imprimirían o filtrarían secretos, y operaciones
# remotas peligrosas. Defensa best-effort; la protección principal son las reglas deny/ask de
# .claude/settings.json. Falla CERRADO.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! CMD="$(node "$DIR/hook-input.mjs" command)"; then
  echo "BLOQUEADO: no se pudo leer la entrada del hook (fallo cerrado)." >&2
  exit 2
fi
block() {
  echo "BLOQUEADO: el comando podría exponer secretos o tocar el remoto de forma peligrosa ($1). Consulta nombres, nunca valores; usa un script que no imprima." >&2
  exit 2
}
has() { printf '%s' "$C" | grep -qiE -- "$1"; }
# .env.example es público: se elimina antes de comprobar lecturas de .env
C="$(printf '%s' "$CMD" | sed -E 's/\.env\.example//g')"
READERS='(cat|less|more|head|tail|type|bat|nl|strings|xxd|od|sed|awk|grep|rg|cut|base64|get-content|gc|select-string|sls)'
SENSITIVE='(\.env([^[:alnum:]]|$)|local\.properties|[/\\~]\.rallycopilot|\.p12|\.pfx|\.jks|\.keystore|\.pem|id_rsa|id_ed25519|\.ssh[/\\])'
B='(^|[^[:alnum:]_-])'
has "$B$READERS[[:space:]][^|;&]*$SENSITIVE" && block "lectura de archivo sensible"
has "${B}(cp|copy|copy-item|scp)[[:space:]][^|;&]*$SENSITIVE" && block "copia de archivo sensible"
# Variables de entorno
has "${B}printenv([^[:alnum:]_-]|$)" && block "printenv"
if has '(^|[;&|][[:space:]]*)env[[:space:]]*($|[;&|])'; then
  has '(^|[;&|][[:space:]]*)env[[:space:]]*\|[[:space:]]*(grep[[:space:]]+-c|wc)([[:space:]]|$)' || block "env sin filtro"
fi
has '(^|[;&|][[:space:]]*)set[[:space:]]*($|[;&|])' && block "set sin argumentos"
has "${B}(export[[:space:]]+-p|declare[[:space:]]+-[px]|compgen[[:space:]]+-v)" && block "listado de variables"
has '(get-childitem|gci|dir|ls)[[:space:]]+env:' && block "listado de variables de entorno"
has '(echo|printf|write-output|write-host)[^|;&]*\$(\{)?(env:)?[[:alnum:]_]*(key|secret|token|password|passwd)' && block "eco de variable sensible"
has '\$env:[[:alnum:]_]*(key|secret|token|password|passwd)' && block "lectura de variable sensible en PowerShell"
has 'getenvironmentvariable' && block "lectura de variables con .NET"
has "${B}(node|deno|bun|python3?|py)[[:space:]][^|;&]*(-e|-p|--eval|--print|-c)[[:space:]].*(process\.env|os\.environ|deno\.env)" && block "variables de entorno en código inline"
# Git: el push normal lo frena la regla «ask» de settings.json (pide permiso al usuario).
# Aquí solo se bloquea lo destructivo: push forzado, borrado de ramas remotas y add forzado.
has "${B}git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+push[^|;&]*[[:space:]](-f|--force|--force-with-lease|--mirror|--delete)([[:space:]=]|$)" && block "git push forzado"
has "${B}git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+add([[:space:]][^|;&]*)?[[:space:]](-f|--force)([[:space:]]|$)" && block "git add forzado"
exit 0
