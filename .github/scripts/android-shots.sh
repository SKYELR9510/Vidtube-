#!/bin/bash
# Capturas reales de la app Android en un emulador (pruebas en GitHub)
OUT=shots; mkdir -p $OUT
adb install -r app.apk || exit 1
adb shell settings put global window_animation_scale 0
adb shell am start -n com.silva.vidtube/.MainActivity
sleep 45
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
# Toca el elemento con ese texto o descripción (la última coincidencia si se pide "last")
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  b=$(python3 - "$1" "$2" <<'PY'
import re, sys
s = open('ui.xml', encoding='utf-8').read(); t = sys.argv[1]; last = len(sys.argv) > 2 and sys.argv[2] == 'last'
hits = []
for m in re.finditer(r'<node [^>]*>', s):
    n = m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        hits.append(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
if hits: print(hits[-1] if last else hits[0])
PY
)
  echo "tap $1 -> $b"; [ -n "$b" ] && adb shell input tap $b
}
shot 01-inicio
adb shell input swipe 540 1700 540 900 500; sleep 4; shot 02-inicio-secciones
adb shell input swipe 540 1700 540 700 500; sleep 4; shot 03-descubre
adb shell input swipe 540 1700 540 700 500; sleep 4; shot 04-recomendado
tap Explorar last; sleep 5; shot 05-explorar
tap Conciertos; sleep 12; shot 06-busqueda
tap Inicio last; sleep 3
adb shell input swipe 540 700 540 1700 300; sleep 2
adb shell input tap 540 1100; sleep 20; shot 07-video
adb shell input keyevent KEYCODE_BACK; sleep 3
tap Shorts last; sleep 18; shot 08-shorts
tap Suscripciones last; sleep 6; shot 09-suscripciones
tap Biblioteca last; sleep 5; shot 10-biblioteca
tap Inicio last; sleep 3
adb shell input swipe 540 700 540 1700 300; sleep 2
tap "Tu perfil"; sleep 2; shot 11-perfil
tap "Usar tema claro"; sleep 6; shot 12-tema-claro
adb shell input swipe 540 1700 540 900 500; sleep 4; shot 13-claro-secciones
# Tablet / horizontal
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 6; shot 14-horizontal
adb logcat -d -t 400 *:E > $OUT/logcat-errores.txt 2>/dev/null
exit 0
