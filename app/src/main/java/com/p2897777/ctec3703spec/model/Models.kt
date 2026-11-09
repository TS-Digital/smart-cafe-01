package com.p2897777.ctec3703spec.model


/**
 * Domain models. Money is always stored as whole pence (Int) to avoid
 * floating point rounding errors in totals.
 */

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: Long
)

enum class Category(val label: String) {
    HOT_DRINKS("Hot drinks"),
    COLD_DRINKS("Cold drinks"),
    CAKES("Cakes"),
    PASTRIES("Pastries"),
    SAVOURY("Savoury");

    companion object {
        fun fromName(name: String): Category =
            entries.firstOrNull { it.name == name } ?: SAVOURY
    }
}

data class Product(
    val id: Long,
    val name: String,
    val description: String,
    val category: Category,
    val pricePence: Int,
    val available: Boolean,
    val emoji: String,
    /** Tags used by the recommendation engine, e.g. "hot", "cold", "sweet". */
    val tags: Set<String> = emptySet()
)

data class CartLine(
    val product: Product,
    val quantity: Int
) {
    val lineTotalPence: Int get() = product.pricePence * quantity
}

/**
 * Order lifecycle used by the café (brief section 3.2.6).
 * The order of the entries IS the allowed progression.
 */
enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    PREPARING("Preparing"),
    READY("Ready for collection"),
    COLLECTED("Collected"),
    CANCELLED("Cancelled");

    /** Next status in the normal flow, or null if this is a final state. */
    fun next(): OrderStatus? = when (this) {
        PENDING -> PREPARING
        PREPARING -> READY
        READY -> COLLECTED
        COLLECTED, CANCELLED -> null
    }

    val isActive: Boolean get() = this != COLLECTED && this != CANCELLED

    /** Customers can only cancel before the kitchen starts preparing. */
    val canCustomerCancel: Boolean get() = this == PENDING

    companion object {
        fun fromName(name: String): OrderStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

data class OrderItem(
    val productId: Long,
    /** Name and price are snapshotted so old orders stay correct if the menu changes. */
    val productName: String,
    val unitPricePence: Int,
    val quantity: Int
) {
    val lineTotalPence: Int get() = unitPricePence * quantity
}

data class Order(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val totalPence: Int,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<OrderItem> = emptyList(),
    /** Orders still waiting or being prepared when this one was placed. */
    val queueAhead: Int = 0,
    /** What the wait-time model predicted at checkout, in seconds (null for demo/legacy orders). */
    val predictedWaitSeconds: Long? = null
) {
    val itemCount: Int get() = items.sumOf { it.quantity }
}

data class StatusChange(
    val status: OrderStatus,
    val changedAt: Long
)

data class Feedback(
    val id: Long,
    val userId: Long,
    val orderId: Long?,
    val productId: Long?,
    val rating: Int,
    val comment: String,
    val createdAt: Long
)
