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

Run its portable test suite with:

```bash
bash native-remaster/test-audio-runtime.sh
```

The next Android layer will adapt Storage Access Framework document trees to
`AudioAssetSource` and connect resolved music/SFX entries to the native mixer.
