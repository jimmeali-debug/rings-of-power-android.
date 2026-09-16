# Reverse-engineering milestone 15: VGM export

Verified YM2612 register traces can now be packaged as standard VGM files for
playback and rendering with existing chip-audio tools.

## Timing and commands

The exporter maps every 60 Hz driver tick to VGM command `0x62`, which waits
735 samples at the VGM rate of 44,100 Hz. Within each tick, register writes
retain the stable sequence produced by the voice allocator and patch setup.

YM2612 writes use the standard commands:

- `0x52 address value` for port 0
- `0x53 address value` for port 1

The header declares the Genesis YM2612 clock as 7,670,454 Hz, VGM version 1.71,
the 60 Hz source rate, and the exact total 44.1 kHz sample duration. Selector-end
key-off writes are included before the `0x66` end command.

## Export workflow

First create a trace, then package it:

```bash
python3 tools/export_ym2612_trace.py game.bin 0 selector-00.tsv
python3 tools/export_vgm.py selector-00.tsv selector-00.vgm
```

The resulting VGM contains no ROM image and no sampled commercial audio. It is
a deterministic stream of synthesized-chip register commands derived from an
owner-supplied matching ROM and remains outside source control.

## Accuracy boundary

The VGM is tick-accurate and preserves write order within each tick. Individual
Z80 instruction-cycle gaps between patch-register writes are not yet modeled;
the writes for one event occur at the same VGM sample position. This is
sufficient for musical playback and patch verification, while later
emulator-capture comparison can determine whether sub-tick write timing needs
to be reproduced.

## Next analysis targets

1. Render the VGM files to WAV with a pinned open-source YM2612 core.
2. Compare register logs and audio against Genesis emulator captures.
3. Add optional enhanced-patch and replacement-track layers.
