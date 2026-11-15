"""What the loop window actually looks like: where it starts, and whether anything sits outside it.

Two facts decide whether a "reserved seam" can exist at all:

  * audio before `loop_start` -- a pre-roll the engine skips on the second pass;
  * audio after `loop_end` -- a tail the engine would have to reach past the loop point to use.

If a file has neither, its loop is cut exactly at the loop boundaries and there is no reserved material
anywhere. That is a finding, and it contradicts the usual assumption, so it is measured rather than assumed.
"""
from __future__ import annotations

import json

HEADERS = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-loop-headers.json"


def main() -> None:
    with open(HEADERS, encoding="utf-8") as f:
        headers = json.load(f)

    rows = []
    for row in headers["rows"]:
        loops = [c for c in row["chans"] if c["loop_end"] != 0xFFFFFFFF]
        if loops and row["name"].startswith("BGM_"):
            rows.append((row["name"], loops[0]))

    print("looping BGM files:", len(rows))
    preroll_zero = [n for n, c in rows if c["loop_start"] == 0]
    preroll = [(n, c["loop_start"]) for n, c in rows if c["loop_start"] > 0]
    print("  loop_start == 0 (no pre-roll)      :", len(preroll_zero))
    print("  loop_start  > 0 (pre-roll present) :", len(preroll))
    if preroll:
        sizes = sorted(v for _, v in preroll)
        print("    pre-roll frames min/median/max   : %d / %d / %d" % (
            sizes[0], sizes[len(sizes) // 2], sizes[-1]))
        print("    pre-roll ms min/median/max       : %.1f / %.1f / %.1f" % (
            sizes[0] / 48.0, sizes[len(sizes) // 2] / 48.0, sizes[-1] / 48.0))
        print()
        print("    the files that have one:")
        for name, value in sorted(preroll, key=lambda x: -x[1])[:25]:
            print("      %-46s preroll=%8d (%.1f ms)" % (name[:46], value, value / 48.0))

    print()
    tails = [(n, c["frames"] - c["loop_end"]) for n, c in rows]
    print("  frames == loop_end (no tail)       :", sum(1 for _, t in tails if t == 0))
    print("  frames  > loop_end (tail present)  :", sum(1 for _, t in tails if t > 0))

    # Loop length distribution, so a reader can tell a 4-bar loop from a whole prelude.
    lengths = sorted((c["loop_end"] - c["loop_start"]) for _, c in rows)
    print()
    print("  loop frames min/median/max         : %d / %d / %d" % (
        lengths[0], lengths[len(lengths) // 2], lengths[-1]))
    print("  loop seconds min/median/max        : %.2f / %.2f / %.2f" % (
        lengths[0] / 48000.0, lengths[len(lengths) // 2] / 48000.0, lengths[-1] / 48000.0))


if __name__ == "__main__":
    main()
