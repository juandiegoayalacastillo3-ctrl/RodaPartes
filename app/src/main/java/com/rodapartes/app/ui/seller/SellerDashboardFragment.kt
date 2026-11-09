package com.rodapartes.app.ui.seller

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentSellerDashboardBinding
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.color
import com.rodapartes.app.util.visible

/** Panel de ventas del vendedor. */
class SellerDashboardFragment : Fragment(R.layout.fragment_seller_dashboard) {

    private var _binding: FragmentSellerDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSellerDashboardBinding.bind(view)
        binding.toolbar.subtitle = Session(requireContext()).name

        val products = MockData.sellerProducts()
        val orders = MockData.sellerOrders()
        val lowStock = products.filter { it.lowStock }

        binding.statSales.tvValue.text = Money.short(2_418_500)
        binding.statSales.tvLabel.text = getString(R.string.stat_sales_month)
        binding.statOrders.tvValue.text = "37"
        binding.statOrders.tvLabel.text = getString(R.string.stat_orders_month)
        binding.statDispatch.tvValue.text = orders.count {
            it.status == OrderStatus.NUEVO || it.status == OrderStatus.PREPARACION
        }.toString()
        binding.statDispatch.tvLabel.text = getString(R.string.stat_to_dispatch)
        binding.statLowStock.tvValue.text = lowStock.size.toString()
        binding.statLowStock.tvValue.setTextColor(requireContext().color(if (lowStock.isEmpty()) R.color.texto else R.color.peligro))
        binding.statLowStock.tvLabel.text = getString(R.string.stat_low_stock)

        // Ventas de los últimos 7 días en miles de pesos
        val week = listOf(310f, 420f, 260f, 515f, 380f, 590f, 445f)
        binding.chart.setData(week, resources.getStringArray(R.array.dias_semana).toList())
        binding.tvChartTotal.text = getString(R.string.seller_chart_total, Money.format(week.sum().toInt() * 1000))

        binding.cardLowStock.visible(lowStock.isNotEmpty())
        binding.tvLowStock.text = getString(R.string.seller_low_stock_alert, lowStock.joinToString { it.name })
        binding.cardLowStock.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_products) }
        binding.btnAllOrders.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_seller_orders) }

        // Pedidos recientes (se inflan con el mismo layout de las listas)
        orders.take(3).forEach { o ->
            val item = ItemOrderBinding.inflate(layoutInflater, binding.llRecent, false)
            item.tvCode.text = getString(R.string.order_code, o.code)
            item.tvStatus.applyStatus(o.status)
            item.tvParties.text = o.buyer
            item.tvSummary.text = o.summary
            item.tvDate.text = o.date
            item.tvTotal.text = Money.format(o.total)
            item.root.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_seller_orders) }
            binding.llRecent.addView(item.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
