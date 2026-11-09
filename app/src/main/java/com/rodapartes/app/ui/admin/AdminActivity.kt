package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.databinding.ActivityAdminBinding
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session

/**
 * Interfaz del Administrador. Tiene dos menús de navegación:
 * - Bottom Navigation: Panel, Usuarios, Productos y Ventas.
 * - Navigation Drawer: las mismas secciones + bitácora, políticas y cerrar sesión.
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    // Relación entre opciones de la barra inferior y del menú lateral
    private val bottomToDrawer = mapOf(
        R.id.nav_admin_panel to R.id.drawer_panel,
        R.id.nav_admin_users to R.id.drawer_users,
        R.id.nav_admin_products to R.id.drawer_products,
        R.id.nav_admin_sales to R.id.drawer_sales
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val session = Session(this)
        val header = binding.navView.getHeaderView(0)
        header.findViewById<TextView>(R.id.tvAdminName).text = session.name
        header.findViewById<TextView>(R.id.tvAdminEmail).text = session.email

        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val (fragment, title) = when (item.itemId) {
                R.id.nav_admin_users -> AdminUsersFragment() to R.string.nav_users
                R.id.nav_admin_products -> AdminProductsFragment() to R.string.admin_moderation_title
                R.id.nav_admin_sales -> AdminSalesFragment() to R.string.sales_title
                else -> AdminDashboardFragment() to R.string.admin_title
            }
            show(fragment)
            binding.toolbar.setTitle(title)
            bottomToDrawer[item.itemId]?.let { binding.navView.setCheckedItem(it) }
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            val bottomId = bottomToDrawer.entries.firstOrNull { it.value == item.itemId }?.key
            when {
                bottomId != null -> binding.bottomNav.selectedItemId = bottomId
                item.itemId == R.id.drawer_audit -> showAudit()
                item.itemId == R.id.drawer_policies -> showPolicies()
                item.itemId == R.id.drawer_logout -> Router.logout(this)
            }
            bottomId != null
        }

        // El botón atrás primero cierra el menú lateral
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.nav_admin_panel
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** Bitácora de auditoría: solo lectura desde la app (RF-ADU-06). */
    private fun showAudit() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_audit)
            .setItems(resources.getStringArray(R.array.bitacora_demo), null)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showPolicies() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_policies)
            .setMessage(R.string.policies_msg)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }
}
