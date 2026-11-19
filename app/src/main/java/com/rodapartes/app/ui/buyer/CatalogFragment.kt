package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.databinding.FragmentCatalogBinding
import com.rodapartes.app.ui.common.ProductGridAdapter
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.visible

/**
 * Pestaña "Buscar": catálogo de publicaciones aprobadas (RF-INI-02).
 * Los productos llegan de Firestore en tiempo real y se filtran en el celular por texto,
 * referencia, categoría y compatibilidad con la moto del comprador.
 */
class CatalogFragment : Fragment(R.layout.fragment_catalog) {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductGridAdapter
    private var listener: ListenerRegistration? = null
    private var all: List<Product> = emptyList()
    private var selectedCategory: Category? = null
    private val myMoto get() = Session.user?.moto.orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCatalogBinding.bind(view)

        selectedCategory = arguments?.getString(ARG_CATEGORY)?.let { Category.valueOf(it) }
        binding.swCompatible.isChecked = arguments?.getBoolean(ARG_COMPATIBLE) ?: false
        binding.swCompatible.visible(myMoto.isNotBlank())
        binding.swCompatible.text = getString(R.string.catalog_only_compatible_moto, myMoto)

        adapter = ProductGridAdapter(emptyList(), myMoto) { product ->
            startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_ID, product.id))
        }
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        buildChips()
        binding.etSearch.doAfterTextChanged { applyFilters() }
        binding.swCompatible.setOnCheckedChangeListener { _, _ -> applyFilters() }

        listener = ProductRepository.listenCatalog { products ->
            all = products
            if (_binding != null) applyFilters()
        }
    }

    /** Crea un Chip por categoría (más "Todo") dentro del ChipGroup. */
    private fun buildChips() {
        binding.chipCategories.addView(Chip(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.filter_all)
            isCheckable = true
            isChecked = selectedCategory == null
        })
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

    /** Aplica búsqueda, categoría y compatibilidad sobre la lista recibida de Firestore. */
    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val onlyCompatible = binding.swCompatible.isChecked && myMoto.isNotBlank()
        val result = all.filter { p ->
            (selectedCategory == null || p.categoryEnum == selectedCategory) &&
                (!onlyCompatible || p.fits(myMoto)) &&
                (query.isEmpty() ||
                    p.name.contains(query, ignoreCase = true) ||
                    p.ref.contains(query, ignoreCase = true) ||
                    p.sellerName.contains(query, ignoreCase = true) ||
                    p.compatible.any { it.contains(query, ignoreCase = true) })
        }
        adapter.submit(result)
        binding.tvCount.text = resources.getQuantityString(R.plurals.products_count, result.size, result.size)
        binding.empty.root.visible(result.isEmpty())
        binding.empty.tvEmptyTitle.text = getString(R.string.catalog_empty_title)
        binding.empty.tvEmptyBody.text = getString(R.string.catalog_empty_body)
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_COMPATIBLE = "compatible"

        /** Crea el catálogo ya filtrado por categoría o por compatibilidad con mi moto. */
        fun newInstance(category: Category?, onlyCompatible: Boolean) = CatalogFragment().apply {
            arguments = bundleOf(ARG_CATEGORY to category?.name, ARG_COMPATIBLE to onlyCompatible)
        }
    }
}
