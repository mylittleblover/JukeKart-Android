package com.mylittleblover.mkwplayer.ui.art

/**
 * Resolves runtime `.mkwpack` artwork candidate filenames for a stage code.
 *
 * Public builds do not ship copyrighted course photography inside the APK; real covers are
 * resolved dynamically from user-imported `.mkwpack/artwork/` directories via [packCandidates],
 * falling back cleanly to [CoursePlaceholder] when absent.
 */
internal object CourseCoverArt {

    private val knownAnchorCodes: Set<String> = setOf(
        "AirshipFortress_00",
        "BigTree_00",
        "Cave_00",
        "Central_00",
        "ChocoMountain_00",
        "Cinema_00",
        "Coaster",
        "DKJungleParkway_00",
        "DKPass_00",
        "DesertHills_00",
        "DinoBones_00",
        "DinoDinoJungle_00",
        "DonkeyKong",
        "FirstCircuit",
        "IceBuildings_00",
        "KoopaTroopaBeach_00",
        "LagoonCity_00",
        "Lava_00",
        "MooMooFarm_00",
        "PeachBeach_00",
        "RaftingRiver_00",
        "Rainbow_00",
        "SFCMarioCircuit_00",
        "ShyGuyBazaar_00",
        "SnowMountain_00",
        "StormClouds_00",
        "ToadFactory_00",
        "WarioShipyard_00",
        "WarioStadium",
        "WestCity",
    )

    val size: Int get() = 0

    fun codes(): Set<String> = knownAnchorCodes

    fun resolve(@Suppress("UNUSED_PARAMETER") stageCode: String): Int? = null

    /** Candidate filenames inside `.mkwpack/artwork/` when an external cover pack is installed. */
    fun packCandidates(stageCode: String): List<String> {
        val raw = stageCode.trim()
        if (raw.isEmpty()) return emptyList()
        val bare = raw.removeSuffix("_00")
            .replace(Regex("_SFC$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_Pattern\\d+$", RegexOption.IGNORE_CASE), "")
        val lower = bare.lowercase()
        val anchored = if (bare in knownAnchorCodes) bare else "${bare}_00"
        return listOf(
            "cover_${lower}.webp",
            "cover_${lower}.png",
            "$anchored.webp",
            "$anchored.png",
            "$bare.webp",
            "$bare.png",
            "$raw.webp",
            "$raw.png",
        ).distinct()
    }
}
