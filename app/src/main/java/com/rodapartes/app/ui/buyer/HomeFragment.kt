package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.util.LocationHelper
import com.rodapartes.app.util.PlaceResult
import com.rodapartes.app.util.viewScope
import kotlinx.coroutines.launch
import com.rodapartes.app.databinding.FragmentHomeBinding
import com.rodapartes.app.databinding.ItemCategoryBinding
import com.rodapartes.app.ui.common.ProductGridAdapter
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack

/** Inicio del comprador: saludo, su moto, categorías, destacados y tiendas cercanas. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val host get() = requireActivity() as BuyerActivity
    private var listener: ListenerRegistration? = null
    private var lastPlace: PlaceResult? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        val user = Session.user
        binding.tvGreeting.text = if (user != null)
            getString(R.string.home_greeting, user.name.substringBefore(" "))
        else getString(R.string.home_greeting_guest)
        binding.tvMoto.text = user?.moto?.ifBlank { null } ?: getString(R.string.home_choose_moto)

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

        // Destacados: las 4 publicaciones aprobadas más recientes (Firestore en tiempo real)
        val adapter = ProductGridAdapter(emptyList(), user?.moto.orEmpty()) { product ->
            startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra(ProductDetailActivity.EXTRA_ID, product.id))
        }
        binding.rvFeatured.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvFeatured.adapter = adapter
        listener = ProductRepository.listenCatalog { products ->
            adapter.submit(products.filter { it.stock > 0 }.take(4))
            val stores = products.map { it.sellerId }.distinct().size
            _binding?.tvNearbyCount?.text = resources.getQuantityString(R.plurals.stores_count, stores, stores)
        }

        // Dirección de entrega: se toma de la ubicación actual si ya hay permiso
        if (LocationHelper.hasPermission(requireContext())) {
            viewScope.launch {
                runCatching { LocationHelper.current(requireContext()) }.getOrNull()?.let { place ->
                    lastPlace = place
                    _binding?.tvDeliverTo?.text = getString(R.string.home_deliver_to_value, place.address)
                }
            }
        }

        binding.searchPill.setOnClickListener { host.openCatalog() }
        binding.btnSeeAll.setOnClickListener { host.openCatalog() }
        binding.cardMoto.setOnClickListener { host.openCatalog(onlyCompatible = true) }
        binding.btnNotifications.setOnClickListener { view.snack(getString(R.string.home_no_notifications)) }
        // Abre Google Maps con la ubicación actual para ver las tiendas de la zona
        binding.btnMap.setOnClickListener {
            val place = lastPlace
            if (place != null) LocationHelper.openInMaps(requireContext(), place.lat, place.lng, getString(R.string.home_map_label))
            else view.snack(getString(R.string.home_map_need_location))
        }
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
