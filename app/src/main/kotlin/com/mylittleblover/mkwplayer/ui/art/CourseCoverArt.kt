package com.mylittleblover.mkwplayer.ui.art

import com.mylittleblover.mkwplayer.R

/**
 * The real course covers, one landscape photograph per stage.
 *
 * These are the archive's own stage select images (`C_StageL_*` in
 * `readable_assets/images/Layout`), downscaled to 720x406 and re-encoded as WebP. They are what a course
 * card should show: [CoursePlaceholder] crops the world map around a pin, which is a location marker, not a
 * picture of the course.
 *
 * Keyed by the same anchor code [StageArt] uses, so a stage cannot have an icon and a cover that disagree
 * about which stage it is. A stage with no cover returns null and the caller falls back to the map crop;
 * there is deliberately no substitution of another course's art.
 */
internal object CourseCoverArt {

    private val byAnchorCode: Map<String, Int> = mapOf(
        "AirshipFortress_00" to R.drawable.cover_airshipfortress,
        "BigTree_00" to R.drawable.cover_bigtree,
        "Cave_00" to R.drawable.cover_cave,
        "Central_00" to R.drawable.cover_central,
        "ChocoMountain_00" to R.drawable.cover_chocomountain,
        "Cinema_00" to R.drawable.cover_cinema,
        "Coaster" to R.drawable.cover_coaster,
        "DKJungleParkway_00" to R.drawable.cover_dkjungleparkway,
        "DKPass_00" to R.drawable.cover_dkpass,
        "DesertHills_00" to R.drawable.cover_deserthills,
        "DinoBones_00" to R.drawable.cover_dinobones,
        "DinoDinoJungle_00" to R.drawable.cover_dinodinojungle,
        "DonkeyKong" to R.drawable.cover_donkeykong,
        "FirstCircuit" to R.drawable.cover_firstcircuit,
        "IceBuildings_00" to R.drawable.cover_icebuildings,
        "KoopaTroopaBeach_00" to R.drawable.cover_koopatroopabeach,
        "LagoonCity_00" to R.drawable.cover_lagooncity,
        "Lava_00" to R.drawable.cover_lava,
        "MooMooFarm_00" to R.drawable.cover_moomoofarm,
        "PeachBeach_00" to R.drawable.cover_peachbeach,
        "RaftingRiver_00" to R.drawable.cover_raftingriver,
        "Rainbow_00" to R.drawable.cover_rainbow,
        "SFCMarioCircuit_00" to R.drawable.cover_sfcmariocircuit,
        "ShyGuyBazaar_00" to R.drawable.cover_shyguybazaar,
        "SnowMountain_00" to R.drawable.cover_snowmountain,
        "StormClouds_00" to R.drawable.cover_stormclouds,
        "ToadFactory_00" to R.drawable.cover_toadfactory,
        "WarioShipyard_00" to R.drawable.cover_warioshipyard,
        "WarioStadium" to R.drawable.cover_wariostadium,
        "WestCity" to R.drawable.cover_westcity,
    )

    /** How many stages have a cover. */
    val size: Int get() = byAnchorCode.size

    /** Every anchor code with a cover. */
    fun codes(): Set<String> = byAnchorCode.keys

    /**
     * The cover for a stage code, or null.
     *
     * The library and the map spell a stage two ways: packs carry the export code (`AirshipFortress_00`) and
     * the browse catalogue carries the bare name (`AirshipFortress`). Both are tried. An arrangement
     * (`Cinema_SFC`, `SnowMountain_Pattern2`) is the same course and shares its cover; anything else is null
     * rather than a guess.
     */
    fun resolve(stageCode: String): Int? {
        val code = stageCode.trim()
        if (code.isEmpty()) return null
        byAnchorCode[code]?.let { return it }
        byAnchorCode[code.removeSuffix("_00")]?.let { return it }
        byAnchorCode[code + "_00"]?.let { return it }

        val base = code
            .replace(Regex("_SFC$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_Pattern\\d+$", RegexOption.IGNORE_CASE), "")
        if (base != code) return resolve(base)
        return null
    }
}
