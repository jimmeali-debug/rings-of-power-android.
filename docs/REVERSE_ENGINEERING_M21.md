# Reverse-engineering milestone 21: verified audio playback bridge

The native audio runtime can now open an activated override as a playback-ready
stream while preserving an explicit original-audio fallback.

## Playback handle

`AudioOverrideManager.openOverride(kind, id)` returns an optional
`OpenedAudioOverride`. A present handle owns both the validated manifest entry
and its `InputStream`; callers close the handle after handing the stream to the
decoder. An empty result means the engine must use its original Genesis audio
path.

The verified pack retains the `AudioAssetSource` used at activation, so Android
document-provider streams are opened only for entries that passed the complete
manifest, ROM hash, byte-length, and SHA-256 checks.

## Replacement ordering

Manifest and asset verification remains outside the manager's playback lock,
so a large candidate pack cannot stall current playback while it is checked.
The final active-pack swap, deactivation, and playback stream opening share a
short synchronized boundary. This guarantees that replacement cannot release
the old Android tree permission between selecting an old entry and opening its
stream.

Already-open streams remain owned by their playback handles. New requests see
either the old complete pack or the new complete pack, never a partially
verified state.

## Verification

The portable runtime test now checks override metadata and bytes, absent and
inactive fallback, closeable playback handles, and replacement waiting for an
in-progress stream open. The Android provider simulation also opens the real
document-backed playback stream. Both suites continue to compile under Java 11,
and CI compiles the production adapter against Android API 35.
