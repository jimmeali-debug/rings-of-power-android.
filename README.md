# Rings of Power Android Compatibility Build

This repository builds an Android compatibility APK for playing a legally
obtained Sega Genesis copy of *Rings of Power*.

## What this first APK is

Milestone 2 uses the open-source
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

## ROM boundary

No Rings of Power ROM, extracted commercial assets, maps, dialogue, music, or
sound effects may be committed to this repository. After installing the APK,
select the ROM from device storage.

## Licensing

The compatibility build is derived from Lemuroid and its bundled open-source
components. Their licenses and notices are included by the upstream source and
build. Any redistribution must preserve all applicable licenses and notices.
