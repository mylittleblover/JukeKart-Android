package com.mylittleblover.mkwplayer.library

/**
 * One playable entry as the library UI sees it.
 *
 * Derived from the OST index, which assigns every entry a stable key from its
 * container position. The key is what persists; [displayName] is only a label
 * and may change when naming research improves.
 */
data class TrackEntry(
    val entryKey: String,
    val displayName: String,
    val category: TrackCategory,
    /** True when the source bytes are a BWAV this build can decode. */
    val playable: Boolean,
    /** Why it is not playable, when it is not. Null when playable. */
    val blockedReason: String? = null,
)

/** Grouping for the library list. Mirrors the index classifier, minus classes with no music. */
enum class TrackCategory(val label: String) {
    COURSE("Course themes"),
    BATTLE("Battle"),
    JUKEBOX("Jukebox"),
    SEQUENCE("Sequences"),
    MISSION("Missions"),
    CUTSCENE("Cutscenes"),
    DEMO("Demos"),
    BOOT("Boot"),
    SOUND_EFFECT("Sound effects"),
    OTHER("Other"),
    ;

    companion object {
        /**
         * Maps the index classifier's names onto UI groups.
         *
         * Unknown classes fall to [OTHER] rather than throwing: the index is
         * provisional and may gain a class before this list is updated.
         */
        fun fromIndexName(name: String): TrackCategory = when (name.uppercase()) {
            "COURSE" -> COURSE
            "BATTLE" -> BATTLE
            "JUKEBOX" -> JUKEBOX
            "SEQUENCE" -> SEQUENCE
            "MISSION" -> MISSION
            "CUTSCENE" -> CUTSCENE
            "DEMO" -> DEMO
            "BOOT" -> BOOT
            "SOUND_EFFECT" -> SOUND_EFFECT
            else -> OTHER
        }
    }
}

/**
 * Decides whether a track can be played by this build.
 *
 * Two facts drive it, both measured rather than assumed:
 *  - the decoder handles DSP-ADPCM (codec 1) and chunked Opus (codec 2)
 *  - every course theme is decodable, so a course theme must never be greyed out
 *
 * The measured distribution, checked against the real files:
 *
 *   COURSE    338 x kind1                     -> playable
 *   JUKEBOX   227 x kind2                     -> playable since codec 2 was wired up
 *   CUTSCENE   22 x kind2                     -> playable
 *   BATTLE     16 x kind1                     -> playable
 *   SEQUENCE   33 x kind1 + 1 x kind2         -> playable
 *   DEMO        1 x kind1 + 3 x kind2         -> playable
 *
 * Codec 2 used to be refused wholesale, which greyed out the entire jukebox. That was a limitation of the
 * reader, not of the container: `parseBwavOpusChannel` read the pre-skip from the wrong word of the config
 * block, so no codec-2 file ever reached the decoder. With that fixed, all 227 jukebox files decode (probed
 * one at a time through the real `openBwav`, 227 OK / 0 refused / 0 silent).
 *
 * The mixed groups are still why playability must be decided per entry rather than by category.
 *
 * Keeping this rule in one place means the UI cannot drift from the decoder's actual capability.
 */
object Playability {

    /** BWAV codec kinds this build decodes: Nintendo DSP-ADPCM and chunked Opus. */
    val SUPPORTED_BWAV_CODEC_KINDS: Set<Int> = setOf(1, 2)

    /** The codec a build without Opus would stop at. Kept as the documented lower bound. */
    const val SUPPORTED_BWAV_CODEC_KIND = 1

    /** What the UI shows for a file whose codec is not implemented. */
    const val REASON_UNSUPPORTED_CODEC = "This entry uses an audio codec this build does not decode yet."

    /**
     * @param codecKind the BWAV channel codec kind, or null when the bytes are not
     *   a BWAV this build can inspect.
     */
    fun evaluate(codecKind: Int?): Result {
        if (codecKind == null) {
            return Result(playable = false, reason = "Unreadable source.")
        }
        return if (codecKind in SUPPORTED_BWAV_CODEC_KINDS) {
            Result(playable = true, reason = null)
        } else {
            Result(playable = false, reason = REASON_UNSUPPORTED_CODEC)
        }
    }

    data class Result(val playable: Boolean, val reason: String?)
}

/**
 * Orders entries for display.
 *
 * Course themes first because they are the ones this build is verified to play;
 * putting 227 unplayable JukeBox rows at the top would present the limitation
 * before the working feature.
 */
object TrackOrdering {

    fun sort(entries: List<TrackEntry>): List<TrackEntry> =
        entries.sortedWith(
            compareBy<TrackEntry> { if (it.playable) 0 else 1 }
                .thenBy { it.category.ordinal }
                .thenBy { it.displayName },
        )

    /** Groups by category, preserving [sort]'s order within each group. */
    fun group(entries: List<TrackEntry>): List<Pair<TrackCategory, List<TrackEntry>>> =
        sort(entries)
            .groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> category.ordinal }
}
