#!/usr/bin/env python3
"""Convert a verified YM2612 TSV trace to a tick-accurate VGM file."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

VGM_VERSION = 0x00000171
YM2612_CLOCK_HZ = 7_670_454
SAMPLES_PER_TICK = 735  # 44,100 Hz / 60 Hz
DATA_START = 0x100


def put_u32(header: bytearray, offset: int, value: int) -> None:
    header[offset : offset + 4] = value.to_bytes(4, "little")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("trace_tsv", type=Path)
    parser.add_argument("output_vgm", type=Path)
    args = parser.parse_args()

    with args.trace_tsv.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    if not rows:
        raise ValueError("Trace contains no rows")

    stream = bytearray()
    current_tick = 0
    previous_sequence = -1
    register_writes = 0
    for row in rows:
        tick = int(row["tick"])
        sequence = int(row["sequence"])
        if tick < current_tick or sequence <= previous_sequence:
            raise ValueError("Trace rows are not in stable chronological order")
        while current_tick < tick:
            stream.append(0x62)
            current_tick += 1
        previous_sequence = sequence

        if not row["address"]:
            continue
        port = int(row["port"])
        if port not in (0, 1):
            raise ValueError(f"Invalid YM2612 port {port}")
        address = int(row["address"], 16)
        value = int(row["value"], 16)
        stream.extend((0x52 + port, address, value))
        register_writes += 1
    stream.append(0x66)

    header = bytearray(DATA_START)
    header[0:4] = b"Vgm "
    put_u32(header, 0x08, VGM_VERSION)
    put_u32(header, 0x18, current_tick * SAMPLES_PER_TICK)
    put_u32(header, 0x24, 60)
    put_u32(header, 0x2C, YM2612_CLOCK_HZ)
    put_u32(header, 0x34, DATA_START - 0x34)
    output = header + stream
    put_u32(output, 0x04, len(output) - 4)

    args.output_vgm.parent.mkdir(parents=True, exist_ok=True)
    args.output_vgm.write_bytes(output)
    print(
        f"ticks={current_tick} samples={current_tick * SAMPLES_PER_TICK} "
        f"writes={register_writes} bytes={len(output)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
