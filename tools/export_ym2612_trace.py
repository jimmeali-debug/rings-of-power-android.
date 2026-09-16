#!/usr/bin/env python3
"""Export deterministic YM2612 register writes for a music selector."""

from __future__ import annotations

import argparse
import csv
import hashlib
from dataclasses import dataclass
from pathlib import Path

from decode_music_events import decode_stream
from extract_audio_resources import (
    INSTRUMENT_POINTER_TABLE,
    INSTRUMENT_VIEW_SIZE,
    MUSIC_POINTERS,
    Z80_DRIVER_SIZE,
    Z80_DRIVER_START,
)
from extract_type1_resources import EXPECTED_SHA256

REGISTER_ORDER = (
    0x30, 0x38, 0x34, 0x3C,
    0x40, 0x48, 0x44, 0x4C,
    0x50, 0x58, 0x54, 0x5C,
    0x60, 0x68, 0x64, 0x6C,
    0x70, 0x78, 0x74, 0x7C,
    0x80, 0x88, 0x84, 0x8C,
    0xA4, 0xA0, 0xB0, 0xB4,
)
VOICE_ROUTES = (
    (0, 0, 0),
    (0, 1, 1),
    (0, 2, 2),
    (1, 0, 4),
    (1, 1, 5),
)
PITCH_LOW_TABLE = 0x03C2
PITCH_HIGH_TABLE = 0x0416
INITIAL_PROGRAM_MAP = 0x014D

# channel-9 note: (patch pointer, A0 low byte, A4 high byte, patch label)
PERCUSSION = {
    0x24: (0x0B96, 0x36, 0x04, "percussion-a"),
    0x29: (0x0B96, 0x36, 0x04, "percussion-a"),
    0x31: (0x0BBA, 0x57, 0x76, "percussion-b"),
    0x33: (0x0BBA, 0x57, 0x76, "percussion-b"),
    0x2E: (0x0BBA, 0x57, 0x76, "percussion-b"),
    0x36: (0x0BDE, 0x17, 0x17, "percussion-c"),
    0x30: (0x0BDE, 0x17, 0x17, "percussion-c"),
    0x2A: (0x0BDE, 0x17, 0x17, "percussion-c"),
    0x2C: (0x0BDE, 0x17, 0x17, "percussion-c"),
    0x26: (0x0C02, 0x36, 0x24, "percussion-d"),
    0x2D: (0x0C02, 0x36, 0x24, "percussion-d"),
}


@dataclass
class VoiceOwner:
    logical_channel: int
    note: int


def program_pointer(driver: bytes, program: int) -> int:
    offset = INSTRUMENT_POINTER_TABLE + program * 2
    return int.from_bytes(driver[offset : offset + 2], "little")


def note_frequency(driver: bytes, note: int) -> tuple[int, int]:
    normalized = note
    while normalized < 12:
        normalized += 12
    while normalized >= 96:
        normalized -= 12
    index = normalized - 12
    return driver[PITCH_LOW_TABLE + index], driver[PITCH_HIGH_TABLE + index]


def velocity_total_level(raw: int, velocity: int) -> int:
    attenuation = 16 - ((velocity + 7) >> 3)
    result = (raw + attenuation) & 0xFF
    return 0x7F if result & 0x80 else result


