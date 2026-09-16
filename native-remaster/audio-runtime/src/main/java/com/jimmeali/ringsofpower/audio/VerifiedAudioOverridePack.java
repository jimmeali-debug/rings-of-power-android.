package com.jimmeali.ringsofpower.audio;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class VerifiedAudioOverridePack {
    private final AudioOverrideManifest manifest;
    private final Map<AudioAssetKey, AudioOverrideEntry> entries;
    private final AudioAssetSource source;

    private VerifiedAudioOverridePack(
            AudioOverrideManifest manifest,
            Map<AudioAssetKey, AudioOverrideEntry> entries,
            AudioAssetSource source) {
        this.manifest = manifest;
        this.entries = Collections.unmodifiableMap(entries);
        this.source = source;
    }

    public static VerifiedAudioOverridePack verify(
            AudioOverrideManifest manifest,
            String selectedRomSha256,
            AudioAssetSource source) throws IOException {
        if (!manifest.romSha256().equals(selectedRomSha256)) {
            throw new IllegalArgumentException("Audio pack targets a different ROM revision");
        }
        Map<AudioAssetKey, AudioOverrideEntry> verified = new HashMap<>();
        for (AudioOverrideEntry entry : manifest.entries()) {
            long bytes = 0;
            MessageDigest digest = sha256();
            try (InputStream input = source.open(entry.path())) {
                if (input == null) {
                    throw new IOException("Missing override asset: " + entry.path());
                }
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, count);
                    bytes += count;
                }
            }
            if (bytes != entry.bytes()) {
                throw new IOException("Size mismatch for " + entry.path());
            }
            if (!hex(digest.digest()).equals(entry.outputSha256())) {
                throw new IOException("SHA-256 mismatch for " + entry.path());
            }
            verified.put(entry.key(), entry);
        }
        return new VerifiedAudioOverridePack(manifest, verified, source);
    }

    public AudioOverrideManifest manifest() {
        return manifest;
    }

    public Optional<AudioOverrideEntry> resolve(AudioAssetKind kind, int id) {
        return Optional.ofNullable(entries.get(new AudioAssetKey(kind, id)));
    }

    public Optional<OpenedAudioOverride> open(AudioAssetKind kind, int id) throws IOException {
        AudioOverrideEntry entry = entries.get(new AudioAssetKey(kind, id));
        if (entry == null) {
            return Optional.empty();
        }
        InputStream input = source.open(entry.path());
        if (input == null) {
            throw new IOException("Override source returned no stream: " + entry.path());
        }
        return Optional.of(new OpenedAudioOverride(entry, input));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xFF));
        }
        return result.toString();
    }
}
