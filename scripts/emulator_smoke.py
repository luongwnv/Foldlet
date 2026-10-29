#!/usr/bin/env python3
"""Run a 512x260 UI smoke test only on the dedicated LIT emulator."""
import argparse
import pathlib
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--serial", help="Auto-detects the dedicated LIT emulator if omitted")
parser.add_argument("--suite", choices=("preview", "drawer"), default="preview")
args = parser.parse_args()
if args.serial is None:
    for line in subprocess.check_output(["adb", "devices"], text=True).splitlines():
        if not line.startswith("emulator-"):
            continue
        candidate = line.split()[0]
        name = subprocess.run(["adb", "-s", candidate, "emu", "avd", "name"], capture_output=True, text=True, timeout=10).stdout
        if "LIT_Cover_API_36_1" in name.splitlines():
            args.serial = candidate
            break
    if args.serial is None:
        raise SystemExit("Start the dedicated LIT_Cover_API_36_1 AVD first")
if not args.serial.startswith("emulator-"):
    parser.error("This script only operates on the dedicated emulator, never a physical phone")
root = pathlib.Path(__file__).resolve().parents[1]
out = root / "artifacts" / "emulator"
out.mkdir(parents=True, exist_ok=True)
adb = ["adb", "-s", args.serial]

def command(*arguments, check=True, timeout=45):
    return subprocess.run(adb + list(arguments), text=True, capture_output=True, check=check, timeout=timeout).stdout.strip()

avd = command("emu", "avd", "name")
if "LIT_Cover_API_36_1" not in avd.splitlines():
    raise SystemExit("Refusing to modify an AVD other than LIT_Cover_API_36_1")
deadline = time.monotonic() + 300
while time.monotonic() < deadline:
    status = command("shell", "getprop", "sys.boot_completed", check=False)
    if status == "1":
        break
    print("Waiting for first emulator boot...", flush=True)
    time.sleep(10)
else:
    raise SystemExit("Emulator has not completed booting; see artifacts/emulator.log")

# Changes apply only to the explicitly checked disposable LIT emulator.
command("shell", "settings", "put", "system", "accelerometer_rotation", "0")
command("shell", "settings", "put", "system", "user_rotation", "0")
command("shell", "settings", "put", "system", "font_scale", "1.0")
command("shell", "settings", "put", "system", "screen_off_timeout", "600000")
# Avoid the emulator's first-use fullscreen tutorial covering captured app pages.
command("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
command("shell", "wm", "size", "512x260")
command("shell", "wm", "density", "160")
command("shell", "input", "keyevent", "KEYCODE_WAKEUP")
command("shell", "input", "keyevent", "KEYCODE_MENU")
for apk in (root / "app/build/outputs/apk/debug/app-debug.apk", root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    print(command("install", "-r", str(apk)), flush=True)
result = command("shell", "am", "instrument", "-w", "-e", "mode", args.suite, "vn.lit.screenos.test/vn.lit.screenos.HardwareSmokeTest", timeout=60)
(out / ("smoke.txt" if args.suite == "preview" else "drawer-smoke.txt")).write_text(result + "\n")
print(result, flush=True)
if "PASS:" not in result:
    raise SystemExit(1)
captures = [(f"smoke-{i}", name) for i, name in enumerate(("apps", "notifications", "media", "toggles"))] if args.suite == "preview" else [(f"drawer-{name}", f"drawer-{name}") for name in ("grid", "scrolled", "search", "favorite", "recent")]
for remote, name in captures:
    with (out / f"{name}.png").open("wb") as image:
        subprocess.run(adb + ["exec-out", "run-as", "vn.lit.screenos", "cat", f"cache/{remote}.png"], stdout=image, check=True, timeout=30)
print(f"Screenshots: {out}")
