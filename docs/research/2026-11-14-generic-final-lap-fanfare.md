# Generic FinalLapFF → Fast

Applies to ordinary courses only. Rainbow keeps its separate, previously verified FinalLapFanfare and Final entry timing.

## Evidence

User supplied three gameplay screen recordings and `BGM_Seq_FinalLapFF.bwav` on 2026-11-14. The recordings contain loud game sound effects. Bandpass-filtered normalized correlation against independent original source windows isolates the music without using the sound effects as timing markers.

| Course | FF source-zero in recording | Fast source-zero in recording | Difference |
| --- | ---: | ---: | ---: |
| DinoBones | 1.704125 s | 5.639250 s | 3.935125 s |
| WestCity | 2.791875 s | 6.727000 s | 3.935125 s |
| DinoDinoJungle | 3.267125 s | 7.197250 s | 3.930125 s |

FF matches its native playback speed in all three recordings. Independent FF windows agree on its start. Fast matches source zero, regardless of each course's BPM or MINF phase. The FF source has 268759 stereo frames at 48000 Hz (5.5991458 s); its physical tail continues over Fast.

The application uses 188880 transport frames (3.935 s), rounded from the median observed delay. This is a reference-derived timing with roughly 5 ms inter-recording variation, **not an exact authored MINF event**. Analysis scripts and measurements are retained locally in `/workspace/mkw-planning/final-lap-evidence/`.

## User-confirmed outgoing behavior

The user explicitly requires the original Full to be interrupted immediately when switching: no fade-out, no next-bar wait, and no Full tail over FF. Only the FF tail overlaps Fast. This requirement supersedes inference about a possible short fade in the noisy recordings.

## Implementation

`FinalLapFanfareCompiler` extends the normal production graph after alternate-version and Donkey-region compilation. Existing Full→Fast edges become Immediate transitions with a shared FF bridge, zero outgoing/incoming fades, and a fixed sample timeline `(0,188880,188880,0)`. Fast starts from source frame zero. The bridge segment's logical exit is 188880 while its clip keeps all 268759 physical source frames. Native `postExit` retains that bridge tail; the looping Full has zero departure fade and stops at the Immediate source boundary.

Cinema and SnowMountain retain their independent Full→Fast paths for each version; same-speed version crossfades are unchanged. Donkey retains region 1→2→3→4→5; only region 5→Fast receives this FF. Rainbow does not pass through this compiler.

Android supplies the bundled source when opening a normal flow and preserves it in `flowBytes` for stop/restart. The shared FF is prepared before playback, including in streamed flows. Existing mkwpacks do not need reimporting. Offline legacy research graphs without the shared asset retain their prior speed edges.

## Source packaging

APK asset: `app/src/main/assets/audio/BGM_Seq_FinalLapFF.bwav` (307376 bytes).

SHA-256: `60790cdf84a2e04c9fffca7ccee8bafa5be2bfb6ceb6dfd4f98ecfc8bdd8ce94`.

The supplied binary is excluded from Git, like other audio assets. To reproduce the APK in another checkout, copy the user's supplied BWAV to that asset path. Production compilation validates the source digest and format.

## Verification scope

A new real-source graph test first failed because FF was absent. The follow-up test run was stopped when the user requested immediate build delivery and their own testing. A read-only independent code review found no Critical/Important issues. No new PCM or device playback acceptance is claimed for alpha13; the user will perform practical validation.
