package com.mylittleblover.mkwplayer.i18n

import com.mylittleblover.mkwplayer.R

/** One configuration point for supported app languages; tags never become music identities. */
internal enum class AppLanguage(val tag: String, val labelResource: Int) {
    SYSTEM("", R.string.language_follow_system),
    SIMPLIFIED_CHINESE("zh-Hans", R.string.language_zh_hans_native),
    TRADITIONAL_CHINESE("zh-Hant", R.string.language_zh_hant_native),
    ENGLISH("en", R.string.language_en_native),
    JAPANESE("ja", R.string.language_ja_native);

    companion object {
        fun fromTag(tag: String): AppLanguage {
            if (tag.isBlank()) return SYSTEM
            return entries.firstOrNull { it.tag == languageKey(tag.substringBefore(',')) } ?: ENGLISH
        }
        fun fromStoredTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.tag == languageKey(tag) } ?: SYSTEM
        }
    }
}
