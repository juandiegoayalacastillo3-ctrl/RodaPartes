package com.rodapartes.app.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemSellerOrderBinding
import com.rodapartes.app.ui.common.km
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Historial de pedidos recibidos con cambio de estado (RF-COM-02, RF-COM-03). */
class SellerOrdersFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<Order> = emptyList()
    private val adapter = Adapter()
    private var filter: OrderStatus? = null

    private val tabs = listOf(null, OrderStatus.NUEVO, OrderStatus.PREPARACION, OrderStatus.ENVIADO, OrderStatus.ENTREGADO)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.title = getString(R.string.seller_orders_title)

        tabs.forEachIndexed { i, status ->
            binding.chipFilters.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.label ?: getString(R.string.filter_all)
                tag = i
                isCheckable = true
                isChecked = i == 0
            })
        }
        binding.chipFilters.setOnCheckedStateChangeListener { group, ids ->
            filter = ids.firstOrNull()?.let { tabs[group.findViewById<Chip>(it).tag as Int] }
            refresh()
        }

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        refresh()
    }

    private fun refresh() {
        items = MockData.sellerOrders().filter { filter == null || it.status == filter }
        adapter.notifyDataSetChanged()
        binding.empty.root.visible(items.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_receipt)
        binding.empty.tvEmptyTitle.text = getString(R.string.seller_orders_empty)
        binding.empty.tvEmptyBody.text = getString(R.string.seller_orders_empty_body)
        (activity as? SellerActivity)?.updateOrdersBadge()
    }

    private fun changeStatus(order: Order, status: OrderStatus) {
        order.status = status
        refresh()
        // En producción: notificación push al comprador en cada cambio (FCM)
        binding.root.snack(getString(R.string.order_status_changed, order.code, status.label))
    }

    private fun reject(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.order_reject_title)
            .setMessage(getString(R.string.order_reject_msg, order.code))
            .setPositiveButton(R.string.action_reject) { _, _ -> changeStatus(order, OrderStatus.CANCELADO) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemSellerOrderBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSellerOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val o = items[position]
            with(holder.b) {
                tvCode.text = getString(R.string.order_code, o.code)
                tvStatus.applyStatus(o.status)
                tvBuyer.text = resources.getQuantityString(R.plurals.seller_order_line, o.items, o.buyer, o.items, Money.format(o.total))
                tvSummary.text = o.summary
                tvAddress.text = getString(R.string.address_distance, o.address, km(o.distanceKm))

                groupNew.visible(o.status == OrderStatus.NUEVO)
                val next = o.status.next()
                btnNext.visible(o.status == OrderStatus.PREPARACION || o.status == OrderStatus.ENVIADO)
                btnNext.text = getString(R.string.order_mark_as, next.label.lowercase())

                btnAccept.setOnClickListener { changeStatus(o, OrderStatus.PREPARACION) }
                btnReject.setOnClickListener { reject(o) }
                btnNext.setOnClickListener { changeStatus(o, next) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
