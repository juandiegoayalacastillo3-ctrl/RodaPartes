package com.rodapartes.app.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.databinding.ActivityBuyerBinding
import com.rodapartes.app.ui.auth.LoginActivity
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.color

/**
 * Interfaz del Comprador. Cambia de fragmento según la opción de la Bottom Navigation:
 * Inicio, Buscar (catálogo), Carrito, Compras y Perfil.
 */
class BuyerActivity : AppCompatActivity() {

    lateinit var binding: ActivityBuyerBinding

    /** Carrito actual (lo comparten el badge, el detalle y el checkout). */
    var cart: List<CartItem> = emptyList()
        private set
    private var cartListener: ListenerRegistration? = null

    // Filtros que se le pasan al catálogo cuando se abre desde el inicio
    private var pendingCategory: Category? = null
    private var pendingCompatible = false

    private val tabsNeedLogin = setOf(R.id.nav_cart, R.id.nav_orders, R.id.nav_profile)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Escucha el carrito en Firestore para mantener el número del ícono al día
        Session.user?.let { user ->
            cartListener = CartRepository.listen(user.uid) { items ->
                cart = items
                updateCartBadge()
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId in tabsNeedLogin && !Session.isLoggedIn) {
                askLogin()
                false
            } else {
                show(item.itemId)
                true
            }
        }
        // Volver a tocar la pestaña actual no recarga la pantalla
        binding.bottomNav.setOnItemReselectedListener { }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = intent.getIntExtra(EXTRA_TAB, R.id.nav_home)
        }
    }

    /** Si se abre de nuevo con una pestaña indicada, cambia a esa pestaña. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        binding.bottomNav.selectedItemId = intent.getIntExtra(EXTRA_TAB, R.id.nav_home)
    }

    /** Deja de escuchar el carrito en Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        cartListener?.remove()
        super.onDestroy()
    }

    /** Muestra el fragmento de la pestaña elegida. */
    private fun show(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_search -> CatalogFragment.newInstance(pendingCategory, pendingCompatible)
            R.id.nav_cart -> CartFragment()
            R.id.nav_orders -> OrdersFragment()
            R.id.nav_profile -> ProfileFragment()
            else -> HomeFragment()
        }
        pendingCategory = null
        pendingCompatible = false
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Abre la pestaña Buscar con un filtro ya aplicado. */
    fun openCatalog(category: Category? = null, onlyCompatible: Boolean = false) {
        pendingCategory = category
        pendingCompatible = onlyCompatible
        if (binding.bottomNav.selectedItemId == R.id.nav_search) show(R.id.nav_search)
        else binding.bottomNav.selectedItemId = R.id.nav_search
    }

    /** Cambia de pestaña desde otro fragmento. */
    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** Número de productos en el ícono del carrito. */
    fun updateCartBadge() {
        val count = cart.sumOf { it.qty }.toInt()
        if (count > 0) {
            binding.bottomNav.getOrCreateBadge(R.id.nav_cart).apply {
                number = count
                backgroundColor = color(R.color.naranja)
                badgeTextColor = color(R.color.blanco)
                isVisible = true
            }
        } else {
            binding.bottomNav.removeBadge(R.id.nav_cart)
        }
    }

    /** Diálogo para invitados que intentan usar algo que exige cuenta. */
    fun askLogin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guest_title)
            .setMessage(R.string.guest_message)
            .setPositiveButton(R.string.login_button) { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
            }
            .setNegativeButton(R.string.action_not_now, null)
            .show()
    }

    companion object {
        const val EXTRA_TAB = "tab"

        /** Intent para volver a una pestaña concreta sin duplicar la actividad. */
        fun intentForTab(context: Context, tab: Int) =
            Intent(context, BuyerActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
