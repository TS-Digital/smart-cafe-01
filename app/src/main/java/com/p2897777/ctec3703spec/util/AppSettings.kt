package com.p2897777.ctec3703spec.util

import android.content.Context
import com.p2897777.ctec3703spec.BuildConfig

/**
 * Device-level settings. Developer mode reveals the staff tools (simulator settings and the
 * staff test console). It is off by default and can never be switched on in a release build,
 * so customers of a real build cannot reach café-side controls.
 */
class AppSettings(context: Context, private val isDebugBuild: Boolean = BuildConfig.DEBUG) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether this build is allowed to offer developer mode at all. */
    val developerModeAvailable: Boolean get() = isDebugBuild

    var developerMode: Boolean
        get() = isDebugBuild && prefs.getBoolean(KEY_DEV, false)
        set(value) { prefs.edit().putBoolean(KEY_DEV, value && isDebugBuild).apply() }

    private companion object {
        const val KEY_DEV = "developer_mode"
    }
}
