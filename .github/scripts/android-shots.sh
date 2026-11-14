#!/bin/bash
# Capturas reales de la app Android en un emulador (pruebas en GitHub). Pantalla 1080×2400.
OUT=shots; mkdir -p $OUT
A() { timeout 25 adb "$@"; }
A install -r app.apk || exit 1
A shell am start -n com.silva.vidtube/.MainActivity
sleep 45
shot() { timeout 25 adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
# Toca por texto/descripción; si el árbol de la pantalla no se puede leer, usa las coordenadas X Y de respaldo
tap() {
  rm -f ui.xml
  A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && A pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  b=""
  [ -f ui.xml ] && b=$(python3 .github/scripts/find_node.py ui.xml "$1" "$2")
  [ -z "$b" ] && b="$3 $4"
  echo "tap $1 -> $b"; A shell input tap $b
}
swipe_up() { A shell input swipe 540 1800 540 800 450; sleep 4; }
BAR=2250
shot 01-inicio
swipe_up; shot 02-inicio-secciones
swipe_up; shot 03-descubre
swipe_up; shot 04-recomendado
tap Explorar last 540 $BAR; sleep 5; shot 05-explorar
tap Conciertos first 810 900; sleep 12; shot 06-busqueda
A shell input keyevent KEYCODE_BACK; sleep 2
tap Inicio last 108 $BAR; sleep 4
A shell input tap 540 1500; sleep 20; shot 07-video
A shell input keyevent KEYCODE_BACK; sleep 3
tap Shorts last 324 $BAR; sleep 18; shot 08-shorts
tap Suscripciones last 756 $BAR; sleep 6; shot 09-suscripciones
tap Biblioteca last 972 $BAR; sleep 5; shot 10-biblioteca
tap Inicio last 108 $BAR; sleep 4
tap "Tu perfil" first 1012 140; sleep 2; shot 11-perfil
tap "Usar tema claro" first 850 395; sleep 6; shot 12-tema-claro
swipe_up; shot 13-claro-secciones
A shell settings put system accelerometer_rotation 0
A shell settings put system user_rotation 1; sleep 6; shot 14-horizontal
timeout 20 adb logcat -d -t 400 '*:E' > $OUT/logcat-errores.txt 2>/dev/null
exit 0
