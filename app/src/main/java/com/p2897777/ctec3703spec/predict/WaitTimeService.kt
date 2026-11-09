package com.p2897777.ctec3703spec.predict

import com.p2897777.ctec3703spec.data.OrderRepository
import com.p2897777.ctec3703spec.data.WaitRecord
import kotlin.math.roundToInt

/**
 * Connects the wait-time model to the database: retrains on the latest completed orders,
 * gives estimates at checkout, and measures how accurate past predictions really were.
 */
class WaitTimeService(private val orders: OrderRepository) {

    data class Estimate(val seconds: Long, val ordersAhead: Int, val modelTrained: Boolean)

    data class Accuracy(
        val trainedOn: Int,
        val model: WaitTimeModel,
        /** Average |predicted − actual| over orders that had a real prediction at checkout. */
        val liveMaeSeconds: Double?,
        val liveCount: Int,
        /** Share of live predictions within ±1 minute of the actual wait. */
        val withinOneMinute: Double?,
        /** Error the cold-start default formula would have had on the same orders (the baseline). */
        val baselineMaeSeconds: Double? = null
    )

    @Volatile private var cached: WaitTimeModel? = null
    @Volatile private var cachedCount = -1

    /** Retrains only when new completed orders exist, so it is cheap to call often. */
    fun model(): WaitTimeModel {
        val history = orders.waitTimeHistory()
        val current = cached
        if (current != null && history.size == cachedCount) return current
        return WaitTimeModel.train(history.map { it.toSample() }).also {
            cached = it
            cachedCount = history.size
        }
    }

    fun estimate(itemCount: Int): Estimate {
        val ahead = orders.queueSize()
        val m = model()
        return Estimate(m.predictSeconds(itemCount, ahead), ahead, m.isTrained)
    }

    /** Used by OrderRepository.placeOrder to store the prediction made at checkout. */
    fun predictor(): (Int, Int) -> Long {
        val m = model()
        return { items, ahead -> m.predictSeconds(items, ahead) }
    }

    fun accuracy(): Accuracy {
        val history = orders.waitTimeHistory()
        val m = model()
        val live = history.filter { it.predictedSeconds != null }
        val errors = live.map { kotlin.math.abs(it.predictedSeconds!! - it.actualSeconds) }
        return Accuracy(
            trainedOn = history.size,
            model = m,
            liveMaeSeconds = errors.takeIf { it.isNotEmpty() }?.average(),
            liveCount = live.size,
            withinOneMinute = errors.takeIf { it.isNotEmpty() }?.let { e -> e.count { it <= 60.0 }.toDouble() / e.size },
            baselineMaeSeconds = live.takeIf { it.isNotEmpty() }
                ?.let { WaitTimeModel.meanAbsoluteError(WaitTimeModel.PRIOR, it.map { r -> r.toSample() }) }
        )
    }

    companion object {
        fun WaitRecord.toSample() = WaitTimeModel.Sample(itemCount, ordersAhead, actualSeconds)

        /** 75 -> "about 1 min", 30 -> "under a minute", 400 -> "about 7 min" */
        fun friendly(seconds: Long): String {
            if (seconds < 60) return "under a minute"
            val mins = (seconds / 60.0).roundToInt().coerceAtLeast(1)
            return "about $mins min"
        }
    }
}
