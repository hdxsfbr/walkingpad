# SupeRun WalkingPad / PitPat BLE Controller

Minimal tooling for talking directly to some SupeRun-branded PitPat / WalkingPad treadmills over BLE, plus a small Android MVP app.

## What's here

- `pitpat_controller/` — Python BLE controller and protocol helpers
- `app/` — minimal Android app for connect / start / set speed / pause / stop
- `test_ble_connect.py` — quick BLE probe script
- `heartbeat_probe.py` — keepalive experiment script

## Features

- BLE keepalive packet generation
- Start / speed / pause / stop commands
- Status packet parsing
- Experimental speaker / buzzer packet helpers
- Android app that uses the same packet format as the Python controller

## Device assumptions

This was built against a SupeRun-branded PitPat / WalkingPad variant exposing:

- Service: `0000ffff-0000-1000-8000-00805f9b34fb`
- Write characteristic: `0000ff01-0000-1000-8000-00805f9b34fb`
- Notify characteristic: `0000ff02-0000-1000-8000-00805f9b34fb`

The current code defaults to BLE name matching (`PITPAT-T01`). If your device name differs, pass a custom name or address.

## Python usage

```bash
cd ~/code/walkingpad
python3 -m venv .venv
. .venv/bin/activate
pip install bleak

python pitpat_controller/cli.py scan
python pitpat_controller/cli.py --name PITPAT-T01 monitor
python pitpat_controller/cli.py --name PITPAT-T01 start --speed-mph 1.0
python pitpat_controller/cli.py --name PITPAT-T01 set-speed --speed-mph 1.5
python pitpat_controller/cli.py --name PITPAT-T01 pause
python pitpat_controller/cli.py --name PITPAT-T01 stop
```

You can also target a specific BLE MAC if needed:

```bash
python pitpat_controller/cli.py --address AA:BB:CC:DD:EE:FF start --speed-mph 1.0
```

## Android app

The Android MVP app currently expects a target name and address in `WalkingPadProtocol.java`.
Before building for your own treadmill, set those constants to match your device.

Build with a normal Android toolchain:

```bash
./gradlew assembleDebug
```

Expected APK path:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Speed conversion currently uses `mph * 1600` based on observed traffic and experiments.
- This repo intentionally does **not** include APKs, raw bugreports, BLE snoop logs, or decompiled proprietary app sources.
- Use this for interoperability, research, and your own hardware at your own risk.

## Legal / ethics

This repo is meant for interoperability and personal hardware control research.
Do not use it to redistribute proprietary app assets, copyrighted decompiled sources, or other people's device data.
