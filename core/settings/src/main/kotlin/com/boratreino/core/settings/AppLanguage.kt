package com.boratreino.core.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app's language, picked in Settings. Baianês is Brazilian Portuguese with Bahia's slang
 * (res/values-pt-rBR in each module). Applies to the whole app, notifications included, and is
 * remembered by Android.
 */
enum class AppLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"),
    BAIANES("pt-BR", "Baianês"),
    ;

    companion object {
        /** The chosen one; until someone picks, whatever the phone's language suggests. */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val locale = if (chosen.isEmpty) Locale.getDefault() else chosen[0] ?: Locale.getDefault()
            return if (locale.language == "pt") BAIANES else ENGLISH
        }

        /** Switches the app's language; open screens redraw in it at once. */
        fun choose(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
