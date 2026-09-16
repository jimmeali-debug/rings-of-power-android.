# Rings of Power Android Compatibility Build

This repository builds an Android compatibility APK for playing a legally
obtained Sega Genesis copy of *Rings of Power* and hosts the clean-room
analysis tools for the later native remaster.

## What the current APK is

The APK uses the open-source
[Lemuroid](https://github.com/Swordfish90/Lemuroid) Android frontend and its
Genesis Plus GX Libretro core. It provides:

- Sega Genesis emulation
- user-selected ROM import (the commercial ROM is never stored here)
- touchscreen controls
- Bluetooth/USB controller support
- quick save/load and automatic state restoration
- display filters and control customization

This compatibility APK is the playable baseline. It is not yet the later native
remaster with replacement HD graphics and remastered audio.

## Build

GitHub Actions clones a pinned upstream Lemuroid revision, including its
submodules, and builds unsigned/debug APK artifacts. Open the repository's
**Actions** tab, select **Build compatibility APK**, and download the
`rings-of-power-compatibility-apks` artifact after the job succeeds.

## Reverse-engineering toolchain

The current deterministic tools operate only on a matching, owner-supplied ROM:

- `tools/extract_rom_dictionary.py` extracts the 3,466-entry word dictionary.
- `tools/extract_dialogue.py` reconstructs 1,184 tokenized text records.
- `tools/extract_type1_resources.py` expands 152 verified LZSS resources.
- `tools/render_tilesets.py` renders 90 palette-correct 48×48 portrait assets.
- `tools/render_primary_tiles.py` renders 34 verified primary tile streams.
- `tools/render_verified_maps.py` reconstructs two complete 40×28 map planes.
- `tools/extract_audio_resources.py` inventories the Z80 driver, music, and SFX data.
- `tools/decode_music_events.py` decodes native events and exports timing-correct MIDI.
- `tools/decode_sfx_macros.py` decodes verified instrument, note, and pitch macros.
- `tools/decode_special_dac.py` reconstructs the seven delta-coded DAC samples.
- `tools/decode_instrument_programs.py` decodes all YM2612 program parameters.
- `tools/build_audio_playback_manifest.py` joins every verified audio component.

Verified formats and code paths are recorded in
`docs/REVERSE_ENGINEERING_M1.md` through `docs/REVERSE_ENGINEERING_M13.md`.
Generated text, binaries, and PNGs stay outside source control.

## ROM boundary

No Rings of Power ROM, extracted commercial assets, maps, dialogue, music, or
sound effects may be committed to this repository. After installing the APK,
select the ROM from device storage.

The supported reference ROM has SHA-256:

`36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5`

## Licensing

The compatibility build is derived from Lemuroid and its bundled open-source
components. Their licenses and notices are included by the upstream source and
build. Any redistribution must preserve all applicable licenses and notices.
