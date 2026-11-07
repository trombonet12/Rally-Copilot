#!/usr/bin/env bash
# Mide CPU y memoria del proceso de Rally Copilot durante una conducción simulada con la pantalla apagada (REN-2).
#
# Uso:   bash scripts/medir-bateria.sh <copiloto: on|off> [minutos=10] [traza=docs/rutas/ma-2141-sa-calobra.gpx] [kmh=60]
# Antes: emulador arrancado (AVD trombito-36), APK debug instalado, permisos concedidos y la app abierta una vez.
#        El micrófono del emulador usa el del PC (o silencio): la puerta de voz reacciona a lo que oiga.
#
# Qué hace: el ajuste «Copiloto» se cambia a mano en Ajustes antes de lanzar el script (el argumento on/off solo
# etiqueta la salida). Después: abre la app, espera a que pulses «Conducir» (modo libre), apaga la pantalla, reproduce la
# traza con gps-replay y, pasados los minutos, imprime:
#   - CPU del proceso (utime+stime de /proc/<pid>/stat, en ms) durante la ventana y su % de un núcleo,
#   - `dumpsys batterystats` del paquete (resumen) guardado en el archivo de salida,
#   - `dumpsys meminfo` (TOTAL PSS) con la conducción en marcha y 60 s después de terminarla (los modelos de voz deben
#     haberse liberado: ver REN-4, gracia de 15 s).
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash en Windows no debe reescribir rutas como /sdcard

COPILOTO="${1:?indica on u off (solo para etiquetar la salida)}"
MINUTOS="${2:-10}"
TRAZA="${3:-docs/rutas/ma-2141-sa-calobra.gpx}"
KMH="${4:-60}"
PAQUETE="${PAQUETE:-app.trombito.rallycopilot.dev}" # el APK debug lleva sufijo .dev
SALIDA="${SALIDA:-medicion-bateria-${COPILOTO}.txt}"

adb() { command adb "$@"; }

pid_app() { adb shell pidof "$PAQUETE" | tr -d '\r' | awk '{print $1}'; }

cpu_ms() { # utime+stime del proceso en ms (CLK_TCK = 100 en Android)
    adb shell "cat /proc/$1/stat" | tr -d '\r' | awk '{printf "%d", ($14 + $15) * 10}'
}

pss_kb() {
    adb shell dumpsys meminfo "$PAQUETE" | tr -d '\r' | awk '/TOTAL PSS:/ {print $3; exit} /^ +TOTAL / {print $2; exit}'
}

{
    echo "== Medición Copiloto=${COPILOTO} · ${MINUTOS} min · ${TRAZA} a ${KMH} km/h · $(date -Is)"
    adb shell dumpsys batterystats --reset > /dev/null
    adb shell am start -n "${PAQUETE}/app.trombito.rallycopilot.MainActivity" > /dev/null
    sleep 3
    echo ">> Pulsa «Conducir» en la app (modo libre) y vuelve aquí; empiezo en 20 s."
    sleep 20
    PID="$(pid_app)"
    echo "pid=${PID}"
    echo "PSS con la app abierta (KB): $(pss_kb)"
    CPU_ANTES="$(cpu_ms "$PID")"
    adb shell input keyevent KEYCODE_SLEEP
    node scripts/gps-replay.mjs "$TRAZA" --kmh "$KMH" &
    REPLAY=$!
    sleep $((MINUTOS * 60))
    kill "$REPLAY" 2> /dev/null || true
    CPU_DESPUES="$(cpu_ms "$PID")"
    echo "CPU del proceso en la ventana: $((CPU_DESPUES - CPU_ANTES)) ms (de $((MINUTOS * 60000)) ms)"
    awk -v c="$((CPU_DESPUES - CPU_ANTES))" -v t="$((MINUTOS * 60000))" 'BEGIN {printf "= %.2f %% de un núcleo\n", 100 * c / t}'
    echo "PSS durante la conducción (KB): $(pss_kb)"
    adb shell dumpsys batterystats "$PAQUETE" | tr -d '\r' | grep -iE "Estimated power use|Uid u0a|cpu|Foreground|Mic|audio" | head -20
    adb shell input keyevent KEYCODE_WAKEUP
    echo ">> Termina la conducción desde la notificación o la app; espero 60 s."
    sleep 60
    echo "PSS 60 s tras terminar (KB): $(pss_kb)"
} 2>&1 | tee "$SALIDA"
