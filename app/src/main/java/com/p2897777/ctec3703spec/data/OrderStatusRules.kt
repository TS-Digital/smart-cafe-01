package com.p2897777.ctec3703spec.data

import com.p2897777.ctec3703spec.model.OrderStatus

/**
 * Business rules for order status changes. Pure Kotlin, so it is unit tested on the JVM.
 *
 * Allowed: Pending -> Preparing -> Ready -> Collected, and Pending -> Cancelled.
 * Not allowed: going backwards, skipping steps, or leaving a final state.
 */
object OrderStatusRules {

    /** How many orders the simulated baristas can prepare at the same time. */
    const val BARISTA_CAPACITY = 2

    const val PENDING_BASE_MS = 10_000L
    const val PREPARING_BASE_MS = 8_000L
    const val PREPARING_PER_ITEM_MS = 6_000L
    const val READY_BASE_MS = 45_000L

    fun canMove(from: OrderStatus, to: OrderStatus): Boolean = when {
        to == OrderStatus.CANCELLED -> from == OrderStatus.PENDING
        else -> from.next() == to
    }

    /**
     * Timing used by the simulated café: how long an order sits in each status before the
     * "kitchen" moves it on. Preparing time grows with the number of items, and [variation]
     * (around 1.0) makes some orders a little quicker or slower, like real baristas.
     * Returns null for statuses that never auto-advance.
     */
    fun dwellMillis(
        status: OrderStatus,
        speedFactor: Double = 1.0,
        itemCount: Int = 1,
        variation: Double = 1.0
    ): Long? {
        val base = when (status) {
            OrderStatus.PENDING -> PENDING_BASE_MS
            OrderStatus.PREPARING -> ((PREPARING_BASE_MS + PREPARING_PER_ITEM_MS * itemCount.coerceAtLeast(1)) * variation).toLong()
            OrderStatus.READY -> READY_BASE_MS
            OrderStatus.COLLECTED, OrderStatus.CANCELLED -> return null
        }
        return (base / speedFactor).toLong().coerceAtLeast(1_000L)
    }

    /** Should an order last updated at [updatedAt] move on at time [now]? */
    fun isDue(
        status: OrderStatus,
        updatedAt: Long,
        now: Long,
        speedFactor: Double = 1.0,
        itemCount: Int = 1,
        variation: Double = 1.0
    ): Boolean {
        val dwell = dwellMillis(status, speedFactor, itemCount, variation) ?: return false
        return now - updatedAt >= dwell
    }

    /**
     * Repeatable per-order variation between 0.8 and 1.2, derived from the order id so the
     * same order always takes the same time (keeps tests deterministic).
     */
    fun variationFor(orderId: Long): Double = 0.8 + ((orderId * 37 + 11) % 41) / 100.0
}
