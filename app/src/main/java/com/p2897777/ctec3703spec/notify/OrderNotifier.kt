package com.p2897777.ctec3703spec.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.data.StatusUpdate
import com.p2897777.ctec3703spec.model.OrderStatus
import com.p2897777.ctec3703spec.ui.OrderDetailActivity

/** Posts a system notification whenever one of the customer's orders changes status (req 3.1.10). */
class OrderNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Only notifies the currently logged-in customer, never someone else's order. */
    fun notifyStatus(update: StatusUpdate, loggedInUserId: Long) {
        if (update.userId != loggedInUserId || !canPost()) return
        val intent = Intent(context, OrderDetailActivity::class.java)
            .putExtra(OrderDetailActivity.EXTRA_ORDER_ID, update.orderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, update.orderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleFor(update.orderId, update.to))
            .setContentText(messageFor(update.to))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(update.orderId.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: the in-app status still updates
        }
    }

    companion object {
        const val CHANNEL_ID = "order_status"

        fun titleFor(orderId: Long, status: OrderStatus) = "Order #$orderId: ${status.label}"

        fun messageFor(status: OrderStatus): String = when (status) {
            OrderStatus.PENDING -> "We've received your order."
            OrderStatus.PREPARING -> "The barista is preparing your order."
            OrderStatus.READY -> "Your order is ready! Please collect it from the counter."
            OrderStatus.COLLECTED -> "Enjoy! Tap to rate your order."
            OrderStatus.CANCELLED -> "Your order has been cancelled."
        }
    }
}
