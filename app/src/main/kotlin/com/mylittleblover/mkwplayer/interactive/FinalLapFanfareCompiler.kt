package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.security.MessageDigest

/** Ordinary courses only. Rainbow has its own independently verified fanfare graph. */
internal object FinalLapFanfareCompiler {
    const val ASSET = "BGM_Seq_FinalLapFF.bwav"
    // Median measured from three gameplay recordings, not a MINF event. See research note.
    private const val FAST_START = 188_880L
    private const val SHA256 = "60790cdf84a2e04c9fffca7ccee8bafa5be2bfb6ceb6dfd4f98ecfc8bdd8ce94"
    @Volatile
    private var verifiedAssetBytes: ByteArray? = null
    @Volatile
    private var verifiedDigest: ByteArray? = null

    fun extend(base: InteractivePlaybackPlan, bytes: Map<String,ByteArray>): InteractivePlaybackPlan {
        val fastTargets=(listOfNotNull(base.fast)+base.versions.mapNotNull { it.fast }).toSet()
        val rules=base.spec.transitions.filter { rule ->
            rule.entry!=G.EntryMode.SameTime && rule.sources.none { it in fastTargets } &&
                rule.destinations.singleOrNull() in fastTargets
        }
        if(rules.isEmpty()) return base
        // Offline research graphs may omit shared assets. Android always supplies the bundled source.
        val data=bytes[ASSET] ?: return base
        require(base.name!="Rainbow") { "Rainbow must keep its dedicated FinalLapFanfare" }
        val digest = if (data === verifiedAssetBytes && verifiedDigest != null) {
            verifiedDigest!!
        } else {
            val computed = MessageDigest.getInstance("SHA-256").digest(data)
            require(computed.joinToString("") { "%02x".format(it.toInt() and 255) } == SHA256) { "Unexpected FinalLapFF source" }
            verifiedDigest = computed
            verifiedAssetBytes = data
            computed
        }
        val info=(BwavHeader.read(data) as? BwavHeader.Result.Ok)?.info ?: error("Unreadable FinalLapFF")
        require(info.sampleRate==48000 && info.channels==2 && info.frames.toLong()==268759L)
        require(base.spec.transportSampleRate==48000)
        var id=(base.spec.segments.map { it.index }+base.spec.tracks.map { it.index }+
            base.spec.transitions.map { it.index }+base.spec.segments.flatMap { it.cues.map { c -> c.index } }+
            base.spec.tracks.flatMap { it.clips.map { c -> c.index } }).max()+1
        val ff=id++
        val spec=base.spec.copy(
            assets=base.spec.assets+G.Asset(ASSET,48000,2,268759,digest),
            segments=base.spec.segments+G.Segment(ff,exit=FAST_START),
            tracks=base.spec.tracks+G.Track(id++,ff,listOf(G.Clip(id++,base.spec.assets.size,sourceLength=268759))),
            transitions=base.spec.transitions.map { rule ->
                if(rule !in rules) rule else rule.copy(sync=G.SyncPoint.Immediate,sourceCue=null,
                    bridge=ff,fadeOut=G.Fade(),fadeIn=G.Fade(),postExit=true,preEntry=false,
                    timeline=G.TransitionTimeline(0,FAST_START,FAST_START,0))
            },
        )
        // With an Immediate rule and zero outgoing fade, Native cuts the Full voice at sourceSync.
        // postExit retains the bridge's physical tail after Fast starts, not the looping Full.
        val packet=G.build(spec)
        val paths=base.assetPaths+ASSET
        val bridgeSources = rules.flatMapTo(mutableSetOf()) { it.sources }
        val updatedEntryPrepared = base.entryPreparedAssets.mapValues { (segment, assets) ->
            if (segment in bridgeSources) assets + ASSET else assets
        }
        return base.copy(spec=spec,packet=packet,assetPaths=paths,labels=base.labels+(ff to "FinalLapFF"),
            preparedAssets=base.preparedAssets+ASSET,
            entryPreparedAssets=updatedEntryPrepared,
            standard=base.standard?.copy(packet=packet,assetPaths=paths))
    }
}
