package com.jimmeali.ringsofpower.audio;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AudioOverrideManager {
    private final AtomicReference<VerifiedAudioOverridePack> active = new AtomicReference<>();

    public void activate(
            AudioOverrideManifest manifest,
            String selectedRomSha256,
            AudioAssetSource source) throws IOException {
        VerifiedAudioOverridePack verified =
                VerifiedAudioOverridePack.verify(manifest, selectedRomSha256, source);
        synchronized (this) {
            active.set(verified);
        }
    }

    public synchronized void deactivate() {
        active.set(null);
    }

    public synchronized Optional<OpenedAudioOverride> openOverride(
            AudioAssetKind kind, int id) throws IOException {
        VerifiedAudioOverridePack pack = active.get();
        return pack == null ? Optional.empty() : pack.open(kind, id);
    }

    public Optional<AudioOverrideManifest> activeManifest() {
        VerifiedAudioOverridePack pack = active.get();
        return pack == null ? Optional.empty() : Optional.of(pack.manifest());
    }

    public AudioResolution resolve(AudioAssetKind kind, int id) {
        AudioAssetKey key = new AudioAssetKey(kind, id);
        VerifiedAudioOverridePack pack = active.get();
        if (pack == null) {
            return AudioResolution.original(key);
        }
        return pack.resolve(kind, id)
                .map(AudioResolution::override)
                .orElseGet(() -> AudioResolution.original(key));
    }
}
