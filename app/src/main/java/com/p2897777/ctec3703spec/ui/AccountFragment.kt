package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.OrderStatusRules
import com.p2897777.ctec3703spec.databinding.FragmentAccountBinding
import com.p2897777.ctec3703spec.recommend.OrderInsights
import com.p2897777.ctec3703spec.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountFragment : Fragment() {

    private var _b: FragmentAccountBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentAccountBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val cafe = requireContext().app
        b.simEnabled.isChecked = cafe.simulator.enabled
        b.speedSlider.value = cafe.simulator.speedFactor.toFloat().coerceIn(1f, 5f)
        updateSpeedLabel(b.speedSlider.value)
        b.simEnabled.setOnCheckedChangeListener { _, on -> cafe.saveSimulatorSettings(on, b.speedSlider.value.toDouble()) }
        b.speedSlider.addOnChangeListener { _, v, _ ->
            updateSpeedLabel(v)
            cafe.saveSimulatorSettings(b.simEnabled.isChecked, v.toDouble())
        }
        b.openAdmin.setOnClickListener { startActivity(Intent(requireContext(), AdminSimulatorActivity::class.java)) }
        b.logoutButton.setOnClickListener { logout() }
        // Developer mode: staff tools only exist in debug builds and stay hidden until switched on
        b.devMode.visibility = if (cafe.settings.developerModeAvailable) View.VISIBLE else View.GONE
        b.devMode.isChecked = cafe.settings.developerMode
        showStaffTools(cafe.settings.developerMode)
        b.devMode.setOnCheckedChangeListener { _, on ->
            cafe.settings.developerMode = on
            showStaffTools(on)
        }
        b.deleteButton.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val cafe = requireContext().app
        val userId = cafe.session.userId
        viewLifecycleOwner.lifecycleScope.launch {
            val (user, stats) = withContext(Dispatchers.IO) {
                cafe.users.findById(userId) to OrderInsights.compute(cafe.orders.ordersFor(userId))
            }
            val acc = withContext(Dispatchers.IO) { cafe.waitTimes.accuracy() }
            val recs = withContext(Dispatchers.IO) { cafe.recStats.totals(userId) }
            if (_b == null) return@launch
            b.nameText.text = user?.name ?: cafe.session.userName
            b.emailText.text = user?.email ?: ""
            b.statsText.text = if (stats.orderCount == 0) "Place your first order to see your stats here." else buildString {
                appendLine("Orders: ${stats.orderCount}")
                appendLine("Total spent: ${Money.format(stats.totalSpentPence)}")
                appendLine("Average order: ${Money.format(stats.averageOrderPence)}")
                stats.favouriteItem?.let { appendLine("Favourite: $it (${stats.favouriteCount}×)") }
                stats.busiestHour?.let { append("You usually order around %02d:00".format(it)) }
            }
            b.recStatsText.text = recs.addRate?.let {
                "${recs.shown} recommendations shown, ${recs.added} added to cart (%.0f%% add rate)".format(it * 100)
            } ?: "No recommendations shown yet."
            b.modelText.text = buildString {
                val m = acc.model
                if (!m.isTrained) {
                    appendLine("Learning… ${acc.trainedOn}/${com.p2897777.ctec3703spec.predict.WaitTimeModel.MIN_SAMPLES} completed orders so far.")
                    append("Using default timings until then. Tip: the staff console can add demo history.")
                } else {
                    appendLine("Trained on ${acc.trainedOn} completed orders (linear regression).")
                    appendLine("Wait ≈ %.0fs + %.0fs per item + %.0fs per order ahead".format(m.intercept, m.perItem, m.perOrderAhead))
                    if (acc.liveMaeSeconds != null) {
                        appendLine("Live accuracy on ${acc.liveCount} real orders:")
                        appendLine("average error %.0fs, %.0f%% within a minute".format(acc.liveMaeSeconds, (acc.withinOneMinute ?: 0.0) * 100))
                        acc.baselineMaeSeconds?.let { base ->
                            val better = base > acc.liveMaeSeconds
                            append("Default formula on the same orders: %.0fs (%s)".format(base, if (better) "model is better" else "model is not better yet"))
                        }
                    } else append("Place an order to measure live accuracy.")
                }
            }
        }
    }

    private fun showStaffTools(on: Boolean) {
        b.simulatorCard.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun updateSpeedLabel(v: Float) {
        val s = v.toDouble()
        b.speedLabel.text = "Speed: ${v.toInt()}× (wait ${(OrderStatusRules.PENDING_BASE_MS / s / 1000).toInt()}s, " +
            "prepare ${(OrderStatusRules.PREPARING_BASE_MS / s / 1000).toInt()}s + ${(OrderStatusRules.PREPARING_PER_ITEM_MS / s / 1000).toInt()}s per item, " +
            "${OrderStatusRules.BARISTA_CAPACITY} baristas)"
    }

    private fun logout() {
        val ctx = requireContext()
        ctx.app.session.logout()
        startActivity(Intent(ctx, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete account?")
            .setMessage("This permanently deletes your account, cart, orders and feedback from this device. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val cafe = requireContext().app
                val userId = cafe.session.userId
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { cafe.users.deleteAccount(userId) }
                    if (_b != null) logout()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
