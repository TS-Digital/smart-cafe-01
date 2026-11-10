package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.databinding.FragmentMenuBinding
import com.p2897777.ctec3703spec.model.Category
import com.p2897777.ctec3703spec.model.Product

class MenuFragment : Fragment() {

    private var _b: FragmentMenuBinding? = null
    private val b get() = _b!!
    private val vm: MenuViewModel by viewModels()

    private val productAdapter = ProductAdapter(::openProduct) { vm.addToCart(it) }
    private val recAdapter = RecommendationAdapter(::openProduct) { vm.addToCart(it, fromRecommendation = true) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentMenuBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.productList.layoutManager = LinearLayoutManager(requireContext())
        b.productList.adapter = productAdapter
        b.recsList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.recsList.adapter = recAdapter

        buildCategoryChips()
        b.searchInput.setText(vm.query)
        b.searchInput.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }
        b.availableOnly.isChecked = vm.availableOnly
        b.availableOnly.setOnCheckedChangeListener { _, checked -> vm.setAvailableOnly(checked) }

        vm.ratings.observe(viewLifecycleOwner) { productAdapter.ratings = it }
        vm.products.observe(viewLifecycleOwner) { list ->
            productAdapter.submitList(list)
            val filtering = vm.query.isNotBlank() || vm.category != null || vm.availableOnly
            b.recsSection.visibility = if (filtering) View.GONE else View.VISIBLE
            b.menuTitle.text = if (filtering) "${list.size} result${if (list.size == 1) "" else "s"}" else getString(R.string.nav_menu)
            b.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            b.emptyText.text = "Nothing matches \"${vm.query}\". Try another search or category."
        }
        vm.recommendations.observe(viewLifecycleOwner) { recAdapter.submitList(it) }
        vm.weatherLine.observe(viewLifecycleOwner) { b.weatherLine.text = it }
        vm.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
                vm.messageShown()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun buildCategoryChips() {
        val group = b.categoryChips
        group.removeAllViews()
        val options: List<Category?> = listOf(null) + Category.entries
        options.forEach { cat ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = cat?.label ?: getString(R.string.all)
                isCheckable = true
                isChecked = cat == vm.category
                setOnClickListener { vm.setCategory(cat) }
            }
            group.addView(chip)
        }
    }

    private fun openProduct(p: Product) {
        startActivity(Intent(requireContext(), ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_ID, p.id))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
