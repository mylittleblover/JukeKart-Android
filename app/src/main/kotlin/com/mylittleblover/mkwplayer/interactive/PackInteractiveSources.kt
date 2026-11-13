package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.pack.PackPlayback
import java.io.File
import java.security.MessageDigest

/** Restores known interactive source groups without changing pack v1's role vocabulary. */
internal object PackInteractiveSources {
    sealed interface Resolved {
        data class Normal(val flow: StageFlow,val bytes: Map<String,ByteArray>,val title: String): Resolved
        data class Rainbow(val bytes: Map<String,ByteArray>,val title: String): Resolved
    }
    fun resolve(manifest: PackManifest,track: PackManifest.Track,directory: File,
                playable: PackPlayback.Playable,identities: InteractiveAudioIdentities): Resolved? {
        val owner=identities.identityForDigest(track.primary.sha256) ?: return null
        if(owner.stage!="Rainbow" && owner.role!="Main") return null
        fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(playable.bytes.values.map(::sha).toSet()==track.parts.map { it.asset.sha256 }.toSet()) {
            "Installed track bytes no longer match the imported pack"
        }
        val group=manifest.stages.filter { track.key in it.tracks }.singleOrNull()
        val declared=group?.tracks.orEmpty().flatMap { key ->
            manifest.track(key)?.parts.orEmpty().map { it.asset.sha256 }
        }.distinct()
        fun sources(role: String)=declared.filter { digest ->
            identities.identityForDigest(digest)?.let { it.stage==owner.stage && it.role==role }==true
        }
        fun load(digest: String): Pair<String,ByteArray> {
            val asset=manifest.assets.single { it.digest.sha256==digest }
            val data=File(directory,"audio/$digest").readBytes()
            require(data.size.toLong()==asset.byteSize && sha(data)==digest) { "Installed interactive source failed SHA-256: $digest" }
            return identities.identityForDigest(digest)!!.fileName to data
        }
        if(owner.stage=="Rainbow") {
            require(group!=null) { "Rainbow needs a declared stage group containing its complete source set" }
            val bytes=RainbowInteractiveCompiler.roles.associate { role ->
                val matches=declared.filter { digest -> identities.identityForDigest(digest)?.let {
                    it.stage=="Rainbow" && it.fileName=="BGM_Rainbow_$role.bwav"
                }==true }
                require(matches.size==1) { "Rainbow requires exactly one $role source in its stage group" }
                load(matches.single())
            }
            return Resolved.Rainbow(bytes,group.name.ifBlank { track.title })
        }
        val canonical=identities.resolve(playable.flow,playable.bytes)
            ?: error("Known stage source could not restore its interactive identity")
        val familyStages=when(owner.stage) {
            "Cinema","SnowMountain" -> setOf("${owner.stage}_Pattern2")
            "DonkeyKong" -> (2..5).map { "DonkeyKong_Pattern$it" }.toSet()
            else -> emptySet()
        }
        val alternateBytes=if(familyStages.isNotEmpty()) declared.filter { digest ->
            identities.identityForDigest(digest)?.let { it.stage in familyStages && it.role in setOf("Main","Fast") }==true
        }.associate { load(it) } else emptyMap()
        val canonicalBytes=canonical.bytes+alternateBytes
        val fast=sources("Fast")
        require(fast.size<=1) { "Multiple Fast sources declared for ${owner.stage}" }
        if(fast.isEmpty() || canonical.flow.part(StageFlow.Role.Fast)!=null)
            return Resolved.Normal(canonical.flow,canonicalBytes,canonical.title)
        val (name,data)=load(fast.single())
        val header=(BwavHeader.read(data) as? BwavHeader.Result.Ok)?.info
            ?: error("Unreadable Fast source for ${owner.stage}")
        val part=StageFlow.Part(StageFlow.Role.Fast,name,header.frames.toLong(),header.sampleRate,header.channels,
            header.loopStart?.toLong(),header.loopEnd?.toLong())
        return Resolved.Normal(canonical.flow.copy(parts=canonical.flow.parts+part),canonicalBytes+(name to data),canonical.title)
    }
}
