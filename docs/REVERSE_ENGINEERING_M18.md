# Reverse-engineering milestone 18: remaster audio override packs

Replacement music and effects now have a validated, partial-pack format for the
future Android remaster. Artists can replace one asset at a time while every
missing entry continues to use the original clean-room playback path.

## Source layout

Supported source formats are WAV, FLAC, OGG, MP3, M4A, and AAC:

```text
source/
  music/selector-08.wav
  sfx/normal-03.wav
  sfx/special-5b.wav
```

## Build

```bash
python3 tools/build_audio_override_pack.py game.bin source android-audio-pack
```

The builder recognizes all 19 music selectors, normal SFX IDs 0-38, and special
IDs `0x5A-0x60`. It converts music to stereo 48 kHz OGG Vorbis and effects to
mono 48 kHz OGG Vorbis. The resulting `audio-overrides.json` contains source
and output hashes, codecs, durations, target music duration, compatibility,
channels, sample rate, sizes, and stable IDs.

Music within 50 ms of the original selector duration is marked compatible.
Use `--strict-music-duration` to reject a mismatched track instead of recording
the difference. Packs may remain partial throughout development.

Neither source recordings nor generated packs are committed to the repository.
