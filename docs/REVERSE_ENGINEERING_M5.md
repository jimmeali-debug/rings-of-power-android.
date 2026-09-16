# Reverse-engineering milestone 5: primary tiles and palette bank

This milestone separates verified tile graphics from the mixed 62-record
primary archive and locates the ROM's static palette bank. No extracted art is
committed to the repository.

## Verified primary tile records

The primary-resource wrapper at `0x01383C` decompresses a selected record to
RAM. Calls then pass the result to the VRAM DMA helpers at `0x011A7C` or
`0x011ABC`. For the following 34 records, the transfer count exactly matches
the expanded size divided by 32 bytes per Genesis tile:

```text
0, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 16, 18, 19, 20, 21, 27,
28, 29, 30, 31, 33, 34, 36, 37, 38, 41, 45, 46, 47, 53, 56, 58, 60
```

This proves that these resources are 4-bit, 8×8 tile streams. It avoids
misclassifying map and table records whose byte lengths also happen to be
divisible by 32. Test renders reconstruct coherent terrain, interface art,
icons, and glyphs. Record 0 visibly contains the game's font and text symbols.

## Static palette bank

A contiguous bank starts at `0x0CC9D8` and contains 31 complete 16-color
palettes, ending at `0x0CCDB8`. Every word in that range conforms to the
Genesis `0BBB0GGG0RRR0` color-bit layout. Data immediately after the bank is
not palette data, giving it a clean structural boundary.

Code passes individual 32-byte entries from this range to the CRAM writer at
`0x011082`. Verified palette addresses include `0x0CCAD8`, `0x0CCAF8`,
`0x0CCB18`, and entries through `0x0CCD98`.

Scene-to-palette associations are not yet fully mapped. The renderer therefore
uses a conspicuous debug palette by default and accepts an explicit verified
palette-bank index without claiming that it belongs to a given tile record.

## Renderer

Render every proven primary tile record with the debug palette:

```bash
python3 tools/render_primary_tiles.py game.bin rendered-primary
```

Render with static palette 10 instead:

```bash
python3 tools/render_primary_tiles.py game.bin rendered-primary --palette-index 10
```

The renderer supports integer scaling, configurable contact-sheet width, and
transparent or opaque palette index zero. It uses only the Python standard
library and reuses the verified type-1 decompressor.

## Next analysis targets

1. Associate static palettes with their scene-specific primary tile records.
2. Decode records 9 and 35, both exactly 2,240 bytes, as candidate 40×28
   name tables by tracing the blitter at `0x01194A`.
3. Classify the remaining primary records as name tables, collision data,
   control tables, or text.
4. Locate sound-driver sequence and instrument tables.
