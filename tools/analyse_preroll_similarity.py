"""Does anything sit just before `loop_start` that resembles the loop head?

This is the other reading of "a reserved seam": not material before the loop END, but a short lead-in before
the loop START that the exporter wrote so the first repeat can crossfade into the head. It is a different
question from the one `analyse_loop_seams.py` asks, and it needs its own measurement:

    compare samples [loop_start - lag, loop_start) against [loop_start, loop_start + lag)

If the exporter left such a lead-in, that correlation peaks somewhere in the 1-50 ms range and stays high as
the lag grows. If it did not, the correlation falls away immediately, because the region before the loop
point is ordinary preceding music.
"""
from __future__ import annotations

import json
import os
import struct

import numpy as np

HEADERS = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-loop-headers.json"
WAV = r"G:\Mario Kart World\readable_assets\audio\romfs\Sound\Resource\Stream"
OUT = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-preroll.json"

LAGS = [240, 480, 960, 1920, 4800, 9600, 24000]


def read_wav_mono(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None
    pos, fmt = 12, None
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


def corr(a, b):
    a = a - a.mean()
    b = b - b.mean()
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    return 0.0 if denom <= 1e-12 else float(np.dot(a, b) / denom)


def main():
    headers = json.load(open(HEADERS, encoding="utf-8"))
    rows = []
    for row in headers["rows"]:
        loops = [c for c in row["chans"] if c["loop_end"] != 0xFFFFFFFF]
        if loops and row["name"].startswith("BGM_"):
            rows.append((row["name"], loops[0]))
    print("looping BGM files:", len(rows))

    results = []
    for name, ch in rows:
        wav = os.path.join(WAV, os.path.splitext(name)[0] + ".wav")
        if not os.path.isfile(wav):
            continue
        decoded = read_wav_mono(wav)
        if decoded is None:
            continue
        samples, rate = decoded
        start = ch["loop_start"]
        per_lag = {}
        for lag in LAGS:
            if start - lag < 0 or start + lag > len(samples):
                continue
            per_lag[lag] = corr(samples[start - lag:start], samples[start:start + lag])
        if not per_lag:
            continue
        results.append({
            "name": name,
            "loop_start": start,
            "rate": rate,
            "corr": per_lag,
            "best_lag": max(per_lag, key=lambda k: per_lag[k]),
            "best_corr": max(per_lag.values()),
        })

    print("measured:", len(results))
    if not results:
        return
    print()
    header = "  lag(frames)  lag(ms)  median corr"
    print(header)
    for lag in LAGS:
        values = sorted(r["corr"][lag] for r in results if lag in r["corr"])
        if not values:
            continue
        print("  %-12d %-8.1f %.4f" % (lag, lag / 48.0, values[len(values) // 2]))
    tops = sorted(results, key=lambda r: -r["best_corr"])[:10]
    print()
    print("best correlation per file, top 10:")
    for r in tops:
        print("  %-46s best_lag=%6d (%.1f ms) corr=%.4f" % (
            r["name"][:46], r["best_lag"], r["best_lag"] / r["rate"] * 1000, r["best_corr"]))

    json.dump({"lags": LAGS, "results": results}, open(OUT, "w", encoding="utf-8"), indent=1)
    print()
    print("written:", OUT)


if __name__ == "__main__":
    main()
