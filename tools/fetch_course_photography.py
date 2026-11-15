"""Fetch full-resolution Mario Kart World course photography from the Super Mario Wiki.

The shipped covers come from the game's own stage-select tiles (`C_StageL_*`, 825x465). The wiki carries
Nintendo's own press screenshots at 3840x2160, which is the resolution a tablet or a landscape phone can
actually use.

How a picture is chosen, and why each rule exists:

  * Only files whose name starts with `NM_MKWorld_` are considered. Those are the press screenshots; the rest
    of a course page is icons, stickers, minimaps, trading cards and other games' versions of the course.
  * The course's own wiki slug (from `localized_names.json`'s `sourceUrl`) must appear in the file name. A
    course page links screenshots of *neighbouring* courses -- the Desert Hills page shows Gusty Garden --
    and matching on "MKWorld" alone would put the wrong course on a card.
  * The largest landscape image wins, measured from the API's own width and height rather than from a
    thumbnail URL.
  * A course with no qualifying screenshot is reported as missing. Nothing is substituted.

Outputs, per stage anchor code:
  `art_src/<code>.png`     the downloaded original, untouched
  `reports/generated/course-photography.json`   what was chosen, with size, dimensions and the wiki URL

Two tiers, in order, because 13 of the 30 pages carry no press screenshot at all:

  1. `NM_MKWorld_<course>` -- Nintendo's own 3840x2160 press screenshot.
  2. An in-game screenshot whose name starts with `MKWorld_<course>` and is not an icon, sticker, minimap,
     mugshot, kart render or another game's screenshot. Rainbow Road, for instance, has a full set of
     `MKWorld_Rainbow_Road_Lap_1.jpg` captures.

The second tier is used only when the first is absent, and every choice records which tier it came from, so a
reader can tell Nintendo's press art from a gameplay capture without opening the file.
"""
from __future__ import annotations

import json
import os
import re
import time
import urllib.parse
import urllib.request

ASSET_NAMES = r"G:\Mario Kart World\MKW-Player\app\src\main\assets\localized_names.json"
OUT_DIR = r"G:\Mario Kart World\art_src"
REPORT = r"G:\Mario Kart World\MKW-Player\reports\generated\course-photography.json"

API = "https://www.mariowiki.com/api.php"
UA = "MKWPlayer-ArtFetcher/1.0 (course artwork for a personal player; contact: local build)"
SLEEP = 0.4

# The 30 stage anchor codes the app uses, and the wiki slug each one's page lives at. Taken from
# `localized_names.json`, which records the page each localized name was verified against.
STAGES = [
    "AirshipFortress", "BigTree", "Cave", "Central", "ChocoMountain", "Cinema", "Coaster",
    "DKJungleParkway", "DKPass", "DesertHills", "DinoBones", "DinoDinoJungle", "DonkeyKong",
    "FirstCircuit", "IceBuildings", "KoopaTroopaBeach", "LagoonCity", "Lava", "MooMooFarm",
    "PeachBeach", "RaftingRiver", "Rainbow", "SFCMarioCircuit", "ShyGuyBazaar", "SnowMountain",
    "StormClouds", "ToadFactory", "WarioShipyard", "WarioStadium", "WestCity",
]


def api(**params: str) -> dict:
    params.setdefault("format", "json")
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.loads(response.read().decode("utf-8"))


def download(url: str, destination: str) -> int:
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=300) as response:
        payload = response.read()
    with open(destination, "wb") as handle:
        handle.write(payload)
    return len(payload)


def page_images(page: str) -> list[str]:
    try:
        data = api(action="parse", page=page, prop="images")
        return data.get("parse", {}).get("images", [])
    except Exception as error:  # noqa: BLE001 - a page that will not parse is a normal outcome
        print("    page %s failed: %s" % (page, error))
        return []


# Names that are on a course page but are not a picture of the course.
NOT_PHOTOGRAPHY = (
    "icon", "sticker", "map", "logo", "mugshot", "model", "kart", "emblem", "banner",
    "trading", "card", "profile", "soundtrack", "cover", "sprite", "artwork",
    "propeller_rally", "golden_rally", "cherry_rally", "heart_rally", "spinny_rally",
    "moon_rally", "acorn_rally", "boomerang_rally", "turnip_rally", "cloud_rally",
    "drill_rally", "moon_rally", "coin", "routes",
)

