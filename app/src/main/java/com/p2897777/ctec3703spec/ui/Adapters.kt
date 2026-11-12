package com.p2897777.ctec3703spec.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.databinding.ItemAdminOrderBinding
import com.p2897777.ctec3703spec.databinding.ItemCartBinding
import com.p2897777.ctec3703spec.databinding.ItemOrderBinding
import com.p2897777.ctec3703spec.databinding.ItemProductBinding
import com.p2897777.ctec3703spec.databinding.ItemRecommendationBinding
import com.p2897777.ctec3703spec.model.CartLine
import com.p2897777.ctec3703spec.model.Order
import com.p2897777.ctec3703spec.model.Product
import com.p2897777.ctec3703spec.recommend.RecommendationEngine.Recommendation
import com.p2897777.ctec3703spec.util.Money

class ProductAdapter(
    private val onClick: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(diff { it.id }) {

    var ratings: Map<Long, Pair<Double, Int>> = emptyMap()

    class VH(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = getItem(position)
        h.b.emoji.text = p.emoji
        h.b.name.text = p.name
        h.b.description.text = p.description
        h.b.price.text = Money.format(p.pricePence)
        val rating = ratings[p.id]?.let { (avg, n) -> " · ★ %.1f (%d)".format(avg, n) } ?: ""
        h.b.meta.text = if (p.available) p.category.label + rating else "Unavailable"
        h.b.meta.setTextColor(
            ContextCompat.getColor(h.itemView.context, if (p.available) R.color.caramel else R.color.status_cancelled)
        )
        h.b.root.alpha = if (p.available) 1f else 0.55f
        h.b.addButton.isEnabled = p.available
        h.b.addButton.contentDescription = "Add ${p.name} to cart"
        h.b.addButton.setOnClickListener { onAdd(p) }
        h.b.root.setOnClickListener { onClick(p) }
    }
}

class RecommendationAdapter(
    private val onClick: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : ListAdapter<Recommendation, RecommendationAdapter.VH>(diff { it.product.id }) {

    class VH(val b: ItemRecommendationBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = getItem(position)
        h.b.emoji.text = r.product.emoji
        h.b.name.text = r.product.name
        h.b.reason.text = r.reason
        h.b.price.text = Money.format(r.product.pricePence)
        h.b.addButton.setOnClickListener { onAdd(r.product) }
        h.b.root.setOnClickListener { onClick(r.product) }
    }
}

class CartAdapter(
    private val onQuantity: (CartLine, Int) -> Unit,
    private val onRemove: (CartLine) -> Unit
) : ListAdapter<CartLine, CartAdapter.VH>(diff { it.product.id }) {

    class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val line = getItem(position)
        h.b.emoji.text = line.product.emoji
        h.b.name.text = line.product.name
        h.b.quantity.text = line.quantity.toString()
        h.b.lineTotal.text = if (line.product.available)
            "${Money.format(line.product.pricePence)} each · ${Money.format(line.lineTotalPence)}"
        else "No longer available, please remove"
        h.b.minus.setOnClickListener { onQuantity(line, line.quantity - 1) }
        h.b.plus.setOnClickListener { onQuantity(line, line.quantity + 1) }
        h.b.remove.setOnClickListener { onRemove(line) }
    }
}

class OrderAdapter(private val onClick: (Order) -> Unit) :
    ListAdapter<Order, OrderAdapter.VH>(diff { it.id }) {

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val o = getItem(position)
        h.b.title.text = "Order #${o.id}"
        UiUtils.styleStatusChip(h.b.status, o.status)
        h.b.summary.text = o.items.joinToString { "${it.quantity}× ${it.productName}" }
        h.b.meta.text = "${UiUtils.dateTime(o.createdAt)} · ${Money.format(o.totalPence)}"
        h.b.root.setOnClickListener { onClick(o) }
    }
}

class AdminOrderAdapter(private val onAdvance: (Order) -> Unit) :
    ListAdapter<Order, AdminOrderAdapter.VH>(diff { it.id }) {

    class VH(val b: ItemAdminOrderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAdminOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val o = getItem(position)
        val next = o.status.next()
        h.b.text.text = "Order #${o.id} · ${o.status.label}\n" +
            o.items.joinToString { "${it.quantity}× ${it.productName}" } +
            "\nLast change ${UiUtils.time(o.updatedAt)}"
        h.b.advance.text = next?.let { "→ ${it.label.substringBefore(' ')}" } ?: "Done"
        h.b.advance.isEnabled = next != null
        h.b.advance.setOnClickListener { onAdvance(o) }
    }
}

/** DiffUtil callback keyed on a stable id; data classes give us content equality for free. */
private fun <T : Any> diff(id: (T) -> Any) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(a: T, b: T) = id(a) == id(b)
    @android.annotation.SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(a: T, b: T) = a == b
}
