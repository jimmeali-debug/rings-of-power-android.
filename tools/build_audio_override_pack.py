#!/usr/bin/env python3
"""Validate and encode a partial Android-ready remaster audio override pack."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

from decode_music_events import decode_stream
from decode_special_dac import SAMPLE_POINTERS
from extract_audio_resources import MUSIC_POINTERS, SFX_RECORDS
from extract_type1_resources import EXPECTED_SHA256

SUPPORTED_EXTENSIONS = (".wav", ".flac", ".ogg", ".mp3", ".m4a", ".aac")


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
            "-show_entries", "stream=codec_name,sample_rate,channels,duration",
            "-of", "json", str(path),
        ],
        "audio probe",
    )
    streams = json.loads(raw).get("streams", [])
    if len(streams) != 1:
        raise ValueError(f"Expected one audio stream in {path}")
    return streams[0]


def find_source(root: Path, relative_stem: str) -> Path | None:
    matches = [root / f"{relative_stem}{extension}" for extension in SUPPORTED_EXTENSIONS]
    existing = [path for path in matches if path.is_file()]
    if len(existing) > 1:
        raise ValueError(f"Multiple sources supplied for {relative_stem}")
    return existing[0] if existing else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--strict-music-duration", action="store_true")
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None or shutil.which("ffprobe") is None:
        raise SystemExit("FFmpeg and FFprobe are required")
    rom = args.rom.read_bytes()
    digest = hashlib.sha256(rom).hexdigest()
    if digest != EXPECTED_SHA256 and not args.allow_unknown_revision:
        raise SystemExit(f"Unsupported ROM revision ({digest})")

    targets: list[tuple[str, int, str, float | None]] = []
    for selector, start in sorted(MUSIC_POINTERS.items()):
        end = int.from_bytes(rom[start - 4 : start], "big")
        _, ticks, _ = decode_stream(rom[start:end])
        targets.append(("music", selector, f"music/selector-{selector:02d}", ticks / 60))
    for sound_id in range(SFX_RECORDS):
        targets.append(("normal_sfx", sound_id, f"sfx/normal-{sound_id:02d}", None))
    for sound_id in SAMPLE_POINTERS:
        targets.append(("special_sfx", sound_id, f"sfx/special-{sound_id:02x}", None))

    args.output_dir.mkdir(parents=True, exist_ok=True)
    entries: list[dict[str, object]] = []
    for kind, asset_id, relative_stem, target_duration in targets:
        source = find_source(args.source_dir, relative_stem)
        if source is None:
            continue
        source_metadata = probe(source)
        duration = float(source_metadata["duration"])
        duration_delta = None if target_duration is None else duration - target_duration
        duration_compatible = None if duration_delta is None else abs(duration_delta) <= 0.050
        if args.strict_music_duration and duration_compatible is False:
            raise ValueError(
                f"{relative_stem}: duration differs by {duration_delta:+.3f}s from {target_duration:.3f}s"
            )

        output = args.output_dir / f"{relative_stem}.ogg"
        output.parent.mkdir(parents=True, exist_ok=True)
        channels = 2 if kind == "music" else 1
        quality = "6" if kind == "music" else "5"
        run(
            [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                "-map_metadata", "-1", "-ar", "48000", "-ac", str(channels),
                "-c:a", "libvorbis", "-q:a", quality, str(output),
            ],
            f"encode {relative_stem}",
        )
        output_metadata = probe(output)
        entries.append(
            {
                "kind": kind,
                "id": asset_id,
                "path": output.relative_to(args.output_dir).as_posix(),
                "source_sha256": sha256(source),
                "output_sha256": sha256(output),
                "source_duration_seconds": duration,
                "target_duration_seconds": target_duration,
                "duration_delta_seconds": duration_delta,
                "duration_compatible": duration_compatible,
                "sample_rate": int(output_metadata["sample_rate"]),
                "channels": int(output_metadata["channels"]),
                "codec": output_metadata["codec_name"],
                "bytes": output.stat().st_size,
            }
        )

    if not entries:
        raise SystemExit("No recognized override audio files were found")
    manifest = {
        "format": "rings-of-power-audio-overrides",
        "version": 1,
        "rom_sha256": digest,
        "partial_pack": len(entries) < len(targets),
        "entries": entries,
    }
    (args.output_dir / "audio-overrides.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
