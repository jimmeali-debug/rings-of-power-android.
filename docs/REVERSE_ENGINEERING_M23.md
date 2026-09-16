# Milestone 23: ROM-backed native game scene

The native Android demo can now load real *Rings of Power* visual data from an
owner-supplied ROM without embedding or redistributing that data.

## On-device ROM boundary

The app uses Android's system document picker to select one ROM file. It reads
the file in memory, requires the exact 1 MiB size, and requires SHA-256:

`36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5`

Only a matching USA/Europe revision is accepted. The document permission is
persisted so the scene can be restored after relaunch. Replacing or rejecting a
selection releases stale permissions. The ROM and decoded pixels are never
written into the repository, APK, CI artifacts, or app-owned permanent files.

## Native resource decoder

`native-remaster/rom-runtime` ports the verified type-1 LZSS decoder and VDP
name-table renderer to dependency-free Java 11. It reads:

- primary descriptor table at `0x096120`;
- compressed primary data at `0x09638C`;
- static palette bank at `0x0CC9D8`;
- map record 35, tile record 34, palette 30 for the first displayed scene.

The decoder reconstructs the 40×28 name table at native 320×224 resolution,
including tile indices and horizontal/vertical flip attributes. Genesis
`0BBB0GGG0RRR0` palette words are expanded into opaque Android ARGB pixels.

All ROM offsets, expanded sizes, tile indices, compressed input bounds, and a
4 MiB expansion ceiling are checked before output is accepted.

## Playable integration

After decoding, the authentic scene plane is passed directly to the Canvas game
view as an in-memory bitmap. The touch, keyboard, and controller movement layer
continues to run over that plane, and the Sage interaction continues through the
verified audio-override path. Placeholder player/NPC markers remain clean-room
overlays until the corresponding original sprite and collision systems are
mapped.

If no ROM is selected—or validation fails—the app retains its clean-room scene.

## Verification

`native-remaster/test-rom-runtime.sh` builds a synthetic compressed ROM fixture
to test literal LZSS decoding, descriptors, palette conversion, scene dimensions,
pixel output, and SHA rejection without committing copyrighted bytes. With
`RINGS_OF_POWER_ROM` set locally, the same suite also verifies both real decoded
planes against an owner-supplied reference ROM.

GitHub Actions runs the copyright-clean fixture suite and builds the signed
ROM-backed APK. Local validation additionally passed against the reference ROM.
