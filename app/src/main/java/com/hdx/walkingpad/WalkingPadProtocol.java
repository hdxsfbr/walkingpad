package com.hdx.walkingpad;

import java.util.Locale;

final class WalkingPadProtocol {
    static final String DEVICE_NAME = "PITPAT-T01";
    static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";
    static final String SERVICE_UUID = "0000ffff-0000-1000-8000-00805f9b34fb";
    static final String WRITE_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";
    static final String NOTIFY_UUID = "0000ff02-0000-1000-8000-00805f9b34fb";
    static final int DEFAULT_START_RAW = 994;

    private WalkingPadProtocol() {
    }

    static int mphToRaw(float mph) {
        int raw = Math.round(mph * 1600.0f);
        return Math.max(0, Math.min(raw, 0xFFFF));
    }

    static float rawToMph(int raw) {
        return raw / 1600.0f;
    }

    static byte[] makeKeepalive(int seq) {
        return new byte[]{
            0x4D, 0x00, (byte) (seq & 0xFF), 0x05, 0x6A, 0x05, (byte) 0xFD, (byte) 0xF8, 0x43
        };
    }

    static byte[] cmdStart(int seq, float speedMph) {
        byte[] out = base27(seq);
        int speed = mphToRaw(speedMph);
        out[10] = (byte) ((speed >> 8) & 0xFF);
        out[11] = (byte) (speed & 0xFF);
        out[12] = 0x05;
        out[16] = 0x0C;
        writeHex(out, 21, "000c5570");
        out[25] = xorChecksum(out, 5, 25);
        out[26] = 0x43;
        return out;
    }

    static byte[] cmdSetSpeed(int seq, float speedMph) {
        byte[] out = base27(seq);
        int speed = mphToRaw(speedMph);
        out[10] = (byte) ((speed >> 8) & 0xFF);
        out[11] = (byte) (speed & 0xFF);
        out[12] = 0x01;
        out[16] = 0x0C;
        writeHex(out, 21, "000c5570");
        out[25] = xorChecksum(out, 5, 25);
        out[26] = 0x43;
        return out;
    }

    static byte[] cmdPause(int seq, float speedMph) {
        byte[] out = base27(seq);
        int speed = mphToRaw(speedMph);
        out[10] = (byte) ((speed >> 8) & 0xFF);
        out[11] = (byte) (speed & 0xFF);
        out[12] = 0x05;
        out[16] = 0x0A;
        writeHex(out, 21, "000c5570");
        out[25] = xorChecksum(out, 5, 25);
        out[26] = 0x43;
        return out;
    }

    static byte[] cmdStop(int seq) {
        byte[] out = base27(seq);
        out[12] = 0x05;
        out[16] = 0x08;
        writeHex(out, 21, "00000000");
        out[25] = xorChecksum(out, 5, 25);
        out[26] = 0x43;
        return out;
    }

    static StatusPacket parseStatus(byte[] data) {
        StatusPacket packet = new StatusPacket();
        packet.rawHex = bytesToHex(data);
        if (data.length >= 20 && (data[0] & 0xFF) == 0x4D) {
            packet.packetType = data[3] & 0xFF;
            if (data.length >= 13) {
                int currentRaw = ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);
                int targetRaw = ((data[9] & 0xFF) << 8) | (data[10] & 0xFF);
                int maxRaw = ((data[11] & 0xFF) << 8) | (data[12] & 0xFF);
                packet.feedbackMph = rawToMph(currentRaw);
                packet.targetMph = rawToMph(targetRaw);
                packet.maxMph = rawToMph(maxRaw);
            }
        }
        return packet;
    }

    private static byte[] base27(int seq) {
        byte[] out = new byte[27];
        out[0] = 0x4D;
        out[1] = 0x00;
        out[2] = (byte) (seq & 0xFF);
        out[3] = 0x17;
        out[4] = 0x6A;
        out[5] = 0x17;
        out[14] = 0x58;
        return out;
    }

    private static byte xorChecksum(byte[] data, int startInclusive, int endExclusive) {
        int xor = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            xor ^= data[i] & 0xFF;
        }
        return (byte) (xor & 0xFF);
    }

    private static void writeHex(byte[] out, int offset, String hex) {
        for (int i = 0; i < hex.length(); i += 2) {
            out[offset + (i / 2)] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte datum : data) {
            builder.append(String.format(Locale.US, "%02x", datum & 0xFF));
        }
        return builder.toString();
    }

    static final class StatusPacket {
        int packetType = -1;
        float feedbackMph;
        float targetMph;
        float maxMph;
        String rawHex = "";
    }
}
