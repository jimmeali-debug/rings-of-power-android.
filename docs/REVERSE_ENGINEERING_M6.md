# Reverse-engineering milestone 6: verified 40×28 map planes

Two complete primary-archive name tables can now be reconstructed with their
tile streams and scene palettes. The generated images remain local and are not
committed.

## Name-table blitter

The routine at `0x01194A` copies a rectangular big-endian word table into a
VDP name table. Its arguments include destination stride and position, source
width and height, a base tile/palette value, and the source pointer. It also
implements the Genesis name-table attribute bits:

- bits 0-10: tile index
- bit 11: horizontal flip
- bit 12: vertical flip
- bits 13-14: palette selection
- bit 15: priority

The two branches at `0x0178AA-0x01795A` provide exact pairings and dimensions:

| Plane | Name table | Tile stream | Palette-bank entry | Dimensions |
| --- | ---: | ---: | ---: | ---: |
| screen-a | primary record 9 | primary record 6 | 11 (`0x0CCB38`) | 40×28 |
| screen-b | primary record 35 | primary record 34 | 30 (`0x0CCD98`) | 40×28 |

Both name tables are 2,240 bytes: 40 × 28 × two bytes. Record 9 references
tiles 0-437, exactly matching the 438 tiles in record 6. Record 35 references
tiles 0-280, exactly matching the 281 tiles in record 34.

The first table uses 30 horizontally flipped and nine vertically flipped
cells. The second uses 83 horizontally flipped and 160 vertically flipped
cells. Rendering those attributes reconstructs coherent full-screen isometric
planes and validates the word interpretation.

Some areas use palette color zero because these are single VDP planes. The
original scene can place another plane beneath them; the tool intentionally
does not invent that missing composition.

## Renderer

Render both verified planes from a matching owner-supplied ROM:

```bash
python3 tools/render_verified_maps.py game.bin rendered-maps
```

The command writes `screen-a.png` and `screen-b.png` at nearest-neighbor 2×
scale by default. `--scale 1` produces the native 320×224 Genesis resolution.
The renderer validates table dimensions and every referenced tile index.

## Next analysis targets

1. Find the foreground/underlay planes composited with these two screens.
2. Trace the smaller 40-column name-table strips in primary records 13-15.
3. Identify overworld/interior map chunk descriptors outside the primary
   archive.
4. Begin sound-driver and music-table discovery.
