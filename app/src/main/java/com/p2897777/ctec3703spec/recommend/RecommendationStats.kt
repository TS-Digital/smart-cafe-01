package com.p2897777.ctec3703spec.recommend

import android.content.Context

/**
 * Measures whether recommendations are useful: how many were shown, and how many the
 * customer added to their cart straight from a recommendation card.
 * Add rate = added / shown. Counts are kept per user on the device.
 */
class RecommendationStats(private val store: Store) {

    data class Totals(val shown: Int, val added: Int) {
        /** Share of shown recommendations that were added, 0.0 to 1.0, or null before any were shown. */
        val addRate: Double? get() = if (shown == 0) null else added.toDouble() / shown
    }

    fun recordShown(userId: Long, count: Int) {
        if (count <= 0) return
        val t = totals(userId)
        store.save(userId, t.copy(shown = t.shown + count))
    }

    fun recordAdded(userId: Long) {
        val t = totals(userId)
        store.save(userId, t.copy(added = t.added + 1))
    }

    fun totals(userId: Long): Totals = store.load(userId)

    interface Store {
        fun load(userId: Long): Totals
        fun save(userId: Long, totals: Totals)
    }

    class MemoryStore : Store {
        private val map = mutableMapOf<Long, Totals>()
        override fun load(userId: Long) = map[userId] ?: Totals(0, 0)
        override fun save(userId: Long, totals: Totals) { map[userId] = totals }
    }

    class PrefsStore(context: Context) : Store {
        private val prefs = context.applicationContext.getSharedPreferences("rec_stats", Context.MODE_PRIVATE)
        override fun load(userId: Long) = Totals(prefs.getInt("$userId.shown", 0), prefs.getInt("$userId.added", 0))
        override fun save(userId: Long, totals: Totals) {
            prefs.edit().putInt("$userId.shown", totals.shown).putInt("$userId.added", totals.added).apply()
        }
    }
}
