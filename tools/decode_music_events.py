#!/usr/bin/env python3
"""Decode Rings of Power music streams into lossless, timing-preserving TSV."""

from __future__ import annotations

import argparse
import hashlib
from dataclasses import dataclass
from pathlib import Path

from extract_audio_resources import MUSIC_POINTERS
from extract_type1_resources import EXPECTED_SHA256


@dataclass(frozen=True)
class Event:
    offset: int
    tick: int
    status: int
    kind: str
    channel: int
    data1: int
    data2: int | None
    delay_after: int


def decode_stream(raw: bytes) -> tuple[list[Event], int, int]:
    if not raw or raw[0] != 0:
        raise ValueError("Expected music stream header 0x00")
    events: list[Event] = []
    cursor = 1
    tick = 0
    while cursor < len(raw):
        offset = cursor
        status = raw[cursor]
        cursor += 1
        if status == 0xFC:
            return events, tick, cursor

        family = status & 0xF0
        channel = status & 0x0F
        if family in (0x80, 0x90):
            if cursor + 3 > len(raw):
                raise ValueError(f"Truncated note event at 0x{offset:X}")
            note = raw[cursor]
            velocity = raw[cursor + 1]
            cursor += 2
            kind = "note_on" if family == 0x90 and velocity != 0 else "note_off"
            data1 = note
            data2: int | None = velocity
        elif family == 0xC0:
            if cursor + 2 > len(raw):
                raise ValueError(f"Truncated program event at 0x{offset:X}")
            kind = "program_change"
            data1 = raw[cursor]
            data2 = None
            cursor += 1
        else:
            raise ValueError(f"Unknown music status 0x{status:02X} at 0x{offset:X}")

        delay = raw[cursor]
        cursor += 1
        events.append(Event(offset, tick, status, kind, channel, data1, data2, delay))
        tick += delay
    raise ValueError("Music stream has no 0xFC end marker")


def event_tsv(events: list[Event]) -> str:
    rows = ["offset\ttick\tstatus\tkind\tchannel\tdata1\tdata2\tdelay_after"]
    for event in events:
        data2 = "" if event.data2 is None else str(event.data2)
        rows.append(
            f"0x{event.offset:04X}\t{event.tick}\t0x{event.status:02X}\t{event.kind}\t"
            f"{event.channel}\t{event.data1}\t{data2}\t{event.delay_after}"
        )
    return "\n".join(rows) + "\n"


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

    summary = [
        "selector\trom_start\trom_end\tevents\tduration_ticks\tparsed_bytes\tpadding_bytes\tchannels\tfile"
    ]
    for selector, start in sorted(MUSIC_POINTERS.items()):
        end = int.from_bytes(data[start - 4 : start], "big")
        raw = data[start:end]
        events, duration, consumed = decode_stream(raw)
        channels = ",".join(str(value) for value in sorted({event.channel for event in events}))
        filename = f"music-{selector:02d}-events.tsv"
        (args.output_dir / filename).write_text(event_tsv(events), encoding="utf-8")
        summary.append(
            f"{selector}\t0x{start:06X}\t0x{end:06X}\t{len(events)}\t{duration}\t"
            f"{consumed}\t{len(raw) - consumed}\t{channels}\t{filename}"
        )
    (args.output_dir / "manifest.tsv").write_text("\n".join(summary) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
