# Milestone 22: installable native remaster demo

The repository now produces a standalone, signed Android demo APK that exercises
the clean-room native runtime without embedding any commercial game data.

## Playable slice

The demo presents a small top-down scene rendered directly with Android Canvas.
The player can:

- move through the tile field with the touchscreen D-pad;
- use a hardware keyboard D-pad or Android game controller;
- approach the Sage and press Action / controller A;
- trigger music selector 08 through the verified override runtime;
- stop playback, deactivate overrides, or restore the bundled demo pack;
- select an external override-pack folder through Android's system picker.

The scene, character markers, terrain colors, and bundled tone are generated
clean-room demo content. They are not extracted *Rings of Power* art or audio.

## End-to-end audio path

At build time, `tools/generate_demo_audio_pack.py` synthesizes a short stereo
48 kHz tone and encodes it as Vorbis. It writes a version-1 manifest containing
the encoded byte length and SHA-256. On device, the ordinary runtime parses and
verifies that pack before playback. Interaction with the Sage opens the
verified stream, stages it in the app cache, and plays it with Android
`MediaPlayer`.

External packs use the same manifest validator, ROM-revision boundary, asset
hashing, persistent document-tree permission, and playback bridge.

## APK build

`native-remaster/build-demo-apk.sh` uses only the JDK and Android SDK build
tools. It compiles Java 11 source, converts classes with D8, packages assets,
zip-aligns, creates an isolated debug signing key, signs, verifies, and checks
the final APK contents.

GitHub Actions publishes the APK in the `Rings-of-Power-Native-Demo` artifact
from the **Build native remaster demo APK** workflow. The APK requires Android
6.0 (API 23) or newer.

This is a working vertical slice of native rendering, controls, import, pack
verification, fallback, and audio playback. It is not yet a complete recreation
of the original game's maps, combat, quests, or save system.
