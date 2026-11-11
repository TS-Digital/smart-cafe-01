package com.p2897777.ctec3703spec.util

import android.content.Context

/**
 * Remembers who is logged in. Only the user id and display name are kept,
 * never the password or its hash.
 */
class SessionManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("session", Context.MODE_PRIVATE)

    val userId: Long get() = prefs.getLong(KEY_USER_ID, NO_USER)
    val userName: String get() = prefs.getString(KEY_USER_NAME, "") ?: ""
    val isLoggedIn: Boolean get() = userId != NO_USER

    fun login(userId: Long, name: String) {
        prefs.edit().putLong(KEY_USER_ID, userId).putString(KEY_USER_NAME, name).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val NO_USER = -1L
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
    }
}