# Native remaster foundation

The native remaster is separate from the playable Lemuroid compatibility APK.
Its first runtime module consumes the version-1 audio override packs produced by
`tools/build_audio_override_pack.py`.

`audio-runtime` is dependency-free Java compatible with Android API toolchains.
It:

- strictly parses and validates the override manifest;
- rejects unsafe paths, unsupported IDs, formats, sample rates, and channels;
- requires the selected ROM revision to match the pack;
- verifies every encoded asset's byte length and SHA-256 before activation;
- resolves present overrides while leaving absent IDs on the original fallback.

`AudioOverrideManager` provides the thread-safe activation, deactivation, and
original-versus-override resolution API for the future Android mixer.
`openOverride` returns a closeable verified stream and manifest entry when an
override exists; an empty result directs the engine to its original audio path.

Run its portable test suite with:

```bash
bash native-remaster/test-audio-runtime.sh
```

The Android document-tree adapter lives in `android-storage`.
`SafAudioOverrideController`
persists a selected document-tree permission, reads the bounded manifest,
verifies every OGG through `ContentResolver`, activates atomically, and releases
failed or replaced permissions.

Run its provider simulation tests with:

```bash
bash native-remaster/test-android-storage.sh
```

Compile the production adapter against Android API 35 with:

```bash
ANDROID_HOME=/path/to/android-sdk bash native-remaster/compile-android-storage.sh
```


## Game application

`game-app` is the production Android game shell. It verifies an owner-supplied
ROM, renders connected ROM-backed areas, and persists the player's area,
position, and quest state. Build it with:

```bash
ANDROID_HOME=/path/to/android-sdk bash native-remaster/build-game-apk.sh
```

The resulting package is `com.jimmeali.ringsofpower.game`. Demo APKs are no
longer produced; future gameplay milestones extend this application.
