package com.p2897777.ctec3703spec.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.p2897777.ctec3703spec.model.Order
import com.p2897777.ctec3703spec.model.OrderItem
import com.p2897777.ctec3703spec.model.OrderStatus
import com.p2897777.ctec3703spec.model.StatusChange

sealed class PlaceOrderResult {
    data class Success(val orderId: Long, val totalPence: Int) : PlaceOrderResult()
    data class Failure(val message: String) : PlaceOrderResult()
}

/** One completed order as seen by the wait-time model. */
data class WaitRecord(
    val orderId: Long,
    val itemCount: Int,
    val ordersAhead: Int,
    val actualSeconds: Double,
    val predictedSeconds: Long?
)

/** A status change that actually happened, so the caller can notify the customer. */
data class StatusUpdate(val orderId: Long, val userId: Long, val from: OrderStatus, val to: OrderStatus)

class OrderRepository(
    private val db: CafeDatabase,
    private val cart: CartRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Turns the user's cart into an order inside ONE transaction:
     * either the order, its items, its first status entry and the cart clear all happen, or none do.
     */
    fun placeOrder(
        userId: Long,
        note: String = "",
        /** Optional wait-time predictor (itemCount, ordersAhead) -> seconds, stored for later evaluation. */
        predictor: ((Int, Int) -> Long)? = null
    ): PlaceOrderResult {
        val lines = cart.lines(userId)
        if (lines.isEmpty()) return PlaceOrderResult.Failure("Your cart is empty")
        val unavailable = lines.filter { !it.product.available }
        if (unavailable.isNotEmpty()) {
            return PlaceOrderResult.Failure(
                "Please remove unavailable items: " + unavailable.joinToString { it.product.name }
            )
        }
        val cleanNote = note.trim().take(MAX_NOTE_LENGTH)
        val total = lines.sumOf { it.lineTotalPence }
        val now = clock()
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val ahead = queueSize()
            val itemCount = lines.sumOf { it.quantity }
            val orderId = w.insertOrThrow("orders", null, ContentValues().apply {
                put("user_id", userId)
                put("status", OrderStatus.PENDING.name)
                put("total_pence", total)
                put("note", cleanNote)
                put("created_at", now)
                put("updated_at", now)
                put("queue_ahead", ahead)
                predictor?.let { put("predicted_wait_s", it(itemCount, ahead)) }
            })
            lines.forEach { line ->
                w.insertOrThrow("order_items", null, ContentValues().apply {
                    put("order_id", orderId)
                    put("product_id", line.product.id)
                    put("product_name", line.product.name)
                    put("unit_price_pence", line.product.pricePence)
                    put("quantity", line.quantity)
                })
            }
            insertHistory(w, orderId, OrderStatus.PENDING, now)
            w.delete("cart_items", "user_id = ?", arrayOf(userId.toString()))
            w.setTransactionSuccessful()
            return PlaceOrderResult.Success(orderId, total)
        } catch (e: Exception) {
            return PlaceOrderResult.Failure("Could not place your order. Please try again.")
        } finally {
            w.endTransaction()
        }
    }

    fun ordersFor(userId: Long): List<Order> =
        queryOrders("WHERE user_id = ? ORDER BY created_at DESC, id DESC", arrayOf(userId.toString()))
            .map { it.copy(items = itemsFor(it.id)) }

    /** Every order that is not finished yet, across all customers (used by the simulated café). */
    fun activeOrders(): List<Order> =
        queryOrders(
            "WHERE status NOT IN (?, ?) ORDER BY created_at",
            arrayOf(OrderStatus.COLLECTED.name, OrderStatus.CANCELLED.name)
        ).map { it.copy(items = itemsFor(it.id)) }

    fun findById(orderId: Long): Order? {
        val order = queryOrders("WHERE id = ?", arrayOf(orderId.toString())).firstOrNull() ?: return null
        return order.copy(items = itemsFor(orderId))
    }

    fun itemsFor(orderId: Long): List<OrderItem> =
        db.readableDatabase.rawQuery(
            "SELECT product_id, product_name, unit_price_pence, quantity FROM order_items WHERE order_id = ? ORDER BY id",
            arrayOf(orderId.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(OrderItem(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }

    fun history(orderId: Long): List<StatusChange> =
        db.readableDatabase.rawQuery(
            "SELECT status, changed_at FROM order_status_history WHERE order_id = ? ORDER BY changed_at, id",
            arrayOf(orderId.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(StatusChange(OrderStatus.fromName(c.getString(0)), c.getLong(1))) }
        }

    /**
     * Moves an order to [to] only if that is a legal step from its current status.
     * Returns the change made, or null if the move was not allowed.
     */
    fun updateStatus(orderId: Long, to: OrderStatus): StatusUpdate? {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val current = queryOrders("WHERE id = ?", arrayOf(orderId.toString())).firstOrNull() ?: return null
            if (!OrderStatusRules.canMove(current.status, to)) return null
            val now = clock()
            // The WHERE on the old status guards against two updates racing each other
            val rows = w.update(
                "orders",
                ContentValues().apply { put("status", to.name); put("updated_at", now) },
                "id = ? AND status = ?",
                arrayOf(orderId.toString(), current.status.name)
            )
            if (rows != 1) return null
            insertHistory(w, orderId, to, now)
            w.setTransactionSuccessful()
            return StatusUpdate(orderId, current.userId, current.status, to)
        } finally {
            w.endTransaction()
        }
    }

    /** Customer cancellation, only allowed while the order is still Pending and belongs to them. */
    fun cancel(orderId: Long, userId: Long): StatusUpdate? {
        val order = findById(orderId) ?: return null
        if (order.userId != userId || !order.status.canCustomerCancel) return null
        return updateStatus(orderId, OrderStatus.CANCELLED)
    }

    /** Product ids with how many times this user has ordered them (for recommendations). */
    fun productCountsFor(userId: Long): Map<Long, Int> =
        db.readableDatabase.rawQuery(
            "SELECT oi.product_id, SUM(oi.quantity) FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                "WHERE o.user_id = ? AND o.status != ? GROUP BY oi.product_id",
            arrayOf(userId.toString(), OrderStatus.CANCELLED.name)
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    /** Product id sets per order across all customers (for "often ordered together"). */
    fun basketsAllUsers(): List<Set<Long>> =
        db.readableDatabase.rawQuery(
            "SELECT oi.order_id, oi.product_id FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                "WHERE o.status != ? ORDER BY oi.order_id",
            arrayOf(OrderStatus.CANCELLED.name)
        ).use { c ->
            val map = linkedMapOf<Long, MutableSet<Long>>()
            while (c.moveToNext()) map.getOrPut(c.getLong(0)) { mutableSetOf() }.add(c.getLong(1))
            map.values.toList()
        }

    /** Orders the kitchen still has to finish (waiting or being prepared), across all customers. */
    fun queueSize(): Int =
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM orders WHERE status IN (?, ?)",
            arrayOf(OrderStatus.PENDING.name, OrderStatus.PREPARING.name)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** When an order first reached Ready, or null if it hasn't yet. */
    fun readyAt(orderId: Long): Long? =
        db.readableDatabase.rawQuery(
            "SELECT MIN(changed_at) FROM order_status_history WHERE order_id = ? AND status = ?",
            arrayOf(orderId.toString(), OrderStatus.READY.name)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    /**
     * Training data for the wait-time model: every order that reached Ready, with its item count,
     * queue size at checkout and actual seconds from placing to Ready. Also returns the stored
     * prediction (if any) so the model's real-world accuracy can be measured.
     */
    fun waitTimeHistory(): List<WaitRecord> =
        db.readableDatabase.rawQuery(
            "SELECT o.id, o.queue_ahead, o.predicted_wait_s, o.created_at, " +
                "(SELECT MIN(h.changed_at) FROM order_status_history h WHERE h.order_id = o.id AND h.status = ?) AS ready_at, " +
                "(SELECT SUM(i.quantity) FROM order_items i WHERE i.order_id = o.id) AS items " +
                "FROM orders o WHERE ready_at IS NOT NULL ORDER BY o.created_at",
            arrayOf(OrderStatus.READY.name)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    WaitRecord(
                        orderId = c.getLong(0),
                        ordersAhead = c.getInt(1),
                        predictedSeconds = if (c.isNull(2)) null else c.getLong(2),
                        actualSeconds = (c.getLong(4) - c.getLong(3)) / 1000.0,
                        itemCount = c.getInt(5)
                    )
                )
            }
        }

    private fun queryOrders(whereAndOrder: String, args: Array<String>): List<Order> =
        db.readableDatabase.rawQuery(
            "SELECT id, user_id, status, total_pence, note, created_at, updated_at, queue_ahead, predicted_wait_s " +
                "FROM orders $whereAndOrder", args
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Order(
                        id = c.getLong(0), userId = c.getLong(1), status = OrderStatus.fromName(c.getString(2)),
                        totalPence = c.getInt(3), note = c.getString(4), createdAt = c.getLong(5), updatedAt = c.getLong(6),
                        queueAhead = c.getInt(7), predictedWaitSeconds = if (c.isNull(8)) null else c.getLong(8)
                    )
                )
            }
        }

    private fun insertHistory(w: SQLiteDatabase, orderId: Long, status: OrderStatus, at: Long) {
        w.insertOrThrow("order_status_history", null, ContentValues().apply {
            put("order_id", orderId)
            put("status", status.name)
            put("changed_at", at)
        })
    }

    companion object {
        const val MAX_NOTE_LENGTH = 140
    }
}
