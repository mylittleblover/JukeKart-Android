package com.mylittleblover.mkwplayer.pack

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a [PackManifest] back into canonical v1 JSON so the merged internal library
 * manifest passes [PackManifest.parse] and [PackValidation.validate] identically.
 */
internal object PackManifestWriter {

    fun toJson(manifest: PackManifest): String {
        val root = JSONObject()
            .put("formatVersion", manifest.formatVersion)
            .put("packId", manifest.packId)
            .put("packVersion", manifest.packVersion)
            .put("name", manifest.name)
            .put("kind", manifest.kind.name)

        if (manifest.basePackId != null) root.put("basePackId", manifest.basePackId)
        if (manifest.basePackVersion != null) root.put("basePackVersion", manifest.basePackVersion)

        root.put(
            "coverage",
            JSONObject()
                .put("totalLogicalTracks", manifest.coverage.totalLogicalTracks)
                .put("playableLogicalTracks", manifest.coverage.playableLogicalTracks)
                .put("codecKinds", JSONArray(manifest.coverage.codecKinds)),
        )

        val assetsArr = JSONArray()
        manifest.assets.forEach { asset ->
            val obj = JSONObject()
                .put("digest", asset.digest.sha256)
                .put("byteSize", asset.byteSize)
                .put("mediaType", asset.mediaType)
                .put("codecKind", asset.codecKind)
                .put("sampleRate", asset.sampleRate)
                .put("channels", asset.channels)
                .put("frames", asset.frames)
            if (asset.loopStart != null && asset.loopEnd != null) {
                obj.put("loopStart", asset.loopStart)
                obj.put("loopEnd", asset.loopEnd)
            }
            assetsArr.put(obj)
        }
        root.put("assets", assetsArr)

        val tracksArr = JSONArray()
        manifest.tracks.forEach { track ->
            val partsArr = JSONArray()
            track.parts.forEach { part ->
                val p = JSONObject()
                    .put("role", part.role.name)
                    .put("asset", part.asset.sha256)
                if (part.outro != null) p.put("outro", part.outro)
                partsArr.put(p)
            }
            val tObj = JSONObject()
                .put("key", track.key)
                .put("kind", track.kind.name)
                .put("title", track.title)
                .put("primary", track.primary.sha256)
                .put("isFlow", track.isFlow)
                .put("parts", partsArr)

            track.timing?.let { timing ->
                val tmObj = JSONObject()
                    .put("preludeExit", timing.preludeExit.name)
                    .put("bridgeEntry", timing.bridgeEntry.name)
                    .put("outroEntry", timing.outroEntry.name)
                    .put("outroSwitch", timing.outroSwitch.name)
                    .put("mainRepeats", timing.mainRepeats)
                    .put("bridgeFadeOutBeats", timing.bridgeFadeOutBeats)
                    .put("bridgeFadeInBeats", timing.bridgeFadeInBeats)
                tObj.put("timing", tmObj)
            }
            tracksArr.put(tObj)
        }
        root.put("tracks", tracksArr)

        val stagesArr = JSONArray()
        manifest.stages.forEach { stage ->
            stagesArr.put(
                JSONObject()
                    .put("code", stage.code)
                    .put("name", stage.name)
                    .put("tracks", JSONArray(stage.tracks)),
            )
        }
        root.put("stages", stagesArr)

        val provArr = JSONArray()
        manifest.provenance.forEach { prov ->
            val pObj = JSONObject()
                .put("subject", prov.subject)
                .put("field", prov.field)
                .put("level", prov.level.name)
            if (!prov.note.isNullOrBlank()) pObj.put("note", prov.note)
            provArr.put(pObj)
        }
        root.put("provenance", provArr)

        root.put(
            "unlock",
            JSONObject()
                .put("frozen", manifest.unlock.frozen)
                .put("stages", JSONArray(manifest.unlock.stages)),
        )

        return root.toString()
    }
}