PHOTO_SUFFIXES = (".png", ".jpg", ".jpeg", ".webp")


def words_of(text: str) -> list[str]:
    """Lower-case alphanumeric words, with the decorations the wiki and the files disagree on removed."""
    lowered = urllib.parse.unquote(text).lower()
    lowered = lowered.replace("\u2019", "").replace("'", "")
    # A disambiguation suffix names the game, not the course.
    lowered = re.sub(r"\([^)]*\)", " ", lowered)
    return [w for w in re.split(r"[^a-z0-9]+", lowered) if w]


def is_course_photo(name: str, slug: str) -> int:
    """0 when [name] is not a photograph of [slug], else the tier it belongs to.

    Tier 1 is Nintendo's own press screenshot (`NM_MKWorld_<course>`), tier 2 an in-game capture.

    Matching is by word sequence rather than substring because the wiki is inconsistent about every
    separator it uses: `Toad's Factory` is captured as `Toads_Factory`, `Great ? Block Ruins` as
    `Great_Q_Block_Ruins`, and three courses carry abbreviations the file names do not share
    (`Acorn Heights` is `AcH`, `Dry Bones Burnout` is `DBB`, `Koopa Troopa Beach` lives on the page
    `Koopa_Troopa_Beach_(Super_Mario_Kart)`). Those are handled by [ABBREVIATIONS] and [PAGE_OVERRIDES],
    each of which is an explicit statement rather than a fuzzy guess.
    """
    lowered = name.lower()
    if not lowered.endswith(PHOTO_SUFFIXES):
        return 0
    if lowered.startswith("nm_mkworld_"):
        tier, rest = 1, lowered[len("nm_mkworld_"):]
    elif lowered.startswith("mkworld_"):
        tier, rest = 2, lowered[len("mkworld_"):]
    elif lowered.startswith("mkw_"):
        # A short-form capture, e.g. `MKW_AcH_Turn.jpg`.
        tier, rest = 2, lowered[len("mkw_"):]
    else:
        return 0

    stem = os.path.splitext(rest)[0]
    file_words = words_of(stem)
    if any(word in file_words for word in NOT_PHOTOGRAPHY):
        return 0

    wanted = words_of(slug)
    if not wanted:
        return 0
    for candidate in candidate_word_sequences(wanted):
        if matches_prefix(file_words, candidate):
            return tier
    return 0


# Abbreviations the wiki's own capture names use for a course.
ABBREVIATIONS = {
    "acorn heighes": ["ach"],
    "acorn heights": ["ach"],
    "dry bones burnout": ["dbb"],
}


def candidate_word_sequences(wanted: list[str]) -> list[list[str]]:
    """"The course's words, plus the abbreviations the file names may have used instead."""
    sequences = [wanted]
    for phrase, short in ABBREVIATIONS.items():
        words = phrase.split()
        if wanted[: len(words)] == words:
            sequences.append(short + wanted[len(words):])
    return sequences


def matches_prefix(file_words: list[str], wanted: list[str]) -> bool:
    position = 0
    for word in wanted:
        while position < len(file_words) and file_words[position] != word:
            position += 1
        if position >= len(file_words):
            return False
        position += 1
    return True


def image_info(names: list[str]) -> dict[str, dict]:
    """url/size/mime for up to 50 file names at a time."""
    info: dict[str, dict] = {}
    for start in range(0, len(names), 40):
        chunk = names[start:start + 40]
        titles = "|".join("File:" + name for name in chunk)
        try:
            data = api(action="query", titles=titles, prop="imageinfo", iiprop="url|size|mime")
        except Exception as error:  # noqa: BLE001
            print("    imageinfo failed: %s" % error)
            continue
        for page in data.get("query", {}).get("pages", {}).values():
            entries = page.get("imageinfo") or []
            if not entries:
                continue
            title = page.get("title", "")
            name = title[len("File:"):] if title.startswith("File:") else title
            info[name] = entries[0]
        time.sleep(SLEEP)
    return info


