#!/usr/bin/env bash
# Graba en el emulador los clips (con audio) del vídeo promocional de Rally Copilot, los clips de declaración de los
# servicios en primer plano de Play y las capturas crudas de la ficha de Play.
#
# Uso:   bash scripts/video/grabar-clips.sh [fase ...]      (sin argumentos = todos)
#   fases: preparar | clips | fgs | capturas | todos, o un paso suelto: c1 | tramo (c3, c2 y c4) | c5 |
#          fgs-location | fgs-microphone | fgs-datasync
#   preparar  deja el emulador y los datos en un estado conocido y graba la pasada de referencia de Sa Calobra
#             (sin ella no hay coche fantasma ni delta). Borra los viajes, tramos y pasadas de la app en el emulador.
#   clips     build/promo/clips/c1-notas.mp4 c2-tramo.mp4 c3-fantasma.mp4 c4-resumen.mp4 c5-temporada.mp4 (10-11 s cada uno)
#             y, de paso, build/ficha/raw/01-notas.png 02-tramo.png 03-fantasma.png 04-resumen.png. Si ya se grabaron
#             clips encima de la referencia, vuelve a ejecutar `preparar` antes (el delta sale de comparar con ella).
#   fgs       build/promo/fgs/fgs-location.mp4 fgs-microphone.mp4 fgs-datasync.mp4 (15-20 s).
#   capturas  build/ficha/raw/05-tablero.png 06-carreteras.png 07-offline.png 08-hoja.png.
# Si un paso falla, sigue con el siguiente y al final lista los fallidos (código de salida 1).
#
# Requisitos: Git Bash; adb y emulator del SDK en $LOCALAPPDATA/Android/Sdk (o ADB=/ruta/adb); scrcpy >= 3 (vídeo y
# audio; se corta con --time-limit para que el MP4 quede cerrado); node >= 22; ffprobe (opcional, validación final);
# el APK debug ya compilado en app/build/outputs/apk/debug/app-debug.apk (no se ejecuta Gradle). Se arranca el AVD
# trombito-36 si no hay ningún dispositivo.
# Fija 1080x1920/420 dpi, tema oscuro, font_scale 1.0, barra de estado en modo demo (12:00, batería llena, sin
# notificaciones), pantalla siempre encendida y volumen multimedia al máximo; el trap de salida lo restaura todo.
# Ajustes opcionales por entorno: SKIP_INSTALAR=1, REF_KMH (48), VIDEO_KMH (60), REF_SEGUNDOS (125), C1_DESDE (8km).

set -u
export MSYS_NO_PATHCONV=1
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb}"
EMULADOR="${EMULADOR:-$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe}"
SCRCPY="${SCRCPY:-$LOCALAPPDATA/Microsoft/WinGet/Packages/Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe/scrcpy-win64-v5.0.1/scrcpy.exe}"
PKG="${PKG:-app.trombito.rallycopilot.dev}"
APK="${APK:-$RAIZ/app/build/outputs/apk/debug/app-debug.apk}"
RUTA_GPX="${RUTA_GPX:-$RAIZ/docs/rutas/ma-2141-sa-calobra.gpx}"
REF_KMH="${REF_KMH:-48}"          # pasada de referencia: más lenta que el vídeo, para que el delta salga en verde
VIDEO_KMH="${VIDEO_KMH:-60}"
REF_SEGUNDOS="${REF_SEGUNDOS:-125}" # a 48 km/h son unos 1,7 km (el tramo de referencia)
C1_DESDE="${C1_DESDE:-8km}"       # c1 (notas sin tramo conocido) va por otra parte de la misma carretera
CLIPS="$RAIZ/build/promo/clips"
FGS="$RAIZ/build/promo/fgs"
RAW="$RAIZ/build/ficha/raw"
TMP="$RAIZ/build/promo/tmp"
DUMP="$TMP/ui.xml"
LOG_REC="$TMP/scrcpy.log"
LOG_REPLAY="$TMP/replay.log"
SEG_CLIP=11
REC_PID=""
REPLAY_PID=""
FALLOS=()

