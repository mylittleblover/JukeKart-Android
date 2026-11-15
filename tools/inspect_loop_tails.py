"""Two questions the first pass did not answer.

1. Is there material AFTER the loop end? A loop whose file continues past `loop_end` has an exporter tail;
   that tail is where a seam would live if the file carries one at all.
2. Is the correlation distribution bimodal -- one cluster of files with a seam and one without -- or is it a
   single continuous smear? A threshold is only meaningful for the first shape.
"""
from __future__ import annotations

import json
import os
import struct

import numpy as np

HEADERS = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-loop-headers.json"
SEAMS = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-loop-seams.json"


def main() -> None:
    with open(HEADERS, encoding="utf-8") as f:
        headers = json.load(f)

    tails = []
    for row in headers["rows"]:
        loops = [c for c in row["chans"] if c["loop_end"] != 0xFFFFFFFF]
        if not loops or not row["name"].startswith("BGM_"):
            continue
        ch = loops[0]
        tails.append({
            "name": row["name"],
            "frames": ch["frames"],
            "loop_end": ch["loop_end"],
            "after_loop_end": ch["frames"] - ch["loop_end"],
        })

    print("looping BGM files            :", len(tails))
    with_tail = [t for t in tails if t["after_loop_end"] > 0]
    exact = [t for t in tails if t["after_loop_end"] == 0]
    neg = [t for t in tails if t["after_loop_end"] < 0]
    print("  frames == loop_end         :", len(exact))
    print("  frames  > loop_end (tail)  :", len(with_tail))
    print("  frames  < loop_end         :", len(neg), "(loop claims audio the file does not have)")
    if with_tail:
        sizes = sorted(t["after_loop_end"] for t in with_tail)
        print("  tail frames min/median/max : %d / %d / %d" % (
            sizes[0], sizes[len(sizes) // 2], sizes[-1]))
        print("  tail ms min/median/max     : %.1f / %.1f / %.1f" % (
            sizes[0] / 48.0, sizes[len(sizes) // 2] / 48.0, sizes[-1] / 48.0))
        print()
        print("  the files that have one:")
        for t in sorted(with_tail, key=lambda x: -x["after_loop_end"])[:20]:
            print("    %-46s tail=%7d (%.1f ms) loop_end=%8d frames=%8d" % (
                t["name"][:46], t["after_loop_end"], t["after_loop_end"] / 48.0, t["loop_end"], t["frames"]))

    with open(SEAMS, encoding="utf-8") as f:
        seams = json.load(f)
    corrs = sorted(r["corr"] for r in seams["results"])
    # A bimodal set has a wide empty stretch between the low cluster and the high one. Report the largest
    # gap in the sorted values and where it sits, rather than asserting bimodality.
    gaps = [(corrs[i + 1] - corrs[i], corrs[i], corrs[i + 1]) for i in range(len(corrs) - 1)]
    gaps.sort(reverse=True)
    print()
    print("correlation distribution     : n=%d" % len(corrs))
    print("  largest gaps (size, from, to):")
    for size, low, high in gaps[:5]:
        print("    %.4f  %.4f -> %.4f" % (size, low, high))
    above = sum(1 for c in corrs if c >= 0.80)
    print("  at or above 0.80           : %d" % above)
    print("  between 0.50 and 0.80      : %d" % sum(1 for c in corrs if 0.50 <= c < 0.80))
    print("  below 0.50                 : %d" % sum(1 for c in corrs if c < 0.50))


if __name__ == "__main__":
    main()
