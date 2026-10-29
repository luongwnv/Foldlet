#!/usr/bin/env python3
"""ADB self-test of the same whitelisted rotation code used by the tethered helper."""
import argparse
import pathlib
import shlex
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
args = parser.parse_args()
adb = ["adb", "-s", args.serial]

def shell(*arguments):
    return subprocess.check_output(adb + ["shell", shlex.join(arguments)], text=True, timeout=15).strip()

if not shell("getprop", "ro.product.model").startswith("SM-F711"):
    raise SystemExit("This hardware test only supports the Z Flip3 profile")
apk = next(line.removeprefix("package:") for line in shell("pm", "path", "vn.lit.screenos").splitlines() if line.endswith("/base.apk"))
before = {display: (shell("wm", "user-rotation", "-d", str(display)), shell("wm", "fixed-to-user-rotation", "-d", str(display))) for display in (0,1)}
command = shlex.join(["env", f"CLASSPATH={apk}", "app_process", "/system/bin", "vn.lit.screenos.RotationShellService", "1", "portrait"])
process = subprocess.Popen(adb + ["shell", command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
lines = []
observed = ""
try:
    for line in process.stdout:
        lines.append(line.rstrip())
        if line.startswith("rotation-result="):
            observed = shell("dumpsys", "activity", "service", "vn.lit.screenos/.CoverMonitorService")
    code = process.wait(timeout=15)
finally:
    if process.poll() is None:
        process.terminate(); process.wait(timeout=5)
    # Independent recovery also runs if the self-test process exits unexpectedly.
    old_user, old_fixed = before[1]
    shell("wm", "user-rotation", "-d", "1", *old_user.split())
    shell("wm", "fixed-to-user-rotation", "-d", "1", old_fixed)
after = {display: (shell("wm", "user-rotation", "-d", str(display)), shell("wm", "fixed-to-user-rotation", "-d", str(display))) for display in (0,1)}
result = "\n".join(lines) + "\n" + observed
out = pathlib.Path(__file__).resolve().parents[1] / "artifacts" / "rotation-smoke.txt"
out.write_text(result + f"\nPolicy before={before}\nPolicy after={after}\n")
print(result)
if code != 0 or "rotation-result=lock 3" not in result or "rotation-policy-restored" not in result or before != after or "actualRotation=3" not in observed:
    raise SystemExit("Rotation or restoration verification failed; see artifacts/rotation-smoke.txt")
print("PASS: actual cover rotation=3; original cover policy restored; main-display policy unchanged")
