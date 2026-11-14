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
    HOME("Home", 0),
    MEDIA("Media", 1),
    WORLD_MAP("Map", 2),
    PLAYLISTS("Playlists", 3),
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
        fun default(): Tab = HOME
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
    private val previous: List<Detail> = emptyList(),
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
        data class Cup(val id: String) : Detail
        data class Interactive(val code: String) : Detail

        data object Settings : Detail
    }

    /** True when back would leave the detail rather than the tab. */
    val canGoBack: Boolean get() = detail != null

    fun select(next: Tab): NavState =
        NavState(tab = next, detail = null)

    fun open(next: Detail): NavState = NavState(tab = tab, detail = next, previous = previous + listOfNotNull(detail))

    /**
     * Handles back.
     *
     * Returns null when there is nothing left to close, which is the signal to let the system finish
     * the activity. Returning `this` instead would trap the user in the app.
     */
    fun back(): NavState? = if (detail != null) NavState(tab, previous.lastOrNull(), previous.dropLast(if (previous.isEmpty()) 0 else 1)) else null
    /** Stable identity snapshot; translated labels are never saved as navigation keys. */
    fun snapshot(): List<String> = listOf(tab.name, "stack-v2") + (previous + listOfNotNull(detail)).flatMap { page ->
        when (page) {
            is Detail.Course -> listOf("course", page.code)
            Detail.Flow -> listOf("flow", "")
            is Detail.Cup -> listOf("cup", page.id)
            is Detail.Interactive -> listOf("interactive", page.code)
            Detail.Settings -> listOf("settings", "")
        }
    }

    companion object {
        fun restore(state: List<String>): NavState {
            val tab = when (state.firstOrNull()) {
                "LIBRARY" -> Tab.MEDIA
                "MY_ROUTES" -> Tab.PLAYLISTS
                "INTERACTIVE" -> Tab.MEDIA
                else -> Tab.entries.firstOrNull { it.name == state.firstOrNull() } ?: return NavState()
            }
            fun page(kind: String?, value: String?): Detail? = when (kind) {
                "course" -> value?.takeIf { it.isNotBlank() }?.let(Detail::Course)
                "flow" -> Detail.Flow
                "cup" -> value?.takeIf { it.isNotBlank() }?.let(Detail::Cup)
                "interactive" -> value?.takeIf { it.isNotBlank() }?.let(Detail::Interactive)
                "settings" -> Detail.Settings
                else -> null
            }
            val stack = if (state.getOrNull(1) == "stack-v2") {
                state.drop(2).chunked(2).mapNotNull { page(it.firstOrNull(), it.getOrNull(1)) }
            } else listOfNotNull(page(state.getOrNull(1), state.getOrNull(2)))
            val restored = if (state.firstOrNull() == "INTERACTIVE" && stack.isEmpty()) listOf(Detail.Flow) else stack
            return NavState(tab, restored.lastOrNull(), restored.dropLast(if (restored.isEmpty()) 0 else 1))
        }
    }
}
