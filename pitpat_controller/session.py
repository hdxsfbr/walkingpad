from __future__ import annotations

import argparse
import asyncio
import os
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from pitpat_controller.controller import PitPatController
from pitpat_controller.protocol import DEFAULT_ADDR, DEFAULT_NAME

HELP = '''Commands:
  start [mph]      Start at mph (default 0.625)
  speed <mph>      Set speed
  pause            Pause
  stop             Stop
  status           Show connection state
  help             Show this help
  disconnect       Raw BLE disconnect (app-like)
  quit/exit        Exit session script
'''


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description='Interactive BLE session for PitPat/WalkingPad control.')
    p.add_argument('--address', default=DEFAULT_ADDR, help=f'Device BLE address (default: {DEFAULT_ADDR})')
    p.add_argument('--name', default=DEFAULT_NAME, help=f'Device BLE name (default: {DEFAULT_NAME})')
    p.add_argument('--scan', action='store_true', help='Scan for nearby BLE devices and exit.')
    p.add_argument('--debug', action='store_true', help='Print BLE writes/notifications/errors.')
    return p


async def ainput(prompt: str = '') -> str:
    return await asyncio.to_thread(input, prompt)


async def run_scan(address: str, name: str) -> int:
    ctl = PitPatController(address=address, name=name)
    devices = await ctl.scan()
    if not devices:
        print('No BLE devices found.')
        return 1
    for d in devices:
        print(f'{d.address}\t{d.name}')
    return 0


async def interactive_session(address: str, name: str, debug: bool = False) -> int:
    ctl = PitPatController(address=address, name=name, debug=debug)
    print(f'Connecting to treadmill (address={address}, name={name})...')
    await ctl.connect()
    print('Connected. Keepalive running.')
    print(HELP)
    try:
        while True:
            line = (await ainput('pitpat> ')).strip()
            if not line:
                continue
            parts = line.split()
            cmd = parts[0].lower()
            try:
                if cmd in ('quit', 'exit'):
                    break
                elif cmd == 'disconnect':
                    await ctl.disconnect_only()
                    print('Disconnected.')
                    break
                elif cmd == 'help':
                    print(HELP)
                elif cmd == 'status':
                    alive = bool(ctl.client and ctl.client.is_connected)
                    print(f'connected={alive} last_speed_mph={ctl.last_speed_mph:.3f} address={ctl.address} name={ctl.name}')
                elif cmd == 'start':
                    mph = float(parts[1]) if len(parts) > 1 else 994 / 1600.0
                    await ctl.start(mph)
                elif cmd == 'speed':
                    if len(parts) < 2:
                        print('usage: speed <mph>')
                        continue
                    await ctl.set_speed(float(parts[1]))
                elif cmd == 'pause':
                    await ctl.pause()
                elif cmd == 'stop':
                    await ctl.stop()
                else:
                    print('unknown command')
            except Exception as e:
                print('command_failed', repr(e))
    finally:
        if ctl.client and ctl.client.is_connected:
            await ctl.disconnect()
            print('Disconnected.')
    return 0


async def main() -> int:
    args = build_parser().parse_args()
    if args.scan:
        return await run_scan(args.address, args.name)
    return await interactive_session(args.address, args.name, debug=args.debug)


if __name__ == '__main__':
    raise SystemExit(asyncio.run(main()))
