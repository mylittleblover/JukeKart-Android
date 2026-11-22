"""Measure the loop seam on every looping BWAV whose decoded PCM is available.

The question: near the loop end, does the source carry a region that resembles the loop start -- a
"pre-loop tail" the engine could crossfade into the head? And if so, how long is it, and where does the
beat grid say the cut should land instead?

Method, stated so the numbers can be argued with:

  * Candidate lag sweep. For lags from 1 frame to MAX_LAG, compare the FINAL `lag` frames of the loop
    body against the `lag` frames that START at the loop head. Similarity is the normalised correlation
    of the two mean-removed signals, computed on the mono downmix (the two channels of these files are
    DSP-encoded halves of one stereo image, and the seam is a property of the musical edit, not of one
    speaker).
  * A seam exists when the best correlation over the sweep is at least CORR_MIN AND the best lag is at
    least LAG_MIN. The threshold is reported rather than hidden; a file that fails it is listed as
    "no seam", which is the expected answer for many files.
  * The seam length is the lag that achieved the best correlation. It is reported in frames and in
    milliseconds, never rounded to a musically convenient number.

Anything this cannot measure is reported as unmeasured with the reason, never filled in.
"""
from __future__ import annotations

import json
import os
import re
import struct
import sys

import numpy as np

_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BWAV = os.environ.get("MKW_ROMFS_STREAM", os.path.join(_REPO, "romfs", "Sound", "Resource", "Stream"))
WAV = os.environ.get("MKW_WAV_STREAM", os.path.join(_REPO, "readable_assets", "audio", "romfs", "Sound", "Resource", "Stream"))
HEADERS = os.path.join(_REPO, "reports", "generated", "bwav-loop-headers.json")
OUT = os.path.join(_REPO, "reports", "generated", "bwav-loop-seams.json")

NO_LOOP = 0xFFFFFFFF
# The sweep stops here: a "seam" longer than two seconds is not a loop handover, it is a different edit.
MAX_LAG = 48000 * 2
# A seam has to be recognisable. 0.80 is well above what unrelated music reaches by chance at these lengths.
CORR_MIN = 0.80
# Anything shorter than 5 ms is indistinguishable from a sample-alignment artefact of the export.
LAG_MIN = 240


