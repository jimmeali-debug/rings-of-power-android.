package com.jimmeali.ringsofpower.audio;

import java.util.Objects;

public final class AudioAssetKey {
    private final AudioAssetKind kind;
    private final int id;

    public AudioAssetKey(AudioAssetKind kind, int id) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (!kind.acceptsId(id)) {
            throw new IllegalArgumentException("Invalid " + kind.manifestName() + " id: " + id);
        }
        this.id = id;
    }

    public AudioAssetKind kind() {
        return kind;
    }

    public int id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AudioAssetKey)) {
            return false;
        }
        AudioAssetKey key = (AudioAssetKey) other;
        return id == key.id && kind == key.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id);
    }

    @Override
    public String toString() {
        return kind.manifestName() + ":" + id;
    }
}
