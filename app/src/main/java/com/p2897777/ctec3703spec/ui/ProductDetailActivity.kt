package com.p2897777.ctec3703spec.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.CartRepository
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.databinding.ActivityProductDetailBinding
import com.p2897777.ctec3703spec.model.Product
import com.p2897777.ctec3703spec.util.Money
import com.p2897777.ctec3703spec.util.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Product information: price, description, availability and rating (req 3.1.3). */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityProductDetailBinding
    private var product: Product? = null
    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.session.isLoggedIn) { finish(); return }
        b = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        quantity = savedInstanceState?.getInt("qty") ?: 1

        val id = intent.getLongExtra(EXTRA_ID, -1)
        lifecycleScope.launch {
            val (p, rating) = withContext(Dispatchers.IO) { app.products.findById(id) to app.products.averageRatings()[id] }
            if (p == null) {
                Snackbar.make(b.root, "This item could not be found", Snackbar.LENGTH_LONG).show()
                b.addButton.isEnabled = false
                return@launch
            }
            product = p
            b.toolbar.title = p.name
            b.emoji.text = p.emoji
            b.name.text = p.name
            b.category.text = p.category.label
            b.price.text = Money.format(p.pricePence)
            b.description.text = p.description
            b.availability.text = getString(if (p.available) R.string.available else R.string.unavailable)
            b.availability.setTextColor(
                ContextCompat.getColor(this@ProductDetailActivity, if (p.available) R.color.status_ready else R.color.status_cancelled)
            )
            b.rating.text = rating?.let { (avg, n) -> "★ %.1f from %d rating%s".format(avg, n, if (n == 1) "" else "s") } ?: "No ratings yet"
            updateQuantity()
        }

        b.minus.setOnClickListener { if (quantity > 1) { quantity--; updateQuantity() } }
        b.plus.setOnClickListener { if (quantity < Validators.MAX_QUANTITY) { quantity++; updateQuantity() } }
        b.addButton.setOnClickListener { addToCart() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("qty", quantity)
    }

    private fun updateQuantity() {
        val p = product ?: return
        b.quantity.text = quantity.toString()
        b.addButton.isEnabled = p.available
        b.addButton.text = if (p.available) "Add · ${Money.format(p.pricePence * quantity)}" else getString(R.string.unavailable)
    }

    private fun addToCart() {
        val p = product ?: return
        val userId = app.session.userId
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { app.cart.add(userId, p.id, quantity) }
            when (res) {
                is CartRepository.CartResult.Ok -> {
                    DataEvents.notifyCartChanged()
                    Snackbar.make(b.root, "Added $quantity × ${p.name}", Snackbar.LENGTH_SHORT)
                        .setAction("View cart") {
                            startActivity(
                                android.content.Intent(this@ProductDetailActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_TAB, R.id.nav_cart)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            )
                        }.show()
                }
                is CartRepository.CartResult.Error -> Snackbar.make(b.root, res.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
