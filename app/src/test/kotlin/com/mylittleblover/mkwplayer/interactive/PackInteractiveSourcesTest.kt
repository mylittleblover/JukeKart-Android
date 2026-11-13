package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.pack.PackManifest as P
import com.mylittleblover.mkwplayer.pack.PackPlayback
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.*

class PackInteractiveSourcesTest {
    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun audio(seed: Int): ByteArray {
        val b=ByteArray(100);"BWAV".toByteArray().copyInto(b)
        b[4]=0xff.toByte();b[5]=0xfe.toByte();b[6]=1;b[14]=1;b[16]=1
        fun put(at: Int,n: Int) { repeat(4) { b[at+it]=(n ushr (8*it)).toByte() } }
        put(20,48000);put(24,4);put(28,4);put(76,-1);b[99]=seed.toByte();return b
    }
    private data class Fixture(val root: File,val manifest: P,val identities: InteractiveAudioIdentities) {
        fun track(stage: String)=manifest.tracks.first { it.title==stage }
        fun playable(stage: String)=(PackPlayback.prepare(manifest,track(stage),root) as PackPlayback.Result.Ok).playable
    }
    private fun fixture(stage: String,roles: List<String>,extra: List<Pair<String,String>> = emptyList()): Fixture {
        val root=Files.createTempDirectory("interactive-pack").toFile()
        val records=(roles.map { stage to it }+extra).mapIndexed { i,(s,r) ->
            val data=audio(i+1);val digest=sha(data);File(root,"audio/$digest").apply { parentFile.mkdirs();writeBytes(data) }
            Triple(s,r,digest)
        }
        val tracks=records.map { (s,r,d) -> P.Track("course:${d.take(32)}",ResourceKind.COURSE,
            if(r=="Main" || r=="Area1_Inst") s else "$s $r",AudioAssetId(d),false,listOf(P.Part(AudioAssetId(d),P.Role.MAIN))) }
        val assets=records.map { (_,_,d) -> P.Asset(AudioAssetId(d),100,"audio/x-bwav",1,48000,1,4) }
        val manifest=P(1,"test.pack",1,"test",P.Kind.FULL,null,null,P.DeclaredCoverage(tracks.size,tracks.size,listOf(1)),
            assets,tracks,listOf(P.Stage(stage.removeSuffix("_Pattern2"),"显示名称",tracks.map { it.key })),emptyList(),P.Unlock(false,listOf(stage)))
        val entries=JSONArray(records.map { (s,r,d) -> JSONObject().put("sha256",d).put("stage",s).put("role",
            if(s!="Rainbow") r else when { r.endsWith("_Pre")->"Pre";r.endsWith("_Bridge")->"Bridge";"_Outro" in r->"Outro";else->"Main" })
            .put("fileName",if(s=="Rainbow") "BGM_Rainbow_$r.bwav" else "BGM_Stage_$s${if(r=="Main") "" else "_$r"}.bwav") })
        return Fixture(root,manifest,InteractiveAudioIdentities.parse(JSONObject().put("version",1).put("entries",entries).toString()))
    }
    @Test fun sameStageGroupAddsTheRealFastEvenThoughV1DeclaresItAsASeparateTrack() {
        val f=fixture("FirstCircuit",listOf("Main","Fast"))
        try {
            val r=PackInteractiveSources.resolve(f.manifest,f.track("FirstCircuit"),f.root,f.playable("FirstCircuit"),f.identities)
            assertIs<PackInteractiveSources.Resolved.Normal>(r)
            assertNotNull(r.flow.part(StageFlow.Role.Fast));assertEquals("FirstCircuit",r.title)
        } finally { f.root.deleteRecursively() }
    }
    @Test fun pattern2DoesNotBorrowTheBaseArrangementFast() {
        val f=fixture("Cinema_Pattern2",listOf("Main","Fast"),listOf("Cinema" to "Fast"))
        try {
            val r=PackInteractiveSources.resolve(f.manifest,f.track("Cinema_Pattern2"),f.root,f.playable("Cinema_Pattern2"),f.identities) as PackInteractiveSources.Resolved.Normal
            assertEquals("BGM_Stage_Cinema_Pattern2_Fast.bwav",r.flow.part(StageFlow.Role.Fast)!!.fileName)
        } finally { f.root.deleteRecursively() }
    }
    @Test fun baseVersionLoadsOnlyItsDeclaredAlternateSourcesWithTheirOwnHashes() {
        val f=fixture("Cinema",listOf("Main","Fast"),listOf("Cinema_Pattern2" to "Main","Cinema_Pattern2" to "Fast"))
        try {
            val r=PackInteractiveSources.resolve(f.manifest,f.track("Cinema"),f.root,f.playable("Cinema"),f.identities) as PackInteractiveSources.Resolved.Normal
            assertEquals(4,r.bytes.size,"Version controls need both real Full/Fast alternate bytes")
            val digest=f.manifest.tracks.last().primary.sha256
            File(f.root,"audio/$digest").appendBytes(byteArrayOf(8))
            assertFailsWith<IllegalArgumentException> {
                PackInteractiveSources.resolve(f.manifest,f.track("Cinema"),f.root,f.playable("Cinema"),f.identities)
            }
        } finally { f.root.deleteRecursively() }
    }
    @Test fun donkeyGroupLoadsDeclaredRegionSourcesWithoutChangingBasePartRoles() {
        val f=fixture("DonkeyKong",listOf("Main","Fast"),(2..5).map { "DonkeyKong_Pattern$it" to "Main" })
        try {
            val r=PackInteractiveSources.resolve(f.manifest,f.track("DonkeyKong"),f.root,f.playable("DonkeyKong"),f.identities) as PackInteractiveSources.Resolved.Normal
            assertEquals(6,r.bytes.size)
            assertEquals(setOf(StageFlow.Role.Main,StageFlow.Role.Fast),r.flow.parts.map { it.role }.toSet())
        } finally { f.root.deleteRecursively() }
    }
    @Test fun rainbowUsesAllFifteenDeclaredGroupAssetsInsteadOfTheSinglePrimary() {
        val f=fixture("Rainbow",RainbowInteractiveCompiler.roles)
        try {
            val r=PackInteractiveSources.resolve(f.manifest,f.track("Rainbow"),f.root,f.playable("Rainbow"),f.identities)
            assertIs<PackInteractiveSources.Resolved.Rainbow>(r);assertEquals(15,r.bytes.size)
        } finally { f.root.deleteRecursively() }
    }
    @Test fun anIncompleteRainbowGroupFailsBeforeOpeningOutput() {
        val f=fixture("Rainbow",listOf("Area1_Inst"))
        try { assertFailsWith<IllegalArgumentException> {
            PackInteractiveSources.resolve(f.manifest,f.track("Rainbow"),f.root,f.playable("Rainbow"),f.identities)
        } } finally { f.root.deleteRecursively() }
    }
    @Test fun changedSupplementaryBytesAreNotAcceptedAsTheKnownFastIdentity() {
        val f=fixture("FirstCircuit",listOf("Main","Fast"))
        try {
            val fast=f.manifest.tracks.last().primary.sha256
            File(f.root,"audio/$fast").appendBytes(byteArrayOf(1))
            assertFailsWith<IllegalArgumentException> {
                PackInteractiveSources.resolve(f.manifest,f.track("FirstCircuit"),f.root,f.playable("FirstCircuit"),f.identities)
            }
        } finally { f.root.deleteRecursively() }
    }
}
