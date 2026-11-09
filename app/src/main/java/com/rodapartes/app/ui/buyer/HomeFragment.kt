package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.MockData
import com.rodapartes.app.databinding.FragmentHomeBinding
import com.rodapartes.app.databinding.ItemCategoryBinding
import com.rodapartes.app.ui.common.ProductGridAdapter
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val host get() = requireActivity() as BuyerActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        val session = Session(requireContext())
        binding.tvGreeting.text = if (session.loggedIn)
            getString(R.string.home_greeting, session.name.substringBefore(" "))
        else getString(R.string.home_greeting_guest)
        binding.tvMoto.text = MockData.myMoto

        // Accesos rápidos a categorías (se inflan desde item_category.xml)
        Category.entries.forEach { category ->
            val item = ItemCategoryBinding.inflate(layoutInflater, binding.llCategories, false)
            item.tile.setBackgroundResource(category.tileRes)
            item.ivIcon.setImageResource(category.iconRes)
            item.tvLabel.text = category.label
            item.root.contentDescription = category.label
            item.root.setOnClickListener { host.openCatalog(category) }
            binding.llCategories.addView(item.root)
        }

        // Destacados: los más vendidos (aquí, los primeros del catálogo)
        binding.rvFeatured.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvFeatured.adapter = ProductGridAdapter(MockData.catalog().take(4)) { product ->
            startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_ID, product.id))
        }

        binding.searchPill.setOnClickListener { host.openCatalog() }
        binding.btnSeeAll.setOnClickListener { host.openCatalog() }
        binding.cardMoto.setOnClickListener { host.openCatalog(onlyCompatible = true) }
        binding.btnNotifications.setOnClickListener { view.snack(getString(R.string.home_no_notifications)) }
        binding.btnMap.setOnClickListener { view.snack(getString(R.string.home_map_soon)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
