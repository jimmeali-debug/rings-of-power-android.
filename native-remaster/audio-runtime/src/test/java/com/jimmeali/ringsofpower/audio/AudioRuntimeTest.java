package com.jimmeali.ringsofpower.audio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public final class AudioRuntimeTest {
    private static final String ROM_SHA =
            "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5";

    public static void main(String[] args) throws Exception {
        byte[] music = "music-data".getBytes("UTF-8");
        byte[] effect = "effect-data".getBytes("UTF-8");
        String manifestJson = manifest(music, effect);
        AudioOverrideManifest manifest = AudioOverrideManifest.parse(manifestJson);
        check(manifest.entries().size() == 2, "entry count");
        check(manifest.partialPack(), "partial flag");
        check(manifest.find(AudioAssetKind.MUSIC, 8).isPresent(), "music lookup");
        check(!manifest.find(AudioAssetKind.MUSIC, 9).isPresent(), "fallback lookup");

        Map<String, byte[]> files = new HashMap<>();
        files.put("music/selector-08.ogg", music);
        files.put("sfx/special-5b.ogg", effect);
        VerifiedAudioOverridePack pack = VerifiedAudioOverridePack.verify(
                manifest,
                ROM_SHA,
                path -> new ByteArrayInputStream(files.get(path)));
        check(pack.resolve(AudioAssetKind.MUSIC, 8).isPresent(), "verified override");
        check(!pack.resolve(AudioAssetKind.NORMAL_SFX, 3).isPresent(), "verified fallback");

        AudioOverrideManager manager = new AudioOverrideManager();
        check(
                manager.resolve(AudioAssetKind.MUSIC, 8).mode() == AudioResolution.Mode.ORIGINAL,
                "inactive manager fallback");
        manager.activate(manifest, ROM_SHA, path -> new ByteArrayInputStream(files.get(path)));
        check(manager.activeManifest().isPresent(), "active manifest");
        check(
                manager.resolve(AudioAssetKind.MUSIC, 8).mode() == AudioResolution.Mode.OVERRIDE,
                "manager override");
        check(
                manager.resolve(AudioAssetKind.NORMAL_SFX, 3).mode() == AudioResolution.Mode.ORIGINAL,
                "manager partial fallback");
        manager.deactivate();
        check(!manager.activeManifest().isPresent(), "manager deactivate");

        expectFailure(() -> AudioOverrideManifest.parse(
                manifestJson.replace("music/selector-08.ogg", "../escape.ogg")));
        expectFailure(() -> AudioOverrideManifest.parse(
                manifestJson.replace("\"sample_rate\": 48000", "\"sample_rate\": 44100")));
        expectFailure(() -> AudioOverrideManifest.parse(
                manifestJson.replace("\"id\": 8", "\"id\": 99")));
        expectFailure(() -> AudioOverrideManifest.parse(
                manifestJson.replace("\"id\": 8", "\"id\": 18")));
        expectFailure(() -> AudioOverrideManifest.parse(
                manifestJson.replaceFirst(
                        "\"target_duration_seconds\": 1.166667",
                        "\"target_duration_seconds\": null, \"target_duration_seconds\": 1.166667")));
        expectFailure(() -> VerifiedAudioOverridePack.verify(
                manifest,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                path -> new ByteArrayInputStream(files.get(path))));
        expectIoFailure(() -> VerifiedAudioOverridePack.verify(
                manifest,
                ROM_SHA,
                path -> new ByteArrayInputStream("tampered".getBytes("UTF-8"))));

        String duplicate = manifestJson.replace(
                "\n  ]",
                ",\n    " + musicEntry(music).replace("\n", "\n    ") + "\n  ]");
        expectFailure(() -> AudioOverrideManifest.parse(duplicate));
        System.out.println("AudioRuntimeTest: all checks passed");
    }

    private static String manifest(byte[] music, byte[] effect) throws Exception {
        return "{\n"
                + "  \"format\": \"rings-of-power-audio-overrides\",\n"
                + "  \"version\": 1,\n"
                + "  \"rom_sha256\": \"" + ROM_SHA + "\",\n"
                + "  \"partial_pack\": true,\n"
                + "  \"entries\": [\n"
                + "    " + musicEntry(music).replace("\n", "\n    ") + ",\n"
                + "    {\n"
                + "      \"kind\": \"special_sfx\",\n"
                + "      \"id\": 91,\n"
                + "      \"path\": \"sfx/special-5b.ogg\",\n"
                + "      \"output_sha256\": \"" + sha(effect) + "\",\n"
                + "      \"source_duration_seconds\": 1.0,\n"
                + "      \"target_duration_seconds\": null,\n"
                + "      \"duration_delta_seconds\": null,\n"
                + "      \"duration_compatible\": null,\n"
                + "      \"sample_rate\": 48000,\n"
                + "      \"channels\": 1,\n"
                + "      \"codec\": \"vorbis\",\n"
                + "      \"bytes\": " + effect.length + "\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }

    private static String musicEntry(byte[] music) throws Exception {
        return "{\n"
                + "  \"kind\": \"music\",\n"
                + "  \"id\": 8,\n"
                + "  \"path\": \"music/selector-08.ogg\",\n"
                + "  \"output_sha256\": \"" + sha(music) + "\",\n"
                + "  \"source_duration_seconds\": 1.166667,\n"
                + "  \"target_duration_seconds\": 1.166667,\n"
                + "  \"duration_delta_seconds\": 0.0,\n"
                + "  \"duration_compatible\": true,\n"
                + "  \"sample_rate\": 48000,\n"
                + "  \"channels\": 2,\n"
                + "  \"codec\": \"vorbis\",\n"
                + "  \"bytes\": " + music.length + "\n"
                + "}";
    }

    private static String sha(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte item : digest) {
            result.append(String.format("%02x", item & 0xFF));
        }
        return result.toString();
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }

    private static void expectFailure(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectIoFailure(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            // expected
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
