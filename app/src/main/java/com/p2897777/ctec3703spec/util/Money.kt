package com.p2897777.ctec3703spec.util

import java.util.Locale

object Money {
    /** 350 -> "£3.50" */
    fun format(pence: Int): String {
        val sign = if (pence < 0) "-" else ""
        val abs = kotlin.math.abs(pence)
        return String.format(Locale.UK, "%s£%d.%02d", sign, abs / 100, abs % 100)
    }
}
 