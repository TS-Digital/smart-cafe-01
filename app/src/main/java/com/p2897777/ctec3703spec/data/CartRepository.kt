package com.p2897777.ctec3703spec.data

import android.content.ContentValues
import com.p2897777.ctec3703spec.model.CartLine
import com.p2897777.ctec3703spec.util.Validators

/**
 * The cart is persisted per user in SQLite so it survives the app being closed.
 */
class CartRepository(private val db: CafeDatabase, private val products: ProductRepository) {

    sealed class CartResult {
        object Ok : CartResult()
        data class Error(val message: String) : CartResult()
    }

    fun lines(userId: Long): List<CartLine> =
        db.readableDatabase.rawQuery(
            "SELECT p.id, p.name, p.description, p.category, p.price_pence, p.available, p.emoji, p.tags, c.quantity " +
                "FROM cart_items c JOIN products p ON p.id = c.product_id WHERE c.user_id = ? ORDER BY p.name",
            arrayOf(userId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(CartLine(c.toProduct(), c.getInt(8)))
                }
            }
        }

    fun itemCount(userId: Long): Int =
        db.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE user_id = ?", arrayOf(userId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Adds [quantity] of a product, merging with any quantity already in the cart. */
    fun add(userId: Long, productId: Long, quantity: Int = 1): CartResult {
        val product = products.findById(productId) ?: return CartResult.Error("This item no longer exists")
        if (!product.available) return CartResult.Error("${product.name} is currently unavailable")
        val current = quantityOf(userId, productId)
        return setQuantity(userId, productId, current + quantity)
    }

    fun setQuantity(userId: Long, productId: Long, quantity: Int): CartResult {
        if (quantity <= 0) {
            remove(userId, productId)
            return CartResult.Ok
        }
        Validators.quantity(quantity)?.let { return CartResult.Error(it) }
        val cv = ContentValues().apply {
            put("user_id", userId)
            put("product_id", productId)
            put("quantity", quantity)
        }
        db.writableDatabase.insertWithOnConflict(
            "cart_items", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        return CartResult.Ok
    }

    fun remove(userId: Long, productId: Long) {
        db.writableDatabase.delete(
            "cart_items", "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId.toString())
        )
    }

    fun clear(userId: Long) {
        db.writableDatabase.delete("cart_items", "user_id = ?", arrayOf(userId.toString()))
    }

    private fun quantityOf(userId: Long, productId: Long): Int =
        db.readableDatabase.rawQuery(
            "SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?",
            arrayOf(userId.toString(), productId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
}
