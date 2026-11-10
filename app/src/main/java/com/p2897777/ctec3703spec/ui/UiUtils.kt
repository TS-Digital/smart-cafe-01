package com.p2897777.ctec3703spec.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiUtils {
    private val dateTime = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK)
    private val time = SimpleDateFormat("HH:mm:ss", Locale.UK)

    fun dateTime(millis: Long): String = dateTime.format(Date(millis))
    fun time(millis: Long): String = time.format(Date(millis))

    fun statusColorRes(status: OrderStatus): Int = when (status) {
        OrderStatus.PENDING -> R.color.status_pending
        OrderStatus.PREPARING -> R.color.status_preparing
        OrderStatus.READY -> R.color.status_ready
        OrderStatus.COLLECTED -> R.color.status_collected
        OrderStatus.CANCELLED -> R.color.status_cancelled
    }

    fun styleStatusChip(view: TextView, status: OrderStatus) {
        view.text = status.label
        view.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(view.context, statusColorRes(status)))
    }
}