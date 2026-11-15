"""Header-only scan of BWAV loop metadata across the real MKW corpus.

Reads ONLY the 16-byte file header plus the per-channel 76-byte headers.
Does not read audio payload. Writes JSON + prints a summary.

Layout (as the project's reader implements it, little-endian):
  'BWAV' magic at 0, version 0xfeff at 4, format version 1 at 6,
  channel count u16 at 14.
  Channel header at 16 + ch*76:
    +0  u16 codec kind (1 = DSP-ADPCM, 2 = chunked Opus)
    +2  u16 speaker
    +4  u32 sample rate
    +8  u32 frame count
    +12 u32 frame count (duplicate)
    +48 u32 data start offset
    +52 u32 data start offset (duplicate)
    +56 u32 = 1
    +60 u32 loop end   (0xFFFFFFFF = no loop)
    +64 u32 loop start
"""
import json
import os
import struct
import sys

BW = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"
OUT = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-loop-headers.json"
NO_LOOP = 0xFFFFFFFF


def read_header(path):
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        head = f.read(16)
    if len(head) < 16 or head[:4] != b"BWAV":
        return {"error": "not BWAV"}
    version = struct.unpack_from("<H", head, 4)[0]
    fmtver = struct.unpack_from("<H", head, 6)[0]
    count = struct.unpack_from("<H", head, 14)[0]
    if count < 1 or count > 32:
        return {"error": "bad channel count %d" % count}
    need = 16 + count * 76
    with open(path, "rb") as f:
        head = f.read(need)
    if len(head) < need:
        return {"error": "short header"}
    chans = []
    for ch in range(count):
        h = 16 + ch * 76
        chans.append({
            "ch": ch,
            "kind": struct.unpack_from("<H", head, h)[0],
            "speaker": struct.unpack_from("<H", head, h + 2)[0],
            "rate": struct.unpack_from("<I", head, h + 4)[0],
            "frames8": struct.unpack_from("<I", head, h + 8)[0],
            "frames": struct.unpack_from("<I", head, h + 12)[0],
            "data_off48": struct.unpack_from("<I", head, h + 48)[0],
            "data_off52": struct.unpack_from("<I", head, h + 52)[0],
            "one56": struct.unpack_from("<I", head, h + 56)[0],
            "loop_end": struct.unpack_from("<I", head, h + 60)[0],
            "loop_start": struct.unpack_from("<I", head, h + 64)[0],
        })
    return {
        "size": size,
        "version": version,
        "fmtver": fmtver,
        "count": count,
        "chans": chans,
    }


def main():
    names = sorted(n for n in os.listdir(BW) if n.lower().endswith(".bwav"))
    rows = []
    bad = []
    for i, name in enumerate(names):
        info = read_header(os.path.join(BW, name))
        if "error" in info:
            bad.append((name, info["error"]))
            continue
        info["name"] = name
        rows.append(info)
        if (i + 1) % 4000 == 0:
            print("  scanned %d/%d" % (i + 1, len(names)), flush=True)

    looping = []
    for r in rows:
        loops = [c for c in r["chans"] if c["loop_end"] != NO_LOOP]
        r["n_looping_ch"] = len(loops)
        if loops:
            looping.append(r)

    print("\ncorpus")
    print("  bwav files found           %d" % len(names))
    print("  parsed ok                  %d" % len(rows))
    print("  unparseable                %d" % len(bad))
    print("  files with loop metadata   %d" % len(looping))

    # channel-count / codec distribution among looping files
    from collections import Counter
    cc = Counter(r["count"] for r in looping)
    kk = Counter(c["kind"] for r in looping for c in r["chans"])
    print("  looping: channel counts    %s" % dict(sorted(cc.items())))
    print("  looping: codec kinds       %s" % dict(sorted(kk.items())))

    # per-channel disagreement (do channels of one file agree on loop points?)
    disagree = []
    for r in looping:
        ends = {c["loop_end"] for c in r["chans"]}
        starts = {c["loop_start"] for c in r["chans"]}
        if len(ends) > 1 or len(starts) > 1:
            disagree.append((r["name"], sorted(starts), sorted(ends)))
    print("  looping: files whose channels disagree on loop pts: %d" % len(disagree))

    # prefix families among looping files
    fam = Counter()
    for r in looping:
        n = r["name"]
        if n.startswith("BGM_Stage_"):
            fam["BGM_Stage_*"] += 1
        elif n.startswith("BGM_JukeBox_"):
            fam["BGM_JukeBox_*"] += 1
        elif n.startswith("BGM_"):
            fam["BGM_ other"] += 1
        else:
            fam["non-BGM"] += 1
    print("  looping: families          %s" % dict(fam))

    # loop sanity: start<end, end<=frames
    badpts = []
    for r in looping:
        for c in r["chans"]:
            if c["loop_end"] == NO_LOOP:
                continue
            if not (c["loop_start"] < c["loop_end"] <= c["frames"]):
                badpts.append((r["name"], c["ch"], c["loop_start"],
                               c["loop_end"], c["frames"]))
    print("  looping: implausible loop points: %d" % len(badpts))
    for b in badpts[:10]:
        print("     ", b)

    # frames8 vs frames mismatch
    mism = [r["name"] for r in rows if any(c["frames8"] != c["frames"] for c in r["chans"])]
    print("  files where +8 != +12 frame count: %d" % len(mism))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"rows": rows, "bad": bad, "disagree": disagree,
                   "badpts": badpts}, f, indent=1)
    print("\nwritten:", OUT)


if __name__ == "__main__":
    main()
