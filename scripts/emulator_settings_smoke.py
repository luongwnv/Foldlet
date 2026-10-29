#!/usr/bin/env python3
"""Test the phone settings UI on the named LIT AVD; restore its display geometry."""
import pathlib
import re
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
serial = None
for line in subprocess.check_output(["adb", "devices"], text=True).splitlines():
    if line.startswith("emulator-"):
        candidate = line.split()[0]
        name = subprocess.check_output(["adb", "-s", candidate, "emu", "avd", "name"], text=True, timeout=10)
        if "LIT_Cover_API_36_1" in name.splitlines():
            serial = candidate
            break
if serial is None:
    raise SystemExit("Start the dedicated LIT_Cover_API_36_1 AVD first")
adb = ["adb", "-s", serial]

def command(*args, timeout=30):
    return subprocess.check_output(adb + list(args), text=True, timeout=timeout).strip()

old_size = command("shell", "wm", "size")
old_density = command("shell", "wm", "density")
out = root / "artifacts" / "settings"
out.mkdir(parents=True, exist_ok=True)
try:
    # API 36 clamps overrides to 3x the physical maximum (512*3=1536).
    # This gives a phone-like 360dp-wide canvas within that bound.
    command("shell", "wm", "size", "648x1536")
    command("shell", "wm", "density", "288")
    command("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    command("shell", "input", "keyevent", "KEYCODE_MENU")
    for apk in (root / "app/build/outputs/apk/debug/app-debug.apk", root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        print(command("install", "-r", str(apk)), flush=True)
    result = command("shell", "am", "instrument", "-w", "-e", "mode", "settings", "vn.lit.screenos.test/vn.lit.screenos.HardwareSmokeTest", timeout=90)
    (out / "smoke.txt").write_text(result + "\n")
    print(result, flush=True)
    if "PASS:" not in result:
        raise SystemExit(1)
    for name in ("overview", "settings", "settings-lower", "names-preview", "info", "adb"):
        with (out / f"{name}.png").open("wb") as image:
            subprocess.run(adb + ["exec-out", "run-as", "vn.lit.screenos", "cat", f"cache/settings-{name}.png"], stdout=image, check=True, timeout=15)
finally:
    size = re.search(r"Override size: (\d+x\d+)", old_size)
    density = re.search(r"Override density: (\d+)", old_density)
    command("shell", "wm", "size", size[1] if size else "reset")
    command("shell", "wm", "density", density[1] if density else "reset")
