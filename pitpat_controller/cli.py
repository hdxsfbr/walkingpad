from __future__ import annotations
import argparse
import asyncio
import os, sys
if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from pitpat_controller.controller import PitPatController
from pitpat_controller.protocol import DEFAULT_ADDR, DEFAULT_NAME


def parser():
    p = argparse.ArgumentParser()
    p.add_argument('--address', default=DEFAULT_ADDR)
    sub = p.add_subparsers(dest='cmd', required=True)
    sub.add_parser('scan')
    sub.add_parser('monitor')
    s = sub.add_parser('start')
    s.add_argument('--speed-mph', type=float, default=994/1600.0)
    ss = sub.add_parser('set-speed')
    ss.add_argument('--speed-mph', type=float, required=True)
    sub.add_parser('pause')
    sub.add_parser('stop')
    return p


async def main():
    args = parser().parse_args()
    ctl = PitPatController(address=args.address, name=DEFAULT_NAME)
    if args.cmd == 'scan':
        devices = await ctl.scan()
        for d in devices:
            print(d.address, d.name)
        return

    await ctl.connect()
    try:
        if args.cmd == 'monitor':
            await asyncio.sleep(10)
        elif args.cmd == 'start':
            await ctl.start(args.speed_mph)
            await asyncio.sleep(3)
        elif args.cmd == 'set-speed':
            await ctl.set_speed(args.speed_mph)
            await asyncio.sleep(3)
        elif args.cmd == 'pause':
            await ctl.pause()
            await asyncio.sleep(3)
        elif args.cmd == 'stop':
            await ctl.stop()
            await asyncio.sleep(3)
    finally:
        await ctl.disconnect()

if __name__ == '__main__':
    asyncio.run(main())
