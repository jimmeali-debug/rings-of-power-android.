#!/usr/bin/env python3
"""Render primary-archive records proven to be Genesis 4-bit tile graphics."""

from __future__ import annotations

import argparse
import hashlib
import math
from pathlib import Path

from extract_type1_resources import COLLECTIONS, EXPECTED_SHA256, decompress_lzss
from render_tilesets import decode_palette, encode_png

PRIMARY = next(spec for spec in COLLECTIONS if spec.name == "primary")
PALETTE_BANK = 0x0CC9D8
PALETTE_COUNT = 31
TILE_BYTES = 32

# Each of these records is passed to the VRAM DMA helper at 0x011A7C or
# 0x011ABC with a transfer count that exactly equals expanded_size / 32.
VERIFIED_TILE_RECORDS = (
    0, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 16, 18, 19, 20, 21, 27, 28,
    29, 30, 31, 33, 34, 36, 37, 38, 41, 45, 46, 47, 53, 56, 58, 60,
)

DEBUG_PALETTE = [
    (0, 0, 0, 0),
    (35, 35, 35, 255), (68, 68, 68, 255), (101, 101, 101, 255),
    (136, 136, 136, 255), (170, 170, 170, 255), (204, 204, 204, 255),
    (238, 238, 238, 255), (178, 34, 34, 255), (255, 140, 0, 255),
    (255, 215, 0, 255), (34, 139, 34, 255), (0, 191, 255, 255),
    (65, 105, 225, 255), (138, 43, 226, 255), (255, 105, 180, 255),
]


def primary_record(data: bytes, record: int) -> bytes:
    descriptor = PRIMARY.table + record * 10
    offset = int.from_bytes(data[descriptor : descriptor + 4], "big")
    expected_size = int.from_bytes(data[descriptor + 4 : descriptor + 8], "big")
    expanded, _ = decompress_lzss(data[PRIMARY.data + offset :])
    if len(expanded) != expected_size or len(expanded) % TILE_BYTES:
        raise ValueError(f"Unexpected primary tile record {record}")
    return expanded


def render(raw: bytes, palette: list[tuple[int, int, int, int]], columns: int, scale: int) -> tuple[int, int, bytes]:
    tile_count = len(raw) // TILE_BYTES
    rows = math.ceil(tile_count / columns)
    width = columns * 8
    height = rows * 8
    pixels = bytearray(width * height * 4)
    for tile_index in range(tile_count):
        tile_x = tile_index % columns
        tile_y = tile_index // columns
        tile = raw[tile_index * TILE_BYTES : (tile_index + 1) * TILE_BYTES]
        for y in range(8):
            for byte_x in range(4):
                packed = tile[y * 4 + byte_x]
                for half, color_index in enumerate((packed >> 4, packed & 15)):
                    x = tile_x * 8 + byte_x * 2 + half
                    destination = ((tile_y * 8 + y) * width + x) * 4
                    pixels[destination : destination + 4] = bytes(palette[color_index])
    if scale == 1:
        return width, height, bytes(pixels)
    scaled_width = width * scale
    scaled_height = height * scale
    scaled = bytearray(scaled_width * scaled_height * 4)
    for y in range(height):
        row = pixels[y * width * 4 : (y + 1) * width * 4]
        expanded_row = b"".join(row[x : x + 4] * scale for x in range(0, len(row), 4))
        for repeat in range(scale):
            start = (y * scale + repeat) * scaled_width * 4
            scaled[start : start + len(expanded_row)] = expanded_row
    return scaled_width, scaled_height, bytes(scaled)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--palette-index", type=int)
    parser.add_argument("--columns", type=int, default=16)
    parser.add_argument("--scale", type=int, default=2)
    parser.add_argument("--opaque-color-zero", action="store_true")
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()
    if args.columns < 1 or args.scale < 1 or args.scale > 16:
        raise SystemExit("columns must be positive and scale must be from 1 through 16")
    if args.palette_index is not None and not 0 <= args.palette_index < PALETTE_COUNT:
        raise SystemExit(f"--palette-index must be from 0 through {PALETTE_COUNT - 1}")

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")

    if args.palette_index is None:
        palette = DEBUG_PALETTE
        palette_name = "debug"
    else:
        start = PALETTE_BANK + args.palette_index * 32
        palette = decode_palette(data[start : start + 32], not args.opaque_color_zero)
        palette_name = f"palette-{args.palette_index:02d}"
    if args.opaque_color_zero:
        palette = list(palette)
        palette[0] = (*palette[0][:3], 255)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for record in VERIFIED_TILE_RECORDS:
        raw = primary_record(data, record)
        width, height, rgba = render(raw, palette, args.columns, args.scale)
        filename = f"primary-{record:02d}-{palette_name}.png"
        (args.output_dir / filename).write_bytes(encode_png(width, height, rgba))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
