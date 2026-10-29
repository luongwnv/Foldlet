#!/usr/bin/env python3
"""Verify lease expiry without disconnecting other ADB devices; LIT test AVD only."""
import argparse
import json
import os
import pathlib
import signal
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5556")
args = parser.parse_args()
adb = ["adb", "-s", args.serial]
root = pathlib.Path(__file__).resolve().parents[1]

def command(*arguments):
    return subprocess.check_output(adb + list(arguments), text=True, timeout=10).strip()

if not args.serial.startswith("emulator-") or "LIT_Cover_API_36_1" not in command("emu", "avd", "name").splitlines():
    raise SystemExit("This test only operates on the dedicated LIT emulator")

def policies():
    return tuple(command("shell", "wm", op, "-d", "0") for op in ("user-rotation", "fixed-to-user-rotation"))

before = policies()
bridge = None
forward = None
paused = False
lines = []
try:
    bridge = subprocess.Popen([sys.executable, str(root / "scripts/adb_rotation_bridge.py"), "--serial", args.serial, "--emulator"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    token = None
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if bridge.poll() is not None:
            raise RuntimeError("The bridge exited before provisioning; check for another active bridge")
        xml = command("shell", "run-as", "vn.lit.screenos", "cat", "shared_prefs/screenos.xml")
        prefs = {element.get("name"): element for element in ET.fromstring(xml)}
        if "adb_bridge_token" in prefs:
            token = prefs["adb_bridge_token"].text
            port = prefs["adb_bridge_port"].get("value")
            break
        time.sleep(.2)
    if not token:
        raise RuntimeError("The bridge did not provision the app")
    forward = command("forward", "tcp:0", f"tcp:{port}")

    def app_call(op, **values):
        with socket.create_connection(("127.0.0.1", int(forward)), timeout=1) as connection:
            connection.settimeout(2)
            connection.sendall((json.dumps({"auth": token, "op": op, **values}) + "\n").encode())
            return json.loads(connection.makefile("rb").readline(2048))

    assert app_call("ping")["ok"], "App cannot reach helper"
    assert not app_call("heartbeat")["ok"], "App token must not renew the owner lease"
    assert app_call("rotate", mode="portrait")["ok"], "Rotation request failed"
    assert policies() == ("lock 3", "enabled"), "Portrait policy was not applied"
    lines.append("PASS: app-authenticated rotation; app cannot renew the owner lease")

    # Freeze only the child we created. ADB stays connected, so any app traffic
    # can still reach the helper while owner heartbeats have stopped.
    os.kill(bridge.pid, signal.SIGSTOP)
    paused = True
    start = time.monotonic()
    closed = False
    while time.monotonic() - start < 8:
        try:
            assert not app_call("heartbeat")["ok"], "App renewed an owner-only lease"
        except (OSError, json.JSONDecodeError):
            closed = True
            break
        time.sleep(.2)
    assert closed, "Helper stayed alive after the owner lease expired"
    deadline = time.monotonic() + 5
    while policies() != before and time.monotonic() < deadline:
        time.sleep(.2)
    assert policies() == before, "Rotation policies were not restored"
    lines.append("PASS: stopped owner heartbeat closes helper and restores both rotation policies")
finally:
    if bridge is not None:
        if paused:
            os.kill(bridge.pid, signal.SIGCONT)
        if bridge.poll() is None:
            bridge.terminate()
        try:
            bridge.communicate(timeout=20)
        except subprocess.TimeoutExpired:
            bridge.kill()
            bridge.communicate()
    if forward:
        subprocess.run(adb + ["forward", "--remove", f"tcp:{forward}"], capture_output=True, timeout=5)
    # The disposable emulator must remain usable even when an assertion fails.
    if policies() != before:
        command("shell", "wm", "user-rotation", "-d", "0", *before[0].split())
        command("shell", "wm", "fixed-to-user-rotation", "-d", "0", before[1])

report = "\n".join(lines) + "\n"
output = root / "artifacts/adb-bridge-lease.txt"
output.parent.mkdir(exist_ok=True)
output.write_text(report)
print(report, end="")
