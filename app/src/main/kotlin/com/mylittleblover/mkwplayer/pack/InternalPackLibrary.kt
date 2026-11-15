package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.MetadataFile
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.UUID

/**
 * Multi-pack merged internal music library (#84, #85, #90).
 *
 * Combines every verified pack in [PackRegistry] into `<libraryRoot>/internal/`:
 * - Independent `packId`s merge additively without overwriting each other's courses, jukebox
 *   tracks, markers, music graphs, routes, or artwork (#84, #85).
 * - For the same `packId`, the highest `FULL` version forms that pack's baseline and any newer
 *   `DELTA` packs overlay on top of it, allowing intentional removals in a newer `FULL` release
 *   to invalidate deprecated content (#87-#89) while leaving all other packs untouched.
 * - Exposes no user-facing delete/uninstall action (#90).
 */
internal class InternalPackLibrary(private val libraryRoot: File) {

    data class MergedLibrary(
        val manifest: PackManifest,
        val directory: File,
        val activePackFingerprints: List<String>,
        val contributors: List<PackRegistry.Entry>,
    )

    private data class LoadedPack(
        val entry: PackRegistry.Entry,
        val manifest: PackManifest,
        val directory: File,
    )

    val internalDir: File = File(libraryRoot, INTERNAL_DIR_NAME)

    /**
     * Loads an already-materialized internal library when it still matches the active registry set;
     * otherwise deterministically rebuilds it from [PackRegistry].
     */
    fun loadOrMaterialize(): MergedLibrary? {
        recoverInterruptedSwap()
        val contributors = orderContributors(resolveActivePacks())
        if (contributors.isEmpty()) return null

        val manifestFile = File(internalDir, PackManifest.MEMBER_NAME)
        val expectedFingerprints = contributors.map { "${it.entry.packId}:${it.entry.packVersion}" }
        val metaFile = File(internalDir, MERGE_STATE_FILE)
        val cachedManifest = if (manifestFile.isFile && metaFile.isFile) {
            val recorded = runCatching { metaFile.readText().lines().filter { it.isNotBlank() } }.getOrNull()
            if (recorded == expectedFingerprints) {
                val parsed = runCatching {
                    PackManifest.parse(manifestFile.readText()).manifestOrNull()
                }.getOrNull()
                val allPresent = parsed != null && parsed.assets.all { asset ->
                    val f = File(internalDir, "audio/${asset.digest.sha256}")
                    f.isFile && f.length() == asset.byteSize
                }
                if (allPresent) parsed else null
            } else {
                null
            }
        } else {
            null
        }

        if (cachedManifest != null) {
            return MergedLibrary(
                manifest = cachedManifest,
                directory = internalDir,
                activePackFingerprints = expectedFingerprints,
                contributors = contributors.map { it.entry },
            )
        }
        return materialize(contributors)
    }

    /** Deterministically rebuilds `<libraryRoot>/internal/` atomically from [PackRegistry]. */
    fun materialize(): MergedLibrary? {
        recoverInterruptedSwap()
        val contributors = orderContributors(resolveActivePacks())
        if (contributors.isEmpty()) return null
        return materialize(contributors)
    }

    private fun orderContributors(contributors: List<LoadedPack>): List<LoadedPack> =
        contributors.sortedWith(
            compareBy<LoadedPack> { if (it.manifest.isDelta) 1 else 0 }
                .thenBy { it.manifest.packVersion }
                .thenBy { it.entry.packId },
        )

