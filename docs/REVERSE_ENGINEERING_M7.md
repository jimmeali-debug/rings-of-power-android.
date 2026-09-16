# Reverse-engineering milestone 7: audio resource inventory

The sound-driver image, music selector ranges, and normal sound-effect table
are now structurally identified. The extractor preserves their native command
data; it does not mislabel proprietary sequences as WAV or MIDI.

## Z80 sound driver

Initialization routine `0x0EB806` halts and bus-requests the Genesis Z80, then
copies from ROM `0x0EBB6E` to Z80 address `0xA00000`. Register `D0` is loaded
with `0x1576` and copied with `DBRA`, giving an exact driver size of `0x1577`
(5,495) bytes. The ROM driver ends at `0x0ED0E5`.

## Music selectors

The selector dispatcher at `0x0173EC` accepts values 0-20. Selectors 18 and 19
branch directly to the return path; the other 19 selectors supply ROM pointers
to the music loader at `0x0EB88A`.

For each music pointer, the preceding four bytes hold the absolute ROM end
address. The loader stores that boundary and streams the selected range to the
Z80 in pages. This yields 19 verified selector ranges, including shared and
overlapping regions where the original dispatcher intentionally points into
related data.

## Sound-effect table

For sound IDs below `0x5A`, routine `0x017586` computes:

```text
0x0FCAE2 + sound_id * 0x16
```

This proves the dispatcher's addressing rule, but not that all 90 addressable
slots are records. Static call sites and the surrounding ROM structure verify
normal IDs 0-38: 39 records with a 22-byte stride, occupying
`0x0FCAE2-0x0FCE3C`. Bytes after record 38 transition into other tables and
68000 code. Treating the entire numeric range as an SFX bank would therefore
misclassify unrelated ROM data. The routine alternates between two Z80 effect
voices.

IDs `0x5A-0x60` use seven special hard-coded pointers and are intentionally
left out of the normal 22-byte layout. Statically observed callers use special
IDs `0x5B`, `0x5E`, `0x5F`, and `0x60`.

## Extractor

Extract the verified raw resources from a matching owner-supplied ROM:

```bash
python3 tools/extract_audio_resources.py game.bin extracted-audio
```

The command writes:

- `z80-driver.bin`
- 19 `music-XX.bin` selector ranges
- 39 `sfx-XX.bin` verified normal SFX records (IDs 0-38)
- `manifest.tsv` with ROM ranges, sizes, SHA-256 hashes, and filenames

## Next analysis targets

1. Disassemble the extracted Z80 driver and identify its channel structures.
2. Decode music commands, tempo, loops, FM patches, PSG events, and DAC cues.
3. Decode the 22-byte normal SFX records and seven special-pointer effects.
4. Build a deterministic event export before considering MIDI or remastered
   audio rendering.
