# Reverse-engineering milestone 1: text architecture

This document records facts verified against the owner-supplied
*Rings of Power (USA, Europe)* Genesis ROM. The ROM itself and extracted
copyrighted text are intentionally excluded from this repository.

## Verified ROM identity

- SHA-256: `36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5`
- Size: 1,048,576 bytes
- Product code: `GM T-50176 -00`
- Stored/computed Genesis checksum: `0x8B9F`

## Indexed dialogue dictionary

The game stores a large concatenated ASCII dictionary at ROM offset
`0x0D6EBA`. A big-endian 16-bit boundary table starts at `0x0DC0C2`.

Reading ascending boundaries until the first decrease yields:

- 3,467 boundaries
- 3,466 dictionary entries
- final relative boundary `0x5206`
- dictionary text size: 20,998 bytes
- dictionary end: `0x0DC0C0`, immediately before the table header/offset area

For an entry `n`, its bytes are:

```text
ROM[0x0D6EBA + boundary[n] : 0x0D6EBA + boundary[n + 1]]
```

The first entries decode cleanly as ordinary words and phrases, including
articles, locations, character classes, and description fragments. This is
strong evidence that much of the game's prose uses tokenized word indices.

The ROM contains 32-bit references to the dictionary base at:

- `0x0137C0`
- `0x0137EC`
- `0x0137FE`

Those references make the code around `0x0137C0` the leading candidate for
the dictionary/token decoder.

Run the extractor against a legally obtained matching ROM:

```bash
python3 tools/extract_rom_dictionary.py game.bin -o dictionary.tsv
```

## Direct ASCII text

Not all strings use the indexed dictionary. Verified direct strings include:

- defeat/retry prose near `0x01215E`
- UI labels around `0x015BE1`
- class/status text around `0x01A710`
- combat/reward prose around `0x0293F0`
- store messages around `0x029651`

A remaster extractor therefore needs two paths: indexed token reconstruction
and direct-string discovery.

## Next analysis targets

1. Disassemble and annotate the code around `0x0137C0`.
2. Identify the token stream format and sentence/paragraph terminators.
3. Associate token streams with map objects, NPCs, menus, and quest states.
4. Locate font tile data and character-to-tile mappings.
5. Add deterministic extraction tests without committing copyrighted output.
