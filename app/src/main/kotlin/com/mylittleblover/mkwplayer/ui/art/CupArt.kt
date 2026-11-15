package com.mylittleblover.mkwplayer.ui.art

import com.mylittleblover.mkwplayer.R

/**
 * Which artwork the app may show, and what it must say about where the artwork came from.
 *
 * Every image in the app is Nintendo's. The handoff pack is explicit that original game icons must not
 * be built into a published APK without a rights position, so this file exists to make the provenance
 * visible rather than implied: a build that ships these icons is a private research build, and the
 * screen has to say so instead of presenting borrowed art as the app's own.
 *
 * The alternative -- no art at all -- was rejected because the cup list is the one place where a
 * picture carries real information: the seven cup emblems are how a player recognises a cup.
 */
internal object CupArt {

    /**
     * How the artwork in this build is classified.
     *
     * Deliberately not a boolean. "Attribution shown" and "rights cleared" are different claims, and
     * collapsing them would let a research build look like a release.
     */
    enum class RightsStatus {
        /** Nintendo-owned art, included for private research. Not redistributable. */
        PRIVATE_RESEARCH,
    }

    /** The statement the UI must show wherever this art appears. */
    const val NOTICE: String =
        "Cup emblems are Nintendo artwork, included for private research only. Not redistributable."

    val status: RightsStatus get() = RightsStatus.PRIVATE_RESEARCH

    /** A cup the app can show. `id` matches the research export's cup naming. */
    data class Cup(val id: String, val label: String, val drawable: Int)

    /**
     * The seven cups, in the order the game lists them.
     *
     * Order is data, not layout: reordering the list would put the cups in an order the game does not
     * use, which reads as a mistake rather than a choice.
     */
    val cups: List<Cup> = listOf(
        Cup("mushroom", "Mushroom Cup", R.drawable.cup_mushroom),
        Cup("flower", "Flower Cup", R.drawable.cup_flower),
        Cup("star", "Star Cup", R.drawable.cup_star),
        Cup("shell", "Shell Cup", R.drawable.cup_shell),
        Cup("banana", "Banana Cup", R.drawable.cup_banana),
        Cup("leaf", "Leaf Cup", R.drawable.cup_leaf),
        Cup("lightning", "Lightning Cup", R.drawable.cup_lightning),
    )

    /**
     * The hidden cup's emblem.
     *
     * Kept out of [cups] on purpose: a list that includes it would show it wherever the list is
     * rendered, and the cup is supposed to be invisible until earned. Callers ask for it explicitly,
     * and only behind `CupVisibility`.
     */
    val special: Cup = Cup("special", "Special Cup", R.drawable.cup_special)

    /** Every cup including the hidden one, for lookups that must find it. */
    fun all(): List<Cup> = cups + special

    /** Finds a cup by id, or null. Returns null for the hidden cup unless asked, by design. */
    fun cup(id: String): Cup? = cups.firstOrNull { it.id == id }

    /**
     * The cup's emblem, including the hidden one.
     *
     * A separate function rather than a flag on [cup], so a call site that reveals the hidden cup is
     * visibly different from one that does not.
     */
    fun cupIncludingHidden(id: String): Cup? = all().firstOrNull { it.id == id }

    /** A Knockout Tour rally crest matching `official_routes.json` route ids. */
    data class Rally(val id: String, val label: String, val drawable: Int, val code: String)

