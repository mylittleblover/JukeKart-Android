package com.mylittleblover.mkwplayer.pack

/** Lifecycle phase of the single active `.mkwpack` import pipeline (#91-#97). */
internal enum class PackImportPhase {
    IDLE,
    PROBING,
    IMPORTING,
    COMMITTING,
    RESUMABLE,
    PAUSED_NEEDS_REPICK,
    COMPLETED,
    CANCELLED,
    FAILED,
}

/** How an optional road candidate pool update resolved during pack import (#86). */
internal enum class PoolUpdateStatus {
    NONE,
    UNCHANGED,
    REPLACED,
    IGNORED_OLDER,
}

/** Concise post-commit summary / changelog surfaced automatically without confirmation dialogs (#92). */
internal data class PackImportChangelog(
    val packId: String,
    val packVersion: Int,
    val packName: String,
    val isDelta: Boolean,
    val addedOrUpdatedAssets: Int,
    val addedOrUpdatedTracks: Int,
    val totalLibraryTracks: Int,
    val playableLibraryTracks: Int,
    val jukeboxTrackCount: Int,
    val poolStatus: PoolUpdateStatus = PoolUpdateStatus.NONE,
    val poolDataVersion: Int? = null,
    val poolEdgeCount: Int = 0,
    val stoppedActivePlayback: Boolean = false,
    val clearedContinueListening: Boolean = false,
    val invalidatedCustomRoutesCount: Int = 0,
)

/** Durable checkpoint persisted across crashes / process death (#95, #97). */
internal data class ImportCheckpoint(
    val checkpointId: String,
    val sourceUri: String?,
    val displayName: String,
    val sourceByteSize: Long,
    val archiveFingerprint: String,
    val packId: String,
    val packVersion: Int,
    val packName: String,
    val totalMembers: Int,
    val completedMembers: Set<String>,
    val bytesProcessed: Long = 0L,
    val verified: Boolean = false,
    val committing: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

/** Observable UI & foreground notification state for [PackImportCoordinator]. */
internal data class PackImportUiState(
    val phase: PackImportPhase = PackImportPhase.IDLE,
    val displayName: String = "",
    val processedMembers: Int = 0,
    val totalMembers: Int = 0,
    val currentMember: String = "",
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val canCancel: Boolean = false,
    val changelog: PackImportChangelog? = null,
    val errorReason: String? = null,
    val checkpoint: ImportCheckpoint? = null,
    val hasPersistablePermission: Boolean = false,
) {
    val isActive: Boolean
        get() = phase == PackImportPhase.PROBING ||
            phase == PackImportPhase.IMPORTING ||
            phase == PackImportPhase.COMMITTING

    val fraction: Float
        get() = when {
            phase == PackImportPhase.COMMITTING -> 1f
            totalMembers <= 0 -> 0f
            else -> (processedMembers.toFloat() / totalMembers.toFloat()).coerceIn(0f, 1f)
        }
}
