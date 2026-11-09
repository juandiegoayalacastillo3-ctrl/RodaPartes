package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentAdminSalesBinding
import com.rodapartes.app.databinding.ItemCategoryBarBinding
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.snack

/** Reporte de ventas: cifras por periodo, tendencia, categorías y órdenes con retractos/reversiones. */
class AdminSalesFragment : Fragment(R.layout.fragment_admin_sales) {

    private var _binding: FragmentAdminSalesBinding? = null
    private val binding get() = _binding!!
    private var orderFilter: OrderStatus? = null

    /** Datos de demostración por periodo. */
    private data class Period(
        val total: Long, val orders: Int, val chartTitle: Int,
        val values: List<Float>, val labels: List<String>
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminSalesBinding.bind(view)

        binding.chipPeriod.setOnCheckedStateChangeListener { _, ids -> showPeriod(ids.firstOrNull() ?: R.id.chipMonth) }
        showPeriod(R.id.chipMonth)
        buildCategories()
        buildOrderFilters()
        renderOrders()

        binding.btnExport.setOnClickListener { view.snack(getString(R.string.sales_exported)) }
    }

    private fun showPeriod(chipId: Int) {
        val p = when (chipId) {
            R.id.chipWeek -> Period(11_620_000, 241, R.string.chart_week,
                listOf(1.4f, 1.9f, 1.2f, 2.1f, 1.6f, 2.3f, 1.1f), resources.getStringArray(R.array.dias_semana).toList())
            R.id.chipSemester -> Period(232_000_000, 4_870, R.string.chart_semester,
                listOf(31f, 35f, 38f, 36f, 44f, 48f), resources.getStringArray(R.array.meses_corto).toList())
            else -> Period(48_200_000, 1_012, R.string.chart_month,
                listOf(10.8f, 12.1f, 11.4f, 13.9f), listOf("S1", "S2", "S3", "S4"))
        }
        binding.statTotal.tvValue.text = Money.short(p.total)
        binding.statTotal.tvLabel.text = getString(R.string.stat_sales_total)
        binding.statOrders.tvValue.text = String.format("%,d", p.orders).replace(',', '.')
        binding.statOrders.tvLabel.text = getString(R.string.stat_orders)
        binding.statTicket.tvValue.text = Money.short(p.total / p.orders)
        binding.statTicket.tvLabel.text = getString(R.string.stat_ticket)
        binding.statCommission.tvValue.text = Money.short((p.total * 0.08).toLong())
        binding.statCommission.tvLabel.text = getString(R.string.stat_commission)
        binding.tvChartTitle.setText(p.chartTitle)
        binding.chart.setData(p.values, p.labels)
    }

    private fun buildCategories() {
        val shares = listOf(
            R.string.cat_frenos to 30, R.string.cat_aceites to 24, R.string.cat_llantas to 19,
            R.string.cat_cascos to 15, R.string.cat_electrico to 12
        )
        shares.forEach { (name, pct) ->
            val row = ItemCategoryBarBinding.inflate(layoutInflater, binding.llCategories, false)
            row.tvName.setText(name)
            row.tvValue.text = getString(R.string.percent, pct)
            row.progress.progress = pct
            binding.llCategories.addView(row.root)
        }
    }

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

    private fun renderOrders() {
        binding.llOrders.removeAllViews()
        MockData.orders.filter { orderFilter == null || it.status == orderFilter }.forEach { o ->
            val item = ItemOrderBinding.inflate(layoutInflater, binding.llOrders, false)
            item.tvCode.text = getString(R.string.order_code, o.code)
            item.tvStatus.applyStatus(o.status)
            item.tvParties.text = getString(R.string.order_parties, o.buyer, o.seller)
            item.tvSummary.text = o.summary
            item.tvDate.text = o.date
            item.tvTotal.text = Money.format(o.total)
            item.root.setOnClickListener { showOrder(o) }
            binding.llOrders.addView(item.root)
        }
    }

    /** Retractos y reversiones: el admin controla los plazos legales (Ley 1480 / Ley 2439). */
    private fun showOrder(o: Order) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_code, o.code))
            .setMessage(getString(R.string.order_admin_msg, o.buyer, o.seller, o.summary, Money.format(o.total), o.status.label))
            .setNegativeButton(R.string.action_close, null)
        if (o.status == OrderStatus.RETRACTO || o.status == OrderStatus.REVERSION) {
            builder.setMessage(getString(R.string.order_admin_legal_msg, o.buyer, o.summary, Money.format(o.total),
                getString(if (o.status == OrderStatus.RETRACTO) R.string.legal_retract else R.string.legal_reversal)))
            builder.setPositiveButton(R.string.order_refund) { _, _ ->
                o.status = OrderStatus.CANCELADO
                renderOrders()
                binding.root.snack(getString(R.string.order_refunded, o.code))
            }
        }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