    /** The twelve Knockout Tour rallies in game order (`SV00`..`SV11`). */
    val rallies: List<Rally> = listOf(
        Rally("golden_rally", "Golden Rally", R.drawable.sv_00, "SV00"),
        Rally("ice_rally", "Ice Rally", R.drawable.sv_01, "SV01"),
        Rally("moon_rally", "Moon Rally", R.drawable.sv_02, "SV02"),
        Rally("spiny_rally", "Spiny Rally", R.drawable.sv_03, "SV03"),
        Rally("cherry_rally", "Cherry Rally", R.drawable.sv_04, "SV04"),
        Rally("acorn_rally", "Acorn Rally", R.drawable.sv_05, "SV05"),
        Rally("cloud_rally", "Cloud Rally", R.drawable.sv_06, "SV06"),
        Rally("heart_rally", "Heart Rally", R.drawable.sv_07, "SV07"),
        Rally("drill_rally", "Drill Rally", R.drawable.sv_08, "SV08"),
        Rally("boomerang_rally", "Boomerang Rally", R.drawable.sv_09, "SV09"),
        Rally("propeller_rally", "Propeller Rally", R.drawable.sv_10, "SV10"),
        Rally("turnip_rally", "Turnip Rally", R.drawable.sv_11, "SV11"),
    )

    fun rally(id: String): Rally? = rallies.firstOrNull { it.id == id }

    /** Resolves an official route id (either `<cup>_cup` or `<name>_rally`) to its badge drawable. */
    fun routeEmblem(routeId: String): Int? =
        all().firstOrNull { "${it.id}_cup" == routeId }?.drawable ?: rally(routeId)?.drawable

    private val routeMasks: Map<String, Int> = mapOf(
        "mushroom_cup" to R.drawable.map_light_gp00,
        "flower_cup" to R.drawable.map_light_gp01,
        "star_cup" to R.drawable.map_light_gp02,
        "shell_cup" to R.drawable.map_light_gp03,
        "banana_cup" to R.drawable.map_light_gp04,
        "leaf_cup" to R.drawable.map_light_gp05,
        "lightning_cup" to R.drawable.map_light_gp06,
        "special_cup" to R.drawable.map_light_gp07,
        "golden_rally" to R.drawable.map_light_sv00,
        "ice_rally" to R.drawable.map_light_sv01,
        "moon_rally" to R.drawable.map_light_sv02,
        "spiny_rally" to R.drawable.map_light_sv03,
        "cherry_rally" to R.drawable.map_light_sv04,
        "acorn_rally" to R.drawable.map_light_sv05,
        "cloud_rally" to R.drawable.map_light_sv06,
        "heart_rally" to R.drawable.map_light_sv07,
        "drill_rally" to R.drawable.map_light_sv08,
        "boomerang_rally" to R.drawable.map_light_sv09,
        "propeller_rally" to R.drawable.map_light_sv10,
        "turnip_rally" to R.drawable.map_light_sv11,
    )

    /** Resolves an official route id to its `Map_00` corridor highlight mask drawable. */
    fun routeHighlightMask(routeId: String): Int? = routeMasks[routeId]
}

/**
 * Mode emblems, shown on the route and mode pickers.
 *
 * Same provenance rule as [CupArt]: Nintendo's art, private research build, stated on screen.
 */
internal object ModeArt {

    /** One mode. `id` matches the research export's mode naming. */
    data class Mode(val id: String, val label: String, val drawable: Int, val playable: Boolean)

    /**
     * The modes the archive carries music for.
     *
     * `playable` is false for modes whose music this build cannot decode. Marking them playable
     * because an icon exists would be exactly the kind of claim the evidence rules forbid.
     */
    val modes: List<Mode> = listOf(
        Mode("grand_prix", "Grand Prix", R.drawable.mode_grand_prix, playable = true),
        Mode("knockout", "Knockout Tour", R.drawable.mode_knockout, playable = true),
        Mode("time_trial", "Time Trial", R.drawable.mode_time_trial, playable = true),
        Mode("vs", "VS Race", R.drawable.mode_vs, playable = true),
        Mode("balloon", "Balloon Battle", R.drawable.mode_balloon, playable = true),
        Mode("coin", "Coin Runners", R.drawable.mode_coin, playable = true),
        Mode("bomb", "Bob-omb Blast", R.drawable.mode_bobomb, playable = true),
    )

    fun mode(id: String): Mode? = modes.firstOrNull { it.id == id }

    /** The one line that must accompany this art. */
    const val NOTICE: String = CupArt.NOTICE
}
