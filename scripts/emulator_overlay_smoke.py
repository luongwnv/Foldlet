#!/usr/bin/env python3
"""Test real accessibility overlay windows on the dedicated, empty LIT AVD."""
import pathlib
import subprocess
import time

root = pathlib.Path(__file__).resolve().parents[1]
serial = None
for line in subprocess.check_output(["adb", "devices"], text=True).splitlines():
    if not line.startswith("emulator-"):
        continue
    candidate = line.split()[0]
    avd = subprocess.check_output(["adb", "-s", candidate, "emu", "avd", "name"], text=True)
    if "LIT_Cover_API_36_1" in avd.splitlines():
        serial = candidate
        break
if serial is None:
    raise SystemExit("Start the owned LIT_Cover_API_36_1 emulator first")
adb = ["adb", "-s", serial]

def command(*arguments, timeout=45):
    return subprocess.check_output(adb + list(arguments), text=True, timeout=timeout).strip()

for apk in (root / "app/build/outputs/apk/debug/app-debug.apk", root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
    print(command("install", "-r", str(apk)), flush=True)

old_services = command("shell", "settings", "get", "secure", "enabled_accessibility_services")
old_enabled = command("shell", "settings", "get", "secure", "accessibility_enabled")
component = "vn.lit.screenos/vn.lit.screenos.NavigationService"
services = [] if old_services in ("null", "") else old_services.split(":")
if component not in services:
    services.append(component)
out = root / "artifacts" / "overlay"
out.mkdir(parents=True, exist_ok=True)
try:
    # Only this named, empty AVD is changed. Never enable accessibility by ADB on a phone.
    command("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
    command("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    command("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    command("shell", "settings", "put", "system", "screen_off_timeout", "600000")
    command("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    command("shell", "input", "keyevent", "KEYCODE_MENU")
    command("shell", "run-as", "vn.lit.screenos", "rm", "-f", "cache/overlay-await-accessibility")
    test = subprocess.Popen(adb + ["shell", "am", "instrument", "-w", "-e", "mode", "overlay", "vn.lit.screenos.test/vn.lit.screenos.HardwareSmokeTest"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    # Instrumentation restarts the target package. Rebind this test service only
    # after the target is running, without suppressing other accessibility services.
    for _ in range(50):
        marker = subprocess.run(adb + ["shell", "run-as", "vn.lit.screenos", "cat", "cache/overlay-await-accessibility"], capture_output=True, text=True)
        if marker.returncode == 0:
            others = ":".join(s for s in services if s != component)
            if others:
                command("shell", "settings", "put", "secure", "enabled_accessibility_services", others)
            else:
                command("shell", "settings", "delete", "secure", "enabled_accessibility_services")
            for _ in range(40):
                state = command("shell", "dumpsys", "accessibility")
                enabled = next((line for line in state.splitlines() if "Enabled services:" in line), "")
                if component not in enabled:
                    break
                time.sleep(.05)
            command("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
            command("shell", "settings", "put", "secure", "accessibility_enabled", "1")
            break
        if test.poll() is not None:
            break
        time.sleep(.1)
    result, _ = test.communicate(timeout=60)
    (out / "smoke.txt").write_text(result + "\n")
    print(result, flush=True)
    if "PASS:" not in result:
        raise SystemExit(1)
    for name in ("overlay-dashboard", "overlay-edge", "overlay-navigation", "overlay-underlay"):
        with (out / f"{name}.png").open("wb") as image:
            subprocess.run(adb + ["exec-out", "run-as", "vn.lit.screenos", "cat", f"cache/{name}.png"], stdout=image, check=True, timeout=30)
finally:
    for key, value in (("enabled_accessibility_services", old_services), ("accessibility_enabled", old_enabled)):
        if value == "null":
            command("shell", "settings", "delete", "secure", key)
        else:
            command("shell", "settings", "put", "secure", key, value)
