package com.rayoai

import android.app.Application
import com.rayoai.BuildConfig
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.presentation.ui.updates.UpdateInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RayoAIApp : Application() {

    @Inject
    lateinit var updatePreferences: UpdatePreferences

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            UpdateInstaller.maybeCleanupAfterUpdate(this, updatePreferences)
        }
    }
}