def carrier_offsets(algorithm: int) -> set[int]:
    result = {0x13}
    if algorithm >= 4:
        result.add(0x12)
    if algorithm >= 5:
        result.add(0x11)
    if algorithm == 7:
        result.add(0x10)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("selector", type=int, choices=sorted(MUSIC_POINTERS))
    parser.add_argument("output_file", type=Path)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    rom = args.rom.read_bytes()
    digest = hashlib.sha256(rom).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")
    driver = rom[Z80_DRIVER_START : Z80_DRIVER_START + Z80_DRIVER_SIZE]
    initial_programs = list(driver[INITIAL_PROGRAM_MAP : INITIAL_PROGRAM_MAP + 16])
    if initial_programs != list(range(16)):
        raise ValueError(f"Unexpected initial program map: {initial_programs}")

    start = MUSIC_POINTERS[args.selector]
    end = int.from_bytes(rom[start - 4 : start], "big")
    events, duration, _ = decode_stream(rom[start:end])

    free_voices = [0, 1, 2, 3, 4]
    active_voices: list[int] = []
    owners: list[VoiceOwner | None] = [None] * 5
    channel_programs = initial_programs.copy()
    rows: list[dict[str, object]] = []
    sequence = 0

    def emit(tick: int, event_index: int, action: str, logical_channel: int | str,
             note: int | str, velocity: int | str, voice: int | str,
             port: int | str, address: int | str, value: int | str, source: str) -> None:
        nonlocal sequence
        rows.append(
            {
                "tick": tick,
                "sequence": sequence,
                "event_index": event_index,
                "action": action,
                "logical_channel": logical_channel,
                "note": note,
                "velocity": velocity,
                "voice": voice,
                "port": port,
                "address": "" if address == "" else f"0x{int(address):02X}",
                "value": "" if value == "" else f"0x{int(value):02X}",
                "source": source,
            }
        )
        sequence += 1

    def key_off(tick: int, event_index: int, voice: int, action: str, source: str) -> None:
        owner = owners[voice]
        _, _, key_code = VOICE_ROUTES[voice]
        emit(
            tick, event_index, action,
            "" if owner is None else owner.logical_channel,
            "" if owner is None else owner.note,
            "", voice, 0, 0x28, key_code, source,
        )

    def release_voice(tick: int, event_index: int, channel: int, note: int, source: str) -> bool:
        for voice, owner in enumerate(owners):
            if owner is not None and owner.logical_channel == channel and owner.note == note:
                key_off(tick, event_index, voice, "key_off", source)
                owners[voice] = None
                if voice in active_voices:
                    active_voices.remove(voice)
                if voice in free_voices:
                    free_voices.remove(voice)
                free_voices.append(voice)
                return True
        return False

    def allocate_voice(tick: int, event_index: int) -> int:
        if not free_voices:
            stolen = active_voices[0]
            key_off(tick, event_index, stolen, "steal_key_off", "oldest-active")
            owners[stolen] = None
            active_voices.remove(stolen)
            free_voices.append(stolen)
        voice = free_voices.pop(0)
        if voice in active_voices:
            active_voices.remove(voice)
        active_voices.append(voice)
        return voice

    def write_patch(tick: int, event_index: int, channel: int, note: int, velocity: int,
                    voice: int, pointer: int, pitch_low: int, pitch_high: int, source: str) -> None:
        view = driver[pointer : pointer + INSTRUMENT_VIEW_SIZE]
        if len(view) != INSTRUMENT_VIEW_SIZE:
            raise ValueError(f"Truncated patch view at 0x{pointer:04X}")
        port, channel_offset, key_code = VOICE_ROUTES[voice]
        algorithm = view[0x2C] & 7
        carrier_tl = carrier_offsets(algorithm)
        for register in REGISTER_ORDER:
            value = view[register // 4]
            if register == 0xA0:
                value = pitch_low
            elif register == 0xA4:
                value = pitch_high
            elif 0x40 <= register <= 0x4C and register // 4 in carrier_tl:
                value = velocity_total_level(value, velocity)
            emit(
                tick, event_index, "register_write", channel, note, velocity,
                voice, port, register + channel_offset, value, source,
            )
        emit(tick, event_index, "key_on", channel, note, velocity, voice, 0, 0x28, 0xF0 | key_code, source)

    for event_index, event in enumerate(events):
        if event.kind == "program_change":
            channel_programs[event.channel] = event.data1
            emit(event.tick, event_index, "program_change", event.channel, "", "", "", "", "", "", f"program-{event.data1}")
            continue
        if event.kind == "note_off":
            if not release_voice(event.tick, event_index, event.channel, event.data1, "event"):
                emit(event.tick, event_index, "unmatched_note_off", event.channel, event.data1, event.data2 or 0, "", "", "", "", "event")
            continue

        velocity = event.data2 or 0
        if event.channel == 9:
            percussion = PERCUSSION.get(event.data1)
            if percussion is None:
                emit(event.tick, event_index, "ignored_percussion_note", event.channel, event.data1, velocity, "", "", "", "", "channel-9")
                continue
            pointer, pitch_low, pitch_high, source = percussion
        else:
            program = channel_programs[event.channel]
            pointer = program_pointer(driver, program)
            pitch_low, pitch_high = note_frequency(driver, event.data1)
            source = f"program-{program}"
        voice = allocate_voice(event.tick, event_index)
        owners[voice] = VoiceOwner(event.channel, event.data1)
        write_patch(event.tick, event_index, event.channel, event.data1, velocity, voice, pointer, pitch_low, pitch_high, source)

    for voice in reversed(range(5)):
        key_off(duration, len(events), voice, "end_key_off", "selector-end")

    args.output_file.parent.mkdir(parents=True, exist_ok=True)
    with args.output_file.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys(), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
