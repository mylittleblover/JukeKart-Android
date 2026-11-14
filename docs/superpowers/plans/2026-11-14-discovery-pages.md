# Discovery and course pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现方案 A 的首页、六分类媒体、收藏、官方杯赛详情、赛道浏览与真实互动入口，并为后续普通播放/列表编排保留稳定接口。

**Architecture:** 以已验证的 `OfficialRouteCatalog`、`LocalizedNames`、`MapAnchors` 组成只读浏览目录；用户收藏与每日推荐单独存储，音乐身份和显示语言分离。播放器继续由现有 PlayerViewModel/Native 驱动；本计划不以假的普通播放替代真实编排。MkwApp 只负责导航和页面组装，屏幕、目录及用户数据各自独立文件。

**Tech Stack:** Kotlin、Jetpack Compose Material3、StateFlow、现有 Android/Native 音频接口；不新增UI或图片加载库。

**Spec:** `docs/superpowers/specs/2026-11-14-discovery-pages.md`

## Global Constraints

- 最终四个一级入口：首页 / 媒体 / 地图 / 播放列表。
- 每日推荐仅放顶部大封面轮播；10首、手机本地日期零点更新、日内顺序固定；赛道与自由驾驶约各半。只推荐可用且允许显示的内容。
- 媒体六分类：大奖赛 / 生存赛 / VS赛 / 自由驾驶音乐 / 其他配乐 / 短音效。
- 白底轻量 Material 3 方案 A；地图截图封面全部为占位，正式素材由用户后续提供。
- 所有新字符串支持 zh-Hans / zh-Hant / en / ja；语言与展示名不进入音乐、路线、列表或导航身份。
- 页面切换、语言更改、旋转与浏览详情不停止或重新初始化音频。
- `minSdk=26`、`compileSdk=35`、`targetSdk=35`；触摸目标至少48dp；禁止无界嵌套纵向滚动。
- 自定义连线等待有向表；官方路线只读，不复制、不编辑、不伪造道路或Jukebox候选。

## Review Focus

- 候选不足、重复ID或某类音乐完全缺失时，每日推荐仍只含真实可用且唯一的音乐；素材补入不打乱当天已有顺序。Task2测试。
- 语言切换和全角/日文/中文搜索不能改变身份或暴露锁定曲目。Task1测试。
- 从杯赛进入详情，再打开设置切语言，返回应逐层回原页面；旧版保存状态也能恢复。Task3测试。
- 文件损坏或连续快速收藏操作不能静默覆盖旧数据或丢失一次切换。Task2真实文件测试。
- 矮横屏、大字体或缺失音源时，控件可达且只触发所选赛道，不播放另一条已打开的赛道。Task4/5设备测试。

---

### Task 1: Canonical browse catalog and multilingual search

**Files:** Create `app/src/main/kotlin/com/mylittleblover/mkwplayer/library/BrowseCatalog.kt`; modify `routes/OfficialRouteCatalog.kt` (LocalizedNames search accessor); test `app/src/test/kotlin/com/mylittleblover/mkwplayer/library/BrowseCatalogTest.kt`.

**Interfaces:**
- Consumes `OfficialRouteCatalog.routes/visible(Boolean?)/stageCodes(Boolean?)`, `LocalizedNames.name(id,languageTag)`, `MapAnchors.Catalog.stageTolerant(code)`.
- Produces `BrowseCatalog.courses(specialUnlocked: Boolean): List<String>`, `searchCourses(query: String, specialUnlocked: Boolean): List<String>`, `route(id: String, specialUnlocked: Boolean): OfficialRoute?`; returned course IDs are canonical stage codes, never translated text.

- [ ] Write tests for 30 unlocked/29 locked courses, BigTree/SFCMarioCircuit/Central remaining visible, Rainbow hidden, fullwidth `ｍａｒｉｏ` and Chinese/Japanese/English searches matching source names, stable order across language choice, and no unknown stage from a route.
- [ ] Run `:app:testDebugUnitTest --tests '*BrowseCatalogTest'`; expected RED missing API or missing behavior.
- [ ] Implement immutable normalized IDs and NFKC + Locale.ROOT case folding across verified name variants; do not use nearest-neighbour routes or archive names as music labels.
- [ ] Run full `:app:testDebugUnitTest`; expected zero failures, existing explicit opt-in skips retained.
- [ ] Commit catalog/search slice.

### Task 2: Persistent favorites and real daily selection

**Files:** Create `library/FavoriteStore.kt`, `library/DailyRecommendations.kt`, `ui/home/DiscoveryViewModel.kt`; tests `library/FavoriteStoreTest.kt`, `library/DailyRecommendationsTest.kt`.

**Interfaces:**
- Consumes canonical IDs from Task1 and actual availability from `PlayerViewModel.canStartStage(code)` / later verified music registry.
- Produces `FavoriteStore(file: File).read(): Result<Set<String>>`, `toggle(id: String): Result<Set<String>>`; `RecommendationCandidate(id: String, kind: RecommendationKind)` with COURSE/FREE_DRIVING; `DailyRecommendations.select(day: LocalDate, eligible: List<RecommendationCandidate>, previous: List<String>): List<String>` (maximum10).
- `DiscoveryViewModel` owns serialized IO and StateFlow favorites/daily IDs; input time is the phone local date, independent of display language.

