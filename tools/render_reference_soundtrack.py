#!/usr/bin/env python3
"""Render a checksummed lossless reference soundtrack for selected music IDs."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from decode_music_events import decode_stream
from extract_audio_resources import MUSIC_POINTERS


def run(command: list[str], description: str) -> str:
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(f"{description} failed: {detail}")
    return result.stdout


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def probe(path: Path) -> dict[str, object]:
    raw = run(
        [
            "ffprobe", "-v", "error", "-select_streams", "a:0",
            "-show_entries", "stream=codec_name,sample_rate,channels,duration_ts,time_base",
            "-of", "json", str(path),
        ],
        "audio probe",
    )
    streams = json.loads(raw).get("streams", [])
    if len(streams) != 1:
        raise ValueError(f"Expected one audio stream in {path}")
    return streams[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--selectors", type=int, nargs="+", choices=sorted(MUSIC_POINTERS))
    parser.add_argument("--format", choices=("flac", "wav"), default="flac")
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if ffmpeg is None or ffprobe is None:
        raise SystemExit("FFmpeg and FFprobe are required")
    selectors = sorted(set(args.selectors or MUSIC_POINTERS))
    rom = args.rom.read_bytes()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    tools_dir = Path(__file__).resolve().parent
    rows: list[dict[str, object]] = []

    with tempfile.TemporaryDirectory(prefix="rop-soundtrack-") as temporary:
        temp_dir = Path(temporary)
        for selector in selectors:
            start = MUSIC_POINTERS[selector]
            end = int.from_bytes(rom[start - 4 : start], "big")
            _, duration_ticks, _ = decode_stream(rom[start:end])
            expected_samples = duration_ticks * 735
            temporary_wav = temp_dir / f"selector-{selector:02d}.wav"
            revision_flag = ["--allow-unknown-revision"] if args.allow_unknown_revision else []
            run(
                [
                    sys.executable, str(tools_dir / "render_music_wav.py"),
                    str(args.rom), str(selector), str(temporary_wav), *revision_flag,
                ],
                f"selector {selector} render",
            )

            output = args.output_dir / f"selector-{selector:02d}.{args.format}"
            if args.format == "wav":
                shutil.move(temporary_wav, output)
            else:
                run(
                    [
                        ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                        "-i", str(temporary_wav), "-c:a", "flac", "-compression_level", "8", str(output),
                    ],
                    f"selector {selector} FLAC encode",
                )

            metadata = probe(output)
            sample_rate = int(metadata["sample_rate"])
            if metadata.get("time_base") != f"1/{sample_rate}":
                raise ValueError(f"Selector {selector}: unexpected time base {metadata.get('time_base')}")
            frames = int(metadata["duration_ts"])
            if frames != expected_samples:
                raise ValueError(f"Selector {selector}: expected {expected_samples} frames, got {frames}")
            if sample_rate != 44_100 or int(metadata["channels"]) != 2:
                raise ValueError(f"Selector {selector}: unexpected output format")
            rows.append(
                {
                    "selector": selector,
                    "duration_ticks": duration_ticks,
                    "sample_frames": frames,
                    "duration_seconds": f"{frames / sample_rate:.6f}",
                    "sample_rate": sample_rate,
                    "channels": metadata["channels"],
                    "codec": metadata["codec_name"],
                    "bytes": output.stat().st_size,
                    "sha256": sha256(output),
                    "file": output.name,
                }
            )

    with (args.output_dir / "soundtrack-manifest.tsv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys(), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
