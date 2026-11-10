package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.databinding.FragmentOrdersBinding
import com.p2897777.ctec3703spec.model.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Order history and live tracking (req 3.1.8). Updates itself when the café changes a status. */
class OrdersFragment : Fragment() {

    private var _b: FragmentOrdersBinding? = null
    private val b get() = _b!!
    private var all: List<Order> = emptyList()

    private val adapter = OrderAdapter { order ->
        startActivity(Intent(requireContext(), OrderDetailActivity::class.java).putExtra(OrderDetailActivity.EXTRA_ORDER_ID, order.id))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentOrdersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.orderList.layoutManager = LinearLayoutManager(requireContext())
        b.orderList.adapter = adapter
        b.filterChips.setOnCheckedStateChangeListener { _, _ -> render() }
        DataEvents.ordersChanged.observe(viewLifecycleOwner) { load() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val cafe = requireContext().app
        val userId = cafe.session.userId
        viewLifecycleOwner.lifecycleScope.launch {
            all = withContext(Dispatchers.IO) { cafe.orders.ordersFor(userId) }
            if (_b != null) render()
        }
    }

    private fun render() {
        val list = when (b.filterChips.checkedChipId) {
            R.id.chipActive -> all.filter { it.status.isActive }
            R.id.chipPast -> all.filter { !it.status.isActive }
            else -> all
        }
        adapter.submitList(list)
        b.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.emptyText.text = when {
            all.isEmpty() -> getString(R.string.orders_empty)
            b.filterChips.checkedChipId == R.id.chipActive -> "No orders in progress."
            else -> "No past orders yet."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
