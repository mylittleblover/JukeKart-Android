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
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.NativeInteractiveDriver
import com.mylittleblover.mkwplayer.interactive.InteractiveAudioIdentities
import com.mylittleblover.mkwplayer.interactive.PackInteractiveSources
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
import com.mylittleblover.mkwplayer.library.PackPicker
import com.mylittleblover.mkwplayer.pack.PackCoverage
import com.mylittleblover.mkwplayer.pack.PackInstaller
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.pack.PackPlayback
import com.mylittleblover.mkwplayer.pack.PackRegistry
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

    /** The installed pack, or null when the listener is using loose files. */
    private var pack: InstalledPack? = null

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

    /** The jukebox, arranged by chronological source game and official OST track order. */
    val jukebox: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog
        get() = _ui.value.jukebox

    val extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.load(getApplication())
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
        linearCoordinator.attach(getApplication(), loaded)
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
        linearCoordinator.startQueue(entries, startIndex, shuffleOverride = shuffle)
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
        linearCoordinator.startQueue(entries, 0, shuffleOverride = shuffle)
    }

    /** Inserts [item] at the front (LIFO) of the temporary "Play next" queue (#63, #64). */
    fun playJukeboxNext(
        item: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem,
        game: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game,
        languageTag: String,
    ) {
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

        // Restore the installed source without opening audio. Browsing after a process restart
        // must offer the same courses as immediately after import.
        val packRoot = java.io.File(getApplication<Application>().filesDir, PACK_ROOT)
        val restored = PackRegistry(packRoot).load()
            .sortedWith(compareByDescending<PackRegistry.Entry> { it.packVersion }.thenBy { it.packId })
            .firstNotNullOfOrNull { entry ->
                runCatching {
                    val expected = java.io.File(java.io.File(java.io.File(packRoot, "packs"), entry.packId), entry.packVersion.toString()).canonicalFile
                    val directory = java.io.File(entry.directory).canonicalFile
                    if (directory != expected || !directory.toPath().startsWith(packRoot.canonicalFile.toPath())) return@runCatching null
                    val manifest = PackManifest.parse(java.io.File(directory, PackManifest.MEMBER_NAME).readText()).manifestOrNull()
                        ?: return@runCatching null
                    if (manifest.packId != entry.packId || manifest.packVersion != entry.packVersion ||
                        com.mylittleblover.mkwplayer.pack.PackValidation.validate(manifest).failures.isNotEmpty()) return@runCatching null
                    if (!manifest.assets.all { asset ->
                        val file = java.io.File(directory, "audio/${asset.digest.sha256}")
                        file.isFile && file.length() == asset.byteSize
                    }) return@runCatching null
                    InstalledPack(manifest, directory)
                }.getOrNull()
            }
        if (restored != null) {
            pack = restored
            _ui.value = _ui.value.copy(pack = PackUi(
                name = restored.manifest.name,
                trackCount = restored.manifest.tracks.size,
                playableCount = PackPlayback.playableTracks(restored.manifest).size,
            ))
        }

        // The cup's eligibility depends on what the user actually has, so it is derived after the
        // library is restored. Reading the stored progress first means the offer survives a restart.
        establishCup(loaded.tracks)
        refreshJukeboxCatalog()

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
        packPlaybackRequest++
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

            if(bytes.keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(bytes,rejected)
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
            startFlow(assembled.getOrThrow(), bytes, rejected)
        }
    }

    private fun startFlow(flow: StageFlow, sourceBytes: Map<String, ByteArray>, rejected: List<String>,displayTitle: String=flow.name, startingStage: String?=null): Boolean {
        if(nativePerformance?.isFading==true) return false
        packPlaybackRequest++
        val bytes = try {
            val withFf = sourceBytes + (com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET to
                getApplication<Application>().assets.open("audio/BGM_Seq_FinalLapFF.bwav").use { it.readBytes() })
            val ks = extraCatalog.koopaSpeaker
            val dir = pack?.directory
            if (ks != null && dir != null && flow.name == "BGM_Stage_${ks.stageCode}") {
                val normalFile = java.io.File(dir, "audio/${ks.normalAsset.sha256}")
                val fastFile = java.io.File(dir, "audio/${ks.fastAsset.sha256}")
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
        } catch(e: Exception) {
            _ui.value=_ui.value.copy(status="Cannot load FinalLapFF",detail=e.message)
            return false
        }
        val plan = try {
            if(minfCatalog.part(flow.name.removePrefix("BGM_Stage_"),"Full")!=null) {
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
        } catch(e: Exception) {
            _ui.value=_ui.value.copy(status="Cannot prepare interactive playback",detail=e.message)
            return false
        }
        val compiled = try {
            plan?.standard ?: StageFlowCompiler.compileFromBytes(flow, bytes)
        } catch (e: IllegalArgumentException) {
            _ui.value = _ui.value.copy(
                status = "Cannot prepare this flow",
                detail = e.message,
            )
            return false
        }

        val entry = plan?.entry ?: FlowPresentation.entrySection(compiled)
        val packetBytes = plan?.packet ?: compiled.packet
        val assetPaths = plan?.assetPaths ?: compiled.assetPaths
        val state = session.loadFlow(
            title = displayTitle,
            packet = packetBytes,
            assets = assetPaths.map { path -> path to bytes[path]!! },
            frames = FlowPresentation.totalFrames(flow),
            startSegment = entry,
            autoAdvance = plan?.automaticNext ?: emptyMap(),
            streamed = plan?.streamed ?: false,
            preparedAssets = plan?.let { it.preparedAssets + it.entryPreparedAssets[it.entry].orEmpty() } ?: emptySet(),
            prequeueInitial = plan?.let { it.entry in it.entryAutomaticNext } ?: false,
            initialLayerGains = currentLayerGains(plan),
        )
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = "Cannot play this flow", detail = state.detail)
            return false
        }

        linearCoordinator.yieldToExternalTransport()
        this.flow = flow
        this.interactivePlan = plan
        this.nativePerformance = null
        this.performance = null
        this.flowCompiled = compiled
        this.flowBytes = bytes
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

    private fun startRainbow(bytes: Map<String,ByteArray>,notes: List<String>,displayTitle: String="Rainbow", startingStage: String?=null): Boolean {
        if(isSpecialStageLocked("Rainbow")) return false
        if(nativePerformance?.isFading==true) return false
        packPlaybackRequest++
        val plan=try { InteractivePlaybackPlan.rainbow(bytes,minfCatalog).startingAt(startingStage) } catch(e: Exception) {
            _ui.value=_ui.value.copy(status="Cannot prepare Rainbow",detail=e.message);return false
        }
        val frames=plan.spec.segments.sumOf { it.exit }
        val state=session.loadFlow(displayTitle,plan.packet,plan.assetPaths.map { it to bytes.getValue(it) },frames,plan.entry,streamed=true,autoAdvance=plan.automaticNext,
            preparedAssets=plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
            prequeueInitial=plan.entry in plan.entryAutomaticNext)
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
        packPlaybackRequest++
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

            if(scan.bytesByName().keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(scan.bytesByName(),scan.rejected)
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
            startFlow(assembled.getOrThrow(), scan.bytesByName(), scan.rejected)
        }
    }

    /**
     * Imports a soundtrack pack.
     *
     * This is the only import path a normal listener needs: one file carries the audio, the stage
     * structures, the timings and the cue data, so there is nothing to pick one at a time.
     *
     * The import runs off the main thread because it hashes every asset, and the screen is told what it is
     * doing rather than appearing frozen. A refusal is shown verbatim: the importer names the member or rule
     * that failed, and "could not import" tells a user nothing they can act on.
     */
    fun importPack(uri: Uri) {
        if(nativePerformance?.isFading==true) return
        _ui.value = _ui.value.copy(
            busy = true,
            status = "Copying the pack...",
            detail = null,
            pack = _ui.value.pack?.copy(importing = true, progress = "copying"),
        )
        viewModelScope.launch {
            val context = getApplication<Application>()
            val copied = withContext(Dispatchers.IO) { PackPicker.copy(context, uri) }
            val picked = when (copied) {
                is PackPicker.Result.Rejected -> {
                    _ui.value = _ui.value.copy(
                        busy = false,
                        status = "Could not read the pack",
                        detail = copied.reason,
                        pack = _ui.value.pack?.copy(importing = false, progress = ""),
                    )
                    return@launch
                }
                is PackPicker.Result.Ok -> copied
            }

            _ui.value = _ui.value.copy(status = "Installing ${picked.displayName}...")

            val result = withContext(Dispatchers.IO) {
                try {
                    PackInstaller.install(
                        archive = picked.file,
                        libraryRoot = java.io.File(context.filesDir, PACK_ROOT),
                        progress = object : PackInstaller.Progress {
                            override fun onMember(index: Int, total: Int, name: String) = Unit
                        },
                    )
                } finally {
                    picked.file.delete()
                }
            }

            when (result) {
                is PackInstaller.Result.Refused -> {
                    _ui.value = _ui.value.copy(
                        busy = false,
                        status = "This pack was not installed",
                        detail = result.reason,
                        pack = _ui.value.pack?.copy(importing = false, progress = ""),
                    )
                }
                is PackInstaller.Result.Installed -> {
                    onPackInstalled(result.manifest, result.directory, result.coverage, picked.displayName)
                }
            }
        }
    }

    /** Records an installed pack and opens the first stage it can play. */
    private fun onPackInstalled(
        manifest: PackManifest,
        directory: java.io.File,
        coverage: PackCoverage.Coverage,
        displayName: String,
    ) {
        this.pack = InstalledPack(manifest, directory)
        establishCup()

        val playable = PackPlayback.playableTracks(manifest)
        val notes = buildList {
            if (coverage.unsupportedCodecs.isNotEmpty()) {
                add(
                    "${coverage.unsupportedCodecs.sumOf { it.affectedTracks }} track(s) use a codec this " +
                        "build cannot decode.",
                )
            }
        }

        _ui.value = _ui.value.copy(
            busy = false,
            status = if (playable.isEmpty()) "Pack installed, but nothing in it is playable" else "Pack installed",
            detail = notes.firstOrNull(),
            pack = PackUi(
                name = manifest.name.ifBlank { displayName },
                trackCount = manifest.tracks.size,
                playableCount = playable.size,
                notes = notes,
                importing = false,
                progress = "",
            ),
        )
        refreshJukeboxCatalog()

        // Open the first stage, so importing is immediately audible rather than leaving the listener to
        // find their way back to a list.
        val firstStage = manifest.stages.firstNotNullOfOrNull { stage ->
            manifest.tracksOf(stage).firstOrNull { track -> playable.any { it.key == track.key } }
        } ?: playable.firstOrNull()
        if (firstStage != null) {
            if (firstStage.kind == com.mylittleblover.mkwplayer.registry.ResourceKind.JUKEBOX) {
                openJukeboxTrack(firstStage.key)
            } else {
                openPackTrack(firstStage)
            }
        }
    }

    private var packPlaybackRequest=0L

    /** Newest selection waits without blocking the service's foreground promotion. */
    fun openPackTrack(track: PackManifest.Track,perform: Boolean=false,startingStage: String?=null) {
        if(nativePerformance?.isFading==true) return
        val request=++packPlaybackRequest
        viewModelScope.launch {
            PlaybackService.awaitForegroundPromotion()
            if(request!=packPlaybackRequest || nativePerformance?.isFading==true) return@launch
            if(openPackTrackNow(track,startingStage) && perform) startPerformance()
        }
    }

    private fun openPackTrackNow(track: PackManifest.Track,startingStage: String?=null): Boolean {
        if(nativePerformance?.isFading==true) return false
        val installed = pack ?: return false
        return when (val prepared = PackPlayback.prepare(installed.manifest, track, installed.directory)) {
            is PackPlayback.Result.Refused -> {
                _ui.value = _ui.value.copy(status = "Cannot open this track", detail = prepared.reason)
                false
            }
            is PackPlayback.Result.Ok -> {
                val playable = prepared.playable
                val resolved=try { PackInteractiveSources.resolve(installed.manifest,track,installed.directory,playable,interactiveIdentities) } catch(e: Exception) {
                    _ui.value=_ui.value.copy(status="Cannot identify interactive sources",detail=e.message);return false
                }
                when(resolved) {
                    is PackInteractiveSources.Resolved.Rainbow -> startRainbow(resolved.bytes,playable.notes,resolved.title,startingStage)
                    is PackInteractiveSources.Resolved.Normal -> startFlow(resolved.flow,resolved.bytes,playable.notes,resolved.title,startingStage)
                    null -> startFlow(playable.flow,playable.bytes,playable.notes,startingStage=startingStage)
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
                viewModelScope.launch {
                    PlaybackService.awaitForegroundPromotion()
                    if(request!=packPlaybackRequest || interactivePlan!==plan) return@launch
                    val state=session.loadFlow(_ui.value.flow?.title ?: plan.name,plan.packet,plan.assetPaths.map { it to flowBytes.getValue(it) },
                        totalFrames,plan.entry,streamed=plan.streamed,autoAdvance=plan.automaticNext,preparedAssets=plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
                        prequeueInitial=plan.entry in plan.entryAutomaticNext,
                        initialLayerGains=currentLayerGains(plan))
                    if(state.detail!=null) { _ui.value=_ui.value.copy(detail=state.detail);return@launch }
                    nativePerformance=null
                    startPerformance()
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
                session.setLayerGain(trackIdx, db)
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
                    val byStem = mapOf(track.main.stem to mainBytes, track.fast.stem to fastBytes)
                    val byPath = mapOf("${track.main.stem}.bwav" to mainBytes, "${track.fast.stem}.bwav" to fastBytes)
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
        )
        startPolling()
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
        val pending=driver.pending?.let { " → ${plan.labels[it]} (queued)" }.orEmpty()
        _ui.value=_ui.value.copy(detail=if(fromAction) error else driver.diagnostic ?: _ui.value.detail,flow=current.copy(
            activeSegment=driver.current,performing=!driver.finished,
            performanceStage=if(driver.finished) "Finished" else label+pending,
            stageKey=if(driver.finished) "Finished" else label,
            pendingStageKey=if(driver.finished) null else driver.pending?.let { plan.labels[it] },
            canAdvance=driver.canAdvance,canEnd=driver.canEnd,fastPlaying=driver.isFast,canFast=driver.canFast,
            selectedVersion=driver.currentVersion,availableVersions=driver.availableVersions,
            selectedRegion=driver.currentRegion,availableRegions=driver.availableRegions,canCancelPending=driver.canCancelPending,
            sourceFrame=observation?.sourceFrame,sourceFrames=segment?.exit,loopStart=segment?.loop?.start,loopEnd=segment?.loop?.end,
            voiceCount=observation?.voices ?: 0,transportRate=plan.spec.transportSampleRate,fading=driver.isFading,
            fadeRemainingFrames=observation?.fadeEndFrame?.let { end -> observation.transportFrame?.let { (end-it).coerceAtLeast(0) } }))
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

        _ui.value = _ui.value.copy(
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
    }

    fun pause() {
        publish(session.pause())
    }

    fun resume() {
        publish(session.resume())
    }

    /** Seeks by fraction of the track. The UI only knows a 0..1 drag value. */
    fun seekTo(fraction: Float) {
        val frame = SeekTarget.fractionToFrame(fraction, totalFrames)
        if (!SeekTarget.isAddressable(frame, totalFrames)) return
        publish(session.seek(frame))
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
        if (cancelPendingOpen) linearCoordinator.clearSession()
        publish(session.stop())
        _ui.value = _ui.value.copy(progress = 0f, position = EngineSnapshot.formatPosition(0, rate),
            linearMode = false, linearTrack = null, canSeek = false,
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
