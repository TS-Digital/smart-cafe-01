package com.p2897777.ctec3703spec.util

import android.content.Context

/**
 * Slows down password guessing: after [maxAttempts] failed logins for the same email,
 * that email is locked for [lockMillis]. Unknown emails are counted the same way as real
 * ones, so the lockout does not reveal which emails have accounts.
 */
class LoginRateLimiter(
    private val store: AttemptStore,
    private val clock: () -> Long = System::currentTimeMillis,
    val maxAttempts: Int = 5,
    val lockMillis: Long = 60_000L
) {
    /** Milliseconds until [email] may try again, or 0 if it is not locked. */
    fun remainingLockMillis(email: String): Long {
        val state = store.get(key(email)) ?: return 0L
        return (state.lockedUntil - clock()).coerceAtLeast(0L)
    }

    /** Records a failed attempt. Returns true if this failure has just locked the email. */
    fun recordFailure(email: String): Boolean {
        val k = key(email)
        val failures = (store.get(k)?.failures ?: 0) + 1
        return if (failures >= maxAttempts) {
            store.put(k, AttemptState(0, clock() + lockMillis))
            true
        } else {
            store.put(k, AttemptState(failures, 0L))
            false
        }
    }

    fun recordSuccess(email: String) = store.clear(key(email))

    private fun key(email: String) = Validators.normaliseEmail(email)

    data class AttemptState(val failures: Int, val lockedUntil: Long)

    interface AttemptStore {
        fun get(key: String): AttemptState?
        fun put(key: String, state: AttemptState)
        fun clear(key: String)
    }

    /** Used by tests and as a default: forgets everything when the app closes. */
    class MemoryStore : AttemptStore {
        private val map = mutableMapOf<String, AttemptState>()
        override fun get(key: String) = map[key]
        override fun put(key: String, state: AttemptState) { map[key] = state }
        override fun clear(key: String) { map.remove(key) }
    }

    /** Kept in SharedPreferences so closing and reopening the app does not reset a lock. */
    class PrefsStore(context: Context) : AttemptStore {
        private val prefs = context.applicationContext.getSharedPreferences("login_attempts", Context.MODE_PRIVATE)

        override fun get(key: String): AttemptState? {
            if (!prefs.contains("$key.f")) return null
            return AttemptState(prefs.getInt("$key.f", 0), prefs.getLong("$key.l", 0L))
        }

        override fun put(key: String, state: AttemptState) {
            prefs.edit().putInt("$key.f", state.failures).putLong("$key.l", state.lockedUntil).apply()
        }

        override fun clear(key: String) {
            prefs.edit().remove("$key.f").remove("$key.l").apply()
        }
    }
}
  