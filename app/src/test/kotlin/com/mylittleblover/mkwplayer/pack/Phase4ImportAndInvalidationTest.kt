package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.LinearSessionStore
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.LogicalTrack
import com.mylittleblover.mkwplayer.registry.ResourceKind
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig
import com.mylittleblover.mkwplayer.routes.CustomRouteStop
import com.mylittleblover.mkwplayer.routes.CustomRouteStore
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM verification suite for Phase 4 decisions (#84-#97):
 * - #84, #85: Multi-pack additive internal library merge + delta overlay without overwriting sibling packs.
 * - #86: Road candidate pool higher-version replacement and pre-commit same-version conflict refusal.
 * - #87: Flagging invalidated custom routes without mutating/deleting saved route files.
 * - #88, #89: Stopping invalidated active playback and clearing invalidated Continue Listening.
 * - #91-#97: Streaming SAF SeekableByteChannel ZIP reader, pre-commit cancellation, post-commit lock,
 *   single-task exclusion, and crash checkpoint resume / wrong-file re-pick refusal.
 */
class Phase4ImportAndInvalidationTest {

    private data class ZipMemberSpec(
        val name: String,
        val bytes: ByteArray,
        val stored: Boolean = false,
    )

    private fun tempDir(prefix: String = "mkw-phase4-"): File =
        Files.createTempDirectory(prefix).toFile()

    private fun bwavPayload(seed: Int, size: Int = 8192): ByteArray {
        val bytes = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }
        // Distinct recognizable header prefix; validator checks length & SHA-256 while audio is mocked here.
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        return bytes
    }

    private fun writeZip(target: File, members: List<ZipMemberSpec>): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (member in members) {
                val entry = ZipEntry(member.name)
                if (member.stored) {
                    val crc = CRC32().apply { update(member.bytes) }.value
                    entry.method = ZipEntry.STORED
                    entry.size = member.bytes.size.toLong()
                    entry.compressedSize = member.bytes.size.toLong()
                    entry.crc = crc
                } else {
                    entry.method = ZipEntry.DEFLATED
                }
                zip.putNextEntry(entry)
                if (member.bytes.isNotEmpty()) zip.write(member.bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    private data class TrackSpec(
        val kind: String,
        val title: String,
        val bytes: ByteArray,
    ) {
        val digest: String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val key: String = LogicalTrack.idOf(ResourceKind.valueOf(kind), AudioAssetId(digest))
    }

    private data class StageSpec(
        val code: String,
        val name: String,
        val trackKeys: List<String>,
    )

    private fun manifestJson(
        packId: String,
        packVersion: Int,
        packName: String,
        kind: String = "FULL",
        basePackId: String? = null,
        basePackVersion: Int? = null,
        tracks: List<TrackSpec>,
        stages: List<StageSpec>,
    ): String {
        val distinctAssets = tracks.associateBy { it.digest }.values.toList()
        val assetsJson = distinctAssets.joinToString(",") { spec ->
            """
            {
              "digest": "${spec.digest}",
              "byteSize": ${spec.bytes.size},
              "mediaType": "audio/x-bwav",
              "codecKind": 1,
              "sampleRate": 48000,
              "channels": 2,
              "frames": 480000
            }
            """.trimIndent()
        }
        val tracksJson = tracks.joinToString(",") { t ->
            """
            {
              "key": "${t.key}",
              "kind": "${t.kind}",
              "title": "${t.title}",
              "primary": "${t.digest}",
              "isFlow": false,
              "parts": [
                { "asset": "${t.digest}", "role": "MAIN" }
              ]
            }
            """.trimIndent()
        }
        val stagesJson = stages.joinToString(",") { s ->
            val keys = s.trackKeys.joinToString(",") { "\"$it\"" }
            """{ "code": "${s.code}", "name": "${s.name}", "tracks": [$keys] }"""
        }
        val unlockStages = stages.joinToString(",") { "\"${it.code}\"" }
        val baseIdJson = basePackId?.let { "\"$it\"" } ?: "null"
        val baseVerJson = basePackVersion?.toString() ?: "null"
        return """
        {
          "formatVersion": 1,
          "packId": "$packId",
          "packVersion": $packVersion,
          "name": "$packName",
          "kind": "$kind",
          "basePackId": $baseIdJson,
          "basePackVersion": $baseVerJson,
          "coverage": {
            "totalLogicalTracks": ${tracks.size},
            "playableLogicalTracks": ${tracks.size},
            "codecKinds": [1]
          },
          "assets": [$assetsJson],
          "tracks": [$tracksJson],
          "stages": [$stagesJson],
          "provenance": [
            { "subject": "pack", "field": "assets", "level": "MEASURED_L2", "note": "phase4-test" }
          ],
          "unlock": {
            "frozen": true,
            "stages": [$unlockStages]
          }
        }
        """.trimIndent()
    }

    private fun buildPackZip(
        target: File,
        packId: String,
        packVersion: Int,
        packName: String,
        kind: String = "FULL",
        basePackId: String? = null,
        basePackVersion: Int? = null,
        tracks: List<TrackSpec>,
        stages: List<StageSpec>,
        extraMembers: List<ZipMemberSpec> = emptyList(),
        storeAudioUncompressed: Boolean = false,
    ): File {
        val json = manifestJson(
            packId = packId,
            packVersion = packVersion,
            packName = packName,
            kind = kind,
            basePackId = basePackId,
            basePackVersion = basePackVersion,
            tracks = tracks,
            stages = stages,
        )
        val members = buildList {
            add(ZipMemberSpec(PackManifest.MEMBER_NAME, json.toByteArray(StandardCharsets.UTF_8), stored = false))
            tracks.distinctBy { it.digest }.forEach { t ->
                add(ZipMemberSpec("audio/${t.digest}", t.bytes, stored = storeAudioUncompressed))
            }
            addAll(extraMembers)
        }
        return writeZip(target, members)
    }

    private fun jukeboxMetaJson(vararg tracks: TrackSpec): String {
        val entries = tracks.mapIndexed { idx, t ->
            """
            {
              "jukeboxOrder": ${idx + 1},
              "ostIndex": ${idx + 1},
              "gameId": "mkw",
              "gameOrder": ${idx + 1},
              "ostTitle": "${t.title}",
              "ostArrangement": null,
              "displayName": "${t.title}",
              "bwavStem": "stem_$idx",
              "jukeboxCode": "JB_$idx",
              "bwavGameId": "mkw",
              "packTrackKey": "${t.key}",
              "sha256": "${t.digest}",
              "frames": 480000,
              "sampleRate": 48000,
              "durationMs": 10000
            }
            """.trimIndent()
        }.joinToString(",")
        return """
        {
          "games": [
            {
              "id": "mkw",
              "order": 1,
              "year": 2025,
              "yearLabel": "2025",
              "ostSeries": "mkw",
              "names": { "en": "Mario Kart World" }
            }
          ],
          "tracks": [$entries]
        }
        """.trimIndent()
    }

    private fun connectionsCatalog(): StageConnections.Catalog =
        StageConnections.parse(
            """
            {
              "edges": [
                {
                  "from": "Coaster",
                  "to": "Skyline",
                  "kind": "InterStageEx1",
                  "rule": "standard",
                  "start": "rolling",
                  "traffic": "low",
                  "rowId": "row_1",
                  "displayName": "Coaster to Skyline"
                }
              ]
            }
            """.trimIndent(),
        )!!

    @Test
    fun seekableZipReaderAndManifestWriterRoundTrip() {
        val root = tempDir()
        try {
            val track = TrackSpec("COURSE", "Coaster", bwavPayload(11, 16_384))
            val zipFile = buildPackZip(
                target = File(root, "stored-and-deflated.mkwpack"),
                packId = "pack.test",
                packVersion = 1,
                packName = "Round Trip Pack",
                tracks = listOf(track),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(track.key))),
                extraMembers = listOf(ZipMemberSpec("artwork/cover.bin", byteArrayOf(1, 2, 3, 4), stored = true)),
                storeAudioUncompressed = true,
            )

            val fp1: String
            FileChannel.open(zipFile.toPath(), StandardOpenOption.READ).use { ch ->
                SeekableZipReader.open(ch).use { zip ->
                    fp1 = zip.archiveFingerprint
                    assertTrue(zip.memberNames().containsAll(listOf("manifest.json", "audio/${track.digest}", "artwork/cover.bin")))
                    val readBackAudio = zip.readBytes("audio/${track.digest}")
                    assertNotNull(readBackAudio)
                    assertTrue(track.bytes.contentEquals(readBackAudio))

                    val parsed = PackManifest.read(zip.readBytes("manifest.json"))
                    val ok = assertIs<PackManifest.Result.Ok>(parsed)
                    val reencoded = PackManifestWriter.toJson(ok.manifest)
                    val reparsed = assertIs<PackManifest.Result.Ok>(PackManifest.parse(reencoded))
                    assertEquals(ok.manifest, reparsed.manifest)
                    assertTrue(PackValidation.validate(reparsed.manifest).isInstallable)
                }
            }

            // Fingerprint is deterministic across opens on the exact same file.
            FileChannel.open(zipFile.toPath(), StandardOpenOption.READ).use { ch ->
                SeekableZipReader.open(ch).use { zip ->
                    assertEquals(fp1, zip.archiveFingerprint)
                }
            }

            // Corrupting a stored payload byte without fixing Central Directory CRC is caught cleanly.
            val bytes = zipFile.readBytes()
            val marker = "BWAV".toByteArray(StandardCharsets.US_ASCII)
            val idx = (0..(bytes.size - marker.size)).first { i ->
                bytes[i] == marker[0] && bytes[i + 1] == marker[1] && bytes[i + 2] == marker[2] && bytes[i + 3] == marker[3]
            }
            bytes[idx + 12] = (bytes[idx + 12].toInt() xor 0x55).toByte()
            val corrupted = File(root, "corrupted.mkwpack").apply { writeBytes(bytes) }

            FileChannel.open(corrupted.toPath(), StandardOpenOption.READ).use { ch ->
                SeekableZipReader.open(ch).use { zip ->
                    assertFailsWith<ZipException> {
                        zip.readBytes("audio/${track.digest}")
                    }
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun multiPackIncrementalMergeAndDeltaOverlay() {
        val root = tempDir()
        try {
            val libRoot = File(root, "packs")
            val appFiles = File(root, "files").apply { mkdirs() }

            val coasterTrack = TrackSpec("COURSE", "Coaster", bwavPayload(1))
            val jukeboxTrack = TrackSpec("JUKEBOX", "Road Tune", bwavPayload(2))
            val skylineTrack = TrackSpec("COURSE", "Skyline", bwavPayload(3))

            val coursesV1 = buildPackZip(
                target = File(root, "courses_v1.mkwpack"),
                packId = "pack.courses",
                packVersion = 1,
                packName = "Courses Core",
                tracks = listOf(coasterTrack),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(coasterTrack.key))),
                extraMembers = listOf(ZipMemberSpec("artwork/coaster.png", byteArrayOf(10, 20, 30))),
            )

            // Delta pack without installed base must be refused (#84, #85).
            val deltaWithoutBase = buildPackZip(
                target = File(root, "courses_delta_early.mkwpack"),
                packId = "pack.courses",
                packVersion = 2,
                packName = "Courses Delta",
                kind = "DELTA",
                basePackId = "pack.courses",
                basePackVersion = 1,
                tracks = listOf(skylineTrack),
                stages = listOf(StageSpec("Skyline", "Skyline", listOf(skylineTrack.key))),
            )
            FileChannel.open(deltaWithoutBase.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "delta-early", null)
                assertIs<StreamingPackInstaller.Result.Refused>(res)
            }

            // Install Pack A (courses).
            FileChannel.open(coursesV1.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "courses_v1.mkwpack", null)
                assertIs<StreamingPackInstaller.Result.Installed>(res)
            }

            // Install independent Pack B (jukebox). Must NOT overwrite Pack A.
            val jukeboxV1 = buildPackZip(
                target = File(root, "jukebox_v1.mkwpack"),
                packId = "pack.jukebox",
                packVersion = 1,
                packName = "Jukebox Addon",
                tracks = listOf(jukeboxTrack),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(jukeboxTrack.key))),
                extraMembers = listOf(ZipMemberSpec("artwork/jukebox.png", byteArrayOf(40, 50))),
            )
            FileChannel.open(jukeboxV1.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "jukebox_v1.mkwpack", null)
                assertIs<StreamingPackInstaller.Result.Installed>(res)
            }

            // Install Pack A v2 DELTA adding Skyline.
            val coursesV2Delta = buildPackZip(
                target = File(root, "courses_v2.mkwpack"),
                packId = "pack.courses",
                packVersion = 2,
                packName = "Courses Delta v2",
                kind = "DELTA",
                basePackId = "pack.courses",
                basePackVersion = 1,
                tracks = listOf(skylineTrack),
                stages = listOf(StageSpec("Skyline", "Skyline", listOf(skylineTrack.key))),
            )
            val installedDelta = FileChannel.open(coursesV2Delta.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "courses_v2.mkwpack", null)
                assertIs<StreamingPackInstaller.Result.Installed>(res)
            }

            val merged = installedDelta.mergedLibrary ?: InternalPackLibrary(libRoot).loadOrMaterialize()
            assertNotNull(merged)
            val keys = merged.manifest.tracks.map { it.key }.toSet()
            assertEquals(setOf(coasterTrack.key, jukeboxTrack.key, skylineTrack.key), keys)
            assertTrue(File(merged.directory, "audio/${coasterTrack.digest}").isFile)
            assertTrue(File(merged.directory, "audio/${jukeboxTrack.digest}").isFile)
            assertTrue(File(merged.directory, "audio/${skylineTrack.digest}").isFile)
            assertTrue(File(merged.directory, "artwork/coaster.png").isFile)
            assertTrue(File(merged.directory, "artwork/jukebox.png").isFile)

            // Re-loading from cold start hits cached contributors cleanly without rebuilding unnecessarily.
            val reloaded = InternalPackLibrary(libRoot).loadOrMaterialize()
            assertNotNull(reloaded)
            assertEquals(merged.activePackFingerprints, reloaded.activePackFingerprints)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun roadCandidatePoolReplacesWholesaleAndRefusesSameVersionConflictBeforeCommit() {
        val root = tempDir()
        try {
            val libRoot = File(root, "packs")
            val appFiles = File(root, "files").apply { mkdirs() }

            val roadA = TrackSpec("JUKEBOX", "Road A", bwavPayload(10))
            val roadB = TrackSpec("JUKEBOX", "Road B", bwavPayload(20))

            fun poolJson(version: Int, candidates: List<String>): ByteArray {
                val arr = candidates.joinToString(",") { "\"$it\"" }
                return """
                {
                  "schemaVersion": 1,
                  "dataVersion": $version,
                  "roads": [
                    { "from": "Coaster", "to": "Skyline", "candidates": [$arr] }
                  ]
                }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            }

            // v1 installs cleanly with road_a.
            val packV1 = buildPackZip(
                target = File(root, "pool_v1.mkwpack"),
                packId = "pack.pool",
                packVersion = 1,
                packName = "Pool v1",
                tracks = listOf(roadA),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(roadA.key))),
                extraMembers = listOf(ZipMemberSpec("routes/road_pool.json", poolJson(1, listOf(roadA.key)))),
            )
            FileChannel.open(packV1.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "pool_v1.mkwpack", null)
                val ok = assertIs<StreamingPackInstaller.Result.Installed>(res)
                assertEquals(PoolUpdateStatus.REPLACED, ok.poolStatus)
                assertEquals(1, ok.poolDataVersion)
            }

            // Conflicting pack using SAME dataVersion=1 but DIFFERENT candidates must be refused BEFORE commit (#86).
            val packConflict = buildPackZip(
                target = File(root, "pool_conflict.mkwpack"),
                packId = "pack.conflict",
                packVersion = 1,
                packName = "Pool Conflict",
                tracks = listOf(roadB),
                stages = listOf(StageSpec("Skyline", "Skyline", listOf(roadB.key))),
                extraMembers = listOf(ZipMemberSpec("routes/road_pool.json", poolJson(1, listOf(roadA.key, roadB.key)))),
            )
            FileChannel.open(packConflict.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "pool_conflict.mkwpack", null)
                assertIs<StreamingPackInstaller.Result.Refused>(res)
            }
            // Existing pool & registry remain untouched!
            assertTrue(PackRegistry(libRoot).load().none { it.packId == "pack.conflict" })
            val poolAfterConflict = RoadJukeboxCatalog.loadInternalPool(appFiles)
            assertEquals(1, poolAfterConflict.dataVersion)
            assertEquals(listOf(roadA.key), poolAfterConflict.candidatesFor("Coaster", "Skyline"))

            // Higher dataVersion=2 replaces wholesale (road_a dropped, only road_b present — NOT union).
            val packV2 = buildPackZip(
                target = File(root, "pool_v2.mkwpack"),
                packId = "pack.pool",
                packVersion = 2,
                packName = "Pool v2",
                tracks = listOf(roadB),
                stages = listOf(StageSpec("Skyline", "Skyline", listOf(roadB.key))),
                extraMembers = listOf(ZipMemberSpec("routes/road_pool.json", poolJson(2, listOf(roadB.key)))),
            )
            FileChannel.open(packV2.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(ch, libRoot, appFiles, "pool_v2.mkwpack", null)
                val ok = assertIs<StreamingPackInstaller.Result.Installed>(res)
                assertEquals(PoolUpdateStatus.REPLACED, ok.poolStatus)
                assertEquals(2, ok.poolDataVersion)
            }
            val poolV2 = RoadJukeboxCatalog.loadInternalPool(appFiles)
            assertEquals(2, poolV2.dataVersion)
            assertEquals(listOf(roadB.key), poolV2.candidatesFor("Coaster", "Skyline"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun singleTaskExclusionPreCommitCancelAndPostCommitLock() {
        val root = tempDir()
        PackImportCoordinator.dismissCompletedOrError()
        try {
            val libRoot = File(root, "packs")
            val appFiles = File(root, "files").apply { mkdirs() }
            val trackA = TrackSpec("COURSE", "Course A", bwavPayload(30, 131_072))
            val zipA = buildPackZip(
                target = File(root, "a.mkwpack"),
                packId = "pack.a",
                packVersion = 1,
                packName = "Pack A",
                tracks = listOf(trackA),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(trackA.key))),
            )

            // 1. Pre-commit cancel inside onProbed stops import, clears staging, leaves registry clean (#91, #93).
            val preCommitCancel = AtomicBoolean(false)
            FileChannel.open(zipA.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(
                    channel = ch,
                    libraryRoot = libRoot,
                    appFilesDir = appFiles,
                    displayName = "a.mkwpack",
                    sourceUri = null,
                    cancelFlag = preCommitCancel,
                    clearCheckpointOnCancel = true,
                    listener = object : StreamingPackInstaller.Listener {
                        override fun onProbed(manifest: PackManifest, totalMembers: Int, totalBytes: Long) {
                            preCommitCancel.set(true)
                        }
                    },
                )
                assertIs<StreamingPackInstaller.Result.Cancelled>(res)
            }
            assertTrue(PackRegistry(libRoot).load().isEmpty())
            assertFalse(File(libRoot, ".staging").exists())
            assertFalse(File(libRoot, ".import-checkpoint.json").exists())

            // 2. Single-task exclusion via PackImportCoordinator (#94).
            val enteredTask = CountDownLatch(1)
            val releaseTask = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<Pair<PackImportCoordinator.StartResult, StreamingPackInstaller.Result?>> {
                    PackImportCoordinator.executeSynchronouslyForTest(
                        libraryRoot = libRoot,
                        appFilesDir = appFiles,
                        displayName = "a.mkwpack",
                        sourceUri = "file://${zipA.absolutePath}",
                    ) {
                        enteredTask.countDown()
                        assertTrue(releaseTask.await(5, TimeUnit.SECONDS))
                        FileChannel.open(zipA.toPath(), StandardOpenOption.READ)
                    }
                }
                assertTrue(enteredTask.await(5, TimeUnit.SECONDS))

                // Concurrent import must be rejected immediately without queuing.
                val (secondStart, secondRes) = PackImportCoordinator.executeSynchronouslyForTest(
                    libraryRoot = libRoot,
                    appFilesDir = appFiles,
                    displayName = "second.mkwpack",
                    sourceUri = null,
                ) {
                    error("Must not open second channel while busy")
                }
                assertIs<PackImportCoordinator.StartResult.RejectedBusy>(secondStart)
                assertEquals(null, secondRes)

                releaseTask.countDown()
                val (firstStart, firstRes) = future.get(10, TimeUnit.SECONDS)
                assertIs<PackImportCoordinator.StartResult.Started>(firstStart)
                assertIs<StreamingPackInstaller.Result.Installed>(firstRes)
                assertEquals(PackImportPhase.COMPLETED, PackImportCoordinator.state.value.phase)
                assertNotNull(PackImportCoordinator.state.value.changelog)
            } finally {
                executor.shutdownNow()
            }

            // 3. Crossing commit boundary locks out cancellation (#91, #93).
            val trackB = TrackSpec("COURSE", "Course B", bwavPayload(31, 8192))
            val zipB = buildPackZip(
                target = File(root, "b.mkwpack"),
                packId = "pack.b",
                packVersion = 1,
                packName = "Pack B",
                tracks = listOf(trackB),
                stages = listOf(StageSpec("Skyline", "Skyline", listOf(trackB.key))),
            )
            val lateCancelFlag = AtomicBoolean(false)
            FileChannel.open(zipB.toPath(), StandardOpenOption.READ).use { ch ->
                val res = StreamingPackInstaller.installFromChannel(
                    channel = ch,
                    libraryRoot = libRoot,
                    appFilesDir = appFiles,
                    displayName = "b.mkwpack",
                    sourceUri = null,
                    cancelFlag = lateCancelFlag,
                    listener = object : StreamingPackInstaller.Listener {
                        override fun onEnteringCommit() {
                            lateCancelFlag.set(true)
                        }
                    },
                )
                assertIs<StreamingPackInstaller.Result.Installed>(res)
            }
            assertTrue(PackRegistry(libRoot).load().any { it.packId == "pack.b" })
        } finally {
            PackImportCoordinator.dismissCompletedOrError()
            root.deleteRecursively()
        }
    }

    @Test
    fun checkpointResumeSkipsVerifiedMembersAndRefusesWrongRepick() {
        val root = tempDir()
        try {
            val libRoot = File(root, "packs")
            val appFiles = File(root, "files").apply { mkdirs() }
            val track1 = TrackSpec("COURSE", "Track 1", bwavPayload(41, 8192))
            val track2 = TrackSpec("COURSE", "Track 2", bwavPayload(42, 8192))
            val targetZip = buildPackZip(
                target = File(root, "two-tracks.mkwpack"),
                packId = "pack.resume",
                packVersion = 1,
                packName = "Resumable Pack",
                tracks = listOf(track1, track2),
                stages = listOf(
                    StageSpec("Coaster", "Coaster", listOf(track1.key)),
                    StageSpec("Skyline", "Skyline", listOf(track2.key)),
                ),
            )
            val otherZip = buildPackZip(
                target = File(root, "wrong-file.mkwpack"),
                packId = "pack.other",
                packVersion = 1,
                packName = "Wrong Pack",
                tracks = listOf(track1),
                stages = listOf(StageSpec("Coaster", "Coaster", listOf(track1.key))),
            )

            val fingerprint: String
            val manifestBytes: ByteArray
            val audio1Member = "audio/${track1.digest}"
            val audio2Member = "audio/${track2.digest}"
            FileChannel.open(targetZip.toPath(), StandardOpenOption.READ).use { ch ->
                SeekableZipReader.open(ch).use { zip ->
                    fingerprint = zip.archiveFingerprint
                    manifestBytes = zip.readBytes(PackManifest.MEMBER_NAME)!!
                }
            }

            // Prepare durable checkpoint & staging dir simulating prior crash after verifying track1.
            val cpId = "cp-resume-1"
            val store = PackImportCheckpointStore(libRoot)
            val stagingDir = store.stagingDir(cpId).apply { mkdirs() }
            File(stagingDir, PackManifest.MEMBER_NAME).writeBytes(manifestBytes)
            File(stagingDir, "audio").mkdirs()
            val preVerifiedAudio1 = File(stagingDir, audio1Member).apply { writeBytes(track1.bytes) }
            preVerifiedAudio1.setLastModified(1_000_000L)

            val checkpoint = ImportCheckpoint(
                checkpointId = cpId,
                sourceUri = null,
                displayName = "two-tracks.mkwpack",
                sourceByteSize = targetZip.length(),
                archiveFingerprint = fingerprint,
                packId = "pack.resume",
                packVersion = 1,
                packName = "Resumable Pack",
                totalMembers = 3,
                completedMembers = setOf(PackManifest.MEMBER_NAME, audio1Member),
                bytesProcessed = manifestBytes.size.toLong() + track1.bytes.size.toLong(),
            )
            store.write(checkpoint).getOrThrow()

            // 1. Re-picking a DIFFERENT archive must be refused without deleting checkpoint (#97).
            FileChannel.open(otherZip.toPath(), StandardOpenOption.READ).use { ch ->
                val wrongRes = StreamingPackInstaller.installFromChannel(
                    channel = ch,
                    libraryRoot = libRoot,
                    appFilesDir = appFiles,
                    displayName = "wrong-file.mkwpack",
                    sourceUri = null,
                    resumeCheckpoint = checkpoint,
                )
                assertIs<StreamingPackInstaller.Result.SourceMismatch>(wrongRes)
            }
            assertTrue(preVerifiedAudio1.isFile, "Wrong re-pick must keep already-staged verified audio.")
            assertNotNull(store.read().getOrNull())

            // 2. Resuming with the EXACT archive skips verified track1 and extracts only track2 (#95, #97).
            val progressedMembers = mutableListOf<String>()
            val resumeRes = FileChannel.open(targetZip.toPath(), StandardOpenOption.READ).use { ch ->
                StreamingPackInstaller.installFromChannel(
                    channel = ch,
                    libraryRoot = libRoot,
                    appFilesDir = appFiles,
                    displayName = "two-tracks.mkwpack",
                    sourceUri = null,
                    resumeCheckpoint = checkpoint,
                    listener = object : StreamingPackInstaller.Listener {
                        override fun onMemberProgress(
                            processedMembers: Int,
                            totalMembers: Int,
                            memberName: String,
                            bytesProcessed: Long,
                            totalBytes: Long,
                        ) {
                            progressedMembers += memberName
                        }
                    },
                )
            }
            val installed = assertIs<StreamingPackInstaller.Result.Installed>(resumeRes)
            assertTrue(progressedMembers.contains(audio2Member))
            // Verified member preserved its pre-crash staged file (never re-extracted from ZIP) (#95, #97).
            assertEquals(1_000_000L, File(installed.directory, audio1Member).lastModified())
            assertTrue(File(installed.directory, audio2Member).lastModified() > 1_000_000L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun libraryInvalidationStopsPlaybackClearsContinueListeningAndKeepsRoutesForTailRepair() {
        val root = tempDir()
        try {
            val libDir = File(root, "internal").apply { mkdirs() }
            val audioDir = File(libDir, "audio").apply { mkdirs() }
            val coaster = TrackSpec("COURSE", "Coaster", bwavPayload(51))
            val skyline = TrackSpec("COURSE", "Skyline", bwavPayload(52))
            val oldRoad = TrackSpec("JUKEBOX", "Old Road", bwavPayload(53))
            val newRoad = TrackSpec("JUKEBOX", "New Road", bwavPayload(54))

            File(audioDir, coaster.digest).writeBytes(coaster.bytes)
            File(audioDir, skyline.digest).writeBytes(skyline.bytes)
            File(audioDir, newRoad.digest).writeBytes(newRoad.bytes)
            // Note: oldRoad audio file is intentionally absent after update; pool now contains ONLY newRoad.

            val manifestJsonStr = manifestJson(
                packId = "internal.merged",
                packVersion = 2,
                packName = "Merged v2",
                tracks = listOf(coaster, skyline, newRoad),
                stages = listOf(
                    StageSpec("Coaster", "Coaster", listOf(coaster.key)),
                    StageSpec("Skyline", "Skyline", listOf(skyline.key)),
                ),
            )
            val manifest = assertIs<PackManifest.Result.Ok>(PackManifest.parse(manifestJsonStr)).manifest

            val jukeboxMeta = JukeboxCatalog.parseMetadata(jukeboxMetaJson(newRoad))
            val jukeboxCatalog = JukeboxCatalog.buildFromManifest(manifest, libDir, jukeboxMeta)

            val connections = connectionsCatalog()
            val updatedPool = RoadJukeboxCatalog.CandidatePool.parse(
                """
                {
                  "schemaVersion": 1,
                  "dataVersion": 2,
                  "roads": [
                    { "from": "Coaster", "to": "Skyline", "candidates": ["${newRoad.key}"] }
                  ]
                }
                """.trimIndent(),
            )!!

            // Saved custom route whose edge pinned now-missing oldRoad.
            val routeFile = File(root, "routes/custom_routes.json")
            val routeStore = CustomRouteStore(routeFile)
            val savedRoute = routeStore.create(
                name = "Sunset Loop",
                stops = listOf(
                    CustomRouteStop("Coaster", PresetRefs.SYSTEM_DEFAULT),
                    CustomRouteStop("Skyline", PresetRefs.SYSTEM_DEFAULT),
                ),
                edges = listOf(
                    CustomRouteEdgeConfig(
                        from = "Coaster",
                        to = "Skyline",
                        musicEnabled = true,
                        selectedJukeboxKey = oldRoad.key,
                    ),
                ),
                connections = connections,
                loopWholeRoute = false,
            ).getOrThrow()

            val env = LibraryInvalidation.Environment(
                mergedManifest = manifest,
                mergedDirectory = libDir,
                jukeboxCatalog = jukeboxCatalog,
                roadPool = updatedPool,
                stageConnections = connections,
                officialRoutes = null,
                customRoutes = routeStore.read().getOrThrow(),
            )

            // #87: Custom route is flagged invalid when its substitute / required pool candidate / stage is unavailable,
            // yet remains intact & unmutated on disk for tail repair.
            val envMissingSubstitute = env.copy(roadPool = RoadJukeboxCatalog.CandidatePool())
            val flagged = LibraryInvalidation.findInvalidCustomRoutes(envMissingSubstitute)
            assertEquals(1, flagged.size)
            assertEquals(savedRoute.id, flagged.single().first.id)
            assertFalse(flagged.single().second.canPlay)
            val storedAfter = routeStore.find(savedRoute.id).getOrNull()
            assertEquals(savedRoute, storedAfter)

            // #88: Active playback (route edge with oldRoad OR missing stage/lock) must stop immediately.
            val routeCurrentEntry = LinearQueue.Entry(
                key = "custom:${savedRoute.id}:edge:0:${oldRoad.key}",
                title = "Old Road",
                sourceType = LinearSessionStore.SourceType.JUKEBOX,
                sourceTrackKey = oldRoad.key,
                assetSha256 = oldRoad.digest,
            )
            val linearStop = LibraryInvalidation.shouldStopActiveLinear(
                contextKind = LinearSessionStore.ContextKind.CUSTOM_ROUTE,
                contextId = savedRoute.id,
                currentEntry = routeCurrentEntry,
                allQueueEntries = listOf(routeCurrentEntry),
                env = env,
            )
            assertTrue(linearStop.shouldStop)

            val lockedStageStop = LibraryInvalidation.shouldStopInteractiveStage(
                stageCode = "RainbowRoad",
                env = env.copy(isStageHidden = { code -> code == "RainbowRoad" }),
            )
            assertTrue(lockedStageStop.shouldStop)

            // #89: Saved Continue Listening referencing invalidated route/old candidate pool returns false.
            val invalidSnapshot = LinearSessionStore.Snapshot(
                frozenBase = listOf(
                    LinearSessionStore.SavedEntry(
                        key = "custom:${savedRoute.id}:edge:0:${oldRoad.key}",
                        title = "Old Road",
                        sourceType = LinearSessionStore.SourceType.JUKEBOX,
                        sourceTrackKey = oldRoad.key,
                        assetSha256 = oldRoad.digest,
                    ),
                ),
                current = LinearSessionStore.SavedEntry(
                    key = "custom:${savedRoute.id}:edge:0:${oldRoad.key}",
                    title = "Old Road",
                    sourceType = LinearSessionStore.SourceType.JUKEBOX,
                    sourceTrackKey = oldRoad.key,
                    assetSha256 = oldRoad.digest,
                ),
                currentBaseIndex = 0,
                contextKind = LinearSessionStore.ContextKind.CUSTOM_ROUTE,
                contextId = savedRoute.id,
            )
            assertFalse(LibraryInvalidation.isSavedSessionStillValid(invalidSnapshot, env))

            // Valid Continue Listening for an installed Jukebox track remains valid.
            val validSnapshot = LinearSessionStore.Snapshot(
                frozenBase = listOf(
                    LinearSessionStore.SavedEntry(
                        key = newRoad.key,
                        title = newRoad.title,
                        sourceType = LinearSessionStore.SourceType.JUKEBOX,
                        sourceTrackKey = newRoad.key,
                        assetSha256 = newRoad.digest,
                    ),
                ),
                current = LinearSessionStore.SavedEntry(
                    key = newRoad.key,
                    title = newRoad.title,
                    sourceType = LinearSessionStore.SourceType.JUKEBOX,
                    sourceTrackKey = newRoad.key,
                    assetSha256 = newRoad.digest,
                ),
                currentBaseIndex = 0,
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_ALL,
            )
            assertTrue(LibraryInvalidation.isSavedSessionStillValid(validSnapshot, env))

            // Special Cup hidden extras (`opening_special`, `special_cup_appears`, etc.) are blocked when Rainbow is locked.
            val hiddenExtraEntry = LinearSessionStore.SavedEntry(
                key = "extra:opening_special",
                title = "Opening (Special Cup)",
                sourceType = LinearSessionStore.SourceType.EXTRA,
            )
            val lockedExtraSnapshot = LinearSessionStore.Snapshot(
                frozenBase = listOf(hiddenExtraEntry),
                current = hiddenExtraEntry,
                currentBaseIndex = 0,
                contextKind = LinearSessionStore.ContextKind.EXTRA,
                contextId = "opening_special",
            )
            val lockedEnv = env.copy(isStageHidden = { code -> code == "Rainbow" })
            assertFalse(LibraryInvalidation.isSavedSessionStillValid(lockedExtraSnapshot, lockedEnv))
            assertTrue(LibraryInvalidation.isSavedSessionStillValid(lockedExtraSnapshot, env))

            // Non-Special Cup openings (e.g. Mushroom Cup, Knockout Tour) stay allowed even while locked.
            val visibleExtraEntry = LinearSessionStore.SavedEntry(
                key = "extra:opening_knockout",
                title = "Opening (Knockout Tour)",
                sourceType = LinearSessionStore.SourceType.EXTRA,
            )
            val visibleExtraSnapshot = lockedExtraSnapshot.copy(
                frozenBase = listOf(visibleExtraEntry),
                current = visibleExtraEntry,
                contextId = "opening_knockout",
            )
            assertTrue(LibraryInvalidation.isSavedSessionStillValid(visibleExtraSnapshot, lockedEnv))
        } finally {
            root.deleteRecursively()
        }
    }
}
