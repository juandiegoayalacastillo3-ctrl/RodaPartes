package com.rodapartes.app.ui.seller

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.ActivitySellerBinding
import com.rodapartes.app.util.color

/** Interfaz del Vendedor: Panel, Productos, Pedidos y Mi tienda (Bottom Navigation verde azulado). */
class SellerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_products -> SellerProductsFragment()
                R.id.nav_seller_orders -> SellerOrdersFragment()
                R.id.nav_store -> SellerProfileFragment()
                else -> SellerDashboardFragment()
            }
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }
        if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.nav_panel
    }

    override fun onResume() {
        super.onResume()
        updateOrdersBadge()
    }

    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** Número de pedidos nuevos sobre el ícono de Pedidos. */
    fun updateOrdersBadge() {
        val nuevos = MockData.sellerOrders().count { it.status == OrderStatus.NUEVO }
        if (nuevos > 0) {
            binding.bottomNav.getOrCreateBadge(R.id.nav_seller_orders).apply {
                number = nuevos
                backgroundColor = color(R.color.naranja)
                isVisible = true
            }
        } else binding.bottomNav.removeBadge(R.id.nav_seller_orders)
    }
}
