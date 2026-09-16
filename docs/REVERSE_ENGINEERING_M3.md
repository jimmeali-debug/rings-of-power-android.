# Reverse-engineering milestone 3: compressed resources

This milestone identifies and implements the game's primary type-1 resource
compression. Facts were verified against the matching owner-supplied ROM; no
ROM data or extracted assets are stored in the repository.

## Type-1 decoder

The routine at `0x00DD5C` is a 4 KiB sliding-window LZSS decoder:

- the first four source bytes are the expanded size in little-endian order
- the history window is initialized with spaces (`0x20`)
- the initial history write position is `0x0FEE`
- each flag byte describes eight items, least-significant bit first
- flag bit 1 copies one literal byte
- flag bit 0 reads a two-byte back-reference
- reference position is `first | ((second & 0xF0) << 4)`
- reference length is `(second & 0x0F) + 3`
- history positions wrap at 4,096 bytes

The implementation stops when the expanded-size header has been satisfied.
Its consumed source length exactly reaches the next descriptor offset for all
but the last resource in each collection; the last resource reaches the
verified collection boundary.

## Descriptor meaning

Type-1 resources use the same ten-byte descriptor shape identified in
milestone 2, but the fields mean:

| Offset | Size | Meaning |
| --- | ---: | --- |
| `+0` | 4 | compressed-data offset, big-endian |
| `+4` | 4 | expected expanded size, big-endian |
| `+8` | 2 | encoding type (`1`) |

The descriptor does not store compressed size. That size is recovered by
decoding until the expanded-size header is satisfied and is verified against
the next resource offset.

## Verified collections

| Collection | Descriptor table | Compressed data | Records | Compressed end |
| --- | ---: | ---: | ---: | ---: |
| primary | `0x096120` | `0x09638C` | 62 | `0x0B86AE` |
| uniform_1184 | `0x0B86AE` | `0x0B8A32` | 90 | `0x0CC9D8` |

The primary collection has mixed expanded sizes from 112 to 18,752 bytes. It
contains several highly text-like records and many records whose dimensions
and byte patterns are consistent with packed graphics or index tables.

Every record in the second collection expands to exactly 1,184 bytes (592
big-endian words). Its uniform size and word patterns make it a leading map or
layout-table candidate, but that role is not yet proven.

## Extractor

Run the extractor against a legally obtained matching ROM:

```bash
python3 tools/extract_type1_resources.py game.bin extracted-resources
```

The command produces 152 numbered binary resources plus `manifest.tsv`. The
manifest records descriptor and compressed ranges, consumed and expanded
sizes, SHA-256 hashes, and output paths. It validates every descriptor,
collection boundary, expanded size, and compressed-offset transition.

## Next analysis targets

1. Identify dimensions and semantics of the 90 uniform 1,184-byte records.
2. Separate packed 4-bit graphics, tile maps, palettes, tables, and text in
   the primary collection.
3. Trace primary-resource consumers to VRAM upload and map rendering calls.
4. Locate palette loads and associate palettes with extracted tiles.
5. Render deterministic contact sheets for visual classification.
