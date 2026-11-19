package com.rodapartes.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.AuditRepository
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentAdminSalesBinding
import com.rodapartes.app.databinding.ItemCategoryBarBinding
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.ui.common.SalesStats
import com.rodapartes.app.ui.common.bindOrder
import com.rodapartes.app.util.Dates
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.util.visible

/**
 * Reporte de ventas del administrador, calculado con las órdenes de Firestore:
 * cifras por periodo, tendencia, participación por categoría y la lista de órdenes
 * (con retractos y reversiones para aprobar la devolución). Se puede exportar a CSV.
 */
class AdminSalesFragment : Fragment(R.layout.fragment_admin_sales) {

    private var _binding: FragmentAdminSalesBinding? = null
    private val binding get() = _binding!!
    private var orderFilter: OrderStatus? = null
    private var orders: List<Order> = emptyList()
    private var periodChip = R.id.chipMonth
    private var listener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminSalesBinding.bind(view)

        binding.chipPeriod.setOnCheckedStateChangeListener { _, ids ->
            periodChip = ids.firstOrNull() ?: R.id.chipMonth
            render()
        }
        buildOrderFilters()
        binding.btnExport.setOnClickListener { exportCsv() }

        listener = OrderRepository.listenAll { list ->
            orders = list
            render()
        }
    }

    /** Pinta cifras, gráfico, categorías y lista para el periodo elegido. */
    private fun render() {
        if (_binding == null) return
        val days = when (periodChip) {
            R.id.chipWeek -> 7
            R.id.chipSemester -> 182
            else -> 30
        }
        val sales = SalesStats.lastDays(SalesStats.valid(orders), days)
        val total = sales.sumOf { it.total }

        binding.statTotal.tvValue.text = Money.short(total)
        binding.statTotal.tvLabel.text = getString(R.string.stat_sales_total)
        binding.statOrders.tvValue.text = sales.size.toString()
        binding.statOrders.tvLabel.text = getString(R.string.stat_orders)
        binding.statTicket.tvValue.text = Money.short(if (sales.isEmpty()) 0L else total / sales.size)
        binding.statTicket.tvLabel.text = getString(R.string.stat_ticket)
        binding.statCommission.tvValue.text = Money.short((total * 0.08).toLong())
        binding.statCommission.tvLabel.text = getString(R.string.stat_commission)

        val (values, labels) = when (periodChip) {
            R.id.chipWeek -> SalesStats.byDay(sales, resources.getStringArray(R.array.dias_semana))
            R.id.chipSemester -> SalesStats.byMonth(sales, 6)
            else -> SalesStats.byWeek(sales)
        }
        binding.tvChartTitle.setText(when (periodChip) {
            R.id.chipWeek -> R.string.chart_week
            R.id.chipSemester -> R.string.chart_semester
            else -> R.string.chart_month
        })
        binding.chart.setData(values, labels)

        // Participación por categoría
        binding.llCategories.removeAllViews()
        val shares = SalesStats.byCategory(sales)
        shares.forEach { (category, pct) ->
            val row = ItemCategoryBarBinding.inflate(layoutInflater, binding.llCategories, false)
            row.tvName.text = Category.from(category).label
            row.tvValue.text = getString(R.string.percent, pct)
            row.progress.progress = pct
            binding.llCategories.addView(row.root)
        }
        if (shares.isEmpty()) {
            binding.llCategories.addView(android.widget.TextView(requireContext()).apply {
                text = getString(R.string.sales_no_data)
                setTextAppearance(R.style.TextAppearance_RodaPartes_Small)
                setPadding(0, 24, 0, 0)
            })
        }
        renderOrders()
    }

    /** Crea los chips para filtrar las órdenes por estado. */
    private fun buildOrderFilters() {
        val filters = listOf(null, OrderStatus.NUEVO, OrderStatus.ENTREGADO, OrderStatus.RETRACTO, OrderStatus.REVERSION)
        filters.forEachIndexed { i, status ->
            binding.chipOrders.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.label ?: getString(R.string.filter_all)
                tag = i
                isCheckable = true
                isChecked = i == 0
            })
        }
        binding.chipOrders.setOnCheckedStateChangeListener { group, ids ->
            orderFilter = ids.firstOrNull()?.let { filters[group.findViewById<Chip>(it).tag as Int] }
            renderOrders()
        }
    }

    /** Lista de órdenes (todas las tiendas) con el filtro de estado. */
    private fun renderOrders() {
        binding.llOrders.removeAllViews()
        val list = orders.filter { orderFilter == null || it.statusEnum == orderFilter }
        list.take(50).forEach { o ->
            val item = ItemOrderBinding.inflate(layoutInflater, binding.llOrders, false)
            item.bindOrder(o, showBuyer = true)
            item.root.setOnClickListener { showOrder(o) }
            binding.llOrders.addView(item.root)
        }
        binding.btnExport.visible(orders.isNotEmpty())
    }

    /** Retractos y reversiones: el admin aprueba la devolución dentro de los plazos legales. */
    private fun showOrder(o: Order) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_code, o.code))
            .setMessage(getString(R.string.order_admin_msg, o.buyerName, o.sellerName, o.summary,
                Money.format(o.total), o.statusEnum.label, o.paymentRef))
            .setNegativeButton(R.string.action_close, null)
        if (o.statusEnum == OrderStatus.RETRACTO || o.statusEnum == OrderStatus.REVERSION) {
            builder.setMessage(getString(R.string.order_admin_legal_msg, o.buyerName, o.summary, Money.format(o.total),
                getString(if (o.statusEnum == OrderStatus.RETRACTO) R.string.legal_retract else R.string.legal_reversal)))
            builder.setPositiveButton(R.string.order_refund) { _, _ ->
                viewScope.safeLaunch(binding.root) {
                    OrderRepository.setStatus(o.id, OrderStatus.CANCELADO)
                    AuditRepository.log("Aprobó la devolución de #${o.code} (${o.paymentRef})")
                    binding.root.snack(getString(R.string.order_refunded, o.code))
                }
            }
        }
        builder.show()
    }

    /** Comparte las órdenes como CSV (se puede abrir en Excel o enviar por correo). */
    private fun exportCsv() {
        val csv = buildString {
            appendLine("codigo,fecha,comprador,tienda,productos,total,estado,referencia_pago")
            orders.forEach { o ->
                appendLine(listOf(o.code, Dates.short(o.createdAt?.toDate()), o.buyerName, o.sellerName,
                    o.summary, o.total.toString(), o.statusEnum.label, o.paymentRef)
                    .joinToString(",") { "\"" + it.replace("\"", "'") + "\"" })
            }
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sales_title))
            putExtra(Intent.EXTRA_TEXT, csv)
        }, getString(R.string.sales_export)))
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