    private fun materialize(ordered: List<LoadedPack>): MergedLibrary? {

        val assetMap = linkedMapOf<String, Pair<PackManifest.Asset, File>>()
        val trackMap = linkedMapOf<String, PackManifest.Track>()
        val stageOrder = linkedMapOf<String, String>() // canonicalLower -> raw code
        val stageNames = linkedMapOf<String, String>()
        val stageTracks = linkedMapOf<String, MutableList<String>>()
        val provMap = linkedMapOf<Pair<String, String>, PackManifest.Provenance>()

        var maxVersion = 1
        var displayName = "MKW Internal Library"
        var chosenUnlock: PackManifest.Unlock? = null

        for (loaded in ordered) {
            val m = loaded.manifest
            if (m.packVersion >= maxVersion) {
                maxVersion = m.packVersion
                if (m.name.isNotBlank()) displayName = m.name
            }

            for (asset in m.assets) {
                val sourceAudio = File(loaded.directory, "audio/${asset.digest.sha256}")
                if (sourceAudio.isFile && sourceAudio.length() == asset.byteSize) {
                    assetMap[asset.digest.sha256] = asset to sourceAudio
                }
            }

            for (track in m.tracks) {
                if (track.parts.all { it.asset.sha256 in assetMap }) {
                    trackMap[track.key] = track
                }
            }

            for (stage in m.stages) {
                val canonical = stage.code.removeSuffix("_00").lowercase(Locale.ROOT)
                stageOrder[canonical] = stage.code
                if (stage.name.isNotBlank()) stageNames[canonical] = stage.name
                val validNewTracks = stage.tracks.filter { it in trackMap }
                val existing = stageTracks[canonical].orEmpty().filter { it in trackMap }
                stageTracks[canonical] = (validNewTracks + existing).distinct().toMutableList()
            }

            for (prov in m.provenance) {
                provMap[prov.subject to prov.field] = prov
            }

            if (m.unlock.frozen || chosenUnlock == null || !chosenUnlock.frozen) {
                chosenUnlock = m.unlock
            }
        }

        val finalStages = stageOrder.entries.mapNotNull { (canonical, rawCode) ->
            val keys = stageTracks[canonical].orEmpty().filter { it in trackMap }.distinct()
            if (keys.isEmpty()) return@mapNotNull null
            PackManifest.Stage(
                code = rawCode,
                name = stageNames[canonical] ?: rawCode,
                tracks = keys,
            )
        }

        val stagesByCode = finalStages.associateBy { it.code }
        val validUnlockStages = buildList {
            val preferred = chosenUnlock?.stages.orEmpty()
                .filter { code -> stagesByCode[code]?.tracks?.isNotEmpty() == true }
                .distinct()
            if (preferred.isNotEmpty()) {
                addAll(preferred)
            } else {
                ordered.forEach { loaded ->
                    loaded.manifest.unlock.stages.forEach { code ->
                        if (stagesByCode[code]?.tracks?.isNotEmpty() == true && code !in this) {
                            add(code)
                        }
                    }
                }
            }
            if (isEmpty()) {
                finalStages.firstOrNull()?.let { add(it.code) }
            }
        }

        val mergedAssets = assetMap.values.map { it.first }
        val mergedTracks = trackMap.values.toList()
        if (mergedTracks.isEmpty() || mergedAssets.isEmpty()) return null

        val mergedProv = provMap.values.toMutableList()
        if (mergedProv.isEmpty()) {
            mergedProv += PackManifest.Provenance(
                subject = "pack",
                field = "timing",
                level = PackManifest.Level.CHOSEN_BY_PROJECT,
                note = "Merged internal library.",
            )
        }

        val playableCount = PackCoverage.of(
            PackManifest(
                formatVersion = PackManifest.FORMAT_VERSION,
                packId = INTERNAL_PACK_ID,
                packVersion = maxVersion,
                name = displayName,
                kind = PackManifest.Kind.FULL,
                basePackId = null,
                basePackVersion = null,
                coverage = PackManifest.DeclaredCoverage(
                    totalLogicalTracks = mergedTracks.size,
                    playableLogicalTracks = 0,
                    codecKinds = emptyList(),
                ),
                assets = mergedAssets,
                tracks = mergedTracks,
                stages = finalStages,
                provenance = mergedProv,
                unlock = PackManifest.Unlock(
                    frozen = chosenUnlock?.frozen == true && validUnlockStages.isNotEmpty(),
                    stages = validUnlockStages,
                ),
            ),
        ).playableCount

        val mergedManifest = PackManifest(
            formatVersion = PackManifest.FORMAT_VERSION,
            packId = INTERNAL_PACK_ID,
            packVersion = maxVersion,
            name = displayName,
            kind = PackManifest.Kind.FULL,
            basePackId = null,
            basePackVersion = null,
            coverage = PackManifest.DeclaredCoverage(
                totalLogicalTracks = mergedTracks.size,
                playableLogicalTracks = playableCount,
                codecKinds = mergedAssets.map { it.codecKind }.distinct().sorted(),
            ),
            assets = mergedAssets,
            tracks = mergedTracks,
            stages = finalStages,
            provenance = mergedProv,
            unlock = PackManifest.Unlock(
                frozen = chosenUnlock?.frozen == true && validUnlockStages.isNotEmpty(),
                stages = validUnlockStages,
            ),
        )

        val tempDir = File(libraryRoot, "internal.tmp-${UUID.randomUUID()}")
        try {
            if (!tempDir.mkdirs()) return null
            val tmpAudioDir = File(tempDir, "audio").apply { mkdirs() }
            for ((sha, pair) in assetMap) {
                val (asset, srcFile) = pair
                val dstFile = File(tmpAudioDir, sha)
                val existingInternal = File(File(internalDir, "audio"), sha)
                val reused = if (existingInternal.isFile && existingInternal.length() == asset.byteSize) {
                    linkOrCopy(existingInternal, dstFile)
                } else {
                    false
                }
                if (!reused) {
                    linkOrCopy(srcFile, dstFile)
                }
            }

            // Merge auxiliary directories cleanly in precedence order (higher priority overrides same member name).
            for (dirName in listOf("music-graphs", "markers", "routes", "artwork")) {
                val dstSub = File(tempDir, dirName)
                for (loaded in ordered) {
                    val srcSub = File(loaded.directory, dirName)
                    if (!srcSub.isDirectory) continue
                    if (!dstSub.exists()) dstSub.mkdirs()
                    srcSub.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val target = File(dstSub, file.name)
                            if (target.exists()) target.delete()
                            linkOrCopy(file, target)
                        }
                    }
                }
            }

