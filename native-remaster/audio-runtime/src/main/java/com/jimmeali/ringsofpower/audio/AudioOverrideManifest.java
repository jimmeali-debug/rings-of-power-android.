package com.jimmeali.ringsofpower.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AudioOverrideManifest {
    public static final String FORMAT = "rings-of-power-audio-overrides";
    public static final int VERSION = 1;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_PATH =
            Pattern.compile("(?:music|sfx)/[a-z0-9][a-z0-9._-]*\\.ogg");

    private final String romSha256;
    private final boolean partialPack;
    private final List<AudioOverrideEntry> entries;
    private final Map<AudioAssetKey, AudioOverrideEntry> byKey;

    private AudioOverrideManifest(
            String romSha256, boolean partialPack, List<AudioOverrideEntry> entries) {
        this.romSha256 = romSha256;
        this.partialPack = partialPack;
        this.entries = Collections.unmodifiableList(entries);
        Map<AudioAssetKey, AudioOverrideEntry> index = new HashMap<>();
        for (AudioOverrideEntry entry : entries) {
            if (index.put(entry.key(), entry) != null) {
                throw new IllegalArgumentException("Duplicate override key: " + entry.key());
            }
        }
        this.byKey = Collections.unmodifiableMap(index);
    }

    public static AudioOverrideManifest parse(String json) {
        Map<String, Object> root = object(JsonParser.parse(json), "manifest");
        requireString(root, "format", FORMAT);
        requireInteger(root, "version", VERSION);
        String romSha256 = requireSha(root, "rom_sha256");
        boolean partialPack = requireBoolean(root, "partial_pack");
        List<Object> rawEntries = array(root.get("entries"), "entries");
        if (rawEntries.isEmpty()) {
            throw new IllegalArgumentException("Override pack must contain at least one entry");
        }

        List<AudioOverrideEntry> entries = new ArrayList<>();
        for (int index = 0; index < rawEntries.size(); index++) {
            Map<String, Object> raw = object(rawEntries.get(index), "entries[" + index + "]");
            AudioAssetKind kind = AudioAssetKind.parse(requireString(raw, "kind"));
            int id = requireInteger(raw, "id");
            AudioAssetKey key = new AudioAssetKey(kind, id);
            String path = requireString(raw, "path");
            if (!SAFE_PATH.matcher(path).matches() || path.contains("..") || path.startsWith("/")) {
                throw new IllegalArgumentException("Unsafe override path: " + path);
            }
            String expectedPrefix = kind == AudioAssetKind.MUSIC ? "music/" : "sfx/";
            if (!path.startsWith(expectedPrefix)) {
                throw new IllegalArgumentException("Path does not match kind for " + key);
            }
            String outputSha256 = requireSha(raw, "output_sha256");
            requireString(raw, "codec", "vorbis");
            requireInteger(raw, "sample_rate", 48_000);
            int channels = requireInteger(raw, "channels");
            int expectedChannels = kind == AudioAssetKind.MUSIC ? 2 : 1;
            if (channels != expectedChannels) {
                throw new IllegalArgumentException("Unexpected channel count for " + key);
            }
            double sourceDuration = requireNonNegativeDouble(raw, "source_duration_seconds");
            Double targetDuration = nullableDouble(raw.get("target_duration_seconds"), "target_duration_seconds");
            Double durationDelta = nullableDouble(raw.get("duration_delta_seconds"), "duration_delta_seconds");
            Boolean durationCompatible = nullableBoolean(raw.get("duration_compatible"), "duration_compatible");
            if (kind == AudioAssetKind.MUSIC
                    && (targetDuration == null || durationDelta == null || durationCompatible == null)) {
                throw new IllegalArgumentException("Music timing metadata is required for " + key);
            }
            if (kind != AudioAssetKind.MUSIC
                    && (targetDuration != null || durationDelta != null || durationCompatible != null)) {
                throw new IllegalArgumentException("SFX must not contain music timing metadata for " + key);
            }
            long bytes = requirePositiveLong(raw, "bytes");
            entries.add(
                    new AudioOverrideEntry(
                            key, path, outputSha256, sourceDuration, targetDuration,
                            durationDelta, durationCompatible, bytes));
        }
        return new AudioOverrideManifest(romSha256, partialPack, entries);
    }

    public String romSha256() {
        return romSha256;
    }

    public boolean partialPack() {
        return partialPack;
    }

    public List<AudioOverrideEntry> entries() {
        return entries;
    }

    public Optional<AudioOverrideEntry> find(AudioAssetKind kind, int id) {
        return Optional.ofNullable(byKey.get(new AudioAssetKey(kind, id)));
    }

    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static List<Object> array(Object value, String name) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    private static String requireString(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String) || ((String) raw).isEmpty()) {
            throw new IllegalArgumentException(key + " must be a non-empty string");
        }
        return (String) raw;
    }

    private static void requireString(Map<String, Object> value, String key, String expected) {
        if (!requireString(value, key).equals(expected)) {
            throw new IllegalArgumentException("Unsupported " + key);
        }
    }

    private static String requireSha(Map<String, Object> value, String key) {
        String result = requireString(value, key);
        if (!SHA256.matcher(result).matches()) {
            throw new IllegalArgumentException(key + " must be a lowercase SHA-256");
        }
        return result;
    }

    private static int requireInteger(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number) || ((Number) raw).doubleValue() != ((Number) raw).intValue()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return ((Number) raw).intValue();
    }

    private static void requireInteger(Map<String, Object> value, String key, int expected) {
        if (requireInteger(value, key) != expected) {
            throw new IllegalArgumentException("Unsupported " + key);
        }
    }

    private static boolean requireBoolean(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Boolean)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return (Boolean) raw;
    }

    private static double requireNonNegativeDouble(Map<String, Object> value, String key) {
        Double result = nullableDouble(value.get(key), key);
        if (result == null || !Double.isFinite(result) || result < 0) {
            throw new IllegalArgumentException(key + " must be a non-negative number");
        }
        return result;
    }

    private static Double nullableDouble(Object raw, String key) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(key + " must be a number or null");
        }
        double result = ((Number) raw).doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return result;
    }

    private static Boolean nullableBoolean(Object raw, String key) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Boolean)) {
            throw new IllegalArgumentException(key + " must be a boolean or null");
        }
        return (Boolean) raw;
    }

    private static long requirePositiveLong(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = ((Number) raw).longValue();
        if (((Number) raw).doubleValue() != result || result <= 0) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        return result;
    }
}