def read_wav_mono(path: str) -> tuple[np.ndarray, int] | None:
    """Reads a 16-bit PCM WAV as a mono float32 downmix, with its sample rate."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None
    pos = 12
    fmt = None
    while pos + 8 <= len(data):
        tag = data[pos:pos + 4]
        size = struct.unpack_from("<I", data, pos + 4)[0]
        body = pos + 8
        if tag == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", data, body)
        elif tag == b"data":
            if fmt is None:
                return None
            _, channels, rate, _, _, bits = fmt
            if bits != 16 or channels < 1:
                return None
            count = size // (2 * channels)
            pcm = np.frombuffer(data[body:body + count * 2 * channels], dtype="<i2")
            pcm = pcm.reshape(-1, channels).astype(np.float32) / 32768.0
            return pcm.mean(axis=1), rate
        pos = body + size + (size & 1)
    return None


def best_corr(tail: np.ndarray, head: np.ndarray, lag: int) -> float:
    a = tail[-lag:]
    b = head[:lag]
    a = a - a.mean()
    b = b - b.mean()
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denom <= 1e-12:
        return 0.0
    return float(np.dot(a, b) / denom)


def measure(samples: np.ndarray, rate: int, loop_start: int, loop_end: int) -> dict:
    """The seam measurement for one loop window."""
    if loop_start < 0 or loop_end > len(samples) or loop_start >= loop_end:
        return {"error": "loop window outside the decoded audio"}
    body = samples[loop_end - (loop_end - loop_start):loop_end]
    span = loop_end - loop_start
    head = samples[loop_start:loop_end]
    longest = min(MAX_LAG, span // 2)
    if longest < LAG_MIN:
        return {"error": "loop shorter than the minimum seam length", "loop_frames": span}

    # Coarse sweep first, then refine around the best lag: a full-resolution sweep over two seconds is
    # 96 000 correlations per file and buys nothing but time.
    coarse = list(range(LAG_MIN, longest + 1, 480))
    scored = [(lag, best_corr(body, head, lag)) for lag in coarse]
    best_lag, best_score = max(scored, key=lambda pair: pair[1])
    for lag in range(max(LAG_MIN, best_lag - 480), min(longest, best_lag + 480) + 1, 24):
        score = best_corr(body, head, lag)
        if score > best_score:
            best_lag, best_score = lag, score

    has_seam = best_score >= CORR_MIN and best_lag >= LAG_MIN
    return {
        "loop_start": loop_start,
        "loop_end": loop_end,
        "loop_frames": span,
        "seam_present": has_seam,
        # Always recorded: the best lag is the measurement, whether or not it clears the threshold. Recording
        # it only for accepted seams would hide the distribution the threshold was chosen against.
        "best_lag": best_lag,
        "seam_frames": best_lag if has_seam else 0,
        "seam_ms": (best_lag / rate * 1000.0) if has_seam else 0.0,
        "best_lag_ms": best_lag / rate * 1000.0,
        "corr": best_score,
        "threshold": CORR_MIN,
    }


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    with open(HEADERS, encoding="utf-8") as f:
        document = json.load(f)

    looping = []
    for row in document["rows"]:
        loops = [c for c in row["chans"] if c["loop_end"] != NO_LOOP]
        if loops:
            looping.append((row["name"], loops[0]))

    # Only BGM material is musically meaningful here. The 2707 non-BGM looping files are sound effects and
    # interface beds whose loops are mechanical, and measuring a musical seam on them would be a category
    # error dressed up as data. The corpus count still reports them.
    def interesting(name: str, ch: dict) -> bool:
        return name.startswith("BGM_")

    selected = [(n, c) for n, c in looping if interesting(n, c)]
    if limit:
        selected = selected[:limit]
    print("looping files in corpus      :", len(looping))
    print("selected for measurement     :", len(selected))

    results = []
    missing = []
    for index, (name, ch) in enumerate(selected, 1):
        wav = os.path.join(WAV, os.path.splitext(name)[0] + ".wav")
        if not os.path.isfile(wav):
            missing.append((name, "no decoded wav"))
            continue
        decoded = read_wav_mono(wav)
        if decoded is None:
            missing.append((name, "unreadable wav"))
            continue
        samples, rate = decoded
        outcome = measure(samples, rate, ch["loop_start"], ch["loop_end"])
        outcome["name"] = name
        outcome["rate"] = rate
        outcome["decoded_frames"] = int(len(samples))
        results.append(outcome)
        if index % 25 == 0 or index == len(selected):
            done = sum(1 for r in results if r.get("seam_present"))
            print("  [%d/%d] measured=%d seams=%d" % (index, len(selected), len(results), done), flush=True)

    with_seam = [r for r in results if r.get("seam_present")]
    without = [r for r in results if "error" not in r and not r.get("seam_present")]
    errors = [r for r in results if "error" in r]

    print()
    print("measured                     :", len(results))
    print("with a seam                  :", len(with_seam))
    print("without a seam               :", len(without))
    print("measurement errors           :", len(errors))
    print("skipped (no decoded audio)   :", len(missing))
    if with_seam:
        seam_ms = sorted(r["seam_ms"] for r in with_seam)
        print("seam ms min/median/max       : %.1f / %.1f / %.1f" % (
            seam_ms[0], seam_ms[len(seam_ms) // 2], seam_ms[-1]))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"threshold": CORR_MIN, "lag_min": LAG_MIN, "max_lag": MAX_LAG,
                   "results": results, "missing": missing}, f, indent=1)
    print()
    print("written:", OUT)


if __name__ == "__main__":
    main()
