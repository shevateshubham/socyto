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

# On busy CI emulators, Pixel Launcher can show a SYSTEM-level ANR modal over our
# otherwise correctly rendered app. Dismiss its Wait action, then capture the app.
python3 - <<'PY'
import re, subprocess, time, xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.run(['adb',*args],text=True,stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE,check=False).stdout
for attempt in range(4):
    adb('shell','uiautomator','dump','/sdcard/socyto_modal_check.xml')
    xml=adb('shell','cat','/sdcard/socyto_modal_check.xml')
    try:
        root=ET.fromstring(xml)
    except ET.ParseError:
        time.sleep(3)
        continue
    labels=' '.join(n.attrib.get('text','') for n in root.iter('node'))
    if "isn't responding" not in labels and 'is not responding' not in labels:
        break
    wait=next((n for n in root.iter('node') if n.attrib.get('text','').strip()=='Wait'),None)
    if wait is None:
        raise SystemExit('System ANR dialog obscured SOCYTO and no Wait control was found')
    nums=list(map(int,re.findall(r'\d+',wait.attrib.get('bounds',''))))
    if len(nums)!=4:
        raise SystemExit('System ANR Wait control has no usable bounds')
    adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2))
    time.sleep(4)
else:
    raise SystemExit('Repeated system ANR popups obscured SOCYTO')
PY

capture() {
    local label="$1"
    adb exec-out screencap -p > "$OUT/${label}.png"
    adb shell uiautomator dump "/sdcard/${label}.xml" > /dev/null
    adb pull "/sdcard/${label}.xml" "$OUT/${label}-ui.xml" > /dev/null
}
capture "01-login-initial"
adb shell wm size > "$OUT/display-config.txt"
adb shell wm density >> "$OUT/display-config.txt"
adb shell settings get system font_scale >> "$OUT/display-config.txt"

python3 - "$OUT/01-login-initial-ui.xml" "$OUT/01-login-initial.png" "$ORIENTATION" <<'PY'
import sys, xml.etree.ElementTree as ET, struct
xml_file,png_file,orientation=sys.argv[1:]
root=ET.parse(xml_file).getroot()
labels=' '.join(n.attrib.get('text','')+' '+n.attrib.get('content-desc','') for n in root.iter('node'))
assert 'SOCYTO' in labels or 'Welcome back.' in labels, 'SOCYTO not on screen: '+labels[:700]
assert "isn't responding" not in labels, 'A system ANR dialog still obscures the app'
with open(png_file,'rb') as f:
    assert f.read(8)==b'\x89PNG\r\n\x1a\n', 'Screenshot not PNG'
    f.read(8)
    w,h=struct.unpack('>II',f.read(8))
assert w>200 and h>200, f'Invalid screenshot size {w}x{h}'
assert (w>h if orientation=='landscape' else h>w), f'Orientation mismatch {w}x{h}'
print('SOCYTO rendered correctly:',w,h)
PY

# Swipes must originate INSIDE the app's scrollable surface, not at x=12;
# the screen edge is reserved for Android system navigation gestures.
SIZE="$(adb shell wm size | tail -n 1 | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"
ACTUAL_W="${SIZE%x*}"
ACTUAL_H="${SIZE#*x}"
TOUCH_X=$(( ACTUAL_W*2/3 ))
for num in 2 3 4 5 6; do
    adb shell input touchscreen swipe "$TOUCH_X" "$(( ACTUAL_H*4/5 ))" "$TOUCH_X" "$(( ACTUAL_H/4 ))" 450
    sleep 1
    capture "0${num}-login-scroll"
done

python3 - "$OUT" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
directory=pathlib.Path(sys.argv[1])
content=[]
for p in sorted(directory.glob('*-ui.xml')):
    root=ET.parse(p).getroot()
    content.extend((n.attrib.get('text','')+' '+n.attrib.get('content-desc','')) for n in root.iter('node'))
all_text=' '.join(content)
for expected in ('Login ID or administrator email','Password','Sign in','Set up Super Admin account'):
    assert expected in all_text, f'{expected!r} was inaccessible after scrolling'
assert "isn't responding" not in all_text, 'System popup obscured UI during check'
print('All login controls reachable through actual scroll gestures')
PY

adb logcat -d -v brief > "$OUT/logcat.txt" || echo "Logcat unavailable" > "$OUT/logcat-warning.txt"
if grep -A 10 'FATAL EXCEPTION' "$OUT/logcat.txt" | grep -q 'com.socyto.app'; then
    echo "SOCYTO fatal crash detected"
    exit 1
fi
echo "PASS: ${PROFILE} ${WIDTH}x${HEIGHT} density=${DENSITY} font=${FONT} orientation=${ORIENTATION}" | tee "$OUT/result.txt"