            MetadataFile.write(File(tempDir, PackManifest.MEMBER_NAME), PackManifestWriter.toJson(mergedManifest))
            val fingerprints = ordered.map { "${it.entry.packId}:${it.entry.packVersion}" }
            MetadataFile.write(File(tempDir, MERGE_STATE_FILE), fingerprints.joinToString("\n"))

            swapDirectories(tempDir, internalDir)

            return MergedLibrary(
                manifest = mergedManifest,
                directory = internalDir,
                activePackFingerprints = fingerprints,
                contributors = ordered.map { it.entry },
            )
        } finally {
            if (tempDir.exists()) tempDir.deleteRecursively()
        }
    }

    private fun resolveActivePacks(): List<LoadedPack> {
        val entries = PackRegistry(libraryRoot).load()
        if (entries.isEmpty()) return emptyList()

        val valid = entries.mapNotNull { entry ->
            runCatching {
                val expected = File(File(File(libraryRoot, "packs"), entry.packId), entry.packVersion.toString()).canonicalFile
                val directory = File(entry.directory).canonicalFile
                if (directory != expected || !directory.toPath().startsWith(libraryRoot.canonicalFile.toPath())) {
                    return@runCatching null
                }
                val manifestFile = File(directory, PackManifest.MEMBER_NAME)
                if (!manifestFile.isFile) return@runCatching null
                val manifest = PackManifest.parse(manifestFile.readText()).manifestOrNull() ?: return@runCatching null
                if (manifest.packId != entry.packId || manifest.packVersion != entry.packVersion) {
                    return@runCatching null
                }
                if (!PackValidation.validate(manifest).isInstallable) return@runCatching null
                if (!manifest.assets.all { asset ->
                    val f = File(directory, "audio/${asset.digest.sha256}")
                    f.isFile && f.length() == asset.byteSize
                }) return@runCatching null
                LoadedPack(entry, manifest, directory)
            }.getOrNull()
        }

        // Group by packId: latest FULL baseline + newer DELTAs for that packId (#84, #85, #87).
        return valid.groupBy { it.entry.packId }.values.flatMap { group ->
            val sorted = group.sortedBy { it.entry.packVersion }
            val latestFullIndex = sorted.indexOfLast { !it.manifest.isDelta }
            if (latestFullIndex >= 0) sorted.drop(latestFullIndex) else sorted
        }
    }

    private fun recoverInterruptedSwap() {
        if (!internalDir.exists()) {
            val backup = libraryRoot.listFiles()?.firstOrNull {
                it.isDirectory && it.name.startsWith("internal.old-")
            }
            if (backup != null && !backup.renameTo(internalDir)) {
                backup.deleteRecursively()
            }
        }
        libraryRoot.listFiles()?.forEach { child ->
            if (child.isDirectory && (child.name.startsWith("internal.tmp-") || child.name.startsWith("internal.old-"))) {
                child.deleteRecursively()
            }
        }
    }

    private fun swapDirectories(stagedDir: File, targetDir: File) {
        val backup = if (targetDir.exists()) {
            File(libraryRoot, "internal.old-${System.nanoTime()}").also { old ->
                check(targetDir.renameTo(old)) {
                    "Could not rotate previous internal library directory."
                }
            }
        } else {
            null
        }
        if (!stagedDir.renameTo(targetDir)) {
            backup?.renameTo(targetDir)
            error("Could not atomically promote merged internal library.")
        }
        backup?.deleteRecursively()
    }

    private fun linkOrCopy(source: File, target: File): Boolean {
        return try {
            Files.createLink(target.toPath(), source.toPath())
            true
        } catch (_: Exception) {
            try {
                source.copyTo(target, overwrite = true)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        const val INTERNAL_DIR_NAME = "internal"
        const val INTERNAL_PACK_ID = "internal.merged"
        private const val MERGE_STATE_FILE = ".merge-contributors"
    }
}
