"""Generate app/src/main/assets/jukebox_catalog.json from the verified OST-BWAV mapping.

The research mapping in docs/research/2026-11-15-jukebox-ost-bwav-mapping.json records
full audio-correlation proof for all 227 Jukebox tracks (OST #120..#346) across 36 source
games ordered chronologically by release date. This script distills that evidence into a
compact runtime asset keyed by BWAV SHA-256, pack_track_key, and BWAV stem.

Usage:
    python tools/gen_jukebox_catalog.py
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

GAME_METADATA = {
    1: ("smb", 1985, "1985", "Super Mario Bros.", "超级马力欧兄弟", "超級瑪利歐兄弟", "スーパーマリオブラザーズ"),
    2: ("smb2", 1988, "1988", "Super Mario Bros. 2", "超级马力欧USA", "超級瑪利歐USA", "スーパーマリオUSA"),
    3: ("smb3", 1988, "1988", "Super Mario Bros. 3", "超级马力欧兄弟3", "超級瑪利歐兄弟3", "スーパーマリオブラザーズ3"),
    4: ("sml", 1989, "1989", "Super Mario Land", "超级马力欧乐园", "超級瑪利歐樂園", "スーパーマリオランド"),
    5: ("smw", 1990, "1990", "Super Mario World", "超级马力欧世界", "超級瑪利歐世界", "スーパーマリオワールド"),
    6: ("mario_paint", 1992, "1992", "Mario Paint", "马力欧绘画", "瑪利歐繪圖", "マリオペイント"),
    7: ("smk", 1992, "1992", "Super Mario Kart", "超级马力欧卡丁车", "超級瑪利歐賽車", "スーパーマリオカート"),
    8: ("sml2", 1992, "1992", "Super Mario Land 2: 6 Golden Coins", "超级马力欧乐园2 六个金币", "超級瑪利歐樂園2 六個金幣", "スーパーマリオランド2 6つの金貨"),
    9: ("wario_land", 1994, "1994", "Wario Land: Super Mario Land 3", "瓦力欧乐园 超级马力欧乐园3", "瓦利歐樂園 超級瑪利歐樂園3", "スーパーマリオランド3 ワリオランド"),
    10: ("dkc", 1994, "1994", "Donkey Kong Country", "超级咚奇刚", "超級咚奇剛", "スーパードンキーコング"),
    11: ("yoshis_island", 1995, "1995", "Super Mario World 2: Yoshi's Island", "超级马力欧 耀西岛", "超級瑪利歐 耀西島", "スーパーマリオ ヨッシーアイランド"),
    12: ("sm64", 1996, "1996", "Super Mario 64", "超级马力欧64", "超級瑪利歐64", "スーパーマリオ64"),
    13: ("mk64", 1996, "1996", "Mario Kart 64", "马力欧卡丁车64", "瑪利歐賽車64", "マリオカート64"),
    14: ("yoshis_story", 1997, "1997", "Yoshi's Story", "耀西故事", "耀西故事", "ヨッシーストーリー"),
    15: ("mksc", 2001, "2001", "Mario Kart: Super Circuit", "马力欧卡丁车ADVANCE", "瑪利歐賽車Advance", "マリオカートアドバンス"),
    16: ("luigis_mansion", 2001, "2001", "Luigi's Mansion", "路易吉洋馆", "路易吉洋樓", "ルイージマンション"),
    17: ("sms", 2002, "2002", "Super Mario Sunshine", "超级马力欧阳光", "超級瑪利歐陽光", "スーパーマリオサンシャイン"),
    18: ("warioware", 2003, "2003", "WarioWare, Inc.: Mega Microgame$!", "瓦力欧制造", "瓦利歐製造", "メイド イン ワリオ"),
    19: ("mkdd", 2003, "2003", "Mario Kart: Double Dash!!", "马力欧卡丁车 双重冲击!!", "瑪利歐賽車 Double Dash!!", "マリオカート ダブルダッシュ!!"),
    20: ("mkds", 2005, "2005", "Mario Kart DS", "马力欧卡丁车DS", "瑪利歐賽車DS", "マリオカートDS"),
    21: ("nsmb", 2006, "2006", "New Super Mario Bros.", "New 超级马力欧兄弟", "New 超級瑪利歐兄弟", "New スーパーマリオブラザーズ"),
    22: ("smg", 2007, "2007", "Super Mario Galaxy", "超级马力欧银河", "超級瑪利歐銀河", "スーパーマリオギャラクシー"),
    23: ("mkwii", 2008, "2008", "Mario Kart Wii", "马力欧卡丁车Wii", "瑪利歐賽車Wii", "マリオカートWii"),
    24: ("flipnote", 2008, "2008", "Flipnote Studio", "动画笔记", "動畫筆記本", "うごくメモ帳"),
    25: ("nsmbwii", 2009, "2009", "New Super Mario Bros. Wii", "New 超级马力欧兄弟 Wii", "新 超級瑪利歐兄弟 Wii", "New スーパーマリオブラザーズ Wii"),
    26: ("smg2", 2010, "2010", "Super Mario Galaxy 2", "超级马力欧银河2", "超級瑪利歐銀河2", "スーパーマリオギャラクシー2"),
    27: ("sm3dl", 2011, "2011", "Super Mario 3D Land", "超级马力欧 3D乐园", "超級瑪利歐 3D樂園", "スーパーマリオ 3Dランド"),
    28: ("mk7", 2011, "2011", "Mario Kart 7", "马力欧卡丁车7", "瑪利歐賽車7", "マリオカート7"),
    29: ("nsmbu", 2012, "2012", "New Super Mario Bros. U", "New 超级马力欧兄弟U", "New 超級瑪利歐兄弟U", "New スーパーマリオブラザーズ U"),
    30: ("sm3dw", 2013, "2013", "Super Mario 3D World", "超级马力欧 3D世界", "超級瑪利歐 3D世界", "スーパーマリオ 3Dワールド"),
    31: ("mk8dx", 2014, "2014 / 2017", "Mario Kart 8 Deluxe", "马力欧卡丁车8 豪华版", "瑪利歐賽車8 豪華版", "マリオカート8 デラックス"),
    32: ("smm", 2015, "2015", "Super Mario Maker", "超级马力欧创作家", "超級瑪利歐創作家", "スーパーマリオメーカー"),
    33: ("odyssey", 2017, "2017", "Super Mario Odyssey", "超级马力欧 奥德赛", "超級瑪利歐 奧德賽", "スーパーマリオ オデッセイ"),
    34: ("mktour", 2019, "2019", "Mario Kart Tour", "马力欧卡丁车 巡回赛", "瑪利歐賽車 巡迴賽", "マリオカート ツアー"),
    35: ("mklhc", 2020, "2020", "Mario Kart Live: Home Circuit", "马力欧卡丁车实况：家庭赛车场", "瑪利歐賽車實況：家庭賽車場", "マリオカート ライブ ホームサーキット"),
    36: ("bowsers_fury", 2021, "2021", "Super Mario 3D World + Bowser's Fury", "超级马力欧 3D世界 + 狂怒世界", "超級瑪利歐 3D世界 + 狂怒世界", "スーパーマリオ 3Dワールド + フューリーワールド"),
}


def build_catalog(mapping_path: Path) -> dict:
    raw = json.loads(mapping_path.read_text(encoding="utf-8"))
    series_list = raw["gameSeriesOrder"]
    tracks_raw = raw["tracks"]

    if len(series_list) != len(GAME_METADATA):
        raise ValueError(f"Expected {len(GAME_METADATA)} series, found {len(series_list)}")

    games = []
    for s in sorted(series_list, key=lambda x: x["game_order"]):
        order = s["game_order"]
        gid, year, year_label, en_name, zh_hans, zh_hant, ja_name = GAME_METADATA[order]
        games.append({
            "id": gid,
            "order": order,
            "year": year,
            "yearLabel": year_label,
            "ostSeries": s["ost_game_series"],
            "bwavGameIds": s["bwav_game_ids"],
            "names": {
                "en": en_name,
                "zh-Hans": zh_hans,
                "zh-Hant": zh_hant,
                "ja": ja_name,
            },
        })

    tracks = []
    seen_sha = set()
    seen_key = set()
    seen_ost = set()
    for t in sorted(tracks_raw, key=lambda x: x["jukebox_order"]):
        order = t["ost_game_order"]
        gid = GAME_METADATA[order][0]
        sha = t["bwav_sha256"].lower()
        key = t["pack_track_key"]
        ost_idx = t["ost_index"]
        assert sha not in seen_sha, f"Duplicate sha: {sha}"
        assert key not in seen_key, f"Duplicate key: {key}"
        assert ost_idx not in seen_ost, f"Duplicate ost_index: {ost_idx}"
        seen_sha.add(sha)
        seen_key.add(key)
        seen_ost.add(ost_idx)

        frames = int(t["bwav_frames"])
        sample_rate = int(t["bwav_sample_rate"])
        duration_ms = round(frames * 1000 / sample_rate) if sample_rate > 0 else 0

        tracks.append({
            "jukeboxOrder": int(t["jukebox_order"]),
            "ostIndex": int(ost_idx),
            "gameId": gid,
            "gameOrder": int(order),
            "ostTitle": t["ost_title"],
            "ostArrangement": t["ost_arrangement"],
            "displayName": t["ost_display_name"],
            "bwavStem": t["bwav_stem"],
            "jukeboxCode": t["jukebox_code"],
            "bwavGameId": t["bwav_game_id"],
            "packTrackKey": key,
            "sha256": sha,
            "frames": frames,
            "sampleRate": sample_rate,
            "durationMs": duration_ms,
        })

    return {
        "schemaVersion": 1,
        "games": games,
        "tracks": tracks,
    }


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mapping",
        type=Path,
        default=root / "docs" / "research" / "2026-11-15-jukebox-ost-bwav-mapping.json",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=root / "app" / "src" / "main" / "assets" / "jukebox_catalog.json",
    )
    args = parser.parse_args()

    catalog = build_catalog(args.mapping)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(catalog['games'])} games and {len(catalog['tracks'])} tracks to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
