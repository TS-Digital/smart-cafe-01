package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.CartRepository
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.data.PlaceOrderResult
import com.p2897777.ctec3703spec.databinding.FragmentCartBinding
import com.p2897777.ctec3703spec.model.CartLine
import com.p2897777.ctec3703spec.predict.WaitTimeService
import com.p2897777.ctec3703spec.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartFragment : Fragment() {

    private var _b: FragmentCartBinding? = null
    private val b get() = _b!!
    // Resolved on the main thread before any background work, so IO code never touches the Context
    private val cafe by lazy { requireContext().app }
    private val userId get() = cafe.session.userId
    private val repo get() = cafe.cart

    private val adapter = CartAdapter(
        onQuantity = { line, q -> changeQuantity(line, q) },
        onRemove = { line -> val uid = userId; io({ repo.remove(uid, line.product.id) }) { DataEvents.notifyCartChanged(); load() } }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentCartBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cafe // initialise
        b.cartList.layoutManager = LinearLayoutManager(requireContext())
        b.cartList.adapter = adapter
        b.placeOrderButton.setOnClickListener { confirmOrder() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val uid = userId
        io({
            val lines = repo.lines(uid)
            lines to cafe.waitTimes.estimate(lines.sumOf { it.quantity })
        }) { (lines, eta) -> render(lines, eta) }
    }

    private fun render(lines: List<CartLine>, eta: WaitTimeService.Estimate) {
        adapter.submitList(lines)
        val empty = lines.isEmpty()
        b.emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        b.cartList.visibility = if (empty) View.GONE else View.VISIBLE
        b.checkoutPanel.visibility = if (empty) View.GONE else View.VISIBLE
        b.totalText.text = Money.format(lines.sumOf { it.lineTotalPence })
        b.placeOrderButton.isEnabled = lines.isNotEmpty() && lines.all { it.product.available }
        // Smart pickup-time prediction from the wait-time model
        val queue = when (eta.ordersAhead) { 0 -> "no queue"; 1 -> "1 order ahead"; else -> "${eta.ordersAhead} orders ahead" }
        b.etaText.text = "⏱ Ready in ${WaitTimeService.friendly(eta.seconds)} · $queue" +
            if (eta.modelTrained) "" else " (estimate improves as orders complete)"
    }

    private fun changeQuantity(line: CartLine, quantity: Int) {
        val uid = userId
        io({ repo.setQuantity(uid, line.product.id, quantity) }) { res ->
            if (res is CartRepository.CartResult.Error) Snackbar.make(b.root, res.message, Snackbar.LENGTH_SHORT).show()
            DataEvents.notifyCartChanged()
            load()
        }
    }

    private fun confirmOrder() {
        val total = b.totalText.text
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Place order?")
            .setMessage("Your total is $total. Pay at the counter when you collect.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Place order") { _, _ -> placeOrder() }
            .show()
    }

    private fun placeOrder() {
        b.placeOrderButton.isEnabled = false
        val note = b.noteInput.text?.toString().orEmpty()
        val uid = userId
        io({ cafe.orders.placeOrder(uid, note, cafe.waitTimes.predictor()) }) { res ->
            when (res) {
                is PlaceOrderResult.Success -> {
                    b.noteInput.setText("")
                    DataEvents.notifyCartChanged()
                    DataEvents.notifyOrdersChanged()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Order #${res.orderId} confirmed ✅")
                        .setMessage("Total ${Money.format(res.totalPence)}. We'll notify you when it's ready to collect.")
                        .setPositiveButton("Track order") { _, _ ->
                            startActivity(
                                Intent(requireContext(), OrderDetailActivity::class.java)
                                    .putExtra(OrderDetailActivity.EXTRA_ORDER_ID, res.orderId)
                            )
                        }
                        .setOnDismissListener { load() }
                        .show()
                }
                is PlaceOrderResult.Failure -> {
                    Snackbar.make(b.root, res.message, Snackbar.LENGTH_LONG).show()
                    load()
                }
            }
        }
    }

    /** Runs [work] on the IO dispatcher then [ui] on the main thread, if the view still exists. */
    private fun <T> io(work: () -> T, ui: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { work() }
            if (_b != null) ui(result)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