- [ ] Test unique10, five/five when available, only playable supplied candidates, <10 actual results when insufficient, stable seeded order across input order/language, midnight date change, and same-day valid cached order retained when candidates are added/removed.
- [ ] Test real temporary-file roundtrip, damaged-file failure without overwriting its bytes, and two toggles preserving the final original state. No mocked persistence claims.
- [ ] Run focused tests RED, then implement atomic small-file persistence and serialized updates; cache daily IDs/date, not localized labels or audio bytes.
- [ ] Ruling to record: when one family lacks enough eligible items, fill remaining positions from the other available family; never recommend missing audio. If user supplies another ratio policy, update this reversible selection policy.
- [ ] Run full JVM suite GREEN and commit.

### Task 3: Final navigation identities and a real back stack

**Files:** Modify `ui/NavState.kt`, `ui/AppChrome.kt`, `MkwApp.kt`; tests `ui/NavStateTest.kt`, `ui/NavSnapshotTest.kt`.

**Interfaces:**
- Produces four ordered tabs HOME/MEDIA/WORLD_MAP/PLAYLISTS; current snapshot()/restore(List<String>) signatures remain.
- Add `Detail.Cup(routeId: String)` and `Detail.Interactive(courseCode: String)` alongside Course/Settings/legacy Flow. `open(detail)` pushes; `back()` pops; selecting a top-level destination returns its root.
- Restore legacy LIBRARY→MEDIA, MY_ROUTES→PLAYLISTS, INTERACTIVE→MEDIA + legacy Flow; WORLD_MAP remains. Version the new stack snapshot; old flat snapshots still load.

- [ ] Write RED tests for four final tabs, nested Cup→Course→Settings back behavior, locale-independent snapshot roundtrip, selecting current tab from detail returning to root, and legacy/invalid snapshot migration.
- [ ] Implement immutable stack and saved identity codec. Do not save labels; do not call audio Start/Stop during navigation.
- [ ] Wire compact bottom navigation and available-width rail where useful, using real icons with localized labels. Existing map state stays hoisted and saveable.
- [ ] Run JVM suite GREEN; actual navigation regression is expanded in Task5. Commit.

### Task 4: Shared artwork, home and media screens

**Files:** Create `ui/art/CourseCover.kt`, `ui/home/HomeScreen.kt`, `ui/media/MediaScreen.kt`, `ui/media/CupScreen.kt`; add four-language discovery resources; modify MkwApp assembly.

**Interfaces:**
- Consumes Tasks1/2 stable IDs; resolves labels from LocalizedNames at render time. `CourseCover(stageCode, formalPainter?, modifier)` uses supplied formal art when present, otherwise the known map anchor crop with a placeholder label; non-course missing art uses a neutral placeholder.
- Home callbacks open Course/Cup details, toggle favorites, import actual music and open the real current performance; no automatic audio start from a recommendation tap.
- Media callbacks select one of six categories, update real search query, open verified Cup/Course/route details; data-free categories show empty states.

- [ ] Add device tests before implementation: home has one daily cover region, media exposes six categories, search finds Mario Bros. Circuit, favorite persists after fresh Activity, and Cup opens its actual four ordered courses.
- [ ] Watch RED against old UI. Implement lazy/responsive layouts from selected A reference, with home recommendation only in the hero; no duplicate daily row.
- [ ] Continue-listening card is nullable and uses actual unfinished state. Never show “saved session” until a persisted restore record really exists.
- [ ] Run device tests at compact/wide/short landscape, JVM suite and APK build; capture actual screenshots. Commit.

### Task 5: Course presentation and verified interactive entry

**Files:** Create `ui/detail/CourseScreen.kt`; extract/update `ui/play/InteractiveScreen.kt`; modify MkwApp wiring; tests `CourseNavigationDeviceTest.kt`, existing `InteractiveNavigationDeviceTest.kt`.

**Interfaces:**
- Course details receive canonical courseCode, current availability and current matching flow. Only `model.isStageOpen(code)` may bind active flow details.
- Default interactive entry invokes existing `startStagePerformance(code)` / `startPerformance()` only after user Start. Advance/fast/outro/cancel/region/version/pause/resume/stop call the existing Native-backed APIs.
- Ordinary preset card is visually separate and keeps Complete suite as selected identity; actual normal playback is integrated in the next preset/player plan, never delegated to an infinite manual Pre loop.

- [ ] Write RED tests: Media→selected Course→interactive view opens without crash, source-missing view explains import, active controls refer to selected code, Back returns through Cup, and settings language recreation preserves this path.
- [ ] Implement two full-width cards and a dedicated performance view; move diagnostics below an explicit details section rather than the normal primary controls. Remove unrelated settings/import-list nesting from the final console.
- [ ] Keep progress read-only for interactive mode and show Native state; preserve cancellation before actual destination start, versions/regions and strict one-way phases. Do not add fake direct entry choices without a validated start API.
- [ ] Run real navigation tests, JVM suite, APK build and screenshots. Commit this UI slice; do not declare normal presets/queue/restore done.

## Completion and continuation

This subproject is complete only after its real UI tests and whole-branch review pass. Then immediately continue separate plans for finite ordinary presets/player/queue, durable library import/restore and remaining acceptance. No new user approval gate: the user explicitly asked to proceed. Missing directed roads and unverified Jukebox relationships remain blocked as data, while independent pages continue.
