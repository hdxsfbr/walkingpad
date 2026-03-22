# PitPat Walking Pad Controller

Minimal Python controller for BA05/PitPat treadmills like `PITPAT-T01` using BLE.

## Device
- Name: `PITPAT-T01`
- Service: `0000ffff-0000-1000-8000-00805f9b34fb`
- Write char: `0000ff01-0000-1000-8000-00805f9b34fb`
- Notify char: `0000ff02-0000-1000-8000-00805f9b34fb`

## Setup
```bash
cd ~/code/walkingpad
python3 -m venv .venv
. .venv/bin/activate
pip install bleak
```

## Examples
```bash
. ~/code/walkingpad/.venv/bin/activate
python ~/code/walkingpad/pitpat_controller/cli.py scan
python ~/code/walkingpad/pitpat_controller/cli.py monitor
python ~/code/walkingpad/pitpat_controller/cli.py start --speed-mph 1.0
python ~/code/walkingpad/pitpat_controller/cli.py set-speed --speed-mph 1.5
python ~/code/walkingpad/pitpat_controller/cli.py pause
python ~/code/walkingpad/pitpat_controller/cli.py stop
```

## Notes
- BA05 speed scale appears to be `mph * 1600`
- Keepalive packet: `4D 00 <seq> 05 6A 05 FD F8 43`
- Control packet family: `4D 00 <seq> 17 6A 17 ... 43`


Interactive session command: `disconnect` = raw BLE disconnect (mimics app path more closely).
