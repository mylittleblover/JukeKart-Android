package com.mylittleblover.mkwplayer.ui

/**
 * The app's four top-level destinations.
 *
 * The v3.0 navigation contract names `Library / WorldMap / Interactive / MyRoutes` and marks the
 * ordering as a *recommendation* pending user review. That status is recorded here rather than
 * assumed settled: the order can change without the screens changing.
 *
 * Every other page in the 21-page contract is reached from one of these, which is why there are four
 * and not twenty-one. A flat list of twenty-one top-level tabs would be unusable and would not match
 * the contract either.
 */
internal enum class Tab(
    val label: String,
    /** Where this destination sits, from the contract. */
    val order: Int,
) {
    LIBRARY("Library", 0),
    WORLD_MAP("Map", 1),
    INTERACTIVE("Interactive", 2),
    MY_ROUTES("Routes", 3),
    ;

    companion object {
        /** Tabs in display order. */
        fun ordered(): List<Tab> = entries.sortedBy { it.order }

        /**
         * The tab a back press should return to, or null at a root tab.
         *
         * Back from a root tab exits, which is what Android users expect; back from a detail page
         * returns to its tab. Keeping the rule here means every call site agrees.
         */
        fun default(): Tab = LIBRARY
    }
}

/**
 * Navigation state: which tab is showing, and what is open inside it.
 *
 * Deliberately plain Kotlin with no Compose types, so the back-stack behaviour is testable without a
 * device. Back handling is where navigation bugs live, and "the back button went somewhere odd" is not
 * something a screenshot can catch.
 */
internal class NavState(
    val tab: Tab = Tab.default(),
    /** The page open above the tab, or null at the tab root. */
    val detail: Detail? = null,
) {

    /**
     * A page reached from a tab.
     *
     * Only pages that exist are listed. A `Detail` variant with no screen behind it would be a
     * placeholder that looks like a feature, which the contract explicitly forbids.
     */
    sealed interface Detail {
        /** A course's detail page, identified by the export's own course code. */
        data class Course(val code: String) : Detail

        /** The flow currently loaded, with its sections. */
        data object Flow : Detail

        data object Settings : Detail
    }

    /** True when back would leave the detail rather than the tab. */
    val canGoBack: Boolean get() = detail != null

    fun select(next: Tab): NavState =
        if (next == tab) this else NavState(tab = next, detail = null)

    fun open(next: Detail): NavState = NavState(tab = tab, detail = next)

    /**
     * Handles back.
     *
     * Returns null when there is nothing left to close, which is the signal to let the system finish
     * the activity. Returning `this` instead would trap the user in the app.
     */
    fun back(): NavState? = if (detail != null) NavState(tab, detail = null) else null
    /** Stable identity snapshot; translated labels are never saved as navigation keys. */
    fun snapshot(): List<String> = when (val page = detail) {
        null -> listOf(tab.name, "root")
        is Detail.Course -> listOf(tab.name, "course", page.code)
        Detail.Flow -> listOf(tab.name, "flow")
        Detail.Settings -> listOf(tab.name, "settings")
    }

    companion object {
        fun restore(state: List<String>): NavState {
            val tab = Tab.entries.firstOrNull { it.name == state.firstOrNull() } ?: return NavState()
            val detail = when (state.getOrNull(1)) {
                "course" -> state.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(Detail::Course)
                "flow" -> Detail.Flow
                "settings" -> Detail.Settings
                else -> null
            }
            return NavState(tab, detail)
        }
    }

}
