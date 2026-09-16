#!/usr/bin/env python3
"""Decode the Z80 driver's sparse YM2612 instrument-program views."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path

from extract_audio_resources import (
    INSTRUMENT_POINTER_TABLE,
    INSTRUMENT_STRIDE,
    INSTRUMENT_VIEW_SIZE,
    INSTRUMENTS,
    Z80_DRIVER_SIZE,
    Z80_DRIVER_START,
)
from extract_type1_resources import EXPECTED_SHA256

OPERATOR_REGISTERS = {
    1: 0x30,
    2: 0x38,
    3: 0x34,
    4: 0x3C,
}


def carriers(algorithm: int) -> set[int]:
    if algorithm < 4:
        return {4}
    if algorithm == 4:
        return {2, 4}
    if algorithm in (5, 6):
        return {2, 3, 4}
    return {1, 2, 3, 4}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    rom = args.rom.read_bytes()
    digest = hashlib.sha256(rom).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")
    driver = rom[Z80_DRIVER_START : Z80_DRIVER_START + Z80_DRIVER_SIZE]
    args.output_dir.mkdir(parents=True, exist_ok=True)

    programs: list[dict[str, object]] = []
    operators: list[dict[str, object]] = []
    for program in range(INSTRUMENTS):
        table_offset = INSTRUMENT_POINTER_TABLE + program * 2
        pointer = int.from_bytes(driver[table_offset : table_offset + 2], "little")
        expected = 0x10EC + program * INSTRUMENT_STRIDE
        if pointer != expected:
            raise ValueError(f"Program {program}: expected pointer 0x{expected:04X}, got 0x{pointer:04X}")
        view = driver[pointer : pointer + INSTRUMENT_VIEW_SIZE]
        if len(view) != INSTRUMENT_VIEW_SIZE:
            raise ValueError(f"Program {program}: truncated logical view")

        b0 = view[0x2C]
        b4 = view[0x2D]
        algorithm = b0 & 0x07
        carrier_set = carriers(algorithm)
        programs.append(
            {
                "program": program,
                "z80_pointer": f"0x{pointer:04X}",
                "algorithm": algorithm,
                "feedback": (b0 >> 3) & 0x07,
                "pan_left": (b4 >> 7) & 1,
                "pan_right": (b4 >> 6) & 1,
                "ams": (b4 >> 4) & 0x03,
                "fms": b4 & 0x07,
                "carrier_operators": ",".join(map(str, sorted(carrier_set))),
                "b0_raw": f"0x{b0:02X}",
                "b4_raw": f"0x{b4:02X}",
            }
        )

        for operator, register in OPERATOR_REGISTERS.items():
            dt_mul = view[register // 4]
            total_level = view[(register + 0x10) // 4]
            rs_ar = view[(register + 0x20) // 4]
            am_d1r = view[(register + 0x30) // 4]
            d2r = view[(register + 0x40) // 4]
            d1l_rr = view[(register + 0x50) // 4]
            operators.append(
                {
                    "program": program,
                    "operator": operator,
                    "carrier": int(operator in carrier_set),
                    "detune": (dt_mul >> 4) & 0x07,
                    "multiple": dt_mul & 0x0F,
                    "total_level": total_level & 0x7F,
                    "rate_scale": (rs_ar >> 6) & 0x03,
                    "attack_rate": rs_ar & 0x1F,
                    "am_enable": (am_d1r >> 7) & 1,
                    "decay1_rate": am_d1r & 0x1F,
                    "decay2_rate": d2r & 0x1F,
                    "sustain_level": (d1l_rr >> 4) & 0x0F,
                    "release_rate": d1l_rr & 0x0F,
                    "dt_mul_raw": f"0x{dt_mul:02X}",
                    "tl_raw": f"0x{total_level:02X}",
                    "rs_ar_raw": f"0x{rs_ar:02X}",
                    "am_d1r_raw": f"0x{am_d1r:02X}",
                    "d2r_raw": f"0x{d2r:02X}",
                    "d1l_rr_raw": f"0x{d1l_rr:02X}",
                }
            )

    with (args.output_dir / "programs.tsv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=programs[0].keys(), delimiter="\t")
        writer.writeheader()
        writer.writerows(programs)
    with (args.output_dir / "operators.tsv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=operators[0].keys(), delimiter="\t")
        writer.writeheader()
        writer.writerows(operators)

    (args.output_dir / "report.txt").write_text(
        "\n".join(
            [
                f"ROM SHA-256: {digest}",
                f"Decoded programs: {len(programs)}",
                f"Decoded operators: {len(operators)}",
                "Logical view size: 46 bytes; pointer stride: 36 bytes",
                "Frequency offsets 0x28-0x29 are runtime scratch and are not patch parameters.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
