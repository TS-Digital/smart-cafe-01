package com.p2897777.ctec3703spec.sim

import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.data.OrderRepository
import com.p2897777.ctec3703spec.data.OrderStatusRules
import com.p2897777.ctec3703spec.data.StatusUpdate
import com.p2897777.ctec3703spec.model.OrderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulates the café/admin side for the individual Customer build (brief section 3.1, note for individuals).
 *
 * Every [tickMillis] it checks all active orders. When an order has been in its current status
 * longer than the dwell time in [OrderStatusRules], it is moved to the next status through the
 * same repository method an admin app would use, so the database, status history and
 * notifications are all real, not faked in the UI.
 */
class OrderSimulator(
    private val orders: OrderRepository,
    private val onUpdate: (StatusUpdate) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile var enabled: Boolean = true
    @Volatile var speedFactor: Double = 1.0

    private var job: Job? = null

    fun start(scope: CoroutineScope, tickMillis: Long = 3_000L) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (enabled) runCatching { tick() }
                delay(tickMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * One pass over active orders, oldest first. Returns the updates made (also used by tests).
     * Like a real café, only [OrderStatusRules.BARISTA_CAPACITY] orders can be in preparation at once,
     * so a busy queue genuinely makes later orders wait longer. That is what the wait-time model learns.
     */
    fun tick(): List<StatusUpdate> {
        val now = clock()
        val active = orders.activeOrders()
        var preparing = active.count { it.status == OrderStatus.PREPARING }
        val updates = mutableListOf<StatusUpdate>()
        for (order in active) {
            val due = OrderStatusRules.isDue(
                order.status, order.updatedAt, now, speedFactor,
                order.itemCount, OrderStatusRules.variationFor(order.id)
            )
            if (!due) continue
            if (order.status == OrderStatus.PENDING && preparing >= OrderStatusRules.BARISTA_CAPACITY) continue
            val next = order.status.next() ?: continue
            val update = orders.updateStatus(order.id, next) ?: continue
            if (next == OrderStatus.PREPARING) preparing++
            updates += update
        }
        if (updates.isNotEmpty()) {
            updates.forEach(onUpdate)
            DataEvents.notifyOrdersChanged()
        }
        return updates
    }

    /** Manual "admin" action from the test console: move one order on immediately. */
    fun advanceNow(orderId: Long): StatusUpdate? {
        val order = orders.findById(orderId) ?: return null
        val next = order.status.next() ?: return null
        val update = orders.updateStatus(orderId, next) ?: return null
        onUpdate(update)
        DataEvents.notifyOrdersChanged()
        return update
    }
}
 