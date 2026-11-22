package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery

/**
 * Decides which stages count toward the Special Cup.
 *
 * This is a deliberate, documented choice, not a discovered fact. No L3 evidence establishes which
 * tracks the game requires for its hidden cup, so this app uses a rule it can defend: every stage the
 * user has music for must be listened to. That is stricter than "any N tracks", cannot be gamed by
 * having a small library, and does not invent a list of stage names.
 *
 * The rule is labelled PROVISIONAL wherever it is shown, for the same reason the section timings are:
 * presenting a guess as the game's rule would be the worse failure.
 */
internal object SpecialCupRules {

    /** Provenance marker for the eligibility rule. */
    enum class RuleProvenance { PROVISIONAL }

    /**
     * The eligibility rule in force.
     *
     * A single constant rather than a function so callers cannot quietly substitute a different rule
     * and still present it as the same feature.
     */
    const val RULE = "every stage you have"

    val provenance: RuleProvenance get() = RuleProvenance.PROVISIONAL

    /**
     * Eligible stages, from the file names the user has.
     *
     * Takes names rather than tracks so it accepts both the database and a folder scan; both end up
     * as `BGM_Stage_*` prefixes via the shared discovery rule.
     */
    val EXCLUDED_STAGES: Set<String> = setOf("Rainbow", "BigTree", "SFCMarioCircuit")

    private val PATTERN_SUFFIX_REGEX = Regex("_Pattern\\d+$", RegexOption.IGNORE_CASE)
    private val SFC_SUFFIX_REGEX = Regex("_SFC$", RegexOption.IGNORE_CASE)
    private val ZERO_ZERO_SUFFIX_REGEX = Regex("_00$", RegexOption.IGNORE_CASE)

    /**
     * Normalizes a stage identifier or file stem to its logical stage code
     * (e.g., `BGM_Stage_AirshipFortress_00` -> `AirshipFortress`,
     * `Cinema_SFC` -> `Cinema`, `SnowMountain_Pattern2` -> `SnowMountain`).
     */
    fun normalizeStageCode(raw: String): String {
        var code = raw.trim()
        if (code.isEmpty()) return ""
        if (code.endsWith(".bwav", ignoreCase = true) ||
            code.contains("_Pre", ignoreCase = true) ||
            code.contains("_Bridge", ignoreCase = true) ||
            code.contains("_Outro", ignoreCase = true) ||
            code.contains("_Fast", ignoreCase = true)
        ) {
            code = StageFlowDiscovery.prefixOf(code)
        }
        if (code.startsWith("BGM_Stage_", ignoreCase = true)) {
            code = code.substring("BGM_Stage_".length)
        }
        while (true) {
            val next = code
                .replace(PATTERN_SUFFIX_REGEX, "")
                .replace(SFC_SUFFIX_REGEX, "")
                .replace(ZERO_ZERO_SUFFIX_REGEX, "")
            if (next == code) break
            code = next
        }
        return code
    }

    fun isExcludedFromUnlock(stageCode: String): Boolean {
        val clean = normalizeStageCode(stageCode)
        return EXCLUDED_STAGES.any { it.equals(clean, ignoreCase = true) }
    }

    fun eligibleStages(fileNames: Collection<String>): Set<String> =
        fileNames
            .filterNot { it.isBlank() }
            .map { StageFlowDiscovery.prefixOf(it) }
            .filter { it.isNotEmpty() && !isExcludedFromUnlock(it) }
            .toSet()

    /** One line for the screen, so the rule is visible where the progress is shown. */
    fun describe(eligibleCount: Int): String =
        "Provisional rule ($RULE): $eligibleCount stage(s) to listen to. " +
            "The game's own requirement is not established."
}
