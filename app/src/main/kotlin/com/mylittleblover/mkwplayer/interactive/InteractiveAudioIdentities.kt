package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlow
import org.json.JSONObject
import java.security.MessageDigest

/** Content identity survives the pack's translated titles and digest-based cache filenames. */
internal class InteractiveAudioIdentities private constructor(private val rows: Map<String,Identity>) {
    data class Identity(val stage: String,val role: String,val fileName: String) {
        fun belongsToCourse(code: String): Boolean = stage.equals(code, true) ||
            stage.startsWith("${code}_Pattern", true) && stage.substring(code.length + 8).toIntOrNull()?.let { it in 2..5 } == true
    }
    data class Resolved(val flow: StageFlow,val bytes: Map<String,ByteArray>,val title: String=flow.name)
    fun identityForDigest(sha256: String): Identity? = rows[sha256]
    fun resolve(flow: StageFlow,bytes: Map<String,ByteArray>): Resolved? {
        fun identity(data: ByteArray)=rows[MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }]
        val main=flow.part(StageFlow.Role.Main) ?: return null
        val owner=identity(bytes.getValue(main.fileName)) ?: return null
        if(owner.role!="Main" || owner.stage=="Rainbow") return null
        val canonical=linkedMapOf<String,ByteArray>()
        val base=owner.stage.replace(Regex("_Pattern\\d+$"),"")
        val parts=flow.parts.map { part ->
            val data=bytes.getValue(part.fileName)
            val row=identity(data) ?: error("Unknown source identity for ${part.fileName}")
            require(row.role==part.role.name) { "Pack role disagrees with source identity for ${part.fileName}" }
            require(row.stage.replace(Regex("_Pattern\\d+$"),"")==base) { "Pack mixes different stage source identities" }
            require(row.fileName !in canonical) { "Duplicate source identity in pack" }
            canonical[row.fileName]=data
            part.copy(fileName=row.fileName)
        }
        return Resolved(flow.copy(name="BGM_Stage_${owner.stage}",parts=parts),canonical,flow.name)
    }
    companion object {
        fun parse(text: String): InteractiveAudioIdentities {
            val root=JSONObject(text);require(root.getInt("version")==1)
            val list=root.getJSONArray("entries")
            val pairs=(0 until list.length()).map { i -> list.getJSONObject(i).let {
                it.getString("sha256") to Identity(it.getString("stage"),it.getString("role"),it.getString("fileName"))
            } }
            require(pairs.map { it.first }.distinct().size==pairs.size)
            return InteractiveAudioIdentities(pairs.toMap())
        }
    }
}
