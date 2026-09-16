package com.jimmeali.ringsofpower.demo;

import android.content.res.AssetManager;

import com.jimmeali.ringsofpower.audio.AudioAssetSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

final class AssetAudioAssetSource implements AudioAssetSource {
    private static final Pattern SAFE_PATH =
            Pattern.compile("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*");

    private final AssetManager assets;

    AssetAudioAssetSource(AssetManager assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        if (relativePath == null || !SAFE_PATH.matcher(relativePath).matches()
                || relativePath.contains("..") || relativePath.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe asset path: " + relativePath);
        }
        return assets.open(relativePath, AssetManager.ACCESS_STREAMING);
    }
}
