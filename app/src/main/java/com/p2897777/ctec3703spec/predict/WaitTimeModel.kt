package com.p2897777.ctec3703spec.predict

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Predicts how long an order will take to be ready, learned from the café's own order history.
 *
 * Model: multiple linear regression
 *     waitSeconds = b0 + b1 * itemCount + b2 * ordersAhead
 * fitted by ridge-regularised least squares (normal equations, 3x3 solve).
 *
 * - itemCount:   total quantity in the order (more drinks/food take longer to make)
 * - ordersAhead: orders still waiting or being prepared when this one was placed (the queue)
 *
 * Until there are enough completed orders to learn from, it falls back to a sensible prior.
 * Pure Kotlin, so training, prediction and evaluation are all unit tested on the JVM.
 */
class WaitTimeModel private constructor(
    val intercept: Double,
    val perItem: Double,
    val perOrderAhead: Double,
    val trainedOn: Int
) {
    data class Sample(val itemCount: Int, val ordersAhead: Int, val waitSeconds: Double)

    val isTrained: Boolean get() = trainedOn >= MIN_SAMPLES

    fun predictSeconds(itemCount: Int, ordersAhead: Int): Long {
        val raw = intercept + perItem * itemCount + perOrderAhead * ordersAhead
        return raw.coerceIn(MIN_PREDICTION_S, MAX_PREDICTION_S).roundToLong()
    }

    companion object {
        const val MIN_SAMPLES = 5
        /** Ridge penalty: keeps coefficients stable when there are few or very similar orders. */
        const val LAMBDA = 0.5
        const val MIN_PREDICTION_S = 10.0
        const val MAX_PREDICTION_S = 60.0 * 60

        /** Prior used before there is enough data: 60 s base, 20 s per item, 45 s per order ahead. */
        val PRIOR = WaitTimeModel(60.0, 20.0, 45.0, 0)

        fun train(samples: List<Sample>): WaitTimeModel {
            if (samples.size < MIN_SAMPLES) return PRIOR.copyWith(trainedOn = samples.size)

            // Build X^T X (+ λI on the slopes) and X^T y for features [1, items, ahead]
            val xtx = Array(3) { DoubleArray(3) }
            val xty = DoubleArray(3)
            for (s in samples) {
                val x = doubleArrayOf(1.0, s.itemCount.toDouble(), s.ordersAhead.toDouble())
                for (i in 0..2) {
                    xty[i] += x[i] * s.waitSeconds
                    for (j in 0..2) xtx[i][j] += x[i] * x[j]
                }
            }
            xtx[1][1] += LAMBDA
            xtx[2][2] += LAMBDA // intercept is not penalised

            val b = solve3(xtx, xty) ?: return PRIOR.copyWith(trainedOn = samples.size)
            return WaitTimeModel(b[0], b[1], b[2], samples.size)
        }

        /** Mean absolute error in seconds of [model] over [samples] (lower is better). */
        fun meanAbsoluteError(model: WaitTimeModel, samples: List<Sample>): Double =
            if (samples.isEmpty()) 0.0
            else samples.sumOf { abs(model.predictSeconds(it.itemCount, it.ordersAhead) - it.waitSeconds) } / samples.size

        /** Gaussian elimination with partial pivoting. Returns null if the system is singular. */
        internal fun solve3(a: Array<DoubleArray>, y: DoubleArray): DoubleArray? {
            val m = Array(3) { r -> DoubleArray(4) { c -> if (c < 3) a[r][c] else y[r] } }
            for (col in 0..2) {
                val pivot = (col..2).maxByOrNull { abs(m[it][col]) }!!
                if (abs(m[pivot][col]) < 1e-9) return null
                val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
                for (r in 0..2) if (r != col) {
                    val f = m[r][col] / m[col][col]
                    for (c in col..3) m[r][c] -= f * m[col][c]
                }
            }
            return DoubleArray(3) { m[it][3] / m[it][it] }
        }

        private fun WaitTimeModel.copyWith(trainedOn: Int) =
            WaitTimeModel(intercept, perItem, perOrderAhead, trainedOn)
    }
}