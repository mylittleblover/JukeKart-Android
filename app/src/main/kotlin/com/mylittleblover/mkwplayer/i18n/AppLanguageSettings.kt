package com.mylittleblover.mkwplayer.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

internal object AppLanguageSettings {
    private const val FILE = "mkw-app-language"
    private const val LANGUAGE = "language-tag"
    private const val MIGRATED = "platform-locale-migrated"
    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= 33) {
        AppLanguage.fromTag(platformTags(context))
    } else AppLanguage.fromStoredTag(preferences(context).getString(LANGUAGE, null))

    /** Migrates a pre-33 choice once; never overwrites a later system-settings decision. */
    fun migratePlatformChoice(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = preferences(context)
        val tags = platformTags(context)
        if (!prefs.getBoolean(MIGRATED, false)) {
            val legacy = AppLanguage.fromStoredTag(prefs.getString(LANGUAGE, null))
            prefs.edit().putBoolean(MIGRATED, true).apply()
            if (tags.isEmpty() && legacy != AppLanguage.SYSTEM) setPlatformTags(context, legacy.tag)
        }
        prefs.edit().putString(LANGUAGE, current(context).tag).apply()
    }

    fun select(context: Context, language: AppLanguage) {
        if (current(context) == language) return
        preferences(context).edit().putString(LANGUAGE, language.tag).apply()
        if (Build.VERSION.SDK_INT >= 33) setPlatformTags(context, language.tag)
        else context.activity()?.recreate()
    }

    fun wrapLegacy(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val choice = AppLanguage.fromStoredTag(preferences(base).getString(LANGUAGE, null))
        if (choice == AppLanguage.SYSTEM) return base
        val locale = Locale.forLanguageTag(choice.tag)
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale)); setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    @RequiresApi(33)
    private fun platformTags(context: Context): String = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    @RequiresApi(33)
    private fun setPlatformTags(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }
    private fun Context.activity(): Activity? {
        var value = this
        while (value is ContextWrapper) {
            if (value is Activity) return value
            value = value.baseContext
        }
        return value as? Activity
    }
}
