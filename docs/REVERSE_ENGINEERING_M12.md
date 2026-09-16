# Reverse-engineering milestone 12: YM2612 instrument programs

The 32 program views now decode into named YM2612 channel and operator fields.
This pass also corrects the distinction between the 36-byte pointer stride and
the 46-byte sparse logical view consumed by the driver.

## Sparse register-indexed layout

Routine `0x04CC` walks this YM2612 register list:

```text
30 38 34 3C  40 48 44 4C  50 58 54 5C
60 68 64 6C  70 78 74 7C  80 88 84 8C
A4 A0 B0 B4
```

For every register, the driver reads the program byte at `register / 4`. This
direct indexing produces the following logical offsets:

| Offsets | YM2612 meaning |
| --- | --- |
| `0x0C-0x0F` | Operator detune/multiple (`0x30` family) |
| `0x10-0x13` | Operator total level (`0x40`) |
| `0x14-0x17` | Rate scale/attack rate (`0x50`) |
| `0x18-0x1B` | AM enable/first decay rate (`0x60`) |
| `0x1C-0x1F` | Second decay rate (`0x70`) |
| `0x20-0x23` | Sustain level/release rate (`0x80`) |
| `0x28-0x29` | Runtime-generated frequency scratch (`0xA0/A4`) |
| `0x2C` | Feedback and algorithm (`0xB0`) |
| `0x2D` | Stereo pan, AMS, and FMS (`0xB4`) |

The pointer table advances by `0x24`, but the highest field is at `0x2D`.
Consequently, neighboring 46-byte logical views overlap by ten bytes. This is
intentional data packing, not a series of independent 36-byte records.

## Velocity and carrier operators

The note-on path temporarily raises total level according to velocity. It
always adjusts operator 4, then uses algorithm bits to include the additional
carrier operators:

| Algorithm | Carrier operators |
| ---: | --- |
| 0-3 | 4 |
| 4 | 2, 4 |
| 5-6 | 2, 3, 4 |
| 7 | 1, 2, 3, 4 |

The original total-level bytes are saved and restored after the register write.
Frequency bytes at offsets `0x28-0x29` are likewise generated from the note
number immediately before programming the chip; they are not fixed timbre
parameters.

## Decoder

Run:

```bash
python3 tools/decode_instrument_programs.py game.bin decoded-programs
```

The command writes:

- `programs.tsv` with algorithm, feedback, stereo pan, AMS, FMS, and carriers.
- `operators.tsv` with all four operators' envelope and frequency parameters,
  plus the original raw register bytes.
- `report.txt` with pointer and record-count validation.

The result is a deterministic clean-room parameter representation suitable for
a YM2612-compatible renderer without assigning invented General MIDI names.

## Next analysis targets

1. Connect decoded music events, programs, pitch macros, and DAC samples in one
   normalized playback manifest.
2. Validate a clean-room renderer against captured emulator audio.
3. Build replacement/remastered audio assets while retaining original timing.
