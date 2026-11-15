package com.mylittleblover.mkwplayer.library

import org.json.JSONObject

/**
 * The stage-to-stage connections the game itself defines.
 *
 * Exported from `Stage_Connections_Final.csv`: 214 rows, 29 source stages, 30 destination stages. Each row
 * is one inter-stage route with its own kind (`InterStageEx1` / `InterStageEx2` / `LongRally`), race rule,
 * starting type and traffic density.
 *
 * Two facts about this data are load-bearing and are preserved rather than tidied:
 *
 * - **It is directed.** 214 rows are 214 distinct ordered pairs; there is not one reverse duplicate. A
 *   route from A to B says nothing about B to A, so the app never reverses an edge to fill a gap.
 * - **Rainbow is a sink.** It appears 30 times as a destination and never as a source. That is the data
 *   saying the world tour ends at Rainbow, not a missing row, so [outgoing] returns empty for it and the
 *   UI treats "no outgoing connection" as a real answer.
 */
internal object StageConnections {

    /** One directed connection, exactly as the export states it. */
    data class Edge(
        val from: String,
        val to: String,
        val kind: String,
        val rule: String,
        val start: String,
        val traffic: String,
        val rowId: String,
        val displayName: String,
    )

    data class Catalog(
        /** Every edge, ordered by source then destination. */
        val edges: List<Edge>,
        /** The sentence that says where this came from. */
        val provenanceNote: String,
    ) {
        init {
            require(edges.isNotEmpty()) { "a connection table with no edges is not a catalog" }
            // A duplicate ordered pair would make `between(a,b)` ambiguous and a drawn line ambiguous too.
            require(edges.map { it.from to it.to }.toSet().size == edges.size) { "duplicate stage pair" }
        }

        private val bySource: Map<String, List<Edge>> = edges.groupBy { it.from }

        /** Stages this one can lead to. Empty for a sink such as Rainbow, which is a real answer. */
        fun outgoing(from: String): List<Edge> = bySource[normalise(from)].orEmpty()

        /** Every edge that ends at [to]. */
        fun incoming(to: String): List<Edge> = edges.filter { normalise(it.to) == normalise(to) }

        /** The connection from [from] to [to], when the game defines one in that direction. */
        fun between(from: String, to: String): Edge? =
            outgoing(from).firstOrNull { normalise(it.to) == normalise(to) }

        /** True when the game defines [from] to [to]. Never true just because it defines the reverse. */
        fun connects(from: String, to: String): Boolean = between(from, to) != null

        /** Source stages that have at least one outgoing connection. */
        fun sources(): List<String> = bySource.keys.sorted()

        /** Stages that only ever appear as destinations, sorted so the answer is stable. */
        fun sinks(): List<String> =
            (edges.map { normalise(it.to) }.toSet() - bySource.keys).sorted()

        /**
         * The stages [from] reaches directly.
         *
         * This is what the map draws when a course is selected: the real neighbours, not a nearest-distance
         * guess. A stage with no outgoing edges returns empty and the map draws nothing.
         */
        fun neighbours(from: String): List<String> = outgoing(from).map { normalise(it.to) }

        /** The archive spells a stage `AirshipFortress_00`; the export spells it `AirshipFortress`. */
        private fun normalise(code: String): String = code.trim().removeSuffix("_00")
    }

    /** Parses the bundled asset. Null on anything unusable, so the caller can run without connections. */
    fun parse(text: String): Catalog? = runCatching {
        val root = JSONObject(text)
        val array = root.optJSONArray("edges") ?: return@runCatching null
        val edges = ArrayList<Edge>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val from = item.optString("from")
            val to = item.optString("to")
            // An edge missing an endpoint is not a connection; skipping it is better than inventing one.
            if (from.isBlank() || to.isBlank()) continue
            edges += Edge(
                from = from,
                to = to,
                kind = item.optString("kind"),
                rule = item.optString("rule"),
                start = item.optString("start"),
                traffic = item.optString("traffic"),
                rowId = item.optString("rowId"),
                displayName = item.optString("displayName"),
            )
        }
        if (edges.isEmpty()) return@runCatching null
        Catalog(
            edges = edges.sortedWith(compareBy({ it.from }, { it.to }, { it.kind })),
            provenanceNote = root.optString("provenanceNote"),
        )
    }.getOrNull()
}
