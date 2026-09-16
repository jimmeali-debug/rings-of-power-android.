package com.jimmeali.ringsofpower.audio;

import java.util.Objects;
import java.util.Optional;

public final class AudioResolution {
    public enum Mode {
        ORIGINAL,
        OVERRIDE
    }

    private final AudioAssetKey key;
    private final Mode mode;
    private final AudioOverrideEntry override;

    private AudioResolution(AudioAssetKey key, Mode mode, AudioOverrideEntry override) {
        this.key = Objects.requireNonNull(key, "key");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.override = override;
    }

    static AudioResolution original(AudioAssetKey key) {
        return new AudioResolution(key, Mode.ORIGINAL, null);
    }

    static AudioResolution override(AudioOverrideEntry entry) {
        return new AudioResolution(entry.key(), Mode.OVERRIDE, entry);
    }

    public AudioAssetKey key() {
        return key;
    }

    public Mode mode() {
        return mode;
    }

    public Optional<AudioOverrideEntry> override() {
        return Optional.ofNullable(override);
    }
}
