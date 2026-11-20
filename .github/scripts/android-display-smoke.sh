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
if [[ "$ORIENTATION" == landscape ]]; then
    # Android rotation swaps display axes; feed portrait-native dimensions.
    adb shell wm size "${HEIGHT}x${WIDTH}"
else
    adb shell wm size "${WIDTH}x${HEIGHT}"
fi
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

python3 - "$OUT/01-login-ui.xml" "$OUT/01-login-initial.png" "$ORIENTATION" <<'PY'
import sys, xml.etree.ElementTree as ET, struct
xml_file,png_file,orientation=sys.argv[1:]
root=ET.parse(xml_file).getroot()
text=' '.join(n.attrib.get('text','')+' '+n.attrib.get('content-desc','') for n in root.iter('node'))
print('Initial on-screen labels:',text[:2000])
if orientation=='landscape':
    assert 'Email' in text and 'Password' in text, 'Compact landscape login inputs missing'
else:
    assert 'Every detail.' in text or 'Make room for great work.' in text, 'Floot-style editorial SOCYTO login missing'
with open(png_file,'rb') as f:
    assert f.read(8)==b'\x89PNG\r\n\x1a\n', 'Screenshot not PNG'
    f.read(8)
    w,h=struct.unpack('>II',f.read(8))
assert w>200 and h>200, f'Invalid screenshot size {w}x{h}'
if orientation=='landscape':
    assert w > h, f'Landscape request produced portrait screenshot {w}x{h}'
else:
    assert h > w, f'Portrait request produced landscape screenshot {w}x{h}'
print('Real app login screen rendered:', w,h)
PY

# Scroll to ensure sign-in and registration are accessible on constrained displays.
SIZE="$(adb shell wm size | tail -n 1 | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"
ACTUAL_W="${SIZE%x*}"
ACTUAL_H="${SIZE#*x}"
adb shell input touchscreen swipe 12 "$(( ACTUAL_H*4/5 ))" 12 "$(( ACTUAL_H/4 ))" 600
sleep 2
adb exec-out screencap -p > "$OUT/02-login-scrolled.png"
adb shell uiautomator dump /sdcard/socyto_scrolled.xml > /dev/null
adb pull /sdcard/socyto_scrolled.xml "$OUT/02-login-scrolled-ui.xml" > /dev/null
# Larger system fonts may require additional swipes to reach the final action.
adb shell input touchscreen swipe 12 "$(( ACTUAL_H*4/5 ))" 12 "$(( ACTUAL_H/4 ))" 600
sleep 2
adb exec-out screencap -p > "$OUT/03-login-bottom.png"
adb shell uiautomator dump /sdcard/socyto_bottom.xml > /dev/null
adb pull /sdcard/socyto_bottom.xml "$OUT/03-login-bottom-ui.xml" > /dev/null
python3 - "$OUT/01-login-ui.xml" "$OUT/02-login-scrolled-ui.xml" "$OUT/03-login-bottom-ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
content=[]
for path in sys.argv[1:]:
    root=ET.parse(path).getroot()
    content += [(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')) for n in root.iter('node')]
text=' '.join(content)
for expected in ('Email address','Password','Sign in','Create an account'):
    assert expected in text, f'{expected!r} not available after scrolling'
print('All login controls accessible on tested screen size')
PY
adb logcat -d -v brief > "$OUT/logcat.txt" || echo "Logcat unavailable" > "$OUT/logcat-warning.txt"
if grep -A 10 'FATAL EXCEPTION' "$OUT/logcat.txt" | grep -q 'com.socyto.app'; then
    echo "SOCYTO fatal crash detected"; exit 1
fi
echo "PASS: ${PROFILE} ${WIDTH}x${HEIGHT} density=${DENSITY} font=${FONT} orientation=${ORIENTATION}" | tee "$OUT/result.txt"
