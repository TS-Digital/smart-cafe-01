package com.p2897777.ctec3703spec.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.databinding.ActivityAdminSimulatorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Test administration interface" allowed for individual Customer builds (brief 3.1).
 * Simulates café staff: lists active orders and moves one to its next status on demand,
 * through the same rules and repository calls the automatic simulator uses.
 */
class AdminSimulatorActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminSimulatorBinding

    private val adapter = AdminOrderAdapter { order ->
        lifecycleScope.launch {
            val update = withContext(Dispatchers.IO) { app.simulator.advanceNow(order.id) }
            Snackbar.make(
                b.root,
                update?.let { "Order #${it.orderId}: ${it.from.label} → ${it.to.label}" } ?: "That order can't move on",
                Snackbar.LENGTH_SHORT
            ).show()
            load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Staff tools are only reachable in developer mode (see AppSettings)
        if (!app.session.isLoggedIn || !app.settings.developerMode) { finish(); return }
        b = ActivityAdminSimulatorBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        DataEvents.ordersChanged.observe(this) { load() }
        b.demoButton.setOnClickListener {
            b.demoButton.isEnabled = false
            lifecycleScope.launch {
                val added = withContext(Dispatchers.IO) { runCatching { app.demoData.generate() }.getOrDefault(0) }
                DataEvents.notifyOrdersChanged()
                Snackbar.make(b.root, "Added $added demo orders from a separate Demo Customer account", Snackbar.LENGTH_LONG).show()
                b.demoButton.isEnabled = true
            }
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { app.orders.activeOrders() }
            adapter.submitList(list)
            b.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
