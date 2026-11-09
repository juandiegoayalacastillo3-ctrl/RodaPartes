package com.rodapartes.app.ui.buyer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.MockData
import com.rodapartes.app.databinding.FragmentCatalogBinding
import com.rodapartes.app.ui.common.ProductGridAdapter
import com.rodapartes.app.ui.seller.CameraActivity
import com.rodapartes.app.util.visible

/** Pestaña "Buscar": catálogo con búsqueda por texto/referencia y filtros. */
class CatalogFragment : Fragment(R.layout.fragment_catalog) {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductGridAdapter
    private var selectedCategory: Category? = null

    // Recibe la referencia leída con la cámara (escáner)
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(CameraActivity.EXTRA_CODE)?.let { binding.etSearch.setText(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCatalogBinding.bind(view)

        selectedCategory = arguments?.getString(ARG_CATEGORY)?.let { Category.valueOf(it) }
        binding.swCompatible.isChecked = arguments?.getBoolean(ARG_COMPATIBLE) ?: false
        binding.swCompatible.text = getString(R.string.catalog_only_compatible_moto, MockData.myMoto)

        adapter = ProductGridAdapter(emptyList()) { product ->
            startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_ID, product.id))
        }
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        buildChips()

        binding.etSearch.doAfterTextChanged { applyFilters() }
        binding.swCompatible.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.tilSearch.setEndIconOnClickListener {
            scanLauncher.launch(Intent(requireContext(), CameraActivity::class.java)
                .putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_SCAN))
        }

        applyFilters()
    }

    /** Crea un Chip por categoría (más "Todo") dentro del ChipGroup. */
    private fun buildChips() {
        val all = Chip(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.filter_all)
            isCheckable = true
            isChecked = selectedCategory == null
        }
        binding.chipCategories.addView(all)
        Category.entries.forEach { category ->
            binding.chipCategories.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category.label
                tag = category
                isCheckable = true
                isChecked = category == selectedCategory
            })
        }
        binding.chipCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedCategory = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it).tag as? Category }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val onlyCompatible = binding.swCompatible.isChecked
        val result = MockData.catalog().filter { p ->
            (selectedCategory == null || p.category == selectedCategory) &&
                (!onlyCompatible || p.fits(MockData.myMoto)) &&
                (query.isEmpty() ||
                    p.name.contains(query, ignoreCase = true) ||
                    p.ref.contains(query, ignoreCase = true) ||
                    p.compatible.any { it.contains(query, ignoreCase = true) })
        }
        adapter.submit(result)
        binding.tvCount.text = resources.getQuantityString(R.plurals.products_count, result.size, result.size)

        binding.empty.root.visible(result.isEmpty())
        binding.empty.tvEmptyTitle.text = getString(R.string.catalog_empty_title)
        binding.empty.tvEmptyBody.text = getString(R.string.catalog_empty_body)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_COMPATIBLE = "compatible"

        fun newInstance(category: Category?, onlyCompatible: Boolean) = CatalogFragment().apply {
            arguments = bundleOf(ARG_CATEGORY to category?.name, ARG_COMPATIBLE to onlyCompatible)
        }
    }
}
