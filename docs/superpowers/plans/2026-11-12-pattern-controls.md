# Pattern controls implementation

User explicitly authorizes continuous execution after baseline verification; existing requirements and current-delivery-and-next-work.md remain binding.

## Design

Reuse the production MINF graph, extend it with real alternate source segments and explicit transitions. Cinema/SnowMountain preserve source position within the same Full or Fast speed using SameTime with a one-second linear crossfade. DonkeyKong regions use source label-1 cues and destination phase. Never transplant base Outro positions to Donkey patterns. Exact content identities and declared pack stage membership determine available sources.

UI observes committed Native segment and pending destination; it must not announce a new version before Native starts it. During an active fade controls remain disabled to bound voices and prevent conflicting transitions; after completion reverse selection is available. This is an implementation ruling: deterministic two-source fades are preferable to layering a third version request.

## Tasks

1. Compile Cinema/SnowMountain alternatives into the actual production plan. RED tests: alternate main/Fast present, SameTime explicit 1 s fades, selected-version Fast, no cross-speed SameTime. GREEN: augment compiler and binding order, reject incomplete variant pairs. Keep packets and labels consistent.
2. Produce Native previews with the production plan. Test normal and loop-edge both directions for Full/Fast; inspect real position, voices and samples. Fix loop-edge retention based on evidence.
3. Resolve exact alternate pack sources and wire controls through driver/ViewModel/UI. Add four-language resource strings. Verify pending/current state, pause and invalid selection boundaries.
4. Implement Donkey regions from exact raw MINF, with controls and Native WAVs for supported region transitions, plus cancellation before target begins.
5. Run regressions and Android acceptance for new controls. Deliver new APK/previews only once corresponding checks pass, then proceed to map/official routes/multilingual tasks.

## Ledger

- Baseline alpha07 real Android import/playback completed 30/30 and FirstCircuit Pre→Bridge→Full; upload is underway in parallel.
- No new Pattern controls are claimed in alpha07.

- Native budget ruling: combined source sets decode to Cinema 136317616 / SnowMountain 169063160 bytes, over the 128 MiB prepared cap. Mark these production plans streamed and propagate that policy on both initial load and reload; preview uses the same policy. Do not increase global memory budget.

- Fresh review: one Important UI regression found: legacy non-MINF flows never publish canFast. Restored their previous canEnd && !fastPlaying eligibility, while version-aware flows use the Native canFast guard. No other blockers reported; no tests were run by reviewer.

## Current execution evidence

- Cinema/Snow source+controls commit 9da9789; full JVM 1164 / 0 fail / 8 opt-in skips and APK assembly passed, followed by fresh review fix preserving legacy Fast eligibility. 24 Native WAV and fade cursor checks passed, per-track preview ZIPs uploaded and fully downloaded/hash-matched. Android control test interrupted by environment restart; restart later hit process-start ANR under software emulation load, not yet accepted.
- Donkey production graph, SHA-declared source resolution and controls implemented. 40 Native WAV cases pass after raw cue sort regression RED→GREEN. Dedicated plan/driver/pack tests passed. Full JVM with real final pack passed before cancellation changes; alpha08 full suite/build now includes cancellation.
- New Native command17 cancellation boundary first proved rejected before implementation; then G−1/G/G+1 acceptance/rejection passed. Expanded 40-case boundary/pause/source-progress run is underway, as is independent code review.
- Deliveries remain baseline alpha07 and separate newly verified audio previews; no alpha08 APK has been submitted before its device acceptance.

- Device regression found after real import: immediately selecting the next stage could keep main-thread source binding busy before the newly requested Service got its onCreate foreground promotion. New suspend PlaybackStartupGate yields that looper, releases only after service setup/promotion, and newest pack request wins; Stop/direct selections invalidate suspended requests. Kept the rapid-selection device test unchanged rather than hiding the race with a notification wait. Gate tests RED→GREEN; full rebuild/device rerun pending.
- Expanded cancellation harness initially reported 20 loop-end failures because it required an active Voice at exact EOF. Fresh review identified that transient EOF voice retirement cannot establish a source restart. Replaced that criterion with an uninterrupted reference engine, sample PCM/transport comparison through G+1024 and then matching source phase. Kept the failed diagnostic report, did not alter Native cancellation to fit the test. Reference rerun is in progress.

- Final reference cancellation run: 40/40 PASS, three boundary subcases each (120) including pause; successful cancellation PCM equals uninterrupted reference exactly and subsequent source phase matches. Prior absolute-cursor failures were exact-EOF absent-Voice observations, not actual restarts.
- Driver completion now requires Native transport STOPPED as well as no voices; loop-boundary voice retirement alone is not completion. Regression RED→GREEN.
- Foreground gate strengthened to per-start generation acknowledged only from its onStartCommand, not a boolean cleared by an old service instance. Three coroutine cases prove latest-generation waiting, repeated running-service starts, and old callback monotonicity; fresh focused review found no blockers. Full frozen build passed 1175 JVM / 0 failures / 8 opt-in skips and all four ABIs, signature verified; final Android re-run has exclusive CPU after resuming the paused AVD.

- Real diagnosis captured DeadlineMiss on Cinema alternate Full while source frames continued. Native realtime page preparation cannot guarantee arbitrary-position Immediate/SameTime from cold streamed buffers. Final mixed preparation stores exactly4 Full/Fast version sources decoded, others streamed, with strict128MiB check (83.05/81.10MiB). No graph/timing change. Regression/whole suite1176 JVM0fail8skip, fourABI build passed. Real Android FullVersionControlsDeviceTest now PASS for Cinema+Snow; Donkey combined test running. Latest user says finish/deliver THIS part then STOP; do not proceed to map implementation.

## Final Android acceptance and stop boundary

- Mixed preparation actual Android combined run PASS: `OK (2 tests)`,924.204 seconds, FullVersionControlsDeviceTest and DonkeyRegionControlsDeviceTest. Both source-arrangement stages pass Full2/Fast2/Fast1, all five Donkey regions pass queued cancellation/return-to-base and Stop→Start foreground retention.
- Real installed pack digest matches final delivered SHA adf8a6fcf98055342619863e11fb201a39e8d449f42172066bb174a69972e1a5.
- Final APK is the exact tested /workspace/mkw-planning/alpha08-hybrid.apk, SHA9e818ab46287e05aec4309f04f6999e869c0602460c0dac5d62e3bc6f9941ce5. JVM1176,0failure,8opt-in skips; fourABI assembly passed. Fresh hybrid review reports no blockers.
- Per latest user instruction, after final package upload/download integrity verification, STOP. Map/official routes/full application language work stays planned, not implemented.
