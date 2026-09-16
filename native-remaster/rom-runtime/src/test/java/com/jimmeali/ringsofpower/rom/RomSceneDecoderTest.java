package com.jimmeali.ringsofpower.rom;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class RomSceneDecoderTest {
    private static final int PRIMARY_TABLE = 0x096120;
    private static final int PRIMARY_DATA = 0x09638C;
    private static final int PALETTE_BANK = 0x0CC9D8;

    public static void main(String[] args) throws Exception {
        testSyntheticScene();
        if (args.length == 1) {
            testReferenceRom(Path.of(args[0]));
        }
        System.out.println("RomSceneDecoderTest: all checks passed");
    }

    private static void testSyntheticScene() {
        byte[] rom = new byte[RomSceneDecoder.SUPPORTED_SIZE];
        byte[] tile = new byte[32];
        Arrays.fill(tile, (byte) 0x11);
        byte[] map = new byte[40 * 28 * 2];
        byte[] tileCompressed = literalBlock(tile);
        byte[] mapCompressed = literalBlock(map);
        System.arraycopy(tileCompressed, 0, rom, PRIMARY_DATA, tileCompressed.length);
        System.arraycopy(mapCompressed, 0, rom, PRIMARY_DATA + tileCompressed.length, mapCompressed.length);
        descriptor(rom, 6, 0, tile.length);
        descriptor(rom, 9, tileCompressed.length, map.length);
        int palette = PALETTE_BANK + 11 * 32;
        rom[palette + 2] = 0;
        rom[palette + 3] = 0x0e;

        RomSceneDecoder decoder = new RomSceneDecoder();
        RomSceneDecoder.DecodedScene scene =
                decoder.decodeScene(rom, RomSceneDecoder.Scene.SCREEN_A);
        check(scene.width() == 320 && scene.height() == 224, "scene dimensions");
        for (int pixel : scene.argb()) {
            check(pixel == 0xffff0000, "decoded red tile");
        }
        expectFailure(() -> decoder.verifyRom(rom));
    }

    private static void testReferenceRom(Path path) throws Exception {
        byte[] rom = Files.readAllBytes(path);
        RomSceneDecoder decoder = new RomSceneDecoder();
        RomSceneDecoder.DecodedScene first =
                decoder.decodeVerified(rom, RomSceneDecoder.Scene.SCREEN_A);
        RomSceneDecoder.DecodedScene second =
                decoder.decodeVerified(rom, RomSceneDecoder.Scene.SCREEN_B);
        check(first.width() == 320 && first.height() == 224, "reference screen A");
        check(second.width() == 320 && second.height() == 224, "reference screen B");
        check(!Arrays.equals(first.argb(), second.argb()), "reference screens differ");
    }

    private static byte[] literalBlock(byte[] expanded) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int size = expanded.length;
        output.write(size & 0xff);
        output.write((size >>> 8) & 0xff);
        output.write((size >>> 16) & 0xff);
        output.write((size >>> 24) & 0xff);
        int cursor = 0;
        while (cursor < expanded.length) {
            int count = Math.min(8, expanded.length - cursor);
            output.write((1 << count) - 1);
            output.write(expanded, cursor, count);
            cursor += count;
        }
        return output.toByteArray();
    }

    private static void descriptor(byte[] rom, int record, int offset, int size) {
        int start = PRIMARY_TABLE + record * 10;
        writeBigEndian32(rom, start, offset);
        writeBigEndian32(rom, start + 4, size);
        rom[start + 8] = 0;
        rom[start + 9] = 1;
    }

    private static void writeBigEndian32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Failed check: " + label);
        }
    }
}
