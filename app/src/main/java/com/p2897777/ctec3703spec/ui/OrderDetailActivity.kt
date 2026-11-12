package com.p2897777.ctec3703spec.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.CartRepository
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.data.FeedbackRepository
import com.p2897777.ctec3703spec.databinding.ActivityOrderDetailBinding
import com.p2897777.ctec3703spec.databinding.ItemOrderLineBinding
import com.p2897777.ctec3703spec.model.Feedback
import com.p2897777.ctec3703spec.model.Order
import com.p2897777.ctec3703spec.model.OrderStatus
import com.p2897777.ctec3703spec.model.StatusChange
import com.p2897777.ctec3703spec.notify.OrderNotifier
import com.p2897777.ctec3703spec.predict.WaitTimeService
import com.p2897777.ctec3703spec.util.Money
import com.p2897777.ctec3703spec.util.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live order tracking (req 3.1.8), cancellation, and feedback/ratings (req 3.1.9). */
class OrderDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityOrderDetailBinding
    private var orderId = -1L
    private var order: Order? = null
    /** Dropdown options: null = overall service, otherwise a product id from the order. */
    private var targets: List<Pair<String, Long?>> = emptyList()
    @Volatile private var readyAt: Long? = null
    /** Product id → emoji, for the item rows (order lines only snapshot name and price). */
    @Volatile private var emojis: Map<Long, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.session.isLoggedIn) { finish(); return }
        b = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1)

        b.cancelButton.setOnClickListener { confirmCancel() }
        b.submitFeedback.setOnClickListener { submitFeedback() }
        b.reorderButton.setOnClickListener { reorder() }
        DataEvents.ordersChanged.observe(this) { load() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        orderId = intent.getLongExtra(EXTRA_ORDER_ID, orderId)
        load()
    }

    private fun load() {
        val userId = app.session.userId
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val o = app.orders.findById(orderId)
                // Access control: a customer can only open their own orders
                if (o == null || o.userId != userId) null
                else Triple(o, app.orders.history(orderId), app.feedback.forOrder(orderId))
                    .also {
                        readyAt = app.orders.readyAt(orderId)
                        emojis = o.items.mapNotNull { i -> app.products.findById(i.productId)?.let { i.productId to it.emoji } }.toMap()
                    }
            }
            if (data == null) {
                Snackbar.make(b.root, "Order not found", Snackbar.LENGTH_LONG).show()
                b.root.postDelayed({ finish() }, 1500)
                return@launch
            }
            render(data.first, data.second, data.third)
        }
    }

    private fun render(o: Order, history: List<StatusChange>, feedback: List<Feedback>) {
        order = o
        b.toolbar.title = "Order #${o.id}"
        renderStatus(o, history)
        renderItems(o)

        b.cancelButton.visibility = if (o.status.canCustomerCancel) View.VISIBLE else View.GONE
        b.reorderButton.visibility = if (o.status.isActive) View.GONE else View.VISIBLE
        renderEta(o)

        // Feedback: only after collection; one overall rating and one per item
        val rated = feedback.map { it.productId }.toSet()
        targets = (listOf<Pair<String, Long?>>("Overall service" to null) +
            o.items.distinctBy { it.productId }.map { it.productName to it.productId })
            .filter { it.second !in rated }
        val canRate = o.status == OrderStatus.COLLECTED && targets.isNotEmpty()
        b.feedbackForm.visibility = if (canRate) View.VISIBLE else View.GONE
        if (canRate) {
            b.targetDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, targets.map { it.first }))
            if (b.targetDropdown.text.isNullOrEmpty() || targets.none { it.first == b.targetDropdown.text.toString() }) {
                b.targetDropdown.setText(targets.first().first, false)
            }
        }
        b.feedbackTitle.visibility = if (feedback.isEmpty()) View.GONE else View.VISIBLE
        b.feedbackList.visibility = b.feedbackTitle.visibility
        b.feedbackList.text = feedback.joinToString("\n\n") { f ->
            val what = f.productId?.let { pid -> o.items.firstOrNull { it.productId == pid }?.productName } ?: "Overall service"
            "$what: " + "★".repeat(f.rating) + "☆".repeat(5 - f.rating) + if (f.comment.isNotBlank()) "\n“${f.comment}”" else ""
        }
    }

    /** Status card: tinted icon badge, headline, and the four-step tracker with the time each step was reached. */
    private fun renderStatus(o: Order, history: List<StatusChange>) {
        val statusColor = ContextCompat.getColor(this, UiUtils.statusColorRes(o.status))
        b.statusIcon.setImageResource(statusIconRes(o.status))
        b.statusIcon.imageTintList = ColorStateList.valueOf(statusColor)
        // Same hue as the icon at ~15% opacity for the badge behind it
        b.statusIconBg.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(statusColor, 0x26))
        b.statusHeadline.text = o.status.label

        val cancelledAt = history.lastOrNull { it.status == OrderStatus.CANCELLED }?.changedAt
        b.statusMessage.text = OrderNotifier.messageFor(o.status) +
            (cancelledAt?.let { "\nCancelled at ${UiUtils.time(it).substring(0, 5)}" } ?: "")

        val cancelled = o.status == OrderStatus.CANCELLED
        b.tracker.visibility = if (cancelled) View.GONE else View.VISIBLE
        if (cancelled) return

        val steps = listOf(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COLLECTED)
        val current = steps.indexOf(o.status)
        val reachedAt = history.groupBy { it.status }.mapValues { (_, v) -> v.maxOf { it.changedAt } }
        val done = ContextCompat.getColor(this, R.color.coffee)
        val todo = ContextCompat.getColor(this, R.color.latte)
        listOf(b.step0, b.step1, b.step2, b.step3).forEachIndexed { i, s ->
            s.label.text = steps[i].label.substringBefore(' ')
            s.lineLeft.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
            s.lineRight.visibility = if (i == steps.lastIndex) View.INVISIBLE else View.VISIBLE
            s.lineLeft.setBackgroundColor(if (i <= current) done else todo)
            s.lineRight.setBackgroundColor(if (i < current) done else todo)
            when {
                i < current -> {
                    s.dot.setBackgroundResource(R.drawable.bg_circle)
                    s.dot.backgroundTintList = ColorStateList.valueOf(done)
                    s.dot.layoutParams = s.dot.layoutParams.apply { width = dp(14); height = dp(14) }
                }
                i == current -> {
                    s.dot.setBackgroundResource(R.drawable.bg_step_current)
                    s.dot.backgroundTintList = null
                    s.dot.layoutParams = s.dot.layoutParams.apply { width = dp(22); height = dp(22) }
                }
                else -> {
                    s.dot.setBackgroundResource(R.drawable.bg_step_todo)
                    s.dot.backgroundTintList = null
                    s.dot.layoutParams = s.dot.layoutParams.apply { width = dp(14); height = dp(14) }
                }
            }
            s.label.setTextColor(ContextCompat.getColor(this, if (i <= current) R.color.espresso else R.color.text_secondary))
            val at = reachedAt[steps[i]]
            s.time.text = if (i <= current && at != null) UiUtils.time(at).substring(0, 5) else ""
            s.root.contentDescription = steps[i].label + when {
                i < current -> ", done"
                i == current -> ", current step"
                else -> ", not reached yet"
            }
        }
    }

    /** Neat item rows (emoji, name, quantity pill, price) plus the total and any barista note. */
    private fun renderItems(o: Order) {
        b.placedText.text = "Placed ${UiUtils.dateTime(o.createdAt)}"
        b.itemsContainer.removeAllViews()
        o.items.forEach { item ->
            val row = ItemOrderLineBinding.inflate(layoutInflater, b.itemsContainer, true)
            row.emoji.text = emojis[item.productId] ?: "☕"
            row.name.text = item.productName
            row.quantity.text = "${item.quantity}×"
            row.price.text = Money.format(item.lineTotalPence)
        }
        b.totalText.text = Money.format(o.totalPence)
        b.noteText.visibility = if (o.note.isBlank()) View.GONE else View.VISIBLE
        b.noteText.text = "“${o.note}”"
    }

    private fun statusIconRes(s: OrderStatus) = when (s) {
        OrderStatus.PENDING -> R.drawable.ic_status_pending
        OrderStatus.PREPARING -> R.drawable.ic_status_preparing
        OrderStatus.READY -> R.drawable.ic_status_ready
        OrderStatus.COLLECTED -> R.drawable.ic_status_collected
        OrderStatus.CANCELLED -> R.drawable.ic_status_cancelled
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Shows the predicted pickup time while waiting, and predicted vs actual once it is ready. */
    private fun renderEta(o: Order) {
        val predicted = o.predictedWaitSeconds
        val ready = readyAt
        b.etaText.visibility = View.VISIBLE
        b.etaText.text = when {
            o.status == OrderStatus.CANCELLED || predicted == null -> { b.etaText.visibility = View.GONE; "" }
            ready == null ->
                "⏱ Predicted ready around ${UiUtils.time(o.createdAt + predicted * 1000).substring(0, 5)} " +
                    "(${WaitTimeService.friendly(predicted)} from ordering, ${o.queueAhead} ahead in the queue)"
            else -> {
                val actual = (ready - o.createdAt) / 1000
                val diff = kotlin.math.abs(actual - predicted)
                "⏱ Predicted ${formatSecs(predicted)}, actual ${formatSecs(actual)} " +
                    "(${if (diff <= 60) "within a minute" else "off by ${formatSecs(diff)}"})"
            }
        }
    }

    private fun formatSecs(s: Long) = if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"

    /** Puts every still-available item from this order back in the cart. */
    private fun reorder() {
        val o = order ?: return
        val userId = app.session.userId
        lifecycleScope.launch {
            val skipped = withContext(Dispatchers.IO) {
                o.items.filter { app.cart.add(userId, it.productId, it.quantity) !is CartRepository.CartResult.Ok }
                    .map { it.productName }
            }
            DataEvents.notifyCartChanged()
            val msg = if (skipped.isEmpty()) "Added to your cart" else "Added. Not available now: ${skipped.joinToString()}"
            Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).setAction("View cart") {
                startActivity(
                    android.content.Intent(this@OrderDetailActivity, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_TAB, R.id.nav_cart)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }.show()
        }
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel order #$orderId?")
            .setMessage("You can only cancel before the café starts preparing it.")
            .setNegativeButton("Keep order", null)
            .setPositiveButton("Cancel order") { _, _ ->
                val userId = app.session.userId
                lifecycleScope.launch {
                    val res = withContext(Dispatchers.IO) { app.orders.cancel(orderId, userId) }
                    if (res == null) {
                        Snackbar.make(b.root, "Too late, the café has already started preparing this order", Snackbar.LENGTH_LONG).show()
                    } else {
                        app.notifier.notifyStatus(res, userId)
                        DataEvents.notifyOrdersChanged()
                    }
                    load()
                }
            }
            .show()
    }

    private fun submitFeedback() {
        val rating = b.ratingBar.rating.toInt()
        val comment = b.commentInput.text?.toString().orEmpty()
        val err = Validators.rating(rating) ?: Validators.comment(comment)
        if (err != null) {
            Snackbar.make(b.root, err, Snackbar.LENGTH_SHORT).show()
            return
        }
        val target = targets.firstOrNull { it.first == b.targetDropdown.text.toString() } ?: targets.firstOrNull() ?: return
        val userId = app.session.userId
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { app.feedback.submit(userId, orderId, target.second, rating, comment) }
            when (res) {
                is FeedbackRepository.Result.Saved -> {
                    Snackbar.make(b.root, "Thanks for your feedback!", Snackbar.LENGTH_SHORT).show()
                    b.ratingBar.rating = 0f
                    b.commentInput.setText("")
                    b.targetDropdown.setText("", false)
                    load()
                }
                is FeedbackRepository.Result.Error -> Snackbar.make(b.root, res.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }
}
