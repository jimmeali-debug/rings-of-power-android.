# Reverse-engineering milestone 11: special DAC samples

The seven sound IDs `0x5A-0x60` are not FM/PSG macro records. They point to
delta-coded samples played through the YM2612 DAC channel. A deterministic
decoder now reconstructs their unsigned 8-bit PCM and writes playable WAVs.

## Dispatch and pointers

The 68000 dispatcher at `0x017586` selects these ROM pointers:

| Sound ID | ROM pointer |
| --- | --- |
| `0x5A` | `0x004502` |
| `0x5B` | `0x001394` |
| `0x5C` | `0x003ADA` |
| `0x5D` | `0x004500` |
| `0x5E` | `0x004504` |
| `0x5F` | `0x009A60` |
| `0x60` | `0x00AE56` |

It writes the 24-bit pointer into Z80 state at `0x016E-0x0170`. The Z80 path
at `0x0498` programs the Genesis bank window, and the playback path at
`0x01E1-0x02B1` streams the referenced sample to YM2612 DAC register `0x2A`.

## Sample structure

Each pointer addresses a six-byte header followed by packed delta data:

| Offset | Size | Meaning |
| --- | ---: | --- |
| `0x00` | 2 | Rate-search key |
| `0x02` | 2 | Big-endian stored length |
| `0x04` | 1 | Initial unsigned DAC value |
| `0x05` | 1 | Header/control byte retained in metadata |
| `0x06` | variable | Two 4-bit deltas per byte |

The compressed payload size is the stored length minus two. High nibbles play
before low nibbles. The 16 signed deltas at Z80 `0x02B2` are:

```text
-34 -21 -13 -8 -5 -3 -2 -1 0 1 2 3 5 8 13 21
```

Each delta is added with 8-bit wraparound, exactly matching the Z80 `ADD`
behavior. The rate-search routine selects a delay of 6, 10, or 17 for these
samples. WAV rates are cycle-derived nominal rates from the playback loop and
Genesis Z80 clock; the PCM files preserve decoded values independently of that
container timing.

## Decoder

Run:

```bash
python3 tools/decode_special_dac.py game.bin decoded-dac
```

The command writes seven `.pcm` files, seven mono unsigned 8-bit `.wav` files,
and `special-sfx.tsv` with pointers, header fields, sizes, driver delays, and
nominal rates. All generated audio remains outside source control.

## Next analysis targets

1. Identify the in-game meaning of every normal and special sound ID.
2. Assign semantics to all 36 bytes of each FM/PSG program structure.
3. Build a clean-room mixer that combines the decoded music, patches, macros,
   and DAC samples.
