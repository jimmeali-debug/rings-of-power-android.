#!/usr/bin/env python3
"""Extract Rings of Power's indexed word dictionary from an owner-supplied ROM."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

EXPECTED_SHA256 = "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5"
DICTIONARY_BASE = 0x0D6EBA
BOUNDARY_TABLE = 0x0DC0C2
EXPECTED_WORDS = 3466


def read_boundaries(data: bytes) -> list[int]:
    values: list[int] = []
    cursor = BOUNDARY_TABLE
    while cursor + 2 <= len(data):
        value = int.from_bytes(data[cursor : cursor + 2], "big")
        if values and value < values[-1]:
            break
        values.append(value)
        cursor += 2
    return values


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extract the Rings of Power dictionary as tab-separated index, offsets, and text."
    )
    parser.add_argument("rom", type=Path, help="Legally obtained Rings of Power Genesis ROM")
    parser.add_argument("-o", "--output", type=Path, help="Output TSV; stdout when omitted")
    parser.add_argument(
        "--allow-unknown-revision",
        action="store_true",
        help="Attempt extraction even when the ROM hash differs",
    )
    args = parser.parse_args()

    data = args.rom.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(
            f"Unsupported ROM revision ({digest}). "
            "Use --allow-unknown-revision only after independently verifying offsets."
        )

    boundaries = read_boundaries(data)
    if len(boundaries) - 1 != EXPECTED_WORDS:
        raise SystemExit(
            f"Expected {EXPECTED_WORDS} dictionary entries, found {len(boundaries) - 1}"
        )
    if DICTIONARY_BASE + boundaries[-1] > BOUNDARY_TABLE:
        raise SystemExit("Dictionary boundaries overlap the boundary table")

    rows = ["index\tstart\tend\ttext"]
    for index, (start, end) in enumerate(zip(boundaries, boundaries[1:])):
        raw = data[DICTIONARY_BASE + start : DICTIONARY_BASE + end]
        text = raw.decode("ascii", errors="replace")
        escaped = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        rows.append(
            f"{index}\t0x{DICTIONARY_BASE + start:06X}\t"
            f"0x{DICTIONARY_BASE + end:06X}\t{escaped}"
        )

    payload = "\n".join(rows) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
