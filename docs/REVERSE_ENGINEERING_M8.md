# Reverse-engineering milestone 8: music event streams

All 19 music selectors now decode into validated, timing-preserving event
streams. The format is MIDI-like, and the 68K-to-Z80 synchronization path
establishes a normalized 60 Hz native tick.

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

The 68K routine at `0x0EB772` updates the Z80 clock once per video frame. It
adds one tick per NTSC frame and fractional PAL compensation, producing a
normalized 60 Hz clock in both video modes. Event delays are therefore measured
in 1/60-second ticks.

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

The command writes one TSV and one format-0 Standard MIDI file per selector,
plus a summary manifest. Each TSV event row preserves:

- byte offset in the native stream
- absolute native tick
- original status byte
- normalized event kind and channel
- original operands
- delay before the following event

The MIDI files use division 60 and a 60 BPM tempo, making each MIDI tick exactly
1/60 second. Original status bytes, channels, notes, velocities, program
numbers, and ordering are retained. Program numbers refer to the game's custom
sound patches, not General MIDI instrument names; the TSV remains the
authoritative lossless representation.

## Next analysis targets

1. Map program numbers to the driver's FM, PSG, and percussion patches.
2. Decode SFX record fields and special effect pointers.
3. Map the driver's 60 Hz event timing to musical tempo changes, if any.
4. Render the custom patches through an emulated YM2612/PSG signal path.
