# Reverse-engineering milestone 10: normal SFX pitch macros

The 39 verified normal sound-effect records now have a deterministic decoder.
This milestone corrects the earlier assumption that every ID accepted by the
dispatcher represented a valid table entry.

## Record layout

Each record at `0x0FCAE2 + sound_id * 0x16` is 22 bytes:

| Offset | Size | Meaning |
| --- | ---: | --- |
| `0x00` | 1 | Instrument program, 0-31 |
| `0x01` | 1 | Base note |
| `0x02` | 2 | First `(duration, signed pitch step)` pair |
| `0x04` | 18 | Up to nine further pitch pairs |

A zero duration terminates the macro. The pitch step is a signed 8-bit value
added to the current frequency once per sound-driver tick. The driver clock is
normalized to 60 Hz by the 68000-side frame update routine.

## Driver behavior

Z80 routines around `0x0A04` and `0x0A08` initialize two alternating effect
voices. Record byte 0 remaps that voice to the selected program, byte 1 starts
the base note, and the remaining pairs control pitch over time. The tick path
around `0x0A78` applies the signed pitch delta, counts down the current segment,
and loads the next pair.

Thirty-two records contain an explicit zero-duration terminator. IDs 23-28 and
30 fill the record without one; the decoder labels them `external` rather than
inventing a termination rule. Their lifetime is controlled outside the record
or by subsequent driver state.

## Decoder

Run:

```bash
python3 tools/decode_sfx_macros.py game.bin decoded-sfx
```

The command writes:

- `sfx-macros.tsv`, one row per verified normal sound ID, including the
  program, note, termination kind, total segment ticks, and decoded pairs.
- `report.txt`, a compact validation and termination summary.

No copyrighted audio samples are stored in source control. The decoder emits
only structural parameters from an owner-supplied matching ROM.

## Next analysis targets

1. Decode the seven special-pointer effects for IDs `0x5A-0x60`.
2. Assign semantics to all 36 bytes of each FM/PSG program structure.
3. Render the decoded event and patch data through a clean-room audio backend.
