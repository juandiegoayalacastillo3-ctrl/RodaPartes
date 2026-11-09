package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.FragmentAdminDashboardBinding
import com.rodapartes.app.databinding.ViewPendingRowBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.data.Status

/** Dashboard principal del administrador: cifras de la plataforma y pendientes. */
class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val host get() = requireActivity() as AdminActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminDashboardBinding.bind(view)

        binding.tvHello.text = getString(R.string.home_greeting, Session(requireContext()).name.substringBefore(" "))

        // Cifras de demostración (en producción vienen de consultas agregadas)
        binding.statUsers.tvValue.text = "1.284"
        binding.statUsers.tvLabel.text = getString(R.string.stat_users)
        binding.statSellers.tvValue.text = "96"
        binding.statSellers.tvLabel.text = getString(R.string.stat_sellers)
        binding.statProducts.tvValue.text = "3.410"
        binding.statProducts.tvLabel.text = getString(R.string.stat_products)
        binding.statSales.tvValue.text = Money.short(48_200_000)
        binding.statSales.tvLabel.text = getString(R.string.stat_sales_month)

        val sellersPending = MockData.users.count { it.role == Role.SELLER && it.status == UserStatus.PENDIENTE }
        val productsPending = MockData.products.count { it.status == ProductStatus.EN_REVISION || it.reports > 0 }
        val reversals = MockData.orders.count { it.status == OrderStatus.REVERSION }
        val retracts = MockData.orders.count { it.status == OrderStatus.RETRACTO }

        pending(binding.pendingSellers, R.drawable.ic_store, R.string.pending_sellers, R.string.pending_sellers_sub,
            sellersPending, UserStatus.PENDIENTE) { host.openTab(R.id.nav_admin_users) }
        pending(binding.pendingProducts, R.drawable.ic_box, R.string.pending_products, R.string.pending_products_sub,
            productsPending, ProductStatus.EN_REVISION) { host.openTab(R.id.nav_admin_products) }
        pending(binding.pendingReversals, R.drawable.ic_card, R.string.pending_reversals, R.string.pending_reversals_sub,
            reversals, OrderStatus.REVERSION) { host.openTab(R.id.nav_admin_sales) }
        pending(binding.pendingRetracts, R.drawable.ic_history, R.string.pending_retracts, R.string.pending_retracts_sub,
            retracts, OrderStatus.RETRACTO) { host.openTab(R.id.nav_admin_sales) }

        // Ventas de los últimos 6 meses (millones de pesos)
        binding.chart.setData(listOf(31f, 35f, 38f, 36f, 44f, 48f),
            resources.getStringArray(R.array.meses_corto).toList())
    }

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
        super.onDestroyView()
        _binding = null
    }
}
