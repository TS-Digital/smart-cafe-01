package com.p2897777.ctec3703spec.sim


import android.content.ContentValues
import com.p2897777.ctec3703spec.data.CafeDatabase
import com.p2897777.ctec3703spec.data.OrderStatusRules
import com.p2897777.ctec3703spec.data.ProductRepository
import com.p2897777.ctec3703spec.model.OrderStatus
import com.p2897777.ctec3703spec.util.PasswordHasher
import java.util.UUID
import kotlin.random.Random

/**
 * Creates clearly-labelled DEMO order history from a separate "Demo Customer" account, so the
 * recommendation engine and the wait-time model have data to learn from in a fresh install.
 *
 * Timings follow the same café rules the live simulator uses (base time + time per item,
 * a queue with limited barista capacity, per-order variation), so what the model learns from
 * demo data matches how live orders behave. Common pairings (e.g. flat white + croissant)
 * are built in so basket analysis has patterns to find.
 */
class DemoDataGenerator(
    private val db: CafeDatabase,
    private val products: ProductRepository,
    private val random: Random = Random(2897777),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val pairings = listOf(
        listOf("Flat White", "Butter Croissant"),
        listOf("Cappuccino", "Carrot Cake"),
        listOf("Tomato Soup", "Cheese Toastie"),
        listOf("Hot Chocolate", "Chocolate Fudge Cake"),
        listOf("Iced Latte", "Lemon Drizzle"),
        listOf("English Breakfast Tea", "Victoria Sponge")
    )

    /** Inserts [count] completed demo orders spread over the last week. Returns how many were added. */
    fun generate(count: Int = 25): Int {
        val menu = products.search(availableOnly = true)
        if (menu.isEmpty()) return 0
        val byName = menu.associateBy { it.name }
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val userId = demoUserId()
            val now = clock()
            repeat(count) { n ->
                // Basket: usually a known pairing, sometimes random extras
                val basket = mutableMapOf<Long, Int>()
                if (random.nextDouble() < 0.7) {
                    pairings[random.nextInt(pairings.size)].mapNotNull { byName[it] }.forEach { basket[it.id] = 1 }
                }
                repeat(random.nextInt(0, 3)) { menu[random.nextInt(menu.size)].let { basket[it.id] = (basket[it.id] ?: 0) + 1 } }
                if (basket.isEmpty()) menu[random.nextInt(menu.size)].let { basket[it.id] = 1 }
                val items = basket.values.sum()

                // Timing with the same rules as the live simulator
                val ahead = random.nextInt(0, 6)
                val variation = 0.8 + random.nextDouble() * 0.4
                val prep = OrderStatusRules.dwellMillis(OrderStatus.PREPARING, 1.0, items, variation)!!
                val avgPrepOfOthers = OrderStatusRules.PREPARING_BASE_MS + OrderStatusRules.PREPARING_PER_ITEM_MS * 2
                val queueWait = ahead * avgPrepOfOthers / OrderStatusRules.BARISTA_CAPACITY
                val created = now - (count - n) * 6 * 60 * 60 * 1000L - random.nextLong(0, 3_600_000)
                val preparingAt = created + OrderStatusRules.PENDING_BASE_MS + queueWait
                val readyAt = preparingAt + prep
                val collectedAt = readyAt + OrderStatusRules.READY_BASE_MS

                val total = basket.entries.sumOf { (pid, q) -> menu.first { it.id == pid }.pricePence * q }
                val orderId = w.insertOrThrow("orders", null, ContentValues().apply {
                    put("user_id", userId)
                    put("status", OrderStatus.COLLECTED.name)
                    put("total_pence", total)
                    put("note", "[demo data]")
                    put("created_at", created)
                    put("updated_at", collectedAt)
                    put("queue_ahead", ahead)
                    putNull("predicted_wait_s") // demo orders never count towards live accuracy
                })
                basket.forEach { (pid, q) ->
                    val p = menu.first { it.id == pid }
                    w.insertOrThrow("order_items", null, ContentValues().apply {
                        put("order_id", orderId); put("product_id", pid); put("product_name", p.name)
                        put("unit_price_pence", p.pricePence); put("quantity", q)
                    })
                }
                listOf(
                    OrderStatus.PENDING to created, OrderStatus.PREPARING to preparingAt,
                    OrderStatus.READY to readyAt, OrderStatus.COLLECTED to collectedAt
                ).forEach { (st, at) ->
                    w.insertOrThrow("order_status_history", null, ContentValues().apply {
                        put("order_id", orderId); put("status", st.name); put("changed_at", at)
                    })
                }
            }
            w.setTransactionSuccessful()
            return count
        } finally {
            w.endTransaction()
        }
    }

    /** The demo account has a random, discarded password, so nobody can log into it. */
    private fun demoUserId(): Long {
        db.readableDatabase.rawQuery("SELECT id FROM users WHERE email = ?", arrayOf(DEMO_EMAIL)).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val hashed = PasswordHasher.hash(UUID.randomUUID().toString())
        return db.writableDatabase.insertOrThrow("users", null, ContentValues().apply {
            put("name", "Demo Customer"); put("email", DEMO_EMAIL)
            put("password_hash", hashed.hash); put("password_salt", hashed.salt)
            put("created_at", clock())
        })
    }

    companion object {
        const val DEMO_EMAIL = "demo.customer@smartcafe.invalid"
    }
}
