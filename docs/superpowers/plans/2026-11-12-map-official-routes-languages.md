# Map, published official routes and extensible language support

Execution follows completed Pattern control acceptance. User explicitly requests continuous implementation and retains all previous requirements; no new approval gate.

## Binding scope

- Selected white Material 3 design A, existing actual map, StageArt icons and measured anchors. Course map crops remain placeholder covers until user supplies final scenery/art.
- Only published official route sequences: 8 GP cups and 12 Rally routes from MarioWiki revision 5544799 (source snapshot already fetched). No nearest-neighbour graph, inferred reverse edges or custom route creation before the user's directed connectivity table arrives.
- A chosen official sequence is immutable. Mark explicit start/end and preserve stop occurrences. Future custom routes extend only their current tail; never branch from earlier stops.
- Schematic map connectors communicate the published stop order, not measured road geometry. Label that meaning; do not manufacture road polylines. No whole-route music playback button until route audio sequencing is implemented and verified.
- Extensible zh-Hans / zh-Hant / en / ja, plus follow system. Stable identities and order never depend on translated text. New controls use resources; names may come from versioned localized metadata with English fallback.

## Tasks

### 1. Official immutable route data and names

- Create internal `official_routes.json` and source-backed localized-name data from `/workspace/mkw-planning/official-route-sources` (50 raw pages). Keep source URLs/revision/hash metadata.
- Add Kotlin read-only route model/parser using stable IDs and indexed stop occurrences. Explicit start/end; no edit/copy/connect API.
- RED→GREEN tests: 8x4 GP and 12x6 Rally, all stops among actual 30 anchors, known published orders, no future routes, duplicates retained by occurrence, nullable unlock closed for Special, four language coverage/fallback. Validate all source names before using them.

### 2. Shared map geometry and camera

- Pure map transform with actual image aspect, inverse/project, centered clamping and centroid-preserving zoom. One transform drives bitmap, icons, selected-route arrows and hit targets.
- Save normalized camera/selection state across recreation; allow zoom from whole-map fit to detailed view. Start from the selected design's viewport crop; reset is a visible 48dp control.
- RED→GREEN known-coordinate/roundtrip/clamp tests at portrait, landscape and alternate image aspect. Accessibility targets are at least 48dp, not 48 raw pixels.
- Replace dark/yellow placeholder pins with original StageArt icons and white overlay controls. Route start/end and ordered stops remain clear when repeated courses occur.

### 3. Read-only official route map UI

- Selected GP/Rally route highlights only its published sequence; localized stop list and explicit start/end. No editing/copying/custom branch affordances.
- Course taps may open/play the selected installed course; unavailable audio uses the existing honest availability state. Do not claim route-wide automatic playback.
- Special route is hidden until real unlock; infer only Special-exclusive map courses from published route membership (Rainbow), never hide Acorn/Mario Circuit used by normal published rallies.

### 4. Application language interface

- Unified language configuration and resource locale declaration. Android13+ LocaleManager, persistent pre33 override, follow system.
- Preserve navigation, selected official route and map camera on Activity recreation; process-wide audio session and identities continue unchanged.
- New strings in four locales. Locale aliases zh-CN/SG -> Hans and zh-TW/HK/MO -> Hant, English fallback. Do not claim legacy screens fully translated just because new resources exist.

### 5. Validation and next handoff work

- JVM regression, actual APK build, Android locale switch/map selection/rotation, screenshots at phone/tablet/landscape and accessibility/touch target review.
- Then continue the remaining handoff: complete library/backed import recovery, immutable queue/frozen sessions, real Jukebox candidate pool, official route audio transitions, remaining UI/acceptance. Keep missing source/table items explicit; never populate guessed values.

## Pre-flight interfaces

- Routes -> map uses anchor stage IDs (normalize `_00` once), ordered indexed occurrences, and localized names; display strings never become audio keys.
- Locale -> navigation requires rememberSaveable/Saver before recreation, because existing NavState uses only remember.
- Map -> playback calls existing installed-stage resolution; planning selection is separate from playback commands.
- Official sequences do not feed legacy undirected MapRoad/nearestNeighbourRoads/custom RouteModel.

## Later route-audio constraint to preserve

Never implement ordinary Outro→next Pre by waiting for an Outro commit and then polling Play(nextPre): the already-verified MooMooFarm Outro06→ChocoMountain Pre starts the destination before Outro starts (33900-frame lead). Route sequencing must compile the Full exit, Outro bridge and next Pre into one Native timeline with sufficient request lead and finite Outro tail, reusing production code in its previews. The UI End selector must consider feasible first-start deadlines, not merely the next Full marker, so a missed pre-entry deadline does not unnecessarily wait a complete loop. This audio integration follows the read-only official-map stage.