a() { "$ADB" "$@"; }
sh_() { a shell "$@"; }
pausa() { sleep "${1:-1.5}"; }
ruta_win() { command -v cygpath >/dev/null && cygpath -w "$1" || echo "$1"; }   # adb/scrcpy no entienden /c/...
log() { echo "[grabar-clips] $*" >&2; }

# --- UI por uiautomator (por texto o content-desc; nunca coordenadas fijas) ------------------------------------------
volcar() {
  local i
  for i in 1 2 3; do
    sh_ uiautomator dump /sdcard/rc-ui.xml 2>&1 | grep -q "dumped to" && break
    sleep 0.7
  done
  a exec-out cat /sdcard/rc-ui.xml | tr -d '\r' | sed 's/<node /\n<node /g' >"$DUMP"
}

# "=Texto" = exacto; "~regex" = expresión regular; "Texto" = subcadena; "cd:Texto" = solo content-desc exacto
regex_nodo() {
  case "$1" in
    cd:*) echo "content-desc=\"${1#cd:}\"" ;;
    =*) echo "(text|content-desc)=\"${1#=}\"" ;;
    '~'*) echo "(text|content-desc)=\"[^\"]*(${1#\~})[^\"]*\"" ;;
    *) echo "(text|content-desc)=\"[^\"]*$1[^\"]*\"" ;;
  esac
}

# centro "x y" del n-ésimo nodo que coincide con $1 (ver regex_nodo)
centro() {
  local b
  b=$(grep -E "$(regex_nodo "$1")" "$DUMP" | sed -n "${2:-1}p" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 1
  set -- $b
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}

existe() { volcar; grep -qE "$(regex_nodo "$1")" "$DUMP"; }

# toca el nodo que contiene el texto; falla si no existe
tocar() {
  local xy; volcar
  xy=$(centro "$1" "${2:-1}") || { log "ERROR: no encuentro «$1» en pantalla"; return 1; }
  sh_ input tap $xy; pausa "${3:-1.5}"
}

# espera (hasta $2 s) a que aparezca un nodo; devuelve 1 si no sale
esperar() {
  local limite=$(( SECONDS + ${2:-20} ))
  while [ "$SECONDS" -lt "$limite" ]; do existe "$1" && return 0; sleep 0.5; done
  log "ERROR: «$1» no aparece"; return 1
}

# descarta las pistas en contexto («Entendido») por si saliera alguna
sin_pistas() { local i; for i in 1 2 3; do existe "=Entendido" && tocar "=Entendido" 1 0.8 || return 0; done; }

deslizar() {   # deslizar bajar|subir [ms] [pausa]
  case "$1" in
    bajar) sh_ input swipe 540 1500 540 600 "${2:-900}" ;;
    subir) sh_ input swipe 540 600 540 1500 "${2:-900}" ;;
  esac
  pausa "${3:-0.8}"
}

# vuelve a la pantalla de inicio (pulsa Atrás hasta ver CONDUCIR)
inicio() {
  local i
  for i in 1 2 3 4 5 6; do
    existe "=CONDUCIR" && return 0
    sh_ input keyevent KEYCODE_BACK; pausa 1.2
  done
  existe "=CONDUCIR"
}

# arranca la app desde cero y la deja en inicio
abrir_app() {
  sh_ am force-stop "$PKG"; pausa 1
  sh_ monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; pausa 5
  inicio
}

