# Reverse-engineering milestone 13: unified audio playback manifest

All verified audio components can now be joined into one deterministic JSON
input for a clean-room renderer. The manifest combines music events and timing,
resolved channel programs, YM2612 patches, normal SFX pitch macros, and special
DAC sample descriptors.

## Fresh-driver program state

The Z80 driver image initializes the 16-entry channel program map at `0x014D`
to the identity mapping `0, 1, ... 15`. Music program-change events update this
map, but loading a selector does not itself reset it. For deterministic
standalone playback, the manifest starts every selector from a fresh driver and
records how many note-on events occur before that channel's first explicit
program change.

This assumption is explicit in the JSON. Exact transitions between songs can
later carry the ending program map forward when the game's scene-level music
state machine is reconstructed.

## Manifest contents

Run:

```bash
python3 tools/build_audio_playback_manifest.py game.bin audio-playback-manifest.json
```

The generated version-1 manifest contains:

- the verified ROM hash and 60 Hz tick rate;
- all 32 decoded YM2612 programs and 128 operators;
- all 19 music selector streams with absolute ticks and resolved programs;
- all 39 normal SFX records with signed pitch segments;
- all seven special DAC descriptors with sizes and nominal playback rates.

Generated manifests remain outside source control. The repository stores only
the clean-room decoder and format documentation.

## Next analysis targets

1. Implement the first offline renderer against this normalized manifest.
2. Compare rendered timing and register behavior with emulator captures.
3. Add a replacement-asset layer for remastered music and sound effects.
