package com.p2897777.ctec3703spec.recommend

import com.p2897777.ctec3703spec.model.Product
import kotlin.math.ln

/**
 * Context-aware product recommendations (the app's advanced integration).
 *
 * Combines four signals into one score per product:
 *  1. Personal history  - items this customer orders often ("Your usual").
 *  2. Basket analysis   - association rules mined from ALL past orders: if many baskets that contain
 *                         an item in your cart also contain X, recommend X ("Often ordered with ...").
 *                         Confidence(A -> X) = baskets with A and X / baskets with A.
 *  3. Live weather      - from the Open-Meteo REST API: hot drinks/food when cold or wet,
 *                         cold drinks when warm.
 *  4. Time of day       - breakfast items in the morning, lunch items at midday, treats in the afternoon.
 * Plus a small boost or penalty from customer star ratings.
 *
 * Pure Kotlin with no Android dependencies, so every rule is unit tested.
 */
object RecommendationEngine {

    data class Input(
        val products: List<Product>,
        val myCounts: Map<Long, Int> = emptyMap(),
        val baskets: List<Set<Long>> = emptyList(),
        val cartProductIds: Set<Long> = emptySet(),
        val weather: Weather? = null,
        val hourOfDay: Int = 12,
        val ratings: Map<Long, Pair<Double, Int>> = emptyMap()
    )

    data class Recommendation(
        val product: Product,
        val score: Double,
        val reason: String
    )

    // Weights chosen so a strong basket rule or personal favourite beats a weather nudge
    const val W_PERSONAL = 2.0
    const val W_BASKET = 4.0
    const val W_WEATHER = 1.5
    const val W_TIME = 1.0
    const val W_RATING = 0.5
    const val MIN_SUPPORT = 2 // a rule needs at least this many baskets containing the cart item

    fun recommend(input: Input, limit: Int = 5): List<Recommendation> {
        val byId = input.products.associateBy { it.id }
        val candidates = input.products.filter { it.available && it.id !in input.cartProductIds }

        return candidates.map { p ->
            val reasons = mutableListOf<Pair<Double, String>>()

            input.myCounts[p.id]?.takeIf { it > 0 }?.let { n ->
                reasons += W_PERSONAL * ln(1.0 + n) to "Your usual (ordered $n×)"
            }

            bestRule(p.id, input.cartProductIds, input.baskets)?.let { (fromId, confidence) ->
                val fromName = byId[fromId]?.name ?: "your cart"
                reasons += W_BASKET * confidence to "Often ordered with $fromName"
            }

            input.weather?.let { w -> weatherScore(p, w)?.let { reasons += it } }

            timeScore(p, input.hourOfDay)?.let { reasons += it }

            input.ratings[p.id]?.let { (avg, count) ->
                if (count > 0) reasons += W_RATING * (avg - 3.0) to "Rated %.1f★".format(avg)
            }

            val score = reasons.sumOf { it.first }
            val top = reasons.filter { it.first > 0 }.maxByOrNull { it.first }?.second ?: "Popular choice"
            Recommendation(p, score, top)
        }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.product.name })
            .take(limit)
    }

    /**
     * Strongest association rule cartItem -> target. Returns (cartItemId, confidence) or null.
     */
    fun bestRule(targetId: Long, cart: Set<Long>, baskets: List<Set<Long>>): Pair<Long, Double>? =
        cart.mapNotNull { a ->
            val withA = baskets.count { a in it }
            if (withA < MIN_SUPPORT) return@mapNotNull null
            val withBoth = baskets.count { a in it && targetId in it }
            if (withBoth == 0) null else a to withBoth.toDouble() / withA
        }.maxByOrNull { it.second }

    fun weatherScore(p: Product, w: Weather): Pair<Double, String>? {
        val hot = "hot" in p.tags
        val cold = "cold" in p.tags
        return when {
            (w.isCold || w.isWet) && hot -> W_WEATHER to "Warm up: it's ${w.summary().substringBefore(" in ")}"
            (w.isCold || w.isWet) && cold -> -W_WEATHER to "Cold item on a chilly day"
            w.isWarm && cold -> W_WEATHER to "Cool down: it's ${w.summary().substringBefore(" in ")}"
            w.isWarm && hot -> -W_WEATHER * 0.5 to "Hot item on a warm day"
            else -> null
        }
    }

    fun timeScore(p: Product, hour: Int): Pair<Double, String>? = when {
        hour in 6..10 && "breakfast" in p.tags -> W_TIME to "Great for breakfast"
        hour in 11..14 && "lunch" in p.tags -> W_TIME to "Lunchtime pick"
        hour in 15..18 && "sweet" in p.tags -> W_TIME * 0.8 to "Afternoon treat"
        else -> null
    }
}
