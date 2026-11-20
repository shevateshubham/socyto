#!/usr/bin/env bash
set -euo pipefail
PROFILE="${1:?profile}"
WIDTH="${2:?width}"
HEIGHT="${3:?height}"
DENSITY="${4:?density}"
FONT="${5:?font scale}"
ORIENTATION="${6:-portrait}"
OUT="android-ui-evidence/${PROFILE}"
mkdir -p "$OUT"

adb wait-for-device
adb shell wm size "${WIDTH}x${HEIGHT}"
adb shell wm density "$DENSITY"
adb shell settings put system font_scale "$FONT"
adb shell settings put system accelerometer_rotation 0
if [[ "$ORIENTATION" == landscape ]]; then
    adb shell settings put system user_rotation 1
else
    adb shell settings put system user_rotation 0
fi
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop com.socyto.app || true
adb shell am start -W -n com.socyto.app/.MainActivity | tee "$OUT/launch.txt"
sleep 6
adb exec-out screencap -p > "$OUT/01-login-initial.png"
adb shell uiautomator dump /sdcard/socyto_window.xml | tee "$OUT/ui-dump-output.txt"
adb pull /sdcard/socyto_window.xml "$OUT/01-login-ui.xml" > /dev/null
adb shell wm size > "$OUT/display-config.txt"
adb shell wm density >> "$OUT/display-config.txt"
adb shell settings get system font_scale >> "$OUT/display-config.txt"

python3 - "$OUT/01-login-ui.xml" "$OUT/01-login-initial.png" <<'PY'
import sys, xml.etree.ElementTree as ET, struct
xml_file,png_file=sys.argv[1:]
root=ET.parse(xml_file).getroot()
text=' '.join(n.attrib.get('text','')+' '+n.attrib.get('content-desc','') for n in root.iter('node'))
print('Initial on-screen labels:',text[:2000])
assert 'Welcome' in text or 'Secure business and project management' in text, 'SOCYTO login heading missing'
assert 'Email' in text, 'Email input missing'
with open(png_file,'rb') as f:
    assert f.read(8)==b'\x89PNG\r\n\x1a\n', 'Screenshot not PNG'
    f.read(8)
    w,h=struct.unpack('>II',f.read(8))
assert w>200 and h>200, f'Invalid screenshot size {w}x{h}'
print('Real app login screen rendered:', w,h)
PY

# Scroll to ensure sign-in and registration are accessible on constrained displays.
SIZE="$(adb shell wm size | tail -n 1 | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"
ACTUAL_W="${SIZE%x*}"
ACTUAL_H="${SIZE#*x}"
adb shell input swipe "$(( ACTUAL_W/2 ))" "$(( ACTUAL_H*4/5 ))" "$(( ACTUAL_W/2 ))" "$(( ACTUAL_H/4 ))" 450
sleep 2
adb exec-out screencap -p > "$OUT/02-login-scrolled.png"
adb shell uiautomator dump /sdcard/socyto_scrolled.xml > /dev/null
adb pull /sdcard/socyto_scrolled.xml "$OUT/02-login-scrolled-ui.xml" > /dev/null
python3 - "$OUT/01-login-ui.xml" "$OUT/02-login-scrolled-ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
content=[]
for path in sys.argv[1:]:
    root=ET.parse(path).getroot()
    content += [(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')) for n in root.iter('node')]
text=' '.join(content)
for expected in ('Password','Sign in','Create account'):
    assert expected in text, f'{expected!r} not available after scrolling'
print('All login controls accessible on tested screen size')
PY
adb logcat -d -t 4000 > "$OUT/logcat.txt"
if grep -A 10 'FATAL EXCEPTION' "$OUT/logcat.txt" | grep -q 'com.socyto.app'; then
    echo "SOCYTO fatal crash detected"; exit 1
fi
echo "PASS: ${PROFILE} ${WIDTH}x${HEIGHT} density=${DENSITY} font=${FONT} orientation=${ORIENTATION}" | tee "$OUT/result.txt"
