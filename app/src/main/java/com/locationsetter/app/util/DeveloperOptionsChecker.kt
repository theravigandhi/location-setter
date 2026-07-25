package com.locationsetter.app.util

import android.content.Context
import android.provider.Settings

object DeveloperOptionsChecker {

    /**
     * DEVELOPMENT_SETTINGS_ENABLED lives in Settings.Global, which (unlike Settings.Secure)
     * has no read restriction for third-party apps, so this is a reliable, permission-free check.
     */
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }
}
