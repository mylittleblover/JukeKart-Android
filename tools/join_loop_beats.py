"""Join the BWAV loop windows to the game's own MINF beat grid.

The question this answers: where, exactly, does the loop handover land relative to the beat grid the game
itself publishes? That is a measurement, not a musical judgement -- the grid is in the MINF data, the loop
window is in the BWAV header, and this compares the two.

For each looping BGM file it reports:
  * whether the loop start and loop end sit on a grid line, and by how many frames they miss it;
  * the loop length in beats;
  * whether a "seam" measured by `analyse_loop_seams.py` is inside one beat of the loop end, which is what a
    beat-aligned cut would require.
"""
from __future__ import annotations

import json
import os
import statistics

_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MINF = os.path.join(_REPO, "app", "src", "main", "assets", "interactive_minf.json")
HEADERS = os.path.join(_REPO, "reports", "generated", "bwav-loop-headers.json")
SEAMS = os.path.join(_REPO, "reports", "generated", "bwav-loop-seams.json")
OUT = os.path.join(_REPO, "reports", "generated", "bwav-loop-beats.json")


def nearest_grid(frames: list[int], value: int) -> tuple[int, int]:
    """The closest grid frame and the signed distance to it."""
    best = min(frames, key=lambda f: abs(f - value))
    return best, value - best


def main() -> None:
    minf = json.load(open(MINF, encoding="utf-8"))
    by_name = {r["name"]: r for r in minf["records"]}
    headers = json.load(open(HEADERS, encoding="utf-8"))
    seams = {r["name"]: r for r in json.load(open(SEAMS, encoding="utf-8"))["results"]}

    rows = []
    unmatched = []
    for row in headers["rows"]:
        loops = [c for c in row["chans"] if c["loop_end"] != 0xFFFFFFFF]
        if not loops or not row["name"].startswith("BGM_"):
            continue
        name = row["name"]
        ch = loops[0]
        # MINF records name the resource without an extension; BWAV files carry `.bwav`. Joining on the raw
        # file name matched nothing at all, which is why the first run reported zero joins rather than a
        # partial one.
        record = by_name.get(name) or by_name.get(name.rsplit(".", 1)[0])
        if record is None:
            unmatched.append(name)
            continue
        beats = [b["frame"] for b in record["beats"]]
        if not beats:
            unmatched.append(name + " (no grid)")
            continue
        start_grid, start_delta = nearest_grid(beats, ch["loop_start"])
        end_grid, end_delta = nearest_grid(beats, ch["loop_end"])
        # Spacing from the grid itself rather than from BPM, so a tempo change cannot skew the answer.
        spacing = statistics.median(
            [b - a for a, b in zip(beats, beats[1:])] or [0]) or 1
        seam = seams.get(name, {})
        rows.append({
            "name": name,
            "stage": record.get("stage"),
            "role": record.get("role"),
            "bpm": record.get("bpm"),
            "rate": ch["rate"],
            "beat_frames": spacing,
            "loop_start": ch["loop_start"],
            "loop_end": ch["loop_end"],
            "minf_loop_start": record.get("loopStart"),
            "minf_loop_end": record.get("loopEnd"),
            "bwav_matches_minf": (ch["loop_start"] == record.get("loopStart")
                                  and ch["loop_end"] == record.get("loopEnd")),
            "start_on_grid": start_delta == 0,
            "start_delta": start_delta,
            "end_on_grid": end_delta == 0,
            "end_delta": end_delta,
            "loop_beats": (ch["loop_end"] - ch["loop_start"]) / spacing,
            "corr": seam.get("corr"),
            "best_lag": seam.get("best_lag"),
            "seam_present": seam.get("seam_present"),
            "seam_within_one_beat": (seam.get("seam_present") and seam.get("best_lag", 0) <= spacing),
        })

    total = len(rows)
    print("looping BGM files joined to a MINF record:", total)
    print("looping BGM files with no MINF record       :", len(unmatched))
    if unmatched:
        for name in unmatched[:10]:
            print("   ", name)
    print()
    if not total:
        return
    print("BWAV loop window == MINF loop window :",
          sum(1 for r in rows if r["bwav_matches_minf"]), "/", total)
    print("loop start exactly on a grid line    :", sum(1 for r in rows if r["start_on_grid"]), "/", total)
    print("loop end   exactly on a grid line    :", sum(1 for r in rows if r["end_on_grid"]), "/", total)
    starts = sorted(abs(r["start_delta"]) for r in rows)
    ends = sorted(abs(r["end_delta"]) for r in rows)
    print("  |start - nearest beat| frames  median %d  max %d" % (starts[len(starts) // 2], starts[-1]))
    print("  |end   - nearest beat| frames  median %d  max %d" % (ends[len(ends) // 2], ends[-1]))
    beats = sorted(r["loop_beats"] for r in rows)
    print("  loop length in beats  min/median/max  %.2f / %.2f / %.2f" % (
        beats[0], beats[len(beats) // 2], beats[-1]))
    seams_present = [r for r in rows if r["seam_present"]]
    print()
    print("measured seams in this set            :", len(seams_present))
    for r in seams_present:
        print("   %-44s corr=%.3f lag=%d frames (%.1f ms) beat=%d frames  within one beat=%s" % (
            r["name"][:44], r["corr"], r["best_lag"], r["best_lag"] / r["rate"] * 1000,
            r["beat_frames"], r["seam_within_one_beat"]))

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"rows": rows, "unmatched": unmatched}, f, indent=1)
    print()
    print("written:", OUT)


if __name__ == "__main__":
    main()
