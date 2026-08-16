package com.alertnotes.core.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.alertnotes.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The languages Alert Notes ships, plus "follow the device". */
enum class AppLanguage(val tag: String?, @param:StringRes val labelRes: Int) {
    /**
     * Resource resolution picks the translation matching the device language,
     * falling back to English. This is the default, and it is exactly the
     * required first-run behaviour: a Lithuanian device gets Lithuanian, every
     * other device gets English — with no code involved.
     */
    SYSTEM(null, R.string.settings_language_system),
    ENGLISH("en", R.string.settings_language_english),
    LITHUANIAN("lt", R.string.settings_language_lithuanian),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}

/**
 * Per-app language, without AppCompat.
 *
 * The androidx backport of `setApplicationLocales` requires AppCompatActivity,
 * and this app's activities are FragmentActivity on a plain Material theme —
 * adopting AppCompat purely for a language switcher would mean re-theming the
 * whole app. So there are two paths:
 *
 * * **API 33+** — the platform [android.app.LocaleManager]. The system stores
 *   the choice, recreates the activities, and shows Alert Notes in the per-app
 *   language screen in Settings (backed by `res/xml/locales_config.xml`).
 * * **API 26–32** — the selection is stored here and applied by wrapping each
 *   activity's base context in [wrap]. Activities recreate themselves after a
 *   change, which is the same thing the platform does above.
 *
 * The preference is kept in SharedPreferences rather than DataStore for one
 * specific reason: `attachBaseContext` runs before anything can suspend, and it
 * needs the answer synchronously. It is a single enum, written on user action
 * only, so the cost of a synchronous read is a non-issue — whereas blocking the
 * main thread on a DataStore read at every activity launch would not be.
 */
@Singleton
class AppLocaleManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val current: AppLanguage
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppLanguage.fromTag(
                context.getSystemService(android.app.LocaleManager::class.java)
                    ?.applicationLocales
                    ?.takeUnless { it.isEmpty }
                    ?.get(0)
                    ?.language,
            )
        } else {
            AppLanguage.fromTag(storedTag(context))
        }

    /**
     * Applies [language]. Returns true when the caller must recreate itself —
     * on API 33+ the platform does that for us, below it we have to.
     */
    fun apply(language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales = language.tag
                ?.let { LocaleList.forLanguageTags(it) }
                ?: LocaleList.getEmptyLocaleList()
            return false
        }
        preferences(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        return true
    }

    companion object {
        private const val PREFS = "app_locale"
        private const val KEY_LANGUAGE = "language_tag"

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun storedTag(context: Context): String? =
            preferences(context).getString(KEY_LANGUAGE, null)

        /**
         * Wraps an activity's base context in the selected locale. Called from
         * `attachBaseContext`, before any resource is resolved.
         *
         * A no-op on API 33+, where the platform has already applied the
         * per-app locale to the context we are handed.
         */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = storedTag(base) ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return base.createConfigurationContext(configuration)
        }
    }
}
