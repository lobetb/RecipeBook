package com.blobet.recipebook.ui.tutorial

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorialPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTutorialNeeded(): Boolean {
        val completed = prefs.getBoolean(KEY_COMPLETED, false)
        val savedVersion = prefs.getInt(KEY_VERSION, 0)
        return !completed || savedVersion < TUTORIAL_VERSION
    }

    fun markCompleted() {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putInt(KEY_VERSION, TUTORIAL_VERSION)
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "tutorial_prefs"
        private const val KEY_COMPLETED = "tutorial_completed"
        private const val KEY_VERSION = "tutorial_version"
        private const val TUTORIAL_VERSION = 2
    }
}
