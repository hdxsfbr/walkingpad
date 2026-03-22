from __future__ import annotations

SERVICE_UUID = "0000ffff-0000-1000-8000-00805f9b34fb"
WRITE_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID = "0000ff02-0000-1000-8000-00805f9b34fb"
DEFAULT_NAME = "PITPAT-T01"
DEFAULT_ADDR = ""


def xor_checksum(data: bytes) -> int:
    x = 0
    for b in data:
        x ^= b
    return x


def mph_to_raw(speed_mph: float) -> int:
    return max(0, min(int(round(speed_mph * 1600.0)), 0xFFFF))


def raw_to_mph(raw: int) -> float:
    return raw / 1600.0


def make_keepalive(seq: int) -> bytes:
    return bytes([0x4D, 0x00, seq & 0xFF, 0x05, 0x6A, 0x05, 0xFD, 0xF8, 0x43])


def _base27(seq: int) -> bytearray:
    out = bytearray(27)
    out[0] = 0x4D
    out[1] = 0x00
    out[2] = seq & 0xFF
    out[3] = 0x17
    out[4] = 0x6A
    out[5] = 0x17
    out[6:10] = b'\x00\x00\x00\x00'
    out[13] = 0x00
    out[14] = 0x58
    out[15] = 0x00
    out[17:21] = b'\x00\x00\x00\x00'
    return out


def cmd_start(seq: int, speed_mph: float = 1000/1600.0) -> bytes:
    out = _base27(seq)
    speed = mph_to_raw(speed_mph)
    out[10] = (speed >> 8) & 0xFF
    out[11] = speed & 0xFF
    out[12] = 0x05
    out[16] = 0x0C
    out[21:25] = bytes.fromhex('000c5570')
    out[25] = xor_checksum(out[5:25])
    out[26] = 0x43
    return bytes(out)


def cmd_set_speed(seq: int, speed_mph: float) -> bytes:
    out = _base27(seq)
    speed = mph_to_raw(speed_mph)
    out[10] = (speed >> 8) & 0xFF
    out[11] = speed & 0xFF
    out[12] = 0x01
    out[16] = 0x0C
    out[21:25] = bytes.fromhex('000c5570')
    out[25] = xor_checksum(out[5:25])
    out[26] = 0x43
    return bytes(out)


def cmd_pause(seq: int, speed_mph: float = 1000/1600.0) -> bytes:
    out = _base27(seq)
    speed = mph_to_raw(speed_mph)
    out[10] = (speed >> 8) & 0xFF
    out[11] = speed & 0xFF
    out[12] = 0x05
    out[16] = 0x0A
    out[21:25] = bytes.fromhex('000c5570')
    out[25] = xor_checksum(out[5:25])
    out[26] = 0x43
    return bytes(out)


def cmd_stop(seq: int) -> bytes:
    out = _base27(seq)
    out[10] = 0x00
    out[11] = 0x00
    out[12] = 0x05
    out[16] = 0x08
    out[21:25] = bytes.fromhex('00000000')
    out[25] = xor_checksum(out[5:25])
    out[26] = 0x43
    return bytes(out)


def parse_status(data: bytes) -> dict:
    out = {"raw": data.hex()}
    if len(data) >= 20 and data[0] == 0x4D:
        out["packet_type"] = data[3]
        if len(data) >= 13:
            cur = (data[7] << 8) | data[8]
            tgt = (data[9] << 8) | data[10]
            mx = (data[11] << 8) | data[12]
            out["speed_feedback_mph"] = round(raw_to_mph(cur), 3)
            out["speed_cmd_mph"] = round(raw_to_mph(tgt), 3)
            out["speed_max_mph"] = round(raw_to_mph(mx), 3)
    return out


def _xor_range(vals: bytes) -> int:
    x = vals[0]
    for b in vals[1:]:
        x ^= b
    return x & 0xFF

def speaker_query() -> bytes:
    return bytes.fromhex('6b059f9a43')

def speaker_set(power_on: int, voice_prompts: int) -> bytes:
    body = bytes([0x07, 0x9E, 1 if power_on else 0, 1 if voice_prompts else 0])
    chk = _xor_range(body)
    return bytes([0x6B]) + body + bytes([chk, 0x43])


def _buzzer_packet(toggle_on: bool) -> bytes:
    """Buzzer switch packet from ClassicBleManager.controlBuzzerSwitch.
    toggle=1 (on): byte12=0x00
    toggle=0 (off): byte12=0x10
    """
    out = bytearray(23)
    out[0] = 0x6A
    out[1] = 0x17
    out[2] = 0xF1
    # bytes 3-11 = 0x00
    out[12] = 0x00 if toggle_on else 0x10
    # bytes 13-19 = 0x00
    out[20] = 0x01
    # XOR checksum over bytes 1..20
    chk = out[1]
    for i in range(2, 21):
        chk ^= out[i]
    out[21] = chk & 0xFF
    out[22] = 0x43
    return bytes(out)

def buzzer_off() -> bytes:
    return _buzzer_packet(False)

def buzzer_on() -> bytes:
    return _buzzer_packet(True)
