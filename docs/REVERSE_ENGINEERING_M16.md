# Reverse-engineering milestone 16: first offline WAV renderer

The verified audio pipeline now produces playable WAV files from an
owner-supplied ROM in one command. The renderer exports the music events,
reproduces Z80 voice allocation, generates ordered YM2612 writes, packages
those writes as VGM, and renders the chip stream through FFmpeg's Game Music
Emu backend.

## Render command

```bash
python3 tools/render_music_wav.py game.bin 8 selector-08.wav
```

The selector must be one of the 19 verified selector numbers. Output is stereo
16-bit PCM at 44,100 Hz. FFmpeg must include the `libgme` input demuxer; the tool
reports a clear failure if the dependency is missing or cannot read the VGM.

For register-level inspection, retain the intermediate files:

```bash
python3 tools/render_music_wav.py game.bin 8 selector-08.wav \
  --keep-intermediates decoded-selector-08
```

## Duration handling

The VGM header's total sample count is derived from the original 60 Hz driver
timeline at exactly 735 output samples per tick. Some Game Music Emu builds
return a slightly shorter decoded stream because of synthesis buffering. The
wrapper pads only the tail and constrains the WAV to the verified header
duration, preserving the track timeline without stretching or resampling it.

## Scope

This is the first clean-room reference renderer, not the enhanced soundtrack.
It renders FM music and channel-9 FM percussion. The separately decoded DAC
effects are gameplay-triggered rather than embedded music events and are not
mixed into selector WAVs without a gameplay event timeline.

Generated TSV, VGM, and WAV files remain outside source control.

## Next analysis targets

1. Capture matching selector audio from the Android compatibility build.
2. Compare waveforms, timing, and register behavior with the offline render.
3. Add a remaster layer for replacement patches, music, and sound effects.
