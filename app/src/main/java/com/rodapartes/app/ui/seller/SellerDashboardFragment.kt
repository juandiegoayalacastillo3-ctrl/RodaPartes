package com.rodapartes.app.ui.seller

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.FragmentSellerDashboardBinding
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.ui.common.bindOrder
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.color
import com.rodapartes.app.util.visible
import java.util.Calendar

/**
 * Panel de ventas del vendedor. Todas las cifras se calculan con sus órdenes y productos
 * de Firestore: ventas y pedidos del mes, pendientes por despachar, stock bajo y
 * un gráfico de ventas de los últimos 7 días.
 */
class SellerDashboardFragment : Fragment(R.layout.fragment_seller_dashboard) {

    private var _binding: FragmentSellerDashboardBinding? = null
    private val binding get() = _binding!!
    private var orders: List<Order> = emptyList()
    private var products: List<Product> = emptyList()
    private val listeners = mutableListOf<ListenerRegistration>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSellerDashboardBinding.bind(view)
        val user = Session.user
        binding.toolbar.subtitle = if (user?.statusEnum == UserStatus.PENDIENTE)
            getString(R.string.seller_pending_subtitle, user.displayName) else user?.displayName

        binding.cardLowStock.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_products) }
        binding.btnAllOrders.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_seller_orders) }

        val uid = user?.uid.orEmpty()
        listeners += OrderRepository.listenBySeller(uid) { orders = it; render() }
        listeners += ProductRepository.listenBySeller(uid) { products = it; render() }
    }

    /** Recalcula los indicadores cada vez que cambian las órdenes o los productos. */
    private fun render() {
        if (_binding == null) return
        val valid = orders.filter { it.statusEnum !in listOf(OrderStatus.CANCELADO, OrderStatus.REVERSION, OrderStatus.RETRACTO) }
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        }.time
        val thisMonth = valid.filter { it.createdAt?.toDate()?.after(monthStart) == true }
        val lowStock = products.filter { it.lowStock }

        binding.statSales.tvValue.text = Money.short(thisMonth.sumOf { it.subtotal })
        binding.statSales.tvLabel.text = getString(R.string.stat_sales_month)
        binding.statOrders.tvValue.text = thisMonth.size.toString()
        binding.statOrders.tvLabel.text = getString(R.string.stat_orders_month)
        binding.statDispatch.tvValue.text = orders.count {
            it.statusEnum == OrderStatus.NUEVO || it.statusEnum == OrderStatus.PREPARACION
        }.toString()
        binding.statDispatch.tvLabel.text = getString(R.string.stat_to_dispatch)
        binding.statLowStock.tvValue.text = lowStock.size.toString()
        binding.statLowStock.tvValue.setTextColor(requireContext().color(if (lowStock.isEmpty()) R.color.texto else R.color.peligro))
        binding.statLowStock.tvLabel.text = getString(R.string.stat_low_stock)

        // Ventas de los últimos 7 días (en miles de pesos), hoy es la última barra
        val days = (6 downTo 0).map { back ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        }
        val labels = resources.getStringArray(R.array.dias_semana)
        val values = days.map { day ->
            valid.filter { o ->
                val c = Calendar.getInstance().apply { time = o.createdAt?.toDate() ?: return@filter false }
                c.get(Calendar.YEAR) == day.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
            }.sumOf { it.subtotal } / 1000f
        }
        // Calendar: domingo = 1 ... sábado = 7; el arreglo empieza en lunes
        binding.chart.setData(values, days.map { labels[(it.get(Calendar.DAY_OF_WEEK) + 5) % 7] })
        binding.tvChartTotal.text = getString(R.string.seller_chart_total, Money.format((values.sum() * 1000).toLong()))

        binding.cardLowStock.visible(lowStock.isNotEmpty())
        binding.tvLowStock.text = getString(R.string.seller_low_stock_alert, lowStock.joinToString { it.name })

        // Pedidos recientes con el mismo diseño de las listas
        binding.llRecent.removeAllViews()
        orders.take(3).forEach { o ->
            val item = ItemOrderBinding.inflate(layoutInflater, binding.llRecent, false)
            item.bindOrder(o, showBuyer = false)
            item.tvParties.text = o.buyerName
            item.root.setOnClickListener { (requireActivity() as SellerActivity).openTab(R.id.nav_seller_orders) }
            binding.llRecent.addView(item.root)
        }
        if (orders.isEmpty()) {
            binding.llRecent.addView(android.widget.TextView(requireContext()).apply {
                text = getString(R.string.seller_orders_empty_body)
                setTextAppearance(R.style.TextAppearance_RodaPartes_Small)
            })
        }
    }

    override fun onDestroyView() {
        listeners.forEach { it.remove() }
        listeners.clear()
        super.onDestroyView()
        _binding = null
    }
}
