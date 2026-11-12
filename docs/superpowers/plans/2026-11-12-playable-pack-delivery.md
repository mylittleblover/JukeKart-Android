# Compatible interactive pack delivery

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Implement each task and its checks before claiming completion.

**Goal:** Deliver an APK and one `.mkwpack` containing all 328 supplied sources, verified through real import and playback before uploading with the corrected Rainbow previews.

**Architecture:** Preserve format v1. Normal tracks declare the supported Pre/Short/Bridge/Main/Outro roles; Fast files and Rainbow regions remain separate declared single-file tracks in their stage group. The app resolves these known exact content digests into its already-tested interactive graphs, so no protocol role is forged and no stage is inferred from a display title.

**Tech Stack:** Kotlin/Compose/JNI, existing pack v1, Python ZIP64 builder, Android 15 emulator.

**Spec:** User requests on 2026-11-12: upload APK/previews/a compatible pack together, only after confirming import and playback. Existing MINF compiler and exact SHA identity manifest are authoritative for interactive playback.

## Global constraints

- Keep all 328 real sources; do not duplicate or manufacture missing audio. Only course sources are available; do not claim a full Jukebox/OST pack.
- Preserve corrected Rainbow handover 218734 and Area3 crossfade 48000.
- Keep format v1 and all hash/integrity checks. Unknown identities retain generic declared playback.
- Do not freeze a guessed unlock qualification scope.
- Never commit game audio, APK, generated pack or preview WAVs.

## Review focus

- Translated/renamed track titles and digest filenames still select the exact stage.
- Main and Pattern2 select their own Fast without mixing arrangements.
- Rainbow needs all 15 sources; missing, altered or ambiguous sources fail explicitly.
- An installed stage can be selected without relying on loose-file library rows.
- Import and AAudio/Native playback must be observed; archive validation alone is insufficient.

### Task 1: Pack reader integration

Files: create `interactive/PackInteractiveSources.kt`; modify `InteractiveAudioIdentities.kt`, `player/PlayerViewModel.kt`; test `interactive/PackInteractiveSourcesTest.kt`.

- [x] Add failing tests for normal Fast supplement, distinct Pattern2 Fast, Rainbow completeness and corrupted supplementary bytes.
- [x] Run tests and verify behavioral failures.
- [x] Expose digest lookup, expand only sources declared in the same stage group, verify loaded bytes against exact digests, and reuse the existing compiler.
- [x] Make installed-stage selection and code normalization independent of loose-file rows; preserve display titles.
- [x] Run focused tests and relevant regressions.

### Task 2: Build and independently verify the real pack

Files: create `tools/build_interactive_pack.py`; test `tests/test_interactive_pack.py`.

- [x] Test v1 grouping, Fast standalone roles, Pattern2 separation and all Rainbow roles.
- [x] Build from the app's exact source identity manifest and measured BWAV headers, including loops and checksums.
- [x] Run `tools/verify_pack.py`, Kotlin installer/reader checks, and prepare all stage/variant graphs using installed bytes.

### Task 3: Android import and playback acceptance

Files: create `app/src/androidTest/kotlin/com/mylittleblover/mkwplayer/ResearchPackImportDeviceTest.kt`; version the compatible APK as alpha07.

- [x] Transfer the real pack; invoke the application's real import path.
- [x] Confirm successful import, all 30 stages, active output and increasing Native source position.
- [x] Select and play normal/Fast-capable, explicit-zero Full-entry and Rainbow stages from the installed pack; advance a real Pre→Full transition.
- [x] Build final APK, verify signature, run whole JVM suite and preserve device evidence.

### Task 4: Upload verified deliverables

- [x] Combine alpha07 APK, the tested pack, corrected Rainbow preview ZIP, instructions and SHA256 table; test ZIP CRC and every member hash.
- [x] Upload to the user-authorized free temporary host and verify returned download bytes/size/hash.
- [x] Give the actual download link and expiry, then resume the pending map/official-route/multilingual work.

## Delivery evidence

- Commit f183aa3, actual Android 30/30 output and FirstCircuit transition passed.
- Uploaded APK/pack ZIP 445750587 bytes; downloaded SHA256 6c9516316a8b2f9e68e61398472f8fc2b2c2b929dcccad80e250c7fc80b033de.
- Uploaded all previews ZIP 725990630 bytes; downloaded SHA256 04d00ee4fe5c0f9231946fa60b5da410a30b701064e928869bf57f420e552f45.
- Litterbox and temp.sh returned HTTP 500 on large files; Filebin accepted both, direct downloads were checked in full. Current expiry 2026-11-19 UTC.
- Next work order supersedes original last bullet: user newly prioritizes Donkey/Cinema/Snow Pattern controls before map. Execution continues in 2026-11-12-pattern-controls.md.
