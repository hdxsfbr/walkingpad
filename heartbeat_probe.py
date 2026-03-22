import asyncio
from bleak import BleakScanner, BleakClient

TARGET_ADDR = ''
TARGET_NAME = 'PITPAT-T01'
CHAR_WRITE = '0000ff01-0000-1000-8000-00805f9b34fb'
CHAR_NOTIFY = '0000ff02-0000-1000-8000-00805f9b34fb'

def hb(counter: int) -> bytes:
    # observed pattern: 4d 00 <ctr> 05 6a 05 fd f8 43
    return bytes.fromhex(f'4d00{counter & 0xff:02x}056a05fdf843')

async def main():
    print('scanning...')
    devices = await BleakScanner.discover(timeout=6.0)
    target = None
    for d in devices:
        print('found', d.address, d.name)
        if d.address.upper() == TARGET_ADDR or (d.name or '').upper() == TARGET_NAME:
            target = d
    if not target:
        print('TARGET_NOT_FOUND')
        return

    print('connecting', target.address, target.name)
    client = BleakClient(target.address, timeout=10.0)
    try:
        await client.connect()
        print('connected?', client.is_connected)

        async def cb(_, data: bytearray):
            print('notify ff02', data.hex())

        try:
            await client.start_notify(CHAR_NOTIFY, cb)
            print('notify started')
        except Exception as e:
            print('start_notify_failed', repr(e))

        for i in range(3, 15):
            pkt = hb(i)
            try:
                print('write hb', i, pkt.hex())
                await client.write_gatt_char(CHAR_WRITE, pkt, response=True)
            except Exception as e:
                print('write_failed', i, repr(e))
                break
            await asyncio.sleep(0.35)

        try:
            val = await client.read_gatt_char(CHAR_NOTIFY)
            print('read ff02', val.hex())
        except Exception as e:
            print('read_failed', repr(e))

        await asyncio.sleep(2)
    finally:
        if client.is_connected:
            await client.disconnect()
            print('disconnected')

asyncio.run(main())
