package com.mylittleblover.mkwplayer.i18n

import kotlin.test.*

class AppLanguageTest {
    @Test fun configurationOffersSystemAndTheFourRequestedLanguages() {
        assertEquals(listOf("", "zh-Hans", "zh-Hant", "en", "ja"), AppLanguage.entries.map { it.tag })
        for (language in AppLanguage.entries) assertEquals(language, AppLanguage.fromStoredTag(language.tag))
    }
    @Test fun chineseRegionAliasesResolveToTheCorrectScript() {
        for (tag in listOf("zh-CN", "zh-SG", "zh_Hans", "zh-Hans-TW")) assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag(tag))
        for (tag in listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant-CN")) assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromTag(tag))
    }
    @Test fun platformRegionalVariantsUseTheirSupportedLanguageAndUnknownOverridesFallBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTag("ja-JP"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("fr-FR"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    }
    @Test fun damagedLegacyPreferencesFallBackToSystemWithoutInventingANewChoice() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStoredTag("not-a-supported-language"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStoredTag(null))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromStoredTag("zh_HK"))
    }
}
