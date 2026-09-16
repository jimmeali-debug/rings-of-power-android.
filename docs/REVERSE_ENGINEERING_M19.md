# Reverse-engineering milestone 19: native audio-pack runtime

The repository now contains the first native-remaster runtime module. It loads
the version-1 audio override format from milestone 18 without depending on the
compatibility emulator or bundling the commercial ROM.

## Activation boundary

An override pack activates only after:

1. strict JSON parsing and format/version validation;
2. matching the selected ROM's SHA-256;
3. validating every asset kind, ID, safe relative path, codec, sample rate,
   channel count, duration metadata, and declared byte length;
4. opening every OGG and verifying its SHA-256.

Music IDs accept the verified selector range, normal SFX accept IDs 0-38, and
special SFX accept IDs `0x5A-0x60`. Missing entries are intentional in partial
packs and resolve to the original clean-room playback path.

## Portability and testing

The module is dependency-free Java 11 source, making its core logic usable by
Android without tying validation to an Activity or storage implementation.
`AudioAssetSource` is the boundary for a future Storage Access Framework
adapter.

Run:

```bash
bash native-remaster/test-audio-runtime.sh
```

Tests cover valid resolution, missing-entry fallback, ROM mismatch, tampered
content, unsafe paths, invalid sample rate, invalid IDs, and duplicate entries.
A dedicated GitHub Actions workflow compiles and runs the suite on JDK 17.
