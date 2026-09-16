package com.jimmeali.ringsofpower.rom;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class RomSceneDecoder {
    public static final String SUPPORTED_SHA256 =
            "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5";
    public static final int SUPPORTED_SIZE = 1_048_576;

    private static final int PRIMARY_TABLE = 0x096120;
    private static final int PRIMARY_DATA = 0x09638C;
    private static final int PALETTE_BANK = 0x0CC9D8;
    private static final int DESCRIPTOR_SIZE = 10;
    private static final int MAP_WIDTH_TILES = 40;
    private static final int MAP_HEIGHT_TILES = 28;
    private static final int TILE_BYTES = 32;
    private static final int MAX_EXPANDED_BYTES = 4 * 1024 * 1024;

    public enum Scene {
        SCREEN_A(9, 6, 11),
        SCREEN_B(35, 34, 30);

        private final int mapRecord;
        private final int tileRecord;
        private final int paletteIndex;

        Scene(int mapRecord, int tileRecord, int paletteIndex) {
            this.mapRecord = mapRecord;
            this.tileRecord = tileRecord;
            this.paletteIndex = paletteIndex;
        }
    }

    public DecodedScene decodeVerified(byte[] rom, Scene scene) {
        verifyRom(rom);
        return decodeScene(rom, scene);
    }

    public void verifyRom(byte[] rom) {
        if (rom.length != SUPPORTED_SIZE) {
            throw new IllegalArgumentException(
                    "Unsupported ROM size: " + rom.length + " bytes (expected " + SUPPORTED_SIZE + ")");
        }
        String digest = hex(sha256().digest(rom));
        if (!SUPPORTED_SHA256.equals(digest)) {
            throw new IllegalArgumentException("Unsupported ROM revision (SHA-256 " + digest + ")");
        }
    }

    DecodedScene decodeScene(byte[] rom, Scene scene) {
        if (rom == null || scene == null) {
            throw new NullPointerException("rom and scene are required");
        }
        byte[] nameTable = primaryRecord(rom, scene.mapRecord);
        byte[] tiles = primaryRecord(rom, scene.tileRecord);
        if (nameTable.length != MAP_WIDTH_TILES * MAP_HEIGHT_TILES * 2) {
            throw new IllegalArgumentException("Unexpected map plane size: " + nameTable.length);
        }
        if (tiles.length == 0 || tiles.length % TILE_BYTES != 0) {
            throw new IllegalArgumentException("Unexpected tile stream size: " + tiles.length);
        }
        int[] palette = palette(rom, scene.paletteIndex);
        int width = MAP_WIDTH_TILES * 8;
        int height = MAP_HEIGHT_TILES * 8;
        int[] pixels = new int[width * height];
        int tileCount = tiles.length / TILE_BYTES;

        for (int cell = 0; cell < MAP_WIDTH_TILES * MAP_HEIGHT_TILES; cell++) {
            int word = bigEndian16(nameTable, cell * 2);
            int tileIndex = word & 0x07ff;
            if (tileIndex >= tileCount) {
                throw new IllegalArgumentException(
                        "Map tile " + tileIndex + " exceeds tile count " + tileCount);
            }
            boolean horizontalFlip = (word & 0x0800) != 0;
            boolean verticalFlip = (word & 0x1000) != 0;
            int cellX = cell % MAP_WIDTH_TILES;
            int cellY = cell / MAP_WIDTH_TILES;
            int tileStart = tileIndex * TILE_BYTES;
            for (int y = 0; y < 8; y++) {
                int sourceY = verticalFlip ? 7 - y : y;
                for (int x = 0; x < 8; x++) {
                    int sourceX = horizontalFlip ? 7 - x : x;
                    int packed = tiles[tileStart + sourceY * 4 + sourceX / 2] & 0xff;
                    int colorIndex = (sourceX & 1) == 0 ? packed >>> 4 : packed & 0x0f;
                    int destination = (cellY * 8 + y) * width + cellX * 8 + x;
                    pixels[destination] = palette[colorIndex];
                }
            }
        }
        return new DecodedScene(width, height, pixels);
    }

    private static byte[] primaryRecord(byte[] rom, int record) {
        int descriptor = PRIMARY_TABLE + record * DESCRIPTOR_SIZE;
        requireRange(rom, descriptor, DESCRIPTOR_SIZE, "primary descriptor");
        int offset = bigEndian32(rom, descriptor);
        int expectedSize = bigEndian32(rom, descriptor + 4);
        int encoding = bigEndian16(rom, descriptor + 8);
        if (offset < 0 || expectedSize <= 0 || expectedSize > MAX_EXPANDED_BYTES || encoding != 1) {
            throw new IllegalArgumentException("Invalid primary descriptor " + record);
        }
        int compressedStart = PRIMARY_DATA + offset;
        requireRange(rom, compressedStart, 4, "compressed primary record");
        byte[] expanded = decompressLzss(rom, compressedStart);
        if (expanded.length != expectedSize) {
            throw new IllegalArgumentException("Primary record size mismatch " + record);
        }
        return expanded;
    }

    static byte[] decompressLzss(byte[] source, int start) {
        requireRange(source, start, 4, "type-1 header");
        int expandedSize = littleEndian32(source, start);
        if (expandedSize <= 0 || expandedSize > MAX_EXPANDED_BYTES) {
            throw new IllegalArgumentException("Invalid expanded size: " + expandedSize);
        }
        int cursor = start + 4;
        byte[] window = new byte[4096];
        Arrays.fill(window, (byte) 0x20);
        int writeCursor = 0x0fee;
        int flags = 0;
        int flagBits = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream(expandedSize);

        while (output.size() < expandedSize) {
            if (flagBits == 0) {
                requireRange(source, cursor, 1, "LZSS flags");
                flags = source[cursor++] & 0xff;
                flagBits = 8;
            }
            boolean literal = (flags & 1) != 0;
            flags >>>= 1;
            flagBits--;
            if (literal) {
                requireRange(source, cursor, 1, "LZSS literal");
                byte value = source[cursor++];
                output.write(value);
                window[writeCursor] = value;
                writeCursor = (writeCursor + 1) & 0x0fff;
                continue;
            }

            requireRange(source, cursor, 2, "LZSS back-reference");
            int first = source[cursor++] & 0xff;
            int second = source[cursor++] & 0xff;
            int readCursor = first | ((second & 0xf0) << 4);
            int runLength = (second & 0x0f) + 3;
            for (int index = 0; index < runLength && output.size() < expandedSize; index++) {
                byte value = window[readCursor];
                readCursor = (readCursor + 1) & 0x0fff;
                output.write(value);
                window[writeCursor] = value;
                writeCursor = (writeCursor + 1) & 0x0fff;
            }
        }
        return output.toByteArray();
    }

    private static int[] palette(byte[] rom, int paletteIndex) {
        int start = PALETTE_BANK + paletteIndex * 32;
        requireRange(rom, start, 32, "palette");
        int[] result = new int[16];
        for (int index = 0; index < 16; index++) {
            int word = bigEndian16(rom, start + index * 2);
            int red = expand3((word >>> 1) & 7);
            int green = expand3((word >>> 5) & 7);
            int blue = expand3((word >>> 9) & 7);
            result[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        return result;
    }

    private static int expand3(int value) {
        return (value << 5) | (value << 2) | (value >>> 1);
    }

    private static int bigEndian16(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static int bigEndian32(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static int littleEndian32(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static void requireRange(byte[] data, int offset, int length, String label) {
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IllegalArgumentException("Truncated " + label);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    public static final class DecodedScene {
        private final int width;
        private final int height;
        private final int[] argb;

        private DecodedScene(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int[] argb() {
            return argb.clone();
        }
    }
}
