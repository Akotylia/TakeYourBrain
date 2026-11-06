package com.takeyourbrain.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import com.takeyourbrain.app.R

/**
 * Язык приложения (Android 13+ умеет хранить его сам, отдельно от языка телефона).
 * После смены система сама перезапускает экраны на новом языке.
 */
enum class AppLanguage(val tag: String, @param:StringRes val label: Int) {
    SYSTEM("", R.string.language_system), // как в телефоне
    ENGLISH("en", R.string.language_english),
    RUSSIAN("ru", R.string.language_russian);

    companion object {
        /** Какой язык выбран сейчас. */
        fun current(context: Context): AppLanguage {
            val tag = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            return entries.firstOrNull { it.tag.isNotEmpty() && tag.startsWith(it.tag) } ?: SYSTEM
        }

        fun set(context: Context, language: AppLanguage) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
    }
}
