#!/usr/bin/env python3
"""
Drives the debug APK on a running emulator and saves a screenshot of every screen, in light
and dark mode, so the UI can be reviewed without a phone (PLAN-links-and-ui.md 2.2).

    python3 screenshots.py <app-debug.apk> <out-dir>

Needs adb on PATH and one booted device. Elements are found by their text or content
description in `uiautomator dump`, so the script survives layout changes; it fails, with
the logcat saved next to the screenshots, if the app crashes.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "de.munichhenge"
ACTIVITY = f"{PKG}/de.munichhenge.app.ui.MainActivity"   # applicationId differs from the code namespace
APK, OUT = sys.argv[1], sys.argv[2]
RAW = os.path.join(OUT, "raw")
LOG = []
LAST_DUMP = [""]


def adb(*args, binary=False, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=120)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.decode(errors='replace')}")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def sh(cmd):
    return adb("shell", cmd)


def alive():
    return sh(f"pidof {PKG} || true").strip() != ""


def dump():
    """The current view tree, or None while the UI is animating (uiautomator needs idle)."""
    for _ in range(5):
        out = adb("shell", "uiautomator dump /sdcard/ui.xml", check=False)
        if "dumped to" in out:
            xml = adb("exec-out", "cat /sdcard/ui.xml", binary=True)
            LAST_DUMP[0] = xml.decode(errors="replace")
            root = ET.fromstring(xml)
            if dismiss_system_dialog(root):
                continue
            return root
        LOG.append("dump failed: " + out.strip()[:200])
        time.sleep(1)
    return None


def dismiss_system_dialog(root):
    """Taps "Wait" on an "isn't responding" dialog (slow emulators show them at start)."""
    texts = [(t, d, b) for t, d, b in nodes(root)]
    if any("isn't responding" in t or "isn’t responding" in t for t, _, _ in texts):
        for t, _, b in texts:
            if t in ("Wait", "WAIT", "Close app"):
                LOG.append("dismissed system dialog with " + t)
                tap(b)
                time.sleep(2)
                return True
    return False


def nodes(root):
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            yield n.get("text", ""), n.get("content-desc", ""), (x1, y1, x2, y2)


def find(label, exact=True, lowest=False, timeout=20):
    """Bounds of the element whose text or description is (or contains) `label`."""
    end = time.time() + timeout
    while time.time() < end:
        root = dump()
        if root is not None:
            hits = [b for t, d, b in nodes(root)
                    if ((label in (t, d)) if exact else (label in t or label in d))]
            if hits:
                return max(hits, key=lambda b: b[1]) if lowest else hits[0]
        time.sleep(1)
    return None


def tap(bounds):
    x1, y1, x2, y2 = bounds
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")


def tap_label(label, **kw):
    b = find(label, **kw)
    if b is None:
        LOG.append(f"not found: {label!r}")
        return False
    tap(b)
    return True


def swipe_up():
    sh("input swipe 540 1900 540 700 400")


def back():
    sh("input keyevent 4")


def shot(name, settle=2.5):
    time.sleep(settle)
    if not alive():
        fail(f"app not running before screenshot {name}")
    with open(os.path.join(RAW, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap -p", binary=True))
    LOG.append(f"shot {name}")


def app_logcat():
    """Only what concerns the app: its own lines, crashes, ANRs and activity starts."""
    pid = sh(f"pidof {PKG} || true").strip()
    keep = re.compile(r"munichhenge|AndroidRuntime|FATAL|ANR in|ActivityTaskManager: (START|Displayed)" +
                      (rf"|\(\s*{pid}\)" if pid else ""))
    lines = adb("logcat", "-d", "-v", "time", check=False).splitlines()
    return "\n".join(l for l in lines if keep.search(l))[-60000:] + "\n"


def fail(msg):
    try:
        with open(os.path.join(RAW, "zz-failure.png"), "wb") as f:
            f.write(adb("exec-out", "screencap -p", binary=True))
        with open(os.path.join(OUT, "last_dump.xml"), "w") as f:
            f.write(LAST_DUMP[0])
        LOG.append("foreground: " + sh("dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' || true").strip())
        LOG.append("app alive: " + str(alive()))
    except Exception as e:  # diagnostics must not hide the failure
        LOG.append(f"diagnostics failed: {e}")
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(app_logcat())
    LOG.append("FAILED: " + msg)
    write_log()
    sys.exit(msg)


def write_log():
    with open(os.path.join(OUT, "run.log"), "w") as f:
        f.write("\n".join(LOG) + "\n")


def tour(theme):
    sh(f"cmd uimode night {'yes' if theme == 'dark' else 'no'}")
    sh(f"am force-stop {PKG}")
    LOG.append(f"{theme}: " + sh(f"am start -W -n {ACTIVITY}").strip().replace("\n", " | "))
    if find("Sunrise", exact=False, timeout=60) is None:   # sun line / chip: the Day screen is up
        fail(f"{theme}: Day screen did not appear")
    time.sleep(3)                                # engine + weather
    p = f"{theme}-"

    shot(p + "01-day")
    swipe_up()
    shot(p + "02-day-scrolled")
    if tap_label("next day"):
        shot(p + "03-day-tomorrow", settle=4)
        swipe_up()
        shot(p + "04-day-tomorrow-scrolled")
        tap_label("Today")

    tap_label("Upcoming", lowest=True)
    find("Best in the next", exact=False, timeout=60)
    shot(p + "05-upcoming", settle=4)
    swipe_up()
    shot(p + "06-upcoming-scrolled")
    swipe_up()
    swipe_up()
    shot(p + "07-upcoming-more")

    # first card of the best-of block -> sightline detail
    tap_label("Upcoming", lowest=True)
    head = find("Best in the next", exact=False)
    if head:
        sh(f"input tap 540 {head[3] + 160}")
        shot(p + "08-detail", settle=4)
        swipe_up()
        shot(p + "09-detail-scrolled")
        swipe_up()
        shot(p + "10-detail-more")
        back()

    tap_label("Map", lowest=True)
    shot(p + "11-map", settle=12)               # tiles
    sh("input tap 540 1300")                    # whatever is in the middle
    shot(p + "12-map-tap", settle=3)

    tap_label("Day", lowest=True)
    if tap_label("Filter"):
        shot(p + "13-filter-sheet")
        back()
    if tap_label("Settings"):
        shot(p + "14-settings")
        back()


def main():
    os.makedirs(RAW, exist_ok=True)
    adb("install", "-r", "-g", APK)
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS || true")
    for s in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh(f"settings put global {s} 0")
    adb("logcat", "-c", check=False)
    for theme in ("light", "dark"):
        tour(theme)
    if not alive():
        fail("app died at the end of the tour")
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(app_logcat())
    write_log()


if __name__ == "__main__":
    main()
