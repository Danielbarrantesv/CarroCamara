package com.danielbarrantesv.dashcam.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Forces a locale on any Context by wrapping its Resources configuration.
 *
 * This is the most reliable way to apply a per-app language on Android 12 and below,
 * where AppCompatDelegate.setApplicationLocales() may not propagate to all resources
 * (especially Compose `stringResource()` reads that cache the context).
 *
 * On Android 13+, AppCompatDelegate uses LocaleManager under the hood, but we still
 * wrap the context in attachBaseContext() so that resources read during the very first
 * inflate (before the locale has fully propagated) come out in the right language.
 */
object LocaleHelper {

    /**
     * Wraps [context] so that all resource reads use the given [languageTag].
     * If languageTag is null or empty, returns the original context.
     */
    fun wrap(context: Context, languageTag: String?): Context {
        if (languageTag.isNullOrBlank()) return context
        val locale = parseLocale(languageTag) ?: return context
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        // Also set the LocaleList so that all locales (not just the primary)
        // are aware of the change — needed for some OEM ROMs.
        val localeList = LocaleList(locale)
        config.setLocales(localeList)

        return LocaleContextWrapper(context.createConfigurationContext(config), locale)
    }

    /**
     * Applies the persisted language at app process start.
     * Called from Application.attachBaseContext().
     */
    fun applyAtStartup(base: Context, languageTag: String?): Context {
        // Also set AppCompatDelegate so that AppCompatActivity picks it up.
        if (!languageTag.isNullOrBlank()) {
            try {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(languageTag)
                )
            } catch (_: Exception) {}
        }
        return wrap(base, languageTag)
    }

    /**
     * Persists the locale change and returns immediately. The caller is responsible
     * for recreating the Activity so the new locale takes effect.
     *
     * On Android 13+, also uses LocaleManager to update the system-level per-app
     * language preference.
     */
    fun applyLanguage(context: Context, languageTag: String) {
        // 1. AppCompatDelegate — works on Android 13+ via LocaleManager, and on
        //    12 and below via the AppCompat internal configuration override.
        try {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag)
            )
        } catch (_: Exception) {}

        // 2. On Android 13+, also use the platform LocaleManager API directly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                context.getSystemService(android.app.LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.forLanguageTags(languageTag)
            } catch (_: Exception) {}
        }

        // 3. Update the default Locale so any Java/Kotlin code that reads
        //    Locale.getDefault() (e.g. String.format) uses the new language.
        val locale = parseLocale(languageTag)
        if (locale != null) {
            Locale.setDefault(locale)
        }
    }

    private fun parseLocale(tag: String): Locale? {
        return try {
            // Handle both "es" and "es-ES" style tags.
            val parts = tag.split("-")
            when (parts.size) {
                1 -> Locale(parts[0])
                2 -> Locale(parts[0], parts[1])
                3 -> Locale(parts[0], parts[1], parts[2])
                else -> Locale(parts[0])
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * ContextWrapper that holds the active locale so we can re-apply it on
 * subsequent attachBaseContext calls.
 */
private class LocaleContextWrapper(
    base: Context,
    private val locale: Locale
) : ContextWrapper(base) {

    override fun attachBaseContext(newBase: Context) {
        // Re-wrap so that any further attachBaseContext chain keeps the locale.
        val wrapped = LocaleHelper.wrap(newBase, locale.toLanguageTag())
        super.attachBaseContext(wrapped)
    }
}
