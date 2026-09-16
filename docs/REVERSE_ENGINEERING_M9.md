# Reverse-engineering milestone 9: program/patch bank

Music program-change values can now be resolved to a fixed bank inside the Z80
driver. The extractor preserves all 32 native logical program views.

## Program lookup

The note-on path reaches routine `0x07F0`. It uses the selected program number
to index a little-endian pointer table at Z80 address `0x0382`, then loads the
referenced structure for voice allocation and synthesis setup.

The table contains 32 pointers:

- program 0 points to Z80 address `0x10EC`
- every following pointer advances by `0x24` bytes
- program 31 points to `0x1548`

The pointer stride is 36 bytes, but the driver's sparse register indexing reads
through offset `0x2D`. Each logical view is therefore 46 bytes and overlaps the
next pointer window by ten bytes. The earlier 36-byte interpretation captured
the stride rather than the complete view. Music streams use program values
0-29; programs 30 and 31 remain available to the driver but are not selected
by the 19 decoded music streams.

The structures contain synthesis parameters consumed by the driver's YM2612
and PSG setup routines. Field-level operator/register naming is intentionally
deferred until every read offset and hardware write is mapped; no General MIDI
instrument names are inferred.

## Extractor update

`tools/extract_audio_resources.py` now follows the driver pointer table and
writes:

```text
instrument-00.bin ... instrument-31.bin
```

Each file is a complete 46-byte logical view and receives a manifest row with
its program number, overlapping ROM range, size, and SHA-256 hash. Pointer
order and 36-byte spacing are validated during extraction.

## Next analysis targets

1. Annotate each program-structure byte through its Z80 consumers.
2. Separate YM2612 FM patches from PSG/noise and percussion behavior.
3. Decode the normal SFX macro fields and their 24-byte Z80 copy window.
4. Connect program names and scene usage to the remaster audio asset plan.
