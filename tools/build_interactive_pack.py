"""Build a strict v1 course-source pack for the exact interactive identity catalog.

The legacy pack builder guesses groups from names. This writer instead requires every
source in the supplied identity catalog, preserves arrangements, and declares Fast and
Rainbow source files as separate v1 tracks in their stage group.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import struct
import zipfile
from pathlib import Path

RAINBOW_ROLES = ['Area1_Inst', 'Area1_Pre', 'Area1_Bridge', 'Area1_Melody', 'Area2',
                 *[f'Area2_Outro{n:02}' for n in range(5)], 'Area3_Pre', 'Area3', 'Final_Pre', 'FinalLapFanfare', 'Final']
WIRE_ROLE = dict(Main='MAIN', Pre='PRE', PreShort='PRE_SHORT', Bridge='BRIDGE', Outro='OUTRO')
TIMING = dict(preludeExit='EXIT_CUE', bridgeEntry='NEXT_BAR', outroEntry='NEXT_BAR',
              outroSwitch='IMMEDIATE', mainRepeats=2, bridgeFadeOutBeats=0, bridgeFadeInBeats=0)


def base_stage(stage):
    return re.sub(r'_Pattern\d+$', '', stage)


def create_tracks(entries):
    confirmed = {}
    current = None
    trigger_table = Path(__file__).resolve().parents[1] / 'docs/research/BGM_Stage_Outro_Triggers.md'
    for line in trigger_table.read_text().splitlines():
        header = re.fullmatch(r'## (\w+) \([\d.]+ BPM\)', line)
        if header:
            current = header[1]
            confirmed[current] = set()
        marker = re.match(r'\| Outro(\d+)', line)
        if marker and current:
            confirmed[current].add(int(marker[1]))
    groups = {}
    for row in entries:
        groups.setdefault(row['stage'], []).append(row)
    tracks, stages = [], {}

    def add(stage, title, primary, rows, standalone=False):
        parts = []
        for r in rows:
            role = 'MAIN' if standalone else WIRE_ROLE[r['role']]
            p = dict(asset=r['sha256'], role=role)
            if role == 'OUTRO':
                p['outro'] = int(re.search(r'_Outro(\d+)', r['fileName'])[1])
            parts.append(p)
        key = 'course:' + primary['sha256'][:32]
        track = dict(key=key, kind='COURSE', title=title, primary=primary['sha256'],
                     isFlow=len(parts) > 1, parts=parts)
        if track['isFlow']:
            track['timing'] = dict(TIMING)
        tracks.append(track)
        stages.setdefault(base_stage(stage), []).append(key)

    order = sorted(groups, key=lambda s: (s != 'FirstCircuit', s == 'Rainbow', base_stage(s), s))
    for stage in order:
        rows = groups[stage]
        if stage == 'Rainbow':
            by_name = {r['fileName']: r for r in rows}
            required = {f'BGM_Rainbow_{r}.bwav' for r in RAINBOW_ROLES}
            if set(by_name) != required:
                raise ValueError('Rainbow needs its complete 15-source set')
            for role in RAINBOW_ROLES:
                r = by_name[f'BGM_Rainbow_{role}.bwav']
                add(stage, 'Rainbow · '+role, r, [r], standalone=True)
            continue
        mains = [r for r in rows if r['role'] == 'Main']
        if len(mains) != 1:
            raise ValueError(f'{stage} needs exactly one Main')
        core = [r for r in rows if r['role'] != 'Fast']
        if stage in {'Cinema_Pattern2', 'SnowMountain_Pattern2'}:
            # These shared exits are explicitly covered by the confirmed catalog/previews.
            if stage not in confirmed:
                raise ValueError('Missing confirmed shared exits for '+stage)
            core += [r for r in groups.get(base_stage(stage), []) if r['role'] == 'Outro'
                     and int(re.search(r'_Outro(\d+)', r['fileName'])[1]) in confirmed[stage]]
        core.sort(key=lambda r: (list(WIRE_ROLE).index(r['role']), r['fileName']))
        add(stage, stage, mains[0], core)
        for fast in (r for r in rows if r['role'] == 'Fast'):
            add(stage, stage+' · Fast', fast, [fast], standalone=True)
    keys = [t['key'] for t in tracks]
    if len(set(keys)) != len(keys):
        raise ValueError('Derived logical track key collision')
    return tracks, [dict(code=code, name=code, tracks=keys) for code, keys in stages.items()]


def build(source_root: Path, identities: Path, output: Path):
    entries = json.loads(identities.read_text())['entries']
    files = {}
    for p in source_root.rglob('*.bwav'):
        if p.name in files:
            raise ValueError('Duplicate source name: '+p.name)
        files[p.name] = p
    assets, sources = [], {}
    for r in entries:
        p = files.get(r['fileName'])
        if p is None:
            raise ValueError('Missing required source: '+r['fileName'])
        data = p.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if digest != r['sha256'] or data[:4] != b'BWAV':
            raise ValueError('Source identity mismatch: '+r['fileName'])
        channels = struct.unpack_from('<H', data, 14)[0]
        codec, rate, frames = struct.unpack_from('<H', data, 16)[0], struct.unpack_from('<I', data, 20)[0], struct.unpack_from('<I', data, 28)[0]
        if codec != 1 or rate != 48000 or not 1 <= channels <= 2 or frames <= 0:
            raise ValueError('Unexpected measured BWAV header: '+r['fileName'])
        asset = dict(digest=digest, byteSize=len(data), mediaType='audio/x-bwav', codecKind=codec,
                     sampleRate=rate, channels=channels, frames=frames)
        last, first = struct.unpack_from('<II', data, 76)
        if last != 0xffffffff:
            if not 0 <= first < last <= frames:
                raise ValueError('Invalid measured loop: '+r['fileName'])
            asset.update(loopStart=first, loopEnd=last)
        assets.append(asset)
        sources[digest] = p
    tracks, stages = create_tracks(entries)
    manifest = dict(formatVersion=1, packId='org.mkwplayer.interactive.courses', packVersion=1,
                    name='MKW Player · 30 赛道互动音源', kind='FULL', basePackId=None, basePackVersion=None,
                    coverage=dict(totalLogicalTracks=len(tracks), playableLogicalTracks=len(tracks), codecKinds=[1]),
                    assets=assets, tracks=tracks, stages=stages,
                    provenance=[dict(subject='pack', field='assets', level='MEASURED_L2',
                        note='Exact supplied BWAV SHA-256 and headers; all source groups independently rendered by the Native preview runner.')] +
                        [dict(subject=t['key'], field='timing', level='CHOSEN_BY_PROJECT',
                              note='v1 fallback arrangement; alpha07 identifies these exact bytes and uses its validated MINF interactive compiler.')
                         for t in tracks if t['isFlow']],
                    unlock=dict(frozen=False, stages=[s['code'] for s in stages]))
    catalog = dict(tracks=[{k:t[k] for k in ['key','kind','title']} for t in tracks],
                   generatedBy='tools/build_interactive_pack.py', scope='328 supplied course sources; not the full Jukebox/OST library')
    def encoded(x): return json.dumps(x, ensure_ascii=False, separators=(',', ':')).encode()
    texts = {'manifest.json': encoded(manifest), 'catalog.json': encoded(catalog)}
    checksums = {k:dict(sha256=hashlib.sha256(v).hexdigest(), bytes=len(v)) for k,v in texts.items()}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as z:
        z.writestr('manifest.json', texts['manifest.json'])
        z.writestr('checksums.json', encoded(checksums))
        z.writestr('catalog.json', texts['catalog.json'])
        for digest,p in sorted(sources.items()):
            with p.open('rb') as src, z.open('audio/'+digest, 'w', force_zip64=True) as dst:
                shutil.copyfileobj(src,dst,1024*1024)
    return dict(path=str(output), bytes=output.stat().st_size, assets=len(assets), stages=len(stages), tracks=len(tracks),
                sha256=hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sources', type=Path, required=True)
    p.add_argument('--identities', type=Path, default=Path('app/src/main/assets/interactive_audio_identities.json'))
    p.add_argument('--out', type=Path, required=True)
    a=p.parse_args()
    print(json.dumps(build(a.sources,a.identities,a.out),ensure_ascii=False,indent=2))
