package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.MetadataFile
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streaming `.mkwpack` installer operating directly over any [SeekableByteChannel] (#96)
 * with durable crash checkpoints (#95, #97), pre-commit cancellation (#91, #93),
 * multi-pack incremental merge (#84, #85), and road candidate pool version verification (#86).
 */
internal object StreamingPackInstaller {

    sealed interface Result {
        data class Installed(
            val manifest: PackManifest,
            val directory: File,
            val mergedLibrary: InternalPackLibrary.MergedLibrary?,
            val coverage: PackCoverage.Coverage,
            val poolStatus: PoolUpdateStatus,
            val poolDataVersion: Int?,
            val poolEdgeCount: Int,
            val addedOrUpdatedAssets: Int,
            val addedOrUpdatedTracks: Int,
            val previousManifest: PackManifest?,
        ) : Result

        data class Refused(val reason: String) : Result

        data class Cancelled(val reason: String = "Import cancelled before commit.") : Result

        data class SourceMismatch(
            val expectedDisplayName: String,
            val reason: String,
        ) : Result
    }

    interface Listener {
        fun onProbed(manifest: PackManifest, totalMembers: Int, totalBytes: Long) {}
        fun onMemberProgress(
            processedMembers: Int,
            totalMembers: Int,
            memberName: String,
            bytesProcessed: Long,
            totalBytes: Long,
        ) {}
        fun onEnteringCommit() {}
    }

    private const val STREAM_BUFFER_BYTES = 64 * 1024
    private const val CHECKPOINT_FLUSH_INTERVAL = 4

    fun installFromChannel(
        channel: SeekableByteChannel,
        libraryRoot: File,
        appFilesDir: File,
        displayName: String,
        sourceUri: String?,
        resumeCheckpoint: ImportCheckpoint? = null,
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        clearCheckpointOnCancel: Boolean = true,
        listener: Listener? = null,
    ): Result {
        if (cancelFlag.get()) {
            if (clearCheckpointOnCancel) PackImportCheckpointStore(libraryRoot).clearAll()
            return Result.Cancelled()
        }

        val reader = try {
            SeekableZipReader.open(channel, ownsChannel = false)
        } catch (error: Exception) {
            return Result.Refused(
                "Could not read '${displayName}' as a .mkwpack archive: ${error.message ?: "invalid ZIP archive"}.",
            )
        }

        return reader.use { zip ->
            if (resumeCheckpoint != null) {
                if (zip.fileSize != resumeCheckpoint.sourceByteSize ||
                    zip.archiveFingerprint != resumeCheckpoint.archiveFingerprint
                ) {
                    return@use Result.SourceMismatch(
                        expectedDisplayName = resumeCheckpoint.displayName,
                        reason = "The selected file '$displayName' does not match the paused import " +
                            "'${resumeCheckpoint.displayName}' (${resumeCheckpoint.packId} v${resumeCheckpoint.packVersion}).",
                    )
                }
            }

            val names = zip.memberNames()
            if (!PackMember.hasManifest(names)) {
                return@use Result.Refused(PackManifest.MISSING_REASON)
            }
            val memberRefusals = PackMember.checkAll(names)
            if (memberRefusals.isNotEmpty()) {
                return@use Result.Refused(memberRefusals.joinToString(separator = " ") { it.reason })
            }

            val manifestBytes = zip.readBytes(PackManifest.MEMBER_NAME)
            val manifest = when (val parsed = PackManifest.read(manifestBytes)) {
                is PackManifest.Result.Rejected -> return@use Result.Refused(parsed.reason)
                is PackManifest.Result.Ok -> parsed.manifest
            }

            if (resumeCheckpoint != null &&
                (manifest.packId != resumeCheckpoint.packId || manifest.packVersion != resumeCheckpoint.packVersion)
            ) {
                return@use Result.SourceMismatch(
                    expectedDisplayName = resumeCheckpoint.displayName,
                    reason = "The selected archive declares ${manifest.packId} v${manifest.packVersion}, " +
                        "but the paused import expects ${resumeCheckpoint.packId} v${resumeCheckpoint.packVersion}.",
                )
            }

            val report = PackValidation.validate(manifest)
            if (!report.isInstallable) {
                return@use Result.Refused(report.summary())
            }

            // DELTA packs require a compatible base pack already installed (#84, #85).
            if (manifest.isDelta) {
                val baseEntries = PackRegistry(libraryRoot).load()
                    .filter { it.packId == manifest.basePackId && it.packVersion >= (manifest.basePackVersion ?: 0) }
                if (baseEntries.isEmpty()) {
                    return@use Result.Refused(
                        "Delta pack '${manifest.packId}' v${manifest.packVersion} requires " +
                            "'${manifest.basePackId}' v${manifest.basePackVersion}+ to be installed first.",
                    )
                }
            }

            // Verify declared audio sizes from the ZIP Central Directory before extracting bytes.
            declaredSizeMismatch(zip, manifest)?.let { mismatch ->
                return@use Result.Refused(mismatch)
            }
            val declaredDigests = manifest.assets.map { it.digest.sha256 }.toSet()
            val entryByName = zip.entries.filterNot { it.isDirectory }.associateBy { it.name }
            entryByName.keys
                .filter { it.startsWith("audio/") }
                .firstOrNull { it.removePrefix("audio/") !in declaredDigests }
                ?.let { extraAudio ->
                    return@use Result.Refused(
                        "Archive contains unlisted audio asset '$extraAudio' that is not declared in manifest.json.",
                    )
                }

            val totalUncompressedBytes = zip.entries.filterNot { it.isDirectory }.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
            listener?.onProbed(manifest, names.size, totalUncompressedBytes)

            if (cancelFlag.get()) {
                if (clearCheckpointOnCancel) PackImportCheckpointStore(libraryRoot).clearAll()
                return@use Result.Cancelled()
            }

            // Validate optional road candidate pool updates in `routes/*.json` BEFORE committing (#86).
            val currentPool = RoadJukeboxCatalog.loadInternalPool(appFilesDir)
            var nextPoolToWrite: RoadJukeboxCatalog.CandidatePool? = null
            var poolStatus = PoolUpdateStatus.NONE
            var poolDataVersion: Int? = null
            var poolEdgeCount = currentPool.entries.size

            val candidatePoolCheck = validateIncomingCandidatePools(zip, names, currentPool)
            when (candidatePoolCheck) {
                is PoolValidation.Refused -> return@use Result.Refused(candidatePoolCheck.reason)
                is PoolValidation.Ok -> {
                    nextPoolToWrite = candidatePoolCheck.poolToWrite
                    poolStatus = candidatePoolCheck.status
                    poolDataVersion = candidatePoolCheck.version
                    poolEdgeCount = candidatePoolCheck.edgeCount
                }
            }

            if (!libraryRoot.exists() && !libraryRoot.mkdirs()) {
                return@use Result.Refused("Could not create internal library directory '${libraryRoot.path}'.")
            }

            val checkpointStore = PackImportCheckpointStore(libraryRoot)
            val checkpointId = resumeCheckpoint?.checkpointId ?: UUID.randomUUID().toString()
            checkpointStore.clearExcept(checkpointId)
            val stagingDir = checkpointStore.stagingDir(checkpointId)
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                return@use Result.Refused("Could not create import staging directory.")
            }

            val assetBySha = manifest.assets.associateBy { it.digest.sha256 }
            val completed = LinkedHashSet<String>()
            var bytesDone = 0L

            if (resumeCheckpoint != null) {
                for (member in resumeCheckpoint.completedMembers) {
                    val expectedEntry = entryByName[member] ?: continue
                    val stagedFile = File(stagingDir, member)
                    if (stagedFile.isFile && stagedFile.length() == expectedEntry.uncompressedSize) {
                        val validDigest = if (member.startsWith("audio/")) {
                            sha256File(stagedFile) == member.removePrefix("audio/")
                        } else {
                            true
                        }
                        if (validDigest) {
                            completed += member
                            bytesDone += stagedFile.length()
                        } else {
                            stagedFile.delete()
                        }
                    }
                }
            }

            var currentCheckpoint = ImportCheckpoint(
                checkpointId = checkpointId,
                sourceUri = sourceUri ?: resumeCheckpoint?.sourceUri,
                displayName = displayName,
                sourceByteSize = zip.fileSize,
                archiveFingerprint = zip.archiveFingerprint,
                packId = manifest.packId,
                packVersion = manifest.packVersion,
                packName = manifest.name,
                totalMembers = names.size,
                completedMembers = completed.toSet(),
                bytesProcessed = bytesDone,
                verified = false,
                committing = false,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            checkpointStore.write(currentCheckpoint)

            val destination = packDestination(libraryRoot, manifest)
            val previousMerged = InternalPackLibrary(libraryRoot).loadOrMaterialize()?.manifest

            val sortedEntries = zip.entries.filterNot { it.isDirectory }.sortedBy { it.name }
            var extractedSinceFlush = 0

            for (entry in sortedEntries) {
                if (cancelFlag.get()) {
                    if (clearCheckpointOnCancel) checkpointStore.clearAll()
                    return@use Result.Cancelled()
                }
                if (entry.name in completed) {
                    listener?.onMemberProgress(
                        processedMembers = completed.size,
                        totalMembers = names.size,
                        memberName = entry.name,
                        bytesProcessed = bytesDone,
                        totalBytes = totalUncompressedBytes,
                    )
                    continue
                }

                val target = File(stagingDir, entry.name)
                target.parentFile?.mkdirs()

                val expectedSha = if (entry.name.startsWith("audio/")) entry.name.removePrefix("audio/") else null
                val copyOutcome = extractMemberWithCancel(
                    zip = zip,
                    entry = entry,
                    target = target,
                    expectedSha256 = expectedSha,
                    cancelFlag = cancelFlag,
                    onChunk = { chunkBytes ->
                        bytesDone += chunkBytes
                        listener?.onMemberProgress(
                            processedMembers = completed.size,
                            totalMembers = names.size,
                            memberName = entry.name,
                            bytesProcessed = bytesDone,
                            totalBytes = totalUncompressedBytes,
                        )
                    },
                )
                when (copyOutcome) {
                    is MemberExtract.Cancelled -> {
                        if (clearCheckpointOnCancel) checkpointStore.clearAll()
                        return@use Result.Cancelled()
                    }
                    is MemberExtract.Failed -> {
                        return@use Result.Refused(copyOutcome.reason)
                    }
                    is MemberExtract.Ok -> {
                        if (expectedSha != null) {
                            val expectedAsset = assetBySha[expectedSha]
                            if (expectedAsset == null) {
                                return@use Result.Refused("Unexpected audio asset '${entry.name}'.")
                            }
                            if (copyOutcome.bytes != expectedAsset.byteSize) {
                                return@use Result.Refused(
                                    "Audio asset '${expectedAsset.digest.short()}' extracted ${copyOutcome.bytes} bytes " +
                                        "(expected ${expectedAsset.byteSize}).",
                                )
                            }
                            if (!copyOutcome.sha256.equals(expectedSha, ignoreCase = true)) {
                                return@use Result.Refused(
                                    "Audio asset '${entry.name}' failed SHA-256 verification (got ${copyOutcome.sha256}).",
                                )
                            }
                        }
                        completed += entry.name
                        extractedSinceFlush++
                        listener?.onMemberProgress(
                            processedMembers = completed.size,
                            totalMembers = names.size,
                            memberName = entry.name,
                            bytesProcessed = bytesDone,
                            totalBytes = totalUncompressedBytes,
                        )
                        if (extractedSinceFlush >= CHECKPOINT_FLUSH_INTERVAL) {
                            extractedSinceFlush = 0
                            currentCheckpoint = currentCheckpoint.copy(
                                completedMembers = completed.toSet(),
                                bytesProcessed = bytesDone,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                            checkpointStore.write(currentCheckpoint)
                        }
                    }
                }
            }

            // Final whole-pack presence verification before crossing the commit boundary.
            for (asset in manifest.assets) {
                val stagedAudio = File(stagingDir, "audio/${asset.digest.sha256}")
                if (!stagedAudio.isFile || stagedAudio.length() != asset.byteSize) {
                    return@use Result.Refused(
                        "Missing verified audio asset '${asset.digest.short()}' after staging.",
                    )
                }
            }

            // Final pre-commit cancel check (#91, #93). Once we cross this line, cancel is refused.
            if (cancelFlag.get()) {
                if (clearCheckpointOnCancel) checkpointStore.clearAll()
                return@use Result.Cancelled()
            }

            listener?.onEnteringCommit()
            currentCheckpoint = currentCheckpoint.copy(
                completedMembers = completed.toSet(),
                bytesProcessed = bytesDone,
                verified = true,
                committing = true,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            checkpointStore.write(currentCheckpoint)

            // Atomic commit (#91, #92, #93): swap destination pack dir, record registry, materialize merged library & pool.
            val promoted = commitVerifiedPack(
                libraryRoot = libraryRoot,
                appFilesDir = appFilesDir,
                stagingDir = stagingDir,
                destination = destination,
                manifest = manifest,
                poolToWrite = nextPoolToWrite,
            )
            if (promoted != null) {
                return@use Result.Refused(promoted)
            }

            val merged = InternalPackLibrary(libraryRoot).materialize()
            checkpointStore.clearAll()

            val prevAssetIds = previousMerged?.assets?.map { it.digest.sha256 }?.toSet().orEmpty()
            val prevTracksByKey = previousMerged?.tracks?.associateBy { it.key }.orEmpty()
            val addedOrUpdatedAssets = manifest.assets.count { it.digest.sha256 !in prevAssetIds }
            val addedOrUpdatedTracks = manifest.tracks.count { t ->
                val old = prevTracksByKey[t.key]
                old == null || old != t
            }

            Result.Installed(
                manifest = manifest,
                directory = destination,
                mergedLibrary = merged,
                coverage = PackCoverage.of(merged?.manifest ?: manifest),
                poolStatus = poolStatus,
                poolDataVersion = poolDataVersion,
                poolEdgeCount = poolEdgeCount,
                addedOrUpdatedAssets = addedOrUpdatedAssets.coerceAtLeast(0),
                addedOrUpdatedTracks = addedOrUpdatedTracks.coerceAtLeast(0),
                previousManifest = previousMerged,
            )
        }
    }

    private sealed interface PoolValidation {
        data class Ok(
            val poolToWrite: RoadJukeboxCatalog.CandidatePool?,
            val status: PoolUpdateStatus,
            val version: Int?,
            val edgeCount: Int,
        ) : PoolValidation

        data class Refused(val reason: String) : PoolValidation
    }

    private fun validateIncomingCandidatePools(
        zip: SeekableZipReader,
        names: List<String>,
        currentPool: RoadJukeboxCatalog.CandidatePool,
    ): PoolValidation {
        val routeJsonNames = names.filter { it.startsWith("routes/") && it.endsWith(".json") }.sorted()
        var runningPool = currentPool
        var changedPool: RoadJukeboxCatalog.CandidatePool? = null
        var status = PoolUpdateStatus.NONE

        for (memberName in routeJsonNames) {
            val text = try {
                zip.readText(memberName) ?: continue
            } catch (e: Exception) {
                return PoolValidation.Refused("Could not read '$memberName': ${e.message ?: "corrupt entry"}.")
            }
            val obj = try {
                JSONObject(text)
            } catch (e: Exception) {
                return PoolValidation.Refused("'$memberName' is not valid JSON (${e.message ?: "parse error"}).".trim())
            }
            if (!obj.has("roads") && !obj.has("dataVersion")) continue
            val incoming = RoadJukeboxCatalog.CandidatePool.parse(text)
                ?: return PoolValidation.Refused(
                    "Road candidate pool member '$memberName' is malformed and was refused before installation.",
                )
            when (val merge = runningPool.mergeReplace(incoming)) {
                is RoadJukeboxCatalog.MergeResult.RejectedConflict -> {
                    return PoolValidation.Refused(merge.reason)
                }
                is RoadJukeboxCatalog.MergeResult.Replaced -> {
                    if (merge.changed) {
                        runningPool = merge.pool
                        changedPool = merge.pool
                        status = PoolUpdateStatus.REPLACED
                    } else if (status == PoolUpdateStatus.NONE) {
                        status = PoolUpdateStatus.UNCHANGED
                    }
                }
                is RoadJukeboxCatalog.MergeResult.IgnoredOlderVersion -> {
                    if (status == PoolUpdateStatus.NONE) {
                        status = PoolUpdateStatus.IGNORED_OLDER
                    }
                }
            }
        }
        return PoolValidation.Ok(
            poolToWrite = changedPool,
            status = status,
            version = changedPool?.dataVersion ?: runningPool.dataVersion.takeIf { it > 0 },
            edgeCount = runningPool.entries.size,
        )
    }

    private sealed interface MemberExtract {
        data class Ok(val bytes: Long, val sha256: String) : MemberExtract
        data object Cancelled : MemberExtract
        data class Failed(val reason: String) : MemberExtract
    }

    private fun extractMemberWithCancel(
        zip: SeekableZipReader,
        entry: SeekableZipReader.Entry,
        target: File,
        expectedSha256: String?,
        cancelFlag: AtomicBoolean,
        onChunk: (Int) -> Unit,
    ): MemberExtract {
        val tempPart = File(target.parentFile, "${target.name}.part")
        if (tempPart.exists()) tempPart.delete()
        return try {
            val md = if (expectedSha256 != null) MessageDigest.getInstance("SHA-256") else null
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var total = 0L
            zip.openStream(entry).use { input ->
                FileOutputStream(tempPart).buffered(STREAM_BUFFER_BYTES).use { out ->
                    while (true) {
                        if (cancelFlag.get()) {
                            tempPart.delete()
                            return MemberExtract.Cancelled
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        md?.update(buffer, 0, n)
                        total += n
                        onChunk(n)
                    }
                    out.flush()
                }
            }
            if (cancelFlag.get()) {
                tempPart.delete()
                return MemberExtract.Cancelled
            }
            if (target.exists()) target.delete()
            check(tempPart.renameTo(target)) { "Could not rename staged member '${entry.name}'." }
            val digestHex = md?.digest()?.joinToString("") { "%02x".format(it) }.orEmpty()
            MemberExtract.Ok(bytes = total, sha256 = digestHex)
        } catch (error: Exception) {
            tempPart.delete()
            MemberExtract.Failed("Could not extract '${entry.name}': ${error.message ?: error.javaClass.simpleName}.")
        }
    }

    private fun declaredSizeMismatch(zip: SeekableZipReader, manifest: PackManifest): String? {
        for (asset in manifest.assets) {
            val entry = zip.findEntry("audio/${asset.digest.sha256}") ?: continue
            if (entry.uncompressedSize != asset.byteSize) {
                return "Audio asset '${asset.digest.short()}' declares ${asset.byteSize} bytes " +
                    "in manifest.json, but the archive stores ${entry.uncompressedSize} bytes."
            }
        }
        return null
    }

    private fun commitVerifiedPack(
        libraryRoot: File,
        appFilesDir: File,
        stagingDir: File,
        destination: File,
        manifest: PackManifest,
        poolToWrite: RoadJukeboxCatalog.CandidatePool?,
    ): String? {
        return try {
            destination.parentFile?.mkdirs()
            val backup = if (destination.exists()) {
                File(destination.parentFile, "${destination.name}.old-${System.nanoTime()}").also { old ->
                    check(destination.renameTo(old)) { "Could not rotate previous version directory." }
                }
            } else {
                null
            }
            if (!stagingDir.renameTo(destination)) {
                backup?.renameTo(destination)
                error("Could not move verified staging directory into library.")
            }
            PackRegistry(libraryRoot).record(manifest, destination)
            if (poolToWrite != null) {
                MetadataFile.write(File(appFilesDir, "road-jukebox-pool.json"), poolToWrite.toJsonString())
            }
            backup?.deleteRecursively()
            null
        } catch (error: Exception) {
            "Commit failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun packDestination(libraryRoot: File, manifest: PackManifest): File =
        File(File(File(libraryRoot, "packs"), manifest.packId), manifest.packVersion.toString())

    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(STREAM_BUFFER_BYTES)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
