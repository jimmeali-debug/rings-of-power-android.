# Reverse-engineering milestone 17: reference soundtrack batches

All verified music selectors can now be rendered as a reproducible lossless
reference soundtrack with exact sample counts, hashes, and a machine-readable
manifest.

Run the complete lossless FLAC batch:

```bash
python3 tools/render_reference_soundtrack.py game.bin reference-soundtrack
```

For a smaller validation batch or uncompressed WAV output:

```bash
python3 tools/render_reference_soundtrack.py game.bin reference-soundtrack \
  --selectors 8 16 20 --format wav
```

Every file is checked for stereo 44.1 kHz output and an exact frame count of
735 samples per original 60 Hz tick. `soundtrack-manifest.tsv` records selector,
ticks, frames, duration, codec, byte size, SHA-256, and filename. Generated
audio and manifests remain outside source control.
