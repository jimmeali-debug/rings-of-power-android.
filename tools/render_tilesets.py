#!/usr/bin/env python3
"""Render the 90 verified palette-plus-tile resources as PNG images."""

from __future__ import annotations

import argparse
import hashlib
import struct
import zlib
from pathlib import Path

from extract_type1_resources import COLLECTIONS, EXPECTED_SHA256, decompress_lzss

TILESET_COLLECTION = next(spec for spec in COLLECTIONS if spec.name == "uniform_1184")
PALETTE_BYTES = 32
TILE_BYTES = 32
TILES_ACROSS = 6
TILES_DOWN = 6


def genesis_channel(value: int) -> int:
    """Expand a three-bit Mega Drive color channel to eight bits."""
    return (value << 5) | (value << 2) | (value >> 1)


def decode_palette(raw: bytes, transparent_zero: bool) -> list[tuple[int, int, int, int]]:
    if len(raw) != PALETTE_BYTES:
        raise ValueError("A Genesis palette must contain 16 words")
    colors: list[tuple[int, int, int, int]] = []
    for index in range(16):
        word = int.from_bytes(raw[index * 2 : index * 2 + 2], "big")
        red = genesis_channel((word >> 1) & 7)
        green = genesis_channel((word >> 5) & 7)
        blue = genesis_channel((word >> 9) & 7)
        alpha = 0 if transparent_zero and index == 0 else 255
        colors.append((red, green, blue, alpha))
    return colors


def render_tileset(raw: bytes, scale: int, transparent_zero: bool) -> tuple[int, int, bytes]:
    expected = PALETTE_BYTES + TILES_ACROSS * TILES_DOWN * TILE_BYTES
    if len(raw) != expected:
        raise ValueError(f"Expected {expected} bytes, found {len(raw)}")
    palette = decode_palette(raw[:PALETTE_BYTES], transparent_zero)
    width = TILES_ACROSS * 8
    height = TILES_DOWN * 8
    pixels = bytearray(width * height * 4)
    tiles = raw[PALETTE_BYTES:]

    for tile_index in range(TILES_ACROSS * TILES_DOWN):
        tile_x = tile_index % TILES_ACROSS
        tile_y = tile_index // TILES_ACROSS
        tile = tiles[tile_index * TILE_BYTES : (tile_index + 1) * TILE_BYTES]
        for y in range(8):
            for byte_x in range(4):
                packed = tile[y * 4 + byte_x]
                for half, color_index in enumerate((packed >> 4, packed & 0x0F)):
                    x = byte_x * 2 + half
                    destination = ((tile_y * 8 + y) * width + tile_x * 8 + x) * 4
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


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    body = kind + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def encode_png(width: int, height: int, rgba: bytes) -> bytes:
    stride = width * 4
    scanlines = b"".join(b"\x00" + rgba[y * stride : (y + 1) * stride] for y in range(height))
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(scanlines, 9))
        + png_chunk(b"IEND", b"")
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--scale", type=int, default=4)
    parser.add_argument("--opaque-color-zero", action="store_true")
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()
    if args.scale < 1 or args.scale > 16:
        raise SystemExit("--scale must be between 1 and 16")

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    for record in range(TILESET_COLLECTION.records):
        descriptor = TILESET_COLLECTION.table + record * 10
        offset = int.from_bytes(data[descriptor : descriptor + 4], "big")
        start = TILESET_COLLECTION.data + offset
        expanded, _ = decompress_lzss(data[start:])
        width, height, rgba = render_tileset(expanded, args.scale, not args.opaque_color_zero)
        (args.output_dir / f"tileset-{record:03d}.png").write_bytes(encode_png(width, height, rgba))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