# --- GPS ----------------------------------------------------------------------------------------------------------------
# punto "lon lat" de la traza a los $1 de distancia (0 = inicio)
punto_ruta() {
  (cd "$RAIZ" && node --input-type=module -e "
    import { parsear, indiceDesde } from './scripts/gps-replay.mjs';
    import { readFileSync } from 'node:fs';
    const p = parsear(readFileSync(process.argv[1], 'utf8'));
    const q = p[indiceDesde(p, process.argv[2])];
    console.log(q.lon.toFixed(7) + ' ' + q.lat.toFixed(7));" "$(ruta_win "$RUTA_GPX")" "${1:-0}") | tr -d '\r'
}
colocar_coche() { local lonlat; lonlat=$(punto_ruta "${1:-0}"); a emu geo fix $lonlat 100 8 0 >/dev/null; pausa 2; }

# replay en segundo plano: arrancar_replay <km/h> [desde]
arrancar_replay() {
  local args=("$(ruta_win "$RUTA_GPX")" --kmh "$1")
  [ -n "${2:-}" ] && args+=(--desde "$2")
  (cd "$RAIZ" && ADB="$ADB" exec node scripts/gps-replay.mjs "${args[@]}") >"$LOG_REPLAY" 2>&1 &
  REPLAY_PID=$!
}
parar_replay() {
  [ -n "$REPLAY_PID" ] && kill "$REPLAY_PID" 2>/dev/null
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | Where-Object { \$_.CommandLine -match 'gps-replay' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1
  REPLAY_PID=""
}

# --- grabación (scrcpy: vídeo + audio del dispositivo; --time-limit cierra bien el MP4) -----------------------------------
# rec_iniciar <fichero.mp4> <segundos>: vuelve cuando la grabación ya ha empezado
rec_iniciar() {
  mkdir -p "$(dirname "$1")" "$TMP"; rm -f "$1" "$LOG_REC"
  "$SCRCPY" --record="$(ruta_win "$1")" --no-playback --no-window --no-control --audio-source=output \
    --time-limit="$2" >"$LOG_REC" 2>&1 &
  REC_PID=$!
  local limite=$(( SECONDS + 20 ))
  until grep -q "Recording started" "$LOG_REC" 2>/dev/null; do
    [ "$SECONDS" -ge "$limite" ] && { log "ERROR: scrcpy no empieza a grabar"; cat "$LOG_REC" >&2; return 1; }
    sleep 0.2
  done
}
rec_esperar() { wait "$REC_PID" 2>/dev/null; REC_PID=""; }

# captura PNG cruda de la ficha: captura <nombre>
captura() { mkdir -p "$RAW"; a exec-out screencap -p >"$RAW/$1.png"; log "-> $RAW/$1.png"; }

# --- dispositivo ---------------------------------------------------------------------------------------------------------------
demo() {
  sh_ settings put global sysui_demo_allowed 1
  local c; for c in "-e command enter" "-e command clock -e hhmm 1200" "-e command battery -e level 100 -e plugged false" \
    "-e command network -e wifi show -e level 4 -e fully true" "-e command network -e mobile hide" "-e command notifications -e visible false"; do
    sh_ am broadcast -a com.android.systemui.demo $c >/dev/null
  done
}

dispositivo() {
  if ! a get-state >/dev/null 2>&1; then
    log "arrancando el emulador trombito-36…"
    ( "$EMULADOR" -avd trombito-36 -no-snapshot-save -no-boot-anim >/dev/null 2>&1 & )
    a wait-for-device
    local i; for i in $(seq 1 60); do [ "$(sh_ getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done
  fi
  if [ -z "${ESTADO_ORIGINAL:-}" ]; then
    ESTADO_ORIGINAL=1
    AVION_ORIG=$(sh_ settings get global airplane_mode_on | tr -d '\r')
    NOCHE_ORIG=$(sh_ cmd uimode night | tr -d '\r' | sed 's/.*: *//')
    FONT_ORIG=$(sh_ settings get system font_scale | tr -d '\r')
    TIMEOUT_ORIG=$(sh_ settings get system screen_off_timeout | tr -d '\r')
  fi
  sh_ wm size 1080x1920; sh_ wm density 420
  sh_ settings put system font_scale 1.0
  sh_ cmd uimode night yes >/dev/null
  sh_ settings put system screen_off_timeout 1800000
  sh_ svc power stayon true
  sh_ input keyevent KEYCODE_WAKEUP; sh_ wm dismiss-keyguard >/dev/null 2>&1
  sh_ cmd media_session volume --stream 3 --set 15 >/dev/null 2>&1
  sh_ cmd statusbar collapse >/dev/null 2>&1
  sh_ input keyevent KEYCODE_HOME
  demo
  if [ "${SKIP_INSTALAR:-0}" != 1 ] && [ -f "$APK" ] && [ -z "${INSTALADO:-}" ]; then
    INSTALADO=1
    a install -r -g "$(ruta_win "$APK")" 2>&1 | tail -1 >&2
  fi
  local p; for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS RECORD_AUDIO; do
    sh_ pm grant "$PKG" "android.permission.$p" >/dev/null 2>&1
  done
}

restaurar() {
  parar_replay
  [ -n "$REC_PID" ] && kill "$REC_PID" 2>/dev/null
  a emu network speed full >/dev/null 2>&1
  sh_ am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1
  sh_ settings put global sysui_demo_allowed 0 >/dev/null 2>&1
  sh_ wm size reset; sh_ wm density reset
  sh_ svc power stayon false
  [ -n "${TIMEOUT_ORIG:-}" ] && sh_ settings put system screen_off_timeout "$TIMEOUT_ORIG"
  [ -n "${FONT_ORIG:-}" ] && [ "$FONT_ORIG" != null ] && sh_ settings put system font_scale "$FONT_ORIG"
  [ -n "${NOCHE_ORIG:-}" ] && sh_ cmd uimode night "$NOCHE_ORIG" >/dev/null 2>&1
  if [ "${AVION_ORIG:-0}" = 1 ]; then sh_ cmd connectivity airplane-mode enable >/dev/null 2>&1; fi
  sh_ input keyevent KEYCODE_HOME
}
trap restaurar EXIT

# --- datos de la app -------------------------------------------------------------------------------------------------------------
# SQL sobre la base de la app (debug: run-as). La app tiene que estar parada.
sql() { sh_ "run-as $PKG sqlite3 databases/rally.db \"$1\""; }

reiniciar_datos() {
  sh_ am force-stop "$PKG"; pausa 1
  # sin PRAGMA foreign_keys no hay cascadas: se borra tabla a tabla
  sql "delete from pasada; delete from subtramo; delete from marca; delete from edicion_nota; delete from tramo; delete from recorrido_punto; delete from recorrido; delete from sqlite_sequence where name in ('recorrido','pasada','subtramo','marca','edicion_nota');" >&2
  sql "update ajustes_conduccion set tema='OSCURO', modoCopiloto='COMPLETO', activarConCopiloto=1, cronometrar=1, salidaManual=0, notasGrandes=0;" >&2
  # bienvenida y pistas ya vistas: ningún globo ni pantalla de bienvenida sale en la grabación
  {
    echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
    local k; for k in bienvenida_vista pista_mapa pista_mis_tramos pista_analisis pista_editar_tramo; do
      echo "    <boolean name=\"$k\" value=\"true\" />"
    done
    echo "</map>"
  } >"$TMP/tutorial.xml"
  a push "$(ruta_win "$TMP/tutorial.xml")" /data/local/tmp/tutorial.xml >/dev/null
  sh_ "chmod 644 /data/local/tmp/tutorial.xml; run-as $PKG mkdir -p shared_prefs; run-as $PKG cp /data/local/tmp/tutorial.xml shared_prefs/tutorial.xml"
}

# --- conducción ------------------------------------------------------------------------------------------------------------------
# empieza una conducción con el coche ya colocado en la traza (sin saltos desde otro sitio)
# La primera fijación que lee la app es la última que quedó en caché: si es de otro sitio, el viaje arrastra un salto.
# Por eso se «ceba» antes con una conducción de cuatro segundos (menos de 1 km: no se guarda nada).
empezar_conduccion() {
  colocar_coche "${1:-0}"
  tocar "=CONDUCIR" 1 4 || return 1
  terminar_conduccion; inicio || return 1   # (con viajes previos se abre un resumen vacío: Atrás)
  tocar "=CONDUCIR" 1 5 || return 1
  sin_pistas
}

# termina la conducción: con el coche en marcha el primer toque en ✕ pide confirmación (caduca en ~2 s) y hay que
# tocar otra vez; parado, el primero ya cierra (por eso se mira si el mapa sigue antes del segundo toque)
terminar_conduccion() {
  local xy; volcar
  xy=$(centro "cd:Cerrar el mapa") || { log "ERROR: no encuentro «Cerrar el mapa»"; return 1; }
  sh_ input tap $xy; sleep 0.4
  existe "cd:Cerrar el mapa" && sh_ input tap $xy
  pausa 3
}

# --- pasos ---------------------------------------------------------------------------------------------------------------------------
paso_preparar() {
  reiniciar_datos
  abrir_app || return 1
  log "pasada de referencia a $REF_KMH km/h durante $REF_SEGUNDOS s"
  empezar_conduccion 0 || return 1
  arrancar_replay "$REF_KMH"
  sleep "$REF_SEGUNDOS"
  parar_replay
  terminar_conduccion || return 1
  sleep 2; inicio
  # el tramo de referencia tiene que existir
  local n=0 i   # el guardado de la pasada es asíncrono: se espera hasta 40 s
  for i in $(seq 1 20); do
    n=$(sql "select count(*) from pasada;" | tr -d '\r')
    [ "${n:-0}" -ge 1 ] && break
    sleep 2
  done
  [ "${n:-0}" -ge 1 ] || { log "ERROR: no se guardó la pasada de referencia"; return 1; }
  log "referencia lista: $(sql "select count(*) || ' tramos, ' || (select count(*) from pasada) || ' pasadas' from tramo;" | tr -d '\r')"
}

# el delta verde del vídeo sale de comparar con una pasada de referencia más lenta: si ya se grabaron clips encima,
# el mejor tiempo es el del vídeo anterior y hay que volver a empezar de cero
asegurar_referencia() {
  local n; n=$(sql "select count(*) from recorrido;" | tr -d '\r')
  [ "$n" = 1 ] && return 0
  log "hay $n viajes y se espera solo la pasada de referencia: se prepara de nuevo"
  paso_preparar
}

paso_c1_una() {   # mapa guiando, notas en pantalla y voz cantándolas (por una parte de la carretera sin tramo conocido)
  abrir_app || return 1
  empezar_conduccion "$C1_DESDE" || return 1
  arrancar_replay "$VIDEO_KMH" "$C1_DESDE"
  esperar "~ a [0-9]+ metros" 40 || { parar_replay; return 1; }
  sleep 6
  captura 01-notas
  rec_iniciar "$CLIPS/c1-notas.mp4" "$SEG_CLIP" || { parar_replay; return 1; }
  sleep "$SEG_CLIP"
  rec_esperar
  parar_replay
  terminar_conduccion
}

paso_c1() {
  local i
  for i in 1 2 3; do
    paso_c1_una && clip_ok "$CLIPS/c1-notas.mp4" && return 0
    log "c1 sin vídeo completo o sin voz (intento $i)"; parar_replay
  done
  return 1
}

# c3 (fantasma y delta, al principio del tramo) y c2 (crono, parciales y meta, al final) en la misma pasada,
# y c4 (resumen del viaje) al terminarla
paso_tramo_una() {
  abrir_app || return 1
  empezar_conduccion 0 || return 1
  arrancar_replay "$VIDEO_KMH"
  esperar "Fin de tramo en" 45 || { parar_replay; return 1; }
  sleep 7   # unos segundos dentro del tramo: el copiloto ya canta notas y el fantasma se separa
  captura 03-fantasma
  rec_iniciar "$CLIPS/c3-fantasma.mp4" "$SEG_CLIP" || { parar_replay; return 1; }
  sleep "$SEG_CLIP"
  rec_esperar
  # c2: a 150 m de la meta (bajo el km el HUD cuenta en metros, a pasos de 50): el clip coge los parciales, la llegada
  # y la tarjeta «META · NUEVO MEJOR» con el delta final
  esperar "~Fin de tramo en 150 m|Meta" 150 || { parar_replay; return 1; }
  captura 02-tramo
  rec_iniciar "$CLIPS/c2-tramo.mp4" "$SEG_CLIP" || { parar_replay; return 1; }
  sleep "$SEG_CLIP"
  rec_esperar
  parar_replay
  terminar_conduccion
  esperar "=RESUMEN DEL VIAJE" 15 || return 1
  pausa 2
  # c4: el resumen (arriba, mapa de velocidad y cifras, y al bajar las carreteras)
  captura 04-resumen
  rec_iniciar "$CLIPS/c4-resumen.mp4" "$SEG_CLIP" || return 1
  sleep 2.5
  deslizar bajar 2000 2; deslizar bajar 2000 2.5
  rec_esperar
  inicio
}

# El codificador de vídeo del emulador se para alguna vez a media grabación (clip con menos vídeo que audio): si pasa,
# se rehace toda la pasada, con su referencia, hasta tres veces
paso_tramo() {
  local i
  for i in 1 2 3; do
    paso_tramo_una && clip_ok "$CLIPS/c3-fantasma.mp4" && clip_ok "$CLIPS/c2-tramo.mp4" && return 0
    log "pasada $i incompleta, con vídeo corto o sin voz: se repite con una referencia nueva"
    parar_replay; paso_preparar
  done
  return 1
}

paso_c5() {   # Perfil: tablero y temporada
  inicio || return 1
  tocar "=Perfil del piloto" 1 2.5 || return 1
  volcar; local xt; xt=$(centro "=Temporada") || { log "ERROR: no encuentro la pestaña Temporada"; return 1; }
  rec_iniciar "$CLIPS/c5-temporada.mp4" "$SEG_CLIP" || return 1
  sleep 1.2; deslizar bajar 1400 0.8; deslizar subir 1100 0.4
  sh_ input tap $xt; pausa 1.3          # sin volcar la UI dentro de la grabación: no cabría en los 11 s
  deslizar bajar 1500 1.1; deslizar bajar 1500 1
  rec_esperar
  inicio
}

limpiar_notificaciones() {   # quita las notificaciones del sistema del emulador antes de enseñar el cajón
  sh_ cmd statusbar expand-notifications; pausa 1.5
  existe "=Clear all" && tocar "=Clear all" 1 1
  sh_ cmd statusbar collapse; pausa 1
}

paso_fgs_location() {   # la navegación sigue con la app en segundo plano y la pantalla apagada
  abrir_app || return 1
  limpiar_notificaciones
  empezar_conduccion "$C1_DESDE" || return 1
  arrancar_replay "$VIDEO_KMH" "$C1_DESDE"
  esperar "~ a [0-9]+ metros" 40 || { parar_replay; return 1; }
  sleep 2
  rec_iniciar "$FGS/fgs-location.mp4" 20 || { parar_replay; return 1; }
  sleep 3
  sh_ input keyevent KEYCODE_HOME; pausa 2
  sh_ cmd statusbar expand-notifications; pausa 4
  sh_ cmd statusbar collapse; pausa 1
  sh_ input keyevent KEYCODE_SLEEP; pausa 3
  sh_ input keyevent KEYCODE_WAKEUP; sh_ wm dismiss-keyguard >/dev/null 2>&1; pausa 1.5
  sh_ monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  rec_esperar
  parar_replay
  terminar_conduccion
}

paso_fgs_microphone() {   # «Copiloto» escuchando: ajuste, botón del micrófono y notificación del servicio
  abrir_app || return 1
  limpiar_notificaciones
  # Ensayo fuera de grabación (cebado de la ubicación y coordenadas de los botones): dentro de los 18 s no cabe volcar la UI
  volcar; local xy_conducir xy_mic
  colocar_coche "$C1_DESDE"
  xy_conducir=$(centro "=CONDUCIR") || { log "ERROR: no encuentro CONDUCIR"; return 1; }
  sh_ input tap $xy_conducir; pausa 4
  volcar; xy_mic=$(centro "cd:Hablar al copiloto") || { log "ERROR: no encuentro el micrófono"; return 1; }
  terminar_conduccion; inicio || return 1
  tocar "=Ajustes" 1 2 || return 1
  tocar "=Escucha" 1 2 || return 1
  rec_iniciar "$FGS/fgs-microphone.mp4" 18 || return 1
  sleep 3
  sh_ input keyevent KEYCODE_BACK; sleep 0.8; sh_ input keyevent KEYCODE_BACK; sleep 1.2
  sh_ input tap $xy_conducir; sleep 3
  arrancar_replay "$VIDEO_KMH" "$C1_DESDE"; sleep 1.5
  sh_ input tap $xy_mic; sleep 2.5
  sh_ input keyevent KEYCODE_HOME; sleep 1.2
  sh_ cmd statusbar expand-notifications; sleep 3
  sh_ cmd statusbar collapse
  rec_esperar
  parar_replay
  sh_ monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; pausa 2
  terminar_conduccion
}

paso_fgs_datasync() {   # descarga en Mapas offline (hace falta red: se saca del modo avión mientras tanto)
  abrir_app || return 1
  sh_ cmd connectivity airplane-mode disable >/dev/null 2>&1; sh_ svc wifi enable; sleep 6
  demo
  tocar "=Ajustes" 1 2 || return 1
  tocar "=Datos" 1 2 || return 1
  tocar "=Mapas offline" 1 3 || return 1
  limpiar_notificaciones
  volcar; centro "~Descargar satélite" >/dev/null || { log "ERROR: no hay «Descargar satélite» (¿ya instalado?)"; return 1; }
  rec_iniciar "$FGS/fgs-datasync.mp4" 18 || return 1
  sleep 2
  tocar "~Descargar satélite" 1 6
  sh_ cmd statusbar expand-notifications; pausa 4
  sh_ cmd statusbar collapse; pausa 3
  rec_esperar
  tocar "=Cancelar descarga" 1 2
  sh_ am force-stop "$PKG"
  sh_ run-as "$PKG" rm -rf files/mapa/.nuevo      # restos de la descarga cancelada (cientos de MB)
  [ "${AVION_ORIG:-0}" = 1 ] && sh_ cmd connectivity airplane-mode enable >/dev/null 2>&1
  demo
}

paso_capturas() {
  abrir_app || return 1
  local ok=0
  tocar "=Perfil del piloto" 1 3 && captura 05-tablero || ok=1
  inicio
  tocar "=Mis carreteras" 1 4 && captura 06-carreteras || ok=1
  inicio
  tocar "=Ajustes" 1 2 && tocar "=Datos" 1 2 && tocar "=Mapas offline" 1 3 && captura 07-offline || ok=1
  inicio
  # hoja del copiloto: se abre desde el botón del mapa; coche parado en plena carretera
  empezar_conduccion "$C1_DESDE" && tocar "~Copiloto: modo" 1 2.5 && captura 08-hoja || ok=1
  sh_ input keyevent KEYCODE_BACK; pausa 1
  terminar_conduccion; inicio
  return $ok
}

# el vídeo tiene que llegar al menos al 85 % de la duración del audio (y a 8 s)
video_ok() {
  local f; f="$(ruta_win "$1")"
  local v a
  v=$(ffprobe -v error -select_streams v:0 -show_entries packet=pts_time -of csv=p=0 "$f" 2>/dev/null | sort -n | tail -1)
  a=$(ffprobe -v error -select_streams a:0 -show_entries packet=pts_time -of csv=p=0 "$f" 2>/dev/null | sort -n | tail -1)
  [ -n "$v" ] && [ -n "$a" ] && awk -v v="$v" -v a="$a" 'BEGIN { exit !(v >= 8 && v >= 0.85 * a) }'
}

# con audio: el pico del clip tiene que superar -50 dBFS (la voz del copiloto en el emulador sale hacia -33 dBFS)
audio_ok() {
  local pico
  pico=$(ffmpeg -hide_banner -i "$(ruta_win "$1")" -vn -af volumedetect -f null - 2>&1 | grep -o 'max_volume: [-0-9.]*' | awk '{print $2}')
  [ -n "$pico" ] && awk -v p="$pico" 'BEGIN { exit !(p > -50) }'
}
clip_ok() { video_ok "$1" && audio_ok "$1"; }

# --- validación ------------------------------------------------------------------------------------------------------------------
validar() {
  command -v ffprobe >/dev/null || return 0
  local f
  for f in "$CLIPS"/*.mp4 "$FGS"/*.mp4; do
    [ -f "$f" ] || continue
    video_ok "$f" || echo "AVISO: $(basename "$f") tiene menos vídeo que audio"
    case "$f" in */fgs-datasync.mp4|*/c4-*|*/c5-*) ;; *) audio_ok "$f" || echo "AVISO: $(basename "$f") sin voz del copiloto (silencio)" ;; esac
    echo "$(basename "$f"): $(ffprobe -v error -show_entries stream=codec_type,width,height,duration -of csv=p=0 "$(ruta_win "$f")" | tr '\n' ' ')"
  done
  ls -1 "$RAW"/*.png 2>/dev/null | sed 's|.*/||' | tr '\n' ' '; echo
}

correr() {   # correr <nombre> <función>
  log "== $1 =="
  "$2" || { FALLOS+=("$1"); log "FALLO en $1"; }
}

# --- main ------------------------------------------------------------------------------------------------------------------------
[ "${BASH_SOURCE[0]}" = "$0" ] || return 0   # permite `source` para depurar las funciones
FASES=("$@"); [ ${#FASES[@]} -eq 0 ] && FASES=(todos)
mkdir -p "$TMP" "$CLIPS" "$FGS" "$RAW"
dispositivo
for fase in "${FASES[@]}"; do
  case "$fase" in
    preparar) correr preparar paso_preparar ;;
    clips) correr referencia asegurar_referencia; correr c1 paso_c1; correr tramo-c2-c3-c4 paso_tramo; correr c5 paso_c5 ;;
    fgs) correr fgs-location paso_fgs_location; correr fgs-microphone paso_fgs_microphone; correr fgs-datasync paso_fgs_datasync ;;
    capturas) correr capturas paso_capturas ;;
    c1) correr c1 paso_c1 ;;
    tramo) correr tramo-c2-c3-c4 paso_tramo ;;
    c5) correr c5 paso_c5 ;;
    fgs-location) correr fgs-location paso_fgs_location ;;
    fgs-microphone) correr fgs-microphone paso_fgs_microphone ;;
    fgs-datasync) correr fgs-datasync paso_fgs_datasync ;;
    todos)
      correr preparar paso_preparar
      correr referencia asegurar_referencia; correr c1 paso_c1; correr tramo-c2-c3-c4 paso_tramo; correr c5 paso_c5
      correr fgs-location paso_fgs_location; correr fgs-microphone paso_fgs_microphone; correr fgs-datasync paso_fgs_datasync
      correr capturas paso_capturas ;;
    *) echo "fase desconocida: $fase (preparar|clips|fgs|capturas|todos|c1|tramo|c5|fgs-location|fgs-microphone|fgs-datasync)" >&2; exit 2 ;;
  esac
done
validar
if [ ${#FALLOS[@]} -gt 0 ]; then echo "FALLIDOS: ${FALLOS[*]}" >&2; exit 1; fi
log "todo grabado"
