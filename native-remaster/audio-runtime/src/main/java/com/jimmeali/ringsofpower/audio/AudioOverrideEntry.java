package com.jimmeali.ringsofpower.audio;

import java.util.Objects;

public final class AudioOverrideEntry {
    private final AudioAssetKey key;
    private final String path;
    private final String outputSha256;
    private final double sourceDurationSeconds;
    private final Double targetDurationSeconds;
    private final Double durationDeltaSeconds;
    private final Boolean durationCompatible;
    private final long bytes;

    AudioOverrideEntry(
            AudioAssetKey key,
            String path,
            String outputSha256,
            double sourceDurationSeconds,
            Double targetDurationSeconds,
            Double durationDeltaSeconds,
            Boolean durationCompatible,
            long bytes) {
        this.key = Objects.requireNonNull(key, "key");
        this.path = Objects.requireNonNull(path, "path");
        this.outputSha256 = Objects.requireNonNull(outputSha256, "outputSha256");
        this.sourceDurationSeconds = sourceDurationSeconds;
        this.targetDurationSeconds = targetDurationSeconds;
        this.durationDeltaSeconds = durationDeltaSeconds;
        this.durationCompatible = durationCompatible;
        this.bytes = bytes;
    }

    public AudioAssetKey key() {
        return key;
    }

    public String path() {
        return path;
    }

    public String outputSha256() {
        return outputSha256;
    }

    public double sourceDurationSeconds() {
        return sourceDurationSeconds;
    }

    public Double targetDurationSeconds() {
        return targetDurationSeconds;
    }

    public Double durationDeltaSeconds() {
        return durationDeltaSeconds;
    }

    public Boolean durationCompatible() {
        return durationCompatible;
    }

    public long bytes() {
        return bytes;
    }
}
