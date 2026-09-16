#!/usr/bin/env python3
"""Generate a copyright-clean OGG pack for the standalone Android demo."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROM_SHA256 = "36303fc447c433ebc69c3d4df86c783c86b383e0acead1c19595f13269e248f5"
SAMPLE_RATE = 48_000
DURATION_SECONDS = 2.4


def render_tone(path: Path) -> None:
    frames = round(SAMPLE_RATE * DURATION_SECONDS)
    attack = round(SAMPLE_RATE * 0.08)
    release = round(SAMPLE_RATE * 0.35)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        for index in range(frames):
            envelope = min(1.0, index / attack, (frames - index) / release)
            time = index / SAMPLE_RATE
            chord = (
                math.sin(2.0 * math.pi * 220.0 * time)
                + 0.55 * math.sin(2.0 * math.pi * 277.18 * time)
                + 0.35 * math.sin(2.0 * math.pi * 329.63 * time)
            )
            sample = int(11_000 * envelope * chord / 1.9)
            output.writeframesraw(struct.pack("<hh", sample, sample))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise SystemExit("ffmpeg is required")

    music_dir = args.output / "music"
    music_dir.mkdir(parents=True, exist_ok=True)
    ogg_path = music_dir / "selector-08.ogg"
    with tempfile.TemporaryDirectory() as temporary:
        wav_path = Path(temporary) / "selector-08.wav"
        render_tone(wav_path)
        subprocess.run(
            [
                ffmpeg,
                "-nostdin",
                "-loglevel",
                "error",
                "-y",
                "-i",
                str(wav_path),
                "-c:a",
                "libvorbis",
                "-q:a",
                "5",
                str(ogg_path),
            ],
            check=True,
        )

    encoded = ogg_path.read_bytes()
    manifest = {
        "format": "rings-of-power-audio-overrides",
        "version": 1,
        "rom_sha256": ROM_SHA256,
        "partial_pack": True,
        "entries": [
            {
                "kind": "music",
                "id": 8,
                "path": "music/selector-08.ogg",
                "output_sha256": hashlib.sha256(encoded).hexdigest(),
                "source_duration_seconds": DURATION_SECONDS,
                "target_duration_seconds": DURATION_SECONDS,
                "duration_delta_seconds": 0.0,
                "duration_compatible": True,
                "sample_rate": SAMPLE_RATE,
                "channels": 2,
                "codec": "vorbis",
                "bytes": len(encoded),
            }
        ],
    }
    (args.output / "audio-overrides.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(f"Generated demo pack: {len(encoded)} OGG bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
