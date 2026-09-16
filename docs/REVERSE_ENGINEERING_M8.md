# Reverse-engineering milestone 8: music event streams

All 19 music selectors now decode into validated, timing-preserving event
streams. The format is MIDI-like but remains in the game's native tick domain;
the tool deliberately does not assign an unverified MIDI tempo.

## Driver execution path

The Z80 routine at `0x0804` initializes the streamed music reader at Z80 RAM
`0x1600`. Every verified track starts with a `0x00` format/header byte, which
the routine skips before event processing.

The event dispatcher at `0x084C` recognizes four command families:

| Status | Operand bytes | Meaning |
| --- | ---: | --- |
| `0x8n` | note, velocity | note off on channel `n` |
| `0x9n` | note, velocity | note on on channel `n`; zero velocity is note off |
| `0xCn` | program | program/instrument change on channel `n` |
| `0xFC` | none | end of track |

After every non-terminal event, routine `0x08EA` reads one delay byte. That
delay advances the absolute tick of the following event. Routine `0x046A`
advances the streamed pointer and handles the Z80 `0x1600-0x19FF` page window.

The semantics are also confirmed by the dispatch targets: `0x079E` handles
nonzero-velocity note-on events, `0x06DA` handles note release, and the `0xCn`
path stores the selected program in the channel table.

## Validation results

- All 19 selector ranges begin with header `0x00`.
- Every parsed status belongs to the four verified families.
- Every stream reaches an explicit `0xFC` marker.
- Channels 0-9 are used across the soundtrack.
- Note values observed range from 0 through 99.
- Program values observed include 0 through 29.
- Selector ranges contain padding after `0xFC`; the decoder reports but does
  not interpret it.

Selectors 0 and 20 contain program-change timing patterns without note events.
The other selectors contain paired note streams, with selector-specific
channel and instrument assignments.

## Event decoder

Decode every selector from a matching owner-supplied ROM:

```bash
python3 tools/decode_music_events.py game.bin decoded-music
```

The command writes one TSV file per selector and a summary manifest. Each event
row preserves:

- byte offset in the native stream
- absolute native tick
- original status byte
- normalized event kind and channel
- original operands
- delay before the following event

This lossless intermediate form is suitable for later FM/PSG reconstruction
and for MIDI export once the tick-rate and instrument mappings are verified.

## Next analysis targets

1. Map program numbers to the driver's FM, PSG, and percussion patches.
2. Determine native tick frequency and tempo behavior.
3. Decode SFX record fields and special effect pointers.
4. Export a clearly labeled approximation to Standard MIDI while retaining
   the lossless native TSV as the authoritative representation.
