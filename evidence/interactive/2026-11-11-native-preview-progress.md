# Interactive research playback progress

Status: WIP. Application controls are not yet connected to the new compiler. No Android device/audio-output acceptance is claimed.

Source: user-supplied 2026-11-12 research archive, SHA-verified original BARS, and separately user-confirmed `docs/research/BGM_Stage_Outro_Triggers.md`. Real BWAV ZIPs downloaded from the user's Drive folder remain outside the repository. The 328 original MINF records and 5,114 Pre beat pairs are preserved; non-Pre grids are exported only where their binary layout validates. Five other grids remain explicitly unparsed. A numerical candidate is not upgraded to verified gameplay permission.

Checks performed:

- Reproduced research Pre JSON exactly; 61 resources, 5,114 nodes, 1,311 label-1 candidates, 1,111 within loop windows; WestCity 33 points retain the user's reported listening evidence.
- JVM suite passed after repairing existing RoutePage import/visibility build failures. Native preview test is opt-in and skipped without external audio configuration.
- Python suite: 143 passed; one external pytest plugin deprecation warning.
- Host Native Engine built from the same engine/native sources as Android.
- WestCity Native integration: both Pre entries -> Bridge -> Full, 13 individual confirmed Full-to-Outro points, Full-to-Fast phase-alignment candidate, and a late request deferred from Pre 416953 to 506953. Render tests passed with no rejected commands and no clipped PCM samples.
- This is deterministic Native output, not an Android device recording and not an independent listening/gameplay comparison. Fast alignment remains a research candidate. Whole-track acceptance remains pending.

## Compiled graph v14 extension

Versions 1-13 retain their byte layout. Version 14 appends a transition-timeline table after the v13 clip-envelope section. Each record is a transition index (i32), then bridgeStart, destinationStart, destinationCommit, destinationSource (all signed i64). The first three are fixed Transport-frame offsets relative to the selected source cue; destinationSource is an actual destination source position. Negative start offsets express advance playback, not a hidden seek to source zero. Destination commit must be nonnegative and no earlier than either participating start; source offset must be nonnegative and within the named destination.

No game format names or business rules are added to the Engine. MINF interpretation stays in the MKW compiler. A new cue search minimum-delay argument allows scheduling to skip candidates whose advance playback deadline has already passed, including through source loops.

Next priority per user: IceBuildings. Verify Full source 34286 at Bridge_ToFull 68571; capture both Pre entries, Fast and all six confirmed Outro exits. Raw recordings and logs are kept under /workspace/mkw-recordings, never committed as game assets.
