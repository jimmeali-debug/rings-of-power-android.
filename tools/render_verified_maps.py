#!/usr/bin/env python3
"""Render the two verified 40x28 primary-archive name tables."""

from __future__ import annotations

import argparse
import hashlib
from dataclasses import dataclass
from pathlib import Path

from extract_type1_resources import EXPECTED_SHA256
from render_primary_tiles import PALETTE_BANK, primary_record
from render_tilesets import decode_palette, encode_png


@dataclass(frozen=True)
class MapSpec:
    name: str
    map_record: int
    tile_record: int
    palette_index: int


# Verified from the two branches at 0x0178AA-0x01795A. The name-table blitter
# receives 40x28 dimensions and adds palette select 1 or 2 respectively.
MAPS = (
    MapSpec("screen-a", 9, 6, 11),
    MapSpec("screen-b", 35, 34, 30),
)

WIDTH_TILES = 40
HEIGHT_TILES = 28
TILE_BYTES = 32


def tile_pixels(tile: bytes) -> list[list[int]]:
    rows: list[list[int]] = []
    for y in range(8):
        row: list[int] = []
        for packed in tile[y * 4 : y * 4 + 4]:
            row.extend((packed >> 4, packed & 15))
        rows.append(row)
    return rows


def render_map(name_table: bytes, tiles: bytes, palette: list[tuple[int, int, int, int]], scale: int) -> tuple[int, int, bytes]:
    if len(name_table) != WIDTH_TILES * HEIGHT_TILES * 2:
        raise ValueError("Expected a 40x28 name table")
    tile_count = len(tiles) // TILE_BYTES
    width = WIDTH_TILES * 8
    height = HEIGHT_TILES * 8
    pixels = bytearray(width * height * 4)

    for cell in range(WIDTH_TILES * HEIGHT_TILES):
        word = int.from_bytes(name_table[cell * 2 : cell * 2 + 2], "big")
        index = word & 0x07FF
        if index >= tile_count:
            raise ValueError(f"Name-table tile {index} exceeds the {tile_count}-tile source")
        horizontal_flip = bool(word & 0x0800)
        vertical_flip = bool(word & 0x1000)
        source = tile_pixels(tiles[index * TILE_BYTES : (index + 1) * TILE_BYTES])
        cell_x = cell % WIDTH_TILES
        cell_y = cell // WIDTH_TILES
        for y in range(8):
            source_y = 7 - y if vertical_flip else y
            for x in range(8):
                source_x = 7 - x if horizontal_flip else x
                color = palette[source[source_y][source_x]]
                destination = (((cell_y * 8 + y) * width) + cell_x * 8 + x) * 4
                pixels[destination : destination + 4] = bytes(color)

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
    parser.add_argument("--scale", type=int, default=2)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()
    if args.scale < 1 or args.scale > 8:
        raise SystemExit("--scale must be between 1 and 8")

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    for spec in MAPS:
        name_table = primary_record(data, spec.map_record)
        tiles = primary_record(data, spec.tile_record)
        palette_start = PALETTE_BANK + spec.palette_index * 32
        palette = decode_palette(data[palette_start : palette_start + 32], transparent_zero=False)
        width, height, rgba = render_map(name_table, tiles, palette, args.scale)
        (args.output_dir / f"{spec.name}.png").write_bytes(encode_png(width, height, rgba))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
