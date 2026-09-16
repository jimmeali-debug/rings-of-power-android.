#!/usr/bin/env python3
"""Extract decoded text records from an owner-supplied Rings of Power ROM.

The script writes TSV rather than modifying the ROM.  The leading 16-bit value
in each record is preserved as ``metadata`` because its gameplay meaning has
not yet been verified.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

EXPECTED_SHA256 = "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5"
DICTIONARY_BASE = 0x0D6EBA
BOUNDARY_TABLE = 0x0DC0C2
EXPECTED_WORDS = 3466
DIALOGUE_TABLE = 0x0DDBD8
DIALOGUE_DATA = 0x0E0A18
DESCRIPTOR_SIZE = 10
ENCODING_TYPE = 5
PUNCTUATION = {0x0000: " ", 0x4000: ",", 0x8000: ".", 0xC000: "?"}


def dictionary(data: bytes) -> list[str]:
    boundaries = [
        int.from_bytes(data[pos : pos + 2], "big")
        for pos in range(BOUNDARY_TABLE, BOUNDARY_TABLE + 2 * (EXPECTED_WORDS + 1), 2)
    ]
    if boundaries != sorted(boundaries):
        raise ValueError("Dictionary boundary table is not monotonic")
    # Token zero is reserved. The 68000 decoder maps token N to boundaries
    # N-1..N, so pad the Python list to retain the ROM's one-based indexing.
    words = [""]
    for start, end in zip(boundaries, boundaries[1:]):
        words.append(data[DICTIONARY_BASE + start : DICTIONARY_BASE + end].decode("ascii"))
    return words


def decode_record(raw: bytes, words: list[str]) -> tuple[int, str, list[str]]:
    if len(raw) < 2 or len(raw) % 2:
        raise ValueError("Dialogue record must contain an even number of bytes")
    values = [int.from_bytes(raw[pos : pos + 2], "big") for pos in range(0, len(raw), 2)]
    output: list[str] = []
    warnings: list[str] = []
    for value in values[1:]:
        index = value & 0x3FFF
        if not 0 < index < len(words):
            # The final descriptor contains one out-of-range token. Preserve it
            # visibly instead of inventing text or following the original
            # decoder into the adjacent descriptor bytes.
            output.append(f"[INVALID_TOKEN_0x{index:04X}]")
            warnings.append(f"out-of-range token 0x{index:04X}")
            continue
        word = words[index]
        output.append(word)
        punctuation = PUNCTUATION[value & 0xC000]
        if word != " " and word != punctuation:
            output.append(punctuation)
    return values[0], "".join(output), warnings


def escape_tsv(value: str) -> str:
    return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")


def extract(data: bytes) -> list[str]:
    words = dictionary(data)
    descriptor_bytes = DIALOGUE_DATA - DIALOGUE_TABLE
    if descriptor_bytes % DESCRIPTOR_SIZE:
        raise ValueError("Dialogue descriptor table has an unexpected size")

    rows = ["record\tmetadata\trom_start\trom_end\twarnings\ttext"]
    expected_offset = 0
    for record in range(descriptor_bytes // DESCRIPTOR_SIZE):
        cursor = DIALOGUE_TABLE + record * DESCRIPTOR_SIZE
        offset = int.from_bytes(data[cursor : cursor + 4], "big")
        length = int.from_bytes(data[cursor + 4 : cursor + 8], "big")
        encoding = int.from_bytes(data[cursor + 8 : cursor + 10], "big")
        if offset != expected_offset or encoding != ENCODING_TYPE:
            raise ValueError(f"Unexpected descriptor at record {record}")
        start = DIALOGUE_DATA + offset
        end = start + length
        if end > len(data):
            raise ValueError(f"Record {record} extends beyond the ROM")
        metadata, text, warnings = decode_record(data[start:end], words)
        rows.append(
            f"{record}\t0x{metadata:04X}\t0x{start:06X}\t0x{end:06X}\t"
            f"{escape_tsv('; '.join(warnings))}\t{escape_tsv(text)}"
        )
        expected_offset += length
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("-o", "--output", type=Path)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")

    payload = "\n".join(extract(data)) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
