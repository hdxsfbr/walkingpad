from __future__ import annotations
import asyncio
import json
from bleak import BleakClient, BleakScanner
from .protocol import (
    DEFAULT_ADDR,
    DEFAULT_NAME,
    NOTIFY_UUID,
    WRITE_UUID,
    cmd_pause,
    cmd_set_speed,
    cmd_start,
    cmd_stop,
    make_keepalive,
    parse_status,
    speaker_query,
    speaker_set,
    buzzer_off,
    buzzer_on,
)


class PitPatController:
    def __init__(self, address: str = DEFAULT_ADDR, name: str = DEFAULT_NAME, *, debug: bool = False):
        self.address = address
        self.name = name
        self.debug = debug
        self.client: BleakClient | None = None
        self.seq = 0
        self.last_speed_mph = 994/1600.0
        self._keepalive_task: asyncio.Task | None = None

    async def scan(self, timeout: float = 8.0):
        devices = await BleakScanner.discover(timeout=timeout)
        return devices

    async def connect(self):
        devices = await BleakScanner.discover(timeout=6.0)
        target = None
        for d in devices:
            if d.address.upper() == self.address.upper() or (d.name or '').upper() == self.name.upper():
                target = d
                break
        if target is None:
            raise RuntimeError('target not found during connect scan')
        self.client = BleakClient(target, timeout=10.0)
        await self.client.connect()
        await self.client.start_notify(NOTIFY_UUID, self._on_notify)
        await asyncio.sleep(0.25)
        if not self.client.is_connected:
            raise RuntimeError('connected then dropped before ready')
        self._keepalive_task = asyncio.create_task(self._keepalive_loop())
        await asyncio.sleep(0.05)
        if not self.client.is_connected:
            raise RuntimeError('session failed after keepalive start')

    async def disconnect_only(self):
        if self._keepalive_task:
            self._keepalive_task.cancel()
            self._keepalive_task = None
        if self.client and self.client.is_connected:
            try:
                await self.client.disconnect()
            except Exception:
                pass

    async def disconnect(self):
        if self._keepalive_task:
            self._keepalive_task.cancel()
            self._keepalive_task = None
        if self.client and self.client.is_connected:
            try:
                await self.client.stop_notify(NOTIFY_UUID)
            except Exception:
                pass
            await self.client.disconnect()

    async def _keepalive_loop(self):
        while True:
            await asyncio.sleep(0.2)
            try:
                await self.send_keepalive()
            except Exception:
                break

    def _next_seq(self) -> int:
        n = self.seq & 0xFF
        self.seq = (self.seq + 1) & 0xFF
        return n

    def _on_notify(self, _sender, data: bytearray):
        if self.debug:
            print('notify', json.dumps(parse_status(bytes(data)), ensure_ascii=False))

    async def write(self, payload: bytes, *, quiet: bool = False):
        if not self.client or not self.client.is_connected:
            raise RuntimeError('not connected')
        if self.debug and not quiet:
            print('write', payload.hex())
        try:
            await self.client.write_gatt_char(WRITE_UUID, payload, response=False)
        except Exception as e:
            if self.debug or not quiet:
                print('write_error', repr(e))
            raise

    async def send_keepalive(self):
        await self.write(make_keepalive(self._next_seq()), quiet=True)

    async def start(self, speed_mph: float = 994/1600.0):
        self.last_speed_mph = speed_mph
        await self.write(cmd_start(self._next_seq(), speed_mph))
        await asyncio.sleep(0.05)
        await self.send_keepalive()

    async def set_speed(self, speed_mph: float):
        self.last_speed_mph = speed_mph
        await self.write(cmd_set_speed(self._next_seq(), speed_mph))
        await asyncio.sleep(0.05)
        await self.send_keepalive()

    async def pause(self):
        await self.write(cmd_pause(self._next_seq(), self.last_speed_mph))
        await asyncio.sleep(0.05)
        await self.send_keepalive()

    async def stop(self):
        await self.write(cmd_stop(self._next_seq()))
        await asyncio.sleep(0.05)
        await self.send_keepalive()

    async def speaker_status(self):
        await self.write(speaker_query())


    async def set_buzzer(self, enabled: bool):
        pkt = buzzer_on() if enabled else buzzer_off()
        await self.write(pkt)
    async def set_poweron_beep(self, enabled: bool, voice_prompts: bool = False):
        await self.write(speaker_set(1 if enabled else 0, 1 if voice_prompts else 0))
