package com.jimmeali.ringsofpower.audio.android;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import com.jimmeali.ringsofpower.audio.AudioOverrideManager;
import com.jimmeali.ringsofpower.audio.AudioOverrideManifest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class SafAudioOverrideController {
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private final ContentResolver resolver;
    private final AudioOverrideManager manager;
    private final AtomicReference<Uri> activeTree = new AtomicReference<>();

    public SafAudioOverrideController(ContentResolver resolver, AudioOverrideManager manager) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public AudioOverrideManifest activate(Uri treeUri, String selectedRomSha256) throws IOException {
        Objects.requireNonNull(treeUri, "treeUri");
        Uri existingTree = activeTree.get();
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        boolean activated = false;
        try {
            SafAudioAssetSource source = new SafAudioAssetSource(resolver, treeUri);
            String manifestText;
            try (InputStream input = source.open("audio-overrides.json")) {
                manifestText = readUtf8(input);
            }
            AudioOverrideManifest manifest = AudioOverrideManifest.parse(manifestText);
            manager.activate(manifest, selectedRomSha256, source);
            Uri previous = activeTree.getAndSet(treeUri);
            activated = true;
            if (previous != null && !previous.equals(treeUri)) {
                release(previous);
            }
            return manifest;
        } finally {
            if (!activated && (existingTree == null || !existingTree.equals(treeUri))) {
                release(treeUri);
            }
        }
    }

    public void deactivate() {
        manager.deactivate();
        Uri previous = activeTree.getAndSet(null);
        if (previous != null) {
            release(previous);
        }
    }

    public Optional<Uri> activeTreeUri() {
        return Optional.ofNullable(activeTree.get());
    }

    private void release(Uri treeUri) {
        try {
            resolver.releasePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Permission may already have been removed by the provider or user.
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            if (output.size() + count > MAX_MANIFEST_BYTES) {
                throw new IOException("Audio override manifest exceeds 1 MiB");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
