#!/usr/bin/env python3
"""Index identities of supplied BWAVs; the output contains hashes and names, never audio."""
import argparse
import hashlib
import json
from pathlib import Path


def generate(root):
    rows = []
    for folder in sorted(root.iterdir()):
        if not folder.is_dir():
            continue
        for path in sorted(folder.glob('*.bwav')):
            stem = path.stem
            stage = folder.name.removeprefix('Stage_')
            role = next((role for suffix, role in (
                ('_Pre_Short', 'PreShort'), ('_Pre', 'Pre'), ('_Bridge', 'Bridge'),
                ('_Outro', 'Outro'), ('_Fast', 'Fast')) if suffix in stem), 'Main')
            if '_Pattern' in stem:
                stage += '_Pattern' + stem.rsplit('_Pattern', 1)[1]
            rows.append(dict(stage=stage, role=role, fileName=path.name,
                             sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    assert len(rows) == 328 and len({r['sha256'] for r in rows}) == 328
    return dict(version=1, entries=rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('audio_root', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(generate(args.audio_root), ensure_ascii=False, separators=(',', ':')) + '\n')
