# Reverse-engineering milestone 4: portrait tilesets

The 90 uniform type-1 resources identified in milestone 3 are now verified as
palette-plus-tile image assets rather than map records. No extracted images or
ROM data are committed to the repository.

## Runtime load path

The function at `0x011AE4` performs the complete load:

1. It asks the generic resource loader for a record from descriptor table
   `0x0B86AE` and compressed-data base `0x0B8A32`.
2. The expanded 1,184-byte record is placed at RAM address `0xFF1EC0`.
3. It uploads data beginning at `0xFF1EE0`—32 bytes after the record start—to
   VRAM using the helper at `0x011B2E`.
4. The fixed count `0x24` confirms 36 tiles. At 32 bytes per Genesis 4-bit
   tile, the tile payload is 1,152 bytes.
5. It passes the first 32 bytes at `0xFF1EC0` to `0x011082`, which writes 16
   words to CRAM and therefore confirms a complete palette.

The record layout is consequently:

| Range | Size | Meaning |
| --- | ---: | --- |
| `0x000-0x01F` | 32 bytes | 16 big-endian Genesis palette words |
| `0x020-0x49F` | 1,152 bytes | 36 sequential 8×8, 4-bit tiles |

The tiles form a 6×6 grid, producing one 48×48 image. Palette-correct test
renders reconstruct recognizable character portraits and interface frames,
which independently validates the tile order and color decoding.

## Genesis color and pixel formats

Palette words use three bits per channel:

- red: bits 1-3
- green: bits 5-7
- blue: bits 9-11

Each tile contains eight rows of four bytes. The high nibble is the left pixel
and the low nibble is the right pixel, giving eight 4-bit palette indices per
row. Palette index zero can be rendered transparently for remaster asset work
or opaquely to inspect the original backdrop color.

## Renderer

Render all 90 records from a legally obtained matching ROM:

```bash
python3 tools/render_tilesets.py game.bin rendered-tilesets
```

The command writes 90 palette-correct PNG files using only the Python standard
library. Output defaults to nearest-neighbor 4× scale with palette index zero
transparent. Use `--opaque-color-zero` to retain the original color and
`--scale N` to select an integer scale from 1 through 16.

## Remaster implications

These 48×48 images can now be assigned stable record IDs, compared with their
in-game consumers, and used as exact composition references for redrawn HD
portraits. The extraction path preserves original palette, tile boundaries,
and transparency without embedding copyrighted art in source control.

## Next analysis targets

1. Trace portrait record IDs to character and dialogue identities.
2. Classify the 62 mixed primary resources into tile art, palettes, tables,
   maps, and compressed text.
3. Identify the overworld and interior map formats.
4. Locate music sequence and instrument/sample tables.
