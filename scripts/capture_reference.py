#!/usr/bin/env python3
"""Capture an installed reference APK locally, without modifying the device."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--serial", help="ADB serial when multiple devices are attached")
parser.add_argument("--package", default="apps.ijp.coveros")
args = parser.parse_args()
if not re.fullmatch(r"[A-Za-z0-9_.]+", args.package):
    parser.error("Invalid package name")
adb = ["adb"] + (["-s", args.serial] if args.serial else [])
paths = subprocess.check_output(adb + ["shell", "pm", "path", args.package], text=True).splitlines()
if not paths:
    raise SystemExit("Reference package is not installed")
destination = pathlib.Path(__file__).resolve().parents[1] / ".reference" / "coverscreen-os"
destination.mkdir(parents=True, exist_ok=True)
inventory = []
for line in paths:
    if not line.startswith("package:"):
        continue
    remote = line.removeprefix("package:").strip()
    local = destination / pathlib.PurePosixPath(remote).name
    subprocess.run(adb + ["pull", remote, str(local)], check=True)
    inventory.append({"file": local.name, "bytes": local.stat().st_size,
                      "sha256": hashlib.sha256(local.read_bytes()).hexdigest()})
(destination / "inventory.json").write_text(json.dumps(inventory, indent=2) + "\n")
print(f"Captured {len(inventory)} APKs in {destination}")
