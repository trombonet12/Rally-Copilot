#!/usr/bin/env bash
# Prueba cada bloqueo y cada permiso de los hooks de .claude/hooks/ con entradas sintéticas.
# Uso: bash scripts/test-hooks.sh   (sale con 1 si algún caso falla)
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
H="$ROOT/.claude/hooks"
PASS=0
FAIL=0

json_escape() { node -e 'process.stdout.write(JSON.stringify(process.argv[1]))' "$1"; }

# expect <hook> <exit esperado> <campo> <valor> [tool_name]
expect() {
  local hook="$1" want="$2" field="$3" value="$4" tool="${5:-Write}" input start ms got
  input="{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"$tool\",\"tool_input\":{\"$field\":$(json_escape "$value")}}"
  start=$(date +%s%N)
  printf '%s' "$input" | bash "$H/$hook" >/dev/null 2>&1
  got=$?
  ms=$((($(date +%s%N) - start) / 1000000))
  if [ "$got" -eq "$want" ] && [ "$ms" -lt 5000 ]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FALLO: $hook [$field=$value] esperado=$want obtenido=$got (${ms} ms)"
  fi
}

# protect-secrets.sh: bloquea (2) / permite (0)
for f in '.env' 'C:\Github\APPS\Rally Copilot\.env' 'app/.env.local' '.env.production' 'certs/server.pem' \
  'android/release.jks' 'app/upload.keystore' 'id.key' 'config/secrets/token.txt' 'C:\X\Secrets\a.json' 'cert.p12' 'a.pfx' \
  'local.properties' 'C:\Users\u\.rallycopilot\release-signing.properties' 'C:\Users\u\.rallycopilot\play.json' '/home/u/.ssh/id_rsa'; do
  expect protect-secrets.sh 2 file_path "$f"
done
for f in '.env.example' 'C:\Github\APPS\Rally Copilot\.env.example' 'src/environment.ts' 'docs/keys.md' 'monkey.kt' 'README.md' 'docs/MEMORIA.md'; do
  expect protect-secrets.sh 0 file_path "$f"
done
# Fallo cerrado: entrada inválida
printf 'no-json' | bash "$H/protect-secrets.sh" >/dev/null 2>&1
if [ $? -eq 2 ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); echo "FALLO: protect-secrets.sh no falla cerrado"; fi

# warn-generated.sh: nunca bloquea; avisa en generated/build/dist
for f in 'app/build/generated/x.kt' 'motor/build/classes/x' 'motor/src/main/kotlin/A.kt'; do
  expect warn-generated.sh 0 file_path "$f"
done
OUT="$(printf '{"tool_input":{"file_path":"app/build/a.kt"}}' | bash "$H/warn-generated.sh")"
if printf '%s' "$OUT" | grep -q systemMessage; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); echo "FALLO: warn-generated.sh no avisa"; fi

# block-secret-commands.sh: bloquea (2) / permite (0)
for c in 'cat .env' 'cat app/.env.local' 'head -5 .env.production' 'grep KEY .env' 'printenv' \
  'printenv PLAY_SERVICE_ACCOUNT_JSON' 'env' 'ls && env' 'echo $ANDROID_KEY_PASSWORD' 'echo "${RALLY_KEYSTORE_PASSWORD}"' \
  'Get-Content .env' 'gci env:' 'Write-Output $env:SOME_API_KEY' \
  'env | sort' 'set' 'export -p' 'declare -x' '$env:RALLY_KEY_PASSWORD' '[Environment]::GetEnvironmentVariable("X")' \
  'cp .env /tmp/x' 'cat local.properties' 'cut -d= -f2 local.properties' 'cat ~/.rallycopilot/release-signing.properties' \
  'Get-Content $HOME\.rallycopilot\release-signing.properties' 'type C:\Users\u\.rallycopilot\play.json' 'base64 release.jks' \
  "node -e 'console.log(process.env)'" 'python -c "import os;print(os.environ)"' \
  'git push --force' 'git push -f origin main' 'git push origin main --force-with-lease' 'git -C . push --force' \
  'git push origin --delete main' 'git push --mirror' 'git add -f .env' 'git add --force x'; do
  expect block-secret-commands.sh 2 command "$c" Bash
done
# El push normal no lo bloquea el hook: lo frena la regla «ask» de settings.json (pide permiso).
for c in 'cat .env.example' './gradlew testDebugUnitTest' 'git status' 'git push' 'git push origin main' 'env | grep -c PATH' 'echo hello' \
  'ls environment/' 'node scripts/check-claude-assets.mjs' 'echo $HOME' 'cat README.md' \
  'env | wc -l' 'set -euo pipefail' 'git add docs/a.md' 'git commit -m "x"' 'git log --oneline -3' 'cat .gitignore' \
  'node scripts/gps-replay.mjs docs/rutas/sa-calobra.gpx' 'adb emu geo fix 2.8 39.8' 'grep -rn app.trombito src'; do
  expect block-secret-commands.sh 0 command "$c" Bash
done

# post-edit-lint.sh: falla abierto (0) para archivos inexistentes, tipos sin linter y herramientas ausentes
for f in 'no/existe.ts' 'README.md' "$ROOT/CLAUDE.md"; do
  expect post-edit-lint.sh 0 file_path "$f"
done

# settings.json: el push pide permiso (ask) y no está permitido sin preguntar (allow)
if node -e '
  const s = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).permissions;
  const ok = s.ask.some((r) => r.startsWith("Bash(git push")) && !s.allow.some((r) => r.includes("git push"));
  process.exit(ok ? 0 : 1);
' "$ROOT/.claude/settings.json"; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); echo "FALLO: settings.json no pide permiso para git push"; fi

echo "test-hooks: $PASS OK, $FAIL fallos"
[ "$FAIL" -eq 0 ]
