package com.infinitericks.wallet.core.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class CompactSize {
    private CompactSize() {
    }

    public static byte[] encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative compact size");
        }
        if (value < 0xFD) {
            return new byte[]{(byte) value};
        }
        if (value <= 0xFFFF) {
            return new byte[]{(byte) 0xFD, (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
        }
        if (value <= 0xFFFF_FFFFL) {
            return new byte[]{
                    (byte) 0xFE,
                    (byte) (value & 0xFF),
                    (byte) ((value >> 8) & 0xFF),
                    (byte) ((value >> 16) & 0xFF),
                    (byte) ((value >> 24) & 0xFF)
            };
        }
        return new byte[]{
                (byte) 0xFF,
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 32) & 0xFF),
                (byte) ((value >> 40) & 0xFF),
                (byte) ((value >> 48) & 0xFF),
                (byte) ((value >> 56) & 0xFF)
        };
    }

    public static void write(ByteArrayOutputStream out, long value) throws IOException {
        out.write(encode(value));
    }
}
