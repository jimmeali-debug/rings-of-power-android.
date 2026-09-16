#!/usr/bin/env python3
"""Decompress the two verified type-1 resource collections in the ROM."""

from __future__ import annotations

import argparse
import hashlib
from dataclasses import dataclass
from pathlib import Path

EXPECTED_SHA256 = "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5"
DESCRIPTOR_SIZE = 10
ENCODING_TYPE = 1


@dataclass(frozen=True)
class Collection:
    name: str
    table: int
    data: int
    records: int
    compressed_end: int


COLLECTIONS = (
    Collection("primary", 0x096120, 0x09638C, 62, 0x0B86AE),
    Collection("uniform_1184", 0x0B86AE, 0x0B8A32, 90, 0x0CC9D8),
)


def decompress_lzss(source: bytes) -> tuple[bytes, int]:
    """Decode the LZSS variant implemented by the 68000 routine at 0x00DD5C."""
    if len(source) < 4:
        raise ValueError("Truncated type-1 header")
    expanded_size = int.from_bytes(source[:4], "little")
    cursor = 4
    window = bytearray(b" " * 4096)
    write_cursor = 0x0FEE
    flags = 0
    flag_bits = 0
    output = bytearray()

    while len(output) < expanded_size:
        if flag_bits == 0:
            if cursor >= len(source):
                raise ValueError("Truncated flag byte")
            flags = source[cursor]
            cursor += 1
            flag_bits = 8

        literal = flags & 1
        flags >>= 1
        flag_bits -= 1
        if literal:
            if cursor >= len(source):
                raise ValueError("Truncated literal")
            value = source[cursor]
            cursor += 1
            output.append(value)
            window[write_cursor] = value
            write_cursor = (write_cursor + 1) & 0x0FFF
            continue

        if cursor + 2 > len(source):
            raise ValueError("Truncated back-reference")
        first, second = source[cursor : cursor + 2]
        cursor += 2
        read_cursor = first | ((second & 0xF0) << 4)
        run_length = (second & 0x0F) + 3
        for _ in range(run_length):
            value = window[read_cursor]
            read_cursor = (read_cursor + 1) & 0x0FFF
            output.append(value)
            window[write_cursor] = value
            write_cursor = (write_cursor + 1) & 0x0FFF
            if len(output) == expanded_size:
                break
    return bytes(output), cursor


def extract_collection(data: bytes, spec: Collection, output_dir: Path) -> list[str]:
    rows: list[str] = []
    expected_offset = 0
    collection_dir = output_dir / spec.name
    collection_dir.mkdir(parents=True, exist_ok=True)

    for record in range(spec.records):
        descriptor = spec.table + record * DESCRIPTOR_SIZE
        offset = int.from_bytes(data[descriptor : descriptor + 4], "big")
        expanded_size = int.from_bytes(data[descriptor + 4 : descriptor + 8], "big")
        encoding = int.from_bytes(data[descriptor + 8 : descriptor + 10], "big")
        if offset != expected_offset or encoding != ENCODING_TYPE:
            raise ValueError(f"Unexpected {spec.name} descriptor {record}")

        compressed_start = spec.data + offset
        expanded, consumed = decompress_lzss(data[compressed_start:])
        compressed_end = compressed_start + consumed
        if len(expanded) != expanded_size:
            raise ValueError(f"Expanded size mismatch in {spec.name} record {record}")

        if record + 1 < spec.records:
            next_descriptor = descriptor + DESCRIPTOR_SIZE
            next_offset = int.from_bytes(data[next_descriptor : next_descriptor + 4], "big")
            if offset + consumed != next_offset:
                raise ValueError(f"Compressed size mismatch in {spec.name} record {record}")
        elif compressed_end != spec.compressed_end:
            raise ValueError(f"Unexpected end of {spec.name} collection")

        filename = f"{record:03d}.bin"
        (collection_dir / filename).write_bytes(expanded)
        rows.append(
            f"{spec.name}\t{record}\t0x{descriptor:06X}\t0x{compressed_start:06X}\t"
            f"0x{compressed_end:06X}\t{consumed}\t{len(expanded)}\t"
            f"{hashlib.sha256(expanded).hexdigest()}\t{spec.name}/{filename}"
        )
        expected_offset += consumed
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    rows = [
        "collection\trecord\tdescriptor\tcompressed_start\tcompressed_end\t"
        "compressed_size\texpanded_size\tsha256\tfile"
    ]
    for spec in COLLECTIONS:
        rows.extend(extract_collection(data, spec, args.output_dir))
    (args.output_dir / "manifest.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
