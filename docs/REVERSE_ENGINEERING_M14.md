# Reverse-engineering milestone 14: voice allocation and YM2612 traces

The music decoder can now reproduce the driver's five-voice FM allocator and
export the ordered YM2612 register writes for every selector.

## Voice routing

The table at Z80 `0x00EB` maps the allocator's five voices to chip ports and
key-on channel codes:

| Voice | YM2612 port | Channel offset | Key code |
| ---: | ---: | ---: | ---: |
| 0 | 0 | 0 | 0 |
| 1 | 0 | 1 | 1 |
| 2 | 0 | 2 | 2 |
| 3 | 1 | 0 | 4 |
| 4 | 1 | 1 | 5 |

YM2612 channel 6, represented by key code 6, is excluded from the melodic
allocator because the game uses it for DAC sample playback.

## FIFO allocation and ownership

Routine `0x06A9` initializes the free queue to voices `0,1,2,3,4`. Note-on
removes the first free voice and appends it to the active queue. Note-off finds
the matching note in a 16-logical-channel by 5-voice ownership matrix, keys the
voice off, removes it from the active queue, and appends it to the free queue.

When all voices are occupied, `0x06EC` steals the oldest active voice, performs
its normal key-off/ownership cleanup, and retries the allocation. The exporter
models this exact FIFO behavior rather than assuming that MIDI channels map
directly to hardware channels.

## Channel-9 percussion

Logical channel 9 recognizes eleven note numbers and maps them to four fixed FM
patch views at Z80 `0x0B96`, `0x0BBA`, `0x0BDE`, and `0x0C02`. These notes use
the same five-voice allocator and can therefore steal or be stolen like melodic
notes. Other channel-9 note numbers are ignored by the original driver.

## Register trace

Run:

```bash
python3 tools/export_ym2612_trace.py game.bin 0 selector-00-ym2612.tsv
```

The selector must be one of the 19 verified music selectors. Each TSV row
records the tick, stable within-tick sequence, source event, allocation action,
logical channel, note, velocity, physical voice, chip port, register address,
value, and patch source.

Patch setup follows the driver's exact register order. It includes note-derived
frequency bytes, velocity-adjusted carrier total levels, feedback/algorithm,
pan/AMS/FMS, key-on, voice-steal key-off, matched note-off, and final selector
cleanup writes.

## Next analysis targets

1. Feed these traces to a YM2612 emulator core for offline WAV rendering.
2. Merge DAC writes and FM traces onto the same cycle/tick timeline.
3. Compare generated audio and register logs with emulator captures.
