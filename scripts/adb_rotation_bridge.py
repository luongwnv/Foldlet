#!/usr/bin/env python3
"""Keep a narrowly scoped cover-rotation helper alive only while tethered ADB is live."""
import argparse
import fcntl
import json
import pathlib
import secrets
import shlex
import signal
import socket
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--serial",required=True)
parser.add_argument("--emulator",action="store_true",help="Only permits the named disposable LIT emulator")
args = parser.parse_args()
adb = ["adb","-s",args.serial]
root = pathlib.Path(__file__).resolve().parents[1]
state = root/".reference"/"adb-bridge"
state.mkdir(parents=True,exist_ok=True)
lock = (state/"bridge.lock").open("w")
try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError: raise SystemExit("An ADB rotation bridge is already running in this workspace")

def shell(*arguments,check=True):
    result = subprocess.run(adb+["shell",shlex.join(arguments)],capture_output=True,text=True,timeout=15)
    if check and result.returncode: raise RuntimeError("ADB command failed")
    return result.stdout.strip()

if args.emulator:
    name = subprocess.check_output(adb+["emu","avd","name"],text=True,timeout=5)
    if "LIT_Cover_API_36_1" not in name.splitlines(): raise SystemExit("Only the dedicated LIT test AVD is allowed")
    target_display = 0
else:
    if not shell("getprop","ro.product.model").startswith("SM-F711"):
        raise SystemExit("Only the Z Flip3 cover display is supported")
    path = subprocess.check_output(adb+["get-devpath"],text=True,timeout=5).strip()
    if not path.lower().startswith("usb:"): raise SystemExit("This bridge only runs while the phone is connected by USB")
    target_display = 1
apk = next(line.removeprefix("package:") for line in shell("pm","path","vn.lit.screenos").splitlines() if line.endswith("/base.apk"))
device_port = 38000 + secrets.randbelow(15000)
app_token = secrets.token_urlsafe(32)
owner_token = secrets.token_urlsafe(32)
host_port = None
running = True

def stop(*_):
    global running
    running = False

signal.signal(signal.SIGINT,stop)
signal.signal(signal.SIGTERM,stop)

def call(op,*,owner=True,mode=None):
    with socket.create_connection(("127.0.0.1",host_port),timeout=2) as connection:
        connection.settimeout(8)
        message={"op":op,"auth":owner_token if owner else app_token}
        if mode is not None: message["mode"]=mode
        connection.sendall((json.dumps(message)+"\n").encode())
        response=json.loads(connection.makefile("rb").readline(2048))
        if not response.get("ok"): raise RuntimeError("ADB helper rejected the request")
        return response

try:
    # No credentials are printed or placed in workspace files. The helper is
    # detached from the start command; its owner-only heartbeat limits its life.
    command = shlex.join(["env",f"CLASSPATH={apk}","nohup","app_process","/system/bin","vn.lit.screenos.RotationAdbServer",str(device_port),app_token,owner_token,str(target_display)])
    launched = subprocess.run(adb+["shell",command+" </dev/null >/data/local/tmp/lit-screenos-adb-bridge.log 2>&1 &"],capture_output=True,text=True,timeout=10)
    if launched.returncode: raise RuntimeError("Cannot launch the ADB helper")
    forwarded = subprocess.run(adb+["forward","tcp:0",f"tcp:{device_port}"],capture_output=True,text=True,timeout=10)
    if forwarded.returncode: raise RuntimeError("Cannot forward the helper heartbeat port")
    host_port=int(forwarded.stdout.strip())
    for _ in range(30):
        try: call("heartbeat"); break
        except (OSError,RuntimeError,json.JSONDecodeError): time.sleep(.2)
    else: raise RuntimeError("ADB helper did not become ready; inspect its device log")
    shell("am","broadcast","-n","vn.lit.screenos/.AdbBridgeReceiver","-a","vn.lit.screenos.SET_ADB_BRIDGE","--ei","port",str(device_port),"--es","token",app_token,*( ["--ez","emulator","true"] if args.emulator else [] ))
    print("Foldlet cover rotation is ready over tethered ADB. Keep this process running; Ctrl+C restores rotation.",flush=True)
    while running:
        try: call("heartbeat")
        except (OSError,RuntimeError,json.JSONDecodeError):
            print("ADB disconnected; the device helper will restore rotation and exit after its lease expires.",flush=True)
            break
        time.sleep(1)
finally:
    if host_port is not None:
        try: call("stop")
        except (OSError,RuntimeError,json.JSONDecodeError): pass
    try: shell("am","broadcast","-n","vn.lit.screenos/.AdbBridgeReceiver","-a","vn.lit.screenos.CLEAR_ADB_BRIDGE","--es","token",app_token,check=False)
    except (OSError,RuntimeError,subprocess.TimeoutExpired): pass
    if host_port is not None:
        try: subprocess.run(adb+["forward","--remove",f"tcp:{host_port}"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5)
        except subprocess.TimeoutExpired: pass
    print("ADB rotation bridge stopped.",flush=True)
