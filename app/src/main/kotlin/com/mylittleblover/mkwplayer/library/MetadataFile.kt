package com.mylittleblover.mkwplayer.library

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Small user records are committed by rename; an incomplete write never replaces the old record. */
internal object MetadataFile {
    private val locks = ConcurrentHashMap<String, Any>()
    fun lock(file: File): Any = locks.computeIfAbsent(file.absoluteFile.normalize().path) { Any() }
    fun write(file: File, text: String) {
        val parent = requireNotNull(file.absoluteFile.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create the user-record directory" }
        val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
