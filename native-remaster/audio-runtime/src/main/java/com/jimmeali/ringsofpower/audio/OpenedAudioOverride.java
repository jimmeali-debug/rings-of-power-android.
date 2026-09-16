package com.jimmeali.ringsofpower.audio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class OpenedAudioOverride implements Closeable {
    private final AudioOverrideEntry entry;
    private final InputStream input;

    OpenedAudioOverride(AudioOverrideEntry entry, InputStream input) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.input = Objects.requireNonNull(input, "input");
    }

    public AudioOverrideEntry entry() {
        return entry;
    }

    public InputStream input() {
        return input;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
