package com.mylittleblover.mkwplayer.i18n

import java.util.Locale

/** Script aliases also cover older Android locale choices (CN/SG and TW/HK/MO). */
internal fun languageKey(languageTag: String): String {
    val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
    return when (locale.language) {
        "zh" -> if (locale.script == "Hant" ||
            (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))) "zh-Hant" else "zh-Hans"
        "" -> "en"
        else -> locale.language
    }
}
