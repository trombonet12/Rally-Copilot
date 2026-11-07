#!/usr/bin/env bash
# Humo en el emulador (QA-3): instala el APK release (minificado por R8), arranca la app y falla si el proceso muere o
# logcat trae un FATAL/ANR. Lo usa el job manual `humo-emulador` de .github/workflows/android.yml dentro de
# reactivecircus/android-emulator-runner, con el emulador ya arrancado y `adb` en el PATH.
# Uso: bash scripts/humo-emulador.sh <app-release.apk> [segundos de espera, 45] [carpeta de salida, humo-emulador]
set -euo pipefail

apk="${1:?Uso: humo-emulador.sh <apk> [segundos] [salida]}"
espera="${2:-45}"
salida="${3:-humo-emulador}"
paquete="app.trombito.rallycopilot"
mkdir -p "$salida"

adb install -r -g "$apk"
# -g concede los permisos de ejecución declarados; con el emulador de API < 33 el de notificaciones no existe y no pasa nada.
adb logcat -c
adb shell monkey -p "$paquete" -c android.intent.category.LAUNCHER 1 > "$salida/monkey.txt" 2>&1
sleep "$espera"

adb logcat -d -v threadtime > "$salida/logcat.txt"
estado=0
if ! adb shell pidof "$paquete" > "$salida/pid.txt" 2>&1 || [ ! -s "$salida/pid.txt" ]; then
  echo "humo-emulador: la app no está en marcha tras ${espera} s (proceso muerto)"
  estado=1
fi
if grep -E "FATAL EXCEPTION|Fatal signal|ANR in $paquete" "$salida/logcat.txt" > "$salida/fatales.txt"; then
  echo "humo-emulador: logcat trae un error fatal:"
  head -n 20 "$salida/fatales.txt"
  estado=1
fi
if [ "$estado" -eq 0 ]; then
  echo "humo-emulador: bien (la app sigue viva y logcat sin FATAL tras ${espera} s)"
fi
exit "$estado"
