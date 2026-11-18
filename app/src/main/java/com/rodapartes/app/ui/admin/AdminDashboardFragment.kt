package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.AppUser
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.Status
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.FragmentAdminDashboardBinding
import com.rodapartes.app.databinding.ViewPendingRowBinding
import com.rodapartes.app.ui.common.SalesStats
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.applyStatus

/**
 * Dashboard principal del administrador. Escucha usuarios, productos y órdenes en Firestore
 * y calcula en vivo las cifras de la plataforma y los pendientes por revisar.
 */
class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val host get() = requireActivity() as AdminActivity
    private val listeners = mutableListOf<ListenerRegistration>()
    private var users: List<AppUser> = emptyList()
    private var products: List<Product> = emptyList()
    private var orders: List<Order> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminDashboardBinding.bind(view)
        binding.tvHello.text = getString(R.string.home_greeting, Session.user?.name.orEmpty().substringBefore(" "))

        listeners += UserRepository.listenAll { users = it; render() }
        listeners += ProductRepository.listenAll { products = it; render() }
        listeners += OrderRepository.listenAll { orders = it; render() }
    }

    /** Recalcula cifras, pendientes y gráfico con los datos actuales. */
    private fun render() {
        if (_binding == null) return
        val sales = SalesStats.valid(orders)
        val thisMonth = SalesStats.lastDays(sales, 30)

        binding.statUsers.tvValue.text = users.size.toString()
        binding.statUsers.tvLabel.text = getString(R.string.stat_users)
        binding.statSellers.tvValue.text = users.count { it.roleEnum == Role.SELLER }.toString()
        binding.statSellers.tvLabel.text = getString(R.string.stat_sellers)
        binding.statProducts.tvValue.text = products.count { it.statusEnum == ProductStatus.PUBLICADO }.toString()
        binding.statProducts.tvLabel.text = getString(R.string.stat_products_published)
        binding.statSales.tvValue.text = Money.short(thisMonth.sumOf { it.total })
        binding.statSales.tvLabel.text = getString(R.string.stat_sales_30)

        val sellersPending = users.count { it.roleEnum == Role.SELLER && it.statusEnum == UserStatus.PENDIENTE }
        val productsPending = products.count { it.statusEnum == ProductStatus.EN_REVISION || it.reports > 0 }
        val reversals = orders.count { it.statusEnum == OrderStatus.REVERSION }
        val retracts = orders.count { it.statusEnum == OrderStatus.RETRACTO }

        pending(binding.pendingSellers, R.drawable.ic_store, R.string.pending_sellers, R.string.pending_sellers_sub,
            sellersPending, UserStatus.PENDIENTE) { host.openTab(R.id.nav_admin_users) }
        pending(binding.pendingProducts, R.drawable.ic_box, R.string.pending_products, R.string.pending_products_sub,
            productsPending, ProductStatus.EN_REVISION) { host.openTab(R.id.nav_admin_products) }
        pending(binding.pendingReversals, R.drawable.ic_card, R.string.pending_reversals, R.string.pending_reversals_sub,
            reversals, OrderStatus.REVERSION) { host.openTab(R.id.nav_admin_sales) }
        pending(binding.pendingRetracts, R.drawable.ic_history, R.string.pending_retracts, R.string.pending_retracts_sub,
            retracts, OrderStatus.RETRACTO) { host.openTab(R.id.nav_admin_sales) }

        // Ventas de los últimos 6 meses (millones de pesos)
        val (values, labels) = SalesStats.byMonth(sales, 6)
        binding.chart.setData(values, labels)
    }

    /** Llena una fila de pendientes con su ícono, textos y contador. */
    private fun pending(
        row: ViewPendingRowBinding, icon: Int, title: Int, detail: Int,
        count: Int, style: Status, onClick: () -> Unit
    ) {
        row.ivIcon.setImageResource(icon)
        row.tvTitle.setText(title)
        row.tvDetail.setText(detail)
        row.tvCount.applyStatus(style)
        row.tvCount.text = count.toString()
        row.root.setOnClickListener { onClick() }
    }

    override fun onDestroyView() {
        listeners.forEach { it.remove() }
        listeners.clear()
        super.onDestroyView()
        _binding = null
    }
}
