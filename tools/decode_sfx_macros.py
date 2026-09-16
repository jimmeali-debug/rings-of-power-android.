#!/usr/bin/env python3
"""Decode verified normal sound-effect pitch macros from the ROM."""

from __future__ import annotations

import argparse
import hashlib
from dataclasses import dataclass
from pathlib import Path

from extract_audio_resources import SFX_RECORDS, SFX_RECORD_SIZE, SFX_TABLE_START
from extract_type1_resources import EXPECTED_SHA256


@dataclass(frozen=True)
class Segment:
    duration: int
    pitch_step: int


@dataclass(frozen=True)
class SoundEffect:
    sound_id: int
    program: int
    note: int
    segments: tuple[Segment, ...]
    termination: str


def signed_byte(value: int) -> int:
    return value - 0x100 if value & 0x80 else value


def decode_record(sound_id: int, record: bytes) -> SoundEffect:
    if len(record) != SFX_RECORD_SIZE:
        raise ValueError(f"SFX {sound_id}: expected {SFX_RECORD_SIZE} bytes")

    program, note = record[:2]
    if program >= 32:
        raise ValueError(f"SFX {sound_id}: invalid program {program}")

    segments: list[Segment] = []
    termination = "external"
    for offset in range(2, SFX_RECORD_SIZE, 2):
        duration = record[offset]
        if duration == 0:
            termination = "zero"
            break
        segments.append(Segment(duration, signed_byte(record[offset + 1])))

    return SoundEffect(sound_id, program, note, tuple(segments), termination)


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
        "sound_id\tprogram\tnote\tsegment_count\ttermination\t"
        "total_segment_ticks\tsegments"
    ]
    effects: list[SoundEffect] = []
    for sound_id in range(SFX_RECORDS):
        start = SFX_TABLE_START + sound_id * SFX_RECORD_SIZE
        effect = decode_record(sound_id, data[start : start + SFX_RECORD_SIZE])
        effects.append(effect)
        segment_text = ",".join(
            f"{segment.duration}:{segment.pitch_step}" for segment in effect.segments
        )
        rows.append(
            f"{effect.sound_id}\t{effect.program}\t{effect.note}\t"
            f"{len(effect.segments)}\t{effect.termination}\t"
            f"{sum(segment.duration for segment in effect.segments)}\t{segment_text}"
        )

    (args.output_dir / "sfx-macros.tsv").write_text(
        "\n".join(rows) + "\n", encoding="utf-8"
    )

    zero_terminated = sum(effect.termination == "zero" for effect in effects)
    external = [effect.sound_id for effect in effects if effect.termination == "external"]
    report = [
        f"ROM SHA-256: {digest}",
        f"Decoded normal SFX: {len(effects)}",
        f"Zero-terminated macros: {zero_terminated}",
        f"No in-record terminator: {len(external)} ({','.join(map(str, external))})",
        "Pitch steps are signed 8-bit values applied once per 60 Hz driver tick.",
    ]
    (args.output_dir / "report.txt").write_text(
        "\n".join(report) + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
