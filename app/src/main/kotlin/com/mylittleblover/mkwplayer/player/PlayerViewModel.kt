package com.mylittleblover.mkwplayer.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.audio.TimingDiagnosis
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.library.AudioPicker
import com.mylittleblover.mkwplayer.library.CatalogLoader
import com.mylittleblover.mkwplayer.library.CatalogMatcher
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.NativeInteractiveDriver
import com.mylittleblover.mkwplayer.interactive.InteractiveAudioIdentities
import com.mylittleblover.mkwplayer.interactive.PackInteractiveSources
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.CoursePresetStore
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.UserPlaylist
import com.mylittleblover.mkwplayer.library.LibraryStore
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.library.StageFlowReader
import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import com.mylittleblover.mkwplayer.unlock.SpecialCupRules
import com.mylittleblover.mkwplayer.unlock.UnlockStore
import com.mylittleblover.mkwplayer.unlock.UnlockTestMode
import com.mylittleblover.mkwplayer.unlock.UnlockTestStore
import com.mylittleblover.mkwplayer.library.Playability
import com.mylittleblover.mkwplayer.pack.InternalPackLibrary
import com.mylittleblover.mkwplayer.pack.LibraryInvalidation
import com.mylittleblover.mkwplayer.pack.PackCoverage
import com.mylittleblover.mkwplayer.pack.PackImportChangelog
import com.mylittleblover.mkwplayer.pack.PackImportCoordinator
import com.mylittleblover.mkwplayer.pack.PackImportPhase
import com.mylittleblover.mkwplayer.pack.PackImportUiState
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.pack.PackPlayback
import com.mylittleblover.mkwplayer.pack.PackRegistry
import com.mylittleblover.mkwplayer.pack.PoolUpdateStatus
import com.mylittleblover.mkwplayer.pack.StreamingPackInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Holds the picked tracks and drives the one open transport.
 *
 * Responsibilities stay narrow: accept a picked file, prepare it once, remember what
 * was learned about it, and drive the transport through [PlaybackController]. All
 * audio semantics stay in the engine; all transport rules stay in the controller.
 *
 * Preparation is cached per digest because it costs a hash and a graph build over
 * the whole file, and re-selecting a track should not repeat that.
 */
internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    /** One row in the library. */
    data class Track(
        val key: String,
        val name: String,
        val frames: Long,
        val sampleRate: Int,
        val playable: Boolean,
        val reason: String?,
    )

    /** Everything the screen needs, as one value. */
    data class Ui(
        val tracks: List<Track> = emptyList(),
        val selectedKey: String? = null,
        val title: String = "",
        val status: String = "No track loaded",
        val position: String = "--:--",
        val total: String = "--:--",
        val progress: Float = 0f,
        val canPause: Boolean = false,
        val canResume: Boolean = false,
        val canStop: Boolean = false,
        val timing: String = "Timing unavailable",
        val timingVerdict: TimingDiagnosis.Verdict = TimingDiagnosis.Verdict.NO_DATA,
        val outputActive: Boolean = false,
        val detail: String? = null,
        val busy: Boolean = false,
        /** Bundled catalog, or null when the asset is missing or unreadable. */
        val catalog: CatalogLoader.Catalog? = null,
        /** Category currently expanded in the catalog browser. */
        val openCategory: String? = null,
        /** True when the activity should ask for POST_NOTIFICATIONS. */
        val needsNotificationPermission: Boolean = false,
        /** The loaded multi-part flow, if one is open. Null in single-track mode. */
        val flow: FlowUi? = null,
        /** Special Cup progress and the offer, or null before a library exists. */
        val unlock: UnlockUi? = null,
        val unlockTestMode: UnlockTestMode = UnlockTestMode.NORMAL,
        /** What the last pack import did, or null when none has run. */
        val pack: PackUi? = null,
        /** Cached chronological jukebox catalog built from installed packs + bundled OST metadata. */
        val jukebox: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog =
            com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog(emptyList(), emptyMap()),
        /** True while the active playback session uses standalone linear decoding (Jukebox, etc.). */
        val linearMode: Boolean = false,
        val linearTrack: LinearQueue.Entry? = null,
        val queue: LinearQueue.Snapshot = LinearQueue.Snapshot(),
        val canSeek: Boolean = false,
        val totalFrames: Long = 0L,
        val sampleRate: Int = 0,
        val artworkName: String? = null,
        val linearContextKind: LinearSessionStore.ContextKind = LinearSessionStore.ContextKind.NONE,
        val linearContextId: String? = null,
        val linearContextTitle: String? = null,
        val allowPlayNext: Boolean = true,
        val allowShuffle: Boolean = true,
        val allowSingleRepeat: Boolean = true,
        val allowRepeatToggle: Boolean = true,
        val continueListening: ContinueListeningUi? = null,
        /** Monotonic counter incremented on every library commit to re-evaluate UI state (#84-#89). */
        val libraryGeneration: Int = 0,
        /** Live single-task streaming pack import state (#91-#97). */
        val importState: PackImportUiState = PackImportUiState(),
    )

    data class ContinueListeningUi(
        val key: String,
        val title: String,
        val subtitle: String,
        val artworkName: String?,
        val positionText: String,
        val totalText: String,
        val progress: Float,
        val positionFrames: Long,
        val totalFrames: Long,
        val sampleRate: Int,
        val contextKind: LinearSessionStore.ContextKind,
        val contextId: String?,
        val contextTitle: String?,
    )

    /**
     * The imported soundtrack pack, as the screen sees it.
     *
     * [trackCount] and [playableCount] are the pack's own numbers, not the catalogue's: a pack the user
     * imported is a smaller library than everything that exists, and presenting the pack's contents as the
     * whole soundtrack is how a partial import reads as a complete one.
     */
    data class PackUi(
        val name: String,
        /** Every logical track the pack carries. */
        val trackCount: Int,
        /** How many of them this build can decode. */
        val playableCount: Int,
        /** Non-fatal differences between the pack's manifest and the files on disk. */
        val notes: List<String> = emptyList(),
        /** True while an import is running. */
        val importing: Boolean = false,
        /** Progress line, e.g. "asset 12 of 610". */
        val progress: String = "",
    ) {
        /** One line the screen can show, stating both numbers. */
        val summary: String
            get() = "$trackCount tracks, $playableCount playable in this build"
    }

    /**
     * A multi-part flow, as the screen sees it.
     *
     * `sections` are the requestable parts and `activeSegment` marks the one playing. The
     * `provenance` string is not decoration: it is the difference between showing a testing
     * configuration and presenting it as the game's behaviour.
     */
    data class FlowUi(
        val title: String,
        val sections: List<FlowPresentation.Section>,
        val activeSegment: Int?,
        val summary: String,
        val provenance: String,
        val isProvisional: Boolean,
        /** Outros this flow can end with, in the order the flow lists them. */
        val outros: List<Int> = emptyList(),
        /** True while a whole-stage performance is running. */
        val performing: Boolean = false,
        /** Where the performance is, in words. Empty when nothing is performing. */
        val performanceStage: String = "",
        /** Stable source identifiers; localized only when rendered. */
        val stageKey: String? = null,
        val pendingStageKey: String? = null,
        /** Stages present in the selected source graph, for read-only structure display. */
        val availableStageKeys: List<String> = emptyList(),
        /**
         * Why the performance cannot start, or null when it can.
         *
         * A reason rather than a disabled button: a control that is greyed with no explanation makes the
         * listener guess, and the usual guess is that the app is broken.
         */
        val performanceBlocker: String? = null,
        /** True when the flow carries a fast arrangement, so the console can show the switch. */
        val hasFast: Boolean = false,
        /** True when the fast arrangement is the one playing. */
        val fastPlaying: Boolean = false,
        val regionNumbers: List<Int> = emptyList(),
        val selectedRegion: Int? = null,
        val availableRegions: Set<Int> = emptySet(),
        val versionNumbers: List<Int> = emptyList(),
        val selectedVersion: Int? = null,
        val availableVersions: Set<Int> = emptySet(),
        val canFast: Boolean = false,
        val canCancelPending: Boolean = false,
        val sourceFrame: Long? = null,
        val sourceFrames: Long? = null,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
        val voiceCount: Int = 0,
        val transportRate: Int = 48000,
        val fading: Boolean = false,
        val fadeRemainingFrames: Long? = null,
        /**
         * The next-stage event is available.
         *
         * True only while the prelude is looping, because that is the one moment the event means anything.
         * A button that is always enabled is a button that is sometimes a no-op.
         */
        val canAdvance: Boolean = false,
        /** The ending event is available: the main loop is playing, so an ending can be chosen. */
        val canEnd: Boolean = false,
        val hasSpeakerOverlay: Boolean = false,
        val speakerEnabled: Boolean = false,
        val isBattleTrack: Boolean = false,
        val pendingSyncPoint: com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.SyncPoint? = null,
        val pendingSourceCue: String? = null,
        val nextAdvanceTargetKey: String? = null,
        val nextAdvanceSyncPoint: com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.SyncPoint? = null,
    )

    /**
     * Catalogue rows with ownership, rebuilt whenever the library changes.
     *
     * Separate from [catalog] because ownership changes as files are added or removed, while the
     * catalogue itself is bundled and static.
     */
    /**
     * Catalogue rows with ownership, rebuilt whenever the library changes.
     *
     * Owns no Android state, so it is constructed once the bundled catalogue has been parsed in
     * [init]; ownership changes as files are added or removed while the catalogue stays static.
     */
    private var browser: CatalogBrowser? = null

    /** Catalogue browser state, or null before the bundled catalogue is available. */
    val catalogState: StateFlow<CatalogBrowser.State>?
        get() = browser?.state

    /**
     * The map anchors, or null when the asset is missing.
     *
     * Loaded once and held: the map is drawn on every frame of a scroll, and re-parsing a JSON file per frame
     * would be the most expensive thing on the screen.
     */
    val mapAnchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open(MAP_ANCHORS_ASSET)
                .bufferedReader().use { it.readText() }
                .let { com.mylittleblover.mkwplayer.library.MapAnchors.parse(it) }
        }.getOrNull()
    }

    /**
     * Selects a stage from the map.
     *
     * A stage the map names but which has no files is reported rather than silently ignored: pressing a pin
     * and getting nothing is indistinguishable from a broken app.
     */
    fun selectStage(code: String) {
        if(isSpecialStageLocked(code)) return
        if(nativePerformance?.isFading==true) return
        packPlaybackRequest++
        pack?.let { installed ->
            val stage=installed.manifest.stages.firstOrNull { it.code.removeSuffix("_00").equals(code.removeSuffix("_00"),true) }
            if(stage!=null) {
                stage.tracks.firstNotNullOfOrNull { installed.manifest.track(it) }?.let { openPackTrack(it) }
                return
            }
        }
        val match = _ui.value.tracks.firstOrNull {
            it.name.contains(code.removeSuffix("_00"), ignoreCase = true)
        }
        if (match == null) {
            _ui.value = _ui.value.copy(
                detail = "No files for $code yet. Import a soundtrack pack or open the stage folder.",
            )
            return
        }
        select(match.key)
    }

    internal val currentStageCode: String?
        get() = interactivePlan?.name?.removePrefix("BGM_Stage_")?.removeSuffix("_00")

    internal fun isStageOpen(code: String): Boolean =
        interactivePlan?.name?.removePrefix("BGM_Stage_")?.removeSuffix("_00")?.equals(code.removeSuffix("_00"), true) == true

    fun canStartStage(code: String): Boolean {
        if(isSpecialStageLocked(code)) return false
        val logical=code.removeSuffix("_00")
        if(interactivePlan?.name?.removePrefix("BGM_Stage_")?.equals(logical,true)==true) return true
        val installed=pack ?: return false
        return installed.manifest.stages.any { stage -> stage.code.removeSuffix("_00").equals(logical,true) && stage.tracks.any { installed.manifest.track(it)!=null } }
    }

    /** Options are derived from verified identities in the installed pack without opening playback. */
    internal fun initialStageLabels(code: String): List<String> {
        if (isSpecialStageLocked(code)) return emptyList()
        val installed = pack ?: return emptyList()
        val logical = code.removeSuffix("_00")
        val stage = installed.manifest.stages.singleOrNull { it.code.removeSuffix("_00").equals(logical, true) } ?: return emptyList()
        val identities = stage.tracks.flatMap { installed.manifest.track(it)?.parts.orEmpty() }
            .mapNotNull { interactiveIdentities.identityForDigest(it.asset.sha256) }
            .filter { it.belongsToCourse(logical) }
        if (logical.equals("Rainbow", true)) {
            return listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre", "Final")
                .filter { label -> identities.any { it.fileName == "BGM_Rainbow_${label}.bwav" ||
                    label in listOf("Area1", "Area2", "Area3") && it.fileName == "BGM_Rainbow_${label}_Inst.bwav" } }
        }
        val primary = stage.tracks.firstNotNullOfOrNull { installed.manifest.track(it) } ?: return emptyList()
        val primaryRoles = primary.parts.mapNotNull { interactiveIdentities.identityForDigest(it.asset.sha256) }
            .filter { it.stage.equals(logical, true) }.map { it.role }.toSet()
        val groupedRoles = identities.map { it.role }.toSet()
        val basic = listOf("Pre", "PreShort", "Bridge", "Main", "Fast").filter { role ->
            (if (role == "Fast") role in groupedRoles else role in primaryRoles)
        }
        val extra = if (logical.equals("DonkeyKong", true)) (2..5).mapNotNull { number ->
            "Region $number".takeIf { identities.any { it.fileName == "BGM_Stage_DonkeyKong_Pattern$number.bwav" } }
        } else if (logical.equals("Cinema", true) || logical.equals("SnowMountain", true))
            listOf("Main Pattern2" to "Pattern2", "Fast Pattern2" to "Fast_Pattern2").mapNotNull { (label, role) ->
                label.takeIf { identities.any { it.fileName == "BGM_Stage_${logical}_$role.bwav" } }
            } else emptyList()
        return basic + extra
    }

    /** Resolve the selected course by its stable code before starting any playback. */
    fun startStagePerformance(code: String, startingStage: String? = null) {
        if(isSpecialStageLocked(code)) return
        if(nativePerformance?.isFading==true) return
        val logical=code.removeSuffix("_00")
        if(interactivePlan?.name?.removePrefix("BGM_Stage_")?.equals(logical,true)==true) {
            if (startingStage != null && !_ui.value.canStop) {
                val candidate = interactivePlan?.initialSegments?.singleOrNull { interactivePlan?.labels?.get(it) == startingStage }
                if (candidate == null) { _ui.value = _ui.value.copy(detail = "Selected starting stage is unavailable"); return }
                selectInitialEntry(candidate)
            }
            packPlaybackRequest++;startPerformance();return
        }
        val installed=pack
        val stage=installed?.manifest?.stages?.singleOrNull { it.code.removeSuffix("_00").equals(logical,true) }
        val track=stage?.tracks?.firstNotNullOfOrNull { installed?.manifest?.track(it) }
        if(track==null) { _ui.value=_ui.value.copy(detail="No playable source for $code. Import its audio first.");return }
        openPackTrack(track,perform=true,startingStage=startingStage)
    }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private data class Prepared(
        val name: String,
        val bytes: ByteArray,
        val packet: ByteArray,
        val frames: Long,
        val sampleRate: Int,
    )

    private val prepared = mutableMapOf<String, Prepared>()

    /** The open flow, if any: its compiled graph and the parts it will bind. */
    private var flow: StageFlow? = null
    private var flowCompiled: StageFlowCompiler.Compiled? = null
    private var flowBytes: Map<String, ByteArray> = emptyMap()
    private var interactivePlan: InteractivePlaybackPlan? = null
    private var nativePerformance: NativeInteractiveDriver? = null
    private val minfCatalog by lazy {
        getApplication<Application>().assets.open("interactive_minf.json").bufferedReader().use { MinfCatalog.parse(it.readText()) }
    }
    private val interactiveIdentities by lazy {
        getApplication<Application>().assets.open("interactive_audio_identities.json").bufferedReader().use { InteractiveAudioIdentities.parse(it.readText()) }
    }
    private val finalLapFanfareBytes: ByteArray by lazy {
        getApplication<Application>().assets.open("audio/${com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET}").use { it.readBytes() }
    }

    /** The unified merged internal pack library, or null when using loose files. */
    private var pack: InstalledPack? = null
    private var mergedFingerprints: List<String> = emptyList()

    /**
     * The jukebox the installed pack carries, grouped by source game.
     *
     * Computed from the verified manifest rather than from the file names on disk: a name states where a
     * track came from, but only the manifest states that the pack actually carries it.
     */
    /**
     * The jukebox every installed pack carries, grouped by source game.
     *
     * Read from all installed packs rather than only the active one: the active pack is whichever playback
     * uses, and a listener with both a course pack and a jukebox pack must see the jukebox tracks.
     *
     * Recomputed on read, which is a handful of small JSON parses; the alternative is invalidating a cache at
     * every install and restore path, and a stale cache here is a category that silently offers nothing.
     */
    private val jukeboxMetadata: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Metadata? by lazy {
        runCatching {
            getApplication<Application>().assets.open(JUKEBOX_CATALOG_ASSET)
                .bufferedReader().use { it.readText() }
                .let { com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.parseMetadata(it) }
        }.getOrNull()
    }

    private val linearCoordinator: LinearPlaybackCoordinator
        get() = PlaybackHolder.linear()

    private val linearSessionStore: LinearSessionStore by lazy {
        LinearSessionStore(java.io.File(getApplication<Application>().filesDir, "linear-session.json"))
    }

    private var lastPersistedLinearProgressMs: Long = 0L

    /** The jukebox, arranged by chronological source game and official OST track order. */
    val jukebox: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog
        get() = _ui.value.jukebox

    val extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.load(getApplication())
    }

    val courseMedleyCatalog: com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.load(getApplication())
    }

    val localizedNames: com.mylittleblover.mkwplayer.routes.LocalizedNames? by lazy {
        runCatching {
            getApplication<Application>().assets.open("localized_names.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.routes.LocalizedNames.parse(it.readText())
            }
        }.getOrNull()
    }

    val stageConnections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open("stage_connections.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.library.StageConnections.parse(it.readText())
            }
        }.getOrNull()
    }

    val officialRouteCatalog: com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog? by lazy {
        val knownStages = mapAnchors?.stages?.map { it.code }?.toSet().orEmpty()
        runCatching {
            getApplication<Application>().assets.open("official_routes.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog.parse(it.readText(), knownStages)
            }
        }.getOrNull()
    }

    @Volatile
    var roadJukeboxPool: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.CandidatePool =
        com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.loadInternalPool(getApplication<Application>().filesDir)
        private set

    fun reloadRoadJukeboxPool() {
        roadJukeboxPool = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.loadInternalPool(getApplication<Application>().filesDir)
    }

    val packDirectory: java.io.File?
        get() = pack?.directory

    private var speakerEnabled: Boolean = false

    private fun currentLayerGains(plan: InteractivePlaybackPlan?): Map<Int, Double> {
        if (plan == null || plan.overlayTrackIndices.isEmpty()) return emptyMap()
        val db = if (speakerEnabled) 0.0 else -96.0
        return plan.overlayTrackIndices.associateWith { db }
    }

    private fun refreshJukeboxCatalog() {
        val registry = com.mylittleblover.mkwplayer.pack.PackRegistry(
            java.io.File(getApplication<Application>().filesDir, PACK_ROOT),
        )
        val loaded = com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.load(registry, jukeboxMetadata)
        _ui.value = _ui.value.copy(jukebox = loaded)
        linearCoordinator.configureSessionStore(linearSessionStore)
        linearCoordinator.attach(getApplication(), loaded)
        refreshContinueListening()
    }

    /** Plays a single Jukebox track by its pack key, freezing its source game's list as the queue. */
    fun openJukeboxTrack(key: String, languageTag: String = java.util.Locale.getDefault().toLanguageTag()) {
        val catalog = jukebox
        if (!catalog.tracksByKey.containsKey(key)) {
            _ui.value = _ui.value.copy(
                status = "Cannot open this track",
                detail = "No installed pack carries this track.",
            )
            return
        }
        val game = catalog.gameForTrack(key) ?: run {
            _ui.value = _ui.value.copy(
                status = "Cannot open this track",
                detail = "No installed pack carries this track.",
            )
            return
        }
        val startIndex = game.items.indexOfFirst { it.key == key }.coerceAtLeast(0)
        playJukeboxQueue(game, startIndex, languageTag)
    }

    /** Plays a game's Jukebox tracks in OST order, freezing the queue at session start (#61). */
    fun playJukeboxQueue(
        game: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game,
        startIndex: Int,
        languageTag: String,
        shuffle: Boolean? = null,
    ) {
        if (nativePerformance?.isFading == true) return
        val entries = game.items.map { item -> LinearPlaybackCoordinator.entryFor(item, game, languageTag) }
        if (entries.isEmpty()) return
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = entries,
            startIndex = startIndex,
            shuffleOverride = shuffle,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_GAME,
                contextId = game.id,
                contextTitle = game.localizedName(languageTag),
                packFingerprints = currentPackFingerprints(),
            ),
        )
    }

    /** Plays all installed Jukebox arrangements chronologically across all games. */
    fun playAllJukebox(languageTag: String, shuffle: Boolean = false) {
        if (nativePerformance?.isFading == true) return
        val catalog = jukebox
        val entries = catalog.games.flatMap { game ->
            game.items.map { item -> LinearPlaybackCoordinator.entryFor(item, game, languageTag) }
        }
        if (entries.isEmpty()) return
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = entries,
            startIndex = 0,
            shuffleOverride = shuffle,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_ALL,
                contextId = "all",
                packFingerprints = currentPackFingerprints(),
            ),
        )
    }

    /** Inserts [item] at the front (LIFO) of the temporary "Play next" queue (#63, #64, blocked by #68 on fixed routes). */
    fun playJukeboxNext(
        item: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem,
        game: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game,
        languageTag: String,
    ) {
        if (!_ui.value.allowPlayNext) return
        val entry = LinearPlaybackCoordinator.entryFor(item, game, languageTag)
        val wasIdle = !_ui.value.canStop && _ui.value.queue.current == null
        if (wasIdle) {
            prepareForLinearPlayback()
        }
        linearCoordinator.enqueueNext(entry)
    }

    fun skipNext() {
        linearCoordinator.skipNext()
    }

    fun skipPrevious() {
        linearCoordinator.skipPrevious()
    }

    fun toggleShuffle() {
        linearCoordinator.toggleShuffle()
    }

    fun cycleRepeatMode() {
        linearCoordinator.cycleRepeatMode()
    }

    fun replayOrResumeLinear() {
        if (_ui.value.canResume) {
            resume()
        } else {
            linearCoordinator.replayCurrent()
        }
    }

    fun removeTempQueueItem(index: Int) {
        linearCoordinator.removeTempAt(index)
    }

    fun jumpToTempQueueItem(index: Int) {
        linearCoordinator.jumpToTemp(index)
    }

    fun jumpToUpcomingQueueItem(index: Int) {
        linearCoordinator.jumpToUpcoming(index)
    }

    private fun prepareForLinearPlayback() {
        packPlaybackRequest++
        nativePerformance = null
        interactivePlan = null
        performance = null
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        _ui.value = _ui.value.copy(
            flow = null,
            linearMode = true,
            artworkName = null,
            detail = null,
        )
    }

    /**
     * Applies the master output trim to the running session.
     *
     * Idempotent on purpose: the screen calls this from a `LaunchedEffect` keyed on the chosen value, so it runs
     * once at start-up and again on every change. Re-applying the same numbers costs a queued command and
     * changes nothing audible, which is cheaper than tracking whether it was applied and getting that wrong.
     */
    fun applyOutputTrim(trim: com.mylittleblover.mkwplayer.audio.OutputTrim) {
        PlaybackHolder.get().setOutputTrim(trim)
    }

    /** A pack that has been installed and verified, kept together so a track can be prepared on demand. */
    private data class InstalledPack(val manifest: PackManifest, val directory: java.io.File)

    /**
     * The running stage performance, or null when the listener is driving sections by hand.
     *
     * One driver at a time: two would both advance on the same transport end and between them skip every
     * other section.
     */
    private var performance: PerformanceDriver? = null
    private var pollJob: Job? = null
    private var totalFrames: Long = 0
    private var rate: Int = 0

    /**
     * The shared audio session.
     *
     * Not owned by this view model any more: a foreground service has to be able to keep
     * playing after the activity is gone, so the engine lives in the process-wide holder
     * and both the service and this class read it.
     */
    private val session: PlaybackSession get() = PlaybackHolder.get()

    private val store = LibraryStore(java.io.File(application.filesDir, "library.json"))

    /** Special Cup progress, persisted separately from the library so a corrupt one cannot lose both. */
    private val unlockStore = UnlockStore(java.io.File(application.filesDir, SPECIAL_CUP_FILE))

    private val unlockTestStore = UnlockTestStore(java.io.File(application.filesDir, "unlock-test-mode.json"))
    private var naturalUnlockUi: UnlockUi? = null

    private fun projectUnlock(natural: UnlockUi?): UnlockUi? {
        naturalUnlockUi = natural
        // A research pack with an unfinished scope must never earn the formal gate by accident.
        val formal = if(pack?.manifest?.unlock?.frozen == false) natural?.copy(visible=false, canEnter=false) else natural
        return _ui.value.unlockTestMode.apply(formal)
    }

    fun setUnlockTestMode(mode: UnlockTestMode) {
        unlockTestStore.write(mode).fold(onSuccess = {
            _ui.value = _ui.value.copy(unlockTestMode=mode, unlock=mode.apply(
                if(pack?.manifest?.unlock?.frozen == false) naturalUnlockUi?.copy(visible=false,canEnter=false) else naturalUnlockUi))
            if(isSpecialStageLocked(currentStageCode.orEmpty())) releaseTransport()
        }, onFailure = { _ui.value = _ui.value.copy(detail=it.message) })
    }

    private fun isSpecialStageLocked(code: String): Boolean =
        code.removePrefix("BGM_Stage_").removeSuffix("_00").equals("Rainbow",true) &&
            !com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)

    /** The cup's rules and progress, or null until there is a library to derive eligibility from. */
    private var cup: SpecialCupUnlock? = null

    /** Bridges the transport to the rules; null in the same condition as [cup]. */
    private var listening: ListeningProgress? = null

    /**
     * Feeds a transport sample to the cup.
     *
     * Only playing samples count, and only for the stage the sample belongs to. The result is
     * persisted immediately: losing thirty seconds of listening to a crash would be indistinguishable
     * from the rule not working.
     */
    private fun creditListening(state: PlaybackSession.State) {
        val bridge = listening ?: return
        val prefix = currentStageCode ?: state.title.takeIf { it.isNotEmpty() }?.let(StageFlowDiscovery::prefixOf) ?: return

        if (bridge.snapshot.currentStage != prefix) {
            bridge.begin(prefix, state.totalFrames, state.sampleRate.coerceAtLeast(48_000))
        }
        if (!state.playing && !state.paused) bridge.stopped()

        val outcome = bridge.sample(state.positionFrames, state.playing)
        val offered = outcome.offeredNow || _ui.value.unlock?.offered == true

        if (outcome.ignoredReason == null) {
            _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered)))
            unlockStore.save(outcome.state)?.let { _ui.value = _ui.value.copy(detail = it) }
        }
    }

    init {
        unlockTestStore.read().fold(onSuccess = { _ui.value = _ui.value.copy(unlockTestMode=it) },
            onFailure = { _ui.value = _ui.value.copy(detail=it.message) })
        // The catalog is bundled data, so it is read once rather than re-parsed on every
        // recomposition. A missing or broken asset leaves it null and the UI omits the
        // section instead of showing an empty one.
        val catalog = try {
            getApplication<Application>().assets.open(CATALOG_ASSET).bufferedReader().use {
                CatalogLoader.parse(it.readText())
            }
        } catch (e: Exception) {
            null
        }
        if (catalog != null) {
            _ui.value = _ui.value.copy(catalog = catalog)
            browser = CatalogBrowser(catalog)
            refreshCatalog()
        }

        // Restore before the first frame so the list is never briefly empty on launch.
        val loaded = store.load()
        if (loaded.rows.isNotEmpty()) {
            _ui.value = _ui.value.copy(
                tracks = loaded.rows.map {
                    Track(
                        key = it.key,
                        name = it.name,
                        frames = it.frames,
                        sampleRate = it.sampleRate,
                        playable = it.playable,
                        reason = it.reason,
                    )
                },
            )
        }
        loaded.problem?.let { _ui.value = _ui.value.copy(detail = it) }

        // Restore the unified multi-pack internal library without opening audio (#84, #85).
        val packRoot = java.io.File(getApplication<Application>().filesDir, PACK_ROOT)
        val merged = InternalPackLibrary(packRoot).loadOrMaterialize()
        if (merged != null) {
            pack = InstalledPack(merged.manifest, merged.directory)
            mergedFingerprints = merged.activePackFingerprints
            _ui.value = _ui.value.copy(pack = PackUi(
                name = merged.manifest.name,
                trackCount = merged.manifest.tracks.size,
                playableCount = PackPlayback.playableTracks(merged.manifest).size,
            ))
        }

        // Check for interrupted checkpoint from prior launch and wire single-task coordinator (#91-#97).
        PackImportCoordinator.configureCommitHandler { installed ->
            withContext(Dispatchers.Main.immediate) {
                applyCommittedPackUpdate(installed)
            }
        }
        PackImportCoordinator.checkPendingCheckpoint(getApplication(), packRoot)

        // The cup's eligibility depends on what the user actually has, so it is derived after the
        // library is restored. Reading the stored progress first means the offer survives a restart.
        establishCup(loaded.tracks)
        refreshJukeboxCatalog()

        viewModelScope.launch {
            PackImportCoordinator.state.collect { imp ->
                val importingNow = imp.isActive
                val progressText = when {
                    imp.phase == PackImportPhase.COMMITTING -> "Applying library update..."
                    imp.totalMembers > 0 -> "${imp.processedMembers}/${imp.totalMembers} · ${imp.currentMember}"
                    imp.isActive -> "Reading ${imp.displayName}..."
                    else -> ""
                }
                val currentPack = _ui.value.pack
                _ui.value = _ui.value.copy(
                    busy = importingNow,
                    status = when (imp.phase) {
                        PackImportPhase.FAILED -> "This pack was not installed"
                        PackImportPhase.CANCELLED -> "Import cancelled"
                        PackImportPhase.COMPLETED -> "Library updated"
                        else -> _ui.value.status
                    },
                    detail = when (imp.phase) {
                        PackImportPhase.FAILED,
                        PackImportPhase.PAUSED_NEEDS_REPICK -> imp.errorReason
                        PackImportPhase.CANCELLED -> imp.errorReason ?: _ui.value.detail
                        else -> _ui.value.detail
                    },
                    importState = imp,
                    pack = currentPack?.copy(importing = importingNow, progress = progressText)
                        ?: if (importingNow) {
                            PackUi(
                                name = imp.displayName,
                                trackCount = 0,
                                playableCount = 0,
                                importing = true,
                                progress = progressText,
                            )
                        } else null,
                )
            }
        }

        viewModelScope.launch {
            linearCoordinator.state.collect { linear ->
                if (linear.active && linear.current != null) {
                    flow = null
                    flowCompiled = null
                    flowBytes = emptyMap()
                    interactivePlan = null
                    nativePerformance = null
                    performance = null
                    if (linear.totalFrames > 0L && linear.sampleRate > 0) {
                        totalFrames = linear.totalFrames
                        rate = linear.sampleRate
                    }
                    _ui.value = _ui.value.copy(
                        selectedKey = linear.current.key,
                        flow = null,
                        title = linear.current.title,
                        status = if (linear.loading) "Loading..." else _ui.value.status,
                        total = EngineSnapshot.formatPosition(totalFrames, rate),
                        linearMode = true,
                        linearTrack = linear.current,
                        queue = linear.queue,
                        canSeek = totalFrames > 0L,
                        totalFrames = totalFrames,
                        sampleRate = rate,
                        artworkName = linear.current.artworkName ?: _ui.value.artworkName,
                        linearContextKind = linear.contextKind,
                        linearContextId = linear.contextId,
                        linearContextTitle = linear.contextTitle,
                        allowPlayNext = linear.allowPlayNext,
                        allowShuffle = linear.allowShuffle,
                        allowSingleRepeat = linear.allowSingleRepeat,
                        allowRepeatToggle = linear.allowRepeatToggle,
                        continueListening = null,
                        detail = linear.error,
                    )
                    if (!linear.loading && linear.error == null) {
                        requestNotificationPermissionIfNeeded()
                        startPolling()
                    }
                } else {
                    _ui.value = _ui.value.copy(
                        linearTrack = linear.current,
                        queue = linear.queue,
                        linearContextKind = linear.contextKind,
                        linearContextId = linear.contextId,
                        linearContextTitle = linear.contextTitle,
                        allowPlayNext = linear.allowPlayNext,
                        allowShuffle = linear.allowShuffle,
                        allowSingleRepeat = linear.allowSingleRepeat,
                        allowRepeatToggle = linear.allowRepeatToggle,
                        detail = linear.error ?: _ui.value.detail,
                        status = if (linear.error != null) "Cannot open this track" else _ui.value.status,
                    )
                }
            }
        }
    }

    /**
     * Builds the cup from the library and restores its progress.
     *
     * Eligibility is derived from file names rather than stored, because the rule is "every stage you
     * have" -- a stored list would go stale the moment a stage was added, and the cup would then be
     * earnable without hearing it.
     */
    private fun establishCup(tracks: List<LibraryStore.TrackRow> = emptyList()) {
        val names = _ui.value.tracks.map { it.name }.ifEmpty { tracks.map { it.title } }
        val eligible = pack?.manifest?.let { manifest ->
            manifest.unlock.stages.toSet().ifEmpty { manifest.stages.map { it.code }.toSet() }
        } ?: SpecialCupRules.eligibleStages(names)
        if (eligible.isEmpty()) {
            cup = null
            listening = null
            _ui.value = _ui.value.copy(unlock = projectUnlock(null))
            return
        }

        val restored = unlockStore.load()
        restored.problem?.let { _ui.value = _ui.value.copy(detail = it) }

        val rules = SpecialCupUnlock(eligible).let { fresh ->
            restored.state?.let { fresh.withState(it) } ?: fresh
        }
        cup = rules

        val bridge = ListeningProgress(rules)
        listening = bridge
        // The offer is only claimed if the stored phase says the user was already offered it, so a
        // restart does not re-notify.
        val offered = restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.PENDING ||
            restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING ||
            restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED
        _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered)))
    }

    /** Enters the ceremony. Fails loudly rather than pretending when the cup is not open. */
    fun enterCeremony(): Boolean {
        val rules = cup ?: return false
        val bridge = listening ?: return false
        if (bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING) return false
        val next = rules.withState(bridge.snapshot).beginCeremony()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered = true)))
        return true
    }

    /** Leaves the ceremony early. The entitlement is preserved by the rules. */
    fun leaveCeremony() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).leaveCeremonyEarly()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered = true)))
    }

    /**
     * Reports that the ceremony audio reached its natural end.
     *
     * Called by whoever owns the ceremony track's completion, not by a timer: a UI countdown would be
     * the fake-completion the specification forbids.
     */
    fun ceremonyReachedEnd() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).finishCeremony(reachedEnd = true)
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered = true)))
    }

    /** Consumes the first-time congratulations, if it is owed. */
    fun dismissCongratulations() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (!rules.withState(bridge.snapshot).owesCongratulations) return
        val next = rules.withState(bridge.snapshot).markCongratulated()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = projectUnlock(UnlockUi.from(bridge, offered = true)))
    }

    /**
     * Rebuilds the catalogue rows from the library.
     *
     * Only measured values are offered to the matcher: a track row that has never been opened has
     * no frames yet, and passing zeros would make it look like a mismatch rather than unknown.
     */
    private fun refreshCatalog() {
        val active = browser ?: return
        val files = _ui.value.tracks.map {
            CatalogMatcher.PresentFile(
                fileName = it.name,
                frames = it.frames,
                sampleRate = it.sampleRate,
                channels = 2,
            )
        }
        active.refresh(files)
    }

    /** Writes the current list. Storage failing is reported, never fatal. */
    private fun persist() {
        val problem = store.save(
            _ui.value.tracks.map {
                LibraryStore.Row(
                    key = it.key,
                    name = it.name,
                    frames = it.frames,
                    sampleRate = it.sampleRate,
                    playable = it.playable,
                    reason = it.reason,
                )
            },
        )
        if (problem != null) _ui.value = _ui.value.copy(detail = problem)
    }
    /**
     * Adds a picked file to the library. Copies, inspects and prepares it off the
     * main thread, then selects it.
     */
    fun add(uri: Uri) {
        _ui.value = _ui.value.copy(busy = true, status = "Reading file...", detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { inspect(uri) }
            _ui.value = _ui.value.copy(busy = false)

            when (outcome) {
                is Outcome.Refused -> {
                    _ui.value = _ui.value.copy(status = "Cannot play this file", detail = outcome.reason)
                }
                is Outcome.Added -> {
                    _ui.value = _ui.value.copy(
                        tracks = _ui.value.tracks + outcome.track,
                        status = if (outcome.track.playable) "Ready to play" else "Unsupported codec",
                        detail = outcome.track.reason,
                    )
                    persist()
                    if (outcome.track.playable) select(outcome.track.key)
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Added(val track: Track) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    private fun inspect(uri: Uri): Outcome {
        val picked = when (val copied = AudioPicker.copy(getApplication(), uri)) {
            is AudioPicker.Result.Rejected -> return Outcome.Refused(copied.reason)
            is AudioPicker.Result.Ok -> copied.picked
        }

        val bytes = try {
            picked.readBytes()
        } catch (e: Exception) {
            return Outcome.Refused("The file could not be opened: ${e.message}")
        }

        val header = when (val read = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Rejected -> return Outcome.Refused(read.reason)
            is BwavHeader.Result.Ok -> read.info
        }

        val playability = Playability.evaluate(header.codecKind)
        val track = Track(
            key = picked.key,
            name = picked.displayName,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
            playable = playability.playable,
            reason = playability.reason,
        )

        if (!playability.playable) {
            // Kept in the library so the limitation is visible instead of the file
            // silently vanishing after the picker closes.
            return Outcome.Added(track)
        }

        val packet = try {
            GraphBuilder.build(
                GraphBuilder.Spec(
                    asset = GraphBuilder.Asset(
                        path = picked.displayName,
                        sampleRate = header.sampleRate,
                        channels = header.channels,
                        frames = header.frames.toLong(),
                        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes),
                        loop = if (header.hasLoop) {
                            GraphBuilder.Loop(header.loopStart!!.toLong(), header.loopEnd!!.toLong())
                        } else {
                            null
                        },
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Refused("This file cannot be prepared: ${e.message}")
        }

        val probed = GraphProbe.inspect(packet)
        if (probed is GraphProbe.Result.Rejected) {
            return Outcome.Refused("Internal check failed: ${probed.reason}")
        }

        prepared[picked.key] = Prepared(
            name = picked.displayName,
            bytes = bytes,
            packet = packet,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
        )
        return Outcome.Added(track)
    }

    /**
     * Removes a track from the library and forgets its cached copy.
     *
     * Stops playback first when the removed track is the one playing, otherwise the
     * transport would keep running against a file nobody can see or select.
     */
    fun remove(key: String) {
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        val wasSelected = _ui.value.selectedKey == key
        if (wasSelected) {
            releaseTransport()
        }
        prepared.remove(key)
        _ui.value = _ui.value.copy(
            tracks = _ui.value.tracks.filterNot { it.key == key },
            selectedKey = if (wasSelected) null else _ui.value.selectedKey,
            status = if (wasSelected) "No track loaded" else _ui.value.status,
            detail = if (wasSelected) null else _ui.value.detail,
            canPause = if (wasSelected) false else _ui.value.canPause,
            canResume = if (wasSelected) false else _ui.value.canResume,
            canStop = if (wasSelected) false else _ui.value.canStop,
            progress = if (wasSelected) 0f else _ui.value.progress,
        )
        AudioPicker.prune(getApplication(), _ui.value.tracks.map { it.key }.toSet())
        persist()
    }

    /**
     * Opens the flow contained in the given files.
     *
     * Expects several files from one stage; a single main file is accepted and plays as a
     * one-part flow, which keeps "open these five" and "open this one" on the same path rather
     * than giving the flow code a special case it would then have to keep working.
     */
    fun openFlow(uris: List<Uri>) {
        if(nativePerformance?.isFading==true) return
        val request = ++packPlaybackRequest
        if (uris.isEmpty()) return
        _ui.value = _ui.value.copy(busy = true, status = "Reading files...", detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val bytes = LinkedHashMap<String, ByteArray>()
                val parts = mutableListOf<StageFlow.Part>()
                val rejected = mutableListOf<String>()

                for (uri in uris) {
                    val picked = when (val copied = AudioPicker.copy(getApplication(), uri)) {
                        is AudioPicker.Result.Rejected -> {
                            rejected += copied.reason
                            continue
                        }
                        is AudioPicker.Result.Ok -> copied.picked
                    }
                    val data = try {
                        picked.readBytes()
                    } catch (e: Exception) {
                        rejected += "${picked.displayName}: ${e.message}"
                        continue
                    }
                    // The logical name must be the game file name so part roles can be found;
                    // the cache name is a digest and carries no role.
                    val result = StageFlowReader.readPart(picked.displayName, data)
                    if (result.isFailure) {
                        rejected += result.exceptionOrNull()!!.message ?: picked.displayName
                        continue
                    }
                    bytes[picked.displayName] = data
                    parts += result.getOrThrow()
                }

                Triple(parts, bytes, rejected)
            }

            val (parts, bytes, rejected) = outcome
            _ui.value = _ui.value.copy(busy = false)

            if (parts.isEmpty()) {
                _ui.value = _ui.value.copy(
                    status = "No usable parts",
                    detail = rejected.firstOrNull() ?: "None of the chosen files could be read.",
                )
                return@launch
            }

            if (request != packPlaybackRequest) return@launch
            if(bytes.keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(bytes,rejected,request = request)
                return@launch
            }
            val group = StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))
            if (group == null) {
                _ui.value = _ui.value.copy(status = "No usable parts", detail = rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = "Cannot open this flow",
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), bytes, rejected, request = request)
        }
    }

    private data class PreparedFlowBundle(
        val bytes: Map<String, ByteArray>,
        val plan: InteractivePlaybackPlan?,
        val compiled: StageFlowCompiler.Compiled,
        val entry: Int,
        val wantSpeaker: Boolean,
        val prepared: PlaybackSession.Prepared,
    )

    private sealed interface FlowPrepareOutcome {
        data class Ok(val bundle: PreparedFlowBundle) : FlowPrepareOutcome
        data class Failed(val status: String, val detail: String?) : FlowPrepareOutcome
    }

    private suspend fun startFlow(
        flow: StageFlow,
        sourceBytes: Map<String, ByteArray>,
        rejected: List<String>,
        displayTitle: String = flow.name,
        startingStage: String? = null,
        request: Long = ++packPlaybackRequest,
    ): Boolean {
        if (nativePerformance?.isFading == true) return false
        val previousPlanName = interactivePlan?.name
        val previousSpeaker = speakerEnabled
        val packDir = pack?.directory
        val ffAsset = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET

        val outcome = withContext(Dispatchers.IO) {
            val bytes = try {
                val withFf = sourceBytes + (ffAsset to finalLapFanfareBytes)
                val ks = extraCatalog.koopaSpeaker
                if (ks != null && packDir != null && flow.name == "BGM_Stage_${ks.stageCode}") {
                    val normalFile = java.io.File(packDir, "audio/${ks.normalAsset.sha256}")
                    val fastFile = java.io.File(packDir, "audio/${ks.fastAsset.sha256}")
                    if (normalFile.isFile && fastFile.isFile) {
                        withFf + mapOf(
                            "${ks.normalAsset.stem}.bwav" to normalFile.readBytes(),
                            "${ks.fastAsset.stem}.bwav" to fastFile.readBytes(),
                        )
                    } else {
                        withFf
                    }
                } else {
                    withFf
                }
            } catch (e: Exception) {
                return@withContext FlowPrepareOutcome.Failed("Cannot load FinalLapFF", e.message)
            }

            val plan = try {
                if (minfCatalog.part(flow.name.removePrefix("BGM_Stage_"), "Full") != null) {
                    var built = InteractivePlaybackPlan.normal(flow, bytes, minfCatalog).startingAt(startingStage)
                    val ks = extraCatalog.koopaSpeaker
                    if (ks != null && flow.name == "BGM_Stage_${ks.stageCode}") {
                        val normalBytes = bytes["${ks.normalAsset.stem}.bwav"]
                        val fastBytes = bytes["${ks.fastAsset.stem}.bwav"]
                        if (normalBytes != null && fastBytes != null) {
                            built = com.mylittleblover.mkwplayer.interactive.SpeakerOverlayCompiler.extend(
                                built,
                                ks,
                                normalBytes,
                                fastBytes,
                            )
                        }
                    }
                    built
                } else null
            } catch (e: Exception) {
                return@withContext FlowPrepareOutcome.Failed("Cannot prepare interactive playback", e.message)
            }

            val compiled = try {
                plan?.standard ?: StageFlowCompiler.compileFromBytes(flow, bytes)
            } catch (e: IllegalArgumentException) {
                return@withContext FlowPrepareOutcome.Failed("Cannot prepare this flow", e.message)
            }

            val wantSpeaker = if (plan?.overlayTrackIndices.isNullOrEmpty() || previousPlanName != plan?.name) {
                false
            } else {
                previousSpeaker
            }
            val initialGains = if (plan == null || plan.overlayTrackIndices.isEmpty()) {
                emptyMap()
            } else {
                val db = if (wantSpeaker) 0.0 else -96.0
                plan.overlayTrackIndices.associateWith { db }
            }

            val entry = plan?.entry ?: FlowPresentation.entrySection(compiled)
            val packetBytes = plan?.packet ?: compiled.packet
            val assetPaths = plan?.assetPaths ?: compiled.assetPaths
            val frames = FlowPresentation.totalFrames(flow)
            when (
                val prep = session.prepareFlow(
                    title = displayTitle,
                    packet = packetBytes,
                    assets = assetPaths.map { path -> path to bytes[path]!! },
                    frames = frames,
                    startSegment = entry,
                    autoAdvance = plan?.automaticNext ?: emptyMap(),
                    streamed = plan?.streamed ?: false,
                    preparedAssets = plan?.let { it.preparedAssets + it.entryPreparedAssets[it.entry].orEmpty() } ?: emptySet(),
                    prequeueInitial = plan?.let { it.entry in it.entryAutomaticNext } ?: false,
                    initialLayerGains = initialGains,
                )
            ) {
                is PlaybackSession.PrepareResult.Failed ->
                    FlowPrepareOutcome.Failed("Cannot play this flow", prep.reason)
                is PlaybackSession.PrepareResult.Ok ->
                    FlowPrepareOutcome.Ok(PreparedFlowBundle(bytes, plan, compiled, entry, wantSpeaker, prep.prepared))
            }
        }

        val bundle = when (outcome) {
            is FlowPrepareOutcome.Failed -> {
                if (request == packPlaybackRequest) {
                    _ui.value = _ui.value.copy(status = outcome.status, detail = outcome.detail)
                }
                return false
            }
            is FlowPrepareOutcome.Ok -> outcome.bundle
        }

        if (request != packPlaybackRequest || nativePerformance?.isFading == true) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { bundle.prepared.discard() }
            return false
        }

        speakerEnabled = bundle.wantSpeaker
        val state = session.commitPrepared(bundle.prepared)
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = "Cannot play this flow", detail = state.detail)
            return false
        }

        val plan = bundle.plan
        val compiled = bundle.compiled
        val entry = bundle.entry
        linearCoordinator.yieldToExternalTransport()
        this.flow = flow
        this.interactivePlan = plan
        this.nativePerformance = null
        this.performance = null
        this.flowCompiled = compiled
        this.flowBytes = bundle.bytes
        totalFrames = FlowPresentation.totalFrames(flow)
        rate = state.sampleRate

        _ui.value = _ui.value.copy(
            title = displayTitle,
            status = "Playing",
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canStop = true,
            outputActive = session.hasOutput,
            linearMode = false,
            linearTrack = null,
            canSeek = false,
            totalFrames = totalFrames,
            sampleRate = rate,
            artworkName = extraCatalog.courseArtwork[flow.name.removePrefix("BGM_Stage_")],
            flow = FlowUi(
                title = displayTitle,
                sections = FlowPresentation.sections(flow, compiled),
                activeSegment = entry,
                summary = FlowPresentation.summary(flow),
                provenance = if(plan!=null) "MINF sample timing with confirmed Outro exits" else FlowPresentation.provenanceLabel(flow),
                isProvisional = flow.isProvisional,
                outros = compiled.segmentByOutro.keys.sorted(),
                hasFast = compiled.segmentByRole.containsKey(StageFlow.Role.Fast),
                versionNumbers = plan?.versions?.map { it.number }.orEmpty(),
                regionNumbers = plan?.regions?.keys?.sorted().orEmpty(),
                availableStageKeys = plan?.let { current -> current.spec.segments.mapNotNull { current.labels[it.index] } }
                    ?: FlowPresentation.sections(flow, compiled).map { it.label },
                hasSpeakerOverlay = plan?.overlayTrackIndices?.isNotEmpty() == true,
                speakerEnabled = speakerEnabled,
            ),
            detail = buildList {
                if (rejected.isNotEmpty()) add("Skipped: " + rejected.joinToString("; "))
                if (!session.hasOutput) {
                    add("No device output on this system; the engine is running without a speaker.")
                }
            }.joinToString(" ").ifEmpty { null },
        )

        PlaybackService.start(getApplication())
        requestNotificationPermissionIfNeeded()
        startPolling()
        return true
    }

    private sealed interface RainbowPrepareOutcome {
        data class Ok(val plan: InteractivePlaybackPlan, val frames: Long, val prepared: PlaybackSession.Prepared) : RainbowPrepareOutcome
        data class Failed(val status: String, val detail: String?) : RainbowPrepareOutcome
    }

    private suspend fun startRainbow(
        bytes: Map<String,ByteArray>,
        notes: List<String>,
        displayTitle: String="Rainbow",
        startingStage: String?=null,
        request: Long = ++packPlaybackRequest,
    ): Boolean {
        if(isSpecialStageLocked("Rainbow")) return false
        if(nativePerformance?.isFading==true) return false
        val outcome = withContext(Dispatchers.IO) {
            val plan = try {
                InteractivePlaybackPlan.rainbow(bytes, minfCatalog).startingAt(startingStage)
            } catch (e: Exception) {
                return@withContext RainbowPrepareOutcome.Failed("Cannot prepare Rainbow", e.message)
            }
            val frames = plan.spec.segments.sumOf { it.exit }
            when (
                val prep = session.prepareFlow(
                    title = displayTitle,
                    packet = plan.packet,
                    assets = plan.assetPaths.map { it to bytes.getValue(it) },
                    frames = frames,
                    startSegment = plan.entry,
                    streamed = true,
                    autoAdvance = plan.automaticNext,
                    preparedAssets = plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
                    prequeueInitial = plan.entry in plan.entryAutomaticNext,
                )
            ) {
                is PlaybackSession.PrepareResult.Failed ->
                    RainbowPrepareOutcome.Failed("Cannot play Rainbow", prep.reason)
                is PlaybackSession.PrepareResult.Ok ->
                    RainbowPrepareOutcome.Ok(plan, frames, prep.prepared)
            }
        }
        val (plan, frames, prepared) = when (outcome) {
            is RainbowPrepareOutcome.Failed -> {
                if (request == packPlaybackRequest) {
                    _ui.value = _ui.value.copy(status = outcome.status, detail = outcome.detail)
                }
                return false
            }
            is RainbowPrepareOutcome.Ok -> Triple(outcome.plan, outcome.frames, outcome.prepared)
        }
        if (request != packPlaybackRequest || nativePerformance?.isFading == true) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prepared.discard() }
            return false
        }
        val state = session.commitPrepared(prepared)
        if(state.detail!=null) { _ui.value=_ui.value.copy(status="Cannot play Rainbow",detail=state.detail);return false }
        linearCoordinator.yieldToExternalTransport()
        interactivePlan=plan;nativePerformance=null;performance=null;flow=null;flowCompiled=null;flowBytes=bytes
        totalFrames=frames;rate=48000
        val sections=plan.spec.segments.map { FlowPresentation.Section(it.index,plan.labels.getValue(it.index),it.exit,48000) }
        _ui.value=_ui.value.copy(title=displayTitle,status="Playing",detail=notes.joinToString("; ").ifEmpty { null },
            canPause=true,canStop=true,outputActive=session.hasOutput,
            linearMode=false,linearTrack=null,canSeek=false,totalFrames=frames,sampleRate=48000,artworkName=null,
            flow=FlowUi(displayTitle,sections,plan.entry,"Area1 → Area2 → Area3 → Final","MINF timing; bar exits confirmed",true,
                outros=plan.outros.keys.sorted(), availableStageKeys=sections.map { it.label }))
        PlaybackService.start(getApplication());requestNotificationPermissionIfNeeded();startPolling()
        return true
    }

    /**
     * Opens the stage files found in a folder inside app storage.
     *
     * Same path as [openFlow], minus the picker: this is what makes the flow reachable from an
     * automated test, and it is a working fallback for a user whose file manager returns names the
     * app cannot read.
     */
    fun openFlowFromFolder() {
        if(nativePerformance?.isFading==true) return
        val request = ++packPlaybackRequest
        val folder = java.io.File(getApplication<Application>().filesDir, FLOW_FOLDER)
        _ui.value = _ui.value.copy(busy = true, status = "Scanning folder...", detail = null)
        viewModelScope.launch {
            val scan = withContext(Dispatchers.IO) { FolderAudioSource.scan(folder) }
            _ui.value = _ui.value.copy(busy = false)

            if (scan.missingFolder) {
                _ui.value = _ui.value.copy(
                    status = "No folder",
                    detail = "Put the stage files in ${folder.path} and try again.",
                )
                return@launch
            }
            if (scan.found.isEmpty()) {
                _ui.value = _ui.value.copy(
                    status = "No usable files",
                    detail = scan.rejected.firstOrNull() ?: "The folder holds no readable stage parts.",
                )
                return@launch
            }

            if (request != packPlaybackRequest) return@launch
            if(scan.bytesByName().keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(scan.bytesByName(),scan.rejected,request = request)
                return@launch
            }
            val group = StageFlowDiscovery.dominant(scan.groups())
            if (group == null) {
                _ui.value = _ui.value.copy(status = "No usable files", detail = scan.rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = "Cannot open this flow",
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), scan.bytesByName(), scan.rejected, request = request)
        }
    }

    /**
     * Imports a `.mkwpack` directly via persistable SAF FileChannel streaming (#91-#97).
     *
     * Enforces strictly one active import at a time (#94), never copies the archive into cache
     * storage (#96), supports pre-commit cancellation (#91, #93), crash-safe checkpoint resume
     * (#95, #97), and automatic commit without confirmation dialogs (#92).
     */
    fun importPack(uri: Uri) {
        if (nativePerformance?.isFading == true) return
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.startUriImport(
            context = app,
            uri = uri,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        when (result) {
            is PackImportCoordinator.StartResult.Started -> Unit
            is PackImportCoordinator.StartResult.RejectedBusy -> {
                _ui.value = _ui.value.copy(detail = result.reason)
            }
            is PackImportCoordinator.StartResult.RejectedInvalid -> {
                _ui.value = _ui.value.copy(detail = result.reason)
            }
        }
    }

    fun cancelPackImport(): Boolean {
        val packRoot = java.io.File(getApplication<Application>().filesDir, PACK_ROOT)
        return PackImportCoordinator.requestCancel(packRoot)
    }

    fun resumePackImport() {
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.resumePending(
            context = app,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        if (result is PackImportCoordinator.StartResult.RejectedInvalid) {
            _ui.value = _ui.value.copy(detail = result.reason)
        } else if (result is PackImportCoordinator.StartResult.RejectedBusy) {
            _ui.value = _ui.value.copy(detail = result.reason)
        }
    }

    fun resumePackImportWithRepick(uri: Uri) {
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.resumeWithRepickedUri(
            context = app,
            newUri = uri,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        if (result is PackImportCoordinator.StartResult.RejectedInvalid) {
            _ui.value = _ui.value.copy(detail = result.reason)
        } else if (result is PackImportCoordinator.StartResult.RejectedBusy) {
            _ui.value = _ui.value.copy(detail = result.reason)
        }
    }

    fun dismissImportChangelog() {
        PackImportCoordinator.dismissCompletedOrError()
    }

    /**
     * Applies committed pack + candidate pool updates to active library state, stops any
     * invalidated active playback (#88), clears invalidated Continue Listening (#89), and counts
     * saved custom routes flagged invalid without mutating user edits (#87).
     */
    private fun applyCommittedPackUpdate(
        installed: StreamingPackInstaller.Result.Installed,
    ): PackImportChangelog {
        val merged = installed.mergedLibrary
        val effectiveManifest = merged?.manifest ?: installed.manifest
        val effectiveDir = merged?.directory ?: installed.directory
        this.pack = InstalledPack(effectiveManifest, effectiveDir)
        this.mergedFingerprints = merged?.activePackFingerprints
            ?: listOf("${installed.manifest.packId}:${installed.manifest.packVersion}")

        com.mylittleblover.mkwplayer.ui.art.PackArtwork.evictAll()
        reloadRoadJukeboxPool()
        establishCup()

        val registry = PackRegistry(java.io.File(getApplication<Application>().filesDir, PACK_ROOT))
        val updatedJukebox = com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.load(registry, jukeboxMetadata)
        _ui.value = _ui.value.copy(jukebox = updatedJukebox)
        linearCoordinator.configureSessionStore(linearSessionStore)
        linearCoordinator.attach(getApplication(), updatedJukebox)

        val customRoutes = com.mylittleblover.mkwplayer.routes.CustomRouteStore(customRoutesFile())
            .read().getOrDefault(emptyList())

        val invalidEnv = buildInvalidationEnvironment(
            manifest = effectiveManifest,
            directory = effectiveDir,
            jukeboxCatalog = updatedJukebox,
            customRoutes = customRoutes,
        )

        // #88: Stop active playback immediately if updated data made its route, stage, or assets invalid.
        var stoppedActivePlayback = false
        var stopDetail: String? = null
        val linearNow = linearCoordinator.state.value
        if (linearNow.active && linearNow.current != null) {
            val allQueue = linearCoordinator.queue.snapshot().let { snap ->
                buildList {
                    snap.current?.let { add(it) }
                    addAll(snap.upNext)
                }
            }
            val decision = LibraryInvalidation.shouldStopActiveLinear(
                contextKind = linearNow.contextKind,
                contextId = linearNow.contextId,
                currentEntry = linearNow.current,
                allQueueEntries = allQueue,
                env = invalidEnv,
            )
            if (decision.shouldStop) {
                releaseTransport()
                stoppedActivePlayback = true
                stopDetail = decision.reason
            }
        } else {
            val stageDecision = LibraryInvalidation.shouldStopInteractiveStage(currentStageCode, invalidEnv)
            if (stageDecision.shouldStop) {
                releaseTransport()
                stoppedActivePlayback = true
                stopDetail = stageDecision.reason
            }
        }

        // #89: Clear Continue Listening immediately if updated data invalidated saved route/pool/tracks.
        var clearedContinueListening = false
        val saved = linearSessionStore.read().getOrNull()
        if (saved != null && !LibraryInvalidation.isSavedSessionStillValid(saved, invalidEnv)) {
            linearSessionStore.clear()
            clearedContinueListening = true
            _ui.value = _ui.value.copy(continueListening = null)
        } else {
            refreshContinueListening()
        }

        // #87: Identify invalidated custom routes without mutating/deleting user route edits.
        val invalidRoutesCount = LibraryInvalidation.findInvalidCustomRoutes(invalidEnv).size

        val playable = PackPlayback.playableTracks(effectiveManifest)
        val notes = buildList {
            if (installed.coverage.unsupportedCodecs.isNotEmpty()) {
                add(
                    "${installed.coverage.unsupportedCodecs.sumOf { it.affectedTracks }} track(s) use a codec this " +
                        "build cannot decode.",
                )
            }
            if (stopDetail != null) add(stopDetail)
        }

        val previousGeneration = _ui.value.libraryGeneration
        val hadNothingActiveBefore = session.state.value.active.not() &&
            !linearCoordinator.state.value.active &&
            interactivePlan == null &&
            flow == null

        _ui.value = _ui.value.copy(
            busy = false,
            status = if (playable.isEmpty()) "Pack installed, but nothing in it is playable" else "Library updated",
            detail = notes.firstOrNull(),
            libraryGeneration = previousGeneration + 1,
            pack = PackUi(
                name = effectiveManifest.name.ifBlank { installed.manifest.name },
                trackCount = effectiveManifest.tracks.size,
                playableCount = playable.size,
                notes = notes,
                importing = false,
                progress = "",
            ),
        )

        // Open first stage only on initial empty library import so active playback isn't disrupted (#92).
        if (hadNothingActiveBefore && !stoppedActivePlayback) {
            val firstStage = installed.manifest.stages.firstNotNullOfOrNull { stage ->
                effectiveManifest.tracksOf(stage).firstOrNull { track -> playable.any { it.key == track.key } }
            } ?: playable.firstOrNull { track -> installed.manifest.tracks.any { it.key == track.key } }
            if (firstStage != null) {
                if (firstStage.kind == com.mylittleblover.mkwplayer.registry.ResourceKind.JUKEBOX) {
                    openJukeboxTrack(firstStage.key)
                } else {
                    openPackTrack(firstStage)
                }
            }
        }

        return PackImportChangelog(
            packId = installed.manifest.packId,
            packVersion = installed.manifest.packVersion,
            packName = installed.manifest.name.ifBlank { installed.manifest.packId },
            isDelta = installed.manifest.isDelta,
            addedOrUpdatedAssets = installed.addedOrUpdatedAssets,
            addedOrUpdatedTracks = installed.addedOrUpdatedTracks,
            totalLibraryTracks = effectiveManifest.tracks.size,
            playableLibraryTracks = playable.size,
            jukeboxTrackCount = updatedJukebox.itemsByKey.size,
            poolStatus = installed.poolStatus,
            poolDataVersion = installed.poolDataVersion,
            poolEdgeCount = installed.poolEdgeCount,
            stoppedActivePlayback = stoppedActivePlayback,
            clearedContinueListening = clearedContinueListening,
            invalidatedCustomRoutesCount = invalidRoutesCount,
        )
    }

    private var packPlaybackRequest=0L

    private sealed interface PackOpenOutcome {
        data class Refused(val reason: String) : PackOpenOutcome
        data class IdentityError(val reason: String?) : PackOpenOutcome
        data class Ready(
            val playable: PackPlayback.Playable,
            val resolved: PackInteractiveSources.Resolved?,
        ) : PackOpenOutcome
    }

    /** Newest selection waits without blocking the service's foreground promotion. */
    fun openPackTrack(track: PackManifest.Track,perform: Boolean=false,startingStage: String?=null) {
        if(nativePerformance?.isFading==true) return
        val request=++packPlaybackRequest
        viewModelScope.launch {
            PlaybackService.awaitForegroundPromotion()
            if(request!=packPlaybackRequest || nativePerformance?.isFading==true) return@launch
            if(openPackTrackNow(track,startingStage,request) && perform) startPerformance()
        }
    }

    private suspend fun openPackTrackNow(track: PackManifest.Track,startingStage: String?=null,request: Long): Boolean {
        if(nativePerformance?.isFading==true) return false
        val installed = pack ?: return false
        val outcome = withContext(Dispatchers.IO) {
            when (val prepared = PackPlayback.prepare(installed.manifest, track, installed.directory)) {
                is PackPlayback.Result.Refused -> PackOpenOutcome.Refused(prepared.reason)
                is PackPlayback.Result.Ok -> {
                    val playable = prepared.playable
                    val resolved = try {
                        PackInteractiveSources.resolve(installed.manifest, track, installed.directory, playable, interactiveIdentities)
                    } catch (e: Exception) {
                        return@withContext PackOpenOutcome.IdentityError(e.message)
                    }
                    PackOpenOutcome.Ready(playable, resolved)
                }
            }
        }
        if (request != packPlaybackRequest || nativePerformance?.isFading == true) return false
        return when (outcome) {
            is PackOpenOutcome.Refused -> {
                _ui.value = _ui.value.copy(status = "Cannot open this track", detail = outcome.reason)
                false
            }
            is PackOpenOutcome.IdentityError -> {
                _ui.value = _ui.value.copy(status = "Cannot identify interactive sources", detail = outcome.reason)
                false
            }
            is PackOpenOutcome.Ready -> {
                val playable = outcome.playable
                when (val resolved = outcome.resolved) {
                    is PackInteractiveSources.Resolved.Rainbow ->
                        startRainbow(resolved.bytes, playable.notes, resolved.title, startingStage, request = request)
                    is PackInteractiveSources.Resolved.Normal ->
                        startFlow(resolved.flow, resolved.bytes, playable.notes, resolved.title, startingStage, request = request)
                    null ->
                        startFlow(playable.flow, playable.bytes, playable.notes, startingStage = startingStage, request = request)
                }
            }
        }
    }

    /** Opens the first track of the imported pack. */
    fun openFirstPackStage() {
        val installed = pack ?: return
        val track = PackPlayback.playableTracks(installed.manifest).firstOrNull()
        if (track == null) {
            _ui.value = _ui.value.copy(
                status = "Nothing to play",
                detail = "This pack carries no track this build can decode.",
            )
            return
        }
        openPackTrack(track)
    }

    /** Requests a section of the open flow. */
    fun playSection(segment: Int) {
        if(interactivePlan!=null) {
            startPerformance()
            val plan=interactivePlan!!
            val driver=nativePerformance ?: return
            val number=plan.outros.entries.firstOrNull { it.value==segment }?.key
            val error=when {
                number!=null -> driver.takeOutro(number)
                segment==plan.fast -> driver.enterFast()
                plan.next[driver.current]==segment -> driver.advance()
                else -> "This section has no transition from the current section"
            }
            publishNativePerformance(error,true)
            return
        }
        val compiled = flowCompiled ?: return
        // Taking a section by hand ends the performance: the listener has taken over, and a driver still
        // following the transport would fight them for the next section.
        performance = null
        val state = session.playSegment(segment)
        if (state.detail != null) {
            _ui.value = _ui.value.copy(detail = state.detail)
            return
        }
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                activeSegment = segment,
                performing = false,
                performanceStage = "", stageKey = null, pendingStageKey = null,
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
            ),
            status = "Playing",
        )
    }

    /**
     * Performs the whole open stage: prelude, main loop, then the outro the listener picks.
     *
     * Refuses with a stated reason rather than doing nothing, because a Start button that silently fails is
     * indistinguishable from a broken app.
     */
    fun startPerformance() {
        if(isSpecialStageLocked(currentStageCode.orEmpty())) return
        interactivePlan?.let { plan ->
            if(!session.state.value.loaded) {
                val request=++packPlaybackRequest
                val flowTitle = _ui.value.flow?.title ?: plan.name
                val snapshotBytes = flowBytes
                val snapshotTotalFrames = totalFrames
                val snapshotGains = currentLayerGains(plan)
                viewModelScope.launch {
                    PlaybackService.awaitForegroundPromotion()
                    if(request!=packPlaybackRequest || interactivePlan!==plan || nativePerformance?.isFading == true) return@launch
                    val prep = withContext(Dispatchers.IO) {
                        session.prepareFlow(
                            title = flowTitle,
                            packet = plan.packet,
                            assets = plan.assetPaths.map { it to snapshotBytes.getValue(it) },
                            frames = snapshotTotalFrames,
                            startSegment = plan.entry,
                            streamed = plan.streamed,
                            autoAdvance = plan.automaticNext,
                            preparedAssets = plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
                            prequeueInitial = plan.entry in plan.entryAutomaticNext,
                            initialLayerGains = snapshotGains,
                        )
                    }
                    when (prep) {
                        is PlaybackSession.PrepareResult.Failed -> {
                            if (request == packPlaybackRequest && interactivePlan === plan) {
                                _ui.value = _ui.value.copy(detail = prep.reason)
                            }
                            return@launch
                        }
                        is PlaybackSession.PrepareResult.Ok -> {
                            if (request != packPlaybackRequest || interactivePlan !== plan || nativePerformance?.isFading == true) {
                                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prep.prepared.discard() }
                                return@launch
                            }
                            val state = session.commitPrepared(prep.prepared)
                            if (state.detail != null) {
                                _ui.value = _ui.value.copy(detail = state.detail)
                                return@launch
                            }
                            nativePerformance = null
                            startPerformance()
                        }
                    }
                }
                return
            }
            if(nativePerformance==null) nativePerformance=NativeInteractiveDriver(plan.spec,plan.entry,plan.next,plan.outros,plan.fast,
                request={ segment -> session.playSegment(segment).detail },versions=plan.versions,outroVariants=plan.outroVariants,regions=plan.regions,cancelRequest=session::cancelPendingTransition,mandatoryAutomaticSources=plan.entryAutomaticNext.keys,advanceViaOutro=plan.advanceViaOutro)
            session.interactiveObservation()?.let { nativePerformance!!.observe(it) }
            publishNativePerformance(fromAction=true)
            PlaybackService.start(getApplication())
            requestNotificationPermissionIfNeeded()
            startPolling()
            return
        }
        val compiled = flowCompiled ?: return
        val open = flow ?: return
        if (performance?.isRunning == true) return

        val segments = compiled.segmentByRole
        val driver = PerformanceDriver(
            performance = StagePerformance(
                flow = open,
                segmentByRole = segments,
                segmentByOutro = compiled.segmentByOutro,
            ),
            startSegment = { segment -> session.playSegment(segment).detail == null },
            transport = { session.state.value },
        )

        apply(driver.start(), driver)
        if (driver.isRunning) performance = driver else driver.stop()
    }

    /** The next-stage event: leave the looping prelude for the main loop. */
    fun advancePerformance() {
        nativePerformance?.let { publishNativePerformance(it.advance(),true);return }
        val driver = performance ?: return
        apply(driver.advance(), driver)
    }

    /** Switches between the main loop and its fast arrangement. */
    fun toggleFast() {
        nativePerformance?.let { publishNativePerformance(it.enterFast(),true);return }
        val driver = performance ?: return
        apply(driver.toggleFast(), driver)
    }

    /** Takes the ending nearest to where the main loop has got to. */
    fun takeOutro() {
        nativePerformance?.let { publishNativePerformance(it.takeOutro(),true);return }
        val driver = performance ?: return
        apply(driver.takeOutro(), driver)
    }

    /** Takes a specific Outro number (0-indexed, e.g. 0, 1, 2 for Title Outros). */
    fun takeOutro(number: Int) {
        nativePerformance?.let {
            publishNativePerformance(it.takeOutro(number), true)
            return
        }
    }

    fun toggleSpeakerOverlay() {
        val plan = interactivePlan ?: return
        if (plan.overlayTrackIndices.isEmpty()) return
        speakerEnabled = !speakerEnabled
        val db = if (speakerEnabled) 0.0 else -96.0
        if (session.state.value.loaded) {
            plan.overlayTrackIndices.forEach { trackIdx ->
                session.setLayerGain(trackIdx, db, fadeMs = 1000)
            }
        }
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(speakerEnabled = speakerEnabled),
        )
    }

    fun openInteractiveTitle(
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        autoStart: Boolean = false,
    ) {
        if (nativePerformance?.isFading == true) return
        val item = extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle>()
            .firstOrNull()
        val dir = pack?.directory
        if (item == null || dir == null) {
            _ui.value = _ui.value.copy(status = "Cannot open Title Theme", detail = "No installed pack carries this track.")
            return
        }
        val request = ++packPlaybackRequest
        viewModelScope.launch {
            val loaded = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val refs = listOf(item.harmonica, item.main, item.brassCut) + item.outros.map { it.asset }
                    val byStem = mutableMapOf<String, ByteArray>()
                    val byPath = mutableMapOf<String, ByteArray>()
                    refs.forEach { ref ->
                        if (ref.stem !in byStem) {
                            val file = java.io.File(dir, "audio/${ref.sha256}")
                            if (!file.isFile) error("Missing audio file ${ref.stem}")
                            val bytes = file.readBytes()
                            byStem[ref.stem] = bytes
                            byPath["${ref.stem}.bwav"] = bytes
                        }
                    }
                    val plan = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileTitlePlan(item, byStem)
                    plan to byPath
                }
            }
            if (request != packPlaybackRequest) return@launch
            val (plan, bytesByPath) = loaded.getOrElse { err ->
                _ui.value = _ui.value.copy(status = "Cannot prepare Title Theme", detail = err.message)
                return@launch
            }

            linearCoordinator.yieldToExternalTransport()
            session.stop()
            this@PlayerViewModel.flow = null
            this@PlayerViewModel.flowCompiled = null
            this@PlayerViewModel.performance = null
            this@PlayerViewModel.nativePerformance = null
            this@PlayerViewModel.interactivePlan = plan
            this@PlayerViewModel.flowBytes = bytesByPath
            this@PlayerViewModel.totalFrames = item.main.frames
            this@PlayerViewModel.rate = item.main.sampleRate

            val localizedTitle = item.localizedName(languageTag)
            _ui.value = _ui.value.copy(
                title = localizedTitle,
                status = "Stopped",
                total = EngineSnapshot.formatPosition(item.main.frames, item.main.sampleRate),
                position = "0:00",
                progress = 0f,
                canPause = false,
                canResume = false,
                canStop = false,
                outputActive = session.hasOutput,
                linearMode = false,
                linearTrack = null,
                canSeek = false,
                totalFrames = item.main.frames,
                sampleRate = item.main.sampleRate,
                artworkName = item.artwork,
                detail = null,
                flow = FlowUi(
                    title = localizedTitle,
                    sections = emptyList(),
                    activeSegment = null,
                    summary = "",
                    provenance = "",
                    isProvisional = false,
                    outros = plan.outros.keys.sorted(),
                    hasFast = false,
                    availableStageKeys = plan.customInitialSegments.mapNotNull { plan.labels[it] },
                ),
            )
            if (autoStart) {
                startPerformance()
            }
        }
    }

    fun openInteractiveTwoState(
        track: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack,
        isBattle: Boolean,
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        autoStart: Boolean = true,
    ) {
        if (nativePerformance?.isFading == true) return
        val dir = pack?.directory
        if (dir == null) {
            _ui.value = _ui.value.copy(status = "Cannot open track", detail = "No installed pack carries this track.")
            return
        }
        val request = ++packPlaybackRequest
        viewModelScope.launch {
            val loaded = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val mainFile = java.io.File(dir, "audio/${track.main.sha256}")
                    val fastFile = java.io.File(dir, "audio/${track.fast.sha256}")
                    if (!mainFile.isFile || !fastFile.isFile) error("Missing audio files for ${track.id}")
                    val mainBytes = mainFile.readBytes()
                    val fastBytes = fastFile.readBytes()
                    val ffAsset = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET
                    val ffBytes = finalLapFanfareBytes
                    val byStem = mapOf(
                        track.main.stem to mainBytes,
                        track.fast.stem to fastBytes,
                        ffAsset to ffBytes,
                    )
                    val byPath = mapOf(
                        "${track.main.stem}.bwav" to mainBytes,
                        "${track.fast.stem}.bwav" to fastBytes,
                        ffAsset to ffBytes,
                    )
                    val plan = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileTwoStateLoopPlan(
                        track = track,
                        isBattle = isBattle,
                        bytesByStem = byStem,
                    )
                    plan to byPath
                }
            }
            if (request != packPlaybackRequest) return@launch
            val (plan, bytesByPath) = loaded.getOrElse { err ->
                _ui.value = _ui.value.copy(status = "Cannot prepare track", detail = err.message)
                return@launch
            }

            linearCoordinator.yieldToExternalTransport()
            session.stop()
            this@PlayerViewModel.flow = null
            this@PlayerViewModel.flowCompiled = null
            this@PlayerViewModel.performance = null
            this@PlayerViewModel.nativePerformance = null
            this@PlayerViewModel.interactivePlan = plan
            this@PlayerViewModel.flowBytes = bytesByPath
            this@PlayerViewModel.totalFrames = track.main.frames
            this@PlayerViewModel.rate = track.main.sampleRate

            val localizedTitle = track.localizedName(languageTag)
            _ui.value = _ui.value.copy(
                title = localizedTitle,
                status = "Stopped",
                total = EngineSnapshot.formatPosition(track.main.frames, track.main.sampleRate),
                position = "0:00",
                progress = 0f,
                canPause = false,
                canResume = false,
                canStop = false,
                outputActive = session.hasOutput,
                linearMode = false,
                linearTrack = null,
                canSeek = false,
                totalFrames = track.main.frames,
                sampleRate = track.main.sampleRate,
                artworkName = track.artwork,
                detail = null,
                flow = FlowUi(
                    title = localizedTitle,
                    sections = emptyList(),
                    activeSegment = null,
                    summary = "",
                    provenance = "",
                    isProvisional = false,
                    outros = emptyList(),
                    hasFast = true,
                    isBattleTrack = isBattle,
                    availableStageKeys = plan.customInitialSegments.mapNotNull { plan.labels[it] },
                ),
            )
            if (autoStart) {
                startPerformance()
            }
        }
    }

    fun playExtraLinearQueue(
        entries: List<LinearQueue.Entry>,
        startIndex: Int,
        repeatMode: LinearQueue.RepeatMode = LinearQueue.RepeatMode.LIST,
        shuffle: Boolean = false,
        context: LinearPlaybackCoordinator.SessionContext = LinearPlaybackCoordinator.SessionContext(
            contextKind = LinearSessionStore.ContextKind.EXTRA,
            packFingerprints = currentPackFingerprints(),
        ),
    ) {
        val dir = pack?.directory
        if (dir == null || entries.isEmpty()) return
        if (nativePerformance?.isFading == true) return
        packPlaybackRequest++
        nativePerformance = null
        performance = null
        interactivePlan = null
        flow = null
        flowCompiled = null
        val target = entries[startIndex.coerceIn(0, entries.lastIndex)]
        _ui.value = _ui.value.copy(
            title = target.title,
            artworkName = target.artworkName,
            flow = null,
            linearMode = true,
            canSeek = true,
            detail = null,
        )
        linearCoordinator.startExtraQueue(
            entries = entries,
            startIndex = startIndex,
            packDir = dir,
            repeatMode = repeatMode,
            shuffleOverride = shuffle,
            context = context,
        )
        startPolling()
    }

    internal fun courseMedleysForCourse(code: String): List<Pair<CourseMedleyCatalog.Medley, Boolean>> {
        if (isSpecialStageLocked(code)) return emptyList()
        val dir = pack?.directory
        val catalog = courseMedleyCatalog
        return catalog.forCourse(code).map { medley ->
            val playable = dir != null && com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                medley = medley,
                sampleRate = catalog.sampleRate,
                identities = interactiveIdentities,
                packDir = dir,
            ) != null
            medley to playable
        }
    }

    fun playCourseMedley(
        medleyId: String,
        languageTag: String,
        unlockedCourses: List<String>,
        suiteLabel: String,
        rallyLabel: String,
    ): Boolean {
        val dir = pack?.directory ?: return false
        val catalog = courseMedleyCatalog
        val unlockedSet = unlockedCourses.map { it.removeSuffix("_00") }.toSet()
        val entries = catalog.medleys.mapNotNull { medley ->
            if (isSpecialStageLocked(medley.courseCode)) return@mapNotNull null
            if (unlockedSet.isNotEmpty() && medley.courseCode !in unlockedSet) return@mapNotNull null
            val resolved = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                medley = medley,
                sampleRate = catalog.sampleRate,
                identities = interactiveIdentities,
                packDir = dir,
            ) ?: return@mapNotNull null
            val courseName = localizedNames?.name(medley.courseCode, languageTag) ?: medley.courseCode
            val title = if (medley.isKnockoutVariant) "$courseName · $rallyLabel" else courseName
            val subtitle = if (medley.isKnockoutVariant) "$suiteLabel · $rallyLabel" else suiteLabel
            LinearQueue.Entry(
                key = "medley:${medley.id}",
                title = title,
                subtitle = subtitle,
                sourceType = LinearSessionStore.SourceType.MEDLEY,
                courseCode = medley.courseCode,
                medleyId = medley.id,
                durationMs = medley.durationMs,
                artworkName = extraCatalog.courseArtwork[medley.courseCode],
                courseMedley = resolved,
            )
        }
        val startIndex = entries.indexOfFirst { it.key == "medley:$medleyId" }
        if (startIndex < 0) return false
        playExtraLinearQueue(
            entries = entries,
            startIndex = startIndex,
            repeatMode = LinearQueue.RepeatMode.LIST,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = medleyId,
                contextTitle = suiteLabel,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    internal fun inspectCourseForEditor(code: String): CourseFlowRecipeBuilder.CourseInventory? {
        val dir = pack?.directory ?: return null
        if (isSpecialStageLocked(code)) return null
        val resolver = CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, dir)
        return CourseFlowRecipeBuilder.inspectCourse(code, minfCatalog, courseMedleyCatalog, resolver)
    }

    internal fun previewCustomPreset(
        preset: CoursePreset,
        courseTitle: String,
        suiteLabel: String,
    ): Boolean {
        val dir = pack?.directory ?: return false
        if (isSpecialStageLocked(preset.courseCode)) return false
        val resolver = CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, dir)
        val inventory = CourseFlowRecipeBuilder.inspectCourse(preset.courseCode, minfCatalog, courseMedleyCatalog, resolver)
        val resolved = CourseFlowRecipeBuilder.compileUserPreset(preset, inventory, minfCatalog, resolver)
            ?: return false
        val logical = preset.courseCode.removeSuffix("_00")
        val entry = LinearQueue.Entry(
            key = "preset:${preset.id}",
            title = courseTitle,
            subtitle = "$suiteLabel · ${preset.name}",
            sourceType = LinearSessionStore.SourceType.PRESET,
            courseCode = logical,
            presetId = preset.id,
            durationMs = resolved.medley.durationMs,
            artworkName = extraCatalog.courseArtwork[logical],
            courseMedley = resolved,
        )
        playExtraLinearQueue(
            entries = listOf(entry),
            startIndex = 0,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = preset.id,
                contextTitle = preset.name,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    /**
     * Launches linear playback of a user playlist (#50-#53, #61) with frozen queue state,
     * per-entry preset compilation, and opt-in native cross-track handover where verified.
     */
    fun playUserPlaylist(
        playlist: UserPlaylist,
        presets: List<CoursePreset>,
        startIndex: Int,
        shuffle: Boolean?,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        nativeTransitionPrefix: String,
    ): Boolean {
        val dir = pack?.directory ?: return false
        if (playlist.courseIds.isEmpty()) return false
        val resolver = CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, dir)
        val rawEntries = playlist.courseIds.mapIndexedNotNull { idx, rawCourseId ->
            val logical = rawCourseId.removeSuffix("_00")
            if (isSpecialStageLocked(logical)) return@mapIndexedNotNull null
            val config = playlist.configAt(idx)
            val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                courseCode = logical,
                presetRef = config.presetId,
                userPresets = presets,
                minf = minfCatalog,
                medleyCatalog = courseMedleyCatalog,
                resolver = resolver,
            ) ?: return@mapIndexedNotNull null

            val matchingUserPreset = if (PresetRefs.isUser(config.presetId)) {
                presets.firstOrNull { it.id == config.presetId && it.courseCode.equals(logical, ignoreCase = true) }
            } else {
                null
            }
            val effectiveRef = matchingUserPreset?.id
                ?: if (config.presetId == PresetRefs.SYSTEM_KNOCKOUT && resolved.medley.isKnockoutVariant) {
                    PresetRefs.SYSTEM_KNOCKOUT
                } else {
                    PresetRefs.SYSTEM_DEFAULT
                }
            val presetDisplayName = when {
                matchingUserPreset != null -> matchingUserPreset.name
                effectiveRef == PresetRefs.SYSTEM_KNOCKOUT -> rallyLabel
                else -> suiteLabel
            }
            val courseName = localizedNames?.name(logical, languageTag) ?: logical
            val nativeEnabled = config.nativeTransitionEnabled ?: playlist.nativeTransitionDefault
            LinearQueue.Entry(
                key = "pl:${playlist.id}:$idx:$logical:$effectiveRef",
                title = courseName,
                subtitle = "${playlist.name} · $presetDisplayName",
                sourceType = if (matchingUserPreset != null) {
                    LinearSessionStore.SourceType.PRESET
                } else {
                    LinearSessionStore.SourceType.MEDLEY
                },
                courseCode = logical,
                presetId = effectiveRef,
                medleyId = resolved.medley.id.takeIf { matchingUserPreset == null },
                nativeTransitionEnabled = nativeEnabled,
                nativeTransitionOverride = config.nativeTransitionEnabled,
                nativeTransitionSourceLabel = if (nativeEnabled && resolved.outroHandoverFrame != null) {
                    "$nativeTransitionPrefix · $presetDisplayName"
                } else {
                    null
                },
                arrangement = presetDisplayName,
                durationMs = resolved.medley.durationMs,
                artworkName = extraCatalog.courseArtwork[logical],
                courseMedley = resolved,
            )
        }
        if (rawEntries.isEmpty()) return false
        val clampedStart = startIndex.coerceIn(0, rawEntries.lastIndex)
        playExtraLinearQueue(
            entries = rawEntries,
            startIndex = clampedStart,
            repeatMode = LinearQueue.RepeatMode.LIST,
            shuffle = shuffle == true,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.PLAYLIST,
                contextId = playlist.id,
                contextTitle = playlist.name,
                nativeTransitionDefault = playlist.nativeTransitionDefault,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    sealed interface RoutePlayResult {
        data object Started : RoutePlayResult
        data class Blocked(val reason: String) : RoutePlayResult
    }

    fun allInstalledJukeboxItems(): List<Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>> =
        jukebox.games.flatMap { game -> game.items.map { item -> item to game } }

    fun inspectOfficialRoute(
        route: com.mylittleblover.mkwplayer.routes.OfficialRoute,
        sessionOverrides: Map<Int, String> = emptyMap(),
    ): com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport {
        val dir = pack?.directory
        val items = jukebox.games.flatMap { it.items }
        return com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.inspectOfficialRoute(
            route = route,
            connections = stageConnections,
            pool = roadJukeboxPool,
            installedJukebox = items,
            sessionOverrides = sessionOverrides,
            isStagePlayable = ::canStartStage,
            isStageHidden = ::isSpecialStageLocked,
            isJukeboxReadable = { item ->
                val resolvedDir = jukebox.packByKey[item.track.key] ?: dir
                resolvedDir != null && java.io.File(resolvedDir, "audio/${item.track.primary.sha256}").isFile
            },
        )
    }

    fun inspectCustomRoute(
        route: com.mylittleblover.mkwplayer.routes.CustomRoute,
    ): com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport {
        val dir = pack?.directory
        val items = jukebox.games.flatMap { it.items }
        return com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.inspectCustomRoute(
            route = route,
            connections = stageConnections,
            pool = roadJukeboxPool,
            installedJukebox = items,
            isStagePlayable = ::canStartStage,
            isStageHidden = ::isSpecialStageLocked,
            isJukeboxReadable = { item ->
                val resolvedDir = jukebox.packByKey[item.track.key] ?: dir
                resolvedDir != null && java.io.File(resolvedDir, "audio/${item.track.primary.sha256}").isFile
            },
        )
    }

    fun playOfficialRoute(
        route: com.mylittleblover.mkwplayer.routes.OfficialRoute,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        loopRoute: Boolean,
        sessionOverrides: Map<Int, String> = emptyMap(),
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
    ): RoutePlayResult {
        if (nativePerformance?.isFading == true) {
            return RoutePlayResult.Blocked("Playback is currently fading out.")
        }
        val report = inspectOfficialRoute(route, sessionOverrides)
        if (!report.canPlay) {
            val reason = report.blockingReason ?: "Route cannot be played."
            _ui.value = _ui.value.copy(detail = reason)
            return RoutePlayResult.Blocked(reason)
        }
        val stops = route.stops.map {
            com.mylittleblover.mkwplayer.routes.CustomRouteStop(it.stageCode, null)
        }
        val entries = buildRouteQueueEntries(
            routeId = "official:${route.id}",
            stops = stops,
            report = report,
            userPresets = userPresets,
            languageTag = languageTag,
            suiteLabel = suiteLabel,
            rallyLabel = rallyLabel,
            roadPrefix = roadPrefix,
            customSubstituteTag = "",
        ) ?: return RoutePlayResult.Blocked("Missing required course medley audio.").also {
            _ui.value = _ui.value.copy(detail = it.reason)
        }
        playExtraLinearQueue(
            entries = entries,
            startIndex = 0,
            repeatMode = if (loopRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.OFFICIAL_ROUTE,
                contextId = route.id,
                contextTitle = route.displayName(languageTag),
                nativeTransitionDefault = false,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = false,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = true,
            ),
        )
        requestNotificationPermissionIfNeeded()
        return RoutePlayResult.Started
    }

    fun playCustomRoute(
        route: com.mylittleblover.mkwplayer.routes.CustomRoute,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
        customSubstituteTag: String,
    ): RoutePlayResult {
        if (nativePerformance?.isFading == true) {
            return RoutePlayResult.Blocked("Playback is currently fading out.")
        }
        val report = inspectCustomRoute(route)
        if (!report.canPlay) {
            val reason = report.blockingReason ?: "Custom route cannot be played."
            _ui.value = _ui.value.copy(detail = reason)
            return RoutePlayResult.Blocked(reason)
        }
        val entries = buildRouteQueueEntries(
            routeId = "custom:${route.id}",
            stops = route.stops,
            report = report,
            userPresets = userPresets,
            languageTag = languageTag,
            suiteLabel = suiteLabel,
            rallyLabel = rallyLabel,
            roadPrefix = roadPrefix,
            customSubstituteTag = customSubstituteTag,
        ) ?: return RoutePlayResult.Blocked("Missing required course medley audio.").also {
            _ui.value = _ui.value.copy(detail = it.reason)
        }
        playExtraLinearQueue(
            entries = entries,
            startIndex = 0,
            repeatMode = if (route.loopWholeRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.CUSTOM_ROUTE,
                contextId = route.id,
                contextTitle = route.name,
                nativeTransitionDefault = false,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = false,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = true,
            ),
        )
        requestNotificationPermissionIfNeeded()
        return RoutePlayResult.Started
    }

    private fun buildRouteQueueEntries(
        routeId: String,
        stops: List<com.mylittleblover.mkwplayer.routes.CustomRouteStop>,
        report: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
        customSubstituteTag: String,
    ): List<LinearQueue.Entry>? {
        val dir = pack?.directory ?: return null
        val resolver = CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, dir)
        val gamesById = jukebox.games.associateBy { it.id }
        val entries = mutableListOf<LinearQueue.Entry>()

        for (i in stops.indices) {
            val stop = stops[i]
            val logical = stop.stageCode.removeSuffix("_00")
            val presetRef = stop.presetId ?: com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_DEFAULT
            val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                courseCode = logical,
                presetRef = presetRef,
                userPresets = userPresets,
                minf = minfCatalog,
                medleyCatalog = courseMedleyCatalog,
                resolver = resolver,
            ) ?: return null
            val displayCourse = localizedNames?.name(logical, languageTag) ?: logical
            val presetDisplayName = when (presetRef) {
                com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_KNOCKOUT -> rallyLabel
                com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_DEFAULT -> suiteLabel
                else -> userPresets.firstOrNull { it.id == presetRef }?.name ?: suiteLabel
            }
            entries += LinearQueue.Entry(
                key = "$routeId:stop:$i:$logical",
                title = displayCourse,
                subtitle = presetDisplayName,
                sourceType = if (com.mylittleblover.mkwplayer.library.PresetRefs.isUser(presetRef)) {
                    LinearSessionStore.SourceType.PRESET
                } else {
                    LinearSessionStore.SourceType.MEDLEY
                },
                courseCode = logical,
                presetId = stop.presetId,
                medleyId = resolved.medley.id,
                nativeTransitionEnabled = false,
                arrangement = presetDisplayName,
                durationMs = resolved.medley.durationMs,
                artworkName = extraCatalog.courseArtwork[logical],
                courseMedley = resolved,
            )

            val leg = report.legs.getOrNull(i) ?: continue
            val fromName = localizedNames?.name(leg.from, languageTag) ?: leg.from
            val toName = localizedNames?.name(leg.to, languageTag) ?: leg.to
            val legLabel = "$roadPrefix $fromName → $toName"

            fun roadEntry(item: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, subLabel: String): LinearQueue.Entry {
                val game = gamesById[item.gameId]
                val gameName = game?.localizedName(languageTag) ?: item.gameId
                return LinearQueue.Entry(
                    key = "$routeId:edge:$i:${item.key}",
                    title = item.localizedDisplayName(languageTag),
                    subtitle = subLabel,
                    sourceType = LinearSessionStore.SourceType.JUKEBOX,
                    gameId = item.gameId,
                    gameTitle = gameName,
                    yearLabel = game?.yearLabel?.takeIf { it.isNotBlank() },
                    arrangement = item.localizedArrangement(languageTag),
                    ostIndex = item.ostIndex,
                    durationMs = item.durationMs,
                    artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                    assetSha256 = item.track.primary.sha256,
                    sourceTrackKey = item.track.key,
                )
            }

            when (val res = leg.resolution) {
                is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete -> {
                    val override = res.selectedOverride
                    if (override != null) {
                        entries += roadEntry(override, legLabel)
                    } else if (res.candidates.isNotEmpty()) {
                        val variants = res.candidates.map { roadEntry(it, legLabel) }
                        entries += variants.first().copy(roadCandidateVariants = variants)
                    }
                }
                is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute -> {
                    val subLabel = if (customSubstituteTag.isNotBlank()) "$legLabel · $customSubstituteTag" else legLabel
                    entries += roadEntry(res.track, subLabel)
                }
                else -> Unit // Candidate missing or muted -> cleanly skipped (#73)
            }
        }
        return entries.takeIf { it.isNotEmpty() }
    }

    /** Inspects any saved linear session on cold start and exposes "Continue listening" if still valid (#62, #89). */
    fun refreshContinueListening() {
        val currentState = _ui.value
        if (linearCoordinator.state.value.active || session.state.value.active) {
            if (currentState.continueListening != null) {
                _ui.value = currentState.copy(continueListening = null)
            }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val saved = linearSessionStore.read().getOrNull() ?: return@withContext null
                val presetFile = java.io.File(getApplication<Application>().filesDir, "course-presets.json")
                val userPresets = CoursePresetStore(presetFile).read().getOrDefault(emptyList())
                if (!verifySavedSessionAvailable(saved, pack?.directory, userPresets)) {
                    linearSessionStore.clear()
                    return@withContext null
                }
                val rateHz = saved.sampleRate.coerceAtLeast(1)
                val progress = if (saved.totalFrames > 0L) {
                    (saved.positionFrames.toDouble() / saved.totalFrames).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                ContinueListeningUi(
                    key = saved.current.key,
                    title = saved.current.title,
                    subtitle = saved.contextTitle?.takeIf { it.isNotBlank() } ?: saved.current.subtitle,
                    artworkName = saved.current.artworkName,
                    positionText = EngineSnapshot.formatPosition(saved.positionFrames, rateHz),
                    totalText = EngineSnapshot.formatPosition(saved.totalFrames, rateHz),
                    progress = progress,
                    positionFrames = saved.positionFrames,
                    totalFrames = saved.totalFrames,
                    sampleRate = rateHz,
                    contextKind = saved.contextKind,
                    contextId = saved.contextId,
                    contextTitle = saved.contextTitle,
                )
            }
            if (!linearCoordinator.state.value.active && !session.state.value.active) {
                _ui.value = _ui.value.copy(continueListening = result)
            }
        }
    }

    fun dismissContinueListening() {
        linearCoordinator.clearSavedSession()
        _ui.value = _ui.value.copy(continueListening = null)
    }

    /** Restores a paused linear queue snapshot from disk and resumes at the exact frame (#62, #89). */
    fun resumeContinueListening(onReady: () -> Unit = {}) {
        if (nativePerformance?.isFading == true) return
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val saved = linearSessionStore.read().getOrNull() ?: return@withContext null
                val presetFile = java.io.File(getApplication<Application>().filesDir, "course-presets.json")
                val userPresets = CoursePresetStore(presetFile).read().getOrDefault(emptyList())
                val dir = pack?.directory
                val juke = jukebox
                val resolver = dir?.let {
                    CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, it)
                }
                fun toEntry(se: LinearSessionStore.SavedEntry): LinearQueue.Entry? =
                    resolveSavedEntry(se, dir, juke, userPresets, resolver, saved.nativeTransitionDefault)

                val frozen = saved.frozenBase.mapNotNull(::toEntry)
                val current = toEntry(saved.current) ?: return@withContext null
                if (frozen.isEmpty() && saved.frozenBase.isNotEmpty()) return@withContext null
                val past = saved.pastHistory.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val forward = saved.forwardHistory.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val temp = saved.tempQueue.mapNotNull(::toEntry)
                val upcoming = saved.upcomingBase.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val exported = LinearQueue.ExportedState(
                    frozenBase = frozen,
                    current = current,
                    currentBaseIndex = saved.currentBaseIndex,
                    pastHistory = past,
                    forwardHistory = forward,
                    tempQueue = temp,
                    upcomingBase = upcoming,
                    repeatMode = saved.repeatMode,
                    shuffle = saved.shuffle,
                )
                val isFixedRoute = saved.contextKind == LinearSessionStore.ContextKind.OFFICIAL_ROUTE ||
                    saved.contextKind == LinearSessionStore.ContextKind.CUSTOM_ROUTE
                val context = LinearPlaybackCoordinator.SessionContext(
                    contextKind = saved.contextKind,
                    contextId = saved.contextId,
                    contextTitle = saved.contextTitle,
                    nativeTransitionDefault = saved.nativeTransitionDefault,
                    packFingerprints = currentPackFingerprints(),
                    allowPlayNext = !isFixedRoute,
                    allowShuffle = !isFixedRoute,
                    allowSingleRepeat = !isFixedRoute,
                )
                Triple(exported, context, saved.positionFrames)
            }
            if (restored == null) {
                linearCoordinator.clearSavedSession()
                _ui.value = _ui.value.copy(continueListening = null)
                return@launch
            }
            prepareForLinearPlayback()
            val (exported, context, posFrames) = restored
            linearCoordinator.restoreAndPlay(
                state = exported,
                context = context,
                packDir = pack?.directory,
                resumePositionFrames = posFrames,
            )
            startPolling()
            onReady()
        }
    }

    private fun customRoutesFile(): java.io.File =
        java.io.File(getApplication<Application>().filesDir, "routes/custom_routes.json")

    private fun buildInvalidationEnvironment(
        manifest: com.mylittleblover.mkwplayer.pack.PackManifest?,
        directory: java.io.File?,
        jukeboxCatalog: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog,
        customRoutes: List<com.mylittleblover.mkwplayer.routes.CustomRoute>,
    ): LibraryInvalidation.Environment = LibraryInvalidation.Environment(
        mergedManifest = manifest,
        mergedDirectory = directory,
        jukeboxCatalog = jukeboxCatalog,
        roadPool = roadJukeboxPool,
        stageConnections = stageConnections,
        officialRoutes = officialRouteCatalog,
        customRoutes = customRoutes,
        extraCatalog = extraCatalog,
        isStageHidden = ::isSpecialStageLocked,
    )

    private fun currentPackFingerprints(): List<String> = if (mergedFingerprints.isNotEmpty()) {
        mergedFingerprints
    } else {
        listOfNotNull(pack?.manifest?.let { "${it.packId}:${it.packVersion}" })
    }

    private fun verifySavedSessionAvailable(
        saved: LinearSessionStore.Snapshot,
        packDir: java.io.File?,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    ): Boolean {
        val juke = jukebox
        val customRoutes = com.mylittleblover.mkwplayer.routes.CustomRouteStore(customRoutesFile())
            .read().getOrDefault(emptyList())
        val invalidEnv = buildInvalidationEnvironment(
            manifest = pack?.manifest,
            directory = packDir,
            jukeboxCatalog = juke,
            customRoutes = customRoutes,
        )
        if (!LibraryInvalidation.isSavedSessionStillValid(saved, invalidEnv)) return false
        val resolver = packDir?.let {
            CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, it)
        }
        val toCheck = buildList {
            add(saved.current)
            addAll(saved.frozenBase)
            addAll(saved.tempQueue)
        }
        return toCheck.all { se ->
            resolveSavedEntry(se, packDir, juke, userPresets, resolver, saved.nativeTransitionDefault) != null
        }
    }

    private fun resolveSavedEntry(
        se: LinearSessionStore.SavedEntry,
        packDir: java.io.File?,
        juke: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog,
        userPresets: List<CoursePreset>,
        resolver: CourseFlowRecipeBuilder.SourceResolver?,
        defaultNative: Boolean,
    ): LinearQueue.Entry? {
        val nativeEnabled = se.nativeTransitionOverride ?: defaultNative
        return when (se.sourceType) {
            LinearSessionStore.SourceType.JUKEBOX -> {
                val lookupKey = se.sourceTrackKey ?: se.key
                val item = juke.tracksByKey[lookupKey] ?: return null
                val sha = se.assetSha256 ?: item.primary.sha256
                val dir = juke.packByKey[lookupKey] ?: packDir ?: return null
                if (!java.io.File(dir, "audio/$sha").isFile) return null
                LinearQueue.Entry(
                    key = se.key,
                    title = se.title,
                    subtitle = se.subtitle,
                    sourceType = se.sourceType,
                    gameId = se.gameId,
                    gameTitle = se.gameTitle,
                    yearLabel = se.yearLabel,
                    arrangement = se.arrangement,
                    ostIndex = se.ostIndex,
                    durationMs = se.durationMs,
                    artworkName = se.artworkName,
                    assetSha256 = sha,
                    sourceTrackKey = lookupKey,
                    loop = se.loop,
                    loopStart = se.loopStart,
                    loopEnd = se.loopEnd,
                )
            }
            LinearSessionStore.SourceType.EXTRA -> {
                val dir = packDir ?: return null
                val extraId = se.key.removePrefix("extra:")
                val extraItem = extraCatalog.otherMusic.firstOrNull { it.id == extraId }
                when (extraItem) {
                    is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                        if (!java.io.File(dir, "audio/${extraItem.op.sha256}").isFile ||
                            !java.io.File(dir, "audio/${extraItem.telop.sha256}").isFile
                        ) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            overlaidOpening = extraItem,
                        )
                    }
                    is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                        if (extraItem.parts.any { !java.io.File(dir, "audio/${it.asset.sha256}").isFile }) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            chainedSequence = extraItem,
                        )
                    }
                    else -> {
                        val sha = se.assetSha256 ?: return null
                        if (!java.io.File(dir, "audio/$sha").isFile) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            assetSha256 = sha,
                            loop = se.loop,
                            loopStart = se.loopStart,
                            loopEnd = se.loopEnd,
                        )
                    }
                }
            }
            LinearSessionStore.SourceType.MEDLEY,
            LinearSessionStore.SourceType.PRESET -> {
                val res = resolver ?: return null
                val code = se.courseCode ?: return null
                if (isSpecialStageLocked(code)) return null
                val presetRef = se.presetId ?: if (se.medleyId != null &&
                    courseMedleyCatalog.medleys.firstOrNull { it.id == se.medleyId }?.isKnockoutVariant == true
                ) PresetRefs.SYSTEM_KNOCKOUT else PresetRefs.SYSTEM_DEFAULT
                if (se.sourceType == LinearSessionStore.SourceType.PRESET &&
                    PresetRefs.isUser(presetRef) &&
                    userPresets.none { it.id == presetRef }
                ) {
                    // User preset was deleted; verify fallback still compiles or clear if requested.
                }
                val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                    courseCode = code,
                    presetRef = presetRef,
                    userPresets = userPresets,
                    minf = minfCatalog,
                    medleyCatalog = courseMedleyCatalog,
                    resolver = res,
                ) ?: return null
                LinearQueue.Entry(
                    key = se.key,
                    title = se.title,
                    subtitle = se.subtitle,
                    sourceType = se.sourceType,
                    courseCode = code,
                    presetId = se.presetId,
                    medleyId = se.medleyId,
                    nativeTransitionEnabled = nativeEnabled,
                    nativeTransitionOverride = se.nativeTransitionOverride,
                    arrangement = se.arrangement,
                    durationMs = se.durationMs,
                    artworkName = se.artworkName,
                    courseMedley = resolved,
                )
            }
        }
    }

    internal val initialEntryChoices: Map<Int,String>
        get() = interactivePlan?.let { plan -> plan.initialSegments.associateWith { plan.labels[it].orEmpty() } }.orEmpty()
    internal val selectedEntrySegment: Int? get() = interactivePlan?.entry

    fun selectInitialEntry(segment: Int) {
        val plan = interactivePlan ?: return
        if (_ui.value.canStop || nativePerformance?.isFading == true || segment !in plan.initialSegments) return
        packPlaybackRequest++
        publish(session.stop())
        nativePerformance = null
        interactivePlan = plan.copy(
            entry = segment,
            preparedAssets = plan.entryPreparedAssets[segment] ?: plan.preparedAssets,
        )
        _ui.value = _ui.value.copy(flow = _ui.value.flow?.copy(activeSegment=null, performanceStage="", stageKey=null, pendingStageKey=null, performing=false))
    }

    internal val canFadeStop: Boolean
        get() = interactivePlan != null && session.state.value.loaded && _ui.value.canStop && nativePerformance?.isFading != true

    fun fadeStopPerformance(seconds: Int) {
        if(seconds !in listOf(1,3,5,10) || !canFadeStop) return
        startPerformance()
        val driver=nativePerformance ?: return
        if(driver.isFading) return
        if(_ui.value.canResume) resume()
        val plan=interactivePlan ?: return
        val error=session.fadeStop(seconds*plan.spec.bpm/60.0)
        if(error==null) { packPlaybackRequest++;driver.markFadeRequested() }
        publishNativePerformance(error,true)
    }

    /** Stops a performance without ending the stage. */
    fun stopPerformance() {
        packPlaybackRequest++
        if(nativePerformance!=null) {
            nativePerformance=null
            publish(session.stop())
            _ui.value=_ui.value.copy(flow=_ui.value.flow?.copy(performing=false,performanceStage="Stopped",stageKey="Stopped",pendingStageKey=null,canAdvance=false,canEnd=false,fastPlaying=false,
                canFast=false,canCancelPending=false,fading=false,fadeRemainingFrames=null,voiceCount=0,sourceFrame=null,availableRegions=emptySet(),availableVersions=emptySet(),selectedRegion=null,selectedVersion=null))
            return
        }
        val driver = performance ?: return
        performance = null
        driver.stop()
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                performing = false,
                performanceStage = "Stopped", stageKey = "Stopped", pendingStageKey = null,
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
            ),
        )
    }

    /**
     * Carries a driver event into the screen state.
     *
     * One place, so every control reports the same way. The blocker is only ever cleared by a successful
     * move: clearing it on every event would erase the reason before the listener could read it.
     */
    private fun apply(event: PerformanceDriver.Event, driver: PerformanceDriver) {
        when (event) {
            is PerformanceDriver.Event.Advanced -> publishPerformance(event.segment, driver)
            is PerformanceDriver.Event.Finished -> {
                val finished = !event.reason.contains("stopped", ignoreCase = true)
                if (finished) { stopTransport(cancelPendingOpen = false); markPerformanceFinished() } else stop()
            }
            is PerformanceDriver.Event.Blocked -> {
                // A refused move does not stop the performance: the prelude keeps looping, which is exactly
                // the state a mistaken press should leave the console in.
                _ui.value = _ui.value.copy(detail = event.reason)
            }
            is PerformanceDriver.Event.Waiting -> Unit
        }
    }

    /** Publishes the performance's position after a successful move. */
    private fun publishPerformance(segment: Int, driver: PerformanceDriver) {
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                activeSegment = segment,
                performing = driver.isRunning,
                performanceStage = driver.describe(), stageKey = null, pendingStageKey = null,
                performanceBlocker = null,
                fastPlaying = driver.isFast,
                canAdvance = driver.canAdvance,
                canEnd = driver.canEnd,
            ),
            status = "Playing",
        )
    }

    /** Opens or closes a catalog category in the browser. */
    fun toggleCategory(category: String) {
        _ui.value = _ui.value.copy(
            openCategory = if (_ui.value.openCategory == category) null else category,
        )
    }

    /** Starts the track with the given key, or reports why it cannot start. */
    fun select(key: String) {
        if(nativePerformance?.isFading==true) return
        packPlaybackRequest++
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        if (!track.playable) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                title = track.name,
                status = "Unsupported codec",
                detail = track.reason,
                canPause = false,
                canResume = false,
                canStop = false,
            )
            return
        }

        val ready = prepared[key]
        if (ready == null) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                status = "Not prepared",
                detail = "Pick this file again to reopen it.",
            )
            return
        }

        start(track, ready)
    }

    private fun start(track: Track, ready: Prepared) {
        nativePerformance=null
        interactivePlan=null
        performance=null
        val state = session.load(
            title = ready.name,
            packet = ready.packet,
            audio = ready.bytes,
            assetId = ready.name,
            frames = ready.frames,
        )
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = "Cannot play this file", detail = state.detail)
            return
        }

        linearCoordinator.yieldToExternalTransport()
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        totalFrames = ready.frames
        rate = ready.sampleRate

        _ui.value = _ui.value.copy(
            selectedKey = track.key,
            flow = null,
            title = ready.name,
            status = "Playing",
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canResume = false,
            canStop = true,
            linearMode = false,
            linearTrack = null,
            canSeek = true,
            totalFrames = totalFrames,
            sampleRate = rate,
            outputActive = session.hasOutput,
            detail = if (session.hasOutput) {
                null
            } else {
                "No device output on this system; the engine is running without a speaker."
            },
        )

        // Start the foreground service so playback survives the activity, and on
        // Android 13+ ask for the notification the service needs.
        // Only starts when the session is actually playing; see PlaybackService.start.
        PlaybackService.start(getApplication())
        requestNotificationPermissionIfNeeded()

        startPolling()
    }

    private fun requestNotificationPermissionIfNeeded() {
        // The activity asks; the view model only records that asking is needed, because
        // a permission request needs an Activity. See MainActivity.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val context = getApplication<Application>()
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _ui.value = _ui.value.copy(needsNotificationPermission = true)
        }
    }

    /** Called by the activity once the notification permission result is known. */
    fun onNotificationPermissionResult(granted: Boolean) {
        _ui.value = _ui.value.copy(
            needsNotificationPermission = false,
            detail = if (granted) {
                _ui.value.detail
            } else {
                "Notifications are off, so the media controls will not appear."
            },
        )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                publish(session.poll())
                followPerformance()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Lets the running performance react to the transport.
     *
     * Runs after the state has been published, so an advance is decided on the same reading the screen is
     * about to show rather than on one the listener never saw.
     */
    private fun followPerformance() {
        if(nativePerformance!=null) {
            session.interactiveObservation()?.let { nativePerformance!!.observe(it) }
            if (nativePerformance?.finished == true) {
                val fading = nativePerformance?.isFading == true
                if (fading) stop() else { stopTransport(cancelPendingOpen = false); markPerformanceFinished() }
                return
            }
            publishNativePerformance()
            return
        }
        val driver = performance ?: return
        apply(driver.tick(), driver)
        // The console's availability flags can change without a segment changing -- entering the main loop
        // happens on a command, but the flags are read from the driver, so they are refreshed every poll.
        val current = _ui.value.flow ?: return
        if (current.performing || driver.isRunning) {
            _ui.value = _ui.value.copy(
                flow = current.copy(
                    performanceStage = driver.describe(), stageKey = null, pendingStageKey = null,
                    fastPlaying = driver.isFast,
                    canAdvance = driver.canAdvance,
                    canEnd = driver.canEnd,
                ),
            )
        }
    }
    fun cancelPendingPerformance() {
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.cancelPending(),true)
    }
    fun selectMusicRegion(number: Int) {
        startPerformance()
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.selectRegion(number),true)
    }
    fun selectRecordingVersion(number: Int) {
        startPerformance()
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.selectVersion(number),true)
    }
    private fun publishNativePerformance(error: String?=null,fromAction: Boolean=false) {
        val driver=nativePerformance ?: return
        val plan=interactivePlan ?: return
        val current=_ui.value.flow ?: return
        val observation=driver.observationState
        val segment=plan.spec.segments.singleOrNull { it.index==driver.current }
        val label=plan.labels[driver.current] ?: "Waiting"
        val pendingId=driver.pending
        val pendingTrans=pendingId?.let { target ->
            plan.spec.transitions.firstOrNull { driver.current in it.sources && target in it.destinations }
        }
        val upcomingId=if (pendingId == null && driver.canAdvance) plan.next[driver.current] else null
        val upcomingTrans=upcomingId?.let { target ->
            plan.spec.transitions.firstOrNull { driver.current in it.sources && target in it.destinations }
        }
        val pending=pendingId?.let { " → ${plan.labels[it]} (queued)" }.orEmpty()
        val updated = _ui.value.copy(detail=if(fromAction) error else driver.diagnostic ?: _ui.value.detail,flow=current.copy(
            activeSegment=driver.current,performing=!driver.finished,
            performanceStage=if(driver.finished) "Finished" else label+pending,
            stageKey=if(driver.finished) "Finished" else label,
            pendingStageKey=if(driver.finished) null else pendingId?.let { plan.labels[it] },
            pendingSyncPoint=if(driver.finished) null else pendingTrans?.sync,
            pendingSourceCue=if(driver.finished) null else pendingTrans?.sourceCue,
            nextAdvanceTargetKey=if(driver.finished) null else upcomingId?.let { plan.labels[it] },
            nextAdvanceSyncPoint=if(driver.finished) null else upcomingTrans?.sync,
            canAdvance=driver.canAdvance,canEnd=driver.canEnd,fastPlaying=driver.isFast,canFast=driver.canFast,
            selectedVersion=driver.currentVersion,availableVersions=driver.availableVersions,
            selectedRegion=driver.currentRegion,availableRegions=driver.availableRegions,canCancelPending=driver.canCancelPending,
            sourceFrame=observation?.sourceFrame,sourceFrames=segment?.exit,loopStart=segment?.loop?.start,loopEnd=segment?.loop?.end,
            voiceCount=observation?.voices ?: 0,transportRate=plan.spec.transportSampleRate,fading=driver.isFading,
            fadeRemainingFrames=observation?.fadeEndFrame?.let { end -> observation.transportFrame?.let { (end-it).coerceAtLeast(0) } }))
        if (updated != _ui.value) _ui.value = updated
    }
    private fun publish(state: PlaybackSession.State) {
        creditListening(state)
        val progress = if (state.totalFrames > 0) {
            (state.positionFrames.toDouble() / state.totalFrames).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        // Timing only exists while this screen holds the output handle; the service does
        // not expose it, so an absent packet stays NO DATA rather than being invented.
        val timing = TimingDiagnosis.read(session.timingSnapshot())

        val updated = _ui.value.copy(
            status = when {
                state.detail != null -> "Failed"
                state.playing -> "Playing"
                state.paused -> "Paused"
                state.title.isEmpty() -> "No track loaded"
                else -> "Stopped"
            },
            position = EngineSnapshot.formatPosition(state.positionFrames, state.sampleRate),
            total = EngineSnapshot.formatPosition(state.totalFrames, state.sampleRate),
            progress = progress,
            canPause = state.playing,
            canResume = state.paused,
            canStop = state.active,
            timing = timing?.explanation ?: "Timing unavailable",
            timingVerdict = timing?.verdict ?: TimingDiagnosis.Verdict.NO_DATA,
            outputActive = session.hasOutput,
            detail = state.detail ?: _ui.value.detail,
            totalFrames = state.totalFrames.takeIf { it > 0L } ?: totalFrames,
            sampleRate = state.sampleRate.takeIf { it > 0 } ?: rate,
        )
            val elapsed = android.os.SystemClock.elapsedRealtime()
            if (_ui.value.linearMode && state.playing && elapsed - lastPersistedLinearProgressMs >= 2_000L) {
                lastPersistedLinearProgressMs = elapsed
                linearCoordinator.saveCurrentProgress(state.positionFrames, _ui.value.totalFrames, _ui.value.sampleRate)
            }
            if (updated != _ui.value) _ui.value = updated
    }

    fun pause() {
        val st = session.pause()
        publish(st)
        if (_ui.value.linearMode) {
            linearCoordinator.saveCurrentProgress(st.positionFrames, _ui.value.totalFrames, _ui.value.sampleRate)
        }
    }

    fun resume() {
        publish(session.resume())
    }

    /** Seeks by fraction of the track. The UI only knows a 0..1 drag value. */
    fun seekTo(fraction: Float) {
        val frame = SeekTarget.fractionToFrame(fraction, totalFrames)
        if (!SeekTarget.isAddressable(frame, totalFrames)) return
        if (_ui.value.linearMode) {
            linearCoordinator.seekToFrame(frame)
            publish(session.state.value)
        } else {
            publish(session.seek(frame))
        }
    }

    private fun markPerformanceFinished() {
        _ui.value = _ui.value.copy(flow = _ui.value.flow?.copy(
            performing = false, performanceStage = "Finished", stageKey = "Finished", pendingStageKey = null))
    }

    fun stop() = stopTransport(cancelPendingOpen = true)

    /** Natural completion stops only this session; it must not cancel a newer user selection. */
    private fun stopTransport(cancelPendingOpen: Boolean) {
        if (cancelPendingOpen) packPlaybackRequest++
        nativePerformance=null
        performance?.stop()
        performance=null
        if (cancelPendingOpen) {
            linearCoordinator.clearSession()
            linearCoordinator.clearSavedSession()
        }
        publish(session.stop())
        _ui.value = _ui.value.copy(progress = 0f, position = EngineSnapshot.formatPosition(0, rate),
            linearMode = false, linearTrack = null, canSeek = false,
            continueListening = if (cancelPendingOpen) null else _ui.value.continueListening,
            flow=_ui.value.flow?.copy(performing=false,performanceStage="Stopped",stageKey="Stopped",pendingStageKey=null,canAdvance=false,canEnd=false,fastPlaying=false,
                canFast=false,canCancelPending=false,fading=false,fadeRemainingFrames=null,voiceCount=0,sourceFrame=null,availableRegions=emptySet(),availableVersions=emptySet(),selectedRegion=null,selectedVersion=null))
        PlaybackService.stop(getApplication())
    }

    private fun releaseTransport() {
        pollJob?.cancel()
        pollJob = null
        linearCoordinator.clearSession()
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        nativePerformance = null
        interactivePlan = null
        _ui.value = _ui.value.copy(flow = null, linearMode = false, linearTrack = null, canSeek = false)
        session.release()
        PlaybackService.stop(getApplication())
    }

    override fun onCleared() {
        // Playback deliberately continues: the foreground service keeps the session alive
        // and the notification keeps offering controls. Only the screen stops observing.
        pollJob?.cancel()
        pollJob = null
        super.onCleared()
    }

    companion object {
        /** 200 ms keeps the progress bar smooth without touching the audio thread. */
        const val POLL_INTERVAL_MS = 200L

        /** Bundled compatibility manifest; see tools/gen_catalog.py. */
        const val CATALOG_ASSET = "catalog.json"

        /** Bundled chronological OST-to-BWAV Jukebox catalog; see tools/gen_jukebox_catalog.py. */
        const val JUKEBOX_CATALOG_ASSET = "jukebox_catalog.json"

        /** Folder inside app storage that [openFlowFromFolder] reads. */
        const val FLOW_FOLDER = "stages"

        /** Bundled map anchors. Named here so the missing case can be stated once. */
        const val MAP_ANCHORS_ASSET = "map_anchors.json"

        /** Where installed packs live, under app storage. */
        const val PACK_ROOT = "packs"

        /** Where Special Cup progress lives, beside the library rather than inside it. */
        const val SPECIAL_CUP_FILE = "special-cup.json"
    }
}
