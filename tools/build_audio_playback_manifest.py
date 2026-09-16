#!/usr/bin/env python3
"""Build one normalized clean-room playback manifest from the verified ROM data."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from decode_instrument_programs import OPERATOR_REGISTERS, carriers
from decode_music_events import decode_stream
from decode_sfx_macros import decode_record
from decode_special_dac import SAMPLE_POINTERS, driver_delay, nominal_sample_rate
from extract_audio_resources import (
    INSTRUMENT_POINTER_TABLE,
    INSTRUMENT_STRIDE,
    INSTRUMENT_VIEW_SIZE,
    INSTRUMENTS,
    MUSIC_POINTERS,
    SFX_RECORD_SIZE,
    SFX_RECORDS,
    SFX_TABLE_START,
    Z80_DRIVER_SIZE,
    Z80_DRIVER_START,
)
from extract_type1_resources import EXPECTED_SHA256

TICK_HZ = 60
INITIAL_PROGRAM_MAP = 0x014D


def instrument_manifest(driver: bytes) -> list[dict[str, object]]:
    programs: list[dict[str, object]] = []
    for program in range(INSTRUMENTS):
        pointer_offset = INSTRUMENT_POINTER_TABLE + program * 2
        pointer = int.from_bytes(driver[pointer_offset : pointer_offset + 2], "little")
        expected = 0x10EC + program * INSTRUMENT_STRIDE
        if pointer != expected:
            raise ValueError(f"Program {program}: unexpected pointer 0x{pointer:04X}")
        view = driver[pointer : pointer + INSTRUMENT_VIEW_SIZE]
        b0, b4 = view[0x2C], view[0x2D]
        algorithm = b0 & 7
        operators: list[dict[str, object]] = []
        for operator, register in OPERATOR_REGISTERS.items():
            dt_mul = view[register // 4]
            total_level = view[(register + 0x10) // 4]
            rs_ar = view[(register + 0x20) // 4]
            am_d1r = view[(register + 0x30) // 4]
            d2r = view[(register + 0x40) // 4]
            d1l_rr = view[(register + 0x50) // 4]
            operators.append(
                {
                    "operator": operator,
                    "carrier": operator in carriers(algorithm),
                    "detune": (dt_mul >> 4) & 7,
                    "multiple": dt_mul & 15,
                    "total_level": total_level & 0x7F,
                    "rate_scale": (rs_ar >> 6) & 3,
                    "attack_rate": rs_ar & 31,
                    "am_enable": bool(am_d1r & 0x80),
                    "decay1_rate": am_d1r & 31,
                    "decay2_rate": d2r & 31,
                    "sustain_level": (d1l_rr >> 4) & 15,
                    "release_rate": d1l_rr & 15,
                }
            )
        programs.append(
            {
                "program": program,
                "algorithm": algorithm,
                "feedback": (b0 >> 3) & 7,
                "pan_left": bool(b4 & 0x80),
                "pan_right": bool(b4 & 0x40),
                "ams": (b4 >> 4) & 3,
                "fms": b4 & 7,
                "operators": operators,
            }
        )
    return programs


def music_manifest(rom: bytes, initial_programs: list[int]) -> list[dict[str, object]]:
    tracks: list[dict[str, object]] = []
    for selector, start in sorted(MUSIC_POINTERS.items()):
        end = int.from_bytes(rom[start - 4 : start], "big")
        events, duration, consumed = decode_stream(rom[start:end])
        channel_programs = initial_programs.copy()
        changed_channels: set[int] = set()
        identity_notes = 0
        normalized: list[dict[str, object]] = []
        for event in events:
            item: dict[str, object] = {
                "tick": event.tick,
                "kind": event.kind,
                "channel": event.channel,
                "delay_after": event.delay_after,
            }
            if event.kind == "program_change":
                channel_programs[event.channel] = event.data1
                changed_channels.add(event.channel)
                item["program"] = event.data1
            else:
                item["note"] = event.data1
                item["program"] = channel_programs[event.channel]
                if event.data2 is not None:
                    item["velocity"] = event.data2
                if event.kind == "note_on" and event.channel not in changed_channels:
                    identity_notes += 1
            normalized.append(item)
        tracks.append(
            {
                "selector": selector,
                "duration_ticks": duration,
                "duration_seconds": duration / TICK_HZ,
                "parsed_bytes": consumed,
                "fresh_identity_notes": identity_notes,
                "events": normalized,
            }
        )
    return tracks


def normal_sfx_manifest(rom: bytes) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for sound_id in range(SFX_RECORDS):
        start = SFX_TABLE_START + sound_id * SFX_RECORD_SIZE
        effect = decode_record(sound_id, rom[start : start + SFX_RECORD_SIZE])
        result.append(
            {
                "sound_id": sound_id,
                "program": effect.program,
                "note": effect.note,
                "termination": effect.termination,
                "segments": [
                    {"duration_ticks": segment.duration, "pitch_step": segment.pitch_step}
                    for segment in effect.segments
                ],
            }
        )
    return result


def special_sfx_manifest(rom: bytes, driver: bytes) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for sound_id, pointer in SAMPLE_POINTERS.items():
        header = rom[pointer : pointer + 6]
        compressed_bytes = int.from_bytes(header[2:4], "big") - 2
        delay = driver_delay(header, driver)
        result.append(
            {
                "sound_id": sound_id,
                "rom_pointer": pointer,
                "rate_key": header[:2].hex(),
                "driver_delay": delay,
                "nominal_rate_hz": nominal_sample_rate(delay),
                "compressed_bytes": compressed_bytes,
                "pcm_samples": compressed_bytes * 2,
                "initial_value": header[4],
                "header_byte_5": header[5],
            }
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
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
        raise ValueError(f"Unexpected fresh-driver program map: {initial_programs}")

    manifest = {
        "format": "rings-of-power-audio-playback-manifest",
        "version": 1,
        "rom_sha256": digest,
        "tick_hz": TICK_HZ,
        "program_state_assumption": "fresh driver; channels 0-15 initially map to programs 0-15",
        "initial_program_map": initial_programs,
        "programs": instrument_manifest(driver),
        "music": music_manifest(rom, initial_programs),
        "normal_sfx": normal_sfx_manifest(rom),
        "special_dac_sfx": special_sfx_manifest(rom, driver),
    }
    args.output_file.parent.mkdir(parents=True, exist_ok=True)
    args.output_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
