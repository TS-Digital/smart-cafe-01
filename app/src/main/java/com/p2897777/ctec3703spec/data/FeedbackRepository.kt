package com.p2897777.ctec3703spec.data

import android.content.ContentValues
import com.p2897777.ctec3703spec.model.Feedback
import com.p2897777.ctec3703spec.model.OrderStatus
import com.p2897777.ctec3703spec.util.Validators

class FeedbackRepository(
    private val db: CafeDatabase,
    private val orders: OrderRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {

    sealed class Result {
        data class Saved(val id: Long) : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Saves a rating for an order (service feedback) and optionally for one product in it.
     * Only the customer who placed the order can rate it, and only once it is Collected.
     */
    fun submit(userId: Long, orderId: Long, productId: Long?, rating: Int, comment: String): Result {
        Validators.rating(rating)?.let { return Result.Error(it) }
        Validators.comment(comment)?.let { return Result.Error(it) }
        val order = orders.findById(orderId) ?: return Result.Error("Order not found")
        if (order.userId != userId) return Result.Error("You can only rate your own orders")
        if (order.status != OrderStatus.COLLECTED) return Result.Error("You can rate an order once it has been collected")
        if (productId != null && order.items.none { it.productId == productId }) {
            return Result.Error("That item was not part of this order")
        }
        if (existsFor(userId, orderId, productId)) return Result.Error("You have already rated this")
        val id = db.writableDatabase.insertOrThrow("feedback", null, ContentValues().apply {
            put("user_id", userId)
            put("order_id", orderId)
            if (productId != null) put("product_id", productId) else putNull("product_id")
            put("rating", rating)
            put("comment", comment.trim())
            put("created_at", clock())
        })
        return Result.Saved(id)
    }

    fun forOrder(orderId: Long): List<Feedback> =
        db.readableDatabase.rawQuery(
            "SELECT id, user_id, order_id, product_id, rating, comment, created_at FROM feedback WHERE order_id = ? ORDER BY id",
            arrayOf(orderId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Feedback(
                        c.getLong(0), c.getLong(1), c.getLong(2),
                        if (c.isNull(3)) null else c.getLong(3),
                        c.getInt(4), c.getString(5), c.getLong(6)
                    )
                )
            }
        }

    private fun existsFor(userId: Long, orderId: Long, productId: Long?): Boolean {
        val sql = if (productId == null)
            "SELECT 1 FROM feedback WHERE user_id = ? AND order_id = ? AND product_id IS NULL"
        else "SELECT 1 FROM feedback WHERE user_id = ? AND order_id = ? AND product_id = ?"
        val args = listOfNotNull(userId.toString(), orderId.toString(), productId?.toString()).toTypedArray()
        return db.readableDatabase.rawQuery(sql, args).use { it.moveToFirst() }
    }
}