def slug_for(page_url: str) -> str:
    """The article name, decoded.

    The stored URLs are percent-encoded (`Bowser%27s_Castle_(Mario_Kart_World)`). Passing that straight to
    `urlencode` encodes the `%` again, so the API is asked for a page literally named `Bowser%2527s...` and
    answers with no images -- which read as "this course has no screenshot" for four courses that do.
    """
    return urllib.parse.unquote(page_url.rsplit("/", 1)[-1])


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)

    names = json.load(open(ASSET_NAMES, encoding="utf-8"))["entries"]
    page_by_stage = {}
    for entry in names:
        ident = entry["id"]
        base = re.sub(r"_00$", "", ident)
        if base in STAGES and entry.get("sourceUrl"):
            page_by_stage[base] = slug_for(entry["sourceUrl"])

    # Three stages have no usable page under the slug the names asset recorded. Each override is a measured
    # fact about where the wiki keeps that course's pictures, stated here rather than guessed at run time.
    page_by_stage.update({
        # The recorded page is a bare "Koopa Troopa Beach" with no images at all; the Mario Kart World
        # screenshots are on the Super Mario Kart course's page.
        "KoopaTroopaBeach": "Koopa Troopa Beach (Super Mario Kart)",
        # `Acorn Heights` and `Dry Bones Burnout` carry only abbreviated captures (`MKW_AcH_*`,
        # `MKW_DBB_*`), handled by ABBREVIATIONS.
    })

    print("stages:", len(STAGES), "pages found:", len(page_by_stage))
    missing_page = [s for s in STAGES if s not in page_by_stage]
    if missing_page:
        print("  no wiki page for:", missing_page)
    print()

    chosen: dict[str, dict] = {}
    for index, (stage, page) in enumerate(sorted(page_by_stage.items()), 1):
        images = page_images(page)
        time.sleep(SLEEP)

        # Rank every file on the page: tier 1 (press screenshot) beats tier 2 (in-game capture).
        ranked = []
        for name in images:
            tier = is_course_photo(name, page)
            if tier:
                ranked.append((tier, name))
        ranked.sort()
        if not ranked:
            print("[%2d/%d] %-18s no photograph on the page" % (index, len(page_by_stage), stage))
            continue

        best_tier = ranked[0][0]
        candidates = [name for tier, name in ranked if tier == best_tier]
        tier = best_tier

        info = image_info(candidates)
        landscape = {
            name: value for name, value in info.items()
            if value.get("width", 0) > value.get("height", 0)
        }
        if not landscape:
            print("[%2d/%d] %-18s candidates are not landscape" % (index, len(page_by_stage), stage))
            continue
        best = max(landscape, key=lambda name: landscape[name].get("width", 0) * landscape[name].get("height", 0))
        entry = landscape[best]
        extension = os.path.splitext(best)[1].lower() or ".png"
        destination = os.path.join(OUT_DIR, stage + extension)
        try:
            written = download(entry["url"], destination)
        except Exception as error:  # noqa: BLE001
            print("[%2d/%d] %-18s download failed: %s" % (index, len(page_by_stage), stage, error))
            continue

        chosen[stage] = {
            "file": best,
            "page": page,
            "url": entry["url"],
            "width": entry.get("width"),
            "height": entry.get("height"),
            "bytes": written,
            "mime": entry.get("mime"),
            "local": destination,
            "tier": tier,
        }
        print("[%2d/%d] %-18s T%d %4dx%-4d %6.1f MB  %s" % (
            index, len(page_by_stage), stage, tier, entry.get("width", 0), entry.get("height", 0),
            written / 1024 / 1024, best))
        time.sleep(SLEEP)

    print()
    print("chosen:", len(chosen), "of", len(STAGES))
    big = [s for s, v in chosen.items() if (v["width"] or 0) >= 3000]
    print("at 3000px wide or more:", len(big))
    absent = [s for s in STAGES if s not in chosen]
    if absent:
        print("still missing:", absent)

    json.dump({"stages": chosen, "missing": absent}, open(REPORT, "w", encoding="utf-8"), indent=1)
    print("written:", REPORT)


if __name__ == "__main__":
    main()
