package com.p2897777.ctec3703spec.recommend

import com.p2897777.ctec3703spec.model.Order
import com.p2897777.ctec3703spec.model.OrderStatus
import java.util.Calendar

/** Simple analytics over a customer's order history, shown on the Account tab. Pure and unit tested. */
object OrderInsights {

    data class Stats(
        val orderCount: Int,
        val totalSpentPence: Int,
        val averageOrderPence: Int,
        val favouriteItem: String?,
        val favouriteCount: Int,
        val busiestHour: Int?,
        val cancelledCount: Int
    )

    fun compute(orders: List<Order>, hourOf: (Long) -> Int = ::hourOfDay): Stats {
        val counted = orders.filter { it.status != OrderStatus.CANCELLED }
        val total = counted.sumOf { it.totalPence }
        val itemCounts = counted.flatMap { it.items }
            .groupBy { it.productName }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
        val fav = itemCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()
        val busiest = counted.groupBy { hourOf(it.createdAt) }.maxByOrNull { it.value.size }?.key
        return Stats(
            orderCount = counted.size,
            totalSpentPence = total,
            averageOrderPence = if (counted.isEmpty()) 0 else total / counted.size,
            favouriteItem = fav?.key,
            favouriteCount = fav?.value ?: 0,
            busiestHour = busiest,
            cancelledCount = orders.size - counted.size
        )
    }

    private fun hourOfDay(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}
