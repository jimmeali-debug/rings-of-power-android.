#!/usr/bin/env python3
"""Render a music selector to WAV through the verified trace/VGM pipeline."""

from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

from extract_audio_resources import MUSIC_POINTERS

SAMPLE_RATE = 44_100


def run(command: list[str], description: str) -> None:
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(f"{description} failed: {detail}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("selector", type=int, choices=sorted(MUSIC_POINTERS))
    parser.add_argument("output_wav", type=Path)
    parser.add_argument("--keep-intermediates", type=Path)
    parser.add_argument("--allow-unknown-revision", action="store_true")
    args = parser.parse_args()

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise SystemExit("FFmpeg is required, with the libgme input demuxer enabled")

    tools_dir = Path(__file__).resolve().parent
    with tempfile.TemporaryDirectory(prefix="rop-audio-") as temporary:
        temp_dir = Path(temporary)
        trace = temp_dir / f"selector-{args.selector:02d}.tsv"
        vgm = temp_dir / f"selector-{args.selector:02d}.vgm"
        revision_flag = ["--allow-unknown-revision"] if args.allow_unknown_revision else []
        run(
            [
                sys.executable,
                str(tools_dir / "export_ym2612_trace.py"),
                str(args.rom),
                str(args.selector),
                str(trace),
                *revision_flag,
            ],
            "YM2612 trace export",
        )
        run(
            [sys.executable, str(tools_dir / "export_vgm.py"), str(trace), str(vgm)],
            "VGM export",
        )

        vgm_header = vgm.read_bytes()[:0x100]
        if len(vgm_header) < 0x100 or vgm_header[:4] != b"Vgm ":
            raise ValueError("Generated file is not a valid VGM")
        total_samples = struct.unpack_from("<I", vgm_header, 0x18)[0]
        duration = total_samples / SAMPLE_RATE
        args.output_wav.parent.mkdir(parents=True, exist_ok=True)
        run(
            [
                ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                str(vgm),
                "-af",
                f"apad,atrim=end_sample={total_samples}",
                "-ar",
                str(SAMPLE_RATE),
                "-ac",
                "2",
                "-c:a",
                "pcm_s16le",
                str(args.output_wav),
            ],
            "YM2612 WAV render (FFmpeg/libgme)",
        )

        if args.keep_intermediates:
            args.keep_intermediates.mkdir(parents=True, exist_ok=True)
            shutil.copy2(trace, args.keep_intermediates / trace.name)
            shutil.copy2(vgm, args.keep_intermediates / vgm.name)

    print(
        f"selector={args.selector} samples={total_samples} "
        f"duration={duration:.6f}s output={args.output_wav}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
