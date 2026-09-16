#!/usr/bin/env python3
"""Decode the seven special delta-coded DAC samples from the ROM."""

from __future__ import annotations

import argparse
import csv
import hashlib
import wave
from pathlib import Path

from extract_audio_resources import Z80_DRIVER_SIZE, Z80_DRIVER_START
from extract_type1_resources import EXPECTED_SHA256

SAMPLE_POINTERS = {
    0x5A: 0x004502,
    0x5B: 0x001394,
    0x5C: 0x003ADA,
    0x5D: 0x004500,
    0x5E: 0x004504,
    0x5F: 0x009A60,
    0x60: 0x00AE56,
}
DELTA_TABLE_OFFSET = 0x02B2
RATE_TABLE_OFFSET = 0x02CA
RATE_TABLE_ENTRIES = 0x5B
Z80_CLOCK_HZ = 3_579_545


def signed_byte(value: int) -> int:
    return value - 0x100 if value & 0x80 else value


def driver_delay(header: bytes, driver: bytes) -> int:
    """Reproduce the ordered two-byte search at Z80 0x01E8-0x020F."""
    high, low = header[:2]
    index = 0
    cursor = RATE_TABLE_OFFSET + 1
    for _ in range(RATE_TABLE_ENTRIES):
        difference = (high - driver[cursor]) & 0xFF
        if high == driver[cursor]:
            difference = (low - driver[cursor - 1]) & 0xFF
        if (high == driver[cursor] and low == driver[cursor - 1]) or not difference & 0x80:
            break
        cursor += 2
        index += 1
    return max(1, index)


def nominal_sample_rate(delay: int) -> int:
    # One compressed byte produces two DAC writes. Cycle counts come from the
    # two playback paths at Z80 0x0245-0x0295, including both delay loops.
    return round((2 * Z80_CLOCK_HZ) / (440 + 34 * delay))


def decode_payload(payload: bytes, initial: int, deltas: tuple[int, ...]) -> bytes:
    value = initial
    samples = bytearray()
    for packed in payload:
        for nibble in (packed >> 4, packed & 0x0F):
            value = (value + deltas[nibble]) & 0xFF
            samples.append(value)
    return bytes(samples)


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
    deltas = tuple(signed_byte(value) for value in driver[DELTA_TABLE_OFFSET : DELTA_TABLE_OFFSET + 16])
    expected_deltas = (-34, -21, -13, -8, -5, -3, -2, -1, 0, 1, 2, 3, 5, 8, 13, 21)
    if deltas != expected_deltas:
        raise ValueError(f"Unexpected DAC delta table: {deltas}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    rows: list[dict[str, object]] = []
    for sound_id, pointer in SAMPLE_POINTERS.items():
        header = rom[pointer : pointer + 6]
        stored_length = int.from_bytes(header[2:4], "big")
        compressed_size = stored_length - 2
        if compressed_size <= 0:
            raise ValueError(f"Special SFX 0x{sound_id:02X}: invalid stored length")
        payload_start = pointer + 6
        payload = rom[payload_start : payload_start + compressed_size]
        if len(payload) != compressed_size:
            raise ValueError(f"Special SFX 0x{sound_id:02X}: truncated payload")

        delay = driver_delay(header, driver)
        rate = nominal_sample_rate(delay)
        pcm = decode_payload(payload, header[4], deltas)
        stem = f"special-sfx-{sound_id:02x}"
        (args.output_dir / f"{stem}.pcm").write_bytes(pcm)
        with wave.open(str(args.output_dir / f"{stem}.wav"), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(1)
            output.setframerate(rate)
            output.writeframes(pcm)

        rows.append(
            {
                "sound_id": f"0x{sound_id:02X}",
                "rom_pointer": f"0x{pointer:06X}",
                "rate_key": header[:2].hex().upper(),
                "driver_delay": delay,
                "nominal_rate_hz": rate,
                "compressed_bytes": compressed_size,
                "pcm_samples": len(pcm),
                "initial_value": header[4],
                "header_byte_5": header[5],
                "wav_file": f"{stem}.wav",
            }
        )

    with (args.output_dir / "special-sfx.tsv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys(), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
