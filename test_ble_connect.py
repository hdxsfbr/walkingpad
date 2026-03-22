import asyncio
from bleak import BleakScanner, BleakClient

TARGET_ADDR = ''
TARGET_NAME = 'PITPAT-T01'
CHAR_NOTIFY = '0000ff02-0000-1000-8000-00805f9b34fb'

async def main():
    print('scanning...')
    devices = await BleakScanner.discover(timeout=8.0)
    for d in devices:
        print('found', d.address, d.name)
    target = None
    for d in devices:
        if d.address.upper() == TARGET_ADDR or (d.name or '').upper() == TARGET_NAME:
            target = d
            break
    if not target:
        print('TARGET_NOT_FOUND')
        return
    print('connecting', target.address, target.name)
    async with BleakClient(target.address, timeout=20.0) as client:
        print('connected', client.is_connected)
        try:
            val = await client.read_gatt_char(CHAR_NOTIFY)
            print('ff02', val.hex())
        except Exception as e:
            print('read_ff02_failed', repr(e))
        svcs = client.services
        if svcs:
            for s in svcs:
                print('service', s.uuid)
                for c in s.characteristics:
                    print(' char', c.uuid, c.properties)

asyncio.run(main())
