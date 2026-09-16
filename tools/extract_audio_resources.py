#!/usr/bin/env python3
"""Extract verified raw sound-driver, music, and SFX resources from the ROM."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from extract_type1_resources import EXPECTED_SHA256

Z80_DRIVER_START = 0x0EBB6E
Z80_DRIVER_SIZE = 0x1577
SFX_TABLE_START = 0x0FCAE2
SFX_RECORDS = 90
SFX_RECORD_SIZE = 0x16
INSTRUMENT_POINTER_TABLE = 0x0382
INSTRUMENTS = 32
INSTRUMENT_SIZE = 0x24

# Selectors 18 and 19 branch directly to the return path and have no resource.
MUSIC_POINTERS = {
    0: 0x0ED0E8,
    1: 0x0EDDBA,
    2: 0x0ED4FE,
    3: 0x0EEF8C,
    4: 0x0EFBC8,
    5: 0x0FA926,
    6: 0x0F0C32,
    7: 0x0F26CC,
    8: 0x0F363E,
    9: 0x0F36AC,
    10: 0x0F5182,
    11: 0x00692E,
    12: 0x0F5DAE,
    13: 0x0F6844,
    14: 0x0F900A,
    15: 0x0F9B38,
    16: 0x0F075E,
    17: 0x0FBC7C,
    20: 0x0ED4F4,
}


def write_resource(output_dir: Path, filename: str, payload: bytes, kind: str, selector: str, start: int, end: int) -> str:
    (output_dir / filename).write_bytes(payload)
    return (
        f"{kind}\t{selector}\t0x{start:06X}\t0x{end:06X}\t{len(payload)}\t"
        f"{hashlib.sha256(payload).hexdigest()}\t{filename}"
    )


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

    rows = ["kind\tselector\trom_start\trom_end\tsize\tsha256\tfile"]
    driver_end = Z80_DRIVER_START + Z80_DRIVER_SIZE
    driver = data[Z80_DRIVER_START:driver_end]
    rows.append(
        write_resource(
            args.output_dir,
            "z80-driver.bin",
            driver,
            "z80-driver",
            "-",
            Z80_DRIVER_START,
            driver_end,
        )
    )

    for program in range(INSTRUMENTS):
        pointer_offset = INSTRUMENT_POINTER_TABLE + program * 2
        driver_offset = int.from_bytes(driver[pointer_offset : pointer_offset + 2], "little")
        expected_offset = 0x10EC + program * INSTRUMENT_SIZE
        if driver_offset != expected_offset:
            raise ValueError(f"Unexpected instrument pointer for program {program}")
        start = Z80_DRIVER_START + driver_offset
        end = start + INSTRUMENT_SIZE
        filename = f"instrument-{program:02d}.bin"
        rows.append(
            write_resource(args.output_dir, filename, data[start:end], "instrument", str(program), start, end)
        )

    for selector, start in sorted(MUSIC_POINTERS.items()):
        end = int.from_bytes(data[start - 4 : start], "big")
        if not start < end <= len(data):
            raise ValueError(f"Invalid music boundary for selector {selector}: 0x{start:X}-0x{end:X}")
        filename = f"music-{selector:02d}.bin"
        rows.append(
            write_resource(args.output_dir, filename, data[start:end], "music", str(selector), start, end)
        )

    for selector in range(SFX_RECORDS):
        start = SFX_TABLE_START + selector * SFX_RECORD_SIZE
        end = start + SFX_RECORD_SIZE
        filename = f"sfx-{selector:02d}.bin"
        rows.append(write_resource(args.output_dir, filename, data[start:end], "sfx", str(selector), start, end))

    (args.output_dir / "manifest.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
