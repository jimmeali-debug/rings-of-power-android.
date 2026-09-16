# Reverse-engineering milestone 2: dialogue reconstruction

This milestone turns the dictionary discovery into a deterministic text
decoder. Facts below were verified against the same owner-supplied ROM named
in milestone 1. The ROM and extracted copyrighted text remain excluded from
the repository.

## Decoder routine

The 68000 routine at `0x0136FC` reconstructs text from big-endian 16-bit
tokens. It starts at token 1; token 0 is record metadata and is not copied to
the output buffer.

For each text token:

- bits 0-13 are a one-based dictionary index
- bits 14-15 select trailing punctuation

| High bits | Appended character |
| --- | --- |
| `0x0000` | space |
| `0x4000` | comma |
| `0x8000` | period |
| `0xC000` | question mark |

The decoder suppresses the appended character when the dictionary entry is a
single space or already equals that character. Dictionary token `N` uses
boundaries `N - 1` and `N`; token zero is therefore reserved.

Key instruction ranges are:

- `0x013710-0x013736`: read token and split index/punctuation bits
- `0x013742-0x013760`: map high bits to the four characters
- `0x01377E-0x0137A8`: resolve dictionary boundaries
- `0x0137B2-0x0137DA`: copy dictionary bytes
- `0x0137E0-0x01381E`: conditionally append punctuation

## Descriptor and data tables

Dialogue descriptors occupy `0x0DDBD8-0x0E0A18`. Each descriptor is ten
bytes:

| Offset | Size | Meaning |
| --- | ---: | --- |
| `+0` | 4 | data offset, big-endian |
| `+4` | 4 | encoded byte length, big-endian |
| `+8` | 2 | encoding type |

All 1,184 structurally valid descriptors use encoding type 5. Their offsets
are contiguous. Encoded data begins at `0x0E0A18` and the final descriptor
ends at `0x0EB772`.

The generic resource loader at `0x013654` selects a descriptor by index.
Encoding type 5 dispatches to the token decoder at `0x0136FC`. A verified
caller at `0x032EC4` supplies the dialogue descriptor table and data base.

The first 16-bit value varies independently from the decoded prose. It is
preserved as `metadata` until its gameplay meaning is proven. Most records use
`0x0004`, but 117 distinct values occur.

The final descriptor contains one dictionary index outside the verified
3,466-entry dictionary. The extractor emits an explicit invalid-token marker
instead of inventing text or following the original decoder into adjacent
descriptor bytes.

## Extractor

Run the decoder against a legally obtained matching ROM:

```bash
python3 tools/extract_dialogue.py game.bin -o dialogue.tsv
```

Output contains the record number, raw metadata, ROM byte range, warnings,
and reconstructed text. The script validates the known ROM hash, descriptor
continuity, encoding type, record bounds, dictionary table, and token range.

## Other resource tables

The same generic loader is used by at least two type-1 resource collections:

- descriptor base `0x096120`, data base `0x09638C`, 62 records
- descriptor base `0x0B86AE`, data base `0x0B8A32`, 90 records

These are strong candidates for the next graphics/map extraction pass.

## Next analysis targets

1. Identify the meaning of each record's leading metadata word.
2. Trace dialogue record selectors back to map objects, NPCs, and quest state.
3. Reverse the type-1 resource decompressor called at `0x00DD5C`.
4. Locate palette, tile, tile-map, and font resources.
5. Map direct ASCII strings into the same remaster content database.
