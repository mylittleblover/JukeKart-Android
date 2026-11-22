"""Run the decoder probe over the whole jukebox corpus and summarise what actually decodes.

The probe is the real engine: openBwav() and the audio frame source, nothing reimplemented. This just
drives it over every file and counts the outcomes so a claim like "227 jukebox tracks now decode" is a
measurement rather than an extrapolation from one file.
"""
import os
import re
import subprocess
import sys

_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STREAM = os.environ.get("MKW_ROMFS_STREAM", os.path.join(_REPO, "romfs", "Sound", "Resource", "Stream"))
ADB = os.environ.get("ADB", "adb")
REMOTE = '/data/local/tmp/probe.bwav'
PROBE = '/data/local/tmp/mkw_bwav_decode_probe'


def push_and_probe(local):
    subprocess.run([ADB, 'push', local, REMOTE], capture_output=True, check=True)
    result = subprocess.run(
        [ADB, 'shell', 'LD_LIBRARY_PATH=/data/local/tmp ' + PROBE + ' ' + REMOTE],
        capture_output=True, text=True)
    return result.stdout + result.stderr


def main():
    files = sorted(
        os.path.join(STREAM, name) for name in os.listdir(STREAM)
        if name.startswith('BGM_JukeBox_') and name.endswith('.bwav'))
    if len(sys.argv) > 1:
        files = files[:int(sys.argv[1])]
    print('files to probe:', len(files))

    ok = failed = 0
    failures = []
    silent = []
    total_frames = 0
    for index, path in enumerate(files, 1):
        out = push_and_probe(path)
        name = os.path.basename(path)
        if 'open        : Ok' in out:
            ok += 1
            match = re.search(r'render      : \d+ frames requested, (\d+) produced, status=(\w+)', out)
            frames = int(match.group(1)) if match else 0
            total_frames += frames
            peak = re.search(r'signal      : peak=([\d.]+)', out)
            if not peak or float(peak.group(1)) < 1e-4:
                silent.append((name, peak.group(1) if peak else 'n/a'))
            status = match.group(2) if match else '?'
            if status != 'Ok':
                failures.append((name, 'render status ' + status))
        else:
            failed += 1
            reason = 'unknown'
            for line in out.splitlines():
                if line.startswith(('parse       : FAILED', 'open        : FAILED')):
                    reason = line.split('FAILED')[1].strip()
            failures.append((name, reason))
        if index % 25 == 0 or index == len(files):
            print('  [%d/%d] ok=%d failed=%d silent=%d' % (index, len(files), ok, failed, len(silent)))

    print()
    print('=== summary ===')
    print('decoded OK  :', ok)
    print('refused     :', failed)
    print('silent      :', len(silent))
    print('frames out  :', total_frames)
    if failures:
        print()
        print('refusals:')
        seen = {}
        for name, reason in failures:
            seen.setdefault(reason, []).append(name)
        for reason, names in sorted(seen.items(), key=lambda kv: -len(kv[1])):
            print('  %-40s %d  e.g. %s' % (reason, len(names), names[0]))
    if silent:
        print()
        print('silent renders:')
        for name, peak in silent[:10]:
            print('  ', name, 'peak=', peak)


if __name__ == '__main__':
    main()
