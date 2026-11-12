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
